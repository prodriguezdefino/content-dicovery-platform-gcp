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
import com.google.cloud.pso.beam.contentextract.transforms.DocumentProcessorTransform.DocumentProcessingResult;
import com.google.cloud.pso.beam.contentextract.utils.DocContentRetriever;
import com.google.cloud.pso.rag.common.Ingestion;
import com.google.cloud.pso.rag.common.Ingestion.Request;
import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.drive.GoogleDriveAPIMimeTypes;
import com.google.cloud.pso.rag.drive.GoogleDriveClient;
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
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PInput;
import org.apache.beam.sdk.values.POutput;
import org.apache.beam.sdk.values.PValue;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.apache.beam.sdk.values.TypeDescriptor;
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

    var rawContentAndGoogleUrls =
        input.apply(
            "DistributeByContent",
            ParDo.of(new DistributeByContentDoFn())
                .withOutputTags(
                    DistributeByContentDoFn.googleContent,
                    TupleTagList.of(DistributeByContentDoFn.rawContent)
                        .and(DistributeByContentDoFn.failures)));

    // In case the identifier is a folder then we need to crawl it an extract all the docs in there
    var maybeDocIds =
        rawContentAndGoogleUrls
            .get(DistributeByContentDoFn.googleContent)
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
                MapElements.into(TypeDescriptor.of(Types.Content.class))
                    .via(
                        (Types.Transport t) ->
                            fetcher.retrieveGoogleDriveFileContent(t.contentId(), t.mimeType()))
                    .exceptionsVia(new ErrorHandlingTransform.ErrorHandler<>()));

    var outputContent =
        PCollectionList.of(maybeDocContents.output())
            .and(rawContentAndGoogleUrls.get(DistributeByContentDoFn.rawContent))
            .apply("FlattenOutputs", Flatten.pCollections());

    return DocumentProcessingResult.of(
        input.getPipeline(),
        outputContent,
        maybeDocIds.failures(),
        rawContentAndGoogleUrls.get(DistributeByContentDoFn.failures),
        maybeDocContents.failures());
  }

  public static class DocumentProcessingResult implements POutput {

    private final Pipeline pipeline;
    private final PCollection<Types.Content> output;
    private final PCollection<Types.ProcessingError> docIdFailures;
    private final PCollection<Types.ProcessingError> docUrlFailures;
    private final PCollection<Types.ProcessingError> docContentFailures;
    private final TupleTag<Types.Content> outputTag;
    private final TupleTag<Types.ProcessingError> docIdFailuresTag;
    private final TupleTag<Types.ProcessingError> docUrlFailuresTag;
    private final TupleTag<Types.ProcessingError> docContentFailuresTag;

    public DocumentProcessingResult(
        Pipeline pipeline,
        PCollection<Types.Content> output,
        PCollection<Types.ProcessingError> docIdFailures,
        PCollection<Types.ProcessingError> docUrlFailures,
        PCollection<Types.ProcessingError> docContentFailures,
        TupleTag<Types.Content> outputTag,
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
        PCollection<Types.Content> output,
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

    public PCollection<Types.Content> output() {
      return output;
    }

    public PCollectionList<Types.ProcessingError> failures() {
      return PCollectionList.of(docIdFailures).and(docUrlFailures).and(docContentFailures);
    }
  }

  static class DistributeByContentDoFn extends DoFn<PubsubMessage, Types.Transport> {
    private static final Logger LOG = LoggerFactory.getLogger(DistributeByContentDoFn.class);

    static final TupleTag<Types.Transport> googleContent = new TupleTag<>() {};
    static final TupleTag<Types.Content> rawContent = new TupleTag<>() {};
    static final TupleTag<Types.ProcessingError> failures = new TupleTag<>() {};

    @ProcessElement
    public void process(ProcessContext context) {
      var payload = new String(context.element().getPayload());
      InteractionHelper.jsonMapper(payload, Request.class)
          .flatMap(
              request ->
                  switch (request) {
                    case Request(var gDrives, var __, var ___) when gDrives.isPresent() ->
                        gDriveContent(gDrives.get(), context);
                    case Request(var __, var maybeData, var ___) when maybeData.isPresent() ->
                        rawDataContent(maybeData.get(), context);
                    case Request(var __, var ___, var maybeRefs) when maybeRefs.isPresent() ->
                        referencesContent(maybeRefs.get(), context);
                    default -> Result.failure(notSupported(request.toString()));
                  })
          .orElse(
              ex -> {
                var errMsg =
                    "Error while trying to review if the message contains the document content.";
                LOG.error(errMsg, ex);
                context.output(failures, new Types.Discardable(payload, ex));
                return false;
              });
    }

    static Exception notSupported(String request) {
      return new IllegalArgumentException("Ingestion request not supported: " + request);
    }

    static Result<Boolean, Exception> gDriveContent(
        List<Ingestion.GoogleDrive> gDrives, ProcessContext context) {
      gDrives.forEach(
          gDrive ->
              context.output(
                  googleContent,
                  new Types.Transport(gDrive.urlOrId(), context.element().getAttributeMap())));
      return Result.success(true);
    }

    static Result<Boolean, Exception> rawDataContent(
        Ingestion.RawData rawData, ProcessContext context) {
      return switch (rawData.mimeType()) {
        case TEXT -> {
          var content =
              new Types.Content(
                  rawData.id(),
                  List.of(new String(Base64.getDecoder().decode(rawData.data()))),
                  rawData.mimeType());
          context.output(rawContent, content);
          yield Result.success(true);
        }
        default -> Result.failure(notSupported(rawData.toString()));
      };
    }

    static Result<Boolean, Exception> referencesContent(
        List<Ingestion.Reference> references, ProcessContext context) {
      return references.stream()
          .map(
              ref ->
                  switch (ref.mimeType()) {
                    case PDF, PNG, JPEG, WEBP -> {
                      var content =
                          new Types.Content(ref.url(), List.of(ref.url()), ref.mimeType().toLink());
                      context.output(rawContent, content);
                      yield Result.<Boolean, Exception>success(true);
                    }
                    case VIDEO, VIDEO_LINK -> {
                      var content =
                          new Types.Content(ref.url(), List.of(ref.url()), ref.mimeType());
                      context.output(rawContent, content);
                      yield Result.<Boolean, Exception>success(true);
                    }
                    default -> Result.<Boolean, Exception>failure(notSupported(ref.toString()));
                  })
          .filter(Result::failed)
          .findAny()
          .orElse(Result.success(true));
    }
  }
}
