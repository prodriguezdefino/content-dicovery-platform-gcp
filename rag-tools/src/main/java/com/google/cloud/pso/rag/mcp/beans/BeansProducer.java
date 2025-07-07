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
package com.google.cloud.pso.rag.mcp.beans;

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeansProducer {

  @Autowired
  @Value("${secretmanager.configuration.version}")
  String secretManagerConfigurationVersion;

  private String projectId;
  private String region;
  private String cloudRunServiceId;
  private String pubsubTopic;
  private String bigTableInstanceName;
  private String bigTableQueryContextTableName;
  private String bigTableQueryContextColumnFamily;
  private String bigTableContentTableName;
  private String bigTableContentColumnFamily;
  private String bigTableContentColumnQualifierContent;
  private String bigTableContentColumnQualifierLink;
  private String bigTableContentColumnQualifierContext;
  private String serviceAccount;
  private String alloyDBipAddress;
  private String alloyDBDatabaseName;
  private String alloyDBUser;
  private String alloyDBPassword;
  private String alloyDBSchema;
  private String alloyDBTable;
  private Boolean logInteraction;
  private Interactions interactions;

  private final Integer maxNeighbors = 3;
  private final Double minNeighborDistance = 0.4;
  private final Double temperature = 0.5;
  private final Integer maxOutputTokens = 1024;
  private final Integer topK = 40;
  private final Double topP = 0.95;

  private String configuredBotContextExpertise = "";
  private Boolean includeOwnKnowledgeEnrichment = true;

  @PostConstruct
  public void init() {
    var jsonConfig = Utilities.getSecretValue(secretManagerConfigurationVersion).toStringUtf8();
    var configuration = new Gson().fromJson(jsonConfig, JsonObject.class);
    projectId = configuration.get("project.id").getAsString();
    region = configuration.get("region").getAsString();
    cloudRunServiceId = configuration.get("cloudrun.service.id").getAsString();
    pubsubTopic = configuration.get("pubsub.topic").getAsString();
    var matchingEngineIndexId = configuration.get("matchingengine.index.id").getAsString();
    var matchingEngineIndexEndpointId =
        configuration.get("matchingengine.indexendpoint.id").getAsString();
    var matchingEngineIndexEndpointDomain =
        configuration.get("matchingengine.indexendpoint.domain").getAsString();
    var matchingEngineIndexDeploymentId =
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
    serviceAccount =
        Optional.ofNullable(configuration.get("service.account"))
            .map(jse -> jse.getAsString())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Service Account not present in the configuration."));
    logInteraction =
        Optional.ofNullable(configuration.get("log.interactions"))
            .map(jse -> jse.getAsBoolean())
            .orElse(false);

    alloyDBipAddress = configuration.get("alloy.ipAddress").getAsString();
    alloyDBDatabaseName = configuration.get("alloy.databaseName").getAsString();
    alloyDBUser = configuration.get("alloy.username").getAsString();
    alloyDBPassword = configuration.get("alloy.password").getAsString();
    alloyDBSchema = configuration.get("alloy.schema").getAsString();
    alloyDBTable = configuration.get("alloy.table").getAsString();

    // we assume the service account for the current container has permissions to make requests to
    // the needed Google services.
    GCPEnvironment.trySetup(
        new GCPEnvironment.Config(
            projectId,
            region,
            () -> "",
            new GCPEnvironment.VectorSearchConfig(
                matchingEngineIndexEndpointDomain,
                matchingEngineIndexEndpointId,
                matchingEngineIndexId,
                matchingEngineIndexDeploymentId),
            new GCPEnvironment.AlloyDBConfig(
                alloyDBipAddress,
                alloyDBDatabaseName,
                alloyDBUser,
                alloyDBPassword,
                alloyDBSchema,
                alloyDBTable)));
    interactions =
        new Interactions(
            configuration.get("embeddings_models").getAsJsonArray().get(0).getAsString(),
            configuration.get("vector_storages").getAsJsonArray().get(0).getAsString(),
            configuration.get("chunkers").getAsJsonArray().get(0).getAsString(),
            configuration.get("llms").getAsJsonArray().get(0).getAsString());
  }

  @Bean(name = "cloudrun.service.id")
  public String cloudRunServiceId() {
    return cloudRunServiceId;
  }

  @Bean
  public ServiceTypes.ResourceConfiguration produceResourceConfiguration() {
    return new ServiceTypes.ResourceConfiguration(
        logInteraction,
        maxNeighbors,
        minNeighborDistance,
        temperature,
        maxOutputTokens,
        topK,
        topP);
  }

  @Bean
  public ServiceTypes.BigTableConfiguration produceBigTableConfiguration() {
    return new ServiceTypes.BigTableConfiguration(
        bigTableInstanceName,
        bigTableContentTableName,
        bigTableQueryContextTableName,
        bigTableContentColumnFamily,
        bigTableQueryContextColumnFamily,
        bigTableContentColumnQualifierContent,
        bigTableContentColumnQualifierLink,
        bigTableContentColumnQualifierContext,
        projectId);
  }

  @Bean
  public Interactions interactions() {
    return interactions;
  }

  public record Interactions(
      String embeddingsModel, String vectorStorage, String chunker, String llm) {}
}
