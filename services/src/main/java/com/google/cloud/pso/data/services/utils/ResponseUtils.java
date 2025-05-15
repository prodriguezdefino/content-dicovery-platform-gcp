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
package com.google.cloud.pso.data.services.utils;

import com.google.cloud.pso.data.services.beans.ServiceTypes.SimpleResponse;
import jakarta.ws.rs.core.Response;

/** */
public class ResponseUtils {

  public static Response badRequest(Throwable ex) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new SimpleResponse(ex.getMessage()))
        .build();
  }

  public static Response serverError(Throwable ex) {
    return Response.serverError()
        .entity(new SimpleResponse("Error while processing the ingestion. " + ex.getMessage()))
        .build();
  }

  public static Response ok(String message) {
    return Response.ok(new SimpleResponse(message)).build();
  }
}
