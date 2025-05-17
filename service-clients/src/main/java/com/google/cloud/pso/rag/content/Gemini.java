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

import static com.google.cloud.pso.rag.common.InteractionHelper.jsonMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.Ingestion;
import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.common.Models;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import com.google.cloud.pso.rag.common.Result.Failure;
import com.google.cloud.pso.rag.common.Result.Success;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** */
public class Gemini {

  static final String CHUNKING_INSTRUCTIONS =
      """
      Your definitions chunks creations are:
      1. Aim to generate chunks with a size ranging between 200, when possible, and 2048 tokens as maximum.
      2. Each chunk should focus on a single topic or idea, unless they are too short.
      3. If possible, end chunks at sentence boundaries (periods, question marks, exclamation points).
      4. Try to avoid breaking up important phrases or names.
      5. Include some chunk overlap, enough words to capture the ideas at least.
      6. If a topic spans multiple sentences and exceeds the character limit,
      break the chunk at the most logical word boundary to maintain semantic coherence as much as possible.
      Your response should be formatted as a non formatted JSON string array, each array item being the chunked text.
      --
      """;
  static final String TEXT_BASED_INSTRUCTIONS =
      """
      extract all the text from it, remove all newline characters and empty lines replacing them
      with a single space character, and divide it into chunks which will be used to generate text embeddings.
      """;
  static final String TEXT_CHUNKING_PROMPT =
      "Analyze the following text, " + TEXT_BASED_INSTRUCTIONS + CHUNKING_INSTRUCTIONS;
  static final String PDF_CHUNKING_PROMPT =
      "Analyze the provided PDF, " + TEXT_BASED_INSTRUCTIONS + CHUNKING_INSTRUCTIONS;
  static final String IMAGE_CHUNKING_PROMPT =
      """
      Analyze the provided image and generate a non-formatted description of what is shown in it with a deep level of detail.
      Then, use that description and divide it into chunks which will be used to generate text embeddings.
      """
          + CHUNKING_INSTRUCTIONS;

  private static final Content SYSTEM_INSTRUCTION =
      Content.fromParts(
          Part.fromText("You are an efficient data chunker used to generate text embeddings."));

  private Gemini() {}

  public sealed interface ChunkRequest extends Chunks.ChunkRequest
      permits TextChunkRequest, PDFChunkRequest, ImageChunkRequest {}

  public sealed interface ChunkResponse extends Chunks.ChunkResponse permits TextChunkResponse {}

  public record TextChunkRequest(String model, List<String> content) implements ChunkRequest {}

  public record PDFChunkRequest(String model, List<String> contents, Ingestion.SupportedType type)
      implements ChunkRequest {}

  public record ImageChunkRequest(String model, List<String> contents, Ingestion.SupportedType type)
      implements ChunkRequest {}

  public record TextChunkResponse(List<String> chunks) implements ChunkResponse {}

  static Part partFromType(Ingestion.SupportedType type, String content) {
    return switch (type) {
      case PDF, JPEG, PNG, WEBP -> Part.fromBytes(content.getBytes(), type.mimeType());
      case PDF_LINK -> Part.fromUri(content, Ingestion.SupportedType.PDF.mimeType());
      case TEXT -> Part.fromText(content);
      case JPEG_LINK -> Part.fromUri(content, Ingestion.SupportedType.JPEG.mimeType());
      case PNG_LINK -> Part.fromUri(content, Ingestion.SupportedType.PNG.mimeType());
      case WEBP_LINK -> Part.fromUri(content, Ingestion.SupportedType.WEBP.mimeType());
      default -> throw new IllegalArgumentException("Type not supported: " + type);
    };
  }

  static Result<? extends Chunks.ChunkResponse, ErrorResponse> response(
      GenerateContentResponse generatedResponse) {
    return switch (jsonMapper(
        generatedResponse.text(), new TypeReference<ArrayList<String>>() {})) {
      case Success<ArrayList<String>, ?>(var chunks) ->
          Result.success(new TextChunkResponse(chunks));
      case Failure<?, Exception>(var ex) ->
          Result.failure("Error while parsing response from model.", ex);
    };
  }

  static CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>> internalExec(
      String model, List<Part> parts) {
    return internalExec(model, parts, null);
  }

  static CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>> internalExec(
      String model, List<Part> parts, GenerateContentConfig config) {
    var gemini = Models.gemini(GCPEnvironment.config());
    var safeConfig =
        Optional.ofNullable(config)
            .orElse(
                Models.DEFAULT_CONFIG.toBuilder()
                    .systemInstruction(SYSTEM_INSTRUCTION)
                    .responseSchema(Models.STRING_ARRAY_SCHEMA)
                    .build());
    return CompletableFuture.supplyAsync(
            () ->
                gemini.models.generateContent(
                    model, Content.builder().role("user").parts(parts).build(), safeConfig),
            InteractionHelper.EXEC)
        .thenApply(Gemini::response)
        .exceptionally(error -> Result.failure("Error while generating chunks.", error));
  }

  public static CompletableFuture<Result<? extends Chunks.ChunkResponse, ErrorResponse>>
      extractChunks(ChunkRequest request) {
    return switch (request) {
      case TextChunkRequest(var model, var content) ->
          internalExec(
              model,
              Stream.concat(Stream.of(TEXT_CHUNKING_PROMPT), content.stream())
                  .filter(text -> !text.isBlank())
                  .map(Part::fromText)
                  .toList());
      case PDFChunkRequest(var model, var contents, var type) ->
          internalExec(
              model,
              Stream.concat(
                      contents.stream().map(item -> partFromType(type, item)),
                      Stream.of(Part.fromText(PDF_CHUNKING_PROMPT)))
                  .toList());
      case ImageChunkRequest(var model, var contents, var type) ->
          internalExec(
              model,
              Stream.concat(
                      contents.stream().map(item -> partFromType(type, item)),
                      Stream.of(Part.fromText(IMAGE_CHUNKING_PROMPT)))
                  .toList());
    };
  }
}
