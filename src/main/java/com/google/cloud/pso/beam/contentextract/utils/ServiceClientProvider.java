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
package com.google.cloud.pso.beam.contentextract.utils;

import static com.google.cloud.pso.beam.contentextract.utils.Utilities.createTransport;
import static com.google.cloud.pso.beam.contentextract.utils.Utilities.getSecretValue;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.beam.sdk.values.KV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class ServiceClientProvider implements Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(ServiceClientProvider.class);

  private static final List<String> SCOPES =
      List.of(
          "https://www.googleapis.com/auth/documents",
          "https://www.googleapis.com/auth/drive",
          "https://www.googleapis.com/auth/cloud-platform");
  private static final NetHttpTransport HTTP_TRANSPORT = createTransport();
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String APP_NAME = "DocContentExtractor";
  private static final Drive DRIVE_SERVICE =
      new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
  private static final Docs DOCS_SERVICE =
      new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
  private static final LoadingCache<String, String> TOKEN_CACHE =
      CacheBuilder.<String, String>newBuilder()
          .expireAfterWrite(Duration.ofMinutes(5L))
          .build(
              new CacheLoader<String, String>() {
                @Override
                public String load(String credentialsSecretId) {
                  try {
                    var credentials = getCredentialsFromSecretManager(credentialsSecretId);
                    credentials.refreshIfExpired();
                    var accessToken = credentials.refreshAccessToken();
                    return accessToken.getTokenValue();
                  } catch (IOException ex) {
                    var msg = "Problems while trying to retrieve access token.";
                    LOG.error(msg, ex);
                    throw new RuntimeException(msg, ex);
                  }
                }
              });
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

  private final String region;
  private final String secretManagerId;
  private final String matchingEngineIndexId;

  ServiceClientProvider(String region, String secretManagerId, String matchingEngineIndexId) {
    this.region = region;
    this.secretManagerId = secretManagerId;
    this.matchingEngineIndexId = matchingEngineIndexId;
  }

  public static ServiceClientProvider create(
      String region, String secretManagerId, String matchingEngineIndexId) {
    return new ServiceClientProvider(region, secretManagerId, matchingEngineIndexId);
  }

  static GoogleCredentials getCredentialsFromSecretManager(String secretId) {
    try {
      var buffer = Base64.getDecoder().decode(getSecretValue(secretId).asReadOnlyByteBuffer());
      var in = new ByteBufferBackedInputStream(buffer);
      return GoogleCredentials.fromStream(in).createScoped(SCOPES);
    } catch (IOException ex) {
      var errMsg = "errors while trying to create credentials";
      LOG.error(errMsg, ex);
      throw new RuntimeException(errMsg, ex);
    }
  }

  static String retrieveAccessToken(String secretId) {
    try {
      return TOKEN_CACHE.get(secretId);
    } catch (ExecutionException ex) {
      var msg = "Error while trying to retrieve access token from cache";
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  public static String formatEmbeddingsBody(List<KV<String, List<Double>>> embeddings) {
    var datapointTemplate =
        """
        {
          "datapoint_id" : "%s",
          "feature_vector" : %s
        }""";
    var datapoints =
        embeddings.stream()
            .map(kv -> String.format(datapointTemplate, kv.getKey(), kv.getValue().toString()))
            .toList();
    return String.format(
        """
        {
          "datapoints" : %s
        }""", datapoints.toString());
  }

  public void upsertVectorDBDataPoints(List<KV<String, List<Double>>> dataPoints) {
    try {
      var uriStr =
          String.format(
              "https://%s-aiplatform.googleapis.com/v1/%s:upsertDatapoints",
              region, matchingEngineIndexId);

      var body = formatEmbeddingsBody(dataPoints);

      var request =
          HttpRequest.newBuilder()
              .uri(new URI(uriStr))
              .header("Authorization", "Bearer " + retrieveAccessToken())
              .header("Content-Type", "application/json; charset=utf-8")
              .method("POST", HttpRequest.BodyPublishers.ofString(body))
              .build();

      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new RuntimeException(
            String.format(
                "Error returned by matching engine index upsert: %s \nRequest payload: %s ",
                response.toString(), request.toString()));
      else
        LOG.info(
            String.format(
                "Propagated %d extracted embeddings vectors with ids: %s.",
                dataPoints.size(), dataPoints.stream().map(KV::getKey).toList().toString()));
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to upsert data in matching engine index.";
      throw new RuntimeException(msg, ex);
    }
  }

  String retrieveAccessToken() {
    return retrieveAccessToken(secretManagerId);
  }

  public Drive.Files.Get driveFileGetClient(String driveId) throws IOException {
    return DRIVE_SERVICE.files().get(driveId).setOauthToken(retrieveAccessToken());
  }

  public Drive.Files.List driveFileListClient(String queryString, String pageToken)
      throws IOException {
    return DRIVE_SERVICE
        .files()
        .list()
        .setOauthToken(retrieveAccessToken())
        .setQ(queryString)
        .setPageToken(Optional.ofNullable(pageToken).orElse(""))
        .setSpaces("drive")
        .setPageSize(10)
        .setFields("nextPageToken, files(id, mimeType)");
  }

  public Docs.Documents.Get documentGetClient(String documentId) throws IOException {
    return DOCS_SERVICE.documents().get(documentId).setAccessToken(retrieveAccessToken());
  }
}
