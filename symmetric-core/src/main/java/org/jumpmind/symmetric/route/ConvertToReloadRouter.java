/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.route;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.RelationsList;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.SequenceIdentifier;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * Converts multiple change data rows into reload batches for each table ordered by foreign key dependencies.
 * 
 */
public class ConvertToReloadRouter extends AbstractDataRouter implements IDataRouter, IBuiltInExtensionPoint {
    public final static String ROUTER_ID = "convertToReload";
    private final static String ROUTERS = "c2rRouters";
    private final static String SORTED_TABLES = "c2rSortedTables";
    private final static String INSERT_DATA_SQL = "insert into sym_data " +
            "(data_id, table_name, event_type, row_data, trigger_hist_id, channel_id, node_list, create_time) values (null, ?, ?, ?, ?, ?, ?, ?)";
    private final static int[] INSERT_DATA_TYPES = new int[] { Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.INTEGER,
            Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP };
    private final static String INSERT_DATA_EVENT_SQL = "insert into sym_data_event " +
            "(data_id, batch_id, create_time) values (?, ?, current_timestamp)";
    protected ISymmetricEngine engine;
    protected boolean firstTime = true;
    protected long routeMs, sortMs, insertTempMs, queryNodesMs, insertBatchMs;

    public ConvertToReloadRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,
            boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        if (initialLoad) {
            return toNodeIds(nodes, null);
        }
        long ts = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        Map<String, RouterInfo> routers = (Map<String, RouterInfo>) context.get(ROUTERS);
        if (routers == null) {
            routers = new HashMap<>();
            context.put(ROUTERS, routers);
        }
        String routerId = triggerRouter.getRouterId();
        RouterInfo routerInfo = routers.get(routerId);
        if (routerInfo == null) {
            routerInfo = new RouterInfo(triggerRouter.getRouter(), nodes);
            routers.put(routerId, routerInfo);
        }
        DataEventType eventType = dataMetaData.getData().getDataEventType();
        TableInfo tableInfo = routerInfo.getTableInfo(dataMetaData, triggerRouter);
        if (eventType.equals(DataEventType.INSERT) || eventType.equals(DataEventType.UPDATE)) {
            tableInfo.getCompoundIdList().add(getPkObjects(eventType, dataMetaData));
        } else if (eventType.equals(DataEventType.DELETE)) {
            return toNodeIds(nodes, null);
        }
        routeMs += (System.currentTimeMillis() - ts);
        return null;
    }

    protected Object[] getPkObjects(DataEventType eventType, DataMetaData dataMetaData) {
        String[] rowValues = null;
        if (eventType.equals(DataEventType.INSERT) || eventType.equals(DataEventType.UPDATE)) {
            rowValues = dataMetaData.getData().toParsedRowData();
        } else if (eventType.equals(DataEventType.DELETE)) {
            rowValues = dataMetaData.getData().toParsedPkData();
        }
        Column[] pkColumns = dataMetaData.getRelation().getPrimaryKeyColumns();
        String[] pkValues = (String[]) ArrayUtils.subarray(rowValues, 0, pkColumns.length);
        return engine.getDatabasePlatform().getObjectValues(engine.getSymmetricDialect().getBinaryEncoding(), pkValues, pkColumns);
    }

    @Override
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
        log.debug("Completing batch {}", batch.getBatchId());
        if (batch.getNodeId().equals(Constants.UNROUTED_NODE_ID)) {
            @SuppressWarnings("unchecked")
            Map<String, RouterInfo> routers = (Map<String, RouterInfo>) context.get(ROUTERS);
            List<TableInfo> tableInfos = new ArrayList<>();
            for (RouterInfo routerInfo : routers.values()) {
                tableInfos.addAll(routerInfo.getTableInfos());
            }
            tableInfos = sortTableInfos(context, tableInfos);
            ChannelRouterContext channelContext = ((ChannelRouterContext) context);
            queueEvents(channelContext, channelContext.getSqlTransaction(), batch, tableInfos);
        }
    }

    @Override
    public void contextCommitted(SimpleRouterContext context) {
        if (routeMs + sortMs + insertTempMs + queryNodesMs + insertBatchMs > 60000) {
            log.info("{} router millis are route={}, sort={}, insert.temp={}, query.nodes={}, insert.batches={}",
                    ROUTER_ID, routeMs, sortMs, insertTempMs, queryNodesMs, insertBatchMs);
        }
        routeMs = sortMs = insertTempMs = queryNodesMs = insertBatchMs = 0;
    }

    protected List<TableInfo> sortTableInfos(SimpleRouterContext context, Collection<TableInfo> tableInfos) {
        long ts = System.currentTimeMillis();
        RelationsList sortedRelations = getAllSortedRelations(context);
        Map<Table, TableInfo> tableInfosByTable = new HashMap<>();
        for (TableInfo tableInfo : tableInfos) {
            tableInfosByTable.put(tableInfo.getTable(), tableInfo);
        }
        List<TableInfo> sortedTableInfos = new ArrayList<>();
        for (Relation relation : sortedRelations) {
            TableInfo tableInfo = tableInfosByTable.get(relation);
            if (tableInfo != null) {
                sortedTableInfos.add(tableInfo);
            }
        }
        sortMs += (System.currentTimeMillis() - ts);
        return sortedTableInfos;
    }

    protected RelationsList getAllSortedRelations(SimpleRouterContext context) {
        RelationsList sortedRelations = (RelationsList) context.get(SORTED_TABLES);
        if (sortedRelations == null) {
            List<TriggerHistory> histories = null;
            if (firstTime) {
                histories = engine.getTriggerRouterService().getActiveTriggerHistories();
                firstTime = false;
            } else {
                histories = engine.getTriggerRouterService().getActiveTriggerHistoriesFromCache();
            }
            RelationsList allRelations = new RelationsList(histories.size());
            for (TriggerHistory history : histories) {
                Relation relation = engine.getDatabasePlatform().getRelationFromCache(history.getSourceCatalogName(),
                        history.getSourceSchemaName(), history.getSourceTableName(), false);
                if (relation != null) {
                    allRelations.add(relation);
                }
            }
            sortedRelations = Database.sortByForeignKeys(allRelations);
        }
        return sortedRelations;
    }

    protected void queueEvents(ChannelRouterContext context, ISqlTransaction transaction, OutgoingBatch origBatch, List<TableInfo> tableInfos) {
        final long loadId = engine.getSequenceService().nextVal(transaction, Constants.SEQUENCE_OUTGOING_BATCH_LOAD_ID);
        final int typeForId = engine.getSymmetricDialect().getSqlTypeForIds();
        boolean isPostgres = engine.getDatabasePlatform().getName().equals(DatabaseNamesConstants.POSTGRESQL);
        long ts = System.currentTimeMillis();
        transaction.flush();
        for (TableInfo tableInfo : tableInfos) {
            RouterInfo routerInfo = tableInfo.getRouterInfo();
            String placeHolders = StringUtils.repeat("?", ", ", tableInfo.getPkColumnNames().length + 1);
            String tempSql = "insert into " + routerInfo.getTempTableName() + "(" + tableInfo.getPkColumnNamesAsString() + ", "
                    + " load_id) values (" + placeHolders + ")";
            if (isPostgres) {
                tempSql += " on conflict do nothing";
            }
            transaction.prepare(tempSql);
            int[] types = ArrayUtils.addAll(tableInfo.getPkColumnTypes(), new int[] { typeForId, typeForId });
            for (Object compoundId : tableInfo.getCompoundIdList()) {
                Object[] values = ArrayUtils.add((Object[]) compoundId, loadId);
                try {
                    transaction.addRow(null, values, types);
                } catch (UniqueKeyException e) {
                }
            }
        }
        insertTempMs += (System.currentTimeMillis() - ts);
        Map<String, OutgoingBatch> batchByNode = new HashMap<>();
        for (TableInfo tableInfo : tableInfos) {
            RouterInfo routerInfo = tableInfo.getRouterInfo();
            String reloadSql = getTempTableSql(routerInfo, tableInfo, loadId);
            List<String> nodes = null;
            if (StringUtils.isNotBlank(routerInfo.getNodeQuery())) {
                ts = System.currentTimeMillis();
                transaction.flush();
                nodes = transaction.query(routerInfo.getNodeQuery(), new StringMapper(), new Object[] { loadId }, new int[] { typeForId });
                queryNodesMs += (System.currentTimeMillis() - ts);
            }
            if (nodes == null || nodes.size() == 0) {
                nodes = routerInfo.getNodeIds();
            }
            ts = System.currentTimeMillis();
            long dataId = insertData(transaction, tableInfo, DataEventType.RELOAD.getCode(), reloadSql);
            String tableName = tableInfo.getTableName().toLowerCase();
            for (String nodeId : nodes) {
                OutgoingBatch batch = batchByNode.get(nodeId);
                if (batch == null) {
                    batch = newBatch(transaction, nodeId, loadId, tableInfo, origBatch.getSummary());
                    batchByNode.put(nodeId, batch);
                }
                batch.incrementTableCount(tableName);
                insertDataEvent(transaction, batch.getBatchId(), dataId);
                context.getDataIds().add(dataId);
            }
            insertBatchMs += (System.currentTimeMillis() - ts);
        }
        origBatch.setLoadId(loadId);
        transaction.flush();
    }

    protected String getTempTableSql(RouterInfo routerInfo, TableInfo tableInfo, long loadId) {
        String sql = "1=1";
        if (!StringUtils.isBlank(tableInfo.getInitialLoadSql())) {
            sql = tableInfo.getInitialLoadSql();
        }
        if (tableInfo.getPkColumnNames().length == 1) {
            sql += " and " + tableInfo.getPkColumnName() + " in (select " + tableInfo.getPkColumnName()
                    + " from " + routerInfo.getTempTableName() + " where load_id = " + loadId + ")";
        } else {
            sql += " and exists (select 1 from " + routerInfo.getTempTableName() + " r where "
                    + tableInfo.getPkColumnJoinSql() + " and r.load_id = " + loadId + ")";
        }
        return sql;
    }

    protected OutgoingBatch newBatch(ISqlTransaction transaction, String nodeId, long loadId, TableInfo tableInfo, String summary) {
        OutgoingBatch batch = new OutgoingBatch(Constants.UNROUTED_NODE_ID, tableInfo.getChannelId(), Status.NE);
        batch.setCreateBy(ROUTER_ID);
        batch.incrementRowCount(DataEventType.RELOAD);
        batch.setNodeId(nodeId);
        batch.setLoadId(loadId);
        batch.setSummary(summary);
        engine.getOutgoingBatchService().insertOutgoingBatch(transaction, batch);
        return batch;
    }

    protected long insertData(ISqlTransaction transaction, TableInfo tableInfo, String eventType, String sql) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        long dataId = transaction.insertWithGeneratedKey(INSERT_DATA_SQL,
                engine.getSymmetricDialect().getSequenceKeyName(SequenceIdentifier.DATA),
                engine.getSymmetricDialect().getSequenceName(SequenceIdentifier.DATA),
                new Object[] { tableInfo.getTableName(), eventType, sql, tableInfo.getTriggerHistory().getTriggerHistoryId(),
                        tableInfo.getChannelId(), null, now },
                INSERT_DATA_TYPES);
        return dataId;
    }

    protected void insertDataEvent(ISqlTransaction transaction, long batchId, long dataId) {
        transaction.prepareAndExecute(INSERT_DATA_EVENT_SQL, new Object[] { dataId, batchId },
                new int[] { Types.NUMERIC, Types.NUMERIC });
    }

    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }

    class RouterInfo {
        private Router router;
        private List<String> nodeIds = new ArrayList<>();
        private Map<Integer, TableInfo> tableInfos = new HashMap<>();
        private String tempTableName;
        private String nodeQuery;

        public RouterInfo(Router router, Set<Node> nodes) {
            this.router = router;
            for (Node node : nodes) {
                this.nodeIds.add(node.getNodeId());
            }
            String expression = router.getRouterExpression();
            Pattern pattern = Pattern.compile(".*\\s*temptable=(\\S*)\\s*.*", Pattern.CASE_INSENSITIVE);
            if (expression != null) {
                expression = expression.replaceAll("\n", " ").replaceAll("\r", " ");
                Matcher matcher = pattern.matcher(expression);
                if (matcher.matches()) {
                    tempTableName = matcher.group(1);
                }
                pattern = Pattern.compile(".*\\s*nodequery=\"(.*)\"", Pattern.CASE_INSENSITIVE);
                matcher = pattern.matcher(expression);
                if (matcher.matches()) {
                    nodeQuery = matcher.group(1);
                }
            }
            if (StringUtils.isBlank(tempTableName)) {
                throw new NotImplementedException("Missing temptable={name} for router expression.");
            }
        }

        public Router getRouter() {
            return router;
        }

        public List<String> getNodeIds() {
            return nodeIds;
        }

        public TableInfo getTableInfo(DataMetaData dataMetaData, TriggerRouter triggerRouter) {
            TableInfo tableInfo = tableInfos.get(dataMetaData.getTriggerHistory().getTriggerHistoryId());
            if (tableInfo == null) {
                tableInfo = newTableInfo(dataMetaData, triggerRouter);
                tableInfos.put(dataMetaData.getTriggerHistory().getTriggerHistoryId(), tableInfo);
            }
            return tableInfo;
        }

        public Collection<TableInfo> getTableInfos() {
            return tableInfos.values();
        }

        protected TableInfo newTableInfo(DataMetaData dataMetaData, TriggerRouter triggerRouter) {
            return new TableInfo(this, dataMetaData, triggerRouter);
        }

        public String getTempTableName() {
            return tempTableName;
        }

        public String getNodeQuery() {
            return nodeQuery;
        }
    }

    class TableInfo {
        protected RouterInfo routerInfo;
        protected Table table;
        protected String tableName;
        protected String channelId;
        protected String pkColumnName;
        protected String sourceCatalog;
        protected String sourceSchema;
        protected String targetCatalog;
        protected String targetSchema;
        protected String initialLoadSql;
        protected TriggerHistory triggerHistory;
        protected String[] pkColumnNames;
        protected String pkColumnNamesAsString;
        protected String pkColumnJoinSql;
        protected int[] pkColumnTypes;
        protected List<Object> compoundIdList = new ArrayList<>();

        public TableInfo(RouterInfo routerInfo, DataMetaData dataMetaData, TriggerRouter triggerRouter) {
            this.routerInfo = routerInfo;
            this.channelId = triggerRouter.getTrigger().getChannelId();
            this.sourceCatalog = triggerRouter.getTrigger().getSourceCatalogName();
            this.sourceSchema = triggerRouter.getTrigger().getSourceSchemaName();
            this.table = (Table) dataMetaData.getRelation();
            this.tableName = table.getName();
            this.pkColumnName = table.getPrimaryKeyColumnNames()[0];
            this.initialLoadSql = triggerRouter.getInitialLoadSelect();
            this.triggerHistory = dataMetaData.getTriggerHistory();
            Router router = triggerRouter.getRouter();
            if (router.isUseSourceCatalogSchema()) {
                this.targetCatalog = table.getCatalog();
                this.targetSchema = table.getSchema();
            } else {
                if (StringUtils.isNotBlank(router.getTargetCatalogName())) {
                    this.targetCatalog = router.getTargetCatalogName();
                }
                if (StringUtils.isNotBlank(router.getTargetSchemaName())) {
                    this.targetSchema = router.getTargetSchemaName();
                }
            }
            this.pkColumnNames = table.getPrimaryKeyColumnNames();
            this.pkColumnTypes = new int[table.getPrimaryKeyColumns().length];
            StringBuilder sbNames = new StringBuilder();
            StringBuilder sbJoin = new StringBuilder();
            int i = 0;
            for (Column column : table.getPrimaryKeyColumns()) {
                if (i > 0) {
                    sbNames.append(", ");
                    sbJoin.append(" and ");
                }
                this.pkColumnTypes[i++] = column.getJdbcTypeCode();
                sbNames.append(column.getName());
                sbJoin.append("r.").append(column.getName()).append(" = t.").append(column.getName());
            }
            this.pkColumnNamesAsString = sbNames.toString();
            this.pkColumnJoinSql = sbJoin.toString();
        }

        public RouterInfo getRouterInfo() {
            return routerInfo;
        }

        public Table getTable() {
            return table;
        }

        public String getTableName() {
            return tableName;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getPkColumnName() {
            return pkColumnName;
        }

        public String getSourceCatalog() {
            return sourceCatalog;
        }

        public String getSourceSchema() {
            return sourceSchema;
        }

        public String getTargetCatalog() {
            return targetCatalog;
        }

        public String getTargetSchema() {
            return targetSchema;
        }

        public String getInitialLoadSql() {
            return initialLoadSql;
        }

        public TriggerHistory getTriggerHistory() {
            return triggerHistory;
        }

        public String[] getPkColumnNames() {
            return pkColumnNames;
        }

        public List<Object> getCompoundIdList() {
            return compoundIdList;
        }

        public String getPkColumnNamesAsString() {
            return pkColumnNamesAsString;
        }

        public String getPkColumnJoinSql() {
            return pkColumnJoinSql;
        }

        public int[] getPkColumnTypes() {
            return pkColumnTypes;
        }
    }
}
