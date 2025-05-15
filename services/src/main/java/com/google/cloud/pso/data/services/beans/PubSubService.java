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
package com.google.cloud.pso.data.services.beans;

import com.google.cloud.pso.rag.common.Ingestion.Request;
import com.google.cloud.pso.rag.common.InteractionHelper;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** */
public class PubSubService {

  private final String topic;

  private Publisher publisher;

  public PubSubService(String topic) {
    this.topic = topic;
  }

  @PostConstruct
  public void init() throws IOException {
    publisher = Publisher.newBuilder(topic).build();
  }

  public Result<CompletableFuture<String>, Exception> publishIngestion(Request request) {
    return InteractionHelper.jsonMapper(request)
        .map(
            payload ->
                publisher.publish(
                    PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(payload)).build()))
        .map(InteractionHelper::toCompletableFuture);
  }

  @PreDestroy
  public void destroy() {
    // Close the publisher.
    publisher.shutdown();
  }
}
