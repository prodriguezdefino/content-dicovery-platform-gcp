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
package com.google.cloud.pso.beam.contentextract.utils;

import autovalue.shaded.com.google.auto.service.AutoService;
import com.google.cloud.pso.beam.contentextract.ContentExtractionOptions;
import com.google.cloud.pso.rag.common.GCPEnvironment;
import org.apache.beam.sdk.harness.JvmInitializer;
import org.apache.beam.sdk.options.PipelineOptions;

/** */
@AutoService(JvmInitializer.class)
public class GCPEnvironmentInitializer implements JvmInitializer {

  @Override
  public void beforeProcessing(PipelineOptions options) {
    var extractionOptions = options.as(ContentExtractionOptions.class);
    var config =
        new GCPEnvironment.Config(
            extractionOptions.getProject(),
            extractionOptions.getRegion(),
            () -> "",
            new GCPEnvironment.VectorSearchConfig(
                extractionOptions.getMatchingEngineIndexEndpointDomain(),
                extractionOptions.getMatchingEngineIndexEndpointId(),
                extractionOptions.getMatchingEngineIndexId(),
                extractionOptions.getMatchingEngineIndexEndpointDeploymentName()));
    GCPEnvironment.trySetup(config);
  }
}
