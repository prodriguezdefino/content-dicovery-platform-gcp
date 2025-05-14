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
package com.google.cloud.pso.data.services.resources;

import com.google.cloud.pso.data.services.beans.PubSubService;
import com.google.cloud.pso.data.services.beans.ServiceTypes.GoogleDriveIngestionRequest;
import com.google.cloud.pso.data.services.beans.ServiceTypes.IngestionResponse;
import com.google.cloud.pso.data.services.beans.ServiceTypes.MultipartContentIngestionRequest;
import com.google.cloud.pso.data.services.exceptions.IngestionResourceException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/ingest/content")
public class IngestionResource {
  private static final Logger LOG = LoggerFactory.getLogger(IngestionResource.class);

  @Inject PubSubService psService;

  @POST
  @Path("/gdrive")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "content.ingest.gdrive", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<IngestionResponse> ingestGoogleDriveUrls(
      GoogleDriveIngestionRequest request) {
    return psService
        .publishMessage(request.toProperJSON())
        .thenApply(msgId -> new IngestionResponse("Ingestion trace id: " + msgId))
        .exceptionally(
            ex -> {
              var msg = "Problems while executing the ingestion resource. ";
              LOG.error(msg, ex);
              throw new IngestionResourceException(
                  request.url(), request.urls(), msg + ex.getMessage(), ex);
            });
  }

  @POST
  @Path("/multipart")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Timed(name = "content.ingest.strutured", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<IngestionResponse> ingestMultipartContent(
      MultipartContentIngestionRequest request) {
    return psService
        .publishMessage(request.toProperJSON())
        .thenApply(msgId -> new IngestionResponse("Ingestion trace id: " + msgId))
        .exceptionally(
            ex -> {
              var msg = "Problems while executing the ingestion resource. ";
              LOG.error(msg, ex);
              throw new IngestionResourceException(msg + ex.getMessage(), ex);
            });
  }
}
