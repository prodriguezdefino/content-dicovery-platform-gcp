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
package com.google.cloud.pso.beam.contentextract.clients;

import static com.google.cloud.pso.beam.contentextract.clients.utils.Utilities.buildRetriableExecutorForOperation;
import static com.google.cloud.pso.beam.contentextract.clients.utils.Utilities.executeOperation;

import com.google.cloud.pso.beam.contentextract.clients.exceptions.PalmException;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;

/** */
public class PalmClient extends VertexAIClient {

  private final String projectId;
  private final String region;

  PalmClient(String projectId, String region, String credentialsSecretManagerId) {
    super(credentialsSecretManagerId);
    this.projectId = projectId;
    this.region = region;
  }

  public static PalmClient create(
      String projectId, String region, String credentialsSecretManagerId) {
    return new PalmClient(projectId, region, credentialsSecretManagerId);
  }

  public Types.PalmResponse sendPromptToModel(Types.PalmRequest palmReq) {

    try {
      var uriStr =
          String.format(
              "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/chat-bison:predict",
              region, projectId, region);
      var body = GSON.toJson(palmReq);
      var request = createHTTPBasedRequest(uriStr, body);
      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200)
        throw new RuntimeException(
            String.format(
                "Error returned by PaLM: %d, %s \nRequest payload: %s ",
                response.statusCode(), response.body(), request.toString()));

      return GSON.fromJson(response.body(), Types.PalmResponse.class);
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to retrieve prompt response from PaLM.";
      throw new RuntimeException(msg, ex);
    }
  }

  public Types.PalmResponse sendPromptToModelWithRetries(Types.PalmRequest palmReq) {
    return executeOperation(
        buildRetriableExecutorForOperation(
            "retrieveEmbeddings", Lists.newArrayList(PalmException.class)),
        () -> sendPromptToModel(palmReq));
  }
}
