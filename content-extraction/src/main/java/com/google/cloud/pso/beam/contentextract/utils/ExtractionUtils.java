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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cloud.pso.beam.contentextract.Types.ContentIdExtractError;
import com.google.cloud.pso.beam.contentextract.Types.IndexableContent;
import com.google.cloud.pso.beam.contentextract.Types.Transport;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.values.KV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class ExtractionUtils {

  private static final Logger LOG = LoggerFactory.getLogger(ExtractionUtils.class);

  public static List<Transport> extractContentId(PubsubMessage msg) {
    try {
      var json = new Gson().fromJson(new String(msg.getPayload()), JsonObject.class);
      if (json.has("url"))
        return List.of(new Transport(json.get("url").getAsString(), msg.getAttributeMap()));
      else if (json.has("urls"))
        return json.get("urls").getAsJsonArray().asList().stream()
            .map(e -> new Transport(e.getAsString(), msg.getAttributeMap()))
            .toList();
      else if (json.has("retry"))
        return List.of(new Transport(json.get("retry").getAsString(), msg.getAttributeMap()));
      else if (json.has("retries"))
        return json.get("retries").getAsJsonArray().asList().stream()
            .map(e -> new Transport(e.getAsString(), msg.getAttributeMap()))
            .toList();
      else
        throw new IllegalArgumentException(
            "Provided JSON does not have the expected fields ('url', 'urls', 'retries', 'retry')");
    } catch (Exception ex) {
      var errMsg =
          "Error while trying to extract the content id. Message: " + new String(msg.getPayload());
      LOG.error(errMsg);
      throw new ContentIdExtractError(errMsg, ex);
    }
  }

  public static List<KV<String, String>> docContentToKeyedJSONLFormat(
      KV<String, List<String>> content) {
    return content.getValue().stream()
        .map(
            contentLine -> {
              var json = new JsonObject();
              json.addProperty("text", contentLine);
              return json.toString();
            })
        .map(jsonl -> KV.of(content.getKey(), jsonl))
        .toList();
  }

  public static KV<String, String> contentToKeyedParagraphs(KV<String, List<String>> content) {
    return KV.of(content.getKey(), content.getValue().stream().collect(Collectors.joining("\n")));
  }

  public static List<IndexableContent> addEmbeddingsIdentifiers(
      KV<String, Iterable<KV<String, Iterable<Double>>>> content) {
    var embeddings =
        StreamSupport.stream(content.getValue().spliterator(), false)
            .map(
                val ->
                    KV.of(
                        val.getKey(),
                        StreamSupport.stream(val.getValue().spliterator(), false).toList()))
            .toList();
    return IntStream.range(0, embeddings.size())
        .mapToObj(
            i ->
                new IndexableContent(
                    content.getKey() + Utilities.CONTENT_KEY_SEPARATOR + i,
                    embeddings.get(i).getKey(),
                    embeddings.get(i).getValue()))
        .toList();
  }

  public static FileIO.Write.FileNaming documentAndIdNaming(
      final String prefix, final String suffix) {
    return (window, pane, numShards, shardIndex, compression) -> {
      checkArgument(window != null, "window can not be null");
      checkArgument(pane != null, "pane can not be null");
      checkArgument(compression != null, "compression can not be null");
      StringBuilder res = new StringBuilder(prefix);
      res.append(suffix);
      res.append(compression.getSuggestedSuffix());
      return res.toString();
    };
  }
}
