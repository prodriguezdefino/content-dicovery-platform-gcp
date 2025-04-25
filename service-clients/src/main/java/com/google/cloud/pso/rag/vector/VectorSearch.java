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
package com.google.cloud.pso.rag.vector;

import static com.google.cloud.pso.rag.common.HttpInteractionHelper.createHTTPBasedRequest;
import static com.google.cloud.pso.rag.common.HttpInteractionHelper.httpClient;
import static com.google.cloud.pso.rag.common.HttpInteractionHelper.jsonMapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.GoogleCredentialsCache;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** */
public interface VectorSearch {

  String INDEX_FIND_METHOD = "findNeighbors";
  String INDEX_STORE_METHOD = "upsertDatapoints";

  sealed interface Request extends Vectors.Search, Vectors.Store
      permits SearchRequest, UpsertRequest {}

  sealed interface SearchResponse extends Vectors.SearchResponse
      permits NeighborsResponse, Vectors.ErrorResponse {}

  sealed interface StoreResponse extends Vectors.StoreResponse
      permits UpsertResponse, Vectors.ErrorResponse {}

  static SearchRequest requestFromValues(
      String deployedIndexId, Integer neighborCount, List<Double> values) {
    return new SearchRequest(
        deployedIndexId, List.of(new Query(new Datapoint(values), neighborCount)));
  }

  static URI searchUri(String indexDomain, String indexEndpointPath) throws URISyntaxException {
    return new URI(String.format("https://%s/v1/%s:findNeighbors", indexDomain, indexEndpointPath));
  }

  static URI storeUri(String region, String indexId) throws URISyntaxException {
    return new URI(
        String.format(
            "https://%s-aiplatform.googleapis.com/v1/%s:upsertDatapoints", region, indexId));
  }

  record SearchRequest(
      @JsonProperty("deployed_index_id") String deployedIndexId, List<Query> queries)
      implements Request {}

  record NeighborsResponse(List<Neighbors> nearestNeighbors) implements SearchResponse {}

  /*
  Nearest Neighbor request types.
  */
  record Datapoint(String datapointId, List<Double> featureVector) {
    public Datapoint(List<Double> values) {
      this("dummyId", values);
    }
  }

  record Query(Datapoint datapoint, @JsonProperty("neighbor_count") Integer neighborCount) {}

  /*
  Nearest Neighbor response types.
  */

  record Neighbor(Double distance, Datapoint datapoint) {}

  record Neighbors(String id, List<Neighbor> neighbors) {}

  static String requestBody(Request request) {
    try {
      return jsonMapper(request);
    } catch (JsonProcessingException ex) {
      throw new VectorsException("Problems while marshalling service request.", ex);
    }
  }

  /*
  Index datapoint store types.
  */

  record UpsertRequest(List<Datapoint> datapoints) implements Request {}

  record UpsertResponse() implements StoreResponse {}

  static Supplier<VectorsException> exceptionSupplier(Throwable cause) {
    return () -> new VectorsException("Problems while marshalling service response.", cause);
  }

  static URI resolveRequestUri(Request request) throws URISyntaxException {
    var vectorConfig = GCPEnvironment.config().vectorSearchConfig();
    return switch (request) {
      case SearchRequest __ -> searchUri(vectorConfig.indexDomain(), vectorConfig.indexPath());
      case UpsertRequest __ -> storeUri(GCPEnvironment.config().region(), vectorConfig.indexId());
    };
  }

  static CompletableFuture<HttpResponse<String>> initiateRequest(Request request) {
    try {
      return httpClient()
          .sendAsync(
              createHTTPBasedRequest(
                  resolveRequestUri(request),
                  requestBody(request),
                  GoogleCredentialsCache.retrieveAccessToken(
                      GCPEnvironment.config().serviceAccountEmailSupplier())),
              HttpResponse.BodyHandlers.ofString());
    } catch (URISyntaxException ex) {
      throw new VectorsException("Problems while generating the URI for request.", ex);
    }
  }

  static CompletableFuture<Vectors.StoreResponse> store(Request request) {
    return initiateRequest(request)
        .thenApply(
            httpResponse ->
                switch (httpResponse.statusCode()) {
                  case 200 -> {
                    var maybeJson = jsonMapper(httpResponse.body(), UpsertResponse.class);
                    yield Optional.ofNullable(maybeJson.value())
                        .orElseThrow(
                            () ->
                                new VectorsException(
                                    "Problems while marshalling response.", maybeJson.error()));
                  }
                  default ->
                      new Vectors.ErrorResponse(
                          String.format(
                              """
                              Error returned by VectorSearch, code %d, message: %s.
                              Request payload: %s""",
                              httpResponse.statusCode(), httpResponse.body(), request));
                });
  }

  static CompletableFuture<Vectors.SearchResponse> search(Request request) {
    return initiateRequest(request)
        .thenApply(
            httpResponse ->
                switch (httpResponse.statusCode()) {
                  case 200 -> {
                    var maybeJson = jsonMapper(httpResponse.body(), NeighborsResponse.class);
                    yield Optional.ofNullable(maybeJson.value())
                        .orElseThrow(
                            () ->
                                new VectorsException(
                                    "Problems while marshalling response.", maybeJson.error()));
                  }
                  default ->
                      new Vectors.ErrorResponse(
                          String.format(
                              """
                              Error returned by VectorSearch, code %d, message: %s.
                              Request payload: %s""",
                              httpResponse.statusCode(), httpResponse.body(), request));
                });
  }
}
