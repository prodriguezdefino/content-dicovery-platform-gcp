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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** */
public interface Vectors {

  sealed interface Request permits Search, Store {}

  sealed interface Search extends Request permits VectorSearch.Request {}

  sealed interface Store extends Request permits VectorSearch.Request {}

  sealed interface Response permits StoreResponse, SearchResponse {}

  sealed interface StoreResponse extends Response permits VectorSearch.StoreResponse {}

  sealed interface SearchResponse extends Response permits VectorSearch.SearchResponse {}

  record ErrorResponse(String message, Optional<Throwable> cause)
      implements VectorSearch.SearchResponse, VectorSearch.StoreResponse {
    public ErrorResponse(String message) {
      this(message, Optional.empty());
    }
  }

  static CompletableFuture<? extends SearchResponse> findNearestNeighbors(Search request) {
    return switch (request) {
      case VectorSearch.Request vectorSearch -> VectorSearch.search(vectorSearch);
    };
  }

  static CompletableFuture<? extends StoreResponse> storeVector(Store request) {
    return switch (request) {
      case VectorSearch.Request storeVector -> VectorSearch.store(storeVector);
    };
  }
}
