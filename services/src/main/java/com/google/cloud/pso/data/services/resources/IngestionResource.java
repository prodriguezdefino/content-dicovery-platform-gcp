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
package com.google.cloud.pso.data.services.resources;

import com.google.cloud.pso.data.services.beans.PubSubService;
import com.google.cloud.pso.data.services.exceptions.IngestionResourceException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/ingest/content")
public class IngestionResource {
  private static final Logger LOG = LoggerFactory.getLogger(IngestionResource.class);
  private static final Gson GSON = new Gson();

  @Inject PubSubService psService;

  @POST
  @Path("/gdrive")
  @Produces(MediaType.APPLICATION_JSON)
  public IngestionResponse ingestGoogleDriveUrls(GoogleDriveIngestionRequest request) {
    try {
      var msgId = psService.publishMessage(request.toProperJSON());
      return new IngestionResponse("Ingestion trace id: " + msgId);
    } catch (Exception ex) {
      var msg = "Problems while executing the ingestion resource. ";
      LOG.error(msg, ex);
      throw new IngestionResourceException(request.url, request.urls, msg + ex.getMessage(), ex);
    }
  }

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

  @POST
  @Path("/multipart")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public IngestionResponse ingestMultipartContent(MultipartContentIngestionRequest request)
      throws URISyntaxException {
    var msgId = psService.publishMessage(request.toProperJSON());
    return new IngestionResponse("Ingestion trace id: " + msgId);
  }

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
}
