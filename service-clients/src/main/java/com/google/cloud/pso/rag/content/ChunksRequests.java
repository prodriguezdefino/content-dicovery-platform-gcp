package com.google.cloud.pso.rag.content;

import java.util.List;

/** */
public class ChunksRequests {
  private ChunksRequests() {}

  public static Chunks.ChunkRequest create(String configurationEntry, List<String> dataToChunk) {
    return switch (configurationEntry) {
      case "gemini-2.0-flash", "gemini-2.0-flash-lite-001" ->
          new Gemini.TextChunkRequest(configurationEntry, dataToChunk);
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for chunking request creation.",
                  configurationEntry));
    };
  }
}
