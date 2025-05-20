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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** */
public interface Ingestion {

  enum SupportedType {
    @JsonProperty("text/plain")
    TEXT("text/plain"),
    @JsonProperty("application/pdf")
    PDF("application/pdf"),
    @JsonProperty("link/pdf")
    PDF_LINK("link/pdf"),
    @JsonProperty("image/png")
    PNG("image/png"),
    @JsonProperty("link/png")
    PNG_LINK("link/png"),
    @JsonProperty("image/jpeg")
    JPEG("image/jpeg"),
    @JsonProperty("link/jpeg")
    JPEG_LINK("link/jpeg"),
    @JsonProperty("image/webp")
    WEBP("image/webp"),
    @JsonProperty("link/webp")
    WEBP_LINK("link/webp"),
    @JsonProperty("video/mp4")
    VIDEO("video/mp4"),
    @JsonProperty("link/mp4")
    VIDEO_LINK("link/mp4");

    private String value;

    private SupportedType(String value) {
      this.value = value;
    }

    public String mimeType() {
      return value;
    }

    public SupportedType toLink() {

      return switch (this) {
        case JPEG -> JPEG_LINK;
        case PDF -> PDF_LINK;
        case PNG -> PNG_LINK;
        case WEBP -> WEBP_LINK;
        case VIDEO -> VIDEO_LINK;
        default ->
            throw new IllegalArgumentException(this.name() + " does not have a link version.");
      };
    }

    public static SupportedType fromString(String value) {
      return Arrays.stream(SupportedType.values())
          .filter(type -> type.value.equals(value))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Invalid type: " + value));
    }
  }

  record Request(
      Optional<List<GoogleDrive>> googleDrive,
      Optional<RawData> rawData,
      Optional<List<Reference>> references) {
    public Request(RawData data) {
      this(Optional.empty(), Optional.of(data), Optional.empty());
    }

    public Request(GoogleDrive gdrive) {
      this(Optional.of(List.of(gdrive)), Optional.empty(), Optional.empty());
    }

    public Result<Void, Exception> validate() {
      if (googleDrive.isEmpty() && rawData.isEmpty() && references.isEmpty()) {
        return Result.failure(
            new IllegalArgumentException(
                "At least a Google Drive file, raw data or location reference should be provided."));
      }
      return Result.success(null);
    }
  }

  record GoogleDrive(String urlOrId) {}

  record RawData(String id, byte[] data, SupportedType mimeType) {}

  record Reference(String url, SupportedType mimeType) {}
}
