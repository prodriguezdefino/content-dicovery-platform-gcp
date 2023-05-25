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
package com.google.cloud.pso.data.query.service;

import com.google.cloud.pso.beam.contentextract.clients.EmbeddingsClient;
import com.google.cloud.pso.beam.contentextract.clients.MatchingEngineClient;
import com.google.cloud.pso.beam.contentextract.clients.PalmClient;
import com.google.cloud.pso.beam.contentextract.clients.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Base64;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** */
@Singleton
public class BeansProducer {

  @Inject
  @ConfigProperty(name = "secretmanager.configuration.version")
  String secretManagerConfigurationVersion;

  private String projectId;
  private String region;
  private String credentialsSecretManagerId;
  private String matchingEngineIndexId;
  private String matchingEngineIndexEndpointId;
  private String matchingEngineIndexEndpointDomain;
  private String matchingEngineIndexDeploymentId;
  private String bigTableInstanceName;
  private String bigTableTableName;
  private String bigTableColumnFamily;
  private String bigTableColumnQualifierContent;
  private String bigTableColumnQualifierLink;

  private final Integer maxNeighbors = 5;
  private final Double neighborMaxDistance = 10.0;
  private final Double temperature = 0.2;
  private final Integer maxOutputTokens = 1024;
  private final Integer topK = 40;
  private final Double topP = 0.95;

  @PostConstruct
  public void init() {
    var jsonConfig = Utilities.getSecretValue(secretManagerConfigurationVersion).toStringUtf8();
    var configuration = new Gson().fromJson(jsonConfig, JsonObject.class);
    projectId = configuration.get("project.id").getAsString();
    region = configuration.get("region").getAsString();
    credentialsSecretManagerId = configuration.get("secretmanager.credentials.id").getAsString();
    matchingEngineIndexId = configuration.get("matchingengine.index.id").getAsString();
    matchingEngineIndexEndpointId = configuration.get("matchingengine.indexendpoint.id").getAsString();
    matchingEngineIndexEndpointDomain =
        configuration.get("matchingengine.indexendpoint.domain").getAsString();
    matchingEngineIndexDeploymentId =
        configuration.get("matchingengine.index.deployment").getAsString();
    bigTableInstanceName = configuration.get("bt.instance").getAsString();
    bigTableTableName = configuration.get("bt.table").getAsString();
    bigTableColumnFamily = configuration.get("bt.columnfamily").getAsString();
    bigTableColumnQualifierContent = configuration.get("bt.columnqualifier.content").getAsString();
    bigTableColumnQualifierLink = configuration.get("bt.columnqualifier.link").getAsString();
  }

  @Produces
  public EmbeddingsClient produceEmbeddingsClient() {
    return EmbeddingsClient.create(projectId, region, credentialsSecretManagerId);
  }

  @Produces
  public PalmClient producePalmClient() {
    return PalmClient.create(projectId, region, credentialsSecretManagerId);
  }

  @Produces
  public MatchingEngineClient produceMatchingEngineClient() {
    return MatchingEngineClient.create(
        region,
        matchingEngineIndexId,
        matchingEngineIndexEndpointId,
        matchingEngineIndexEndpointDomain,
        matchingEngineIndexDeploymentId,
        credentialsSecretManagerId);
  }

  @Produces
  @ApplicationScoped
  public BigTableService produceBigTableService() throws IOException {

    var btService =
        new BigTableService(
            bigTableInstanceName,
            bigTableTableName,
            bigTableColumnFamily,
            bigTableColumnQualifierContent,
            bigTableColumnQualifierLink,
            projectId);
    btService.init();
    return btService;
  }

  public record ResourceConfiguration(
      String matchingEngineIndexDeploymentId,
      Integer maxNeighbors,
      Double maxNeighborDistance,
      Double temperature,
      Integer maxOutputTokens,
      Integer topK,
      Double topP) {}

  @Produces
  @ApplicationScoped
  public ResourceConfiguration produceResourceConfiguration() {
    return new ResourceConfiguration(
        matchingEngineIndexDeploymentId,
        maxNeighbors,
        neighborMaxDistance,
        temperature,
        maxOutputTokens,
        topK,
        topP);
  }
}
