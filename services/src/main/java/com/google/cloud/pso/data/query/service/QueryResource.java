package com.google.cloud.pso.data.query.service;

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
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/query/content")
public class QueryResource {

  @Inject BigTableService btService;
  @Inject EmbeddingsClient embeddingsService;
  @Inject MatchingEngineClient matchingEngineService;
  @Inject PalmClient palmService;

  @Inject
  @ConfigProperty(name = "matchingengine.index.deployment")
  String matchingEngineIndexDeploymentId;

  private final Integer maxNeighbors = 5;
  private final Double neighborMaxDistance = 10.0;
  private final Double temperature = 0.2;
  private final Integer maxOutputTokens = 1024;
  private final Integer topK = 40;
  private final Double topP = 0.95;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public QueryResult query(UserQuery query) {

    var embeddingRequest =
        new Types.EmbeddingRequest(List.of(new Types.TextInstance(query.text())));

    var embResponse = embeddingsService.retrieveEmbeddings(embeddingRequest);
    var nnResp =
        matchingEngineService.queryNearestNeighboors(
            embResponse.toNearestNeighborRequest(matchingEngineIndexDeploymentId, maxNeighbors));

    var context =
        nnResp.nearestNeighbors().stream()
            .flatMap(n -> n.neighbors().stream())
            // filter out the dummy index initial vector
            .filter(n -> n.distance() < neighborMaxDistance)
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
                new Types.Parameters(temperature, maxOutputTokens, topK, topP),
                new Types.Instances(prompt)));

    return new QueryResult(
        palmResp.predictions().stream().map(pr -> pr.content()).collect(Collectors.joining("\n")),
        new ArrayList<>(sourceLinks));
  }

  public record UserQuery(String text) {}

  public record QueryResult(String content, List<String> sourceLinks) {}
}
