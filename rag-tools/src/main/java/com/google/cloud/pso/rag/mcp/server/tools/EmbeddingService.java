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
package com.google.cloud.pso.rag.mcp.server.tools;

import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.embeddings.Embeddings;
import com.google.cloud.pso.rag.embeddings.EmbeddingsRequests;
import com.google.cloud.pso.rag.mcp.beans.BeansProducer;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

  private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);

  @Inject BeansProducer.Interactions interactions;

  @Tool(description = "Gets vector embedding for a give text")
  public Embeddings.Response getEmbedding(String query) {
    CompletableFuture<Embeddings.Response> futureResponse =
        Embeddings.retrieveEmbeddings(
                EmbeddingsRequests.create(
                    interactions.embeddingsModel(), Embeddings.Types.TEXT, List.of(query)))
            .thenApply(
                result ->
                    switch (result) {
                      case Result.Success(var embResponse) -> embResponse;
                      case Result.Failure(var f) ->
                          throw new RuntimeException(
                              "Failed to get embedding: " + f.message(), f.cause().orElse(null));
                    });
    return futureResponse.join();
  }
}
