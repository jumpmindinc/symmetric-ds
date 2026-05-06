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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.ISymMetricContext;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.models.MetricContext;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.repository.MetricsRepository;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EngineMetricsServiceTest {
    private MetricsManager manager;
    private ISymmetricEngine engine;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
        engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
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
        service.shutdown(); // calls super.shutdown() → closeAllOtelHandles()
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
        when(ctx.getContextId()).thenReturn(42L);
        assertEquals(42L, service.getOrAssignContextId(metric, mock(MetricsRepository.class)));
    }

    @Test
    void getOrAssignContextId_noContextNoAttributes_returnsUndefined() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("m", "", "", InstrumentType.UPDOWN_COUNTER), io.opentelemetry.api.common.Attributes
                .empty(), List.of());
        assertEquals(MetricContext.UNDEFINED, service.getOrAssignContextId(metric, null));
    }

    @Test
    void getOrAssignContextId_withAttributesAndRepo_registersContextAndReturns() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        MetricAttribute attr = new MetricAttribute("channel", Constants.CHANNEL_DEFAULT);
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("m", "", "", InstrumentType.UPDOWN_COUNTER), io.opentelemetry.api.common.Attributes
                .empty(), List.of(attr));
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricContext ctx = new MetricContext(77L, List.of(attr));
        when(repo.getOrRegisterContext(any(List.class))).thenReturn(ctx);
        assertEquals(77L, service.getOrAssignContextId(metric, repo));
        assertNotNull(metric.getContext());
    }

    @Test
    void getOrAssignContextId_withAttributesButNullRepo_returnsUndefined() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        MetricAttribute attr = new MetricAttribute("channel", Constants.CHANNEL_DEFAULT);
        UpDownCounter metric = new UpDownCounter(new SymMetricDefinition("m", "", "", InstrumentType.UPDOWN_COUNTER), io.opentelemetry.api.common.Attributes
                .empty(), List.of(attr));
        assertEquals(MetricContext.UNDEFINED, service.getOrAssignContextId(metric, null));
        assertNull(metric.getContext());
    }

    @Test
    void purgeMetricStats_nullRepository_returnsEarlyWithoutError() {
        EngineMetricsService service = new NullRepoEngineMetricsService(engine, manager);
        service.initRepository();
        assertDoesNotThrow(() -> service.purgeMetricStats(false));
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
    void saveCompletedIntervalStats_enabledMetricNoCompletedIntervals_doesNotCallSaveIntervals() {
        MetricsRepository repo = mock(MetricsRepository.class);
        MetricKey key = new MetricKey(1L, "host", "test-engine", "s.ud", MetricFactType.INT64, InstrumentType.UPDOWN_COUNTER, true);
        when(repo.getMetricKey(any(), any(), any(), anyBoolean())).thenReturn(key);
        EngineMetricsService service = new MockRepoEngineMetricsService(engine, manager, repo);
        service.initRepository();
        service.registerUpDownCounter(new SymMetricDefinition("s.ud", "d", "r", InstrumentType.UPDOWN_COUNTER));
        service.saveCompletedIntervalStats();
        verify(repo, never()).saveIntervals(any());
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
        }
    }
}
