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
import java.util.concurrent.CompletableFuture;

/** */
public interface Embeddings {

  sealed interface Parameters permits VertexAi.Parameters {}

  sealed interface Request permits VertexAi.Request {}

  sealed interface Response permits ErrorResponse, ValuesResponse {}

  sealed interface ValuesResponse extends Response permits VertexAi.Response {

    ResponseMetadata metadata();
  }

  sealed interface ResponseMetadata permits VertexAi.ResponseMetadata {}

  record ErrorResponse(String message, Optional<Throwable> cause) implements Response {
    public ErrorResponse(String message) {
      this(message, Optional.empty());
    }
  }

  static CompletableFuture<Response> retrieveEmbeddings(Request request) {
    return switch (request) {
      case VertexAi.Request vertexRequest -> VertexAi.retrieveEmbeddings(vertexRequest);
    };
  }

  static List<List<Double>> extractValuesFromEmbeddings(Embeddings.Response embResponse) {
    return switch (embResponse) {
      case VertexAi.TextResponse(var predictions) ->
          predictions.stream().map(emb -> emb.embeddings().values()).toList();
      case VertexAi.MultimodalResponse(var predictions) ->
          predictions.stream().flatMap(mmEmb -> mmEmb.textEmbedding().stream()).toList();
      case ErrorResponse(var message, var cause) ->
          throw cause
              .map(ex -> new RuntimeException(message, ex))
              .orElse(new RuntimeException(message));
    };
  }
}
