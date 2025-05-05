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
import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.GoogleCredentialsCache;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.Failure;
import com.google.cloud.pso.rag.common.Result.Success;
import com.google.cloud.pso.rag.vector.Vectors.ErrorResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/** */
public class VectorSearch {

  private VectorSearch() {}

  sealed interface SearchResponse extends Vectors.SearchResponse
      permits NeighborsResponse, Vectors.ErrorResponse {}

  sealed interface StoreResponse extends Vectors.StoreResponse
      permits UpsertResponse, Vectors.ErrorResponse {}

  sealed interface DeleteResponse extends Vectors.DeleteResponse
      permits RemoveResponse, Vectors.ErrorResponse {}

  public static SearchRequest requestFromValues(
      Integer neighborCount, List<List<Double>> listOfValues) {
    return new SearchRequest(
        IntStream.range(0, listOfValues.size())
            .mapToObj(
                idx ->
                    new Query(new Datapoint("query" + idx, listOfValues.get(idx)), neighborCount))
            .toList());
  }

  static Result<URI, Exception> createUri(String uri) {
    try {
      return Result.success(URI.create(uri));
    } catch (IllegalArgumentException ex) {
      return Result.failure(ex);
    }
  }

  static Result<URI, Exception> searchUri(String indexDomain, String indexEndpointPath) {
    return createUri(
        String.format("https://%s/v1/%s:findNeighbors", indexDomain, indexEndpointPath));
  }

  static Result<URI, Exception> storeUri(String region, String indexId) {
    return createUri(
        String.format(
            "https://%s-aiplatform.googleapis.com/v1/%s:upsertDatapoints", region, indexId));
  }

  static Result<URI, Exception> removeUri(String region, String indexId) {
    return createUri(
        String.format(
            "https://%s-aiplatform.googleapis.com/v1/%s:removeDatapoints", region, indexId));
  }

  /*
  Nearest neighbor search types.
  */

  public record SearchRequest(
      @JsonProperty("deployed_index_id") String deployedIndexId, List<Query> queries)
      implements Vectors.Search {
    public SearchRequest(List<Query> queries) {
      this(GCPEnvironment.config().vectorSearchConfig().deploymentId(), queries);
    }
  }

  public record NeighborsResponse(List<Neighbors> nearestNeighbors) implements SearchResponse {}

  public record Datapoint(String datapointId, List<Double> featureVector) {
    public Datapoint(List<Double> values) {
      this("dummyId", values);
    }
  }

  public record Query(Datapoint datapoint, @JsonProperty("neighbor_count") Integer neighborCount) {}

  public record Neighbor(Double distance, Datapoint datapoint) {}

  public record Neighbors(String id, List<Neighbor> neighbors) {}

  /*
  Index datapoint store types.
  */

  public record UpsertRequest(List<Datapoint> datapoints) implements Vectors.Store {}

  public record UpsertResponse() implements StoreResponse {}

  /*
  Remove datapoint types.
  */

  public record RemoveRequest(List<String> datapointIds) implements Vectors.Delete {}

  public record RemoveResponse() implements DeleteResponse {}

  static Result<URI, Exception> resolveRequestUri(Vectors.Request request) {
    var config = GCPEnvironment.config();
    var region = config.region();
    var vectorConfig = config.vectorSearchConfig();
    return switch (request) {
      case SearchRequest __ -> searchUri(vectorConfig.indexDomain(), vectorConfig.indexPath());
      case UpsertRequest __ -> storeUri(region, vectorConfig.indexId());
      case RemoveRequest __ -> removeUri(region, vectorConfig.indexId());
      default ->
          Result.failure(
              new RuntimeException(
                  String.format("Request type not implemented for VectorSearch: %s", request)));
    };
  }

  static Result<CompletableFuture<HttpResponse<String>>, Exception> executeRequest(
      URI uri, String body) {
    try {
      return Result.success(
          httpClient()
              .sendAsync(
                  createHTTPBasedRequest(
                      uri,
                      body,
                      GoogleCredentialsCache.retrieveAccessToken(
                          GCPEnvironment.config().serviceAccountEmailSupplier())),
                  HttpResponse.BodyHandlers.ofString()));
    } catch (URISyntaxException ex) {
      return Result.failure(ex);
    }
  }

  static ErrorResponse error(HttpResponse<String> httpResponse, Vectors.Request request) {
    return new ErrorResponse(
        String.format(
            """
            Error returned by VectorSearch, code %d, message: %s.
            Request payload: %s""",
            httpResponse.statusCode(), httpResponse.body(), request));
  }

  static ErrorResponse marshalError(String response, Throwable error) {
    return new ErrorResponse(
        String.format("Problems while marshalling response from VectorSearch: %s", response),
        Optional.of(error));
  }

  record HttpRequestParams(URI uri, String body) {}

  static Result<CompletableFuture<HttpResponse<String>>, Exception> executeInternal(
      Vectors.Request request) {
    return jsonMapper(request)
        .flatMap(body -> resolveRequestUri(request).map(uri -> new HttpRequestParams(uri, body)))
        .flatMap(reqParams -> executeRequest(reqParams.uri(), reqParams.body()));
  }

  static CompletableFuture<? extends StoreResponse> store(UpsertRequest request) {
    return switch (executeInternal(request)) {
      case Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              new ErrorResponse(
                  "Errors occurred while generating the request.", Optional.of(error)));
      case Success<CompletableFuture<HttpResponse<String>>, ?>(var value) ->
          value.thenApply(
              httpResponse ->
                  switch (httpResponse.statusCode()) {
                    case 200 ->
                        switch (jsonMapper(httpResponse.body(), UpsertResponse.class)) {
                          case Success<UpsertResponse, ?>(var response) -> response;
                          case Failure<?, Exception>(var error) ->
                              marshalError(httpResponse.body(), error);
                        };
                    default -> error(httpResponse, request);
                  });
    };
  }

  static CompletableFuture<SearchResponse> search(SearchRequest request) {
    return switch (executeInternal(request)) {
      case Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              new ErrorResponse(
                  "Errors occurred while generating the request.", Optional.of(error)));
      case Success<CompletableFuture<HttpResponse<String>>, ?>(var value) ->
          value.thenApply(
              httpResponse ->
                  switch (httpResponse.statusCode()) {
                    case 200 ->
                        switch (jsonMapper(httpResponse.body(), NeighborsResponse.class)) {
                          case Success<NeighborsResponse, ?>(var response) -> response;
                          case Failure<?, Exception>(var error) ->
                              marshalError(httpResponse.body(), error);
                        };
                    default -> error(httpResponse, request);
                  });
    };
  }

  static CompletableFuture<DeleteResponse> remove(RemoveRequest request) {
    return switch (executeInternal(request)) {
      case Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              new ErrorResponse(
                  "Errors occurred while generating the request.", Optional.of(error)));
      case Success<CompletableFuture<HttpResponse<String>>, ?>(var value) ->
          value.thenApply(
              httpResponse ->
                  switch (httpResponse.statusCode()) {
                    case 200 ->
                        switch (jsonMapper(httpResponse.body(), RemoveResponse.class)) {
                          case Success<RemoveResponse, ?>(var response) -> response;
                          case Failure<?, Exception>(var error) ->
                              marshalError(httpResponse.body(), error);
                        };
                    default -> error(httpResponse, request);
                  });
    };
  }
}
