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
        matchingEngineService.queryNearestNeighboors(
            embResponse.toNearestNeighborRequest(
                configuration.matchingEngineIndexDeploymentId(), configuration.maxNeighbors()));

    var context =
        nnResp.nearestNeighbors().stream()
            .flatMap(n -> n.neighbors().stream())
            // filter out the dummy index initial vector
            .filter(n -> n.distance() < configuration.maxNeighborDistance())
            .map(n -> n.datapoint().datapointId())
            .map(id -> btService.queryByPrefix(id))
            .map(content -> Tuple2.of(content.content(), content.sourceLink()))
            .toList();

    var contextContent = context.stream().map(Tuple2::getItem1).toList();
    var sourceLinks = context.stream().map(Tuple2::getItem2).collect(Collectors.toSet());

    var prompt = PromptUtilities.formatPrompt(query.text, contextContent);

    var palmResp =
        palmService.sendPromptToModel(
            new Types.PalmRequest(
                new Types.Parameters(
                    configuration.temperature(),
                    configuration.maxOutputTokens(),
                    configuration.topK(),
                    configuration.topP()),
                new Types.Instances(prompt)));

    return new QueryResult(
        palmResp.predictions().stream().map(pr -> pr.content()).collect(Collectors.joining("\n")),
        new ArrayList<>(sourceLinks));
  }

  public record UserQuery(String text) {}

  public record QueryResult(String content, List<String> sourceLinks) {}
}
