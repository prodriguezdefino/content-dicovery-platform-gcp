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

import com.google.api.services.docs.v1.model.TextRun;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.cloud.pso.beam.contentextract.Types.*;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.beam.sdk.values.KV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class DocContentRetriever implements Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(DocContentRetriever.class);

  private final GoogleDocClient clientProvider;

  private DocContentRetriever(GoogleDocClient serviceClientProvider) {
    this.clientProvider = serviceClientProvider;
  }

  public static DocContentRetriever create(GoogleDocClient provider) {
    return new DocContentRetriever(provider);
  }

  public KV<String, List<String>> retrieveDocumentContent(String documentId) {
    try {
      var response = clientProvider.documentGetClient(documentId).execute();
      var processedId =
          Utilities.newIdFromTitleAndDriveId(response.getTitle(), response.getDocumentId());
      return KV.of(
          processedId,
          response.getBody().getContent().stream()
              .map(
                  a -> {
                    var paragraph = a.getParagraph();
                    if (paragraph != null) {
                      return Optional.of(
                          paragraph.getElements().stream()
                              .map(
                                  paragraphElement ->
                                      Optional.ofNullable(paragraphElement.getTextRun())
                                          .map(TextRun::getContent)
                                          .orElse(""))
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

  public List<File> retrieveDriveFiles(String id) {
    try {
      var maybeFile = clientProvider.driveFileGetClient(id).execute();
      return switch (GoogleDriveAPIMimeTypes.get(maybeFile.getMimeType())) {
        case DOCUMENT -> List.of(maybeFile);
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
