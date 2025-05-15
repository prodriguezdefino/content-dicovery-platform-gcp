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

import static com.google.cloud.pso.rag.common.InteractionHelper.jsonMapper;

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.common.Models;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import com.google.cloud.pso.rag.llm.LLM.Exchange;
import com.google.cloud.pso.rag.llm.LLM.Parameters;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** */
public class Gemini {

  private static final Content SYSTEM_SUMMARIZATION_INSTRUCTION =
      Content.fromParts(
          Part.fromText(
              "You are a succinct summarization bot, "
                  + "which focus strictly on the provided content."));

  sealed interface Chat extends LLM.ChatRequest permits ChatRequest {}

  sealed interface Summarize extends LLM.SummarizationRequest permits SummarizeRequest {}

  public record ChatRequest(String model, List<Exchange> exchanges, Parameters params)
      implements Chat {}

  public record SummarizeRequest(String model, List<String> content, Parameters params)
      implements Summarize {}

  public record ChatResponse(Exchange answer, Optional<String> blockReason)
      implements LLM.ChatResponse {}

  public record SummarizationResponse(String content) implements LLM.SummarizationResponse {}

  private Gemini() {}

  static String formatErrorWithResponse(String response) {
    return String.format("Error returned from the model, see response: %s", response);
  }

  static Optional<String> extractModelFeedback(GenerateContentResponse response) {
    return response
        .promptFeedback()
        .flatMap(
            feedback ->
                feedback
                    .blockReason()
                    .map(
                        reason ->
                            feedback
                                .blockReasonMessage()
                                .map(msg -> reason + " " + msg)
                                .orElse(reason)));
  }

  static Optional<Result<String, ErrorResponse>> extractResponse(GenerateContentResponse response) {
    return Optional.ofNullable(response.parts())
        .map(
            parts ->
                parts.stream()
                    .flatMap(part -> part.text().stream())
                    .collect(Collectors.joining("\n")))
        .map(
            content ->
                jsonMapper(content, String.class)
                    .failMap(
                        error ->
                            new ErrorResponse("Problems capturing response.", Optional.of(error))));
  }

  static Result<String, ErrorResponse> extractSummarization(GenerateContentResponse response) {
    return extractResponse(response)
        .orElseGet(() -> Result.failure(formatErrorWithResponse(response.toString())));
  }

  static Result<? extends LLM.SummarizationResponse, ErrorResponse> summarizeResponse(
      GenerateContentResponse generatedResponse) {
    return extractSummarization(generatedResponse).map(SummarizationResponse::new);
  }

  static Result<Exchange, ErrorResponse> extractExchange(GenerateContentResponse response) {
    return extractResponse(response)
        .map(model -> model.map(text -> new Exchange("model", text)))
        .orElseGet(() -> Result.failure(formatErrorWithResponse(response.toString())));
  }

  static Result<? extends LLM.ChatResponse, ErrorResponse> chattingResponse(
      GenerateContentResponse generatedResponse) {
    return extractExchange(generatedResponse)
        .map(exchange -> new ChatResponse(exchange, extractModelFeedback(generatedResponse)));
  }

  public static CompletableFuture<Result<? extends LLM.ChatResponse, ErrorResponse>> chat(
      ChatRequest request) {
    var gemini = Models.gemini(GCPEnvironment.config());
    return CompletableFuture.supplyAsync(
            () ->
                gemini
                    .chats
                    .create(request.model())
                    .sendMessage(
                        request.exchanges().stream()
                            .map(
                                exch ->
                                    Content.builder()
                                        .role(exch.author())
                                        .parts(List.of(Part.fromText(exch.content())))
                                        .build())
                            .toList(),
                        Models.setupParameters(
                                Models.DEFAULT_CONFIG,
                                request.params().topK(),
                                request.params().topP(),
                                request.params().temperature(),
                                request.params().maxOutputTokens())
                            .toBuilder()
                            .responseSchema(Models.STRING_SCHEMA)
                            .build()),
            InteractionHelper.EXEC)
        .thenApply(Gemini::chattingResponse)
        .exceptionally(error -> Result.failure("Error while generating chat request.", error));
  }

  public static CompletableFuture<Result<? extends LLM.SummarizationResponse, ErrorResponse>>
      summarize(SummarizeRequest request) {
    var gemini = Models.gemini(GCPEnvironment.config());
    return CompletableFuture.supplyAsync(
            () ->
                gemini.models.generateContent(
                    request.model(),
                    Content.builder()
                        .role("user")
                        .parts(
                            request.content().stream()
                                .filter(text -> !text.isBlank())
                                .map(Part::fromText)
                                .toList())
                        .build(),
                    Models.setupParameters(
                            Models.DEFAULT_CONFIG,
                            request.params().topK(),
                            request.params().topP(),
                            request.params().temperature(),
                            request.params().maxOutputTokens())
                        .toBuilder()
                        .systemInstruction(SYSTEM_SUMMARIZATION_INSTRUCTION)
                        .responseSchema(Models.STRING_SCHEMA)
                        .build()),
            InteractionHelper.EXEC)
        .thenApply(Gemini::summarizeResponse)
        .exceptionally(
            error -> Result.failure("Error while generating summarization request.", error));
  }
}
