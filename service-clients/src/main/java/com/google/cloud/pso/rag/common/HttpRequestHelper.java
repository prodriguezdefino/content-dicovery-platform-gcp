package com.google.cloud.pso.rag.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;

/** */
public class HttpRequestHelper {
  public static HttpRequest createHTTPBasedRequest(URI uri, String body, String accessToken)
      throws URISyntaxException {
    return HttpRequest.newBuilder()
        .uri(uri)
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", "application/json; charset=utf-8")
        .method("POST", HttpRequest.BodyPublishers.ofString(body))
        .build();
  }
}
