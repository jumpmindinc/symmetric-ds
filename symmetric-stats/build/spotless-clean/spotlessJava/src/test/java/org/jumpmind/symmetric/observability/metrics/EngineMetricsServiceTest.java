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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
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
    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_registersServiceWithManager() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertTrue(manager.getEngineMetricsServices().contains(service));
    }
    // ── getEngineName ─────────────────────────────────────────────────────────

    @Test
    void getEngineName_delegatesToEngine() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertEquals("test-engine", service.getEngineName());
    }
    // ── getStatisticManager ───────────────────────────────────────────────────

    @Test
    void getStatisticManager_delegatesToEngine() {
        IStatisticManager statMgr = mock(IStatisticManager.class);
        when(engine.getStatisticManager()).thenReturn(statMgr);
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        assertSame(statMgr, service.getStatisticManager());
    }
    // ── shutdown ──────────────────────────────────────────────────────────────

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
        service.registerUpDownCounter(new SymMetricDefinition("s.ud", "d", "r",
                org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType.UPDOWN_COUNTER));
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
    // ── createNodeBatchStatusMetricsMap ───────────────────────────────────────

    @Test
    void createNodeBatchStatusMetricsMap_returnsNonNull() {
        EngineMetricsService service = new EngineMetricsService(engine, manager, false);
        INodeBatchStatusMetricsMap map = service.createNodeBatchStatusMetricsMap(METRIC_ID_BATCHES_OUTGOING, METRIC_ID_DATA_OUTGOING);
        assertNotNull(map);
    }
}
