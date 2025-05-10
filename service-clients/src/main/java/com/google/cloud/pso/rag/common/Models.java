package com.google.cloud.pso.rag.common;

import com.google.cloud.pso.rag.llm.LLM;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import java.util.List;

/** */
public class Models {

  public static final List<SafetySetting> SAFETY_SETTINGS =
      List.of(
          SafetySetting.builder()
              .category("HARM_CATEGORY_HATE_SPEECH")
              .threshold("BLOCK_ONLY_HIGH")
              .build(),
          SafetySetting.builder()
              .category("HARM_CATEGORY_DANGEROUS_CONTENT")
              .threshold("BLOCK_ONLY_HIGH")
              .build());

  public static final Schema STRING_ARRAY_SCHEMA =
      Schema.builder().type("array").items(Schema.builder().type("string").build()).build();

  public static final Schema STRING_SCHEMA = Schema.builder().type("string").build();

  public static final GenerateContentConfig DEFAULT_CONFIG =
      GenerateContentConfig.builder()
          .responseMimeType("application/json")
          .candidateCount(1)
          .safetySettings(SAFETY_SETTINGS)
          .build();

  private static Client GEMINI = null;

  public static Client gemini(GCPEnvironment.Config config) {
    if (GEMINI == null) {
      synchronized (Models.class) {
        if (GEMINI == null) {
          GEMINI =
              Client.builder()
                  .project(config.project())
                  .location(config.region())
                  .credentials(
                      GoogleCredentialsCache.credentials(
                          config.serviceAccountEmailSupplier().get()))
                  .vertexAI(true)
                  .build();
        }
      }
    }
    return GEMINI;
  }

  public static GenerateContentConfig setupParameters(
      GenerateContentConfig config,
      Integer topK,
      Double topP,
      Double temperature,
      Integer maxOutputTokens) {
    return config.toBuilder()
        .topK(topK.floatValue())
        .topP(topP.floatValue())
        .temperature(temperature.floatValue())
        .maxOutputTokens(maxOutputTokens)
        .build();
  }
}
