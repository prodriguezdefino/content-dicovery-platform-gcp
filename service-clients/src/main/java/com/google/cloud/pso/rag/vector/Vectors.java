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

import com.google.cloud.pso.rag.common.Result;
import com.google.cloud.pso.rag.common.Result.ErrorResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** */
public interface Vectors {

  sealed interface Request permits Search, Store, Delete {}

  sealed interface Search extends Request permits VectorSearch.SearchRequest {}

  sealed interface Store extends Request permits VectorSearch.UpsertRequest {}

  sealed interface Delete extends Request permits VectorSearch.RemoveRequest {}

  sealed interface Response permits StoreResponse, SearchResponse, DeleteResponse {}

  sealed interface StoreResponse extends Response permits VectorSearch.UpsertResponse {}

  sealed interface SearchResponse extends Response permits VectorSearch.NeighborsResponse {
    List<Neighbors> nearestNeighbors();
  }

  sealed interface DeleteResponse extends Response permits VectorSearch.RemoveResponse {}

  public record Datapoint(String datapointId, List<Double> featureVector) {
    public Datapoint(List<Double> values) {
      this("dummyId", values);
    }
  }

  public record Neighbor(Double distance, Datapoint datapoint) {}

  public record Neighbors(String id, List<Neighbor> neighbors) {}

  static CompletableFuture<Result<? extends SearchResponse, ErrorResponse>> findNearestNeighbors(
      Search request) {
    return switch (request) {
      case VectorSearch.SearchRequest vectorSearch -> VectorSearch.search(vectorSearch);
    };
  }

  static CompletableFuture<Result<? extends StoreResponse, ErrorResponse>> storeVector(
      Store request) {
    return switch (request) {
      case VectorSearch.UpsertRequest storeVector -> VectorSearch.store(storeVector);
    };
  }

  static CompletableFuture<Result<? extends DeleteResponse, ErrorResponse>> removeVectors(
      Delete request) {
    return switch (request) {
      case VectorSearch.RemoveRequest removeVectors -> VectorSearch.remove(removeVectors);
    };
  }
}
