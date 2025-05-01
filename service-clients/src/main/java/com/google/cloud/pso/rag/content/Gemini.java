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

import static com.google.cloud.pso.rag.common.HttpInteractionHelper.jsonMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.GoogleCredentialsCache;
import com.google.cloud.pso.rag.common.HttpInteractionHelper.Error;
import com.google.cloud.pso.rag.common.HttpInteractionHelper.Json;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** */
public class Gemini {

  static final String TEXT_CHUNKING_PROMPT =
      """
      Analyze the following text and divide it into chunks for generating text embeddings.
      Your goal is to create chunks that are:
      1. Each chunk should focus on a single topic or idea.
      2. Aim for a maximum length of 2048 tokens per chunk.
      3. If possible, end chunks at sentence boundaries (periods, question marks, exclamation points).
      4. Try to avoid breaking up important phrases or names.
      5. Include some chunk overlap
      If a topic spans multiple sentences and exceeds the character limit, break the chunk at the most logical word boundary to maintain semantic coherence as much as possible.
      --
      """;

  private static final Content SYSTEM_INSTRUCTION =
      Content.fromParts(
          Part.fromText("You are an efficient data chunker used to generate text embeddings."));

  private static final List<SafetySetting> SAFETY_SETTINGS =
      List.of(
          SafetySetting.builder()
              .category("HARM_CATEGORY_HATE_SPEECH")
              .threshold("BLOCK_ONLY_HIGH")
              .build(),
          SafetySetting.builder()
              .category("HARM_CATEGORY_DANGEROUS_CONTENT")
              .threshold("BLOCK_ONLY_HIGH")
              .build());

  private static final GenerateContentConfig DEFAULT_CONFIG =
      GenerateContentConfig.builder()
          .responseMimeType("application/json")
          .responseSchema(
              Schema.builder().type("array").items(Schema.builder().type("string").build()).build())
          .candidateCount(1)
          .safetySettings(SAFETY_SETTINGS)
          .systemInstruction(SYSTEM_INSTRUCTION)
          .build();

  private Gemini() {}

  public sealed interface ChunkRequest extends Chunks.ChunkRequest permits TextChunkRequest {}

  public sealed interface ChunkResponse extends Chunks.ChunkResponse
      permits Chunks.ErrorResponse, TextChunkResponse {}

  public record TextChunkRequest(String model, List<String> content) implements ChunkRequest {}

  public record TextChunkResponse(List<String> chunks) implements ChunkResponse {}

  static ChunkResponse response(GenerateContentResponse generatedResponse) {
    return switch (jsonMapper(
        generatedResponse.text(), new TypeReference<ArrayList<String>>() {})) {
      case Json<ArrayList<String>> response -> new TextChunkResponse(response.value());
      case Error error ->
          new Chunks.ErrorResponse(
              "Error while parsing response from model.", Optional.of(error.error()));
    };
  }

  public static CompletableFuture<ChunkResponse> extractChunks(ChunkRequest request) {
    var envConfig = GCPEnvironment.config();
    var gemini =
        Client.builder()
            .project(envConfig.project())
            .location(envConfig.region())
            .credentials(
                GoogleCredentialsCache.credentials(envConfig.serviceAccountEmailSupplier().get()))
            .vertexAI(true)
            .build();

    return switch (request) {
      case TextChunkRequest(var model, var content) ->
          CompletableFuture.supplyAsync(
                  () ->
                      gemini.models.generateContent(
                          model,
                          Content.builder()
                              .role("user")
                              .parts(
                                  Stream.concat(Stream.of(TEXT_CHUNKING_PROMPT), content.stream())
                                      .filter(text -> !text.isBlank())
                                      .map(Part::fromText)
                                      .toList())
                              .build(),
                          DEFAULT_CONFIG))
              .thenApply(Gemini::response)
              .exceptionally(
                  error ->
                      new Chunks.ErrorResponse(
                          "Error while generating chunks.", Optional.of(error)));
    };
  }
}
