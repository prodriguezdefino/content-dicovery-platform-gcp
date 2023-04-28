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
package com.google.cloud.pso.beam;

import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.Contextful;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.WithFailures;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.TypeDescriptors;

/**
 * Simple pipeline that captures a Google Drive links, capture the Documents in it (just that doc in
 * the link or a list of them if the link is a folder) and extracts the content texts in them.
 */
public class ContentExtractionPipeline {

  public interface ContentExtractionOptions extends PipelineOptions {

    @Description("The PubSub subscription to read events from.")
    @Validation.Required
    String getSubscription();

    void setSubscription(String value);

    @Description("The GCS location where extracted content will be written to.")
    @Validation.Required
    String getGCSLocation();

    void setGCSLocation(String value);
  }

  public static void main(String[] args) {
    // capture options and config
    var options = PipelineOptionsFactory.fromArgs(args).withValidation().as(ContentExtractionOptions.class);

    // Create the pipeline
    var pipeline = Pipeline.create(options);

    // Read the events with Google Drive identifiers and extract the content identifier
    var maybeUrls = pipeline
      .apply("ReadFileEvents", PubsubIO.readMessages().fromSubscription(options.getSubscription()))
      .apply("ExtractContentId",
        MapElements
          .into(TypeDescriptors.strings())
          .via((PubsubMessage msg) -> {
            var json = new Gson().fromJson(new String(msg.getPayload()), JsonObject.class);
            return Utilities.extractIdFromURL(json.get("url").getAsString());
          })
          .exceptionsVia(new WithFailures.ExceptionAsMapHandler<PubsubMessage>() {
          }));

    // In case the identifier is a folder then we need to crawl it an extract all the docs in there
    var maybeDocIds = maybeUrls.output()
      .apply("MaybeCrawlFolders",
        FlatMapElements
          .into(TypeDescriptors.strings())
          .via((String driveId) -> {
            return Utilities.retrieveDriveFiles(driveId).stream().map(file -> file.getId()).toList();
          }).exceptionsVia(new WithFailures.ExceptionAsMapHandler<String>() {
        }));

    // Now with the documents we just extract the document in paragraphs as text lines
    var maybeDocContents = maybeDocIds
      .output()
      .apply("ExtractContent",
        MapElements
          .into(
            TypeDescriptors.kvs(
              TypeDescriptors.strings(),
              TypeDescriptors.lists(
                TypeDescriptors.strings())))
          .via(Utilities::retrieveDocumentContent)
          .exceptionsVia(new WithFailures.ExceptionAsMapHandler<String>() {
          }));

    // then we transform the document into JSONL format
    var maybeJSONLs = maybeDocContents
      .output()
      .apply("ToJSONLFormat",
        FlatMapElements
          .into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
          .via((KV<String, List<String>> docContent) -> Utilities.docContentToKeyedJSONLFormat(docContent))
          .exceptionsVia(new WithFailures.ExceptionAsMapHandler<KV<String, List<String>>>() {
          }));

    // we grab the jsonl formatted content and write it into the provided GCS location
    maybeJSONLs
      .output()
      .apply("WriteToGCS",
        FileIO.<String, KV<String, String>>writeDynamic()
          .by(nameAndContent -> nameAndContent.getKey())
          .withDestinationCoder(StringUtf8Coder.of())
          .via(Contextful.fn(nameAndContent -> nameAndContent.getValue()),
            TextIO.sink())
          .to(options.getGCSLocation())
          .withNaming(Contextful.fn(name -> FileIO.Write.defaultNaming("jsonl_docs" + name, ".jsonl"))));

    pipeline.run();
  }
}
