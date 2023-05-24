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
package com.google.cloud.pso.data.query.service;

import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** */
@ApplicationScoped
public class BigTableService {

  @ConfigProperty(name = "bt.instance")
  String bigTableInstanceName;

  @ConfigProperty(name = "bt.table")
  String bigTableTableName;

  @ConfigProperty(name = "bt.columnfamily")
  String bigTableColumnFamily;

  @ConfigProperty(name = "bt.columnqualifier.content")
  String bigTableColumnQualifierContent;

  @ConfigProperty(name = "bt.columnqualifier.link")
  String bigTableColumnQualifierLink;

  @ConfigProperty(name = "project.id")
  String bigTableProjectId;

  BigtableDataClient bigTableClient;

  @PostConstruct
  public void init() throws IOException {
    bigTableClient =
        BigtableDataClient.create(
            BigtableDataSettings.newBuilder()
                .setInstanceId(bigTableInstanceName)
                .setProjectId(bigTableProjectId)
                .build());
  }

  public record ContentByKeyResponse(String key, String content, String sourceLink) {}

  public ContentByKeyResponse queryByPrefix(String key) {
    var row = bigTableClient.readRow(bigTableTableName, key);

    var content =
        row.getCells(bigTableColumnFamily, bigTableColumnQualifierContent).stream()
            // we want the latest version of the content, so reverse ordering here
            .max((r1, r2) -> Long.compare(r1.getTimestamp(), r2.getTimestamp()))
            .map(rc -> rc.getValue().toStringUtf8())
            .orElse("");

    var link =
        row.getCells(bigTableColumnFamily, bigTableColumnQualifierLink).stream()
            // we want the latest version of the content, so reverse ordering here
            .max((r1, r2) -> Long.compare(r1.getTimestamp(), r2.getTimestamp()))
            .map(rc -> rc.getValue().toStringUtf8())
            .orElse("");

    return new ContentByKeyResponse(key, content, link);
  }
}
