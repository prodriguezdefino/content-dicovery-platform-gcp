package com.google.cloud.pso.data.query.service;

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

    return String.format(
        PROMPT_TEMPLATE, context.stream().collect(Collectors.joining(" ")), query);
  }
}
