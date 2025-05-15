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
package com.google.cloud.pso.beam.contentextract.utils;

import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.TextRun;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.cloud.pso.beam.contentextract.Types.*;
import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveAPIMimeTypes;
import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveClient;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import com.google.cloud.pso.beam.contentextract.transforms.RefreshContentTransform.ContentProcessed;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class DocContentRetriever implements Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(DocContentRetriever.class);

  private final GoogleDriveClient clientProvider;

  private DocContentRetriever(GoogleDriveClient serviceClientProvider) {
    this.clientProvider = serviceClientProvider;
  }

  public static DocContentRetriever create(GoogleDriveClient provider) {
    return new DocContentRetriever(provider);
  }

  static Optional<String> retrieveParagraphContent(Paragraph p) {
    return Optional.ofNullable(p)
        .flatMap(par -> Optional.ofNullable(par.getElements()))
        .map(
            elems ->
                elems.stream()
                    .map(
                        paragraphElement ->
                            Optional.ofNullable(paragraphElement.getTextRun())
                                .map(TextRun::getContent)
                                .orElse(""))
                    .filter(paragraphContent -> !paragraphContent.isEmpty())
                    .collect(Collectors.joining(" ")));
  }

  public Content retrieveGoogleDriveFileContent(String fileId, GoogleDriveAPIMimeTypes type) {
    return switch (type) {
      case DOCUMENT -> retrieveDocumentContent(fileId);
      case SPREADSHEET -> retrieveSpreadsheetContent(fileId);
      case PRESENTATION -> retrievePresentationContent(fileId);
      default -> throw new IllegalArgumentException("Not supported mime-type: " + type.name());
    };
  }

  List<List<Object>> retrieveSpreadsheetSheetValues(String sheetId, Sheet sheet) {
    try {
      return clientProvider
          .sheetValuesGetClient(sheetId, sheet.getProperties().getTitle())
          .execute()
          .getValues();
    } catch (Exception ex) {
      var errMsg = "errors while trying to retrieve spreadsheet content, id: " + sheetId;
      LOG.error(errMsg, ex);
      throw new DocumentContentError(errMsg, ex);
    }
  }

  Content retrieveSpreadsheetContent(String sheetId) {
    try {
      var response = clientProvider.sheetGetClient(sheetId).execute();
      var file = clientProvider.driveFileGetClient(sheetId).execute();
      return new Content(
          Utilities.newIdFromTitleAndDriveId(file.getName(), response.getSpreadsheetId()),
          response.getSheets().stream()
              .flatMap(sheet -> retrieveSpreadsheetSheetValues(sheetId, sheet).stream())
              .flatMap(valueList -> valueList.stream())
              .map(
                  value ->
                      Optional.ofNullable(value)
                          .map(v -> v.toString())
                          .filter(v -> !v.isEmpty() && !v.isBlank())
                          .map(v -> v.replace("\n", " "))
                          .orElse(""))
              .filter(text -> !text.isBlank())
              .toList());
    } catch (Exception ex) {
      var errMsg = "errors while trying to retrieve spreadsheet content, id: " + sheetId;
      LOG.error(errMsg, ex);
      throw new DocumentContentError(errMsg, ex);
    }
  }

  Content retrievePresentationContent(String presentationId) {
    try {
      var response = clientProvider.slideGetClient(presentationId).execute();
      var file = clientProvider.driveFileGetClient(presentationId).execute();
      return new Content(
          Utilities.newIdFromTitleAndDriveId(file.getName(), response.getPresentationId()),
          response.getSlides().stream()
              .flatMap(
                  slide ->
                      Optional.ofNullable(slide.getPageElements())
                          .map(List::stream)
                          .orElse(Stream.empty()))
              .flatMap(
                  elems ->
                      Optional.ofNullable(elems.getShape())
                          .map(shape -> shape.getText())
                          .map(tcont -> tcont.getTextElements().stream())
                          .orElse(Stream.empty()))
              .map(
                  textElems ->
                      Optional.ofNullable(textElems.getTextRun())
                          .map(trun -> trun.getContent())
                          .orElse(""))
              .filter(text -> !text.isBlank())
              .map(text -> text.replace("\n", ""))
              .toList());
    } catch (Exception ex) {
      var errMsg = "errors while trying to retrieve presentation content, id: " + presentationId;
      LOG.error(errMsg, ex);
      throw new DocumentContentError(errMsg, ex);
    }
  }

  Content retrieveDocumentContent(String documentId) {
    try {
      var response = clientProvider.documentGetClient(documentId).execute();
      var file = clientProvider.driveFileGetClient(documentId).execute();
      return new Content(
          Utilities.newIdFromTitleAndDriveId(file.getName(), response.getDocumentId()),
          response.getBody().getContent().stream()
              .map(
                  a ->
                      Optional.ofNullable(a.getParagraph())
                          .map(p -> retrieveParagraphContent(p))
                          .orElse(
                              Optional.ofNullable(a.getTable())
                                  .map(
                                      t ->
                                          t.getTableRows().stream()
                                              .flatMap(tr -> tr.getTableCells().stream())
                                              .flatMap(tc -> tc.getContent().stream())
                                              .map(
                                                  se -> retrieveParagraphContent(se.getParagraph()))
                                              .map(maybePar -> maybePar.orElse(""))
                                              .collect(Collectors.joining(" ")))))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .filter(text -> !text.isBlank())
              .map(text -> text.replace("\n", ""))
              .toList());
    } catch (Exception ex) {
      var errMsg = "errors while trying to retrieve document content, id: " + documentId;
      LOG.error(errMsg, ex);
      throw new DocumentContentError(errMsg, ex);
    }
  }

  FileList retrieveDriveQueryResults(String queryString, String pageToken) {
    try {
      return clientProvider.driveFileListClient(queryString, pageToken).execute();
    } catch (IOException ex) {
      var msg =
          "Error while trying to query drive, query: " + queryString + ", page token: " + pageToken;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  public GoogleDriveAPIMimeTypes retrieveFileType(String contentId) {
    return retrieveDriveFiles(contentId).stream()
        .findFirst()
        .map(f -> f.getMimeType())
        .map(type -> GoogleDriveAPIMimeTypes.get(type))
        .orElse(GoogleDriveAPIMimeTypes.UNKNOWN);
  }

  Optional<File> shouldRefreshContent(String contentId, Long lastProcessedInMillis) {
    return retrieveDriveFiles(contentId).stream()
        .map(
            file ->
                Optional.ofNullable(file.getModifiedTime())
                    .filter(modTime -> modTime.getValue() > lastProcessedInMillis)
                    .map(modTime -> file))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  public Optional<File> filterFilesUpForRefresh(ContentProcessed content) {
    return shouldRefreshContent(content.contentId(), content.processedAtInMillis());
  }

  public List<File> retrieveDriveFiles(String id) {
    try {
      // we check if id is an URL for which we need to extract the id, if not use that id
      var validId = Utilities.checkIfValidURL(id) ? Utilities.extractIdFromURL(id) : id;
      var maybeFile = clientProvider.driveFileGetClient(validId).execute();
      return switch (GoogleDriveAPIMimeTypes.get(maybeFile.getMimeType())) {
        case SPREADSHEET, DOCUMENT, PRESENTATION -> List.of(maybeFile);
        case FOLDER -> {
          var queryString = String.format("'%s' in parents", maybeFile.getId());

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
              .flatMap(
                  f -> {
                    if (GoogleDriveAPIMimeTypes.get(f.getMimeType())
                        .equals(GoogleDriveAPIMimeTypes.DOCUMENT)) {
                      return Stream.of(f);
                    } else {
                      return retrieveDriveFiles(f.getId()).stream();
                    }
                  })
              .toList();
        }
        default -> {
          LOG.warn(
              "Skipping file {}, mime type not supported {}",
              maybeFile.getId(),
              maybeFile.getMimeType());
          yield List.of();
        }
      };
    } catch (Exception ex) {
      var msg = "Error while trying to access the provided resource, id: " + id;
      LOG.error(msg, ex);
      throw new DocumentIdError(msg, ex);
    }
  }
}
