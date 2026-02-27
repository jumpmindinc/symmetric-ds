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
package org.jumpmind.symmetric.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.impl.ClusterService;
import org.jumpmind.symmetric.service.impl.ContextService;
import org.jumpmind.symmetric.service.impl.DataService;
import org.jumpmind.symmetric.service.impl.ExtensionService;
import org.jumpmind.symmetric.service.impl.NodeService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.jumpmind.symmetric.service.impl.RouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.statistic.StatisticManager;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class DataGapDetectorTest {
    final static String ENGINE_NAME = "testengine";
    final static String CHANNEL_ID = "testchannel";
    final static String NODE_ID = "00000";
    final static String NODE_GROUP_ID = "testgroup";
    ISqlTemplate sqlTemplate;
    ISqlTransaction sqlTransaction;
    IDataService dataService;
    IParameterService parameterService;
    IContextService contextService;
    ISymmetricDialect symmetricDialect;
    IRouterService routerService;
    IStatisticManager statisticManager;
    INodeService nodeService;
    IClusterService clusterService;
    DataGapFastDetector detector;
    ThreadLocalRandom rand = ThreadLocalRandom.current();

    @BeforeEach
    public void setUp() throws Exception {
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTransaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        symmetricDialect = mock(AbstractSymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.supportsTransactionViews()).thenReturn(false);
        when(symmetricDialect.getDatabaseTime()).thenReturn(0L);
        parameterService = mock(ParameterService.class);
        when(parameterService.getEngineName()).thenReturn(ENGINE_NAME);
        when(parameterService.getLong(ParameterConstants.ROUTING_STALE_DATA_ID_GAP_TIME)).thenReturn(60000000L);
        when(parameterService.getInt(ParameterConstants.DATA_ID_INCREMENT_BY)).thenReturn(1);
        when(parameterService.getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE)).thenReturn(50000000L);
        when(parameterService.getLong(ParameterConstants.ROUTING_GAPS_TRANSACTION_VIEW_CLOCK_SYNC_THRESHOLD_MS))
                .thenReturn(60000L);
        when(parameterService.getLong(ParameterConstants.ROUTING_STALE_GAP_BUSY_EXPIRE_TIME)).thenReturn(60000L);
        when(parameterService.is(ParameterConstants.ROUTING_DETECT_INVALID_GAPS)).thenReturn(true);
        when(parameterService.getInt(ParameterConstants.ROUTING_MAX_GAP_CHANGES)).thenReturn(1000);
        IExtensionService extensionService = mock(ExtensionService.class);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        clusterService = mock(ClusterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getClusterService()).thenReturn(clusterService);
        routerService = new RouterService(engine);
        when(engine.getRouterService()).thenReturn(routerService);
        contextService = mock(ContextService.class);
        dataService = mock(DataService.class);
        statisticManager = mock(StatisticManager.class);
        when(statisticManager.newProcessInfo((ProcessInfoKey) any())).thenReturn(new ProcessInfo());
        nodeService = mock(NodeService.class);
        when(nodeService.findIdentity()).thenReturn(new Node(NODE_ID, NODE_GROUP_ID));
        clusterService = mock(ClusterService.class);
        detector = newGapDetector();
        detector.setFullGapAnalysis(false);
    }

    protected DataGapFastDetector newGapDetector() {
        return new DataGapFastDetector(dataService, parameterService, contextService, symmetricDialect, routerService,
                statisticManager, nodeService, clusterService);
    }

    protected void runGapDetector(List<DataGap> dataGaps, List<Long> dataIds, boolean isAllDataRead) {
        when(dataService.findDataGaps()).thenReturn(dataGaps);
        detector.beforeRouting();
        detector.addDataIds(dataIds);
        detector.setIsAllDataRead(isAllDataRead);
        detector.afterRouting();
    }

    protected void runGapDetector(final List<DataGap> dataGaps1, final List<DataGap> dataGaps2, List<Long> dataIds,
            boolean isAllDataRead) {
        when(dataService.findDataGaps()).thenAnswer(new Answer<List<DataGap>>() {
            int i;

            public List<DataGap> answer(InvocationOnMock invocation) {
                return i++ == 0 ? dataGaps1 : dataGaps2;
            }
        });
        detector.beforeRouting();
        detector.addDataIds(dataIds);
        detector.setIsAllDataRead(isAllDataRead);
        detector.afterRouting();
    }

    @Test
    public void testNewGap() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(4, 50000004));
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(100L);
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(4, 50000004));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(4, 99));
        inserted.add(new DataGap(101, 50000100));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testNewGapFull() throws Exception {
        detector.setFullGapAnalysis(true);
        when(contextService.is(ContextConstants.ROUTING_FULL_GAP_ANALYSIS)).thenReturn(true);
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(100L);
        String sql = ArgumentMatchers.anyString();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<Long> mapper = (ISqlRowMapper<Long>) ArgumentMatchers.any();
        when(sqlTemplate.query(sql, mapper, ArgumentMatchers.eq(4L), ArgumentMatchers.eq(50000004L)))
                .thenReturn(dataIds);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(4, 50000004));
        runGapDetector(dataGaps, new ArrayList<Long>(), true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(4, 50000004));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(4, 99));
        inserted.add(new DataGap(101, 50000100));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testTwoNewGaps() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(4, 50000004));
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(5L);
        dataIds.add(8L);
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(4, 50000004));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(4, 4));
        inserted.add(new DataGap(6, 7));
        inserted.add(new DataGap(9, 50000008));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testTwoNewGapsFull() throws Exception {
        detector.setFullGapAnalysis(true);
        when(contextService.is(ContextConstants.ROUTING_FULL_GAP_ANALYSIS)).thenReturn(true);
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(5L);
        dataIds.add(8L);
        String sql = ArgumentMatchers.anyString();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<Long> mapper = (ISqlRowMapper<Long>) ArgumentMatchers.any();
        when(sqlTemplate.query(sql, mapper, ArgumentMatchers.eq(4L), ArgumentMatchers.eq(50000004L)))
                .thenReturn(dataIds);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(4, 50000004));
        runGapDetector(dataGaps, new ArrayList<Long>(), true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(4, 50000004));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(4, 4));
        inserted.add(new DataGap(6, 7));
        inserted.add(new DataGap(9, 50000008));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapInGap() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 10));
        dataGaps.add(new DataGap(15, 20));
        dataGaps.add(new DataGap(21, 50000020));
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(6L);
        dataIds.add(18L);
        dataIds.add(23L);
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(5, 10));
        deleted.add(new DataGap(15, 20));
        deleted.add(new DataGap(21, 50000020));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(5, 5));
        inserted.add(new DataGap(7, 10));
        inserted.add(new DataGap(15, 17));
        inserted.add(new DataGap(19, 20));
        inserted.add(new DataGap(21, 22));
        inserted.add(new DataGap(24, 50000023));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGapInGapFull() throws Exception {
        detector.setFullGapAnalysis(true);
        when(contextService.is(ContextConstants.ROUTING_FULL_GAP_ANALYSIS)).thenReturn(true);
        when(sqlTemplate.query(ArgumentMatchers.any(), ArgumentMatchers.isA(ISqlRowMapper.class), ArgumentMatchers.any(Object[].class))).thenAnswer(
                new Answer<List<Long>>() {
                    public List<Long> answer(InvocationOnMock invocation) {
                        List<Long> dataIds = new ArrayList<Long>();
                        long startId = (Long) invocation.getArguments()[2];
                        long endId = (Long) invocation.getArguments()[3];
                        if (startId == 5 && endId == 10) {
                            dataIds.add(6L);
                        } else if (startId == 15 && endId == 20) {
                            dataIds.add(18L);
                        } else if (startId == 21 && endId == 50000020) {
                            dataIds.add(23L);
                        }
                        return dataIds;
                    }
                });
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 10));
        dataGaps.add(new DataGap(15, 20));
        dataGaps.add(new DataGap(21, 50000020));
        runGapDetector(dataGaps, new ArrayList<Long>(), true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(5, 10));
        deleted.add(new DataGap(15, 20));
        deleted.add(new DataGap(21, 50000020));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(5, 5));
        inserted.add(new DataGap(7, 10));
        inserted.add(new DataGap(15, 17));
        inserted.add(new DataGap(19, 20));
        inserted.add(new DataGap(21, 22));
        inserted.add(new DataGap(24, 50000023));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapExpire() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        when(symmetricDialect.supportsTransactionViews()).thenReturn(true);
        when(symmetricDialect.getDatabaseTime()).thenReturn(System.currentTimeMillis() + 60001L);
        // sets an isExpired flag to true and no longer deletes the gaps that are
        // expired
        runGapDetector(dataGaps, new ArrayList<Long>(), true);
        Set<DataGap> expiredGaps = new HashSet<DataGap>();
        expiredGaps.add(new DataGap(3, 3));
        expiredGaps.add(new DataGap(5, 6));
        Set<DataGap> inserted = new HashSet<DataGap>();
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, new HashSet<DataGap>());
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, expiredGaps);
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapExpireBusyChannel() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        when(symmetricDialect.supportsTransactionViews()).thenReturn(true);
        when(symmetricDialect.getDatabaseTime()).thenReturn(System.currentTimeMillis() + 60001L);
        when(dataService.countDataInRange(4, 7)).thenReturn(1);
        detector.setLastBusyExpireRunTime(System.currentTimeMillis() - 61000);
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Set<DataGap> expiredGaps = new HashSet<DataGap>();
        expiredGaps.add(new DataGap(3, 3));
        Set<DataGap> inserted = new HashSet<DataGap>();
        verify(dataService).findDataGaps();
        verify(dataService).countDataInRange(2, 4);
        verify(dataService).countDataInRange(4, 7);
        verify(dataService).deleteDataGaps(sqlTransaction, new HashSet<DataGap>());
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, expiredGaps);
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapBusyExpireRun() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        when(symmetricDialect.supportsTransactionViews()).thenReturn(true);
        when(symmetricDialect.getDatabaseTime()).thenReturn(System.currentTimeMillis() + 60001L);
        when(parameterService.getLong(ParameterConstants.ROUTING_STALE_GAP_BUSY_EXPIRE_TIME)).thenReturn(61000L);
        detector.setLastBusyExpireRunTime(System.currentTimeMillis() - 61000);
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Set<DataGap> expiredGaps = new HashSet<DataGap>();
        expiredGaps.add(new DataGap(3, 3));
        expiredGaps.add(new DataGap(5, 6));
        Set<DataGap> inserted = new HashSet<DataGap>();
        verify(dataService).findDataGaps();
        verify(dataService).countDataInRange(2, 4);
        verify(dataService).countDataInRange(4, 7);
        verify(dataService).deleteDataGaps(sqlTransaction, new HashSet<DataGap>());
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, expiredGaps);
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapBusyExpireRunMultiple() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        when(symmetricDialect.supportsTransactionViews()).thenReturn(true);
        when(symmetricDialect.getDatabaseTime()).thenReturn(System.currentTimeMillis() + 60001L);
        when(parameterService.getLong(ParameterConstants.ROUTING_STALE_GAP_BUSY_EXPIRE_TIME)).thenReturn(61000L);
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Assert.assertTrue(detector.getLastBusyExpireRunTime() != 0);
        verify(dataService).findDataGaps();
        verifyNoMoreInteractions(dataService);
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Assert.assertTrue(detector.getLastBusyExpireRunTime() != 0);
        verifyNoMoreInteractions(dataService);
        runGapDetector(dataGaps, new ArrayList<Long>(), true);
        Assert.assertEquals(detector.getLastBusyExpireRunTime(), 0);
        verifyNoMoreInteractions(dataService);
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Assert.assertTrue(detector.getLastBusyExpireRunTime() != 0);
        verifyNoMoreInteractions(dataService);
        detector.setLastBusyExpireRunTime(System.currentTimeMillis() - 61000);
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Set<DataGap> expiredGaps = new HashSet<DataGap>();
        expiredGaps.add(new DataGap(3, 3));
        expiredGaps.add(new DataGap(5, 6));
        verify(dataService).deleteDataGaps(sqlTransaction, new HashSet<DataGap>());
        Set<DataGap> inserted = new HashSet<DataGap>();
        verify(dataService).countDataInRange(2, 4);
        verify(dataService).countDataInRange(4, 7);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, expiredGaps);
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapBusyExpireNoRun() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        when(symmetricDialect.getDatabaseTime()).thenReturn(System.currentTimeMillis() + 60001L);
        when(contextService.getLong(ContextConstants.ROUTING_LAST_BUSY_EXPIRE_RUN_TIME))
                .thenReturn(System.currentTimeMillis());
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        verify(dataService).findDataGaps();
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapExpireOracle() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        when(symmetricDialect.supportsTransactionViews()).thenReturn(true);
        when(symmetricDialect.getEarliestTransactionStartTime())
                .thenReturn(new Date(System.currentTimeMillis() + 60001L));
        runGapDetector(dataGaps, new ArrayList<Long>(), true);
        Set<DataGap> expiredGaps = new HashSet<DataGap>();
        expiredGaps.add(new DataGap(3, 3));
        expiredGaps.add(new DataGap(5, 6));
        Set<DataGap> inserted = new HashSet<DataGap>();
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, new HashSet<DataGap>());
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, expiredGaps);
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapExpireOracleBusyChannel() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(5, 6));
        dataGaps.add(new DataGap(7, 50000006));
        when(symmetricDialect.supportsTransactionViews()).thenReturn(true);
        when(symmetricDialect.getEarliestTransactionStartTime())
                .thenReturn(new Date(System.currentTimeMillis() + 60001L));
        when(dataService.countDataInRange(4, 7)).thenReturn(1);
        detector.setLastBusyExpireRunTime(System.currentTimeMillis() - 61000);
        runGapDetector(dataGaps, new ArrayList<Long>(), false);
        Set<DataGap> expiredGaps = new HashSet<DataGap>();
        expiredGaps.add(new DataGap(3, 3));
        Set<DataGap> inserted = new HashSet<DataGap>();
        verify(dataService).findDataGaps();
        verify(dataService).countDataInRange(2, 4);
        verify(dataService).countDataInRange(4, 7);
        verify(dataService).deleteDataGaps(sqlTransaction, new HashSet<DataGap>());
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, expiredGaps);
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsBeforeAndAfterFull() throws Exception {
        detector.setFullGapAnalysis(true);
        when(contextService.is(ContextConstants.ROUTING_FULL_GAP_ANALYSIS)).thenReturn(true);
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(843L);
        dataIds.add(844L);
        String sql = ArgumentMatchers.anyString();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<Long> mapper = (ISqlRowMapper<Long>) ArgumentMatchers.any();
        when(sqlTemplate.query(sql, mapper, ArgumentMatchers.eq(841L), ArgumentMatchers.eq(50000840L)))
                .thenReturn(dataIds);
        List<DataGap> dataGaps1 = new ArrayList<DataGap>();
        dataGaps1.add(new DataGap(841, 50000840));
        List<DataGap> dataGaps2 = new ArrayList<DataGap>();
        dataGaps2.add(new DataGap(841, 842));
        dataGaps2.add(new DataGap(845, 50000844));
        dataIds = new ArrayList<Long>();
        dataIds.add(845L);
        runGapDetector(dataGaps1, dataGaps2, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(841, 50000840));
        Set<DataGap> deleted2 = new HashSet<DataGap>();
        deleted2.add(new DataGap(845, 50000844));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(841, 842));
        inserted.add(new DataGap(845, 50000844));
        Set<DataGap> inserted2 = new HashSet<DataGap>();
        inserted2.add(new DataGap(846, 50000845));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).deleteDataGaps(sqlTransaction, deleted2);
        verify(dataService).insertDataGaps(sqlTransaction, inserted2);
        verify(dataService, times(2)).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsOverlap() throws Exception {
        List<Long> dataIds = new ArrayList<Long>();
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(30953883, 80953883));
        dataGaps.add(new DataGap(30953884, 80953883));
        runGapDetector(dataGaps, dataIds, true);
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953883, 80953883));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953884, 80953883));
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(30953883, 80953883));
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsOverlapMultiple() throws Exception {
        List<Long> dataIds = new ArrayList<Long>();
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 10));
        dataGaps.add(new DataGap(3, 8));
        dataGaps.add(new DataGap(4, 6));
        dataGaps.add(new DataGap(4, 8));
        dataGaps.add(new DataGap(4, 5));
        dataGaps.add(new DataGap(5, 10));
        dataGaps.add(new DataGap(6, 11));
        runGapDetector(dataGaps, dataIds, true);
        verify(dataService).findDataGaps();
        verify(dataService, VerificationModeFactory.times(6)).deleteDataGap(sqlTransaction, new DataGap(1, 10));
        verify(dataService, VerificationModeFactory.times(5)).insertDataGap(sqlTransaction, new DataGap(1, 10));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(3, 8));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(4, 6));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(4, 8));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(4, 5));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(5, 10));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(6, 11));
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(1, 11));
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsOverlapThenData() throws Exception {
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(30953883L);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(30953883, 80953883));
        dataGaps.add(new DataGap(30953884, 80953883));
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(30953883, 80953883));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(30953884, 80953883));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953883, 80953883));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953884, 80953883));
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(30953883, 80953883));
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsOverlapThenDataFull() throws Exception {
        detector.setFullGapAnalysis(true);
        when(contextService.is(ContextConstants.ROUTING_FULL_GAP_ANALYSIS)).thenReturn(true);
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(30953883L);
        String sql = ArgumentMatchers.anyString();
        @SuppressWarnings("unchecked")
        ISqlRowMapper<Long> mapper = (ISqlRowMapper<Long>) ArgumentMatchers.any();
        when(sqlTemplate.query(sql, mapper, ArgumentMatchers.eq(30953883L), ArgumentMatchers.eq(80953883L)))
                .thenReturn(dataIds);
        List<DataGap> dataGaps1 = new ArrayList<DataGap>();
        dataGaps1.add(new DataGap(30953883, 80953883));
        dataGaps1.add(new DataGap(30953884, 80953883));
        List<DataGap> dataGaps2 = new ArrayList<DataGap>();
        dataGaps2.add(new DataGap(30953884, 80953883));
        dataIds = new ArrayList<Long>();
        dataIds.add(30953883L);
        runGapDetector(dataGaps1, dataGaps2, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(30953883, 80953883));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(30953884, 80953883));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953883, 80953883));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953884, 80953883));
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(30953883, 80953883));
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsOverlapAfterLastGap() throws Exception {
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(30953883L);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(30953883, 80953883));
        dataGaps.add(new DataGap(30953884, 80953883));
        dataGaps.add(new DataGap(30953885, 81953883));
        dataGaps.add(new DataGap(30953885, 30953885));
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(30953883, 80953883));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(30953884, 80953883));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953884, 80953883));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953885, 81953883));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(30953885, 30953885));
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsDuplicateDetection() throws Exception {
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(31832439L);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(31832006, 31832438));
        dataGaps.add(new DataGap(31832439, 81832439));
        dataGaps.add(new DataGap(31832440, 81832439));
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(31832439, 81832439));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(31832440, 81832439));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(31832439, 81832439));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(31832440, 81832439));
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(31832439, 81832439));
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsOverlapDetection() throws Exception {
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(31837983L);
        dataIds.add(31837989L);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(31837983, 81837982));
        dataGaps.add(new DataGap(31837983, 81837983));
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(31837983, 81837983));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(31837984, 31837988));
        inserted.add(new DataGap(31837990, 81837989));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(31837983, 81837982));
        verify(dataService).deleteDataGap(sqlTransaction, new DataGap(31837983, 81837983));
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(31837983, 81837983));
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testGapsFailedToDelete() throws Exception {
        when(parameterService.getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE)).thenReturn(10L);
        when(parameterService.getInt(ParameterConstants.ROUTING_MAX_GAP_CHANGES)).thenReturn(2);
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(4L);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 1));
        dataGaps.add(new DataGap(2, 2));
        dataGaps.add(new DataGap(3, 13));
        runGapDetector(dataGaps, dataIds, false);
        verify(dataService).findDataGaps();
        verify(dataService).deleteAllDataGaps(sqlTransaction);
        verify(dataService).insertDataGap(sqlTransaction, new DataGap(1, 14));
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
        Mockito.reset(dataService);
        dataIds = new ArrayList<Long>();
        dataIds.add(3L);
        dataGaps = detector.getDataGaps();
        runGapDetector(dataGaps, dataIds, false);
        List<DataGap> inserted = new ArrayList<DataGap>();
        inserted.add(new DataGap(1, 1));
        inserted.add(new DataGap(2, 2));
        inserted.add(new DataGap(5, 14));
        verify(dataService).deleteAllDataGaps(sqlTransaction);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }

    @Test
    public void testDataBeforeGap() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(3, 3));
        dataGaps.add(new DataGap(4, 50000004));
        List<Long> dataIds = new ArrayList<Long>();
        dataIds.add(1L);
        dataIds.add(2L);
        dataIds.add(3L);
        dataIds.add(4L);
        runGapDetector(dataGaps, dataIds, true);
        Set<DataGap> deleted = new HashSet<DataGap>();
        deleted.add(new DataGap(3, 3));
        deleted.add(new DataGap(4, 50000004));
        Set<DataGap> inserted = new HashSet<DataGap>();
        inserted.add(new DataGap(5, 50000004));
        verify(dataService).findDataGaps();
        verify(dataService).deleteDataGaps(sqlTransaction, deleted);
        verify(dataService).insertDataGaps(sqlTransaction, inserted);
        verify(dataService).expireDataGaps(sqlTransaction, new HashSet<DataGap>());
        verifyNoMoreInteractions(dataService);
    }
}
