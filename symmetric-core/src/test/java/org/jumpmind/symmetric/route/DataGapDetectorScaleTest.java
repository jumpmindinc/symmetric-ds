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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Gap detection with a gap list shaped like production environments with about 1,000,000 gaps. Gap width is spread evenly over 1 to 104 (median 52; 74.5% of
 * the sampled production gaps are inside that range) with one data ID between gaps (98.6% of the sampled production gaps). Set SYM_GAP_SCALE_TEST_GAPS to
 * change the number of gaps.
 */
class DataGapDetectorScaleTest {
    private static final String GAP_COUNT_VARIABLE = "SYM_GAP_SCALE_TEST_GAPS";
    private static final int DEFAULT_GAP_COUNT = 100000;
    private static final long FIRST_GAP_START_ID = 736728367L;
    private static final int GAP_WIDTH_RANGE = 104;
    private static final int GAP_WIDTH_SPREAD_FACTOR = 37;
    private static final int DATA_RUN_BETWEEN_GAPS = 1;
    private static final long LARGEST_GAP_SIZE = 50000000L;
    private static final long QUERY_LATENCY_IN_MS = 2;
    private static final int LATENCY_GAP_COUNT = 300;
    IDataService dataService;
    IParameterService parameterService;
    IContextService contextService;
    ISqlTemplate sqlTemplate;
    DataGapFastDetector detector;
    AtomicLong queryCount;
    long queryLatencyInMs;
    int gapCount;

    @BeforeEach
    void setUp() {
        gapCount = Integer.parseInt(System.getenv().getOrDefault(GAP_COUNT_VARIABLE, String.valueOf(DEFAULT_GAP_COUNT)));
        queryCount = new AtomicLong();
        queryLatencyInMs = 0;
        sqlTemplate = mock(ISqlTemplate.class, withSettings().stubOnly());
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            queryCount.incrementAndGet();
            if (queryLatencyInMs > 0) {
                Thread.sleep(queryLatencyInMs);
            }
            return new ArrayList<Long>();
        });
        when(sqlTemplate.startSqlTransaction()).thenReturn(mock(ISqlTransaction.class, withSettings().stubOnly()));
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        parameterService = mock(IParameterService.class);
        when(parameterService.getEngineName()).thenReturn("scaletestengine");
        when(parameterService.getLong(ParameterConstants.ROUTING_STALE_DATA_ID_GAP_TIME)).thenReturn(60000000L);
        when(parameterService.getInt(ParameterConstants.DATA_ID_INCREMENT_BY)).thenReturn(1);
        when(parameterService.getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE)).thenReturn(LARGEST_GAP_SIZE);
        when(parameterService.getLong(ParameterConstants.ROUTING_STALE_GAP_BUSY_EXPIRE_TIME)).thenReturn(60000L);
        when(parameterService.is(ParameterConstants.ROUTING_DETECT_INVALID_GAPS)).thenReturn(true);
        when(parameterService.getInt(ParameterConstants.ROUTING_MAX_GAP_CHANGES)).thenReturn(1000);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(statisticManager.newProcessInfo((ProcessInfoKey) any())).thenReturn(new ProcessInfo());
        IRouterService routerService = mock(IRouterService.class);
        when(routerService.getSql(anyString())).thenReturn("select data_id");
        dataService = mock(IDataService.class);
        contextService = mock(IContextService.class);
        detector = new DataGapFastDetector(dataService, parameterService, contextService, symmetricDialect, routerService, statisticManager,
                mock(INodeService.class), mock(IClusterService.class));
    }

    @Test
    void testGeneratedGapsFollowSnapshotShape() {
        List<DataGap> gaps = newSnapshotShapedGaps(gapCount);
        long minWidth = Long.MAX_VALUE;
        long maxWidth = 0;
        long widthSum = 0;
        for (int index = 0; index < gaps.size(); index++) {
            long width = gaps.get(index).getEndId() - gaps.get(index).getStartId() + 1;
            minWidth = Math.min(minWidth, width);
            maxWidth = Math.max(maxWidth, width);
            widthSum += width;
            if (index > 0) {
                assertEquals(DATA_RUN_BETWEEN_GAPS, gaps.get(index).getStartId() - gaps.get(index - 1).getEndId() - 1);
            }
        }
        assertEquals(gapCount, gaps.size());
        assertEquals(FIRST_GAP_START_ID, gaps.get(0).getStartId());
        assertEquals(1L, minWidth);
        assertEquals((long) GAP_WIDTH_RANGE, maxWidth);
        assertEquals(52.5, (double) widthSum / gaps.size(), 0.5);
    }

    @Test
    void testFullGapAnalysisRunsOneQueryPerGap() {
        when(dataService.findDataGaps()).thenReturn(newSnapshotShapedGaps(gapCount));
        detector.setFullGapAnalysis(true);
        long startTime = System.currentTimeMillis();
        detector.beforeRouting();
        long elapsedTimeInMs = System.currentTimeMillis() - startTime;
        System.out.println(String.format("Full gap analysis of %d gaps ran %d queries in %d ms", gapCount, queryCount.get(), elapsedTimeInMs));
        assertEquals((long) gapCount, queryCount.get());
    }

    @Test
    void testQueryLatencyAddsToFullGapAnalysisTimeForEveryGap() {
        when(dataService.findDataGaps()).thenReturn(newSnapshotShapedGaps(LATENCY_GAP_COUNT));
        queryLatencyInMs = QUERY_LATENCY_IN_MS;
        detector.setFullGapAnalysis(true);
        long startTime = System.currentTimeMillis();
        detector.beforeRouting();
        long elapsedTimeInMs = System.currentTimeMillis() - startTime;
        assertEquals((long) LATENCY_GAP_COUNT, queryCount.get());
        assertTrue(elapsedTimeInMs >= LATENCY_GAP_COUNT * QUERY_LATENCY_IN_MS * 0.9);
    }

    @Test
    void testClusterLockingReadsAllGapsOnEveryCycle() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(dataService.findDataGaps()).thenReturn(newSnapshotShapedGaps(gapCount));
        detector.beforeRouting();
        detector.beforeRouting();
        verify(dataService, times(2)).findDataGaps();
        assertEquals(gapCount, detector.getDataGaps().size());
        assertEquals(0L, queryCount.get());
    }

    // Estimates composition of gaps found in production snapshot - JMC LMG ticket JMCH-2310 (about 1,000,000)
    private static List<DataGap> newSnapshotShapedGaps(int count) {
        List<DataGap> gaps = new ArrayList<DataGap>(count);
        long startId = FIRST_GAP_START_ID;
        for (int index = 0; index < count; index++) {
            long width = 1 + ((long) index * GAP_WIDTH_SPREAD_FACTOR) % GAP_WIDTH_RANGE;
            long endId = startId + width - 1;
            gaps.add(new DataGap(startId, endId));
            startId = endId + 1 + DATA_RUN_BETWEEN_GAPS;
        }
        return gaps;
    }
}
