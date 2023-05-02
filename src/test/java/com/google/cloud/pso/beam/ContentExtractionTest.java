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
package com.google.cloud.pso.beam;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.drive.Drive;
import com.google.cloud.pso.beam.contentextract.utils.DocFetcher;
import com.google.cloud.pso.beam.contentextract.utils.ServiceClientProvider;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** */
public class ContentExtractionTest {

  private static final String PUBLIC_DOCUMENT_URL =
      "https://docs.google.com/document/d/1ydVaJJeL1EYbWtlfj9TPfBTE5IBADkQfZrQaBZxqXGs/edit";

  @Test
  public void extractContentFromDocument() throws IOException {
    var docGet = Mockito.mock(Docs.Documents.Get.class);
    Mockito.when(docGet.setAccessToken(ArgumentMatchers.any())).thenReturn(docGet);
    
    
    var driveFileList = Mockito.mock(Drive.Files.List.class);
    var driveFileGet = Mockito.mock(Drive.Files.Get.class);

    var mockedProvider = Mockito.mock(ServiceClientProvider.class);
    Mockito.when(mockedProvider.documentGetClient(ArgumentMatchers.any())).thenReturn(docGet);
    Mockito.when(mockedProvider.driveFileGetClient(ArgumentMatchers.any()))
        .thenReturn(driveFileGet);
    Mockito.when(mockedProvider.driveFileListClient()).thenReturn(driveFileList);

    var fetcher = DocFetcher.createForTests(mockedProvider);
    var docId = Utilities.extractIdFromURL(PUBLIC_DOCUMENT_URL);
    Assert.assertNotNull(docId);
    var files = fetcher.retrieveDriveFiles(docId);
    files.forEach(
        file -> {
          var docContent = fetcher.retrieveDocumentContent(file.getId());
          var jsonLines = Utilities.docContentToKeyedJSONLFormat(docContent);
          System.out.println(docContent.getKey());
          jsonLines.forEach(line -> System.out.println(line.getValue()));
        });
  }
}
