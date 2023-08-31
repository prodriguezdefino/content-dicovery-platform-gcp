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
package com.google.cloud.pso.data.services.beans;

import com.google.cloud.pso.beam.contentextract.clients.EmbeddingsClient;
import com.google.cloud.pso.beam.contentextract.clients.MatchingEngineClient;
import com.google.cloud.pso.beam.contentextract.clients.PalmClient;
import com.google.cloud.pso.beam.contentextract.clients.Types;
import com.google.cloud.pso.data.services.utils.PromptUtilities;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;

/** */
@ApplicationScoped
public class VertexAIService {

  @Inject PalmClient palmService;
  @Inject EmbeddingsClient embeddingsService;
  @Inject MatchingEngineClient matchingEngineService;
  @Inject ServiceTypes.ResourceConfiguration configuration;

  @Timed(name = "palm.exchanges.summarization", unit = MetricUnits.MILLISECONDS)
  public Optional<Types.PalmSummarizationResponse> retrievePreviousSummarizedConversation(
      List<BigTableService.QAndA> qsAndAs) {
    if (qsAndAs.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        palmService.predictSummarizationWithRetries(
            new Types.PalmSummarizationRequest(
                new Types.PalmRequestParameters(
                    configuration.temperature(),
                    configuration.maxOutputTokens(),
                    configuration.topK(),
                    configuration.topP()),
                new Types.SummarizationInstances(
                    PromptUtilities.formatChatSummaryPrompt(
                        qsAndAs.stream().flatMap(q -> q.toExchange().stream()).toList())))));
  }

  @Timed(name = "palm.chat.prediction", unit = MetricUnits.MILLISECONDS)
  public Types.PalmChatResponse retrieveChatResponse(
      List<BigTableService.QAndA> lastsQAndAs,
      ServiceTypes.UserQuery query,
      String palmRequestContext) {
    var currentExchange =
        Lists.newArrayList(lastsQAndAs.stream().flatMap(qaa -> qaa.toExchange().stream()).toList());
    currentExchange.add(new Types.Exchange("user", query.text()));
    var palmResp =
        palmService.predictChatAnswerWithRetries(
            new Types.PalmChatAnswerRequest(
                palmRequestParameters(Optional.ofNullable(query.parameters())),
                new Types.ChatInstances(
                    palmRequestContext, PromptUtilities.EXCHANGE_EXAMPLES, currentExchange)));
    return palmResp;
  }

  @Timed(name = "palm.embeddings.prediction", unit = MetricUnits.MILLISECONDS)
  public Types.EmbeddingsResponse retrieveEmbeddings(
      ServiceTypes.UserQuery query, String previousSummarizedConversation) {
    var embeddingRequest =
        new Types.EmbeddingRequest(
            Lists.newArrayList(
                new Types.TextInstance(query.text() + "\n" + previousSummarizedConversation)));
    var embResponse = embeddingsService.retrieveEmbeddingsWithRetries(embeddingRequest);
    return embResponse;
  }

  @Timed(name = "matchingengine.ann", unit = MetricUnits.MILLISECONDS)
  public Types.NearestNeighborsResponse retrieveNearestNeighbors(
      Types.EmbeddingsResponse embResponse, ServiceTypes.UserQuery query) {
    // retrieve the nearest neighbors using the computed embeddings
    var nnResp =
        matchingEngineService.queryNearestNeighborsWithRetries(
            embResponse.toNearestNeighborRequest(
                configuration.matchingEngineIndexDeploymentId(),
                // use min value between statically configured and the request one (if exists)
                Integer.min(
                    configuration.maxNeighbors(),
                    Optional.ofNullable(query.parameters())
                        .flatMap(params -> Optional.ofNullable(params.maxNeighbors()))
                        .orElse(Integer.MAX_VALUE))));
    return nnResp;
  }

  Types.PalmRequestParameters palmRequestParameters(
      Optional<ServiceTypes.QueryParameters> parameters) {
    return new Types.PalmRequestParameters(
        parameters.map(p -> p.temperature()).orElse(configuration.temperature()),
        parameters.map(p -> p.maxOutputTokens()).orElse(configuration.maxOutputTokens()),
        parameters.map(p -> p.topK()).orElse(configuration.topK()),
        parameters.map(p -> p.topP()).orElse(configuration.topP()));
  }
}
