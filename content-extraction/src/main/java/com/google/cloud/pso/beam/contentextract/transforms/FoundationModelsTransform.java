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
import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.beam.contentextract.clients.MatchingEngineClient;
import com.google.cloud.pso.beam.contentextract.clients.Types;
import com.google.cloud.pso.beam.contentextract.utils.DocContentRetriever;
import com.google.cloud.pso.beam.contentextract.utils.GoogleDocClient;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import org.apache.beam.sdk.coders.DoubleCoder;
import org.apache.beam.sdk.coders.IterableCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.extensions.python.PythonExternalTransform;
import org.apache.beam.sdk.io.gcp.bigtable.BigtableIO;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.joda.time.Instant;

/** */
public abstract class FoundationModelsTransform
    extends PTransform<PCollection<KV<String, List<String>>>, PDone> {

  public static ProcessContentForEmbeddings processAndStoreEmbeddings() {
    return new ProcessContentForEmbeddings();
  }

  public static class ProcessContentForEmbeddings extends FoundationModelsTransform {
    private static final String PYTHON_EMBEDDINGS_TRANSFORM =
        "beam.embeddings.transforms.ExtractEmbeddingsTransform";

    @Override
    @SuppressWarnings("deprecation")
    public PDone expand(PCollection<KV<String, List<String>>> input) {
      var options = input.getPipeline().getOptions().as(ContentExtractionOptions.class);
      var fetcher = DocContentRetriever.create(GoogleDocClient.create(options.getServiceAccount()));

      var keyedContentAndEmbeddings =
          input
              .apply(
                  "PrepContent",
                  MapElements.into(
                          TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                      .via(Utilities::contentToKeyedParagraphs))
              .apply(
                  "GenerateEmbeddings",
                  PythonExternalTransform
                      .<PCollection<KV<String, String>>,
                          PCollection<Iterable<KV<String, KV<String, Iterable<Double>>>>>>
                          from(PYTHON_EMBEDDINGS_TRANSFORM, options.getExpansionService())
                      .withKwargs(
                          Map.of(
                              "project",
                              options.getProject(),
                              "staging_bucket",
                              options.getTempLocation()))
                      .withOutputCoder(
                          IterableCoder.of(
                              KvCoder.of(
                                  StringUtf8Coder.of(),
                                  KvCoder.of(
                                      StringUtf8Coder.of(), IterableCoder.of(DoubleCoder.of()))))))
              .apply("FlatMap", Flatten.iterables())
              .apply("ConsolidateEmbeddings", GroupByKey.create())
              .apply(
                  "FormatEmbeddingsResults",
                  MapElements.into(
                          TypeDescriptors.lists(
                              TypeDescriptors.kvs(
                                  TypeDescriptors.strings(),
                                  TypeDescriptors.kvs(
                                      TypeDescriptors.strings(),
                                      TypeDescriptors.lists(TypeDescriptors.doubles())))))
                      .via(Utilities::addEmbeddingsIdentifiers));

      // store the embeddings into Matching Engine for later query
      keyedContentAndEmbeddings
          .apply(
              "RemoveContentFromEmbeddings",
              MapElements.into(
                      TypeDescriptors.lists(
                          TypeDescriptors.kvs(
                              TypeDescriptors.strings(),
                              TypeDescriptors.lists(TypeDescriptors.doubles()))))
                  .via(Utilities::removeContentFromEmbeddingsKV))
          .apply(
              "UpsertIndexDatapoints",
              ParDo.of(
                  new MatchingEngineDatapointUpsertDoFn(
                      MatchingEngineClient.create(
                          options.getRegion(),
                          options.getMatchingEngineIndexId(),
                          options.getMatchingEngineIndexEndpointId(),
                          options.getMatchingEngineIndexEndpointDomain(),
                          options.getMatchingEngineIndexEndpointDeploymentName()))));

      // also, we need to store the content and its id into BigTable since the content is later
      // needed to set context for the text prediction model
      keyedContentAndEmbeddings
          .apply(
              "ToBigTableContentMutations", ParDo.of(new EmbeddingsToContentMutationsDoFn(fetcher)))
          .apply(
              "WriteContentOnBigTable",
              BigtableIO.write()
                  .withProjectId(options.getProject())
                  .withInstanceId(options.getBigTableInstanceName())
                  .withTableId("content_per_embedding"));

      return PDone.in(input.getPipeline());
    }

    private static class EmbeddingsToContentMutationsDoFn
        extends DoFn<
            List<KV<String, KV<String, List<Double>>>>, KV<ByteString, Iterable<Mutation>>> {

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
            // remove the embeddings values since we don't need them
            .map(kv -> KV.of(kv.getKey(), kv.getValue().getKey()))
            // create the mutation on the KV
            .map(
                kv ->
                    KV.of(
                        ByteString.copyFromUtf8(kv.getKey()),
                        (Iterable<Mutation>)
                            Lists.newArrayList(
                                Mutation.newBuilder()
                                    .setSetCell(
                                        Mutation.SetCell.newBuilder()
                                            .setTimestampMicros(timestamp)
                                            .setValue(ByteString.copyFromUtf8(kv.getValue()))
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
                                                    Utilities
                                                        .reconstructDocumentLinkFromEmbeddingsId(
                                                            kv.getKey(),
                                                            fetcher.retrieveFileType(
                                                                Utilities.fileIdFromContentId(
                                                                    kv.getKey())))))
                                            .setColumnQualifier(
                                                ByteString.copyFromUtf8(columnQualifierLink))
                                            .setFamilyName(columnFamilyName)
                                            .build())
                                    .build())))
            // send the data to storage
            .forEach(kv -> context.output(kv));
      }
    }

    static class MatchingEngineDatapointUpsertDoFn
        extends DoFn<List<KV<String, List<Double>>>, Void> {

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
                    serviceClient.upsertVectorDBDataPoints(
                        new Types.UpsertMatchingEngineDatapoints(
                            embeddings.stream()
                                .map(kv -> new Types.Datapoint(kv.getKey(), kv.getValue(), null))
                                .toList())));
      }
    }
  }
}
