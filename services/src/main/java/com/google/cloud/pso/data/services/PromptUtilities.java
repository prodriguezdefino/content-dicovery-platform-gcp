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

  public static final String NEGATIVE_ANSWER_1 = "Answer is not available in current content base.";
  public static final List<String> NEGATIVE_ANSWERS =
      List.of(NEGATIVE_ANSWER_1, "I'm not sure", "I am not sure");
  public static final String FOUND_IN_INTERNET =
      "I found the following information on the internet";

  private static final String PROMPT_TEMPLATE =
      """
      Answer the question as descriptive as possible using only the information on the provided Context.
      If a possible Answer is not contained in the provided Context, answer "%s".
      Do not use information outside of the context, unless the Question requests for it.
      All of your answers should start with "Hey M8! ".


      Context:
       %s

      Question:
       %s

      Answer:
      """;

  public static String formatPrompt(String query, List<String> context) {

    return String.format(
        PROMPT_TEMPLATE,
        NEGATIVE_ANSWER_1,
        context.stream().collect(Collectors.joining(" ")),
        query);
  }

  public static Boolean checkNegativeAnswer(String answer) {
    return NEGATIVE_ANSWERS.stream().filter(nans -> answer.contains(nans)).findAny().isPresent();
  }
}
