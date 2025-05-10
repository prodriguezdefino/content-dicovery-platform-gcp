package com.google.cloud.pso.rag.llm;

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.common.Models;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.Failure;
import com.google.cloud.pso.rag.common.Result.Success;
import com.google.cloud.pso.rag.llm.LLM.ErrorResponse;
import com.google.cloud.pso.rag.llm.LLM.Parameters;
import com.google.cloud.pso.rag.llm.LLM.Exchange;
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

  public sealed interface ChatResponse extends LLM.ChatResponse
      permits ErrorResponse, ChattingResponse {}

  public sealed interface SummarizeResponse extends LLM.SummarizationResponse
      permits ErrorResponse, SummarizationResponse {}

  public record ChattingResponse(Exchange answer, Optional<String> blockReason)
      implements ChatResponse {}

  public record SummarizationResponse(String content) implements SummarizeResponse {}

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

  static Optional<String> extractResponse(GenerateContentResponse response) {
    return Optional.ofNullable(response.parts())
        .map(
            parts ->
                parts.stream()
                    .flatMap(part -> part.text().stream())
                    .collect(Collectors.joining("\n")));
  }

  static Result<String, String> extractSummarization(GenerateContentResponse response) {
    return extractResponse(response)
        .map(content -> Result.<String, String>success(content))
        .orElseGet(() -> Result.<String, String>failure(response.toString()));
  }

  static SummarizeResponse summarizeResponse(GenerateContentResponse generatedResponse) {
    return switch (extractSummarization(generatedResponse)) {
      case Success<String, ?>(var content) -> new SummarizationResponse(content);
      case Failure<?, String>(var textResponse) ->
          new ErrorResponse(formatErrorWithResponse(textResponse));
    };
  }

  static Result<Exchange, String> extractExchange(GenerateContentResponse response) {
    return extractResponse(response)
        .map(text -> new Exchange("model", text))
        .map(exch -> Result.<Exchange, String>success(exch))
        .orElseGet(() -> Result.<Exchange, String>failure(response.toString()));
  }

  static ChatResponse chattingResponse(GenerateContentResponse generatedResponse) {
    return switch (extractExchange(generatedResponse)) {
      case Success<Exchange, ?>(var exchange) ->
          new ChattingResponse(exchange, extractModelFeedback(generatedResponse));
      case Failure<?, String>(var jsonResponse) ->
          new ErrorResponse(formatErrorWithResponse(jsonResponse));
    };
  }

  public static CompletableFuture<ChatResponse> chat(ChatRequest request) {
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
        .exceptionally(
            error -> new ErrorResponse("Error while generating chat request.", Optional.of(error)));
  }

  public static CompletableFuture<SummarizeResponse> summarize(SummarizeRequest request) {
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
            error ->
                new ErrorResponse(
                    "Error while generating summarization request.", Optional.of(error)));
  }
}
