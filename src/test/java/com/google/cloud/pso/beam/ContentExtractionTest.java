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
import com.google.api.services.docs.v1.model.Body;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphElement;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.TextRun;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.cloud.pso.beam.contentextract.utils.DocFetcher;
import com.google.cloud.pso.beam.contentextract.utils.GoogleDriveAPIMimeTypes;
import com.google.cloud.pso.beam.contentextract.utils.ServiceClientProvider;
import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import java.io.IOException;
import java.util.List;
import org.apache.beam.sdk.values.KV;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** */
public class ContentExtractionTest {

  private static final String PUBLIC_DOCUMENT_URL =
      "https://docs.google.com/document/d/some_made_up_id";

  Document createMockDocument() {
    return new Document()
        .setBody(
            new Body()
                .setContent(
                    List.of(
                        new StructuralElement()
                            .setParagraph(
                                new Paragraph()
                                    .setElements(
                                        List.of(
                                            new ParagraphElement()
                                                .setTextRun(
                                                    new TextRun()
                                                        .setContent(
                                                            "Some random content for test purposes."))))))))
        .setTitle("A document title")
        .setDocumentId("SomeDocumentID");
  }

  File createMockFile() {
    return new File().setMimeType(GoogleDriveAPIMimeTypes.FILE.mimeType()).setId("SomeFileId");
  }

  FileList createMockFileList() {
    return new FileList().setIncompleteSearch(false).setFiles(List.of(createMockFile()));
  }

  ServiceClientProvider createMockClientProvider() throws IOException {
    var docGet = Mockito.mock(Docs.Documents.Get.class);
    Mockito.when(docGet.execute()).thenReturn(createMockDocument());

    var driveFileList = Mockito.mock(Drive.Files.List.class);
    Mockito.when(driveFileList.execute()).thenReturn(createMockFileList());

    var driveFileGet = Mockito.mock(Drive.Files.Get.class);
    Mockito.when(driveFileGet.execute()).thenReturn(createMockFile());

    var mockedProvider = Mockito.mock(ServiceClientProvider.class);
    Mockito.when(mockedProvider.documentGetClient(ArgumentMatchers.any())).thenReturn(docGet);
    Mockito.when(mockedProvider.driveFileGetClient(ArgumentMatchers.any()))
        .thenReturn(driveFileGet);
    Mockito.when(
            mockedProvider.driveFileListClient(
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(driveFileList);
    return mockedProvider;
  }

  @Test
  public void extractContentFromDocument() throws IOException {
    var mockedProvider = createMockClientProvider();

    var fetcher = DocFetcher.create(mockedProvider);
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

  @Test
  public void testEmbeddingToJSON() {
    KV<String, Iterable<Iterable<Double>>> kv =
        KV.of("someid", List.of(List.of(3.5, 4.5, 0.9), List.of(1.2, 4.2, 3.1)));

    var res = Utilities.embeddingToRightTypes(kv);
    System.out.println(res);
  }

  @Test
  public void testNewId() {
    var expected = "name_some_2022_info___18Ds2syb04";
    Assert.assertEquals(
        expected, Utilities.newIdFromTitleAndDriveId("[name] some 2022 info", "18Ds2syb04"));
  }
}
