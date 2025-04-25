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
package com.google.cloud.pso.rag.common;

import com.google.common.base.Preconditions;
import java.util.function.Supplier;

/** */
public class GCPEnvironment {

  private static GCPEnvironment INSTANCE = null;

  public record VectorSearchConfig(String indexDomain, String indexPath, String indexId) {}

  public record Config(
      String project,
      String region,
      Supplier<String> serviceAccountEmailSupplier,
      VectorSearchConfig vectorSearchConfig) {}

  private final Config config;

  private GCPEnvironment(Config config) {
    this.config = config;
  }

  public static void trySetup(Config config) {
    if (INSTANCE == null) {
      synchronized (GCPEnvironment.class) {
        if (INSTANCE == null) {
          INSTANCE = new GCPEnvironment(config);
        }
      }
    }
  }

  public static Config config() {
    Preconditions.checkState(INSTANCE != null, "Configuration is not initialized.");
    return INSTANCE.config;
  }
}
