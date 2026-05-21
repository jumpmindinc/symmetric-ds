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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_BATCH_DATE;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_BATCHES;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_DATA;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_DATA_ROWS;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_DELETE_EVENT_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_INSERT_EVENT_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_OLDEST_BATCH_TIME;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_OTHER_EVENT_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_RELOAD_EVENT_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_ROWS_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_TOTAL_BYTES;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_TOTAL_EXTRACT_MILLIS;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_TOTAL_LOAD_MILLIS;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_TOTAL_MILLIS;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_TOTAL_NETWORK_MILLIS;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_TOTAL_ROUTER_MILLIS;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.COL_UPDATE_EVENT_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_BATCH_ID;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_BYTE_COUNT;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_CHANNEL_ID;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_ERROR_FLAG;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_LAST_UPDATE_TIME;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_NODE_ID;
import static org.jumpmind.symmetric.service.impl.OutgoingBatchService.OUT_BATCH_COL_STATUS;

import java.util.Date;

import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.model.BacklogSummary;
import org.jumpmind.symmetric.model.OutgoingBatchSummary;
import org.jumpmind.symmetric.model.OutgoingBatchSummaryByNodeBriefStats;
import org.jumpmind.symmetric.service.impl.OutgoingBatchService.BacklogSummaryMapper;
import org.jumpmind.symmetric.service.impl.OutgoingBatchService.OutgoingBatchSummaryByNodeBriefStatsMapper;
import org.jumpmind.symmetric.service.impl.OutgoingBatchService.OutgoingBatchSummaryMapper;
import org.junit.jupiter.api.Test;

public class OutgoingBatchServiceTest {
    @Test
    public void testSummaryMapperWithoutNodeOrChannel() {
        Row row = buildSummaryRow();
        OutgoingBatchSummary summary = new OutgoingBatchSummaryMapper(false, false).mapRow(row);
        assertNull(summary.getNodeId());
        assertNull(summary.getChannel());
        assertCommonSummaryFields(summary);
    }

    @Test
    public void testSummaryMapperWithNode() {
        Row row = buildSummaryRow();
        OutgoingBatchSummary summary = new OutgoingBatchSummaryMapper(true, false).mapRow(row);
        assertEquals("node1", summary.getNodeId());
        assertNull(summary.getChannel());
        assertCommonSummaryFields(summary);
    }

    @Test
    public void testSummaryMapperWithNodeAndChannel() {
        Row row = buildSummaryRow();
        OutgoingBatchSummary summary = new OutgoingBatchSummaryMapper(true, true).mapRow(row);
        assertEquals("node1", summary.getNodeId());
        assertEquals("default", summary.getChannel());
        assertCommonSummaryFields(summary);
    }

    @Test
    public void testSummaryMapperErrorFlag() {
        Row row = buildSummaryRow();
        assertFalse(new OutgoingBatchSummaryMapper(false, false).mapRow(row).isErrorFlag());
        row.put(OUT_BATCH_COL_ERROR_FLAG, true);
        assertTrue(new OutgoingBatchSummaryMapper(false, false).mapRow(row).isErrorFlag());
    }

    @Test
    public void testBriefStatsMapper() {
        Date batchDate = new Date();
        Row row = new Row(
                new String[] { OUT_BATCH_COL_NODE_ID, OUT_BATCH_COL_STATUS, COL_BATCH_DATE, COL_BATCHES, COL_DATA_ROWS },
                new Object[] { "node1", "OK", batchDate, 42L, 100L });
        OutgoingBatchSummaryByNodeBriefStats stats = new OutgoingBatchSummaryByNodeBriefStatsMapper().mapRow(row);
        assertEquals("node1", stats.nodeId());
        assertEquals("OK", stats.status());
        assertEquals(batchDate, stats.batchDate());
        assertEquals(42L, stats.batchCount());
        assertEquals(100L, stats.dataRows());
    }

    @Test
    public void testBacklogSummaryMapper() {
        Row row = new Row(
                new String[] { OUT_BATCH_COL_BYTE_COUNT, COL_ROWS_COUNT },
                new Object[] { 5000L, 250L });
        BacklogSummary summary = new BacklogSummaryMapper().mapRow(row);
        assertEquals(5000L, summary.getByteCount());
        assertEquals(250L, summary.getRowCount());
    }

    private Row buildSummaryRow() {
        Date updateTime = new Date();
        Date createTime = new Date();
        return new Row(
                new String[] {
                        OUT_BATCH_COL_NODE_ID, OUT_BATCH_COL_CHANNEL_ID,
                        COL_BATCHES, COL_DATA, OUT_BATCH_COL_STATUS,
                        COL_OLDEST_BATCH_TIME, OUT_BATCH_COL_LAST_UPDATE_TIME,
                        COL_TOTAL_BYTES, COL_TOTAL_MILLIS,
                        OUT_BATCH_COL_ERROR_FLAG, OUT_BATCH_COL_BATCH_ID,
                        COL_INSERT_EVENT_COUNT, COL_UPDATE_EVENT_COUNT,
                        COL_DELETE_EVENT_COUNT, COL_OTHER_EVENT_COUNT, COL_RELOAD_EVENT_COUNT,
                        COL_TOTAL_ROUTER_MILLIS, COL_TOTAL_EXTRACT_MILLIS,
                        COL_TOTAL_NETWORK_MILLIS, COL_TOTAL_LOAD_MILLIS
                },
                new Object[] {
                        "node1", "default",
                        10, 500, "ER",
                        createTime, updateTime,
                        1024L, 200L,
                        false, 42L,
                        100, 200, 50, 10, 5,
                        50L, 100L, 30L, 20L
                });
    }

    private void assertCommonSummaryFields(OutgoingBatchSummary summary) {
        assertEquals(10, summary.getBatchCount());
        assertEquals(500, summary.getDataCount());
        assertEquals("ER", summary.getStatus().name());
        assertEquals(1024L, summary.getTotalBytes());
        assertEquals(200L, summary.getTotalMillis());
        assertFalse(summary.isErrorFlag());
        assertEquals(42L, summary.getMinBatchId());
        assertEquals(100, summary.getInsertCount());
        assertEquals(200, summary.getUpdateCount());
        assertEquals(50, summary.getDeleteCount());
        assertEquals(50L, summary.getRouterMillis());
        assertEquals(100L, summary.getExtractMillis());
        assertEquals(30L, summary.getTransferMillis());
        assertEquals(20L, summary.getLoadMillis());
    }
}
