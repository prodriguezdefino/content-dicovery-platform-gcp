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
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** */
@Singleton
public class ClientsProducer {

  @Inject
  @ConfigProperty(name = "project.id")
  String projectId;

  @Inject
  @ConfigProperty(name = "region")
  String region;

  @Inject
  @ConfigProperty(name = "secretmanager.credentials.id")
  String credentialsSecretManagerId;

  @Inject
  @ConfigProperty(name = "matchingengine.index.id")
  String matchingEngineIndexId;

  @Inject
  @ConfigProperty(name = "matchingengine.indexendpoint.id")
  String matchingEngineIndexEndpointId;

  @Inject
  @ConfigProperty(name = "matchingengine.indexendpoint.domain")
  String matchingEngineIndexEndpointDomain;

  @Inject
  @ConfigProperty(name = "matchingengine.index.deployment")
  String matchingEngineIndexDeploymentId;

  public ClientsProducer() {}

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
}
