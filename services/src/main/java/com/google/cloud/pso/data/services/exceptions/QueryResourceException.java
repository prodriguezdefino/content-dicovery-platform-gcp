/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.cloud.pso.data.services.exceptions;

/** Represents errors occurred while interacting with the query resource. */
public class QueryResourceException extends RuntimeException {

  private final String queryText;
  private final String sessionId;

  public QueryResourceException(String message, String queryText, String sessionId) {
    super(message);
    this.queryText = queryText;
    this.sessionId = sessionId;
  }

  public QueryResourceException(
      String message, String queryText, String sessionId, Throwable cause) {
    super(message, cause);
    this.queryText = queryText;
    this.sessionId = sessionId;
  }

  public String getQueryText() {
    return queryText;
  }

  public String getSessionId() {
    return sessionId;
  }
}
