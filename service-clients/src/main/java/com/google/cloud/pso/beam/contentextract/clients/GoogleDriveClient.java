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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.slides.v1.Slides;
import com.google.cloud.pso.rag.common.GoogleCredentialsCache;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;

/** */
public class GoogleDriveClient implements Serializable {

  private static final NetHttpTransport HTTP_TRANSPORT = Utilities.createTransport();
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String APP_NAME = "DocContentExtractor";
  private static final Drive DRIVE_SERVICE =
      new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
  private static final Docs DOCS_SERVICE =
      new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
  private static final Sheets SHEETS_SERVICE =
      new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();
  private static final Slides SLIDES_SERVICE =
      new Slides.Builder(HTTP_TRANSPORT, JSON_FACTORY, null).setApplicationName(APP_NAME).build();

  private final String credentialsPrincipal;

  public GoogleDriveClient(String credentialsPrincipal) {
    this.credentialsPrincipal = credentialsPrincipal;
  }

  public static GoogleDriveClient create(String credentialsPrincipal) {
    return new GoogleDriveClient(credentialsPrincipal);
  }

  String retrieveAccessToken() {
    return GoogleCredentialsCache.retrieveAccessToken(credentialsPrincipal);
  }

  public Drive.Files.Get driveFileGetClient(String driveId) throws IOException {
    return DRIVE_SERVICE
        .files()
        .get(driveId)
        .setOauthToken(retrieveAccessToken())
        .setFields("id, mimeType, modifiedTime, name, webViewLink");
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

  public Sheets.Spreadsheets.Get sheetGetClient(String spreadsheetId) throws IOException {
    return SHEETS_SERVICE.spreadsheets().get(spreadsheetId).setAccessToken(retrieveAccessToken());
  }

  public Sheets.Spreadsheets.Values.Get sheetValuesGetClient(
      String spreadsheetId, String sheetTitle) throws IOException {
    return SHEETS_SERVICE
        .spreadsheets()
        .values()
        .get(spreadsheetId, sheetTitle)
        .setAccessToken(retrieveAccessToken());
  }

  public Slides.Presentations.Get slideGetClient(String slideId) throws IOException {
    return SLIDES_SERVICE.presentations().get(slideId).setAccessToken(retrieveAccessToken());
  }
}
