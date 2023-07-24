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
package com.google.cloud.pso.beam.contentextract.clients;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.stream.Collectors;

/** */
public class Types {

  public record CrowdingTag(String crowdingAttribute) {}

  public record Datapoint(
      String datapointId, List<Double> featureVector, CrowdingTag crowdingTag) {}

  public record DatapointsResponse(List<Datapoint> datapoints) {}

  public record QueryDatapoint(
      @SerializedName("datapoint_id") String datapointId,
      @SerializedName("feature_vector") List<Double> featureVector) {}

  public record QueryDatapointRequest(
      QueryDatapoint datapoint, @SerializedName("neighbor_count") Integer neighborCount) {}

  public record NearestNeighborRequest(
      @SerializedName("deployed_index_id") String deployedIndexId,
      List<QueryDatapointRequest> queries) {}

  public record Stats(Boolean truncated, @SerializedName("token_count") Integer tokenCount) {}

  public record Embedding(Stats statistics, List<Double> values) {

    public QueryDatapointRequest toQueryDatapoint(Integer neighborCount) {
      return new QueryDatapointRequest(new QueryDatapoint("dummyId", values), neighborCount);
    }
  }

  public record Embeddings(Embedding embeddings) {}

  public record EmbeddingsResponse(List<Embeddings> predictions) {

    public NearestNeighborRequest toNearestNeighborRequest(
        String indexDeploymentId, Integer neighborCount) {

      return new NearestNeighborRequest(
          indexDeploymentId,
          predictions.stream()
              .map(embs -> embs.embeddings().toQueryDatapoint(neighborCount))
              .toList());
    }
  }

  public record TextInstance(String content) {}

  public record EmbeddingRequest(List<TextInstance> instances) {}

  public record Neighbor(Double distance, Datapoint datapoint) {}

  public record Neighbors(String id, List<Neighbor> neighbors) {}

  public record NearestNeighborsResponse(List<Neighbors> nearestNeighbors) {}

  public record Exchange(String author, String content) {}

  public record Example(String input, String output) {}

  public record ChatInstances(String context, List<Example> examples, List<Exchange> messages) {}

  public record PalmRequestParameters(
      Double temperature, Integer maxOutputTokens, Integer topK, Double topP) {}

  public record PalmChatAnswerRequest(PalmRequestParameters parameters, ChatInstances instances) {}

  public record SafetyAttributes(List<String> categories, List<Double> scores, Boolean blocked) {}

  public record Citation(Integer startIndex, Integer endIndex, String url) {}

  public record CitationMetadata(List<Citation> citations) {}

  public record PalmChatPrediction(
      List<SafetyAttributes> safetyAttributes,
      List<CitationMetadata> citationMetadata,
      List<Exchange> candidates) {}

  public record PalmChatResponse(List<PalmChatPrediction> predictions) {}

  public record SummarizationInstances(String prompt) {}

  public record PalmSummarizationRequest(
      PalmRequestParameters parameters, SummarizationInstances instances) {}

  public record PalmSummarizationPrediction(
      SafetyAttributes safetyAttributes, CitationMetadata citationMetadata, String content) {}

  public record PalmSummarizationResponse(List<PalmSummarizationPrediction> predictions) {

    public String summary() {
      return predictions.stream().map(sum -> sum.content()).collect(Collectors.joining(" "));
    }
  }

  public record UpsertMatchingEngineDatapoints(List<Types.Datapoint> datapoints) {}

  public record DeleteMatchingEngineDatapoints(List<String> datapointIds) {}

  public record ContentProcessed(String contentId, Long processedAtInMillis) {}
}
