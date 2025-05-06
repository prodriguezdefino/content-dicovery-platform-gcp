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

import com.google.cloud.pso.beam.contentextract.clients.GoogleDriveClient;
import com.google.cloud.pso.data.services.beans.BeansProducer;
import com.google.cloud.pso.data.services.beans.BigTableService;
import com.google.cloud.pso.data.services.beans.ServiceTypes.ContentInfo;
import com.google.cloud.pso.data.services.beans.ServiceTypes.ContentKeys;
import com.google.cloud.pso.data.services.beans.ServiceTypes.ContentUrl;
import com.google.cloud.pso.data.services.beans.ServiceTypes.Info;
import com.google.cloud.pso.rag.vector.VectorRequests;
import com.google.cloud.pso.rag.vector.Vectors;
import com.google.common.collect.Lists;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.stream.Collectors;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Path("/admin/content")
@SessionScoped
public class AdminResource {

  private static final Logger LOG = LoggerFactory.getLogger(QueryResource.class);

  @Inject BigTableService btService;
  @Inject GoogleDriveClient googleDriveClient;
  @Inject BeansProducer.Interactions interactions;

  @GET
  @Path("/ids")
  @Produces(MediaType.APPLICATION_JSON)
  public ContentKeys retrieveAllContentKeys() {
    return new ContentKeys(btService.retrieveAllContentEntries());
  }

  @GET
  @Path("/infos")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "content.admin.retrieveall.info", unit = MetricUnits.MILLISECONDS)
  public ContentInfo retrieveAllContentInfo() {
    return new ContentInfo(
        btService.retrieveAllContentEntries().stream()
            .map(
                key -> {
                  var keyComponents = key.split("___");
                  if (keyComponents.length != 3) {
                    LOG.warn("Key with non expected components length {}", key);
                    return null;
                  }
                  return new Info(keyComponents[0], keyComponents[1]);
                })
            .filter(info -> info != null)
            .collect(Collectors.toSet())
            .stream()
            .toList());
  }

  @GET
  @Path("/urls")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "content.admin.retrieveall.urls", unit = MetricUnits.MILLISECONDS)
  public ContentUrl retrieveAllContentURls() {
    return new ContentUrl(
        btService.retrieveAllContentEntries().stream()
            .map(
                key -> {
                  var keyComponents = key.split("___");
                  if (keyComponents.length != 3) {
                    LOG.warn("Key with non expected components length {}", key);
                    return null;
                  }
                  return keyComponents[1];
                })
            .filter(id -> id != null)
            .collect(Collectors.toSet())
            .stream()
            .parallel()
            .map(
                fileId -> {
                  try {
                    return googleDriveClient.driveFileGetClient(fileId).execute().getWebViewLink();
                  } catch (IOException ex) {
                    var msg = "Problems while retrieving Google Drive file info.";
                    LOG.error(msg, ex);
                    throw new RuntimeException(msg, ex);
                  }
                })
            .toList());
  }

  @DELETE
  @Path("/ids")
  @Consumes(MediaType.APPLICATION_JSON)
  @Timed(name = "content.admin.delete.key", unit = MetricUnits.MILLISECONDS)
  public void deleteContentKey(ContentKeys contentKeys) {
    Vectors.removeVectors(VectorRequests.remove(interactions.vectorStorage(), contentKeys.keys()));
    btService.deleteRowsByKeys(contentKeys.keys());
  }

  @DELETE
  @Path("/info")
  @Consumes(MediaType.APPLICATION_JSON)
  @Timed(name = "content.admin.delete.info", unit = MetricUnits.MILLISECONDS)
  public void deleteAllContentWithInfo(ContentInfo contentInfo) {
    var contentIdsToDelete = Lists.<String>newArrayList();
    var prefixesToCheck =
        contentInfo.content().stream()
            .map(i -> i.name() + "___" + i.driveId())
            .collect(Collectors.toSet());
    for (var key : btService.retrieveAllContentEntries()) {
      var keyComponents = key.split("___");
      if (keyComponents.length != 3) {
        LOG.warn("Key with non expected components length {}", key);
        continue;
      }
      var prefix = keyComponents[0] + "___" + keyComponents[1];
      if (prefixesToCheck.contains(prefix)) {
        contentIdsToDelete.add(key);
      }
    }
    Vectors.removeVectors(VectorRequests.remove(interactions.vectorStorage(), contentIdsToDelete));
    btService.deleteRowsByKeys(contentIdsToDelete);
  }
}
