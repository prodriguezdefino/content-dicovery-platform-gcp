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
package com.google.cloud.pso.data.services.beans;

import autovalue.shaded.com.google.common.collect.Lists;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import com.google.cloud.pso.beam.contentextract.clients.Types;
import com.google.cloud.pso.beam.contentextract.clients.utils.Utilities;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@ApplicationScoped
public class BigTableService {
  private static final Logger LOG = LoggerFactory.getLogger(BigTableService.class);

  private static final Comparator<RowCell> ORDERED_CELL_COMPARATOR =
      (r1, r2) -> Long.compare(r1.getTimestamp(), r2.getTimestamp());

  private final String instanceName;
  private final String contentTableName;
  private final String queryContextTableName;
  private final String contentColumnFamily;
  private final String queryContextColumnFamily;
  private final String columnQualifierContent;
  private final String columnQualifierLink;
  private final String columnQualifierContext;
  private final String projectId;

  private BigtableDataClient bigTableClient;

  public BigTableService(ServiceTypes.BigTableConfiguration config) {
    this.instanceName = config.instanceName();
    this.contentTableName = config.contentTableName();
    this.queryContextTableName = config.queryContextTableName();
    this.contentColumnFamily = config.contentColumnFamily();
    this.queryContextColumnFamily = config.queryContextColumnFamily();
    this.columnQualifierContent = config.columnQualifierContent();
    this.columnQualifierLink = config.columnQualifierLink();
    this.columnQualifierContext = config.columnQualifierContext();
    this.projectId = config.projectId();
  }

  @PostConstruct
  public void init() throws IOException {
    bigTableClient =
        BigtableDataClient.create(
            BigtableDataSettings.newBuilder()
                .setInstanceId(instanceName)
                .setProjectId(projectId)
                .build());
  }

  public record ContentByKeyResponse(String key, String content, String sourceLink) {}

  public record QAndA(String question, String answer) {

    public List<Types.Exchange> toExchange() {
      return Lists.newArrayList(
          new Types.Exchange("user", question), new Types.Exchange("bot", answer));
    }
  }

  static QAndA fromAppended(String appended) {
    var qas = appended.split("___");
    if (qas.length != 2) {
      LOG.warn("will be skipping previous non valid QAndA content: " + appended);
      return null;
    }
    return new QAndA(qas[0], qas[1]);
  }

  public record ConversationContextBySessionResponse(String session, List<QAndA> qAndAs) {}

  Row readRowWithRetries(String tableId, String key) {
    return Utilities.executeOperation(
        Utilities.buildRetriableExecutorForOperation(
            "readRow", Lists.newArrayList(ApiException.class)),
        () -> bigTableClient.readRow(tableId, key));
  }

  @Timed(name = "bt.retrieve.content", unit = MetricUnits.MILLISECONDS)
  public ContentByKeyResponse queryByPrefix(String key) {
    var row = readRowWithRetries(contentTableName, key);

    var content =
        Optional.ofNullable(row)
            .map(
                r ->
                    r.getCells(contentColumnFamily, columnQualifierContent).stream()
                        .max(ORDERED_CELL_COMPARATOR)
                        .map(rc -> rc.getValue().toStringUtf8())
                        .orElse(""))
            .orElse("");

    var link =
        Optional.ofNullable(row)
            .map(
                r ->
                    r.getCells(contentColumnFamily, columnQualifierLink).stream()
                        .max(ORDERED_CELL_COMPARATOR)
                        .map(rc -> rc.getValue().toStringUtf8())
                        .orElse(""))
            .orElse("");

    return new ContentByKeyResponse(key, content, link);
  }

  @Timed(name = "bt.retrieve.exchanges", unit = MetricUnits.MILLISECONDS)
  public ConversationContextBySessionResponse retrieveConversationContext(String session) {
    if (session.isEmpty() || session.isBlank()) {
      // nothing to be retrieved.
      return new ConversationContextBySessionResponse(session, Lists.newArrayList());
    }
    var row = readRowWithRetries(queryContextTableName, session);

    return new ConversationContextBySessionResponse(
        session,
        Optional.ofNullable(row)
            .map(
                r ->
                    r
                        .getCells(
                            queryContextColumnFamily,
                            ByteString.copyFromUtf8(columnQualifierContext))
                        .stream()
                        .sorted(ORDERED_CELL_COMPARATOR)
                        .map(rc -> fromAppended(rc.getValue().toStringUtf8()))
                        .filter(qaa -> qaa != null)
                        .toList())
            .orElse(Lists.newArrayList()));
  }

  @Timed(name = "bt.store.exchange", unit = MetricUnits.MILLISECONDS)
  public void storeQueryToContext(String session, String query, String answer) {
    if (session.isEmpty() || session.isBlank()) {
      // nothing to be stored.
      return;
    }
    RowMutation rowMutation =
        RowMutation.create(queryContextTableName, session)
            .setCell(
                queryContextColumnFamily,
                ByteString.copyFromUtf8(columnQualifierContext),
                Instant.now().toEpochMilli() * 1000,
                ByteString.copyFromUtf8(query + "___" + answer));
    Utilities.executeOperation(
        Utilities.buildRetriableExecutorForOperation(
            "storeQueryContext", Lists.newArrayList(ApiException.class)),
        () -> bigTableClient.mutateRow(rowMutation));
  }

  @Timed(name = "bt.delete.content", unit = MetricUnits.MILLISECONDS)
  public void deleteRowsByKeys(List<String> rowKeys) {
    try (var tableAdminClient = BigtableTableAdminClient.create(projectId, instanceName)) {
      // remove all the content rows with the provided keys, blocking until all of them are
      // completed
      rowKeys.forEach(key -> tableAdminClient.dropRowRange(contentTableName, key));
    } catch (Exception ex) {
      LOG.error("problems while removing content ids from BigTable.", ex);
      throw new RuntimeException(ex);
    }
  }

  @Timed(name = "bt.delete.session", unit = MetricUnits.MILLISECONDS)
  public void removeSessionInfo(String sessionId) {
    try (var tableAdminClient = BigtableTableAdminClient.create(projectId, instanceName)) {
      tableAdminClient.dropRowRange(queryContextTableName, sessionId);
    } catch (Exception ex) {
      LOG.error("problems while removing session ids from BigTable.", ex);
      throw new RuntimeException(ex);
    }
  }

  @Timed(name = "bt.retrieve.allcontent", unit = MetricUnits.MILLISECONDS)
  public List<String> retrieveAllContentEntries() {
    var contentKeys = Lists.<String>newArrayList();
    for (var row : bigTableClient.readRows(Query.create(contentTableName))) {
      contentKeys.add(row.getKey().toStringUtf8());
    }
    return contentKeys;
  }

  @PreDestroy
  public void close() {
    bigTableClient.close();
  }
}
