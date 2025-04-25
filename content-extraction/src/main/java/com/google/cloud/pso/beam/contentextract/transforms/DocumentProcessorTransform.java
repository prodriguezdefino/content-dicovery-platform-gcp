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

import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.beam.contentextract.Types;
import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveAPIMimeTypes;
import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveClient;
import com.google.cloud.pso.beam.contentextract.transforms.DocumentProcessorTransform.DocumentProcessingResult;
import com.google.cloud.pso.beam.contentextract.utils.DocContentRetriever;
import com.google.cloud.pso.beam.contentextract.utils.ExtractionUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PInput;
import org.apache.beam.sdk.values.POutput;
import org.apache.beam.sdk.values.PValue;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class DocumentProcessorTransform
    extends PTransform<PCollection<PubsubMessage>, DocumentProcessingResult> {

  public static DocumentProcessorTransform create() {
    return new DocumentProcessorTransform();
  }

  @Override
  public DocumentProcessingResult expand(PCollection<PubsubMessage> input) {
    var fetcher =
        DocContentRetriever.create(
            GoogleDriveClient.create(
                input
                    .getPipeline()
                    .getOptions()
                    .as(ContentExtractionOptions.class)
                    .getServiceAccount()));

    var maybePubSubMessageContent =
        input.apply(
            "CheckIfContentIsIncluded",
            ParDo.of(new CheckIfContentIsIncludedDoFn())
                .withOutputTags(
                    CheckIfContentIsIncludedDoFn.pubsubMessageContent,
                    TupleTagList.of(CheckIfContentIsIncludedDoFn.documentContent)
                        .and(CheckIfContentIsIncludedDoFn.failures)));

    var maybeUrls =
        maybePubSubMessageContent
            .get(CheckIfContentIsIncludedDoFn.pubsubMessageContent)
            .apply(
                "ExtractContentId",
                FlatMapElements.into(TypeDescriptor.of(Types.Transport.class))
                    .via(ExtractionUtils::extractContentId)
                    .exceptionsVia(new ErrorHandlingTransform.ErrorHandler<>()));

    // In case the identifier is a folder then we need to crawl it an extract all the docs in there
    var maybeDocIds =
        maybeUrls
            .output()
            .apply(
                "MaybeCrawlFolders",
                FlatMapElements.into(TypeDescriptor.of(Types.Transport.class))
                    .via(
                        (Types.Transport t) -> {
                          return fetcher.retrieveDriveFiles(t.contentId()).stream()
                              .map(
                                  file ->
                                      new Types.Transport(
                                          file.getId(),
                                          // add the mime-type to the transport map so we can
                                          // predicate later on which content retriever to use
                                          Stream.of(
                                                  t.metadata(),
                                                  Map.of(
                                                      GoogleDriveAPIMimeTypes.MIME_TYPE_KEY,
                                                      file.getMimeType()))
                                              .flatMap(map -> map.entrySet().stream())
                                              .collect(
                                                  Collectors.toMap(
                                                      Map.Entry::getKey,
                                                      Map.Entry::getValue,
                                                      (e1, e2) -> e1))))
                              .toList();
                        })
                    .exceptionsVia(new ErrorHandlingTransform.ErrorHandler<>()));

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
                    .via(
                        (Types.Transport t) ->
                            fetcher.retrieveGoogleDriveFileContent(t.contentId(), t.mimeType()))
                    .exceptionsVia(new ErrorHandlingTransform.ErrorHandler<>()));

    var outputContent =
        PCollectionList.of(maybeDocContents.output())
            .and(maybePubSubMessageContent.get(CheckIfContentIsIncludedDoFn.documentContent))
            .apply("FlattenOutputs", Flatten.pCollections());

    return DocumentProcessingResult.of(
        input.getPipeline(),
        outputContent,
        maybeDocIds.failures(),
        maybeUrls.failures(),
        maybeDocContents.failures());
  }

  public static class DocumentProcessingResult implements POutput {

    private final Pipeline pipeline;
    private final PCollection<KV<String, List<String>>> output;
    private final PCollection<Types.ProcessingError> docIdFailures;
    private final PCollection<Types.ProcessingError> docUrlFailures;
    private final PCollection<Types.ProcessingError> docContentFailures;
    private final TupleTag<KV<String, List<String>>> outputTag;
    private final TupleTag<Types.ProcessingError> docIdFailuresTag;
    private final TupleTag<Types.ProcessingError> docUrlFailuresTag;
    private final TupleTag<Types.ProcessingError> docContentFailuresTag;

    public DocumentProcessingResult(
        Pipeline pipeline,
        PCollection<KV<String, List<String>>> output,
        PCollection<Types.ProcessingError> docIdFailures,
        PCollection<Types.ProcessingError> docUrlFailures,
        PCollection<Types.ProcessingError> docContentFailures,
        TupleTag<KV<String, List<String>>> outputTag,
        TupleTag<Types.ProcessingError> docIdFailuresTag,
        TupleTag<Types.ProcessingError> docUrlFailuresTag,
        TupleTag<Types.ProcessingError> docContentFailuresTag) {
      this.pipeline = pipeline;
      this.output = output;
      this.docIdFailures = docIdFailures;
      this.docUrlFailures = docUrlFailures;
      this.docContentFailures = docContentFailures;
      this.outputTag = outputTag;
      this.docIdFailuresTag = docIdFailuresTag;
      this.docUrlFailuresTag = docUrlFailuresTag;
      this.docContentFailuresTag = docContentFailuresTag;
    }

    public static DocumentProcessingResult of(
        Pipeline pipeline,
        PCollection<KV<String, List<String>>> output,
        PCollection<Types.ProcessingError> docIdFailures,
        PCollection<Types.ProcessingError> docUrlFailures,
        PCollection<Types.ProcessingError> docContentFailures) {
      return new DocumentProcessingResult(
          pipeline,
          output,
          docIdFailures,
          docUrlFailures,
          docContentFailures,
          new TupleTag<>(),
          new TupleTag<>(),
          new TupleTag<>(),
          new TupleTag<>());
    }

    @Override
    public Pipeline getPipeline() {
      return pipeline;
    }

    @Override
    public Map<TupleTag<?>, PValue> expand() {
      return Map.of(
          outputTag,
          output,
          docUrlFailuresTag,
          docUrlFailures,
          docIdFailuresTag,
          docIdFailures,
          docContentFailuresTag,
          docContentFailures);
    }

    @Override
    public void finishSpecifyingOutput(
        String transformName, PInput input, PTransform<?, ?> transform) {}

    public PCollection<KV<String, List<String>>> output() {
      return output;
    }

    public PCollectionList<Types.ProcessingError> failures() {
      return PCollectionList.of(docIdFailures).and(docUrlFailures).and(docContentFailures);
    }
  }

  static class CheckIfContentIsIncludedDoFn extends DoFn<PubsubMessage, PubsubMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(CheckIfContentIsIncludedDoFn.class);

    static final TupleTag<PubsubMessage> pubsubMessageContent = new TupleTag<>() {};
    static final TupleTag<KV<String, List<String>>> documentContent = new TupleTag<>() {};
    static final TupleTag<Types.ProcessingError> failures = new TupleTag<>() {};

    @ProcessElement
    public void process(ProcessContext context) {
      var payload = new String(context.element().getPayload());
      try {
        var json = new Gson().fromJson(payload, JsonObject.class);

        if (json.has("document")) {
          // the document property is present, the message should have the content included, so
          // there is no need to extract it from Google Drive.
          var document = json.getAsJsonObject("document");
          if (document.has("id") && document.has("content")) {
            var content =
                KV.of(
                    document.get("id").getAsString(),
                    Stream.of(
                            new String(
                                    Base64.getDecoder()
                                        .decode(document.get("content").getAsString()))
                                .split("\n"))
                        .toList());
            LOG.info("Extracted {} from document json.", content.toString());
            context.output(documentContent, content);
          } else {
            throw new IllegalArgumentException(
                "The provided document does not contain a document 'id' or a 'content' property.");
          }
        } else {
          // nothing to be done here, let the pipeline continue processing
          context.output(pubsubMessageContent, context.element());
        }
      } catch (Exception ex) {
        var errMsg = "Error while trying to review if the message contains the document content.";
        LOG.error(errMsg);
        context.output(failures, new Types.Discardable(payload, ex));
      }
    }
  }
}
