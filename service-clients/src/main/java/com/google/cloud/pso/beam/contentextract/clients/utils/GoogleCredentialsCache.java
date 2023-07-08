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
package com.google.cloud.pso.beam.contentextract.clients.utils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
                public String load(String credentialsPrincipal) {
                  return accessToken(credentialsPrincipal);
                }
              });

  static String accessToken(String credentialsPrincipal) {
    try {
      var credentials =
          Optional.of(credentialsPrincipal)
              .filter(p -> !p.isEmpty() && !p.isBlank())
              .map(p -> getImpersonatedCredentialsWithScopes(p))
              .orElse(getDefaultCredentials());
      credentials.refreshIfExpired();
      var accessToken = credentials.refreshAccessToken();
      return accessToken.getTokenValue();
    } catch (IOException ex) {
      var msg =
          String.format(
              "Problems while trying to retrieve access token, principal '%s'.",
              credentialsPrincipal);
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  static GoogleCredentials getDefaultCredentials() {
    try {
      return GoogleCredentials.getApplicationDefault();
    } catch (Exception ex) {
      var errMsg = "errors while trying to create default credentials";
      LOG.error(errMsg, ex);
      throw new RuntimeException(errMsg, ex);
    }
  }

  /**
   * this method is only available because currently there is no way to add scopes to the default
   * credentials while using GCE service account.
   */
  static GoogleCredentials getImpersonatedCredentialsWithScopes(String targetPrincipal) {
    try {
      return ImpersonatedCredentials.create(
          GoogleCredentials.getApplicationDefault(), targetPrincipal, null, SCOPES, 3600);
    } catch (Exception ex) {
      var errMsg =
          String.format(
              "errors while trying to create impersonated '%s' credentials", targetPrincipal);
      LOG.error(errMsg, ex);
      throw new RuntimeException(errMsg, ex);
    }
  }

  public static String retrieveAccessToken(String credentialsPrincipal) {
    try {
      return TOKEN_CACHE.get(credentialsPrincipal);
    } catch (Exception ex) {
      var msg = "Error while trying to retrieve access token from cache";
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }
}
