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
package com.google.cloud.pso.rag.common;

import java.util.List;
import java.util.Optional;

/** */
public interface Ingestion {

  enum MimeType {
    TEXT,
    PDF,
    IMAGE,
    VIDEO,
    AUDIO
  }

  record Request(
      Optional<List<String>> googleDriveUrls,
      Optional<RawData> rawData,
      Optional<List<Reference>> references) {
    public Request(RawData data) {
      this(Optional.empty(), Optional.of(data), Optional.empty());
    }

    public Result<Void, Exception> validate() {
      if (googleDriveUrls.isEmpty() && rawData.isEmpty() && references.isEmpty()) {
        return Result.failure(
            new IllegalArgumentException(
                "At least a Google Drive file, raw data or location reference should be provided."));
      }
      return Result.success(null);
    }
  }

  record GoogleDrive(String url) {}

  record RawData(String id, byte[] data, MimeType mimeType) {}

  record Reference(String url, MimeType mimeType) {}
}
