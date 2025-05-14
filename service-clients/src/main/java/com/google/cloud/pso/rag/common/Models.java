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
package com.google.cloud.pso.rag.common;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import java.util.List;

/** */
public class Models {

  public static final String PDF_MIME = "application/pdf";
  public static final String JSON_MIME = "application/json";
  public static final String TEXT_MIME = "text/plain";

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
          .responseMimeType(JSON_MIME)
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
