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
package com.google.cloud.pso.rag.embeddings;

import java.util.List;
import java.util.Optional;

/** */
public class EmbeddingsRequests {

  private EmbeddingsRequests() {}

  public static Embeddings.Request create(
      String configurationEntry, Embeddings.Types type, List<String> dataToEmbed) {
    return switch (type) {
      case TEXT -> textEmbeddings(configurationEntry, dataToEmbed);
      case IMAGE_LINK, IMAGE_RAW -> imageEmbeddings(configurationEntry, type, dataToEmbed);
      case VIDEO_LINK, VIDEO_RAW -> videoEmbeddings(configurationEntry, type, dataToEmbed);
    };
  }

  static Embeddings.Request textEmbeddings(String configurationEntry, List<String> dataToEmbed) {
    return switch (configurationEntry) {
      case "text-embedding-005", "text-embedding-004" ->
          new VertexAi.Text(
              configurationEntry, dataToEmbed.stream().map(VertexAi.TextInstance::new).toList());
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for text embedding requests.",
                  configurationEntry));
    };
  }

  static Embeddings.Request imageEmbeddings(
      String configurationEntry, Embeddings.Types type, List<String> dataToEmbed) {
    return switch (configurationEntry) {
      case "multimodalembedding@001" ->
          new VertexAi.Multimodal(
              configurationEntry,
              dataToEmbed.stream()
                  .map(
                      data ->
                          switch (type) {
                            case IMAGE_LINK ->
                                new VertexAi.ImageData(
                                    Optional.empty(), Optional.of(data), Optional.empty());
                            case IMAGE_RAW ->
                                new VertexAi.ImageData(
                                    Optional.of(data), Optional.empty(), Optional.empty());
                            default ->
                                throw new IllegalArgumentException("Image data not supported.");
                          })
                  .map(VertexAi.MultimodalInstance::new)
                  .toList());
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry %s not supported for text embedding requests.",
                  configurationEntry));
    };
  }

  static Embeddings.Request videoEmbeddings(
      String configurationEntry, Embeddings.Types type, List<String> dataToEmbed) {
    return switch (configurationEntry) {
      case "multimodalembedding@001" ->
          new VertexAi.Multimodal(
              configurationEntry,
              dataToEmbed.stream()
                  .map(
                      data ->
                          switch (type) {
                            case VIDEO_LINK ->
                                new VertexAi.VideoData(
                                    Optional.empty(), Optional.of(data), Optional.empty());
                            case VIDEO_RAW ->
                                new VertexAi.VideoData(
                                    Optional.of(data), Optional.empty(), Optional.empty());
                            default ->
                                throw new IllegalArgumentException("Image data not supported.");
                          })
                  .map(VertexAi.MultimodalInstance::new)
                  .toList());
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry %s not supported for text embedding requests.",
                  configurationEntry));
    };
  }
}
