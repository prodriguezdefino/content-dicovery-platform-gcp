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
package com.google.cloud.pso.beam.contentextract.clients;

import static com.google.cloud.pso.beam.contentextract.clients.utils.Utilities.buildRetriableExecutorForOperation;
import static com.google.cloud.pso.beam.contentextract.clients.utils.Utilities.executeOperation;

import com.google.cloud.pso.beam.contentextract.clients.exceptions.MatchingEngineException;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class MatchingEngineClient extends VertexAIClient {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingEngineClient.class);

  private final String region;
  private final String matchingEngineIndexId;
  private final String matchingEngineIndexEndpointId;
  private final String matchingEngineIndexEndpointDomain;
  private final String matchingEngineIndexDeploymentId;

  MatchingEngineClient(
      String region,
      String matchingEngineIndexId,
      String matchingEngineIndexEndpointId,
      String matchingEngineIndexEndpointDomain,
      String matchingEngineIndexDeploymentId) {
    super();
    this.region = region;
    this.matchingEngineIndexId = matchingEngineIndexId;
    this.matchingEngineIndexEndpointId = matchingEngineIndexEndpointId;
    this.matchingEngineIndexEndpointDomain = matchingEngineIndexEndpointDomain;
    this.matchingEngineIndexDeploymentId = matchingEngineIndexDeploymentId;
  }

  public static MatchingEngineClient create(
      String region,
      String matchingEngineIndexId,
      String matchingEngineIndexEndpointId,
      String matchingEngineIndexEndpointDomain,
      String matchingEngineIndexDeploymentId) {
    return new MatchingEngineClient(
        region,
        matchingEngineIndexId,
        matchingEngineIndexEndpointId,
        matchingEngineIndexEndpointDomain,
        matchingEngineIndexDeploymentId);
  }

  public Types.DatapointsResponse readIndexDatapoints(List<String> datapointIds) {
    try {
      var uriStr =
          String.format(
              "https://%s/v1beta1/%s:readIndexDatapoints",
              matchingEngineIndexEndpointDomain, matchingEngineIndexEndpointId);
      var body = formatReadIndexDatapoints(matchingEngineIndexDeploymentId, datapointIds);
      var request = createHTTPBasedRequest(uriStr, body);
      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200)
        throw new RuntimeException(
            String.format(
                "Error returned by matching engine index read datapoints: %s \nRequest payload: %s.",
                response.toString(), request.toString()));

      return GSON.fromJson(response.body(), Types.DatapointsResponse.class);
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to retrieve datapoints from matching engine index.";
      LOG.error(msg, ex);
      throw new MatchingEngineException(msg, ex);
    }
  }

  void deleteVectorDBDatapoints(Types.DeleteMatchingEngineDatapoints deleteRequest) {
    try {
      var uriStr =
          String.format(
              "https://%s-aiplatform.googleapis.com/v1/%s:removeDatapoints",
              region, matchingEngineIndexId);

      var body = GSON.toJson(deleteRequest);
      var request = createHTTPBasedRequest(uriStr, body);
      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200)
        throw new RuntimeException(
            String.format(
                "Error returned by matching engine index upsert: %s \nRequest payload: %s ",
                response.toString(), request.toString()));
      else
        LOG.info(
            "Deleted {} datapoints with ids: {}.",
            deleteRequest.datapointIds().size(),
            deleteRequest.datapointIds().toString());
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to upsert data in matching engine index.";
      LOG.error(msg, ex);
      throw new MatchingEngineException(msg, ex);
    }
  }

  public void deleteVectorDBDatapointsWithRetries(
      Types.DeleteMatchingEngineDatapoints deleteRequest) {
    executeOperation(
        buildRetriableExecutorForOperation(
            "deleteVectorDBDatapoints", Lists.newArrayList(MatchingEngineException.class)),
        () -> deleteVectorDBDatapoints(deleteRequest));
  }

  void upsertVectorDBDataPoints(Types.UpsertMatchingEngineDatapoints upsertRequest) {
    try {
      var uriStr =
          String.format(
              "https://%s-aiplatform.googleapis.com/v1/%s:upsertDatapoints",
              region, matchingEngineIndexId);

      var body = formatUpsertDatapoints(upsertRequest);
      var request = createHTTPBasedRequest(uriStr, body);
      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200)
        throw new RuntimeException(
            String.format(
                "Error returned by matching engine index upsert: %s \nRequest payload: %s ",
                response.toString(), request.toString()));
      else
        LOG.info(
            "Upserted {} extracted embeddings vectors with ids: {}.",
            upsertRequest.datapoints().size(),
            upsertRequest.datapoints().stream().map(emb -> emb.datapointId()).toList().toString());
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to upsert data in matching engine index.";
      LOG.error(msg, ex);
      throw new MatchingEngineException(msg, ex);
    }
  }

  public void upsertVectorDBDataPointsWithRetries(
      Types.UpsertMatchingEngineDatapoints upsertRequest) {

    executeOperation(
        buildRetriableExecutorForOperation(
            "upsertVectorDBDatapoints", Lists.newArrayList(MatchingEngineException.class)),
        () -> upsertVectorDBDataPoints(upsertRequest));
  }

  Types.NearestNeighborsResponse queryNearestNeighbors(Types.NearestNeighborRequest nnRequest) {

    try {
      var uriStr =
          String.format(
              "https://%s/v1beta1/%s:findNeighbors",
              matchingEngineIndexEndpointDomain, matchingEngineIndexEndpointId);
      var body = GSON.toJson(nnRequest);
      var request = createHTTPBasedRequest(uriStr, body);
      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200)
        throw new RuntimeException(
            String.format(
                "Error returned by matching engine index find neighbors: %s \nRequest payload: %s ",
                response.toString(), request.toString()));

      return GSON.fromJson(response.body(), Types.NearestNeighborsResponse.class);
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to retrieve nearest neighbors from matching engine index.";
      LOG.error(msg, ex);
      throw new MatchingEngineException(msg, ex);
    }
  }

  public Types.NearestNeighborsResponse queryNearestNeighborsWithRetries(
      Types.NearestNeighborRequest nnRequest) {

    return executeOperation(
        buildRetriableExecutorForOperation(
            "retrieveEmbeddings", Lists.newArrayList(MatchingEngineException.class)),
        () -> queryNearestNeighbors(nnRequest));
  }
}
