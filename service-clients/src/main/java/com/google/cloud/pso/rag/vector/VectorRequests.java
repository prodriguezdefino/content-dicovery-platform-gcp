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
                        return new VectorSearch.Datapoint(
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
                          new VectorSearch.Query(
                              new VectorSearch.Datapoint(vector.values()), quantity))
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
