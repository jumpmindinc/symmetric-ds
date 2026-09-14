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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.impl.DataService;
import org.jumpmind.symmetric.service.impl.ExtensionService;
import org.jumpmind.symmetric.service.impl.NodeService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.jumpmind.symmetric.service.impl.RouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.statistic.StatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class DataGapRouteCursorTest {
    protected IParameterService parameterService;
    protected ISqlTemplate sqlTemplate;
    protected NodeChannel nodeChannel;

    @BeforeEach
    public void setUp() throws Exception {
        sqlTemplate = mock(ISqlTemplate.class);
        ISqlRowMapper<Data> mapper = any();
        when(sqlTemplate.queryForCursor((String) any(), mapper, (Object[]) any(), (int[]) any())).thenReturn(new ListReadCursor());
        parameterService = mock(ParameterService.class);
        when(parameterService.getInt(eq(ParameterConstants.ROUTING_MAX_GAPS_TO_QUALIFY_IN_SQL), anyInt())).thenReturn(3);
        when(parameterService.getInt(eq(ParameterConstants.ROUTING_DATA_READER_THRESHOLD_GAPS_TO_USE_GREATER_QUERY), anyInt())).thenReturn(3);
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)).thenReturn(true);
        nodeChannel = new NodeChannel(Constants.CHANNEL_DEFAULT);
        nodeChannel.setMaxDataToRoute(1000);
        nodeChannel.setBatchAlgorithm(DefaultBatchAlgorithm.NAME);
    }

    protected IDataGapRouteCursor buildCursor(List<DataGap> dataGaps, boolean useMultipleQueries) throws Exception {
        when(parameterService.getEngineName()).thenReturn("myEngine");
        IStatisticManager statisticManager = mock(StatisticManager.class);
        when(statisticManager.newProcessInfo((ProcessInfoKey) any())).thenReturn(new ProcessInfo());
        INodeService nodeService = mock(NodeService.class);
        IDataService dataService = mock(DataService.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.scrubSql(any())).thenAnswer(i -> i.getArguments()[0]);
        ISymmetricDialect symmetricDialect = mock(AbstractSymmetricDialect.class);
        when(symmetricDialect.massageDataExtractionSql(any(), eq(false))).thenAnswer(i -> i.getArguments()[0]);
        when(symmetricDialect.supportsTransactionId()).thenReturn(true);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlTypeForIds()).thenReturn(Types.BIGINT);
        IExtensionService extensionService = mock(ExtensionService.class);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        IRouterService routerService = new RouterService(engine);
        when(engine.getRouterService()).thenReturn(routerService);
        ChannelRouterContext context = new ChannelRouterContext("000", nodeChannel, mock(ISqlTransaction.class), null);
        context.setDataGaps(dataGaps);
        if (useMultipleQueries) {
            return new DataGapRouteMultiCursor(context, engine);
        }
        return new DataGapRouteCursor(context, engine);
    }

    @Test
    public void testOrderBy() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(0, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, false);
        assertTrue("Should query each gap", cursor.isEachGapQueried());
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 0l, Long.MAX_VALUE };
        int[] types = new int[] { Types.VARCHAR, Types.BIGINT, Types.BIGINT };
        verify(sqlTemplate).queryForCursor(contains("order by d.data_id"), any(), eq(args), eq(types));
    }

    @Test
    public void testOrderByNatural() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)).thenReturn(false);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(0, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, false);
        assertTrue(cursor.isEachGapQueried());
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 0l, Long.MAX_VALUE };
        int[] types = new int[] { Types.VARCHAR, Types.BIGINT, Types.BIGINT };
        verify(sqlTemplate).queryForCursor(not(contains("order by")), any(), eq(args), eq(types));
    }

    @Test
    public void testMemoryOrderById() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_INTO_MEMORY_ENABLED, false)).thenReturn(true);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(0, Long.MAX_VALUE));
        List<Data> datas = new ArrayList<Data>();
        datas.add(new Data(3, null, null, null, null, null, null, null, null, null));
        datas.add(new Data(1, null, null, null, null, null, null, null, null, null));
        datas.add(new Data(2, null, null, null, null, null, null, null, null, null));
        ISqlRowMapper<Data> mapper = any();
        when(sqlTemplate.queryForCursor((String) any(), mapper, (Object[]) any(), (int[]) any())).thenReturn(new ListReadCursor(datas));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, false);
        for (int i = 1; i < 4; i++) {
            Data data = cursor.next();
            assertTrue(data != null);
            assertTrue(data.getDataId() == i);
        }
        assertTrue(cursor.isEachGapQueried());
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 0l, Long.MAX_VALUE };
        int[] types = new int[] { Types.VARCHAR, Types.BIGINT, Types.BIGINT };
        verify(sqlTemplate).queryForCursor(not(contains("order by d.data_id")), any(), eq(args), eq(types));
    }

    @Test
    public void testChannelFlags() throws Exception {
        nodeChannel.setUsePkDataToRoute(false);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(0, Long.MAX_VALUE));
        buildCursor(dataGaps, false);
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 0l, Long.MAX_VALUE };
        int[] types = new int[] { Types.VARCHAR, Types.BIGINT, Types.BIGINT };
        verify(sqlTemplate).queryForCursor(not(contains("d.pk_data")), any(), eq(args), eq(types));
        buildCursor(dataGaps, false);
        nodeChannel.setUseRowDataToRoute(false);
        buildCursor(dataGaps, false);
        verify(sqlTemplate).queryForCursor(not(contains("d.row_data")), any(), eq(args), eq(types));
        nodeChannel.setUseOldDataToRoute(false);
        buildCursor(dataGaps, false);
        verify(sqlTemplate).queryForCursor(not(contains("d.old_data")), any(), eq(args), eq(types));
    }

    @Test
    public void testExceptionAndRetry() throws Exception {
        ISqlRowMapper<Data> mapper = any();
        when(sqlTemplate.queryForCursor((String) any(), mapper, (Object[]) any(), (int[]) any())).then(new Answer<ISqlReadCursor<Data>>() {
            int count = 0;

            @Override
            public ISqlReadCursor<Data> answer(InvocationOnMock invocation) throws Throwable {
                if (count++ == 0) {
                    throw new RuntimeException();
                }
                return new ListReadCursor();
            }
        });
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(0, Long.MAX_VALUE));
        buildCursor(dataGaps, false);
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 0l, Long.MAX_VALUE };
        int[] types = new int[] { Types.VARCHAR, Types.BIGINT, Types.BIGINT };
        verify(sqlTemplate, times(2)).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(types));
    }

    @Test
    public void testQueryGreaterThan() throws Exception {
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 3));
        dataGaps.add(new DataGap(5, 5));
        dataGaps.add(new DataGap(7, 10));
        dataGaps.add(new DataGap(12, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, false);
        assertFalse(cursor.isEachGapQueried());
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 1l };
        int[] types = getTypes(args.length);
        verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(types));
    }

    @Test
    public void testQueryMergeFinalGaps() throws Exception {
        when(parameterService.getInt(eq(ParameterConstants.ROUTING_DATA_READER_THRESHOLD_GAPS_TO_USE_GREATER_QUERY), anyInt())).thenReturn(0);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 3));
        dataGaps.add(new DataGap(5, 5));
        dataGaps.add(new DataGap(7, 10));
        dataGaps.add(new DataGap(12, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, false);
        assertFalse(cursor.isEachGapQueried());
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 1l, 3l, 5l, 5l, 7l, Long.MAX_VALUE };
        int[] types = getTypes(args.length);
        verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(types));
    }

    @Test
    public void testQueryMultipleOneGapOver() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_USE_MULTIPLE_QUERIES)).thenReturn(true);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 3));
        dataGaps.add(new DataGap(5, 5));
        dataGaps.add(new DataGap(7, 10));
        dataGaps.add(new DataGap(12, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, true);
        assertTrue(cursor.isEachGapQueried());
        while (cursor.next() != null) {
        }
        InOrder inOrder = Mockito.inOrder(sqlTemplate);
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 1l, 3l, 5l, 5l, 7l, 10l };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        args = new Object[] { Constants.CHANNEL_DEFAULT, 12l, Long.MAX_VALUE };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        verifyNoMoreInteractions(sqlTemplate);
    }

    @Test
    public void testQueryMultipleThreeGapsOver() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_USE_MULTIPLE_QUERIES)).thenReturn(true);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 3));
        dataGaps.add(new DataGap(5, 5));
        dataGaps.add(new DataGap(7, 10));
        dataGaps.add(new DataGap(12, 12));
        dataGaps.add(new DataGap(14, 30));
        dataGaps.add(new DataGap(40, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, true);
        assertTrue(cursor.isEachGapQueried());
        while (cursor.next() != null) {
        }
        InOrder inOrder = Mockito.inOrder(sqlTemplate);
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 1l, 3l, 5l, 5l, 7l, 10l };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        args = new Object[] { Constants.CHANNEL_DEFAULT, 12l, 12l, 14l, 30l, 40l, Long.MAX_VALUE };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        verifyNoMoreInteractions(sqlTemplate);
    }

    @Test
    public void testQueryMultipleThreeQueries() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_DATA_READER_USE_MULTIPLE_QUERIES)).thenReturn(true);
        List<DataGap> dataGaps = new ArrayList<DataGap>();
        dataGaps.add(new DataGap(1, 3));
        dataGaps.add(new DataGap(5, 5));
        dataGaps.add(new DataGap(7, 10));
        dataGaps.add(new DataGap(12, 12));
        dataGaps.add(new DataGap(14, 30));
        dataGaps.add(new DataGap(35, 37));
        dataGaps.add(new DataGap(40, Long.MAX_VALUE));
        IDataGapRouteCursor cursor = buildCursor(dataGaps, true);
        assertTrue(cursor.isEachGapQueried());
        while (cursor.next() != null) {
        }
        InOrder inOrder = Mockito.inOrder(sqlTemplate);
        Object[] args = new Object[] { Constants.CHANNEL_DEFAULT, 1l, 3l, 5l, 5l, 7l, 10l };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        args = new Object[] { Constants.CHANNEL_DEFAULT, 12l, 12l, 14l, 30l, 35l, 37l };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        args = new Object[] { Constants.CHANNEL_DEFAULT, 40l, Long.MAX_VALUE };
        inOrder.verify(sqlTemplate).queryForCursor(argThat(new SqlArgMatcher(args.length)), any(), eq(args), eq(getTypes(args.length)));
        verifyNoMoreInteractions(sqlTemplate);
    }

    protected int[] getTypes(int size) {
        int[] types = new int[size];
        types[0] = Types.VARCHAR;
        for (int i = 1; i < types.length; i++) {
            types[i] = Types.BIGINT;
        }
        return types;
    }

    static class SqlArgMatcher implements ArgumentMatcher<String> {
        int argCount;

        SqlArgMatcher(int argCount) {
            this.argCount = argCount;
        }

        @Override
        public boolean matches(String str) {
            return str.chars().filter(ch -> ch == '?').count() == argCount;
        }
    }
}
