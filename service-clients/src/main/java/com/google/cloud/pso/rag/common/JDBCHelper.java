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
package com.google.cloud.pso.rag.common;

import com.pgvector.PGvector;
import org.postgresql.PGConnection;

import java.sql.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JDBCHelper {
  private static final ExecutorService databaseExecutor = Executors.newFixedThreadPool(1);

  public static CompletableFuture<Integer> executeUpdateAsync(
      String jdbcUrl,
      String user,
      String password,
      String sql,
      Consumer<PreparedStatement> paramSetter) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password); ) {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            paramSetter.accept(pstmt);
            ;
            return pstmt.executeUpdate();
          } catch (SQLException e) {
            throw new RuntimeException("Database error during query: " + e.getMessage(), e);
          }
        },
        databaseExecutor);
  }

  public static CompletableFuture<ResultSet> executeQueryAsync(
      String jdbcUrl,
      String user,
      String password,
      String sql,
      Consumer<PreparedStatement> paramSetter) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
            PreparedStatement pstmt = connection.prepareStatement(sql);
            paramSetter.accept(pstmt);
            pstmt.execute();
            return pstmt.getResultSet();
          } catch (SQLException e) {
            throw new RuntimeException("Database error during query: " + e.getMessage(), e);
          }
        },
        databaseExecutor);
  }

  public static <T> Stream<T> streamResultSet(ResultSet rs, ResultSetMapper<T> mapper)
      throws SQLException {
    Iterator<T> iterator =
        new Iterator<T>() {
          private boolean hasNext = advance();

          private boolean advance() {
            try {
              return rs.next();
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public boolean hasNext() {
            return hasNext;
          }

          @Override
          public T next() {
            try {
              T row = mapper.map(rs);
              hasNext = advance();
              return row;
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }
        };

    Iterable<T> iterable = () -> iterator;
    return StreamSupport.stream(iterable.spliterator(), false)
        .onClose(
            () -> {
              try {
                rs.close();
                Statement stmt = rs.getStatement();
                Connection conn = stmt.getConnection();
                stmt.close();
                conn.close();
              } catch (SQLException e) {
                throw new RuntimeException(e);
              }
            });
  }

  public interface ResultSetMapper<T> {
    T map(ResultSet rs) throws SQLException;
  }

  public static List<Double> pGvectorToListDouble(PGvector pGvector) {
    return IntStream.range(0, pGvector.toArray().length)
        .mapToDouble(i -> pGvector.toArray()[i])
        .boxed()
        .collect(Collectors.toList());
  }
}
