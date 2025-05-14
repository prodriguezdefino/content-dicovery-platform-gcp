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
import com.google.cloud.pso.beam.contentextract.Types.Content;
import com.google.cloud.pso.beam.contentextract.Types.ContentChunks;
import com.google.cloud.pso.rag.content.Chunks;
import com.google.cloud.pso.rag.content.ChunksRequests;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Reshuffle;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class ContentChunker extends PTransform<PCollection<Content>, PCollection<ContentChunks>> {

  public static ContentChunker create() {
    return new ContentChunker();
  }

  @Override
  public PCollection<ContentChunks> expand(PCollection<Content> input) {
    var options = input.getPipeline().getOptions();
    var chunkerConfig = options.as(ContentExtractionOptions.class).getChunkerConfiguration();
    return input
        .apply("StableContent", Reshuffle.viaRandomKey())
        .apply("Chunk", ParDo.of(new ChunkContent(chunkerConfig)));
  }

  static class ChunkContent extends DoFn<Content, ContentChunks> {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkContent.class);

    private final String chunkerConfig;

    public ChunkContent(String chunkerConfig) {
      this.chunkerConfig = chunkerConfig;
    }

    @ProcessElement
    public void process(@Element Content content, OutputReceiver<ContentChunks> receiver) {
      var chunkResult =
          Chunks.chunk(
                  ChunksRequests.create(
                      chunkerConfig, Chunks.SupportedTypes.TEXT, content.content()))
              .join();
      var chunks =
          chunkResult
              .map(resp -> new ContentChunks(content.key(), resp.chunks()))
              .orElseThrow(error -> new RuntimeException(error.message(), error.cause().get()));
      LOG.info("processed chunks size: {}", chunks, chunks.chunks().size());
      receiver.output(chunks);
    }
  }
}
