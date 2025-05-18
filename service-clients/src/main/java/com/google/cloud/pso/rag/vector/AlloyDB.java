package com.google.cloud.pso.rag.vector;

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.JDBCHelper;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import com.pgvector.PGvector;
import com.google.cloud.pso.rag.common.Result.Success;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.cloud.pso.rag.common.JDBCHelper.executeSQLStmtAsync;

public class AlloyDB {

  private AlloyDB() {}

  static String searchSQLStatement(SearchRequest request) {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    String queriesToSearchForStr =
        IntStream.range(0, request.queries().size())
            .mapToObj(
                i ->
                    String.format(
                        "SELECT %d internalId, '%s' id, '%s'::vector AS embedding, %d max_nn",
                        i,
                        request.queries().get(i).datapoint().datapointId(),
                        request.queries().get(i).datapoint().featureVector(),
                        request.queries().get(i).neighborCount()))
            .collect(Collectors.joining(" UNION ALL\n"));

    return String.format(
        "WITH query_vectors AS (\n"
            + " %s "
            + " ), all_neighbors_w_distance as (\n"
            + "  SELECT \n"
            + "    t1.internalId, t1.id queryVectorId, \n"
            + "    t1.embedding query_embedding, t2.id neighborId, \n"
            + "    t2.embedding neighborEmbedding, t1.max_nn,\n"
            + "    t1.embedding <=> t2.embedding distance\n"
            + "  FROM \n"
            + "    query_vectors t1\n"
            + "  CROSS JOIN\n"
            + "    %s.%s t2\n"
            + "), vectors_with_distance AS (\n"
            + "  SELECT \n"
            + "    internalId, queryVectorId, query_embedding, \n"
            + "    neighborId, neighborEmbedding, distance, max_nn,\n"
            + "    row_number() OVER(PARTITION BY internalId ORDER BY distance) rn\n"
            + "  FROM \n"
            + "    all_neighbors_w_distance\n"
            + ")\n"
            + "SELECT \n"
            + "  internalId, queryVectorId, query_embedding, \n"
            + "  neighborId, neighborEmbedding, distance\n"
            + "FROM\n"
            + "  vectors_with_distance\n"
            + "WHERE \n"
            + "  rn <= max_nn\n"
            + "ORDER BY\n"
            + "  internalId asc",
        queriesToSearchForStr, alloyDBConfig.schema(), alloyDBConfig.table());
  }

  static String upsertSQLStatement(UpsertRequest request) {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    String recordsToUpsert =
        request.datapoints.stream()
            .map(
                datapoint ->
                    String.format(
                        "('%s' ,('%s'))", datapoint.datapointId(), datapoint.featureVector()))
            .collect(Collectors.joining(","));
    return String.format(
        "INSERT INTO %s.%s (id, embedding) VALUES %s ON CONFLICT "
            + "(id) DO UPDATE SET embedding = EXCLUDED.embedding; ",
        alloyDBConfig.schema(), alloyDBConfig.table(), recordsToUpsert);
  }

  static String removeSQLStatement(RemoveRequest request) {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    return String.format(
        "DELETE FROM %s.%s WHERE id in (%s);",
        alloyDBConfig.schema(),
        alloyDBConfig.table(),
        request.datapointIds.stream()
            .map(id -> String.format("'%s'", id))
            .collect(Collectors.joining(",")));
  }

  static String alloyJDBCUrl() {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    return String.format(
        "jdbc:postgresql://%s:5432/%s", alloyDBConfig.ipAddressDB(), alloyDBConfig.databaseName());
  }

  /*
  Nearest neighbor search types.
  */
  public record SearchRequest(List<AlloyDB.Query> queries) implements Vectors.Search {}

  public record NeighborsResponse(List<Vectors.Neighbors> nearestNeighbors)
      implements Vectors.SearchResponse {}

  public record Query(Vectors.Datapoint datapoint, Integer neighborCount) {}

  record NNSearchSQLResultRow(
      Integer internalId,
      String queryVectorId,
      String neighborId,
      PGvector neighborEmbedding,
      Double distance) {}
  ;

  /*
  Index datapoint store types.
  */
  public record UpsertRequest(List<Vectors.Datapoint> datapoints) implements Vectors.Store {}

  public record UpsertResponse() implements Vectors.StoreResponse {}

  /*
  Remove datapoint types.
  */
  public record RemoveRequest(List<String> datapointIds) implements Vectors.Delete {}

  public record RemoveResponse() implements Vectors.DeleteResponse {}

  static String buildQuery(Vectors.Request request) {
    return switch (request) {
      case SearchRequest searchRequest -> searchSQLStatement(searchRequest);
      case UpsertRequest upsertRequest -> upsertSQLStatement(upsertRequest);
      case RemoveRequest removeRequest -> removeSQLStatement(removeRequest);
      default ->
          throw new IllegalStateException(
              "Request type not implemented for VectorSearch: " + request);
    };
  }

  /*
  Nearest neighbor search helper.
  */
  static Result<Stream<NNSearchSQLResultRow>, Exception> StreamSearchResultSet(
      ResultSet resultSet) {
    try {
      return Result.success(
          JDBCHelper.streamResultSet(
              resultSet,
              rs -> {
                int internalId = rs.getInt("internalId");
                String queryVectorId = rs.getString("queryVectorId");
                String neighborId = rs.getString("neighborId");
                PGvector neighborEmbedding = (PGvector) resultSet.getObject("neighborEmbedding");
                Double distance = rs.getDouble("distance");
                return new NNSearchSQLResultRow(
                    internalId, queryVectorId, neighborId, neighborEmbedding, distance);
              }));
    } catch (SQLException e) {
      return Result.failure(e);
    }
  }

  static void addResultRowToMap(
      Map<Integer, Vectors.Neighbors> integerNeighborsMap,
      NNSearchSQLResultRow nnSearchSQLResultRow) {
    Vectors.Datapoint datapoint =
        new Vectors.Datapoint(
            nnSearchSQLResultRow.neighborId,
            JDBCHelper.pGvectorToListDouble(nnSearchSQLResultRow.neighborEmbedding));
    Vectors.Neighbor neighbor = new Vectors.Neighbor(nnSearchSQLResultRow.distance, datapoint);

    integerNeighborsMap
        .computeIfAbsent(
            nnSearchSQLResultRow.internalId,
            k -> new Vectors.Neighbors(nnSearchSQLResultRow.queryVectorId, new ArrayList<>()))
        .neighbors()
        .add(neighbor);
  }

  static Result<NeighborsResponse, ErrorResponse> neighborsResponseFromResultSet(
      ResultSet resultSet) {
    /*
     * result from DB has <internalId, queryVectorId, neighborId, neighborEmbedding, distance>
     * this has to be mapped to <<queryVectorId,<neighborId,neighborEmbedding>[]>, distance>[]
     * respecting the order of the queries in the request
     */
    Map<Integer, Vectors.Neighbors> internalIdToNeighborsMap = new LinkedHashMap<>();
    Result<Stream<NNSearchSQLResultRow>, Exception> nnSearchSQLResultStream =
        StreamSearchResultSet(resultSet);

    return switch (nnSearchSQLResultStream) {
      case Result.Failure<?, Exception>(var error) ->
          Result.failure("Errors occurred while parsing search results", error);
      case Success<Stream<NNSearchSQLResultRow>, ?>(var resultSetStream) -> {
        resultSetStream.forEach(
            nnSearchSQLResultRow -> {
              addResultRowToMap(internalIdToNeighborsMap, nnSearchSQLResultRow);
            });
        yield Result.success(
            new NeighborsResponse(new ArrayList<>(internalIdToNeighborsMap.values())));
      }
    };
  }

  static Result<CompletableFuture<ResultSet>, Exception> executeInternal(Vectors.Request request) {
    String sql = buildQuery(request);
    String jdbcUrl = alloyJDBCUrl();
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    try {
      return Result.success(
          executeSQLStmtAsync(sql, jdbcUrl, alloyDBConfig.user(), alloyDBConfig.password()));
    } catch (Exception e) {
      return Result.failure(e);
    }
  }

  static CompletableFuture<Result<? extends Vectors.SearchResponse, ErrorResponse>> search(
      SearchRequest request) {
    return switch (executeInternal(request)) {
      case Result.Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure(
                  new ErrorResponse(
                      "Errors occurred while executing search SQL statement.",
                      Optional.of(error))));
      case Result.Success<CompletableFuture<ResultSet>, ?>(var value) ->
          value.thenApply(resultSet -> neighborsResponseFromResultSet(resultSet));
    };
  }

  static CompletableFuture<Result<? extends Vectors.StoreResponse, ErrorResponse>> store(
      UpsertRequest request) {
    return switch (executeInternal(request)) {
      case Result.Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure(
                  new ErrorResponse(
                      "Errors occurred while executing upsert SQL statement.",
                      Optional.of(error))));
      case Result.Success<CompletableFuture<ResultSet>, ?>(var value) ->
          CompletableFuture.completedFuture(Result.success(new UpsertResponse()));
    };
  }

  static CompletableFuture<Result<? extends Vectors.DeleteResponse, ErrorResponse>> remove(
      RemoveRequest request) {
    return switch (executeInternal(request)) {
      case Result.Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure(
                  new ErrorResponse(
                      "Errors occurred while executing delete SQL statement.",
                      Optional.of(error))));
      case Result.Success<CompletableFuture<ResultSet>, ?>(var value) ->
          CompletableFuture.completedFuture(Result.success(new RemoveResponse()));
    };
  }
}
