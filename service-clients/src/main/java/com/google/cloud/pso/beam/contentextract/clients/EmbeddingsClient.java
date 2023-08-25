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

import com.google.cloud.pso.beam.contentextract.clients.exceptions.EmbeddingsException;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A minimal REST based client to interact with the embeddings model in VertexAI. */
public class EmbeddingsClient extends VertexAIClient {
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddingsClient.class);

  private final String projectId;
  private final String region;

  EmbeddingsClient(String projectId, String region) {
    super();
    this.projectId = projectId;
    this.region = region;
  }

  public static EmbeddingsClient create(String projectId, String region) {
    return new EmbeddingsClient(projectId, region);
  }

  Types.EmbeddingsResponse retrieveEmbeddings(Types.EmbeddingRequest embeddingRequest) {
    try {
      var uriStr =
          String.format(
              "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/textembedding-gecko:predict",
              region, projectId, region);
      var body = GSON.toJson(embeddingRequest);
      var request = createHTTPBasedRequest(uriStr, body);
      var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200)
        throw new EmbeddingsException(
            String.format(
                "Error returned by embeddings model: %s \nRequest payload: %s ",
                response.toString(), request.toString()));

      return GSON.fromJson(response.body(), Types.EmbeddingsResponse.class);
    } catch (IOException | InterruptedException | URISyntaxException ex) {
      var msg = "Error while trying to retrieve embeddings from model.";
      LOG.error(msg, ex);
      throw new EmbeddingsException(msg, ex);
    }
  }

  public Types.EmbeddingsResponse retrieveEmbeddingsWithRetries(
      Types.EmbeddingRequest embeddingRequest) {
    return executeOperation(
        buildRetriableExecutorForOperation(
            "retrieveEmbeddings", Lists.newArrayList(EmbeddingsException.class)),
        () -> retrieveEmbeddings(embeddingRequest));
  }
}
