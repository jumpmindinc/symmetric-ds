package org.jumpmind.symmetric.ext;

/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.RandomStringUtils;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.transform.ColumnPolicy;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.util.AppUtils;

public class StressTestHeartbeatListener
        implements org.jumpmind.symmetric.ext.IHeartbeatListener, org.jumpmind.symmetric.ext.ISymmetricEngineAware {
    private static final String SERVER_NODE_GROUP = "server";
    private static final String CLIENT_NODE_GROUP = "client";
    private static final String SERVER_TO_CLIENT = "server_to_client";
    private static final String CLIENT_TO_SERVER = "client_to_server";
    private static final String CONTROL_CHANNEL = "stress_test_control";
    private static final String SYNC_CHANNEL = "stress_test_sync";
    private static final String STRESS_TEST_CONTROL = "STRESS_TEST_CONTROL";
    private static final String STRESS_TEST_STATUS = "STRESS_TEST_STATUS";
    private static final String STRESS_TEST_ROW_OUTGOING = "STRESS_TEST_ROW_OUTGOING";
    private static final String STRESS_TEST_ROW_INCOMING = "STRESS_TEST_ROW_INCOMING";
    private String selectControlSql = "select * from " + STRESS_TEST_CONTROL;
    private String selectStatusSql = "select status from " + STRESS_TEST_STATUS + " where run_id=? and node_id = ?";
    private String insertStatusSql = "insert into " + STRESS_TEST_STATUS
            + " (run_id,node_id,status,start_time) values (?,?,?,current_timestamp)";
    private String updateStatusSql = "update " + STRESS_TEST_STATUS
            + " set status=?, end_time=current_timestamp where run_id=? and node_id = ?";
    private String dropOutgoingSql = "drop table if exists " + STRESS_TEST_ROW_OUTGOING;
    private String dropIncomingSql = "drop table if exists " + STRESS_TEST_ROW_INCOMING;
    private ISymmetricEngine engine;
    private Runnable runnable;
    private Thread thread;
    private boolean initialized = false;
    private Map<Integer, Long> outgoingCounts = new HashMap<Integer, Long>();

    /**
     * Creates the STRESS_TEST_CONTROL and STRESS_TEST_STATUS tables and adds triggers
     */
    private void initStressTest() {
        createChannels();
        engine.getDatabasePlatform().getDdlBuilder().setDelimitedIdentifierModeOn(false);
        createStressTestControlTable();
        if (!engine.getTriggerRouterService().doesTriggerExistForTable(STRESS_TEST_CONTROL)) {
            addStressTestControlTriggers();
        }
        createStressTestStatusTable();
        if (!engine.getTriggerRouterService().doesTriggerExistForTable(STRESS_TEST_STATUS)) {
            addStressTestStatusTriggers();
        }
        engine.syncTriggers();
        initialized = true;
    }

    private void createChannels() {
        if (engine.getConfigurationService().getChannel(CONTROL_CHANNEL) == null) {
            Channel control = new Channel(CONTROL_CHANNEL, 100000);
            engine.getConfigurationService().saveChannel(control, false);
        }
        if (engine.getConfigurationService().getChannel(SYNC_CHANNEL) == null) {
            Channel sync = new Channel(SYNC_CHANNEL, 100001);
            engine.getConfigurationService().saveChannel(sync, false);
        }
    }

    /**
     * Creates the STRESS_TEST_ROW_OUTGOING table, seeds initial test data, configures the table, and loads the table to the client
     */
    private void initStressTestRowOutgoing(int payloadColumns, int runId, int initialSeedSize) {
        createStressTestRowOutgoingTable(payloadColumns);
        seedOutgoing(runId, initialSeedSize, payloadColumns);
        addStressTestRowOutgoingConfig();
        engine.syncTriggers();
        for (Node client : engine.getNodeService().findTargetNodesFor(NodeGroupLinkAction.W)) {
            // Drop the table using the config channel to force drop order
            engine.getDataService().sendSQL(client.getNodeId(), null, null, "SYM_PARAMETER", dropOutgoingSql);
            engine.getDataService().sendSchema(client.getNodeId(), null, null, STRESS_TEST_ROW_OUTGOING, true, false, false, false);
            if (initialSeedSize > 0) {
                engine.getDataService().reloadTable(client.getNodeId(), null, null, STRESS_TEST_ROW_OUTGOING);
            }
        }
    }

    /**
     * Creates the STRESS_TEST_ROW_INCOMING table and configures the table
     */
    private void initStressTestRowIncoming(int payloadColumns) {
        createStressTestRowIncomingTable(payloadColumns);
        addStressTestRowIncomingConfig();
        engine.syncTriggers();
        for (Node client : engine.getNodeService().findTargetNodesFor(NodeGroupLinkAction.W)) {
            // Drop the table using the config channel to force drop order
            engine.getDataService().sendSQL(client.getNodeId(), null, null, "SYM_PARAMETER", dropIncomingSql);
            engine.getDataService().sendSchema(client.getNodeId(), null, null, STRESS_TEST_ROW_INCOMING, true, false, false, false);
        }
    }

    /**
     * Creates the STRESS_TEST_CONTROL table
     */
    private void createStressTestControlTable() {
        Column runId = new Column("RUN_ID");
        runId.setMappedType("INTEGER");
        runId.setPrimaryKey(true);
        runId.setRequired(true);
        Column clientCommitSleepMs = new Column("CLIENT_COMMIT_SLEEP_MS");
        clientCommitSleepMs.setMappedType("BIGINT");
        Column clientCommitRows = new Column("CLIENT_COMMIT_ROWS");
        clientCommitRows.setMappedType("BIGINT");
        Column serverCommitSleepMs = new Column("SERVER_COMMIT_SLEEP_MS");
        serverCommitSleepMs.setMappedType("BIGINT");
        Column serverCommitRows = new Column("SERVER_COMMIT_ROWS");
        serverCommitRows.setMappedType("BIGINT");
        Column payloadColumns = new Column("PAYLOAD_COLUMNS");
        payloadColumns.setMappedType("INTEGER");
        Column initialSeedSize = new Column("INITIAL_SEED_SIZE");
        initialSeedSize.setMappedType("BIGINT");
        Column duration = new Column("DURATION_MINUTES");
        duration.setMappedType("BIGINT");
        Table table = new Table(STRESS_TEST_CONTROL, runId, clientCommitSleepMs, clientCommitRows, serverCommitSleepMs, serverCommitRows,
                payloadColumns, initialSeedSize, duration);
        engine.getDatabasePlatform().createTables(true, true, table);
    }

    /**
     * Creates the STRESS_TEST_STATUS table
     */
    private void createStressTestStatusTable() {
        Column runId = new Column("RUN_ID");
        runId.setMappedType("INTEGER");
        runId.setPrimaryKey(true);
        runId.setRequired(true);
        Column nodeId = new Column("NODE_ID");
        nodeId.setMappedType("VARCHAR");
        nodeId.setPrimaryKey(true);
        nodeId.setRequired(true);
        nodeId.setSize("50");
        Column status = new Column("STATUS");
        status.setMappedType("VARCHAR");
        status.setSize("10");
        status.setRequired(true);
        status.setDefaultValue("NEW");
        Column startTime = new Column("START_TIME");
        startTime.setMappedType("TIMESTAMP");
        Column endTime = new Column("END_TIME");
        endTime.setMappedType("TIMESTAMP");
        Table table = new Table(STRESS_TEST_STATUS, runId, nodeId, status, startTime, endTime);
        engine.getDatabasePlatform().createTables(true, true, table);
    }

    /**
     * Creates the STRESS_TEST_RUN_OUTGOING table
     */
    private void createStressTestRowOutgoingTable(int payloadColumns) {
        Column[] defaultColumns = getDefaultStressTestRowColumns();
        Table table = new Table(STRESS_TEST_ROW_OUTGOING, defaultColumns);
        List<Column> payloads = getPayloadColumns(payloadColumns);
        table.addColumns(payloads);
        engine.getDatabasePlatform().createTables(true, true, table);
    }

    /**
     * Creates the STRESS_TEST_RUN_INCOMING table
     */
    private void createStressTestRowIncomingTable(int payloadColumns) {
        Column[] defaultColumns = getDefaultStressTestRowColumns();
        Table table = new Table(STRESS_TEST_ROW_INCOMING, defaultColumns);
        List<Column> payloads = getPayloadColumns(payloadColumns);
        table.addColumns(payloads);
        engine.getDatabasePlatform().createTables(true, true, table);
    }

    /**
     * Creates the default columns for the STRESS_TEST_ROW_* tables
     */
    private Column[] getDefaultStressTestRowColumns() {
        Column rowId = new Column("ROW_ID");
        rowId.setMappedType("INTEGER");
        rowId.setPrimaryKey(true);
        rowId.setRequired(true);
        Column nodeId = new Column("NODE_ID");
        nodeId.setMappedType("VARCHAR");
        nodeId.setPrimaryKey(true);
        nodeId.setRequired(true);
        nodeId.setSize("50");
        Column runId = new Column("RUN_ID");
        runId.setMappedType("INTEGER");
        runId.setPrimaryKey(true);
        runId.setRequired(true);
        Column insertTime = new Column("INSERT_TIME");
        insertTime.setMappedType("TIMESTAMP");
        Column insertSyncTime = new Column("INSERT_SYNC_TIME");
        insertSyncTime.setMappedType("TIMESTAMP");
        Column[] columns = new Column[] { rowId, nodeId, runId, insertTime, insertSyncTime };
        return columns;
    }

    /**
     * Creates the payload columns for the STRESS_TEST_RUN_* tables
     */
    private List<Column> getPayloadColumns(int payloadColumns) {
        List<Column> payloads = new ArrayList<Column>();
        for (int c = 0; c < payloadColumns; c++) {
            Column column = new Column("PAYLOAD_" + c);
            column.setMappedType("VARCHAR");
            column.setSize("100");
            payloads.add(column);
        }
        return payloads;
    }

    /**
     * Adds triggers to the STRESS_TEST_CONTROL table
     */
    private void addStressTestControlTriggers() {
        addTrigger(STRESS_TEST_CONTROL, CONTROL_CHANNEL, SERVER_TO_CLIENT);
        addTrigger(STRESS_TEST_CONTROL, CONTROL_CHANNEL, CLIENT_TO_SERVER);
    }

    /**
     * Adds triggers to the STRESS_TEST_STATUS table
     */
    private void addStressTestStatusTriggers() {
        addTrigger(STRESS_TEST_STATUS, CONTROL_CHANNEL, SERVER_TO_CLIENT);
        addTrigger(STRESS_TEST_STATUS, CONTROL_CHANNEL, CLIENT_TO_SERVER);
    }

    /**
     * Adds triggers and transforms to the STRESS_TEST_ROW_OUTGOING table
     */
    private void addStressTestRowOutgoingConfig() {
        addTrigger(STRESS_TEST_ROW_OUTGOING, SYNC_CHANNEL, SERVER_TO_CLIENT);
        addOutgoingTransform();
    }

    /**
     * Adds triggers and transforms to the STRESS_TEST_ROW_INCOMING table
     */
    private void addStressTestRowIncomingConfig() {
        addTrigger(STRESS_TEST_ROW_INCOMING, SYNC_CHANNEL, SERVER_TO_CLIENT);
        addTrigger(STRESS_TEST_ROW_INCOMING, SYNC_CHANNEL, CLIENT_TO_SERVER);
        addIncomingTransform();
    }

    /**
     * Adds a trigger and trigger router to a table
     */
    private void addTrigger(String table, String channel, String routerId) {
        Trigger trigger = engine.getTriggerRouterService().getTriggerById(table);
        if (trigger == null) {
            trigger = new Trigger(table, channel);
            engine.getTriggerRouterService().saveTrigger(trigger);
        }
        TriggerRouter triggerRouter = engine.getTriggerRouterService().findTriggerRouterById(table, routerId);
        if (triggerRouter == null) {
            Router router = engine.getTriggerRouterService().getRouterById(routerId);
            if (router != null) {
                triggerRouter = new TriggerRouter(trigger, router);
                engine.getTriggerRouterService().saveTriggerRouter(triggerRouter);
            }
        }
    }

    /**
     * Adds transforms to the STRESS_TEST_ROW_OUTGOING table
     */
    private void addOutgoingTransform() {
        TransformTableNodeGroupLink outgoingTransform = new TransformTableNodeGroupLink();
        outgoingTransform.setTransformPoint(TransformPoint.LOAD);
        outgoingTransform.setTransformId("stress_test_outgoing_transform");
        outgoingTransform.setSourceTableName(STRESS_TEST_ROW_OUTGOING);
        outgoingTransform.setTargetTableName(STRESS_TEST_ROW_OUTGOING);
        outgoingTransform.setColumnPolicy(ColumnPolicy.IMPLIED);
        outgoingTransform.setNodeGroupLink(engine.getConfigurationService().getNodeGroupLinkFor(SERVER_NODE_GROUP, CLIENT_NODE_GROUP, true));
        TransformColumn outgoingInsertCol = new TransformColumn("INSERT_SYNC_TIME", "INSERT_SYNC_TIME", false, "variable",
                "system_timestamp");
        outgoingInsertCol.setTransformId(outgoingTransform.getTransformId());
        outgoingInsertCol.setTransformOrder(1);
        outgoingInsertCol.setIncludeOn(IncludeOnType.ALL);
        outgoingTransform.addTransformColumn(outgoingInsertCol);
        engine.getTransformService().saveTransformTable(outgoingTransform, true);
    }

    /**
     * Adds transforms to the STRESS_TEST_ROW_INCOMING table
     */
    private void addIncomingTransform() {
        TransformTableNodeGroupLink incomingTransform = new TransformTableNodeGroupLink();
        incomingTransform.setTransformPoint(TransformPoint.LOAD);
        incomingTransform.setTransformId("stress_test_incoming_transform");
        incomingTransform.setSourceTableName(STRESS_TEST_ROW_INCOMING);
        incomingTransform.setTargetTableName(STRESS_TEST_ROW_INCOMING);
        incomingTransform.setColumnPolicy(ColumnPolicy.IMPLIED);
        incomingTransform.setNodeGroupLink(engine.getConfigurationService().getNodeGroupLinkFor(CLIENT_NODE_GROUP, SERVER_NODE_GROUP, true));
        TransformColumn incomingInsertCol = new TransformColumn("INSERT_SYNC_TIME", "INSERT_SYNC_TIME", false, "variable",
                "system_timestamp");
        incomingInsertCol.setTransformId(incomingTransform.getTransformId());
        incomingInsertCol.setTransformOrder(3);
        incomingInsertCol.setIncludeOn(IncludeOnType.ALL);
        incomingTransform.addTransformColumn(incomingInsertCol);
        engine.getTransformService().saveTransformTable(incomingTransform, true);
    }

    /**
     * Checks the STRESS_TEST_CONTROL table for new stress test runs and starts a stress test accordingly
     */
    private void runTest(final Node me) {
        if (!initialized) {
            initStressTest();
        }
        List<Row> runs = engine.getSqlTemplate().query(selectControlSql);
        for (Row run : runs) {
            int runId = run.getInt("RUN_ID");
            int payloadColumns = run.getInt("PAYLOAD_COLUMNS");
            int initialSeedSize = run.getInt("INITIAL_SEED_SIZE");
            long duration = run.getLong("DURATION_MINUTES");
            String status = engine.getSqlTemplate().queryForString(selectStatusSql, runId, me.getNodeId());
            if (isMasterNode(me) && StringUtils.isBlank(status)) {
                initStressTestRowOutgoing(payloadColumns, runId, initialSeedSize);
                initStressTestRowIncoming(payloadColumns);
                long commitRows = run.getLong("SERVER_COMMIT_ROWS");
                long sleepMs = run.getLong("SERVER_COMMIT_SLEEP_MS");
                engine.getSqlTemplate().update(insertStatusSql, runId, me.getNodeId(), "RUNNING");
                for (Node client : engine.getNodeService().findTargetNodesFor(NodeGroupLinkAction.W)) {
                    engine.getSqlTemplate().update(insertStatusSql, runId, client.getNodeId(), "RUNNING");
                }
                fillOutgoing(runId, duration, commitRows, sleepMs, payloadColumns);
                engine.getSqlTemplate().update(updateStatusSql, "COMPLETE", runId, me.getNodeId());
            } else if (!isMasterNode(me) && !StringUtils.isBlank(status) && status.equals("RUNNING")) {
                long commitRows = run.getLong("CLIENT_COMMIT_ROWS");
                long sleepMs = run.getLong("CLIENT_COMMIT_SLEEP_MS");
                fillIncoming(runId, duration, commitRows, sleepMs, payloadColumns);
                engine.getSqlTemplate().update(updateStatusSql, "COMPLETE", runId, me.getNodeId());
            }
        }
    }

    private boolean isMasterNode(Node me) {
        return me.getNodeGroupId().equals(SERVER_NODE_GROUP);
    }

    /**
     * Fills the STRESS_TEST_OUTGOING table with test data
     */
    private void seedOutgoing(int runId, long rows, int payloadColumns) {
        long currentRows = outgoingCounts.containsKey(runId) ? outgoingCounts.get(runId) : 0;
        String sql = buildInsertSql(STRESS_TEST_ROW_OUTGOING, payloadColumns);
        while (currentRows < rows) {
            insert(sql, currentRows, runId, payloadColumns);
            currentRows++;
        }
        outgoingCounts.put(runId, currentRows);
    }

    /**
     * Fills the STRESS_TEST_OUTGOING table with test data
     */
    private void fillOutgoing(int runId, long duration, long commitRows, long sleepMs, int payloadColumns) {
        long currentRows = outgoingCounts.containsKey(runId) ? outgoingCounts.get(runId) : 0;
        long startTime = System.currentTimeMillis();
        long durationMs = duration * 60000;
        String sql = buildInsertSql(STRESS_TEST_ROW_OUTGOING, payloadColumns);
        while (System.currentTimeMillis() - startTime < durationMs) {
            for (long commitRow = 0; commitRow < commitRows; commitRow++) {
                insert(sql, currentRows + commitRow, runId, payloadColumns);
            }
            currentRows += commitRows;
            AppUtils.sleep(sleepMs);
        }
        outgoingCounts.put(runId, currentRows);
    }

    /**
     * Fills the STRESS_TEST_INCOMING table with test data
     */
    private void fillIncoming(int runId, long duration, long commitRows, long sleepMs, int payloadColumns) {
        long currentRows = 0;
        long startTime = System.currentTimeMillis();
        long durationMs = duration * 60000;
        String sql = buildInsertSql(STRESS_TEST_ROW_INCOMING, payloadColumns);
        while (System.currentTimeMillis() - startTime < durationMs) {
            for (long commitRow = 0; commitRow < commitRows; commitRow++) {
                insert(sql, currentRows + commitRow, runId, payloadColumns);
            }
            currentRows += commitRows;
            AppUtils.sleep(sleepMs);
        }
    }

    /**
     * Inserts test data into STRESS_TEST_OUTGOING or STRESS_TEST_INCOMING
     */
    private void insert(String sql, long rowId, int runId, int payloadColumns) {
        String nodeId = engine.getEngineName();
        Object[] values = new Object[payloadColumns + 3];
        values[0] = rowId;
        values[1] = nodeId;
        values[2] = runId;
        for (int c = 3; c < values.length; c++) {
            values[c] = RandomStringUtils.secure().nextAlphanumeric(100);
        }
        engine.getSqlTemplate().update(sql, values);
    }

    private String buildInsertSql(String table, int payloadColumns) {
        StringBuilder sql = new StringBuilder("insert into " + table);
        StringBuilder columns = new StringBuilder(" (row_id,node_id,run_id,insert_time");
        StringBuilder values = new StringBuilder(" values (?,?,?,current_timestamp");
        List<Column> payloadColumnList = getPayloadColumns(payloadColumns);
        for (Column column : payloadColumnList) {
            columns.append(",").append(column.getName().toLowerCase());
            values.append(",?");
        }
        columns.append(")");
        values.append(")");
        return sql.append(columns).append(values).toString();
    }

    @Override
    public void heartbeat(final Node me) {
        if (runnable == null) {
            runnable = new Runnable() {
                @Override
                public void run() {
                    runTest(me);
                }
            };
        }
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(runnable);
            thread.start();
        }
    }

    @Override
    public long getTimeBetweenHeartbeatsInSeconds() {
        return 0;
    }

    @Override
    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }
}