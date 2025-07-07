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
import com.google.cloud.pso.rag.embeddings.VertexAi;
import com.google.cloud.pso.rag.mcp.beans.BeansProducer;
import com.google.cloud.pso.rag.mcp.beans.BigTableService;
import com.google.cloud.pso.rag.mcp.beans.ServiceTypes;
import com.google.cloud.pso.rag.vector.VectorRequests;
import com.google.cloud.pso.rag.vector.Vectors;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class NearestNeighborsService {

  private static final Logger LOG = LoggerFactory.getLogger(NearestNeighborsService.class);

  @Inject BeansProducer.Interactions interactions;
  @Inject ServiceTypes.ResourceConfiguration configuration;
  @Inject BigTableService btService;

  @Tool(
      description =
          "Get nearest neighbors from vector db based on embeddings. The response contains several ids, "
              + "each id represents and individual file that has content relevant to the users question")
  public Vectors.SearchResponse getNearestNeighbors(List<Double> embeddingValues) {
    Embeddings.Response embResponse =
        new VertexAi.TextResponse(
            List.of(
                new VertexAi.TextPrediction(new VertexAi.TextEmbeddings(null, embeddingValues))));
    CompletableFuture<Vectors.SearchResponse> futureResponse =
        Vectors.findNearestNeighbors(
                VectorRequests.find(
                    interactions.vectorStorage(),
                    Embeddings.extractValuesFromEmbeddings(embResponse).stream()
                        .map(VectorRequests.Vector::new)
                        .toList(),
                    configuration.maxNeighbors()))
            .thenApply(
                result ->
                    switch (result) {
                      case Result.Success(var searchRes) -> searchRes;

                      case Result.Failure(var f) ->
                          throw new RuntimeException(
                              "Failed to get nearest neighbors: " + f.message(),
                              f.cause().orElse(null));
                    });
    return futureResponse.join();
  }

  @Tool(description = "Get content of nearest neighbor for a single id")
  public String getContent(String id) {
    return btService.queryByPrefix(id).content();
  }
}
