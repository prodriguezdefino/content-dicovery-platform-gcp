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
package com.google.cloud.pso.rag.vector;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** */
public class VectorRequests {

  private VectorRequests() {}

  public record Vector(Optional<String> id, List<Double> values) {
    public Vector(List<Double> values) {
      this(Optional.empty(), values);
    }

    public Vector(String id, List<Double> values) {
      this(Optional.of(id), values);
    }
  }

  public static Vectors.Store store(String configurationEntry, List<Vector> vectors) {
    return store(configurationEntry, "", vectors);
  }

  public static Vectors.Store store(
      String configurationEntry, String idPrefix, List<Vector> vectors) {
    return switch (configurationEntry) {
      case "vector_search" ->
          new VectorSearch.UpsertRequest(
              IntStream.range(0, vectors.size())
                  .mapToObj(
                      idx -> {
                        var vector = vectors.get(idx);
                        return new Vectors.Datapoint(
                            vector.id().orElse(idPrefix + idx), vector.values());
                      })
                  .toList());
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for vector store request creation.",
                  configurationEntry));
    };
  }

  public static Vectors.Search find(
      String configurationEntry, List<Vector> vectors, Integer quantity) {
    return switch (configurationEntry) {
      case "vector_search" ->
          new VectorSearch.SearchRequest(
              vectors.stream()
                  .map(
                      vector ->
                          new VectorSearch.Query(new Vectors.Datapoint(vector.values()), quantity))
                  .toList());
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for vector store request creation.",
                  configurationEntry));
    };
  }

  public static Vectors.Delete remove(String configurationEntry, List<String> vectorIds) {
    return switch (configurationEntry) {
      case "vector_search" -> new VectorSearch.RemoveRequest(vectorIds);
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Configuration entry (%s) not supported for vector store request creation.",
                  configurationEntry));
    };
  }
}
