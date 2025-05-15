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
package com.google.cloud.pso.data.services.beans;

import com.google.cloud.pso.beam.contentextract.clients.Types;
import com.google.cloud.pso.rag.common.Ingestion.MimeType;
import com.google.cloud.pso.rag.common.Ingestion.RawData;
import com.google.cloud.pso.rag.common.Ingestion.Request;
import com.google.cloud.pso.rag.common.Result;
import jakarta.ws.rs.FormParam;
import java.util.List;

/** */
public class ServiceTypes {

  public record LinkAndDistance(String link, Double distance) {}

  public record ContentAndMetadata(String content, String link, Double distance) {
    public LinkAndDistance toLinkAndDistance() {
      return new LinkAndDistance(link(), distance());
    }
  }

  public record QueryParameters(
      String botContextExpertise,
      Boolean includeOwnKnowledgeEnrichment,
      Integer maxNeighbors,
      Double temperature,
      Integer maxOutputTokens,
      Integer topK,
      Double topP) {}

  public record UserQuery(String text, String sessionId, QueryParameters parameters) {}

  public record QueryResult(
      String content, String previousConversationSummary, List<LinkAndDistance> sourceLinks) {}

  public static class MultipartContentIngestionRequest {
    @FormParam("documentId")
    String documentId;

    @FormParam("documentContent")
    byte[] content;

    @FormParam("mimeType")
    String mimeType;

    public Result<Request, Exception> toIngestionRequest() {
      if (documentId != null
          && !documentId.isBlank()
          && content != null
          && mimeType != null
          && !mimeType.isBlank())
        return Result.success(
            new Request(new RawData(documentId, content, MimeType.valueOf(mimeType))));
      return Result.failure(
          new IllegalArgumentException(
              "The request should have a valid and not empty id, content and mime type."));
    }
  }

  public record SimpleResponse(String status) {}

  public record ContentKeys(List<String> keys) {}

  public record ContentInfo(List<Info> content) {}

  public record ContentUrl(List<String> urls) {}

  public record Info(String name, String driveId) {}

  public record ResourceConfiguration(
      Boolean logInteractions,
      Integer maxNeighbors,
      Double maxNeighborDistance,
      Double temperature,
      Integer maxOutputTokens,
      Integer topK,
      Double topP) {}

  public record BigTableConfiguration(
      String instanceName,
      String contentTableName,
      String queryContextTableName,
      String contentColumnFamily,
      String queryContextColumnFamily,
      String columnQualifierContent,
      String columnQualifierLink,
      String columnQualifierContext,
      String projectId) {}

  public record ContentByKeyResponse(String key, String content, String sourceLink) {

    public static ContentByKeyResponse empty() {
      return empty("");
    }

    public static ContentByKeyResponse empty(String key) {
      return new ContentByKeyResponse(key, "", "");
    }
  }

  public record QAndA(String question, String answer) {

    public List<Types.Exchange> toExchange() {
      return List.of(new Types.Exchange("user", question), new Types.Exchange("bot", answer));
    }
  }

  public record ConversationContextBySessionResponse(String session, List<QAndA> qAndAs) {}
}
