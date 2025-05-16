package com.google.cloud.pso.rag.common;

import com.pgvector.PGvector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JDBCHelper {
  private static final ExecutorService databaseExecutor = Executors.newFixedThreadPool(1);

  public static CompletableFuture<ResultSet> executeSQLStmtAsync(
      String sql, String jdbcUrl, String user, String password) {
    return CompletableFuture.supplyAsync(
        () -> {
          // JDBC operations happen in a thread from databaseExecutor
          try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
              Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            return stmt.getResultSet();
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
        .mapToDouble(i -> pGvector.toArray()[i]) // convert float to double
        .boxed() // box primitive double to Double
        .collect(Collectors.toList());
  }
}
