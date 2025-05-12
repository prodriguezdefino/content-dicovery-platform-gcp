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
package com.google.cloud.pso.data.services.resources;

import com.google.cloud.pso.data.services.beans.BigTableService;
import com.google.cloud.pso.data.services.beans.ServiceTypes;
import com.google.cloud.pso.data.services.beans.ServiceTypes.ContentAndMetadata;
import com.google.cloud.pso.data.services.beans.ServiceTypes.LinkAndDistance;
import com.google.cloud.pso.data.services.beans.ServiceTypes.QueryResult;
import com.google.cloud.pso.data.services.beans.ServiceTypes.ResourceConfiguration;
import com.google.cloud.pso.data.services.beans.ServiceTypes.UserQuery;
import com.google.cloud.pso.data.services.beans.VertexAIService;
import com.google.cloud.pso.data.services.exceptions.QueryResourceException;
import com.google.cloud.pso.data.services.utils.PromptUtilities;
import com.google.cloud.pso.rag.llm.Gemini;
import com.google.cloud.pso.rag.vector.VectorSearch;
import com.google.cloud.pso.rag.vector.Vectors;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/query/content")
@SessionScoped
public class QueryResource {

  private static final Logger LOG = LoggerFactory.getLogger(QueryResource.class);
  private static final Gson GSON = new Gson();

  @Inject BigTableService btService;
  @Inject ResourceConfiguration configuration;
  @Inject VertexAIService vertexaiService;

  @Inject
  @Named("botContextExpertise")
  String configuredBotContextExpertise;

  @Inject
  @Named("includeOwnKnowledgeEnrichment")
  Boolean includeOwnKnowledgeEnrichment;

  List<ServiceTypes.QAndA> removeRepeatedAndNegaviteAnswers(List<ServiceTypes.QAndA> qsAndAs) {
    var prevQuestions = Sets.<String>newHashSet();
    var deduplicatedQAndAs = Lists.<ServiceTypes.QAndA>newArrayList();
    for (var qaa : qsAndAs) {
      if (!prevQuestions.contains(qaa.question())
          && !PromptUtilities.checkNegativeAnswer(qaa.answer())) {
        prevQuestions.add(qaa.question());
        deduplicatedQAndAs.add(qaa);
      }
    }
    return deduplicatedQAndAs;
  }

  void logInteraction(UserQuery query, QueryResult response) {
    if (!configuration.logInteractions()) {
      return;
    }
    LOG.info(
        "Interaction id : {}\n, Request: {}\n, Response: {}\n",
        UUID.randomUUID().toString(),
        GSON.toJsonTree(query).toString(),
        GSON.toJsonTree(response).toString());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "content.query", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<QueryResult> query(UserQuery query) {
    try {
      Preconditions.checkState(
          query.sessionId() != null, "Session id should be present, even if empty.");
      Preconditions.checkState(query.text() != null, "A valid question should be provided.");
      Preconditions.checkState(!query.text().trim().isEmpty(), "Provided query is empty.");

      // retrieve the previous q and as from the conversation context removing the repeated and
      // negative answers coming from the model
      var qAndAs =
          removeRepeatedAndNegaviteAnswers(
              btService.retrieveConversationContext(query.sessionId()).qAndAs());

      // keep the embeddings context as the last 5 questions (summarization may become too
      // clumsy).
      var lastsQAndAs =
          qAndAs.size() > 5 ? qAndAs.subList(qAndAs.size() - 6, qAndAs.size() - 1) : qAndAs;

      // retrieve the summary of the previous conversation and generate embeddings adding that
      // context to the user query
      var previousFuture =
          lastsQAndAs.isEmpty()
              ? CompletableFuture.completedFuture("")
              : vertexaiService
                  .retrievePreviousSummarizedConversation(lastsQAndAs)
                  .thenApply(
                      summary ->
                          summary
                              .map(resp -> resp.content())
                              .orElseThrow(
                                  error ->
                                      new QueryResourceException(
                                          error.message(),
                                          query.text(),
                                          query.sessionId(),
                                          error.cause().get())));
      var contextFuture =
          previousFuture.thenCompose(
              previousSummarizedConversation ->
                  // given the query and previous conversation summary, retrieve embeddings
                  vertexaiService
                      .retrieveEmbeddings(query, previousSummarizedConversation)
                      // and those nearest neighbors
                      .thenCompose(
                          embResponse ->
                              vertexaiService.retrieveNearestNeighbors(embResponse, query))
                      // given the retrieved neighbors, use their ids to retrieve the chunks
                      // text content
                      .thenApply(
                          nnResp ->
                              switch (nnResp) {
                                case Vectors.ErrorResponse(var message, var cause) ->
                                    throw cause
                                        .map(ex -> new RuntimeException(message, ex))
                                        .orElse(new RuntimeException(message));
                                case VectorSearch.NeighborsResponse(var nearestNeighbors) ->
                                    nearestNeighbors.stream()
                                        .flatMap(n -> n.neighbors().stream())
                                        // filter out the dummy index initial vector
                                        .filter(
                                            n -> n.distance() < configuration.maxNeighborDistance())
                                        .sorted((n1, n2) -> -n1.distance().compareTo(n2.distance()))
                                        // we keep only the most relevant context entries
                                        .limit(configuration.maxNeighbors())
                                        // capture content and link from storage and preserve
                                        // distance from
                                        // original
                                        // query
                                        .map(
                                            nn -> {
                                              var content =
                                                  btService.queryByPrefix(
                                                      nn.datapoint().datapointId());
                                              return new ContentAndMetadata(
                                                  content.content(),
                                                  content.sourceLink(),
                                                  nn.distance());
                                            })
                                        .toList();
                              }));
      var textResponseFuture =
          contextFuture
              .thenCompose(
                  context -> {
                    // given the textual context and the previously retrieved existing conversation
                    // request a chat
                    // response to the model using the provided query.
                    var palmRequestContext =
                        PromptUtilities.formatChatContextPrompt(
                            context.stream().map(ContentAndMetadata::content).toList(),
                            // if there is a query param knowledge setup we use that
                            Optional.ofNullable(query.parameters())
                                .map(p -> Optional.ofNullable(p.botContextExpertise()))
                                // or default to whatever was configured, if anything
                                .orElse(Optional.ofNullable(configuredBotContextExpertise)),
                            // also use the query configured knowledge enrichment, if tis there.
                            Optional.ofNullable(query.parameters())
                                .flatMap(
                                    p -> Optional.ofNullable(p.includeOwnKnowledgeEnrichment()))
                                // or default to whatever was configured, if anything
                                .orElse(
                                    Optional.ofNullable(includeOwnKnowledgeEnrichment)
                                        .orElse(true)));
                    return vertexaiService.retrieveChatResponse(
                        lastsQAndAs, query, palmRequestContext);
                  })
              .thenApply(
                  response ->
                      response
                          .map(
                              chat ->
                                  switch (chat) {
                                    case Gemini.ChatResponse(var __, var blocked) when blocked
                                            .isPresent() ->
                                        "Response blocked by model. " + blocked.get();
                                    case Gemini.ChatResponse(var exchange, var __) ->
                                        exchange.content();
                                  })
                          .orElseThrow(
                              error ->
                                  new QueryResourceException(
                                      error.message(),
                                      query.text(),
                                      query.sessionId(),
                                      error.cause().get())));

      return contextFuture.thenCombine(
          textResponseFuture,
          (context, responseText) -> {
            // the context source links
            var sourceLinks =
                context.stream()
                    // discard content
                    .map(ContentAndMetadata::toLinkAndDistance)
                    // filter empty links
                    .filter(ld -> !ld.link().isBlank())
                    // get max distance value per link
                    .collect(
                        Collectors.toMap(
                            LinkAndDistance::link,
                            LinkAndDistance::distance,
                            (d1, d2) -> d1 > d2 ? d1 : d2))
                    // deduplicate
                    .entrySet()
                    .stream()
                    // order descending by distance
                    .sorted((e1, e2) -> -e1.getValue().compareTo(e2.getValue()))
                    .map(e -> new LinkAndDistance(e.getKey(), e.getValue()))
                    .toList();
            var responseLinks =
                PromptUtilities.checkNegativeAnswer(responseText)
                        || responseText.contains(PromptUtilities.FOUND_IN_INTERNET)
                    ? List.<LinkAndDistance>of()
                    : sourceLinks;
            // store the new exchange
            btService.storeQueryToContext(query.sessionId(), query.text(), responseText);
            // to finally return a query response
            var response = new QueryResult(responseText, previousFuture.join(), responseLinks);
            logInteraction(query, response);
            return response;
          });
    } catch (Exception ex) {
      var msg = "Problems while executing the query resource. ";
      LOG.error(msg, ex);
      throw new QueryResourceException(msg + ex.getMessage(), query.text(), query.sessionId(), ex);
    }
  }
}
