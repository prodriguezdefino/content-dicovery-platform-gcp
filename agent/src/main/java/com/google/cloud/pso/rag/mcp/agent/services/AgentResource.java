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
package com.google.cloud.pso.rag.mcp.agent.services;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** */
@Service
public class AgentResource {

  private final LLMModelService claude;

  public AgentResource(LLMModelService claude) {
    this.claude = claude;
  }

  public Mono<ServerResponse> interaction(ServerRequest request) {
    return request
        .bodyToMono(Request.class)
        .flatMap(
            body ->
                claude
                    .generate(body.q(), List.of())
                    .collectList()
                    .flatMap(
                        response ->
                            ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(
                                    new Response(response.stream().collect(Collectors.joining()))))
                    .onErrorResume(
                        ex ->
                            ServerResponse.status(HttpStatusCode.valueOf(500))
                                .bodyValue(new Response(ex.getMessage()))))
        .switchIfEmpty(ServerResponse.badRequest().build());
  }

  record Request(String q) {}

  record Response(String a) {}
}
