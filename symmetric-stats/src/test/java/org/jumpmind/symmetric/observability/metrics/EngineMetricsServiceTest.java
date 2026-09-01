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
package org.jumpmind.symmetric.observability.metrics;

import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_OUTGOING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricContext;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.models.MetricContext;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.observability.repository.MetricsRepository;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;

class EngineMetricsServiceTest {
    private static final long MOCK_CONTEXT_ID = 42L;
    private static final long REGISTERED_CONTEXT_ID = 77L;
    private static final long MOCK_METRIC_KEY_ID = 1L;
    private static final int ONE_WEEK_MINUTES = 10080;
    private MetricsManager manager;
    private ISymmetricEngine engine;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
        engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
        when(engine.isInitialized()).thenReturn(true);
    }

    @Test
    void constructor_registersServiceWithManager() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertTrue(manager.getEngineMetricsServices().contains(service));
    }

    @Test
    void getEngineName_delegatesToEngine() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertEquals("test-engine", service.getEngineName());
    }

    @Test
    void getStatisticManager_delegatesToEngine() {
        IStatisticManager statMgr = mock(IStatisticManager.class);
        when(engine.getStatisticManager()).thenReturn(statMgr);
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertSame(statMgr, service.getStatisticManager());
    }

    @Test
    void isEngineInitialized_delegatesToEngine() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertTrue(service.isEngineInitialized());
        when(engine.isInitialized()).thenReturn(false);
        assertFalse(service.isEngineInitialized());
    }

    @Test
    void shutdown_unregistersServiceFromManager() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertTrue(manager.getEngineMetricsServices().contains(service));
        service.shutdown();
        assertFalse(manager.getEngineMetricsServices().contains(service));
    }

    @Test
    void shutdown_clearsRegisteredMetrics() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        service.registerUpDownCounter(new SymMetricDefinition("s.ud", "d", "r", InstrumentType.UPDOWN_COUNTER));
        assertFalse(service.getAllMetrics().isEmpty());
        service.shutdown();
        assertTrue(service.getAllMetrics().isEmpty());
    }

    @Test
    void shutdown_otelEnabled_closesOtelHandlesAndClearsMetrics() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, true);
        service.registerUpDownCounter(new SymMetricDefinition("otel.sd.ud", "d", "r", InstrumentType.UPDOWN_COUNTER));
        service.registerIncreasingCounter(new SymMetricDefinition("otel.sd.ic", "d", "r", InstrumentType.COUNTER));
        service.registerDoubleGauge(new SymMetricDefinition("otel.sd.dg", "d", "r", InstrumentType.DOUBLE_GAUGE));
        service.registerLongGauge(new SymMetricDefinition("otel.sd.lg", "d", "r", InstrumentType.LONG_GAUGE));
        assertFalse(service.getAllMetrics().isEmpty());
        service.shutdown();
        assertTrue(service.getAllMetrics().isEmpty());
    }

    @Test
    void createNodeBatchStatusMetricsMap_returnsNonNull() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        INodeBatchStatusMetricsMap map = service.createNodeBatchStatusMetricsMap(METRIC_ID_BATCHES_OUTGOING, METRIC_ID_DATA_OUTGOING);
        assertNotNull(map);
    }

    @Test
    void getOrAssignContextId_metricWithContextAlreadySet_returnsContextId() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        ISymMetric metric = mock(ISymMetric.class);
        ISymMetricContext ctx = mock(ISymMetricContext.class);
        when(metric.getContext()).thenReturn(ctx);
        when(ctx.getContextId()).thenReturn(MOCK_CONTEXT_ID);
        assertEquals(MOCK_CONTEXT_ID, service.getOrAssignContextId(metric, mock(MetricsRepository.class)));
    }

    @Test
    void getOrAssignContextId_noContextNoAttributes_returnsUndefined() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("m", "", "", InstrumentType.UPDOWN_COUNTER), Attributes
                .empty(), MetricAttributeList.of());
        assertEquals(MetricContext.UNDEFINED, service.getOrAssignContextId(metric, null));
    }

    @Test
    void getOrAssignContextId_withAttributesAndRepo_registersContextAndReturns() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        MetricAttribute attr = new MetricAttribute("channel", Constants.CHANNEL_DEFAULT);
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("m", "", "", InstrumentType.UPDOWN_COUNTER), Attributes
                .empty(), MetricAttributeList.of(attr));
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricContext ctx = new MetricContext(REGISTERED_CONTEXT_ID, MetricAttributeList.of(attr));
        when(repo.getOrRegisterContext(any(MetricAttributeList.class))).thenReturn(ctx);
        assertEquals(REGISTERED_CONTEXT_ID, service.getOrAssignContextId(metric, repo));
        assertNotNull(metric.getContext());
    }

    @Test
    void getOrAssignContextId_withAttributesButNullRepo_returnsUndefined() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        MetricAttribute attr = new MetricAttribute("channel", Constants.CHANNEL_DEFAULT);
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("m", "", "", InstrumentType.UPDOWN_COUNTER), Attributes
                .empty(), MetricAttributeList.of(attr));
        assertEquals(MetricContext.UNDEFINED, service.getOrAssignContextId(metric, null));
        assertNull(metric.getContext());
    }

    @Test
    void createMetricsRepository_withMockedEngine_constructsWithoutError() {
        IParameterService paramService = mock(IParameterService.class);
        when(paramService.getTablePrefix()).thenReturn("sym");
        IDatabasePlatform dbPlatform = mock(IDatabasePlatform.class);
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        when(dialect.getPlatform()).thenReturn(dbPlatform);
        when(engine.getParameterService()).thenReturn(paramService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        RealInitEngineMetricsService service = new RealInitEngineMetricsService(engine, manager, mock(MetricsRepository.class));
        assertNotNull(service.createMetricsRepository());
    }

    @Test
    void initializeDefaultMetrics_registersDefaultMetrics() {
        RealInitEngineMetricsService service = new RealInitEngineMetricsService(engine, manager, mock(MetricsRepository.class));
        int count = service.initializeDefaultMetrics();
        assertTrue(count > 0);
        assertFalse(service.getAllMetrics().isEmpty());
    }

    @Test
    void initializeDefaultMetrics_whenFactoryThrows_returnsZero() {
        MetricsManager spyManager = spy(TestMetricsManagerFactory.create());
        IMetricDefinitionFactory mockFactory = mock(IMetricDefinitionFactory.class);
        when(mockFactory.initializeMetrics(any())).thenThrow(new RuntimeException("factory error"));
        doReturn(mockFactory).when(spyManager).getMetricDefinitionFactory();
        RealInitEngineMetricsService service = new RealInitEngineMetricsService(engine, spyManager, mock(MetricsRepository.class));
        assertEquals(0, service.initializeDefaultMetrics());
    }

    @Test
    void initializeDefaultContexts_callsRepoForAllDefaultContexts() {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        service.initializeDefaultContexts(repo);
        verify(repo, atLeastOnce()).getOrRegisterContext(any(ContextDefinition.class));
    }

    @Test
    void initializeDefaultContexts_whenRepoThrows_doesNotPropagateException() {
        MetricsRepository repo = mock(MetricsRepository.class);
        doThrow(new RuntimeException("db error")).when(repo).getOrRegisterContext(any(ContextDefinition.class));
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertDoesNotThrow(() -> service.initializeDefaultContexts(repo));
    }

    @Test
    void initializeStatsWorksets_withEnabledKey_seedsWorkset() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "one.metric", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(anyString(), any(), any(), anyBoolean())).thenReturn(key);
        when(repo.loadRecentIntervalsPerKey(any())).thenReturn(Map.of(key, List.of()));
        OneMetricInitService service = new OneMetricInitService(engine, manager, repo);
        service.initRepository();
        assertTrue(service.getAllMetrics().iterator().next().isOpen());
    }

    @Test
    void initializeStatsWorksets_withDisabledKey_closesMetric() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "one.metric", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, false);
        when(repo.getMetricKey(anyString(), any(), any(), anyBoolean())).thenReturn(key);
        when(repo.loadRecentIntervalsPerKey(any())).thenReturn(Map.of());
        OneMetricInitService service = new OneMetricInitService(engine, manager, repo);
        service.initRepository();
        service.initWorksetsIfNeeded();
        assertFalse(service.getAllMetrics().iterator().next().isOpen());
    }

    @Test
    void initializeStatsWorksets_whenGetMetricKeyThrows_catchesExceptionAndKeepsMetricOpen() {
        MetricsRepository repo = mock(MetricsRepository.class);
        when(repo.getMetricKey(anyString(), any(), any(), anyBoolean())).thenThrow(new RuntimeException("db error"));
        when(repo.loadRecentIntervalsPerKey(any())).thenReturn(Map.of());
        OneMetricInitService service = new OneMetricInitService(engine, manager, repo);
        assertDoesNotThrow(service::initRepository);
        assertTrue(service.getAllMetrics().iterator().next().isOpen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeStatsWorksets_multipleInstrumentsSameKey_loadsHistoryOnce() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "one.metric", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(anyString(), any(), any(), anyBoolean())).thenReturn(key);
        when(repo.loadRecentIntervalsPerKey(any())).thenReturn(Map.of(key, List.of()));
        TwoContextInitService service = new TwoContextInitService(engine, manager, repo);
        service.initRepository();
        service.initWorksetsIfNeeded();
        verify(repo, times(1)).loadRecentIntervalsPerKey(any(Collection.class));
        assertEquals(2, service.getAllMetrics().size());
        service.getAllMetrics().forEach(m -> assertTrue(m.isOpen()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedWorksetsFromHistory_emptyHistory_savesBootstrapZeroInterval() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "one.metric", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(anyString(), any(), any(), anyBoolean())).thenReturn(key);
        when(repo.loadRecentIntervalsPerKey(any())).thenReturn(Map.of(key, List.of()));
        OneMetricInitService service = new OneMetricInitService(engine, manager, repo);
        service.initRepository();
        service.initWorksetsIfNeeded();
        verify(repo, times(1)).saveIntervals(any(List.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedWorksetsFromHistory_nonEmptyHistory_doesNotSaveBootstrapInterval() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "one.metric", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(anyString(), any(), any(), anyBoolean())).thenReturn(key);
        when(repo.loadRecentIntervalsPerKey(any())).thenReturn(Map.of(key, List.of(mock(ISymIntervalStats.class))));
        OneMetricInitService service = new OneMetricInitService(engine, manager, repo);
        service.initRepository();
        service.initWorksetsIfNeeded();
        verify(repo, never()).saveIntervals(any(List.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void initWorksetsIfNeeded_engineNotInitialized_returnsFalseWithoutLoadingHistory() {
        MetricsRepository repo = mock(MetricsRepository.class);
        OneMetricInitService service = new OneMetricInitService(engine, manager, repo);
        service.initRepository();
        when(engine.isInitialized()).thenReturn(false);
        assertFalse(service.initWorksetsIfNeeded());
        verify(repo, never()).loadRecentIntervalsPerKey(any(Collection.class));
    }

    @Test
    void purgeMetricStats_nullRepository_returnsEarlyWithoutError() {
        EngineMetricsService service = new NullRepoEngineMetricsService(engine, manager);
        service.initRepository();
        assertDoesNotThrow(() -> service.purgeMetricStats(false));
    }

    @Test
    void purgeMetricStats_withNonNullRepo_callsPurgeIntervalStats() {
        MetricsRepository repo = mock(MetricsRepository.class);
        IParameterService paramService = mock(IParameterService.class);
        when(paramService.getInt(anyString(), anyInt())).thenReturn(ONE_WEEK_MINUTES);
        when(engine.getParameterService()).thenReturn(paramService);
        when(repo.purgeIntervalStats(any())).thenReturn(1);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        assertDoesNotThrow(() -> service.purgeMetricStats(false));
        verify(repo, atLeastOnce()).purgeIntervalStats(any());
    }

    @Test
    void purgeMetricStats_engineNotInitialized_doesNotCallPurgeIntervalStats() {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        when(engine.isInitialized()).thenReturn(false);
        service.purgeMetricStats(false);
        verify(repo, never()).purgeIntervalStats(any());
    }

    @Test
    void saveCompletedIntervalStats_engineNotInitialized_doesNotTouchRepository() {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        service.registerUpDownCounter(new SymMetricDefinition("s.uninitialized", "d", "r", InstrumentType.UPDOWN_COUNTER));
        when(engine.isInitialized()).thenReturn(false);
        service.saveCompletedIntervalStats();
        verify(repo, never()).getMetricKey(anyString(), any(), any(), anyBoolean());
        verify(repo, never()).saveIntervals(any());
    }

    @Test
    void saveCompletedIntervalStats_noMetrics_doesNotCallSaveIntervals() {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        service.saveCompletedIntervalStats();
        verify(repo, never()).saveIntervals(any());
    }

    @Test
    void saveCompletedIntervalStats_disabledMetric_isSkippedAndSaveNotCalled() {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        service.registerUpDownCounter(new SymMetricDefinition("s.skip", "d", "r", InstrumentType.UPDOWN_COUNTER));
        service.getAllMetrics().iterator().next().close();
        service.saveCompletedIntervalStats();
        verify(repo, never()).saveIntervals(any());
    }

    @Test
    void saveCompletedIntervalStats_metricWithIsEnabledFalse_skipsViaIsEnabledCheck() {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        service.registerUpDownCounter(new SymMetricDefinition("s.disabled", "d", "r", InstrumentType.UPDOWN_COUNTER));
        ((AbstractQueuedMetric) service.getAllMetrics().iterator().next()).isMetricEnabled = false;
        service.saveCompletedIntervalStats();
        verify(repo, never()).saveIntervals(any());
    }

    @Test
    void saveCompletedIntervalStats_enabledMetricNoCompletedIntervals_doesNotCallSaveIntervals() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "s.ud", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(any(), any(), any(), anyBoolean())).thenReturn(key);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        service.registerUpDownCounter(new SymMetricDefinition("s.ud", "d", "r", InstrumentType.UPDOWN_COUNTER));
        service.saveCompletedIntervalStats();
        verify(repo, never()).saveIntervals(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveCompletedIntervalStats_whenExportThrows_catchesException() throws Exception {
        MetricsRepository repo = mock(MetricsRepository.class);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        Field f = AbstractMetricsService.class.getDeclaredField("metrics");
        f.setAccessible(true);
        ConcurrentHashMap<String, ISymMetric> metricsMap = (ConcurrentHashMap<String, ISymMetric>) f.get(service);
        ISymMetric badMetric = mock(ISymMetric.class);
        when(badMetric.isEnabled()).thenReturn(true);
        when(badMetric.getMetricId()).thenReturn("bad.metric");
        doThrow(new RuntimeException("close error")).when(badMetric).closeCompletedIntervals();
        metricsMap.put("bad.metric", badMetric);
        assertDoesNotThrow(service::saveCompletedIntervalStats);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveCompletedIntervalStats_withCompletedIntervals_callsSaveIntervals() throws Exception {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(MOCK_METRIC_KEY_ID, "host", "test-engine", "t.metric", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(any(), any(), any(), anyBoolean())).thenReturn(key);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        Field f = AbstractMetricsService.class.getDeclaredField("metrics");
        f.setAccessible(true);
        ConcurrentHashMap<String, ISymMetric> metricsMap = (ConcurrentHashMap<String, ISymMetric>) f.get(service);
        ISymMetric metric = mock(ISymMetric.class);
        when(metric.isEnabled()).thenReturn(true);
        when(metric.getMetricId()).thenReturn("t.metric");
        when(metric.getFactType()).thenReturn(MetricFactType.INT64);
        when(metric.getMetricType()).thenReturn(InstrumentType.UPDOWN_COUNTER);
        when(metric.getAttributes()).thenReturn(new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        when(metric.getContext()).thenReturn(null);
        when(metric.exportCompletedIntervals()).thenReturn(List.of(mock(ISymIntervalStats.class)));
        metricsMap.put("t.metric", metric);
        service.saveCompletedIntervalStats();
        verify(repo).saveIntervals(any());
    }

    /** Overrides createMetricsRepository() and initializeDefaultContexts() but not initializeDefaultMetrics(), to test the real implementation. */
    private static class RealInitEngineMetricsService extends EngineMetricsService {
        private final MetricsRepository mockRepo;

        RealInitEngineMetricsService(ISymmetricEngine engine, MetricsManager manager, MetricsRepository repo) {
            super(engine, manager, false);
            this.mockRepo = repo;
        }

        @Override
        protected MetricsRepository createMetricsRepository() {
            return mockRepo;
        }

        @Override
        protected void initializeDefaultContexts(MetricsRepository repo) {
            // Skip default metric contexts
        }
    }

    /** Overrides createMetricsRepository() to return null, to exercise the early-return paths. */
    private static class NullRepoEngineMetricsService extends EngineMetricsService {
        NullRepoEngineMetricsService(ISymmetricEngine engine, MetricsManager manager) {
            super(engine, manager, false);
        }

        @Override
        protected MetricsRepository createMetricsRepository() {
            return null;
        }

        @Override
        protected int initializeDefaultMetrics() {
            return 0;
        }

        @Override
        protected void initializeDefaultContexts(MetricsRepository repo) {
            // Skip default metric contexts
        }
    }

    /** Overrides createMetricsRepository() to return a provided mock, for saveCompletedIntervalStats tests. */
    private static class MockRepoEngineMetricsService extends EngineMetricsService {
        private final MetricsRepository mockRepo;

        MockRepoEngineMetricsService(ISymmetricEngine engine, MetricsManager manager, MetricsRepository repo) {
            super(engine, manager, false);
            this.mockRepo = repo;
        }

        @Override
        protected MetricsRepository createMetricsRepository() {
            return mockRepo;
        }

        @Override
        protected int initializeDefaultMetrics() {
            return 0;
        }

        @Override
        protected void initializeDefaultContexts(MetricsRepository repo) {
            // Skip default metric contexts
        }
    }

    /** Overrides createMetricsRepository() to return a provided mock and registers exactly one metric, to exercise initWorksetForMetric paths. */
    private static class OneMetricInitService extends EngineMetricsService {
        private final MetricsRepository mockRepo;

        OneMetricInitService(ISymmetricEngine engine, MetricsManager manager, MetricsRepository repo) {
            super(engine, manager, false);
            this.mockRepo = repo;
        }

        @Override
        protected MetricsRepository createMetricsRepository() {
            return mockRepo;
        }

        @Override
        protected int initializeDefaultMetrics() {
            registerUpDownCounter(new SymMetricDefinition("one.metric", "d", "r", InstrumentType.UPDOWN_COUNTER));
            return 1;
        }

        @Override
        protected void initializeDefaultContexts(MetricsRepository repo) {
            // Skip default metric contexts
        }
    }

    /** Registers two instruments for the same metric ID with different attribute contexts to verify history is loaded once per key. */
    private static class TwoContextInitService extends EngineMetricsService {
        private final MetricsRepository mockRepo;

        TwoContextInitService(ISymmetricEngine engine, MetricsManager manager, MetricsRepository repo) {
            super(engine, manager, false);
            this.mockRepo = repo;
        }

        @Override
        protected MetricsRepository createMetricsRepository() {
            return mockRepo;
        }

        @Override
        protected int initializeDefaultMetrics() {
            MetricAttributeList ctx1 = new MetricAttributeList(List.of(new MetricAttribute("node", "node-1")));
            MetricAttributeList ctx2 = new MetricAttributeList(List.of(new MetricAttribute("node", "node-2")));
            registerUpDownCounter(new SymMetricDefinition("one.metric", "d", "r", InstrumentType.UPDOWN_COUNTER), ctx1);
            registerUpDownCounter(new SymMetricDefinition("one.metric", "d", "r", InstrumentType.UPDOWN_COUNTER), ctx2);
            return 2;
        }

        @Override
        protected void initializeDefaultContexts(MetricsRepository repo) {
            // Skip default metric contexts
        }
    }
}
