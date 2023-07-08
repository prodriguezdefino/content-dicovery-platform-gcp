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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import com.google.cloud.pso.beam.contentextract.clients.utils.GoogleCredentialsCache;
import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;

/** */
public class GoogleDocClient implements Serializable {

  private static final NetHttpTransport HTTP_TRANSPORT = createTransport();
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String APP_NAME = "DocContentExtractor";
  private static final Drive DRIVE_SERVICE =
      new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
  private static final Docs DOCS_SERVICE =
      new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();

  private final String credentialsPrincipal;

  public GoogleDocClient(String credentialsPrincipal) {
    this.credentialsPrincipal = credentialsPrincipal;
  }

  public static GoogleDocClient create(String credentialsPrincipal) {
    return new GoogleDocClient(credentialsPrincipal);
  }

  String retrieveAccessToken() {
    return GoogleCredentialsCache.retrieveAccessToken(credentialsPrincipal);
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
