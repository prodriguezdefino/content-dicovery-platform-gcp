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
import com.google.common.collect.Lists;
import io.smallrye.mutiny.tuples.Tuple2;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/query/content")
public class QueryResource {

  @Inject BigTableService btService;
  @Inject EmbeddingsClient embeddingsService;
  @Inject MatchingEngineClient matchingEngineService;
  @Inject PalmClient palmService;
  @Inject BeansProducer.ResourceConfiguration configuration;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public QueryResult query(UserQuery query) {

    var embeddingRequest =
        new Types.EmbeddingRequest(List.of(new Types.TextInstance(query.text())));

    var embResponse = embeddingsService.retrieveEmbeddings(embeddingRequest);
    var nnResp =
        matchingEngineService.queryNearestNeighbors(
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
            .entrySet()
            .stream()
            // order descending by distance
            .sorted((e1, e2) -> -e1.getValue().compareTo(e2.getValue()))
            .map(e -> e.getKey())
            .toList();

    var prompt = PromptUtilities.formatPrompt(query.text, contextContent);

    var palmResp =
        palmService.sendPromptToModel(
            new Types.PalmRequest(
                new Types.PalmRequestParameters(
                    configuration.temperature(),
                    configuration.maxOutputTokens(),
                    configuration.topK(),
                    configuration.topP()),
                new Types.Instances(prompt)));

    var responseText =
        palmResp.predictions().stream().anyMatch(pp -> pp.safetyAttributes().blocked())
            ? "Response blocked by model, check on provided document links if available."
            : palmResp.predictions().stream()
                .map(pr -> pr.content())
                .collect(Collectors.joining("\n"));
    var responseLinks =
        PromptUtilities.checkNegativeAnswer(responseText)
                || responseText.contains(PromptUtilities.FOUND_IN_INTERNET)
            ? List.<String>of()
            : sourceLinks;

    return new QueryResult(responseText, responseLinks);
  }

  record LinkAndDistance(String link, Double distance) {}

  record ContentAndMetadata(String content, String link, Double distance) {
    public LinkAndDistance toLinkAndDistance() {
      return new LinkAndDistance(link(), distance());
    }
  }

  public record UserQuery(String text) {}

  public record QueryResult(String content, List<String> sourceLinks) {}
}
