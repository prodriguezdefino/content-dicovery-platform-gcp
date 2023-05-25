package com.google.cloud.pso.beam.contentextract.clients;

import com.google.api.client.util.Preconditions;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class Utilities {
  private static final Logger LOG = LoggerFactory.getLogger(Utilities.class);

  public static ByteString getSecretValue(String secretId) {
    try (var client = SecretManagerServiceClient.create()) {
      LOG.info("retrieving encoded key secret {}", secretId);
      Preconditions.checkArgument(
          SecretVersionName.isParsableFrom(secretId), "The provided secret is not parseable.");
      var secretVersionName = SecretVersionName.parse(secretId);
      return client.accessSecretVersion(secretVersionName).getPayload().getData();
    } catch (Exception ex) {
      var msg = "Error while interacting with SecretManager client, key: " + secretId;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }
}
