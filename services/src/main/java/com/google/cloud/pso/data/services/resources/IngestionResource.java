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
import com.google.cloud.pso.data.services.beans.ServiceTypes.GoogleDriveIngestionRequest;
import com.google.cloud.pso.data.services.beans.ServiceTypes.IngestionResponse;
import com.google.cloud.pso.data.services.beans.ServiceTypes.MultipartContentIngestionRequest;
import com.google.cloud.pso.data.services.exceptions.IngestionResourceException;
import java.net.URISyntaxException;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
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
  public IngestionResponse ingestGoogleDriveUrls(GoogleDriveIngestionRequest request) {
    try {
      var msgId = psService.publishMessage(request.toProperJSON());
      return new IngestionResponse("Ingestion trace id: " + msgId);
    } catch (Exception ex) {
      var msg = "Problems while executing the ingestion resource. ";
      LOG.error(msg, ex);
      throw new IngestionResourceException(
          request.url(), request.urls(), msg + ex.getMessage(), ex);
    }
  }

  @POST
  @Path("/multipart")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Timed(name = "content.ingest.strutured", unit = MetricUnits.MILLISECONDS)
  public IngestionResponse ingestMultipartContent(MultipartContentIngestionRequest request)
      throws URISyntaxException {
    var msgId = psService.publishMessage(request.toProperJSON());
    return new IngestionResponse("Ingestion trace id: " + msgId);
  }
}
