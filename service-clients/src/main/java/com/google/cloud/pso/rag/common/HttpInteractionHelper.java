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
package com.google.cloud.pso.rag.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/** */
public class HttpInteractionHelper {

  static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
  static final ObjectMapper JSON_MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static HttpClient httpClient() {
    return HTTP_CLIENT;
  }

  public sealed interface MaybeJson<T> permits Json, Error {}

  public record Json<T>(T value) implements MaybeJson<T> {}

  public record Error<T>(Throwable error) implements MaybeJson<T> {}

  public static <T> MaybeJson<T> jsonMapper(String value, Class<T> valueType) {
    try {
      return new Json<>(JSON_MAPPER.readValue(value, valueType));
    } catch (JsonProcessingException ex) {
      return new Error<>(ex);
    }
  }

  public static <T> MaybeJson<T> jsonMapper(String value, TypeReference<T> valueTypeReference) {
    try {
      return new Json<>(JSON_MAPPER.readValue(value, valueTypeReference));
    } catch (JsonProcessingException ex) {
      return new Error<>(ex);
    }
  }

  public static String jsonMapper(Object value) throws JsonProcessingException {
    return JSON_MAPPER.writeValueAsString(value);
  }

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
