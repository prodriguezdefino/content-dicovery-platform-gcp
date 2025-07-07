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
package com.google.cloud.pso.rag.mcp.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.mcp.client.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.reactive.function.client.WebClient;

/** */
@Configuration
@EnableConfigurationProperties({McpClientCommonProperties.class, McpSseClientProperties.class})
public class SpringAiConfig {

  private final String systemText =
      """
        You are an expert AI assistant designed to answer user questions.
        You have at your disposal a rag framework that can help you get content relevant to the
        users question. First you must convert the users question into a embedding.
        You can use the getEmbedding tools for that.
        Then you need to search the vector db for embeddings similar to that of the original question
        (nearest neighbors). You can use the getNearestNeighbors tool for that. The response contains
        several ids, each representing a different file that might have information relevant to the
        users question.
        Then for each id you must look up the actual content, you can use the getContent tool.
        You will have to run the tool several times, once for each id in the output of
        getNearestNeighbors. At this point you will have the content of several documents available.
        Leverage that content to answer the user's question.
      """;
  ;

  @Bean
  public SystemPromptTemplate initSystemTemplate() {
    return new SystemPromptTemplate(systemText);
  }

  @Bean
  @Scope("prototype")
  public List<McpAsyncClient> mcpAsyncClientList(
      McpSseClientProperties mcpSseProperties,
      McpClientCommonProperties mcpCommonProperties,
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper) {
    return mcpSseProperties.getConnections().entrySet().stream()
        .map(
            entry ->
                new NamedClientMcpTransport(
                    entry.getKey(),
                    WebFluxSseClientTransport.builder(
                            webClientBuilder.clone().baseUrl(entry.getValue().url()))
                        .sseEndpoint(
                            Optional.ofNullable(entry.getValue().sseEndpoint()).orElse("/sse"))
                        .objectMapper(objectMapper)
                        .build()))
        .map(
            transport ->
                McpClient.async(transport.transport())
                    .clientInfo(
                        new McpSchema.Implementation(
                            mcpCommonProperties.getName() + " - " + transport.name(),
                            mcpCommonProperties.getVersion()))
                    .requestTimeout(mcpCommonProperties.getRequestTimeout())
                    .build())
        .map(
            client -> {
              client.initialize().block();
              return client;
            })
        .toList();
  }
}
