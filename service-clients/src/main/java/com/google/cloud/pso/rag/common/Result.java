package com.google.cloud.pso.rag.common;

import java.util.function.Function;

/**
 * A right-biased monad to handle process execution results.
 *
 * @param <T> The type of for the successful execution.
 * @param <E> The type for the error execution result.
 */
public sealed interface Result<T, E> {

  record Success<T, E>(T value) implements Result<T, E> {}

  record Failure<T, E>(E error) implements Result<T, E> {}

  static <T, E> Result<T, E> success(T value) {
    return new Success<>(value);
  }

  static <T, E> Result<T, E> failure(E error) {
    return new Failure<>(error);
  }

  default <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
    return switch (this) {
      case Success<T, E>(var value) -> new Success<>(mapper.apply(value));
      case Failure<T, E>(var error) -> new Failure<>(error);
    };
  }

  default <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper) {
    return switch (this) {
      case Success<T, E>(var value) -> mapper.apply(value);
      case Failure<T, E>(var error) -> new Failure<>(error);
    };
  }
}
