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

import com.google.cloud.pso.beam.contentextract.clients.EmbeddingsClient;
import com.google.cloud.pso.beam.contentextract.clients.MatchingEngineClient;
import com.google.cloud.pso.beam.contentextract.clients.PalmClient;
import com.google.cloud.pso.beam.contentextract.clients.Types;
import com.google.cloud.pso.data.services.exceptions.QueryResourceException;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/query/content")
@SessionScoped
public class QueryResource {

  private static final Logger LOG = LoggerFactory.getLogger(QueryResource.class);

  @Inject BigTableService btService;
  @Inject EmbeddingsClient embeddingsService;
  @Inject MatchingEngineClient matchingEngineService;
  @Inject PalmClient palmService;
  @Inject BeansProducer.ResourceConfiguration configuration;
  @Inject RoutingContext routingContext;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public QueryResult query(UserQuery query) {
    try {
      Preconditions.checkState(query.sessionId() != null, "A valid session id should be provided.");
      Preconditions.checkState(query.text() != null, "A valid question should be provided.");

      var previousExchange =
          btService.retrieveConversationContext(query.sessionId()).exchanges().stream()
              .flatMap(qas -> qas.toExchange().stream())
              .toList();

      var currentExchange = Lists.newArrayList(previousExchange);
      currentExchange.add(new Types.Exchange("user", query.text()));

      var toRetriveEmbeddings =
          query.text()
              + "\n"
              + previousExchange.stream()
                  .map(ex -> ex.content())
                  .limit(5)
                  .collect(Collectors.joining("\n"));

      var embeddingRequest =
          new Types.EmbeddingRequest(List.of(new Types.TextInstance(toRetriveEmbeddings)));

      var embResponse = embeddingsService.retrieveEmbeddingsWithRetries(embeddingRequest);
      var nnResp =
          matchingEngineService.queryNearestNeighborsWithRetries(
              embResponse.toNearestNeighborRequest(
                  configuration.matchingEngineIndexDeploymentId(), configuration.maxNeighbors()));

      var context =
          nnResp.nearestNeighbors().stream()
              .flatMap(n -> n.neighbors().stream())
              // filter out the dummy index initial vector
              .filter(n -> n.distance() < configuration.maxNeighborDistance())
              // capture content and link from storage and preserve distance from original query
              .map(
                  nn -> {
                    var content = btService.queryByPrefix(nn.datapoint().datapointId());
                    return new ContentAndMetadata(
                        content.content(), content.sourceLink(), nn.distance());
                  })
              .toList();

      var contextContent = context.stream().map(ContentAndMetadata::content).toList();
      var sourceLinks =
          context.stream()
              // discard content
              .map(ContentAndMetadata::toLinkAndDistance)
              // filter empty links
              .filter(ld -> !ld.link().isBlank())
              // get max distance value per link
              .collect(
                  Collectors.toMap(
                      LinkAndDistance::link, ld -> ld.distance(), (d1, d2) -> d1 > d2 ? d1 : d2))
              // deduplicate
              .entrySet()
              .stream()
              // order descending by distance
              .sorted((e1, e2) -> -e1.getValue().compareTo(e2.getValue()))
              .map(e -> e.getKey())
              .toList();

      var palmRequestContext = PromptUtilities.formatPrompt(contextContent);

      var palmResp =
          palmService.sendPromptToModelWithRetries(
              new Types.PalmRequest(
                  new Types.PalmRequestParameters(
                      configuration.temperature(),
                      configuration.maxOutputTokens(),
                      configuration.topK(),
                      configuration.topP()),
                  new Types.Instances(
                      palmRequestContext, PromptUtilities.EXCHANGE_EXAMPLES, currentExchange)));

      var responseText =
          palmResp.predictions().stream()
                  .flatMap(pp -> pp.safetyAttributes().stream())
                  .anyMatch(saf -> saf.blocked())
              ? "Response blocked by model, check on provided document links if any available."
              : palmResp.predictions().stream()
                  .flatMap(pr -> pr.candidates().stream())
                  .map(ex -> ex.content())
                  .collect(Collectors.joining("\n"));
      var responseLinks =
          PromptUtilities.checkNegativeAnswer(responseText)
                  || responseText.contains(PromptUtilities.FOUND_IN_INTERNET)
              ? List.<String>of()
              : sourceLinks;

      // store the new exchange
      btService.storeQueryToContext(query.sessionId(), query.text(), responseText);

      return new QueryResult(responseText, responseLinks);
    } catch (Exception ex) {
      var msg = "Problems while executing the query resource. ";
      LOG.error(msg, ex);
      throw new QueryResourceException(msg + ex.getMessage());
    }
  }

  record LinkAndDistance(String link, Double distance) {}

  record ContentAndMetadata(String content, String link, Double distance) {
    public LinkAndDistance toLinkAndDistance() {
      return new LinkAndDistance(link(), distance());
    }
  }

  public record UserQuery(String text, String sessionId) {}

  public record QueryResult(String content, List<String> sourceLinks) {}
}
