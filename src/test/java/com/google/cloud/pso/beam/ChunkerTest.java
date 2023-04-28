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

import com.google.cloud.pso.beam.contentextract.utils.Utilities;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ChunkerTest {

  private static final Logger LOG = LoggerFactory.getLogger(ChunkerTest.class);

  /*
   - terraform related resources
    - google_iap_client
    - cloud secrets: store credentials
      - download them and store them at pipeline startup 
   */
  private static final String PUBLIC_DOCUMENT_URL = "https://docs.google.com/document/d/1ydVaJJeL1EYbWtlfj9TPfBTE5IBADkQfZrQaBZxqXGs/edit";

  @Test
  public void extractContentFromDocument() {
    var docId = Utilities.extractIdFromURL(PUBLIC_DOCUMENT_URL);
    Assert.assertNotNull(docId);
    var files = Utilities.retrieveDriveFiles(docId);
    files.forEach(file -> {
      var docContent = Utilities.retrieveDocumentContent(file.getId());
      var jsonLines = Utilities.docContentToKeyedJSONLFormat(docContent);
      System.out.println(docContent.getKey());
      jsonLines.forEach(line -> System.out.println(line.getValue()));
    });
  }

}
