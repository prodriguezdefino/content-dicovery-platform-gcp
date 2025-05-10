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
package com.google.cloud.pso.beam.contentextract.clients.utils;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.Preconditions;
import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveAPIMimeTypes;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities that support the usage of the different clients. */
public class Utilities {
  public static final String CONTENT_KEY_SEPARATOR = "___";
  private static final Logger LOG = LoggerFactory.getLogger(Utilities.class);
  static final Long BACKOFF_DELAY_IN_SECONDS = 5L;
  static final Long BACKOFF_MAX_DELAY_IN_MINUTES = 5L;
  static final Double RETRY_JITTER_PROB = 0.2;
  static final Integer MAX_RETRIES = 3;

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

  public static <T> FailsafeExecutor<T> buildRetriableExecutorForOperation(
      String operationName, List<Class<? extends Throwable>> exClasses) {
    return Failsafe.with(
        RetryPolicy.<T>builder()
            .handle(Lists.newArrayList(exClasses))
            .withMaxAttempts(MAX_RETRIES)
            .withBackoff(
                Duration.ofSeconds(BACKOFF_DELAY_IN_SECONDS),
                Duration.ofMinutes(BACKOFF_MAX_DELAY_IN_MINUTES))
            .withJitter(RETRY_JITTER_PROB)
            .onFailedAttempt(
                e ->
                    LOG.error(
                        "Execution failed for operation: " + operationName, e.getLastException()))
            .onRetry(
                r ->
                    LOG.info(
                        "Retrying operation {}, for {} time.",
                        operationName,
                        r.getExecutionCount()))
            .onRetriesExceeded(e -> LOG.error("Failed to execute operation {}, retries exhausted."))
            .build());
  }

  public static <T> T executeOperation(
      FailsafeExecutor<T> failsafeExecutor, CheckedSupplier<T> operation) {
    return failsafeExecutor.get(() -> operation.get());
  }

  public static <T> void executeOperation(
      FailsafeExecutor<T> failsafeExecutor, CheckedRunnable runnable) {
    failsafeExecutor.run(runnable);
  }

  public static NetHttpTransport createTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException ex) {
      var errMsg = "Errors while trying to create a transport object.";
      LOG.error(errMsg, ex);
      throw new RuntimeException(ex);
    }
  }

  static String extractIdByPattern(String path, String itemPattern) {
    var pathParts = path.split(itemPattern);
    if (pathParts.length < 2) {
      throw new IllegalArgumentException(
          "The path does not contain a Google Drive id, path: " + path);
    }
    var containsId = pathParts[1].split("/")[0];
    if (containsId.isEmpty()) {
      throw new IllegalArgumentException(
          "Wrong path pattern, path: " + path + ", pattern: " + itemPattern);
    }
    return containsId;
  }

  public static Boolean checkIfValidURL(String maybeUrl) {
    try {
      URI.create(maybeUrl);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  public static String extractIdFromURL(String url) {
    var path = URI.create(url).getPath();
    Preconditions.checkState(!path.isEmpty(), "The URL path should not be empty");
    if (path.contains("/document/d/")) {
      return extractIdByPattern(path, "/document/d/");
    } else if (path.contains("/presentation/d/")) {
      return extractIdByPattern(path, "/presentation/d/");
    } else if (path.contains("/spreadsheets/d/")) {
      return extractIdByPattern(path, "/spreadsheets/d/");
    } else if (path.contains("/drive/folders/")) {
      return extractIdByPattern(path, "/drive/folders/");
    } else {
      throw new IllegalArgumentException(
          "The shared URL is not a document or drive folder one: " + url);
    }
  }

  public static String newIdFromTitleAndDriveId(String title, String driveId) {
    var normalized = Normalizer.normalize(title, Normalizer.Form.NFD);
    return normalized
            .replaceAll("[ ]{1,}", "_")
            .replaceAll("[\\[\\]]", "")
            .replaceAll("[\\(\\)]", "")
            .replaceAll("[\\n\\r]", "")
            .toLowerCase()
        + CONTENT_KEY_SEPARATOR
        + driveId;
  }

  public static String prefixIdFromContentId(String contentId) {
    var embeddingsIdParts = contentId.split(CONTENT_KEY_SEPARATOR);
    if (embeddingsIdParts.length != 3) {
      LOG.warn("Expected a 3 part embeddings id, got {}. Returning empty string.", contentId);
      return "";
    }
    // we only keep the file id part, discarding doc name and embeddings sequence
    return embeddingsIdParts[0] + CONTENT_KEY_SEPARATOR + embeddingsIdParts[1];
  }

  public static String fileIdFromContentId(String contentId) {
    var embeddingsIdParts = contentId.split(CONTENT_KEY_SEPARATOR);
    if (embeddingsIdParts.length != 3) {
      LOG.warn("Expected a 3 part embeddings id, got {}. Returning empty string.", contentId);
      return "";
    }
    // we only keep the file id part, discarding doc name and embeddings sequence
    return embeddingsIdParts[1];
  }

  public static String reconstructDocumentLinkFromEmbeddingsId(
      String embeddingsId, GoogleDriveAPIMimeTypes type) {
    return switch (type) {
          case DOCUMENT -> "https://docs.google.com/document/d/";
          case SPREADSHEET -> "https://docs.google.com/spreadsheets/d/";
          case PRESENTATION -> "https://docs.google.com/presentation/d/";
          default -> "NA ";
        }
        + fileIdFromContentId(embeddingsId);
  }
}
