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
package org.jumpmind.symmetric.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jumpmind.symmetric.model.AbstractBatch;
import org.jumpmind.symmetric.model.OutgoingBatchSummary;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeBatchStatusMetricsMapTest {
    private static final String BATCHES_METRIC = "sym.batches.outgoing.count";
    private static final String ROWS_METRIC = "sym.rows.outgoing.count";
    private IEngineMetricsService metricsService;
    private NodeBatchStatusMetricsMap map;

    @BeforeEach
    void setUp() {
        metricsService = mock(IEngineMetricsService.class);
        when(metricsService.registerLongGauge(anyString(), any())).thenAnswer(inv -> newGauge());
        map = new NodeBatchStatusMetricsMap(metricsService, BATCHES_METRIC, ROWS_METRIC);
    }

    private static ISymLongGauge newGauge() {
        AtomicLong value = new AtomicLong(0);
        return new ISymLongGauge() {
            @Override
            public void setValue(long v) {
                value.set(v);
            }

            @Override
            public long getValue() {
                return value.get();
            }

            @Override
            public void add(long delta) {
                value.addAndGet(delta);
            }
        };
    }

    private static OutgoingBatchSummary summary(String nodeId, AbstractBatch.Status status, int batchCount, int dataCount) {
        OutgoingBatchSummary s = new OutgoingBatchSummary();
        s.setNodeId(nodeId);
        s.setStatus(status);
        s.setBatchCount(batchCount);
        s.setDataCount(dataCount);
        return s;
    }

    @Test
    void getOrCreate_newPair_createsEntry() {
        INodeBatchStatusGauge g = map.getOrCreate("node-1", "OK");
        assertNotNull(g);
        assertEquals("node-1", g.getNodeId());
        assertEquals("OK", g.getBatchStatus());
    }

    @Test
    void getOrCreate_samePairTwice_returnsSameEntry() {
        INodeBatchStatusGauge first = map.getOrCreate("node-1", "OK");
        INodeBatchStatusGauge second = map.getOrCreate("node-1", "OK");
        assertTrue(first == second);
    }

    @Test
    void getOrCreate_differentStatuses_createsDifferentEntries() {
        INodeBatchStatusGauge ok = map.getOrCreate("node-1", "OK");
        INodeBatchStatusGauge err = map.getOrCreate("node-1", "ER");
        assertFalse(ok == err);
        assertEquals(2, map.size());
    }

    @Test
    void getBatchCount_absentKey_returnsZero() {
        assertEquals(0L, map.getBatchCount("ghost", "OK"));
    }

    @Test
    void getRowCount_absentKey_returnsZero() {
        assertEquals(0L, map.getRowCount("ghost", "OK"));
    }

    @Test
    void getBatchCount_afterSet_returnsValue() {
        map.setBatchCount("n1", "OK", 5L);
        assertEquals(5L, map.getBatchCount("n1", "OK"));
    }

    @Test
    void getRowCount_afterSet_returnsValue() {
        map.setRowCount("n1", "OK", 100L);
        assertEquals(100L, map.getRowCount("n1", "OK"));
    }

    @Test
    void setBatchAndRowCounts_setsGaugesToSuppliedValues() {
        map.setBatchAndRowCounts("n2", "ER", 3L, 99L);
        assertEquals(3L, map.getBatchCount("n2", "ER"));
        assertEquals(99L, map.getRowCount("n2", "ER"));
    }

    @Test
    void setBatchAndRowCounts_createsEntryIfAbsent() {
        assertFalse(map.contains("n3" + NodeBatchStatusMetricsMap.ENTRY_KEY_DELIMITER + "NE"));
        map.setBatchAndRowCounts("n3", "NE", 1L, 10L);
        assertTrue(map.contains("n3" + NodeBatchStatusMetricsMap.ENTRY_KEY_DELIMITER + "NE"));
    }

    @Test
    void setBatchCount_overwritesPreviousValue() {
        map.setBatchCount("n4", "OK", 10L);
        map.setBatchCount("n4", "OK", 20L);
        assertEquals(20L, map.getBatchCount("n4", "OK"));
    }

    @Test
    void setRowCount_overwritesPreviousValue() {
        map.setRowCount("n4", "OK", 50L);
        map.setRowCount("n4", "OK", 75L);
        assertEquals(75L, map.getRowCount("n4", "OK"));
    }

    @Test
    void addBatchCount_accumulatesDeltas() {
        map.addBatchCount("n5", "OK", 3L);
        map.addBatchCount("n5", "OK", 2L);
        assertEquals(5L, map.getBatchCount("n5", "OK"));
    }

    @Test
    void addRowCount_accumulatesDeltas() {
        map.addRowCount("n5", "OK", 100L);
        map.addRowCount("n5", "OK", 50L);
        assertEquals(150L, map.getRowCount("n5", "OK"));
    }

    @Test
    void setAllMetrics_updatesPresentSummaries() {
        List<OutgoingBatchSummary> summaries = List.of(
                summary("n6", AbstractBatch.Status.OK, 4, 200));
        map.setAllMetrics(summaries);
        assertEquals(4L, map.getBatchCount("n6", "OK"));
        assertEquals(200L, map.getRowCount("n6", "OK"));
    }

    @Test
    void setAllMetrics_zerosOutStaleEntries() {
        map.setBatchAndRowCounts("n7", "OK", 10L, 500L);
        map.setAllMetrics(List.of());
        assertEquals(0L, map.getBatchCount("n7", "OK"));
        assertEquals(0L, map.getRowCount("n7", "OK"));
    }

    @Test
    void setAllMetrics_multipleNodes_allUpdated() {
        List<OutgoingBatchSummary> summaries = List.of(
                summary("n8", AbstractBatch.Status.OK, 1, 10),
                summary("n9", AbstractBatch.Status.ER, 2, 20));
        map.setAllMetrics(summaries);
        assertEquals(1L, map.getBatchCount("n8", "OK"));
        assertEquals(2L, map.getBatchCount("n9", "ER"));
    }

    @Test
    void setSpecifiedMetrics_updatesOnlySpecifiedSummaries() {
        map.setBatchAndRowCounts("n10", "OK", 5L, 50L);
        map.setBatchAndRowCounts("n11", "ER", 3L, 30L);
        map.setSpecifiedMetrics(List.of(summary("n10", AbstractBatch.Status.OK, 9, 90)));
        assertEquals(9L, map.getBatchCount("n10", "OK"));
        assertEquals(9L, map.getBatchCount("n10", "OK"));
        // n11 untouched
        assertEquals(3L, map.getBatchCount("n11", "ER"));
    }

    @Test
    void gaugesForNode_returnsOnlyMatchingNodeEntries() {
        map.getOrCreate("n12", "OK");
        map.getOrCreate("n12", "ER");
        map.getOrCreate("n13", "OK");
        List<NodeBatchStatusGauge> result = map.gaugesForNode("n12");
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(g -> "n12".equals(g.getNodeId())));
    }

    @Test
    void gaugesForNode_noMatchingNode_returnsEmptyList() {
        map.getOrCreate("n14", "OK");
        List<NodeBatchStatusGauge> result = map.gaugesForNode("n99");
        assertTrue(result.isEmpty());
    }

    @Test
    void compositeKey_usesDelimiter() {
        map.getOrCreate("n15", "OK");
        assertTrue(map.keys().contains("n15" + NodeBatchStatusMetricsMap.ENTRY_KEY_DELIMITER + "OK"));
    }
}
