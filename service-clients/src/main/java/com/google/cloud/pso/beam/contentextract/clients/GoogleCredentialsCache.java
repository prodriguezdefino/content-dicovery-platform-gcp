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

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class GoogleCredentialsCache {
  private static final Logger LOG = LoggerFactory.getLogger(GoogleCredentialsCache.class);

  private static final List<String> SCOPES =
      List.of(
          "https://www.googleapis.com/auth/documents",
          "https://www.googleapis.com/auth/drive",
          "https://www.googleapis.com/auth/cloud-platform");
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

  public static String retrieveAccessToken(String secretId) {
    try {
      return TOKEN_CACHE.get(secretId);
    } catch (ExecutionException ex) {
      var msg = "Error while trying to retrieve access token from cache";
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  public static ByteString getSecretValue(String secretId) {
    try (var client = SecretManagerServiceClient.create()) {
      LOG.info("retrieving encoded key secret {}", secretId);
      Preconditions.checkArgument(
          SecretVersionName.isParsableFrom(secretId), "The provided secret is not parseable.");
      var secretVersionName = SecretVersionName.parse(secretId);
      return client.accessSecretVersion(secretVersionName).getPayload().getData();
    } catch (Exception ex) {
      var msg = "Error while interacting with SecretManager client, key: " + secretId;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }
}
