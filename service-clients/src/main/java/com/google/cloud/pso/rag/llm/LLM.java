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
