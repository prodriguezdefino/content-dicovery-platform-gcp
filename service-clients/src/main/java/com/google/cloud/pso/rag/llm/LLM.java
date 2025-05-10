package com.google.cloud.pso.rag.llm;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** */
public interface LLM {

  sealed interface ChatRequest permits Gemini.Chat {}

  sealed interface SummarizationRequest permits Gemini.Summarize {}

  sealed interface ChatResponse permits Gemini.ChatResponse {}

  sealed interface SummarizationResponse permits Gemini.SummarizeResponse {}

  record Exchange(String author, String content) {}

  public record Parameters(
      Double temperature, Integer maxOutputTokens, Integer topK, Double topP) {}

  record ErrorResponse(String message, Optional<Throwable> cause)
      implements Gemini.ChatResponse, Gemini.SummarizeResponse {
    public ErrorResponse(String message) {
      this(message, Optional.empty());
    }
  }

  static CompletableFuture<? extends ChatResponse> chat(ChatRequest request) {
    return switch (request) {
      case Gemini.ChatRequest geminiRequest -> Gemini.chat(geminiRequest);
    };
  }

  static CompletableFuture<? extends SummarizationResponse> summarize(
      SummarizationRequest request) {
    return switch (request) {
      case Gemini.SummarizeRequest geminiRequest -> Gemini.summarize(geminiRequest);
    };
  }
}
