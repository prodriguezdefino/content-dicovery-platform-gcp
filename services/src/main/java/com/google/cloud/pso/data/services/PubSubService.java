package com.google.cloud.pso.data.services;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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

  public String publishMessage(String message) {
    try {
      var pubsubMessage =
          PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(message)).build();
      return publisher.publish(pubsubMessage).get();
    } catch (InterruptedException | ExecutionException ex) {
      throw new RuntimeException("Problems while publishing request.", ex);
    }
  }

  @PreDestroy
  public void destroy() {
    // Close the publisher.
    publisher.shutdown();
  }
}
