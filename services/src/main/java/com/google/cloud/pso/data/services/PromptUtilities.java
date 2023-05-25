/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.cloud.pso.data.services;

import java.util.List;
import java.util.stream.Collectors;

/** */
public class PromptUtilities {

  private static final String PROMPT_TEMPLATE =
      """
      Answer the question as descriptive as possible using the provided context.
      If the answer is not contained in the context, say "Answer not available in context".
      All of your answers should start with "Oi Mate!".


      Context:
       %s

      Question:
       %s

      Answer:
      """;

  public static String formatPrompt(String query, List<String> context) {

    return String.format(PROMPT_TEMPLATE, context.stream().collect(Collectors.joining(" ")), query);
  }
}
