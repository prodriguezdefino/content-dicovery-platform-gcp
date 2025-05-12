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

import com.google.cloud.pso.beam.contentextract.Types.Content;
import com.google.cloud.pso.beam.contentextract.Types.ContentChunks;
import com.google.cloud.pso.rag.content.Chunks;
import com.google.cloud.pso.rag.content.Gemini;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Reshuffle;
import org.apache.beam.sdk.values.PCollection;

/** */
public class ContentChunker extends PTransform<PCollection<Content>, PCollection<ContentChunks>> {

  public static ContentChunker create() {
    return new ContentChunker();
  }

  @Override
  public PCollection<ContentChunks> expand(PCollection<Content> input) {
    return input
        .apply("StableContent", Reshuffle.viaRandomKey())
        .apply("Chunk", ParDo.of(new ChunkContent()));
  }

  static class ChunkContent extends DoFn<Content, ContentChunks> {

    @ProcessElement
    public void process(@Element Content content, OutputReceiver<ContentChunks> receiver) {
      Chunks.chunk(new Gemini.TextChunkRequest("gemini-2.0-flash", content.content()))
          .thenApply(
              response ->
                  response
                      .map(resp -> new ContentChunks(content.key(), resp.chunks()))
                      .orElseThrow(
                          error ->
                              error
                                  .cause()
                                  .map(t -> new RuntimeException(error.message(), t))
                                  .orElseGet(() -> new RuntimeException(error.message()))))
          .thenAccept(receiver::output);
    }
  }
}
