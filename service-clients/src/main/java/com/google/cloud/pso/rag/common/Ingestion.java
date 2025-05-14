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
      Optional<GoogleDrive> googleDrive,
      Optional<RawData> rawData,
      Optional<List<Reference>> references) {

    public Result<Void, IllegalArgumentException> validate() {
      if (googleDrive.isEmpty() && rawData.isEmpty() && references.isEmpty()) {
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
