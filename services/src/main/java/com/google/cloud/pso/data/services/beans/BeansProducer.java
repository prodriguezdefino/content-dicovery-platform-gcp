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
package com.google.cloud.pso.data.services.beans;

import com.google.cloud.pso.beam.contentextract.clients.EmbeddingsClient;
import com.google.cloud.pso.beam.contentextract.clients.MatchingEngineClient;
import com.google.cloud.pso.beam.contentextract.clients.PalmClient;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Singleton
public class BeansProducer {
  private static final Logger LOG = LoggerFactory.getLogger(BeansProducer.class);

  @Inject
  @ConfigProperty(name = "secretmanager.configuration.version")
  String secretManagerConfigurationVersion;

  private String projectId;
  private String region;
  private String cloudRunServiceId;
  private String pubsubTopic;
  private String matchingEngineIndexId;
  private String matchingEngineIndexEndpointId;
  private String matchingEngineIndexEndpointDomain;
  private String matchingEngineIndexDeploymentId;
  private String bigTableInstanceName;
  private String bigTableQueryContextTableName;
  private String bigTableQueryContextColumnFamily;
  private String bigTableContentTableName;
  private String bigTableContentColumnFamily;
  private String bigTableContentColumnQualifierContent;
  private String bigTableContentColumnQualifierLink;
  private String bigTableContentColumnQualifierContext;

  private final Integer maxNeighbors = 5;
  private final Double maxNeighborDistance = 10.0;
  private final Double temperature = 0.1;
  private final Integer maxOutputTokens = 1024;
  private final Integer topK = 20;
  private final Double topP = 0.5;

  private String configuredBotContextExpertise = "";
  private Boolean includeOwnKnowledgeEnrichment = true;

  @PostConstruct
  public void init() {
    var jsonConfig = Utilities.getSecretValue(secretManagerConfigurationVersion).toStringUtf8();
    LOG.debug("configuration {}", jsonConfig);
    var configuration = new Gson().fromJson(jsonConfig, JsonObject.class);
    projectId = configuration.get("project.id").getAsString();
    region = configuration.get("region").getAsString();
    cloudRunServiceId = configuration.get("cloudrun.service.id").getAsString();
    pubsubTopic = configuration.get("pubsub.topic").getAsString();
    matchingEngineIndexId = configuration.get("matchingengine.index.id").getAsString();
    matchingEngineIndexEndpointId =
        configuration.get("matchingengine.indexendpoint.id").getAsString();
    matchingEngineIndexEndpointDomain =
        configuration.get("matchingengine.indexendpoint.domain").getAsString();
    matchingEngineIndexDeploymentId =
        configuration.get("matchingengine.index.deployment").getAsString();
    bigTableInstanceName = configuration.get("bt.instance").getAsString();
    bigTableQueryContextTableName = configuration.get("bt.contexttable").getAsString();
    bigTableQueryContextColumnFamily = configuration.get("bt.contextcolumnfamily").getAsString();
    bigTableContentTableName = configuration.get("bt.contenttable").getAsString();
    bigTableContentColumnFamily = configuration.get("bt.contentcolumnfamily").getAsString();
    bigTableContentColumnQualifierContent =
        configuration.get("bt.contentcolumnqualifier.content").getAsString();
    bigTableContentColumnQualifierLink =
        configuration.get("bt.contentcolumnqualifier.link").getAsString();
    bigTableContentColumnQualifierContext =
        configuration.get("bt.contextcolumnqualifier").getAsString();
    configuredBotContextExpertise =
        Optional.ofNullable(configuration.get("bot.contextexpertise"))
            .map(jse -> jse.getAsString())
            .orElse("");
    includeOwnKnowledgeEnrichment =
        Optional.ofNullable(configuration.get("bot.includeownknowledge"))
            .map(jse -> jse.getAsBoolean())
            .orElse(true);
  }

  @Produces
  @Named("cloudrun.service.id")
  public String cloudRunServiceId() {
    return cloudRunServiceId;
  }

  @Produces
  public EmbeddingsClient produceEmbeddingsClient() {
    return EmbeddingsClient.create(projectId, region);
  }

  @Produces
  public PalmClient producePalmClient() {
    return PalmClient.create(projectId, region);
  }

  @Produces
  public MatchingEngineClient produceMatchingEngineClient() {
    return MatchingEngineClient.create(
        region,
        matchingEngineIndexId,
        matchingEngineIndexEndpointId,
        matchingEngineIndexEndpointDomain,
        matchingEngineIndexDeploymentId);
  }

  @Produces
  @ApplicationScoped
  public BigTableService produceBigTableService() throws IOException {

    var btService =
        new BigTableService(
            bigTableInstanceName,
            bigTableQueryContextTableName,
            bigTableQueryContextColumnFamily,
            bigTableContentTableName,
            bigTableContentColumnFamily,
            bigTableContentColumnQualifierContent,
            bigTableContentColumnQualifierLink,
            bigTableContentColumnQualifierContext,
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
        maxNeighborDistance,
        temperature,
        maxOutputTokens,
        topK,
        topP);
  }

  @Produces
  @ApplicationScoped
  public PubSubService producePubSubService() throws IOException {
    var psService = new PubSubService(pubsubTopic);
    psService.init();
    return psService;
  }

  @Produces
  @Named("botContextExpertise")
  public String configuredBotContextExpertise() {
    return configuredBotContextExpertise;
  }

  @Produces
  @Named("includeOwnKnowledgeEnrichment")
  public Boolean includeOwnKnowledgeEnrichment() {
    return includeOwnKnowledgeEnrichment;
  }
}
