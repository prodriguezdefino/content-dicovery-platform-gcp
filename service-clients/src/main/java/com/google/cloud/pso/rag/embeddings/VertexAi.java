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
package com.google.cloud.pso.rag.embeddings;

import static com.google.cloud.pso.rag.common.InteractionHelper.createHTTPBasedRequest;
import static com.google.cloud.pso.rag.common.InteractionHelper.httpClient;
import static com.google.cloud.pso.rag.common.InteractionHelper.jsonMapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.GoogleCredentialsCache;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.Failure;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import com.google.cloud.pso.rag.common.Result.Success;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** */
public class VertexAi {

  private VertexAi() {}

  static URI uri(String project, String region, String model) throws URISyntaxException {
    return new URI(
        "https://"
            + region
            + "-aiplatform.googleapis.com/v1/projects/"
            + project
            + "/locations/"
            + region
            + "/publishers/google/models/"
            + model
            + ":predict");
  }

  /*
   Interface definition.
  */

  sealed interface Request extends Embeddings.Request permits Text, Multimodal {
    String model();
  }

  sealed interface Parameters extends Embeddings.Parameters
      permits TextParameters, MultimodalParameters {}

  sealed interface Response extends Embeddings.Response permits TextResponse, MultimodalResponse {}

  sealed interface ResponseMetadata extends Embeddings.ResponseMetadata
      permits TextResponseMetadata, MultimodalResponseMetadata {}

  /*
   Text embeddings requests types.
  */

  public record Text(String model, List<TextInstance> text, Optional<TextParameters> params)
      implements Request {
    public Text(String model, List<TextInstance> text) {
      this(model, text, Optional.empty());
    }
  }

  record TextEmbeddingRequest(List<TextInstance> instances, Optional<TextParameters> parameters) {}

  public record TextInstance(
      String content,
      @JsonProperty("task_type") Optional<String> taskType,
      Optional<String> title) {
    public TextInstance(String content) {
      this(content, Optional.empty(), Optional.empty());
    }
  }

  public record TextParameters(
      @JsonProperty("auto_truncate") Boolean autoTruncate,
      @JsonProperty("output_dimensionality") Integer outputDimensionality)
      implements Parameters {}

  record TextResponse(List<TextPrediction> predictions) implements Response {

    @Override
    public Embeddings.ResponseMetadata metadata() {
      return new TextResponseMetadata(
          predictions.stream()
              .map(TextPrediction::embeddings)
              .map(TextEmbeddings::statistics)
              .toList());
    }
  }

  record TextResponseMetadata(List<Stats> stats) implements ResponseMetadata {}

  record TextPrediction(TextEmbeddings embeddings) {}

  record TextEmbeddings(Stats statistics, List<Double> values) {}

  record Stats(Boolean truncated, @JsonProperty("token_count") Integer tokenCount) {}

  /*
   Multimodal embeddings requests types.
  */

  public record Multimodal(String model, List<MultimodalInstance> data) implements Request {}

  record MultimodalEmbeddingRequest(List<MultimodalInstance> instances) {}

  public record MultimodalInstance(
      Optional<String> text,
      Optional<ImageData> image,
      Optional<VideoData> video,
      Optional<MultimodalParameters> parameters) {
    public MultimodalInstance {
      if (text.isEmpty() && image.isEmpty() && video.isEmpty()) {
        throw new IllegalArgumentException(
            "A multimodal embeddings request should set one of text, image or video data.");
      }
    }

    public MultimodalInstance(ImageData image) {
      this(Optional.empty(), Optional.of(image), Optional.empty(), Optional.empty());
    }

    public MultimodalInstance(VideoData video) {
      this(Optional.empty(), Optional.empty(), Optional.of(video), Optional.empty());
    }
  }

  public record MultimodalParameters(Integer dimension) implements Parameters {}

  public record ImageData(
      Optional<String> bytesBase64Encoded, Optional<String> gcsUri, Optional<String> mimeType) {

    public ImageData {
      if (bytesBase64Encoded.isEmpty() && gcsUri.isEmpty()) {
        throw new IllegalArgumentException(
            "Image data should be provided as encoded base64 bytes or as a GCS URI location.");
      }
    }
  }

  public record VideoData(
      Optional<String> bytesBase64Encoded,
      Optional<String> gcsUri,
      Optional<VideoSegment> videoSegmentConfig) {
    public VideoData {
      if (bytesBase64Encoded.isEmpty() && gcsUri.isEmpty()) {
        throw new IllegalArgumentException(
            "Video data should be provided as encoded base64 bytes or as a GCS URI location.");
      }
    }
  }

  public record VideoSegment(
      Optional<Integer> startOffsetSec,
      Optional<Integer> endOffsetSec,
      Optional<Integer> intervalSec) {}

  public record MultimodalResponse(List<MultimodalPrediction> predictions) implements Response {

    @Override
    public Embeddings.ResponseMetadata metadata() {
      return new MultimodalResponseMetadata();
    }
  }

  public record MultimodalResponseMetadata() implements ResponseMetadata {}

  public record MultimodalPrediction(
      Optional<List<Double>> textEmbedding,
      Optional<List<Double>> imageEmbedding,
      Optional<VideoEmbedding> videoEmbeddings) {}

  public record VideoEmbedding(
      Integer startOffsetSec, Integer endOffsetSec, List<Double> embedding) {}

  static Result<String, Exception> requestBody(Request request) {
    return switch (request) {
      case Text(var __, var text, var params) -> jsonMapper(new TextEmbeddingRequest(text, params));
      case Multimodal(var __, var data) -> jsonMapper(new MultimodalEmbeddingRequest(data));
    };
  }

  static ErrorResponse marshalFailure(Exception error) {
    return new ErrorResponse("Problems unmarshalling the response.", Optional.of(error));
  }

  static Result<? extends Embeddings.Response, ErrorResponse> response(
      Request request, HttpResponse<String> httpResponse) {
    return switch (request) {
      case Text __ when httpResponse.statusCode() == 200 ->
          jsonMapper(httpResponse.body(), TextResponse.class)
              .orElseApply(error -> marshalFailure(error));
      case Multimodal __ when httpResponse.statusCode() == 200 ->
          jsonMapper(httpResponse.body(), MultimodalResponse.class)
              .orElseApply(error -> marshalFailure(error));
      default ->
          Result.failure(
              String.format(
                  """
                  Error returned by embeddings model %s, code: %d, message: %s
                  Request payload: %s""",
                  request.model(), httpResponse.statusCode(), httpResponse.body(), request));
    };
  }

  static Result<CompletableFuture<HttpResponse<String>>, Exception> executeRequest(
      String body, String model) {
    try {
      var envConfig = GCPEnvironment.config();
      return Result.success(
          httpClient()
              .sendAsync(
                  createHTTPBasedRequest(
                      uri(envConfig.project(), envConfig.region(), model),
                      body,
                      GoogleCredentialsCache.retrieveAccessToken(
                          envConfig.serviceAccountEmailSupplier())),
                  HttpResponse.BodyHandlers.ofString()));
    } catch (URISyntaxException ex) {
      return Result.failure(ex);
    }
  }

  static Result<CompletableFuture<HttpResponse<String>>, Exception> executeInternal(
      Request request) {
    return requestBody(request).flatMap(body -> executeRequest(body, request.model()));
  }

  static CompletableFuture<Result<? extends Embeddings.Response, ErrorResponse>> retrieveEmbeddings(
      Request request) {
    return switch (executeInternal(request)) {
      case Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure("Error occurred while generating the request.", error));
      case Success<CompletableFuture<HttpResponse<String>>, ?>(var value) ->
          value.thenApply(httpResponse -> response(request, httpResponse));
    };
  }
}
