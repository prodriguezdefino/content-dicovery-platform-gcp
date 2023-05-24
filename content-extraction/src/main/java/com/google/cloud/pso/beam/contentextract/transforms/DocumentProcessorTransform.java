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

import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.beam.contentextract.Types;
import com.google.cloud.pso.beam.contentextract.transforms.DocumentProcessorTransform.DocumentProcessingResult;
import com.google.cloud.pso.beam.contentextract.utils.DocContentRetriever;
import com.google.cloud.pso.beam.contentextract.utils.GoogleDocClient;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import java.util.List;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PInput;
import org.apache.beam.sdk.values.POutput;
import org.apache.beam.sdk.values.PValue;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;

/** */
public class DocumentProcessorTransform
    extends PTransform<PCollection<PubsubMessage>, DocumentProcessingResult> {

  public static DocumentProcessorTransform create() {
    return new DocumentProcessorTransform();
  }

  @Override
  public DocumentProcessingResult expand(PCollection<PubsubMessage> input) {
    var options = input.getPipeline().getOptions().as(ContentExtractionOptions.class);
    var fetcher = DocContentRetriever.create(GoogleDocClient.create(options.getSecretManagerId()));

    var maybeUrls =
        input.apply(
            "ExtractContentId",
            FlatMapElements.into(TypeDescriptor.of(Types.Transport.class))
                .via(Utilities::extractContentId)
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
                              .map(file -> new Types.Transport(file.getId(), t.metadata()))
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
                    .via((Types.Transport t) -> fetcher.retrieveDocumentContent(t.contentId()))
                    .exceptionsVia(new ErrorHandlingTransform.ErrorHandler<>()));

    return DocumentProcessingResult.of(
        input.getPipeline(),
        maybeDocContents.output(),
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
}
