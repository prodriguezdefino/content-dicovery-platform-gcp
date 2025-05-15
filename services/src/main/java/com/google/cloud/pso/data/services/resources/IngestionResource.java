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
import com.google.cloud.pso.data.services.beans.ServiceTypes.MultipartContentIngestionRequest;
import com.google.cloud.pso.data.services.utils.ResponseUtils;
import com.google.cloud.pso.rag.common.Ingestion.Request;
import com.google.cloud.pso.rag.common.Result;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;

@Path("/ingest/content")
public class IngestionResource {
  @Inject PubSubService psService;

  @POST
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "content.ingest", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<Response> ingestContent(Request request) {
    return processResult(request.validate().flatMap(__ -> psService.publishIngestion(request)));
  }

  @POST
  @Path("/multipart")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Timed(name = "content.ingest.multipart", unit = MetricUnits.MILLISECONDS)
  public CompletableFuture<Response> ingestMultipartContent(
      MultipartContentIngestionRequest request) {
    return processResult(
        request.toIngestionRequest().flatMap(body -> psService.publishIngestion(body)));
  }

  static CompletableFuture<Response> processResult(
      Result<CompletableFuture<String>, ? extends Throwable> result) {
    return result
        .map(
            future ->
                future
                    .thenApply(msgId -> ResponseUtils.ok("Ingestion trace id: " + msgId))
                    .exceptionally(ex -> ResponseUtils.serverError(ex)))
        .orElse(ex -> CompletableFuture.completedFuture(ResponseUtils.badRequest(ex)));
  }
}
