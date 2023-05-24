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
