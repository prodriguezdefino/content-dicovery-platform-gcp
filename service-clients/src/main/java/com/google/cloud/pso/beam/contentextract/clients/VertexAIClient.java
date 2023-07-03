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

import com.google.cloud.pso.beam.contentextract.clients.utils.GoogleCredentialsCache;
import com.google.gson.Gson;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;

/** */
public abstract class VertexAIClient implements Serializable {

  static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
  static final Gson GSON = new Gson();

  private final String credentialsSecretManagerId;

  VertexAIClient(String credentialsSecretManagerId) {
    this.credentialsSecretManagerId = credentialsSecretManagerId;
  }

  String retrieveAccessToken() {
    return GoogleCredentialsCache.retrieveAccessToken(credentialsSecretManagerId);
  }

  public static String formatUpsertDatapoints(Types.UpsertMatchingEngineDatapoints embeddings) {
    var datapointTemplate =
        """
        {
          "datapoint_id" : "%s",
          "feature_vector" : %s
        }""";
    var datapoints =
        embeddings.datapoints().stream()
            .map(
                emb ->
                    String.format(
                        datapointTemplate, emb.datapointId(), emb.featureVector().toString()))
            .toList();
    return String.format(
        """
        {
          "datapoints" : %s
        }""", datapoints.toString());
  }

  public static String formatReadIndexDatapoints(
      String deployedIndexId, List<String> datapointIds) {
    var bodyTemplate =
        """
        {
          "deployed_index_id":"%s",
          "ids": %s
        }""";

    return String.format(
        bodyTemplate,
        deployedIndexId,
        datapointIds.stream().map(id -> "\"" + id + "\"").toList().toString());
  }

  protected HttpRequest createHTTPBasedRequest(String uri, String body) throws URISyntaxException {
    return HttpRequest.newBuilder()
        .uri(new URI(uri))
        .header("Authorization", "Bearer " + retrieveAccessToken())
        .header("Content-Type", "application/json; charset=utf-8")
        .method("POST", HttpRequest.BodyPublishers.ofString(body))
        .build();
  }
}
