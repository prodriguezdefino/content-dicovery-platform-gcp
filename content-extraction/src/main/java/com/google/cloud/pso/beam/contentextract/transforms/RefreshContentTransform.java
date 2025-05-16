/*
 * Copyright (C) 2025 Google Inc.
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

import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.beam.contentextract.utils.DocContentRetriever;
import com.google.cloud.pso.rag.common.Utilities;
import com.google.cloud.pso.rag.drive.GoogleDriveClient;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.StreamSupport;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.GenerateSequence;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.Keys;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.WithKeys;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PDone;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class RefreshContentTransform extends PTransform<PBegin, PDone> {
  private static final Logger LOG = LoggerFactory.getLogger(RefreshContentTransform.class);

  public record ContentProcessed(String contentId, Long processedAtInMillis) {}

  public static RefreshContentTransform create() {
    return new RefreshContentTransform();
  }

  @Override
  public PDone expand(PBegin input) {
    var options = input.getPipeline().getOptions().as(ContentExtractionOptions.class);
    var hours = 6;
    var fetcher =
        DocContentRetriever.create(
            GoogleDriveClient.create(
                input
                    .getPipeline()
                    .getOptions()
                    .as(ContentExtractionOptions.class)
                    .getServiceAccount()));
    input
        .apply(
            "RefreshEvery" + hours + "hours",
            GenerateSequence.from(0L).withRate(1, Duration.standardHours(hours)))
        .apply(
            "ReadContentIds",
            ParDo.of(
                new ReadFromTableFn(
                    options.getProject(),
                    options.getBigTableInstanceName(),
                    options.getBigTableTableName())))
        .apply("Flatten", Flatten.iterables())
        .apply("AddKeys", WithKeys.of(id -> id))
        .setCoder(KvCoder.of(StringUtf8Coder.of(), StringUtf8Coder.of()))
        .apply(
            "ApplyWindow",
            Window.<KV<String, String>>into(FixedWindows.of(Duration.standardMinutes(1)))
                .triggering(Repeatedly.forever(AfterWatermark.pastEndOfWindow()))
                .discardingFiredPanes()
                .withAllowedLateness(Duration.standardMinutes(1)))
        .apply(
            "Deduplicate",
            Combine.perKey(
                iterable ->
                    StreamSupport.stream(iterable.spliterator(), false).findFirst().orElse("")))
        .apply("KeepIds", Keys.create())
        .apply("ShouldRefreshDoc", ParDo.of(new ShouldRefreshDocFn(fetcher, hours)))
        .apply("SendToPubSub", PubsubIO.writeMessages().to(options.getTopic()));

    return PDone.in(input.getPipeline());
  }

  static class ShouldRefreshDocFn extends DoFn<String, PubsubMessage> {

    private final DocContentRetriever fetcher;
    private final Integer lastRefreshHoursAgo;

    public ShouldRefreshDocFn(DocContentRetriever fetcher, Integer minsAgo) {
      this.fetcher = fetcher;
      this.lastRefreshHoursAgo = minsAgo;
    }

    @ProcessElement
    public void process(ProcessContext context) {
      var lastRefresh = Instant.now().minus(Duration.standardHours(lastRefreshHoursAgo));
      fetcher
          .filterFilesUpForRefresh(new ContentProcessed(context.element(), lastRefresh.getMillis()))
          .map(file -> file.getWebViewLink())
          .map(
              url -> {
                var json = new JsonObject();
                json.addProperty("url", url);
                return json;
              })
          .map(json -> new PubsubMessage(json.toString().getBytes(), Maps.newHashMap()))
          .ifPresent(pmsg -> context.output(pmsg));
    }
  }

  static class ReadFromTableFn extends DoFn<Long, List<String>> {

    private final String projectId;
    private final String instanceId;
    private final String tableId;

    public ReadFromTableFn(String projectId, String instanceId, String tableId) {
      this.projectId = projectId;
      this.instanceId = instanceId;
      this.tableId = tableId;
    }

    @ProcessElement
    public void processElement(@Element Long input, DoFn.OutputReceiver<List<String>> out) {
      try (var dataClient = BigtableDataClient.create(projectId, instanceId)) {
        var contentKeys = Sets.<String>newHashSet();
        for (var row : dataClient.readRows(Query.create(tableId))) {
          contentKeys.add(Utilities.fileIdFromContentId(row.getKey().toStringUtf8()));
          if (contentKeys.size() > 1000) {
            out.output(contentKeys.stream().toList());
            LOG.info("sent {}", contentKeys.toString());
            contentKeys.clear();
          }
        }
        if (!contentKeys.isEmpty()) {
          LOG.info("sent {}", contentKeys.toString());
          out.output(contentKeys.stream().toList());
        }
      } catch (Exception ex) {
        LOG.error("problems while reading content ids from BigTable.", ex);
        throw new RuntimeException(ex);
      }
    }
  }
}
