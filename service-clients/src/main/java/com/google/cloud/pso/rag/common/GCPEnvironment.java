package com.google.cloud.pso.rag.common;

import com.google.common.base.Preconditions;
import java.util.function.Supplier;

/** */
public class GCPEnvironment {

  private static GCPEnvironment INSTANCE = null;

  public record Config(
      String project, String region, Supplier<String> serviceAccountEmailSupplier) {}

  private final Config config;

  private GCPEnvironment(Config config) {
    this.config = config;
  }

  public static void trySetup(
      String projectId, String regionId, Supplier<String> serviceAccountEmailSupplier) {
    if (INSTANCE == null) {
      synchronized (GCPEnvironment.class) {
        if (INSTANCE == null) {
          INSTANCE =
              new GCPEnvironment(new Config(projectId, regionId, serviceAccountEmailSupplier));
        }
      }
    }
  }

  public static Config config() {
    Preconditions.checkState(INSTANCE != null, "Configuration is not initialized.");
    return INSTANCE.config;
  }
}
