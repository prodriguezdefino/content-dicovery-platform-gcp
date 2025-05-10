package com.google.cloud.pso.rag.llm;

import java.util.List;

/** */
public class LLMRequests {

  private LLMRequests() {}

  public static LLM.ChatRequest chat(
      String configurationEntry, List<LLM.Exchange> interactions, LLM.Parameters params) {
    return switch (configurationEntry) {
      case "gemini-2.0-flash", "gemini-2.0-flash-lite-001" ->
          new Gemini.ChatRequest(configurationEntry, interactions, params);
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for LLM chat requests.",
                  configurationEntry));
    };
  }

  public static LLM.SummarizationRequest summarize(
      String configurationEntry, List<String> contents, LLM.Parameters params) {
    return switch (configurationEntry) {
      case "gemini-2.0-flash", "gemini-2.0-flash-lite-001" ->
          new Gemini.SummarizeRequest(configurationEntry, contents, params);
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for LLM summarization requests.",
                  configurationEntry));
    };
  }
}
