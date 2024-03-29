/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.pso.beam.contentextract.transforms;

import com.google.bigtable.v2.Mutation;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.beam.contentextract.Types.IndexableContent;
import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveClient;
import com.google.cloud.pso.beam.contentextract.clients.MatchingEngineClient;
import com.google.cloud.pso.beam.contentextract.clients.Types.DeleteMatchingEngineDatapoints;
import com.google.cloud.pso.beam.contentextract.clients.Types.UpsertMatchingEngineDatapoints;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import com.google.cloud.pso.beam.contentextract.utils.DocContentRetriever;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.beam.sdk.io.gcp.bigtable.BigtableIO;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class StoreEmbeddingsResults extends PTransform<PCollection<List<IndexableContent>>, PDone> {

  private static final Logger LOG = LoggerFactory.getLogger(StoreEmbeddingsResults.class);

  public static StoreEmbeddingsResults create() {
    return new StoreEmbeddingsResults();
  }

  @Override
  public PDone expand(PCollection<List<IndexableContent>> input) {
    var options = input.getPipeline().getOptions().as(ContentExtractionOptions.class);
    var fetcher = DocContentRetriever.create(GoogleDriveClient.create(options.getServiceAccount()));
    var matchingEngineClient =
        MatchingEngineClient.create(
            options.getRegion(),
            options.getMatchingEngineIndexId(),
            options.getMatchingEngineIndexEndpointId(),
            options.getMatchingEngineIndexEndpointDomain(),
            options.getMatchingEngineIndexEndpointDeploymentName());

    input
        .apply(
            "ClassifyIfNeedToRemoveContent",
            ParDo.of(
                new CheckContentToRemoveFn(
                    options.getProject(),
                    options.getBigTableInstanceName(),
                    options.getBigTableTableName())))
        .apply(
            "RemoveFromIndexes",
            ParDo.of(
                new RemoveContentFromIndexes(
                    options.getProject(),
                    options.getBigTableInstanceName(),
                    options.getBigTableTableName(),
                    matchingEngineClient)));

    // store the embeddings into Matching Engine for later query
    input.apply(
        "UpsertIndexDatapoints",
        ParDo.of(new MatchingEngineDatapointUpsertDoFn(matchingEngineClient)));

    // also, we need to store the content and its id into BigTable since the content is later
    // needed to set context for the text prediction model
    input
        .apply(
            "ToBigTableContentMutations", ParDo.of(new EmbeddingsToContentMutationsDoFn(fetcher)))
        .apply(
            "WriteContentOnBigTable",
            BigtableIO.write()
                .withProjectId(options.getProject())
                .withInstanceId(options.getBigTableInstanceName())
                .withTableId(options.getBigTableTableName()));

    return PDone.in(input.getPipeline());
  }

  static class RemoveContentFromIndexes extends DoFn<List<String>, Void> {

    private final String projectId;
    private final String instanceId;
    private final String tableId;
    private final MatchingEngineClient mEClient;

    public RemoveContentFromIndexes(
        String projectId, String instanceId, String tableId, MatchingEngineClient mEClient) {
      this.projectId = projectId;
      this.instanceId = instanceId;
      this.tableId = tableId;
      this.mEClient = mEClient;
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      // remove data from the matching engine index
      mEClient.deleteVectorDBDatapointsWithRetries(
          new DeleteMatchingEngineDatapoints(context.element()));

      // remove all the content rows with prefix
      try (var tableAdminClient = BigtableTableAdminClient.create(projectId, instanceId)) {
        tableAdminClient.dropRowRange(
            tableId,
            context.element().stream()
                .map(s -> Utilities.prefixIdFromContentId(s))
                .findFirst()
                .orElse("NA"));
      } catch (Exception ex) {
        LOG.error("problems while removing content ids from BigTable.", ex);
        throw new RuntimeException(ex);
      }
    }
  }

  static class CheckContentToRemoveFn extends DoFn<List<IndexableContent>, List<String>> {

    private final String projectId;
    private final String instanceId;
    private final String tableId;

    public CheckContentToRemoveFn(String projectId, String instanceId, String tableId) {
      this.projectId = projectId;
      this.instanceId = instanceId;
      this.tableId = tableId;
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      var contentIds = context.element().stream().map(c -> c.key()).collect(Collectors.toSet());
      context.element().stream()
          .findFirst()
          .ifPresent(
              first -> {
                // we assume all the contents come with the same prefix id since all the content is
                // from the same document
                var prefix = Utilities.prefixIdFromContentId(first.key());
                try (var dataClient = BigtableDataClient.create(projectId, instanceId)) {
                  var notPresentKeys = Lists.<String>newArrayList();
                  // iterate on already existing entries for this content id
                  for (var row : dataClient.readRows(Query.create(tableId).prefix(prefix))) {
                    if (!contentIds.contains(row.getKey().toStringUtf8())) {
                      // mark those not present in the current content for deletion
                      notPresentKeys.add(row.getKey().toStringUtf8());
                    }
                  }
                  if (!notPresentKeys.isEmpty()) {
                    context.output(notPresentKeys);
                  }
                } catch (Exception ex) {
                  LOG.error("problems while reading prefixed content ids from BigTable.", ex);
                  throw new RuntimeException(ex);
                }
              });
    }
  }

  static class EmbeddingsToContentMutationsDoFn
      extends DoFn<List<IndexableContent>, KV<ByteString, Iterable<Mutation>>> {

    private final String columnFamilyName = "data";
    private final String columnQualifierContent = "content";
    private final String columnQualifierLink = "link";
    private final DocContentRetriever fetcher;

    public EmbeddingsToContentMutationsDoFn(DocContentRetriever fetcher) {
      this.fetcher = fetcher;
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      var timestamp = Instant.now().getMillis() * 1000;
      context.element().stream()
          // create the mutation on the KV
          .map(
              content ->
                  KV.of(
                      ByteString.copyFromUtf8(content.key()),
                      (Iterable<Mutation>)
                          Lists.newArrayList(
                              Mutation.newBuilder()
                                  .setSetCell(
                                      Mutation.SetCell.newBuilder()
                                          .setTimestampMicros(timestamp)
                                          .setValue(ByteString.copyFromUtf8(content.content()))
                                          .setColumnQualifier(
                                              ByteString.copyFromUtf8(columnQualifierContent))
                                          .setFamilyName(columnFamilyName)
                                          .build())
                                  .build(),
                              Mutation.newBuilder()
                                  .setSetCell(
                                      Mutation.SetCell.newBuilder()
                                          .setTimestampMicros(timestamp)
                                          .setValue(
                                              ByteString.copyFromUtf8(
                                                  Utilities.reconstructDocumentLinkFromEmbeddingsId(
                                                      content.key(),
                                                      fetcher.retrieveFileType(
                                                          Utilities.fileIdFromContentId(
                                                              content.key())))))
                                          .setColumnQualifier(
                                              ByteString.copyFromUtf8(columnQualifierLink))
                                          .setFamilyName(columnFamilyName)
                                          .build())
                                  .build())))
          // send the data to storage
          .forEach(kv -> context.output(kv));
    }
  }

  static class MatchingEngineDatapointUpsertDoFn extends DoFn<List<IndexableContent>, Void> {
    private final MatchingEngineClient serviceClient;

    public MatchingEngineDatapointUpsertDoFn(MatchingEngineClient serviceClient) {
      this.serviceClient = serviceClient;
    }

    @ProcessElement
    public void process(ProcessContext context) {
      // recommendation is not to send more than 20 datapoints per request to matching engine
      // index upsert method
      Lists.partition(context.element(), 15)
          .forEach(
              embeddings ->
                  serviceClient.upsertVectorDBDataPointsWithRetries(
                      new UpsertMatchingEngineDatapoints(
                          embeddings.stream()
                              .map(
                                  kv ->
                                      new com.google.cloud.pso.beam.contentextract.clients.Types
                                          .Datapoint(kv.key(), kv.embedding(), null))
                              .toList())));
    }
  }
}
