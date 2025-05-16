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
package com.google.cloud.pso.data.services.beans;

import com.google.cloud.pso.data.services.utils.PromptUtilities;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import com.google.cloud.pso.rag.embeddings.Embeddings;
import com.google.cloud.pso.rag.embeddings.EmbeddingsRequests;
import com.google.cloud.pso.rag.llm.LLM;
import com.google.cloud.pso.rag.llm.LLMRequests;
import com.google.cloud.pso.rag.vector.VectorRequests;
import com.google.cloud.pso.rag.vector.Vectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;

/** */
@ApplicationScoped
public class VertexAIService {

  @Inject ServiceTypes.ResourceConfiguration configuration;
  @Inject BeansProducer.Interactions interactions;

  @Timed(name = "palm.exchanges.summarization", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<Result<? extends LLM.SummarizationResponse, ErrorResponse>>
      retrievePreviousSummarizedConversation(List<ServiceTypes.QAndA> qsAndAs) {
    return LLM.summarize(
        LLMRequests.summarize(
            interactions.llm(),
            PromptUtilities.formatChatSummaryPrompt(
                qsAndAs.stream().flatMap(q -> q.toExchange().stream()).toList()),
            new LLM.Parameters(
                configuration.temperature(),
                configuration.maxOutputTokens(),
                configuration.topK(),
                configuration.topP())));
  }

  @Timed(name = "palm.chat.prediction", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<Result<? extends LLM.ChatResponse, ErrorResponse>> retrieveChatResponse(
      List<ServiceTypes.QAndA> lastsQAndAs, ServiceTypes.UserQuery query, String context) {

    var exchanges =
        Stream.concat(
                lastsQAndAs.stream()
                    .flatMap(
                        qaa ->
                            Stream.of(
                                new LLM.Exchange("user", qaa.question()),
                                new LLM.Exchange("model", qaa.answer()))),
                Stream.of(new LLM.Exchange("user", query.text())))
            .toList();

    return LLM.chat(
        LLMRequests.chat(
            interactions.llm(),
            context,
            exchanges,
            llmParameters(Optional.ofNullable(query.parameters()))));
  }

  @Timed(name = "embeddings.prediction", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<Result<? extends Embeddings.Response, ErrorResponse>> retrieveEmbeddings(
      ServiceTypes.UserQuery query, String previousSummarizedConversation) {
    return Embeddings.retrieveEmbeddings(
        EmbeddingsRequests.create(
            interactions.embeddingsModel(), Embeddings.Types.TEXT, List.of(query.text())));
  }

  @Timed(name = "vectorseach.ann", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<Result<? extends Vectors.SearchResponse, ErrorResponse>>
      retrieveNearestNeighbors(Embeddings.Response embResponse, ServiceTypes.UserQuery query) {

    return Vectors.findNearestNeighbors(
        VectorRequests.find(
            interactions.vectorStorage(),
            Embeddings.extractValuesFromEmbeddings(embResponse).stream()
                .map(VectorRequests.Vector::new)
                .toList(),
            // use min value between statically configured and the request one (if exists)
            Integer.min(
                configuration.maxNeighbors(),
                Optional.ofNullable(query.parameters())
                    .flatMap(params -> Optional.ofNullable(params.maxNeighbors()))
                    .orElse(Integer.MAX_VALUE))));
  }

  LLM.Parameters llmParameters(Optional<ServiceTypes.QueryParameters> parameters) {
    return new LLM.Parameters(
        parameters.map(p -> p.temperature()).orElse(configuration.temperature()),
        parameters.map(p -> p.maxOutputTokens()).orElse(configuration.maxOutputTokens()),
        parameters.map(p -> p.topK()).orElse(configuration.topK()),
        parameters.map(p -> p.topP()).orElse(configuration.topP()));
  }
}
