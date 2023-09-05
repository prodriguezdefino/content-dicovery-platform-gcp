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
package com.google.cloud.pso.data.services.beans;

import com.google.cloud.pso.beam.contentextract.clients.Types;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import javax.ws.rs.FormParam;

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
      String content,
      String previousConversationSummary,
      List<LinkAndDistance> sourceLinks,
      List<Types.CitationMetadata> citationMetadata,
      List<Types.SafetyAttributes> safetyAttributes) {}

  public static class MultipartContentIngestionRequest {
    @FormParam("documentId")
    String documentId;

    @FormParam("documentContent")
    byte[] content;

    public String toProperJSON() throws URISyntaxException {
      var json = new JsonObject();
      var document = new JsonObject();
      document.addProperty("id", documentId);
      document.addProperty("content", Base64.getEncoder().encodeToString(content));
      json.add("document", document);
      return json.toString();
    }
  }

  public record IngestionResponse(String status) {}

  public record GoogleDriveIngestionRequest(String url, List<String> urls) {

    public String toProperJSON() throws URISyntaxException {
      var json = new JsonObject();
      if (url != null) {
        json.addProperty("url", checkUrl(url));
      } else if (urls != null) {
        var array = new JsonArray();
        urls.stream().map(u -> checkUrl(u)).forEach(u -> array.add(u));
        json.add("urls", array);
      } else throw new IllegalArgumentException("Malformed request.");

      return json.toString();
    }

    String checkUrl(String maybeUrl) {
      try {
        return new URI(maybeUrl).toString();
      } catch (URISyntaxException ex) {
        throw new IllegalArgumentException(ex);
      }
    }
  }

  public record ContentKeys(List<String> keys) {}

  public record ContentInfo(List<Info> content) {}

  public record ContentUrl(List<String> urls) {}

  public record Info(String name, String driveId) {}

  public record ResourceConfiguration(
      Boolean logInteractions,
      String matchingEngineIndexDeploymentId,
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
}
