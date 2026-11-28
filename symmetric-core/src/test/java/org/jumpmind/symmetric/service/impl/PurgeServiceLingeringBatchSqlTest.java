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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.mapper.LongMapper;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Runs the lingering-batch purge statements of {@link PurgeServiceSqlMap} against an H2 database. A lingering batch is an OK batch that the purge range already
 * passed while it was still outstanding. Its sym_data rows can also be routed to batches for other nodes that are still outstanding, either as separate batches
 * (routing.use.non.common.for.incoming) or as rows of the same common batch.
 */
public class PurgeServiceLingeringBatchSqlTest {
    private static final String TABLE_PREFIX = "sym";
    private static final String NODE_LOADED = "dealer-loaded";
    private static final String NODE_OFFLINE = "dealer-offline";
    private static final long ITEM_DATA_ID = 1;
    private static final long ITEM_DESCRIPTION_DATA_ID = 2;
    private static final long INVENTORY_TRUCK_DATA_ID = 3;
    private static final long UNSHARED_DATA_ID = 4;
    private static final long LINGERING_BATCH_ID = 101;
    private static final long OUTSTANDING_BATCH_ID = 102;
    private static final long COMMON_BATCH_ID = 201;
    private SingleConnectionDataSource dataSource;
    private ISqlTemplate sqlTemplate;
    private PurgeServiceSqlMap sqlMap;

    @BeforeEach
    public void setUp() throws Exception {
        Class.forName("org.h2.Driver");
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:lingering" + UUID.randomUUID().toString().replace("-", ""));
        dataSource = new SingleConnectionDataSource(connection, true);
        IDatabasePlatform platform = JdbcDatabasePlatformFactory.getInstance().create(dataSource, new SqlTemplateSettings(), true, false);
        sqlTemplate = platform.getSqlTemplate();
        sqlMap = new PurgeServiceSqlMap(platform, AbstractSqlMap.mergeSqlReplacementTokens(null, TABLE_PREFIX));
        sqlTemplate.update("create table sym_data (data_id bigint not null primary key, table_name varchar(255))");
        sqlTemplate.update("create table sym_data_event (data_id bigint not null, batch_id bigint not null, primary key (data_id, batch_id))");
        sqlTemplate.update("create table sym_outgoing_batch (batch_id bigint not null, node_id varchar(50) not null, status char(2), "
                + "primary key (batch_id, node_id))");
    }

    @AfterEach
    public void tearDown() {
        dataSource.destroy();
    }

    @Test
    public void testDeleteDataByBatchIdKeepsRowsSharedWithOutstandingBatchForAnotherNode() {
        insertData(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID);
        insertBatch(LINGERING_BATCH_ID, NODE_LOADED, Status.OK, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID);
        insertBatch(OUTSTANDING_BATCH_ID, NODE_OFFLINE, Status.NE, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID);
        int deletedCount = deleteDataByBatchId(LINGERING_BATCH_ID);
        assertEquals(0, deletedCount);
        assertEquals(Arrays.asList(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID), findDataIds());
    }

    @Test
    public void testDeleteDataByBatchIdDeletesOnlyRowsWithoutOutstandingBatch() {
        insertData(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID, UNSHARED_DATA_ID);
        insertBatch(LINGERING_BATCH_ID, NODE_LOADED, Status.OK, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, UNSHARED_DATA_ID);
        insertBatch(OUTSTANDING_BATCH_ID, NODE_OFFLINE, Status.NE, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID);
        int deletedCount = deleteDataByBatchId(LINGERING_BATCH_ID);
        assertEquals(1, deletedCount);
        assertEquals(Arrays.asList(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID), findDataIds());
    }

    @Test
    public void testDeleteDataByBatchIdDeletesRowsWhenEveryBatchIsOk() {
        insertData(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID);
        insertBatch(LINGERING_BATCH_ID, NODE_LOADED, Status.OK, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID);
        insertBatch(OUTSTANDING_BATCH_ID, NODE_OFFLINE, Status.OK, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID, INVENTORY_TRUCK_DATA_ID);
        int deletedCount = deleteDataByBatchId(LINGERING_BATCH_ID);
        assertEquals(2, deletedCount);
        assertEquals(Arrays.asList(INVENTORY_TRUCK_DATA_ID), findDataIds());
    }

    @Test
    public void testDeleteDataByBatchIdKeepsRowsOfCommonBatchWithOutstandingNode() {
        insertData(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID);
        insertBatch(COMMON_BATCH_ID, NODE_LOADED, Status.OK, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID);
        insertBatchStatus(COMMON_BATCH_ID, NODE_OFFLINE, Status.NE);
        int deletedCount = deleteDataByBatchId(COMMON_BATCH_ID);
        assertEquals(0, deletedCount);
        assertEquals(Arrays.asList(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID), findDataIds());
    }

    @Test
    public void testDeleteDataByBatchIdDeletesRowsOfCommonBatchWhenEveryNodeIsOk() {
        insertData(ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID);
        insertBatch(COMMON_BATCH_ID, NODE_LOADED, Status.OK, ITEM_DATA_ID, ITEM_DESCRIPTION_DATA_ID);
        insertBatchStatus(COMMON_BATCH_ID, NODE_OFFLINE, Status.OK);
        int deletedCount = deleteDataByBatchId(COMMON_BATCH_ID);
        assertEquals(2, deletedCount);
        assertEquals(Arrays.asList(), findDataIds());
    }

    @Test
    public void testCountCommonBatchNotStatusForBatchIdCountsOutstandingNodes() {
        insertBatch(COMMON_BATCH_ID, NODE_LOADED, Status.OK);
        insertBatchStatus(COMMON_BATCH_ID, NODE_OFFLINE, Status.NE);
        insertBatch(LINGERING_BATCH_ID, NODE_LOADED, Status.OK);
        assertEquals(1, countCommonBatchNotOk(COMMON_BATCH_ID));
        assertEquals(0, countCommonBatchNotOk(LINGERING_BATCH_ID));
    }

    private int deleteDataByBatchId(long batchId) {
        return sqlTemplate.update(sqlMap.getSql("deleteDataByBatchId"), batchId, Status.OK.name());
    }

    private long countCommonBatchNotOk(long batchId) {
        return sqlTemplate.queryForLong(sqlMap.getSql("countCommonBatchNotStatusForBatchId"), batchId, Status.OK.name());
    }

    private void insertData(long... dataIds) {
        for (long dataId : dataIds) {
            sqlTemplate.update("insert into sym_data (data_id, table_name) values (?, ?)", dataId, "table_" + dataId);
        }
    }

    private void insertBatch(long batchId, String nodeId, Status status, long... dataIds) {
        insertBatchStatus(batchId, nodeId, status);
        for (long dataId : dataIds) {
            sqlTemplate.update("insert into sym_data_event (data_id, batch_id) values (?, ?)", dataId, batchId);
        }
    }

    private void insertBatchStatus(long batchId, String nodeId, Status status) {
        sqlTemplate.update("insert into sym_outgoing_batch (batch_id, node_id, status) values (?, ?, ?)", batchId, nodeId, status.name());
    }

    private List<Long> findDataIds() {
        return sqlTemplate.query("select data_id from sym_data order by data_id", new LongMapper());
    }
}
