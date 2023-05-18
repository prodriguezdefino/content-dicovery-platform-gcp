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

import com.google.cloud.pso.beam.contentextract.Types.*;
import com.google.cloud.pso.beam.contentextract.transforms.DocumentProcessorTransform;
import com.google.cloud.pso.beam.contentextract.transforms.ErrorHandlingTransform;
import com.google.cloud.pso.beam.contentextract.transforms.FoundationModelsTransform;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Contextful;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.joda.time.Duration;

/**
 * Simple pipeline that captures a Google Drive links, from them compiles the Documents in it (just
 * that doc in the link or a list of them if the link is a folder), extracts the content texts in
 * them and then process + store the embeddings.
 */
public class ContentExtractionPipeline {

  public static void main(String[] args) {
    // capture options and config
    var options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(ContentExtractionOptions.class);

    // Create the pipeline
    var pipeline = Pipeline.create(options);
    pipeline.getCoderRegistry().registerCoderForClass(Transport.class, TransportCoder.of());

    // Read the events with Google Drive identifiers and extract the documents contents
    var maybeDocsContents =
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
            .apply("ExtractDocumentsContent", DocumentProcessorTransform.create());

    // then we transform the document's content into JSONL format and store it on GCS
    maybeDocsContents
        .output()
        .apply(
            "ToJSONLFormat",
            FlatMapElements.into(
                    TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                .via(Utilities::docContentToKeyedJSONLFormat))
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
                        name -> FileIO.Write.defaultNaming("jsonl_content/" + name, ".jsonl"))));

    // also we grab the content an create document chunks that will be used to extract embeddings
    // and then they will be stored in MatchingEngine
    maybeDocsContents
        .output()
        .apply("ProcessAndStoreEmbeddings", FoundationModelsTransform.processAndStoreEmbeddings());

    // also little bit of error handling.
    // if the error is retriable (like lack of permissions on the docs) the events will be sent to
    // the original PubSub Topic
    maybeDocsContents
        .failures()
        .apply("ProcessErrorAndMaybeRetry", ErrorHandlingTransform.create());

    pipeline.run();
  }
}
