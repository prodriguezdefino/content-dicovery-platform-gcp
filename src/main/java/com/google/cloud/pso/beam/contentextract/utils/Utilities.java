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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.TextRun;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.beam.sdk.values.KV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Utilities {

  private static final Logger LOG = LoggerFactory.getLogger(Utilities.class);

  private static final NetHttpTransport HTTP_TRANSPORT = createTransport();
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String APP_NAME = "DocContentExtractor";
  private static final List<String> SCOPES
    = List.of(DocsScopes.DOCUMENTS_READONLY, DocsScopes.DRIVE_READONLY);
  private static final Drive DRIVE_SERVICE = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
    .setApplicationName(APP_NAME)
    .build();
  private static final Docs DOCS_SERVICE = new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
    .setApplicationName(APP_NAME)
    .build();
  private static final LoadingCache<String, String> TOKEN_CACHE = CacheBuilder.<String, String>newBuilder()
    .expireAfterWrite(Duration.ofMinutes(5L))
    .build(new CacheLoader<String, String>() {
      @Override
      public String load(String key) {
        try {
          var credentials = getCredentialsFromFile();
          credentials.refreshIfExpired();
          var accessToken = credentials.refreshAccessToken();
          return accessToken.getTokenValue();
        } catch (IOException ex) {
          throw new RuntimeException("Problems while trying to retrieve access token.", ex);
        }
      }
    });

  enum GoogleDriveAPIMimeTypes {
    AUDIO("application/vnd.google-apps.audio"),
    DOCUMENT("application/vnd.google-apps.document"),
    THIRDPARTY_SHORTCUT("application/vnd.google-apps.drive-sdk"),
    DRAWING("application/vnd.google-apps.drawing"),
    FILE("application/vnd.google-apps.file"),
    FOLDER("application/vnd.google-apps.folder"),
    FORM("application/vnd.google-apps.form"),
    FUSION_TABLE("application/vnd.google-apps.fusiontable"),
    JAMBOARD("application/vnd.google-apps.jam"),
    MAP("application/vnd.google-apps.map"),
    PHOTO("application/vnd.google-apps.photo"),
    SLIDE("application/vnd.google-apps.presentation"),
    APP_SCRIPT("application/vnd.google-apps.script"),
    SHORTCUT("application/vnd.google-apps.shortcut"),
    SITE("application/vnd.google-apps.site"),
    SHEET("application/vnd.google-apps.spreadsheet"),
    UNKNOWN("application/vnd.google-apps.unknown"),
    VIDEO("application/vnd.google-apps.video");

    static public GoogleDriveAPIMimeTypes get(String value) {
      return Arrays
        .stream(GoogleDriveAPIMimeTypes.values())
        .filter(val -> val.mimeType().equals(value))
        .findAny()
        .orElseThrow(() -> new RuntimeException("MimeType value not recognized: " + value));
    }

    private final String mimeType;

    GoogleDriveAPIMimeTypes(String mimeType) {
      this.mimeType = mimeType;
    }

    public String mimeType() {
      return mimeType;
    }

  }

  static NetHttpTransport createTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException ex) {
      var errMsg = "Errors while trying to create a transport object.";
      LOG.error(errMsg, ex);
      throw new RuntimeException(ex);
    }
  }

  static GoogleCredentials getCredentialsFromFile() {
    try {
      return GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
    } catch (IOException ex) {
      var errMsg = "errors while trying to create credentials";
      LOG.error(errMsg, ex);
      throw new RuntimeException(errMsg, ex);
    }
  }

  static String retrieveAccessToken() {
    try {
      return TOKEN_CACHE.get("");
    } catch (ExecutionException ex) {
      var msg = "Error while trying to retrieve access token from cache";
      throw new RuntimeException(msg, ex);
    }
  }

  static FileList retrieveDriveQueryResults(String queryString, String pageToken) {
    try {
      return DRIVE_SERVICE.files().list()
        .setOauthToken(retrieveAccessToken())
        .setQ(queryString)
        .setPageToken(Optional.ofNullable(pageToken).orElse(""))
        .setSpaces("drive")
        .setPageSize(10)
        .setFields("nextPageToken, files(id, mimeType)")
        .execute();
    } catch (IOException ex) {
      var msg = "Error while trying to query drive, query: " + queryString + ", page token: " + pageToken;
      throw new RuntimeException(msg, ex);
    }
  }

  public static String extractIdFromURL(String url) {
    try {
      var parsedUrl = new URL(url);
      var path = parsedUrl.getPath();
      Preconditions.checkState(!path.isEmpty(), "The URL path should not be empty");
      Preconditions.checkState(path.contains("/d/"), "This path does not conform a Google drive one");
      var pathParts = path.split("/d/");
      if (pathParts.length < 2) {
        throw new IllegalArgumentException("The path does not contain a Google drive id, path: " + path);
      }
      var containsId = pathParts[1].split("/")[0];
      if (containsId.isEmpty()) {
        throw new IllegalArgumentException("Wrong URL: " + url);
      }
      return containsId;
    } catch (MalformedURLException ex) {
      throw new IllegalArgumentException("Problems while parsing the Google drive URL: " + url, ex);
    }
  }

  public static KV<String, List<String>> retrieveDocumentContent(String documentId) {
    try {
      var response = DOCS_SERVICE.documents().get(documentId).setAccessToken(retrieveAccessToken()).execute();
      var processedId = response.getTitle().replace(" ", "_").toLowerCase() + "-" + response.getDocumentId();
      return KV.of(processedId, response.getBody().getContent().stream()
        .map(a -> {
          var paragraph = a.getParagraph();
          if (paragraph != null) {
            return Optional.of(
              paragraph.getElements()
                .stream()
                .map(paragraphElement -> Optional.ofNullable(paragraphElement.getTextRun()).map(TextRun::getContent).orElse(""))
                .filter(paragraphContent -> !paragraphContent.isEmpty())
                .collect(Collectors.joining(" ")));
          } else {
            return Optional.<String>empty();
          }
        })
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(text -> !text.isBlank())
        .map(text -> text.replace("\n", ""))
        .toList());
    } catch (IOException ex) {
      var errMsg = "errors while trying to retrieve document content, id: " + documentId;
      throw new RuntimeException(errMsg, ex);
    }
  }

  public static List<KV<String, String>> docContentToKeyedJSONLFormat(KV<String, List<String>> content) {
    return content.getValue()
      .stream()
      .map(contentLine -> {
        var json = new JsonObject();
        json.addProperty("text", contentLine);
        return json.toString();
      })
      .map(jsonl -> KV.of(content.getKey(), jsonl))
      .toList();
  }

  public static List<File> retrieveDriveFiles(String id) {
    try {
      var maybeFile = DRIVE_SERVICE.files().get(id).setOauthToken(retrieveAccessToken()).execute();
      return switch (GoogleDriveAPIMimeTypes.get(maybeFile.getMimeType())) {
        case DOCUMENT ->
          List.of(maybeFile);
        case FOLDER -> {
          var queryString = String.format("'%s' in parents", id);

          var files = Lists.<File>newArrayList();
          var queryDone = false;
          String pageToken = null;
          while (!queryDone) {
            var results = retrieveDriveQueryResults(queryString, pageToken);
            files.addAll(results.getFiles());
            if (results.getNextPageToken() != null) {
              pageToken = results.getNextPageToken();
            } else {
              queryDone = true;
            }
          }
          yield files.stream()
          .flatMap(f -> {
            if (GoogleDriveAPIMimeTypes.get(f.getMimeType()).equals(GoogleDriveAPIMimeTypes.DOCUMENT)) {
              return Stream.of(f);
            } else {
              return retrieveDriveFiles(f.getId()).stream();
            }
          }).toList();
        }
        default -> {
          LOG.warn("Skipping file {}, mime type not supported {}", maybeFile.getId(), maybeFile.getMimeType());
          yield List.of();
        }
      };
    } catch (IOException ex) {
      var msg = "Error while trying to access the provided resource, id: " + id;
      throw new RuntimeException(msg, ex);
    }
  }
}
