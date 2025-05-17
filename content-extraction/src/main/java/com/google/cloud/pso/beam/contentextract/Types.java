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
package com.google.cloud.pso.beam.contentextract;

import com.google.cloud.pso.rag.common.Ingestion;
import com.google.cloud.pso.rag.common.Ingestion.GoogleDrive;
import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.drive.GoogleDriveAPIMimeTypes;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.beam.sdk.coders.CoderException;
import org.apache.beam.sdk.coders.CustomCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.coders.MapCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;

/** */
public class Types {

  private static final String RETRY_COUNT_KEY = "retries";

  @DefaultCoder(TransportCoder.class)
  public record Transport(String contentId, Map<String, String> metadata) {

    public Integer retriesCount() {
      return retriesCount(metadata);
    }

    Integer retriesCount(Map<String, String> map) {
      return Optional.ofNullable(map.get(RETRY_COUNT_KEY)).map(Integer::valueOf).orElse(0);
    }

    public GoogleDriveAPIMimeTypes mimeType() {
      return Optional.ofNullable(metadata.get(GoogleDriveAPIMimeTypes.MIME_TYPE_KEY))
          .map(GoogleDriveAPIMimeTypes::get)
          .orElse(GoogleDriveAPIMimeTypes.UNKNOWN);
    }
  }

  public static class TransportCoder extends CustomCoder<Transport> {

    private static final TransportCoder INSTANCE = new TransportCoder();
    static final StringUtf8Coder dataCoder = StringUtf8Coder.of();
    static final MapCoder metadataCoder = MapCoder.of(StringUtf8Coder.of(), StringUtf8Coder.of());

    private TransportCoder() {}

    public static TransportCoder of() {
      return INSTANCE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void encode(Transport value, OutputStream outStream) throws CoderException, IOException {
      dataCoder.encode(value.contentId, outStream);
      metadataCoder.encode(value.metadata, outStream);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Transport decode(InputStream inStream) throws CoderException, IOException {
      return new Transport(dataCoder.decode(inStream), metadataCoder.decode(inStream));
    }
  }

  public static class DocumentIdError extends IllegalArgumentException {
    public DocumentIdError(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class DocumentContentError extends IllegalArgumentException {
    public DocumentContentError(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public sealed interface ProcessingError extends Serializable permits Discardable, Retriable {}

  public record Discardable(String element, Exception errorInfo) implements ProcessingError {
    public String toErrorString() {
      return ImmutableMap.of(
              "className", errorInfo.getClass().getName(),
              "message", errorInfo.getMessage(),
              "stackTrace", Arrays.toString(errorInfo.getStackTrace()))
          .toString();
    }
  }

  public record Retriable(String contentId, Map<String, String> metadata, Integer retryCount)
      implements ProcessingError {

    public PubsubMessage toPubsubMessage() {
      return InteractionHelper.jsonMapper(new GoogleDrive(contentId()))
          .map(body -> new PubsubMessage(body.getBytes(), metadata()))
          .orElseThrow(
              error -> new RuntimeException("Problems while trying to serialize retriable."));
    }
  }

  public enum Operation {
    UPSERT,
    DELETE
  }

  public record Content(String key, List<String> content, Ingestion.SupportedType type)
      implements Serializable {}

  public record ContentChunks(String key, List<String> chunks) implements Serializable {}

  public record IndexableContent(String key, String content, List<Double> embedding)
      implements Serializable {}

  public record IndexableContentOperation(IndexableContent content, Operation operation)
      implements Serializable {}
}
