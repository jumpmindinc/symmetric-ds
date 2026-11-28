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
package org.jumpmind.symmetric.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.service.impl.AbstractSqlMap;
import org.jumpmind.symmetric.service.impl.MockParameterService;
import org.jumpmind.symmetric.service.impl.OutgoingBatchServiceSqlMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OutgoingBatchReadyQueueDbTest {
    private static final String TABLE_PREFIX = "sym";
    private static final String OUTGOING_BATCH = TableConstants.getTableName(TABLE_PREFIX, TableConstants.SYM_OUTGOING_BATCH);
    private static final Object[] READY_STATUSES = { Status.NE.name(), Status.QY.name(), Status.SE.name(), Status.LD.name(), Status.ER.name(),
            Status.IG.name(), Status.RS.name() };
    private static IDatabasePlatform platform;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
    }

    @AfterEach
    void dropOutgoingBatch() {
        Table table = readOutgoingBatchFromDatabase();
        if (table != null) {
            platform.dropTables(true, table);
        }
    }

    @Test
    void testAlterSwapsStatusIndexWhenReadyQueuesParameterChanges() {
        platform.createDatabase(readOutgoingBatchModel(false), false, false);
        assertStatusIndexes(false);
        Database readyQueuesModel = readOutgoingBatchModel(true);
        platform.alterDatabase(readyQueuesModel, null, false);
        assertStatusIndexes(true);
        assertFalse(platform.getDdlBuilder().isAlterDatabase(platform.readFromDatabase(readyQueuesModel.getTables()), readyQueuesModel));
        Database statusModel = readOutgoingBatchModel(false);
        platform.alterDatabase(statusModel, null, false);
        assertStatusIndexes(false);
        assertFalse(platform.getDdlBuilder().isAlterDatabase(platform.readFromDatabase(statusModel.getTables()), statusModel));
    }

    @Test
    void testReadyChannelsMatchUnsentBatches() {
        platform.createDatabase(readOutgoingBatchModel(true), false, false);
        insertBatches();
        Set<List<Object>> expected = new HashSet<>(Arrays.asList(
                Arrays.asList("node-1", "default", null),
                Arrays.asList("node-1", "reload", 0),
                Arrays.asList("node-1", "reload", 2),
                Arrays.asList("node-1", "reload", null),
                Arrays.asList("node-1", null, null),
                Arrays.asList("node-1", null, 1),
                Arrays.asList("node-2", "config", null),
                Arrays.asList("node-2", "default", null),
                Arrays.asList("node-3", "default", null),
                Arrays.asList("node-3", "reload", 1)));
        OutgoingBatchServiceSqlMap sqlMap = new OutgoingBatchServiceSqlMap(platform,
                AbstractSqlMap.mergeSqlReplacementTokens(null, TABLE_PREFIX));
        assertEquals(expected, queryReadyChannels(sqlMap.getSql("selectReadyChannels")));
        if (platform.getName().startsWith("postgres")) {
            assertEquals(expected, queryReadyChannels(sqlMap.getSql("selectReadyChannelsLooseScan")));
        }
    }

    private void insertBatches() {
        Object[][] batches = {
                { 1, "node-1", "default", "NE", null },
                { 2, "node-1", "default", "NE", null },
                { 3, "node-1", "default", "OK", null },
                { 4, "node-1", "reload", "LD", 0 },
                { 5, "node-1", "reload", "NE", 2 },
                { 6, "node-1", "reload", "IG", null },
                { 7, "node-1", null, "ER", null },
                { 8, "node-1", null, "NE", 1 },
                { 9, "node-1", "other", "OK", 3 },
                { 10, "node-2", "config", "QY", null },
                { 11, "node-2", "default", "SE", null },
                { 12, "node-2", "default", "RS", null },
                { 13, "node-2", "reload", "RQ", 1 },
                { 14, "node-3", "default", "RS", null },
                { 15, "node-3", "reload", "NE", 1 },
                { 16, "node-3", "reload", "OK", 2 },
                { 17, "node-4", "default", "OK", null } };
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        for (Object[] batch : batches) {
            sqlTemplate.update("insert into " + OUTGOING_BATCH + " (batch_id, node_id, channel_id, status, thread_id) values (?, ?, ?, ?, ?)", batch);
        }
    }

    private Set<List<Object>> queryReadyChannels(String sql) {
        List<Row> rows = platform.getSqlTemplate().query(sql, READY_STATUSES);
        return rows.stream()
                .map(row -> Arrays.<Object> asList(row.getString("node_id"), row.getString("channel_id"), row.getInteger("thread_id")))
                .collect(Collectors.toSet());
    }

    private static Database readOutgoingBatchModel(boolean isReadyQueuesEnabled) {
        MockParameterService parameterService = new MockParameterService(ParameterConstants.SYNC_USE_READY_QUEUES,
                Boolean.toString(isReadyQueuesEnabled));
        Database database = new AbstractTriggerTemplateTest.MockAbstractSymmetricDialect(parameterService, platform).readSymmetricSchemaFromXml();
        database.removeAllTablesExcept(platform.alterCaseToMatchDatabaseDefaultCase(OUTGOING_BATCH));
        return database;
    }

    private static Table readOutgoingBatchFromDatabase() {
        Relation relation = platform.getRelationFromCache(platform.alterCaseToMatchDatabaseDefaultCase(OUTGOING_BATCH), true);
        return relation instanceof Table table ? table : null;
    }

    private static void assertStatusIndexes(boolean isReadyQueuesEnabled) {
        Table table = readOutgoingBatchFromDatabase();
        assertNotNull(table);
        Map<String, IIndex> indexesByName = Arrays.stream(table.getIndices())
                .collect(Collectors.toMap(index -> index.getName().toLowerCase(), index -> index));
        IIndex readyQueueIndex = indexesByName.get(TABLE_PREFIX + "_" + OutgoingBatchStatusIndexSelector.IDX_OB_READY_QUEUE);
        IIndex statusIndex = indexesByName.get(TABLE_PREFIX + "_" + OutgoingBatchStatusIndexSelector.IDX_OB_STATUS);
        if (isReadyQueuesEnabled) {
            assertNotNull(readyQueueIndex);
            assertNull(statusIndex);
            assertEquals(Arrays.asList("status", "node_id", "channel_id", "thread_id"), Arrays.stream(readyQueueIndex.getColumns())
                    .map(column -> column.getName().toLowerCase()).collect(Collectors.toList()));
        } else {
            assertNotNull(statusIndex);
            assertNull(readyQueueIndex);
        }
    }
}
