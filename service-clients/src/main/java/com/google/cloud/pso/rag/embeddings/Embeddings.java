package com.google.cloud.pso.rag.embeddings;

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

  record ErrorResponse(String cause) implements Response {}

  static CompletableFuture<Response> retrieveEmbeddings(Request request) {
    return switch (request) {
      case VertexAi.Request vertexRequest -> VertexAi.retrieveEmbeddings(vertexRequest);
      default -> throw new RuntimeException("Embeddings request type not implemented.");
    };
  }
}
