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

import com.google.cloud.pso.beam.contentextract.Types;
import com.google.gson.JsonObject;
import java.util.List;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.values.KV;

/** */
public class ExtractionUtils {

  public static List<KV<String, String>> docContentToKeyedJSONLFormat(Types.Content content) {
    return content.content().stream()
        .map(
            contentLine -> {
              var json = new JsonObject();
              json.addProperty("text", contentLine);
              return json.toString();
            })
        .map(jsonl -> KV.of(content.key(), jsonl))
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
