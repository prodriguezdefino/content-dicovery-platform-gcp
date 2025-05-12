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
import com.google.cloud.pso.beam.contentextract.Types.ContentChunks;
import com.google.cloud.pso.beam.contentextract.Types.IndexableContent;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import com.google.cloud.pso.rag.embeddings.Embeddings;
import com.google.cloud.pso.rag.embeddings.EmbeddingsRequests;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Reshuffle;
import org.apache.beam.sdk.values.PCollection;

/** */
public class ProcessEmbeddings
    extends PTransform<PCollection<ContentChunks>, PCollection<List<IndexableContent>>> {

  public static ProcessEmbeddings create() {
    return new ProcessEmbeddings();
  }

  @Override
  public PCollection<List<IndexableContent>> expand(PCollection<ContentChunks> input) {
    var options = input.getPipeline().getOptions();
    var embeddingsConfig = options.as(ContentExtractionOptions.class).getEmbeddingsConfiguration();
    return input
        .apply("StableChunks", Reshuffle.viaRandomKey())
        .apply("Embeddings", ParDo.of(new EmbeddingsRetriever(embeddingsConfig)));
  }

  static class EmbeddingsRetriever extends DoFn<ContentChunks, List<IndexableContent>> {

    private final String embeddingsConfig;

    public EmbeddingsRetriever(String embeddingsConfig) {
      this.embeddingsConfig = embeddingsConfig;
    }

    @ProcessElement
    public void process(
        @Element ContentChunks content, OutputReceiver<List<IndexableContent>> receiver) {
      Embeddings.retrieveEmbeddings(
              EmbeddingsRequests.create(embeddingsConfig, Embeddings.Types.TEXT, content.chunks()))
          .thenApply(
              response ->
                  response
                      .map(text -> Embeddings.extractValuesFromEmbeddings(text))
                      .orElseThrow(
                          error -> new RuntimeException(error.message(), error.cause().get())))
          .thenApply(
              embValues ->
                  IntStream.range(0, content.chunks().size())
                      .mapToObj(
                          idx ->
                              new IndexableContent(
                                  content.key() + Utilities.CONTENT_KEY_SEPARATOR + idx,
                                  content.chunks().get(idx),
                                  embValues.get(idx)))
                      .toList())
          .thenAccept(receiver::output);
    }
  }
}
