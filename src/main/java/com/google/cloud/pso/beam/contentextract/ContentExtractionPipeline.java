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
package com.google.cloud.pso.beam.contentextract;

import com.google.cloud.pso.beam.contentextract.utils.DocFetcher;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.DoubleCoder;
import org.apache.beam.sdk.coders.IterableCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;
import org.apache.beam.sdk.extensions.python.PythonExternalTransform;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.SdkHarnessOptions;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.Contextful;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.WithFailures;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.joda.time.Duration;

/**
 * Simple pipeline that captures a Google Drive links, capture the Documents in it (just that doc in
 * the link or a list of them if the link is a folder) and extracts the content texts in them.
 */
public class ContentExtractionPipeline {

  private static final String PYTHON_EMBEDDINGS_TRANSFORM =
      "beam.embeddings.transforms.ExtractEmbeddingsTransform";

  public interface ContentExtractionOptions extends PipelineOptions, SdkHarnessOptions {

    @Description("The PubSub subscription to read events from.")
    @Validation.Required
    String getSubscription();

    void setSubscription(String value);

    @Description("The GCS location where extracted content will be written to.")
    @Validation.Required
    String getBucketLocation();

    void setBucketLocation(String value);

    @Description("The secret identifier to use while accessing credentials.")
    @Validation.Required
    String getSecretId();

    void setSecretId(String value);

    @Description("The GCP project.")
    @Default.InstanceFactory(GcpOptions.DefaultProjectFactory.class)
    String getProject();

    void setProject(String value);

    @Description("The local expansion server url in the form of 'localhost:PORT'.")
    @Validation.Required
    String getExpansionService();

    void setExpansionService(String value);
  }

  public static void main(String[] args) {
    // capture options and config
    var options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(ContentExtractionOptions.class);

    // Create the pipeline
    var pipeline = Pipeline.create(options);

    var secret =
        String.format(
            "projects/%s/secrets/%s/versions/latest", options.getProject(), options.getSecretId());
    var fetcher = DocFetcher.create(secret);

    // Read the events with Google Drive identifiers and extract the content identifier
    var maybeUrls =
        pipeline
            .apply(
                "ReadSharedURLs",
                PubsubIO.readMessages().fromSubscription(options.getSubscription()))
            .apply(
                "ApplyWindow",
                Window.<PubsubMessage>into(FixedWindows.of(Duration.standardMinutes(1)))
                    .triggering(Repeatedly.forever(AfterWatermark.pastEndOfWindow()))
                    .discardingFiredPanes()
                    .withAllowedLateness(Duration.standardMinutes(1)))
            .apply(
                "ExtractContentId",
                FlatMapElements.into(TypeDescriptors.strings())
                    .via(
                        (PubsubMessage msg) -> {
                          var json =
                              new Gson().fromJson(new String(msg.getPayload()), JsonObject.class);
                          if (json.has("url"))
                            return List.of(
                                Utilities.extractIdFromURL(json.get("url").getAsString()));
                          else if (json.has("urls"))
                            return json.get("urls").getAsJsonArray().asList().stream()
                                .map(e -> e.getAsString())
                                .toList();
                          else
                            throw new IllegalArgumentException(
                                "Provided JSON does not have the expected fields ('url', 'urls')");
                        })
                    .exceptionsVia(new WithFailures.ExceptionAsMapHandler<PubsubMessage>() {}));

    // In case the identifier is a folder then we need to crawl it an extract all the docs in there
    var maybeDocIds =
        maybeUrls
            .output()
            .apply(
                "MaybeCrawlFolders",
                FlatMapElements.into(TypeDescriptors.strings())
                    .via(
                        (String driveId) -> {
                          return fetcher.retrieveDriveFiles(driveId).stream()
                              .map(file -> file.getId())
                              .toList();
                        })
                    .exceptionsVia(new WithFailures.ExceptionAsMapHandler<String>() {}));

    // Now with the documents we just extract the document in paragraphs as text lines
    var maybeDocContents =
        maybeDocIds
            .output()
            .apply(
                "ExtractContent",
                MapElements.into(
                        TypeDescriptors.kvs(
                            TypeDescriptors.strings(),
                            TypeDescriptors.lists(TypeDescriptors.strings())))
                    .via((String docId) -> fetcher.retrieveDocumentContent(docId))
                    .exceptionsVia(new WithFailures.ExceptionAsMapHandler<String>() {}));

    // then we transform the document into JSONL format
    var maybeJSONLs =
        maybeDocContents
            .output()
            .apply(
                "ToJSONLFormat",
                FlatMapElements.into(
                        TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                    .via(Utilities::docContentToKeyedJSONLFormat)
                    .exceptionsVia(
                        new WithFailures.ExceptionAsMapHandler<KV<String, List<String>>>() {}));

    // we grab the jsonl formatted content and write it into the provided GCS location
    maybeJSONLs
        .output()
        .apply(
            "WriteJSONLToGCS",
            FileIO.<String, KV<String, String>>writeDynamic()
                .by(nameAndLineContent -> nameAndLineContent.getKey())
                .withDestinationCoder(StringUtf8Coder.of())
                .via(
                    Contextful.fn(
                        nameAndLineContent ->
                            nameAndLineContent.getValue().replaceAll("[\\n\\r]", "")),
                    TextIO.sink())
                .to(options.getBucketLocation())
                .withNaming(
                    Contextful.fn(
                        name -> FileIO.Write.defaultNaming("jsonl_docs/" + name, ".jsonl"))));

    // also we grab the content an create document chunks that will be used to extract embeddings
    maybeDocContents
        .output()
        .apply(
            "PrepContent",
            MapElements.into(
                    TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                .via(
                    content ->
                        KV.of(
                            content.getKey(),
                            content.getValue().stream().collect(Collectors.joining("\n")))))
        .apply(
            "GenerateEmbeddings",
            PythonExternalTransform
                .<PCollection<KV<String, String>>,
                    PCollection<KV<String, Iterable<Iterable<Double>>>>>
                    from(PYTHON_EMBEDDINGS_TRANSFORM, options.getExpansionService())
                .withKwargs(
                    Map.of(
                        "project",
                        options.getProject(),
                        "staging_bucket",
                        options.getTempLocation()))
                .withOutputCoder(
                    KvCoder.of(
                        StringUtf8Coder.of(),
                        IterableCoder.of(IterableCoder.of(DoubleCoder.of())))))
        .apply(
            "FormatEmbeddings",
            FlatMapElements.into(
                    TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                .via(Utilities::embeddingToKeyedJSONLFormat))
        .apply(
            "WriteContentToGCS",
            FileIO.<String, KV<String, String>>writeDynamic()
                .by(nameAndContent -> nameAndContent.getKey())
                .withDestinationCoder(StringUtf8Coder.of())
                .via(
                    Contextful.fn(
                        nameAndContent -> nameAndContent.getValue().replaceAll("[\\n\\r]", "")),
                    TextIO.sink())
                .to(options.getBucketLocation())
                .withNaming(
                    Contextful.fn(
                        name ->
                            FileIO.Write.defaultNaming(
                                "embeddings-index-contents/" + name, ".emb"))));

    // little bit of error handling
    PCollectionList.of(
            maybeUrls
                .failures()
                .apply(
                    "FormatPubsubMessageToError",
                    MapElements.into(
                            TypeDescriptors.kvs(
                                TypeDescriptors.strings(),
                                TypeDescriptors.maps(
                                    TypeDescriptors.strings(), TypeDescriptors.strings())))
                        .via(
                            error ->
                                KV.of(new String(error.getKey().getPayload()), error.getValue()))))
        .and(maybeDocIds.failures())
        .and(maybeDocContents.failures())
        .and(
            maybeJSONLs
                .failures()
                .apply(
                    "FormatContentToError",
                    MapElements.into(
                            TypeDescriptors.kvs(
                                TypeDescriptors.strings(),
                                TypeDescriptors.maps(
                                    TypeDescriptors.strings(), TypeDescriptors.strings())))
                        .via(error -> KV.of(error.getKey().getKey(), error.getValue()))))
        .apply("FlattenErrors", Flatten.pCollections())
        .apply(
            "WriteErrorsToGCS",
            FileIO.<String, KV<String, Map<String, String>>>writeDynamic()
                .by(error -> error.getKey().replaceAll("[\\n\\r]", ""))
                .withDestinationCoder(StringUtf8Coder.of())
                .via(Contextful.fn(error -> error.getValue().toString()), TextIO.sink())
                .to(options.getBucketLocation())
                .withNaming(
                    Contextful.fn(name -> FileIO.Write.defaultNaming("errors/" + name, ".txt"))));

    pipeline.run();
  }
}
