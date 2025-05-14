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
package com.google.cloud.pso.rag.content;

import java.util.List;

/** */
public class ChunksRequests {
  private ChunksRequests() {}

  public static Chunks.ChunkRequest create(
      String configurationEntry, Chunks.SupportedTypes type, String dataToChunk) {
    return create(configurationEntry, type, List.of(dataToChunk));
  }

  public static Chunks.ChunkRequest create(
      String configurationEntry, Chunks.SupportedTypes type, List<String> dataToChunk) {
    return switch (configurationEntry) {
      case "gemini-2.0-flash", "gemini-2.0-flash-lite" ->
          createGeminiRequest(configurationEntry, type, dataToChunk);
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for chunking request creation.",
                  configurationEntry));
    };
  }

  static Chunks.ChunkRequest createGeminiRequest(
      String configurationEntry, Chunks.SupportedTypes type, List<String> dataToChunk) {
    return switch (type) {
      case PDF -> new Gemini.PDFChunkRequest(configurationEntry, dataToChunk);
      case TEXT -> new Gemini.TextChunkRequest(configurationEntry, dataToChunk);
    };
  }
}
