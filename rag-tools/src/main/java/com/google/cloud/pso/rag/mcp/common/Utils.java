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
package com.google.cloud.pso.rag.mcp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
public class Utils {
  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

  private Utils() {}

  @FunctionalInterface
  public interface CheckedSupplier<T> {
    T apply() throws Exception;
  }

  @FunctionalInterface
  public interface CheckedFunction<R, T> {
    T apply(R param) throws Exception;
  }

  public static <T> T execute(
      CheckedSupplier<T> toExecute, String errorMessage, Object... errorArgs) {
    try {
      var result = toExecute.apply();
      LOG.info("Completed execution: " + result.toString());
      return result;
    } catch (Exception ex) {
      String msg = String.format(errorMessage, errorArgs);
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }

  public static <R, T> T execute(
      CheckedFunction<R, T> toExecute, R param, String errorMessage, Object... errorArgs) {
    try {
      var result = toExecute.apply(param);
      LOG.info("Completed execution: " + result.toString());
      return result;
    } catch (Exception ex) {
      String msg = String.format(errorMessage, errorArgs);
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    }
  }
}
