package com.google.cloud.pso.rag.embeddings;

/** */
public class EmbeddingsException extends RuntimeException {

  public EmbeddingsException(String message, Throwable cause) {
    super(message, cause);
  }

  public EmbeddingsException(String message) {
    super(message);
  }
}
