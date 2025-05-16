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

import java.util.Optional;
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

  static <T, E> Result<T, ErrorResponse> failure(String message, Throwable cause) {
    return new Failure<>(new ErrorResponse(message, Optional.of(cause)));
  }

  static <T, E> Result<T, ErrorResponse> failure(String message) {
    return new Failure<>(new ErrorResponse(message, Optional.empty()));
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

  default T orElseThrow(Function<? super E, ? extends RuntimeException> exceptionMapper) {
    return switch (this) {
      case Success<T, E>(var value) -> value;
      case Failure<T, E>(var error) -> throw exceptionMapper.apply(error);
    };
  }

  default T orElse(Function<? super E, T> exceptionMapper) {
    return switch (this) {
      case Success<T, E>(var value) -> value;
      case Failure<T, E>(var error) -> exceptionMapper.apply(error);
    };
  }

  default <U> Result<T, U> failMap(Function<? super E, ? extends U> failureMapper) {
    return switch (this) {
      case Success<T, ?>(var value) -> Result.success(value);
      case Failure<?, E>(var error) -> Result.failure(failureMapper.apply(error));
    };
  }

  record ErrorResponse(String message, Optional<Throwable> cause) {
    public ErrorResponse(String message) {
      this(message, Optional.empty());
    }
  }
}
