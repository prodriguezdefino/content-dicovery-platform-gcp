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

import com.google.cloud.pso.rag.common.GCPEnvironment;
import com.google.cloud.pso.rag.common.JDBCHelper;
import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import com.pgvector.PGvector;
import org.postgresql.PGConnection;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.cloud.pso.rag.common.JDBCHelper.*;

public class AlloyDB {

  private AlloyDB() {}

  static PreparedStmtParams searchPstmtParams(SearchRequest request) {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    String singleQuery = "SELECT ? AS internalId, ? AS id, ?::vector AS embedding, ? AS max_nn";
    String queriesToSearchForStr =
        String.join(" UNION ALL\n", Collections.nCopies(request.queries().size(), singleQuery));

    String searchSql =
        String.format(
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
                + "  internalId, queryVectorId, \n"
                + "  neighborId, neighborEmbedding, distance\n"
                + "FROM\n"
                + "  vectors_with_distance\n"
                + "WHERE \n"
                + "  rn <= max_nn\n"
                + "ORDER BY\n"
                + "  internalId asc",
            queriesToSearchForStr, alloyDBConfig.schema(), alloyDBConfig.table());

    return new PreparedStmtParams(
        searchSql,
        pstmt -> {
          IntStream.range(0, request.queries().size())
              .forEach(
                  i -> {
                    Query query = request.queries().get(i);
                    try {
                      pstmt.setInt(i * 4 + 1, i);
                      pstmt.setString(i * 4 + 2, query.datapoint().datapointId());
                      pstmt.setString(i * 4 + 3, query.datapoint().featureVector().toString());
                      pstmt.setInt(i * 4 + 4, query.neighborCount());
                    } catch (SQLException e) {
                      throw new RuntimeException(e);
                    }
                  });
        });
  }

  static PreparedStmtParams upsertPstmtParams(UpsertRequest request) {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    String singleInsertValue = "(? ,CAST(? as vector))";
    String insertValues =
        String.join(",\n", Collections.nCopies(request.datapoints().size(), singleInsertValue));
    String upsertSql =
        String.format(
            "INSERT INTO %s.%s (id, embedding) VALUES %s ON CONFLICT "
                + "(id) DO UPDATE SET embedding = EXCLUDED.embedding; ",
            alloyDBConfig.schema(), alloyDBConfig.table(), insertValues);

    return new PreparedStmtParams(
        upsertSql,
        pstmt -> {
          IntStream.range(0, request.datapoints().size())
              .forEach(
                  i -> {
                    Vectors.Datapoint datapoint = request.datapoints.get(i);
                    try {
                      pstmt.setString(i * 2 + 1, datapoint.datapointId());
                      pstmt.setString(i * 2 + 2, datapoint.featureVector().toString());
                    } catch (SQLException e) {
                      throw new RuntimeException(e);
                    }
                  });
        });
  }

  static PreparedStmtParams removePstmtParams(RemoveRequest request) {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    String removeSql =
        String.format(
            "DELETE FROM %s.%s WHERE id = ANY(?);", alloyDBConfig.schema(), alloyDBConfig.table());
    return new PreparedStmtParams(
        removeSql,
        ptsmt -> {
          try {
            ptsmt.setArray(
                1, ptsmt.getConnection().createArrayOf("TEXT", request.datapointIds.toArray()));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  static String alloyJDBCUrl() {
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    return String.format(
        "jdbc:postgresql://%s:5432/%s", alloyDBConfig.ipAddressDB(), alloyDBConfig.databaseName());
  }

  public record PreparedStmtParams(
      String sqlString, Consumer<PreparedStatement> pstmtParamSetter) {}
  ;

  /*
  Nearest neighbor search types.
  */
  public record SearchRequest(List<Query> queries) implements Vectors.Search {}

  public record NeighborsResponse(List<Vectors.Neighbors> nearestNeighbors)
      implements Vectors.SearchResponse {}

  public record Query(Vectors.Datapoint datapoint, Integer neighborCount) {}

  record NNSearchSQLResultCompID(int internalId, String queryVectorId) {}
  ;

  record NNSearchResultRow(NNSearchSQLResultCompID compID, Vectors.Neighbor neighbor) {}
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

  static PreparedStmtParams getPstmtParams(Vectors.Request request) {
    return switch (request) {
      case SearchRequest searchRequest -> searchPstmtParams(searchRequest);
      case UpsertRequest upsertRequest -> upsertPstmtParams(upsertRequest);
      case RemoveRequest removeRequest -> removePstmtParams(removeRequest);
      default ->
          throw new IllegalStateException(
              "Request type not implemented for VectorSearch: " + request);
    };
  }

  /*
  Nearest neighbor search helpers.
  */
  static NNSearchResultRow nnSearchResultRowFromResultSet(ResultSet rs) throws SQLException {
    ((PGConnection) rs.getStatement().getConnection()).addDataType("vector", PGvector.class);
    int internalId = rs.getInt("internalId");
    String queryVectorId = rs.getString("queryVectorId");
    NNSearchSQLResultCompID compID = new NNSearchSQLResultCompID(internalId, queryVectorId);
    String neighborId = rs.getString("neighborId");
    PGvector neighborEmbedding = (PGvector) rs.getObject("neighborEmbedding");
    Double distance = (Double) rs.getDouble("distance");
    Vectors.Datapoint datapoint =
        new Vectors.Datapoint(neighborId, pGvectorToListDouble(neighborEmbedding));
    Vectors.Neighbor neighbor = new Vectors.Neighbor(distance, datapoint);
    return new NNSearchResultRow(compID, neighbor);
  }

  static Result<NeighborsResponse, ErrorResponse> neighborsResponseFromResultSet(
      ResultSet resultSet) {
    /*
     * result from DB has <internalId, queryVectorId, neighborId, neighborEmbedding, distance>
     * this has to be mapped to <vectorQueryId,<distance, neighborId, neighborEmbedding>[]>[]
     * respecting the order of the queries in the request
     */
    try {
      NeighborsResponse neighborsResponse =
          new NeighborsResponse(
              streamResultSet(resultSet, AlloyDB::nnSearchResultRowFromResultSet)
                  .collect(Collectors.groupingBy(NNSearchResultRow::compID))
                  .entrySet()
                  .stream()
                  .map(
                      entry ->
                          new Vectors.Neighbors(
                              entry.getKey().queryVectorId(),
                              entry.getValue().stream().map(NNSearchResultRow::neighbor).toList()))
                  .toList());
      return Result.success(neighborsResponse);
    } catch (SQLException e) {
      return Result.failure("Errors occurred while parsing search results", e);
    }
  }

  static Result<CompletableFuture<Integer>, Exception> executeUpdateInternal(
      Vectors.Request request) {
    PreparedStmtParams pstmtParams = getPstmtParams(request);
    String jdbcUrl = alloyJDBCUrl();
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    return Result.success(
        executeUpdateAsync(
            jdbcUrl,
            alloyDBConfig.user(),
            alloyDBConfig.password(),
            pstmtParams.sqlString(),
            pstmtParams.pstmtParamSetter()));
  }

  static Result<CompletableFuture<ResultSet>, Exception> executeQueryInternal(
      Vectors.Request request) {
    PreparedStmtParams pstmtParams = getPstmtParams(request);
    String jdbcUrl = alloyJDBCUrl();
    var alloyDBConfig = GCPEnvironment.config().alloyDBConfig();
    return Result.success(
        executeQueryAsync(
            jdbcUrl,
            alloyDBConfig.user(),
            alloyDBConfig.password(),
            pstmtParams.sqlString(),
            pstmtParams.pstmtParamSetter()));
  }

  static CompletableFuture<Result<? extends Vectors.SearchResponse, ErrorResponse>> search(
      SearchRequest request) {
    return switch (executeQueryInternal(request)) {
      case Result.Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure(
                  new ErrorResponse(
                      "Errors occurred while executing search SQL statement.",
                      Optional.of(error))));
      case Result.Success<CompletableFuture<ResultSet>, ?>(var value) ->
          value.thenApply(rs -> neighborsResponseFromResultSet(rs));
    };
  }

  static CompletableFuture<Result<? extends Vectors.StoreResponse, ErrorResponse>> store(
      UpsertRequest request) {
    return switch (executeUpdateInternal(request)) {
      case Result.Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure(
                  new ErrorResponse(
                      "Errors occurred while executing upsert SQL statement.",
                      Optional.of(error))));
      case Result.Success<CompletableFuture<Integer>, ?>(var value) ->
          CompletableFuture.completedFuture(Result.success(new UpsertResponse()));
    };
  }

  static CompletableFuture<Result<? extends Vectors.DeleteResponse, ErrorResponse>> remove(
      RemoveRequest request) {
    return switch (executeUpdateInternal(request)) {
      case Result.Failure<?, Exception>(var error) ->
          CompletableFuture.completedFuture(
              Result.failure(
                  new ErrorResponse(
                      "Errors occurred while executing delete SQL statement.",
                      Optional.of(error))));
      case Result.Success<CompletableFuture<Integer>, ?>(var value) ->
          CompletableFuture.completedFuture(Result.success(new RemoveResponse()));
    };
  }
}
