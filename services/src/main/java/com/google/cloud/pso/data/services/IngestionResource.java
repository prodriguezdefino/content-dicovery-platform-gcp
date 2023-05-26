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
package com.google.cloud.pso.data.services;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URISyntaxException;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/ingest/content")
public class IngestionResource {

  @Inject PubSubService psService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public GoogleDriveIngestionResponse query(GoogleDriveIngestionRequest request)
      throws URISyntaxException {
    var msgId = psService.publishMessage(request.toProperJSON());
    return new GoogleDriveIngestionResponse("Ingestion trace id: " + msgId);
  }

  public record GoogleDriveIngestionRequest(String url) {

    public String toProperJSON() throws URISyntaxException {
      var json = new JsonObject();
      json.addProperty("url", new URI(url).toString());
      return json.toString();
    }
  }

  public record GoogleDriveIngestionResponse(String status) {}
}