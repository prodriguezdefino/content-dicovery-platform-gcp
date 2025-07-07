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
package com.google.cloud.pso.rag.mcp.config;

import com.google.cloud.pso.rag.mcp.server.tools.EmbeddingService;
import com.google.cloud.pso.rag.mcp.server.tools.NearestNeighborsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** */
@Configuration
public class ToolsConfiguration {

  @Bean
  public ToolCallbackProvider embeddingTool(EmbeddingService embeddingService) {
    return MethodToolCallbackProvider.builder().toolObjects(embeddingService).build();
  }

  @Bean
  public ToolCallbackProvider nearestNeighborsTool(
      NearestNeighborsService nearestNeighborsService) {
    return MethodToolCallbackProvider.builder().toolObjects(nearestNeighborsService).build();
  }
}
