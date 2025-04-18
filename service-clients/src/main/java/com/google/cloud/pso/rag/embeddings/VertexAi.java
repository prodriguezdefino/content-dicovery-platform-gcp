package com.google.cloud.pso.rag.embeddings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.cloud.pso.rag.common.GoogleCredentialsCache;
import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.HttpRequestHelper;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** */
public interface VertexAi {
  static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
  static final ObjectMapper JSON_MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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

  sealed interface Response extends Embeddings.ValuesResponse
      permits TextResponse, MultimodalResponse {}

  sealed interface ResponseMetadata extends Embeddings.ResponseMetadata
      permits TextResponseMetadata, MultimodalResponseMetadata {}

  /*
   Text embeddings requests types.
  */

  record Text(String model, List<TextInstance> text, Optional<TextParameters> params)
      implements Request {
    public Text(String model, List<TextInstance> text) {
      this(model, text, Optional.empty());
    }
  }

  record TextEmbeddingRequest(List<TextInstance> instances, Optional<TextParameters> parameters) {}

  record TextInstance(
      String content,
      @JsonProperty("task_type") Optional<String> taskType,
      Optional<String> title) {
    public TextInstance(String content) {
      this(content, Optional.empty(), Optional.empty());
    }
  }

  record TextParameters(
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

  record Multimodal(String model, List<MultimodalInstance> data) implements Request {}

  record MultimodalEmbeddingRequest(List<MultimodalInstance> instances) {}

  record MultimodalInstance(
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
  }

  record MultimodalParameters(Integer dimension) implements Parameters {}

  record ImageData(
      Optional<String> bytesBase64Encoded, Optional<String> gcsUri, Optional<String> mimeType) {

    public ImageData {
      if (bytesBase64Encoded.isEmpty() && gcsUri.isEmpty()) {
        throw new IllegalArgumentException(
            "Image data should be provided as encoded base64 bytes or as a GCS URI location.");
      }
    }
  }

  record VideoData(
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

  record VideoSegment(
      Optional<Integer> startOffsetSec,
      Optional<Integer> endOffsetSec,
      Optional<Integer> intervalSec) {}

  record MultimodalResponse(List<MultimodalPrediction> predictions) implements Response {

    @Override
    public Embeddings.ResponseMetadata metadata() {
      return new MultimodalResponseMetadata();
    }
  }

  record MultimodalResponseMetadata() implements ResponseMetadata {}

  record MultimodalPrediction(
      Optional<List<Double>> textEmbedding,
      Optional<List<Double>> imageEmbedding,
      Optional<VideoEmbedding> videoEmbeddings) {}

  record VideoEmbedding(Integer startOffsetSec, Integer endOffsetSec, List<Double> embedding) {}

  static String requestBody(Request request) {
    try {
      return switch (request) {
        case Text(var __, var text, var params) ->
            JSON_MAPPER.writeValueAsString(new TextEmbeddingRequest(text, params));
        case Multimodal(var __, var data) ->
            JSON_MAPPER.writeValueAsString(new MultimodalEmbeddingRequest(data));
      };
    } catch (JsonProcessingException ex) {
      throw new EmbeddingsException("Problems while marshalling service request.", ex);
    }
  }

  static Embeddings.Response response(Request request, HttpResponse<String> httpResponse) {
    try {
      if (httpResponse.statusCode() != 200) {
        return new Embeddings.ErrorResponse(
            String.format(
                """
                Error returned by embeddings model, code: %d, message: %s
                Request payload: %s""",
                httpResponse.statusCode(), httpResponse.body(), request.toString()));
      }
      return switch (request) {
        case Text __ -> JSON_MAPPER.readValue(httpResponse.body(), TextResponse.class);
        case Multimodal __ -> JSON_MAPPER.readValue(httpResponse.body(), MultimodalResponse.class);
      };
    } catch (JsonProcessingException ex) {
      throw new EmbeddingsException("Problems while unmarshalling responses from service.", ex);
    }
  }

  static CompletableFuture<Embeddings.Response> retrieveEmbeddings(Request request) {
    try {
      var envConfig = GCPEnvironment.config();
      return HTTP_CLIENT
          .sendAsync(
              HttpRequestHelper.createHTTPBasedRequest(
                  uri(envConfig.project(), envConfig.region(), request.model()),
                  requestBody(request),
                  GoogleCredentialsCache.retrieveAccessToken(
                      envConfig.serviceAccountEmailSupplier())),
              HttpResponse.BodyHandlers.ofString())
          .thenApply(httpResponse -> response(request, httpResponse));
    } catch (URISyntaxException ex) {
      throw new EmbeddingsException("Problems while generating the URI for request.", ex);
    }
  }
}
