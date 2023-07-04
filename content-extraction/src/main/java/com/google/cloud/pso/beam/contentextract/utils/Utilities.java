/*
 * Copyright (C) 2023 Google Inc.
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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.Preconditions;
import com.google.cloud.pso.beam.contentextract.Types.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
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
public class Utilities {

  private static final Logger LOG = LoggerFactory.getLogger(Utilities.class);
  private static final String CONTENT_KEY_SEPARATOR = "___";

  static NetHttpTransport createTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException ex) {
      var errMsg = "Errors while trying to create a transport object.";
      LOG.error(errMsg, ex);
      throw new RuntimeException(ex);
    }
  }

  static String extractIdByPattern(String path, String itemPattern) {
    var pathParts = path.split(itemPattern);
    if (pathParts.length < 2) {
      throw new IllegalArgumentException(
          "The path does not contain a Google Drive id, path: " + path);
    }
    var containsId = pathParts[1].split("/")[0];
    if (containsId.isEmpty()) {
      throw new IllegalArgumentException(
          "Wrong path pattern, path: " + path + ", pattern: " + itemPattern);
    }
    return containsId;
  }

  public static String extractIdFromURL(String url) {
    try {
      var parsedUrl = new URL(url);
      var path = parsedUrl.getPath();
      Preconditions.checkState(!path.isEmpty(), "The URL path should not be empty");
      if (path.contains("/document/d/")) {
        return extractIdByPattern(path, "/document/d/");
      } else if (path.contains("/drive/folders/")) {
        return extractIdByPattern(path, "/drive/folders/");
      } else {
        throw new IllegalArgumentException(
            "The shared URL is not a document or drive folder one: " + url);
      }
    } catch (MalformedURLException ex) {
      var msg = "Problems while parsing the Google drive URL: " + url;
      LOG.error(msg, ex);
      throw new IllegalArgumentException(msg, ex);
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

  public static List<KV<String, KV<String, List<Double>>>> addEmbeddingsIdentifiers(
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
        .mapToObj(i -> KV.of(content.getKey() + CONTENT_KEY_SEPARATOR + i, embeddings.get(i)))
        .toList();
  }

  public static List<KV<String, List<Double>>> removeContentFromEmbeddingsKV(
      List<KV<String, KV<String, List<Double>>>> content) {
    return content.stream().map(kv -> KV.of(kv.getKey(), kv.getValue().getValue())).toList();
  }

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

  public static String newIdFromTitleAndDriveId(String title, String driveId) {
    var normalized = Normalizer.normalize(title, Normalizer.Form.NFD);
    return normalized
            .replaceAll("[ ]{1,}", "_")
            .replaceAll("[\\[\\]]", "")
            .replaceAll("[\\(\\)]", "")
            .replaceAll("[\\n\\r]", "")
            .toLowerCase()
        + CONTENT_KEY_SEPARATOR
        + driveId;
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

  public static String reconstructDocumentLinkFromEmbeddingsId(String embeddingsId) {
    var embeddingsIdParts = embeddingsId.split(CONTENT_KEY_SEPARATOR);
    if (embeddingsIdParts.length != 3) {
      LOG.warn("Expected a 3 part embeddings id, got {}. Returning empty string.", embeddingsId);
      return "";
    }
    // we only keep the document id part, discarding doc name and embeddings sequence
    return "https://docs.google.com/document/d/" + embeddingsIdParts[1];
  }
}
