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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.IIncreasingCounter;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostMetricsServiceTest {
    private MetricsManager manager;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
    }

    @Test
    void constructor_otelDisabled_doesNotThrow() {
        assertDoesNotThrow(() -> new HostMetricsService(manager, false));
    }

    @Test
    void constructor_otelEnabled_doesNotThrow() {
        assertDoesNotThrow(() -> new HostMetricsService(manager, true));
    }

    @Test
    void isOtelPublishingEnabled_false_matchesConstructorArg() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertFalse(service.isOtelPublishingEnabled());
    }

    @Test
    void isOtelPublishingEnabled_true_matchesConstructorArg() {
        HostMetricsService service = new HostMetricsService(manager, true);
        assertTrue(service.isOtelPublishingEnabled());
    }

    @Test
    void saveCompletedIntervalStats_isNoOp_doesNotThrow() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertDoesNotThrow(service::saveCompletedIntervalStats);
    }

    @Test
    void shutdown_doesNotThrow() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertDoesNotThrow(service::shutdown);
    }

    // ── registerUpDownCounter / getUpDownCounter ──────────────────────────────

    @Test
    void registerUpDownCounter_createsAndCachesEntry() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.ud", "desc", "rows", InstrumentType.UPDOWN_COUNTER);
        IUpDownCounter counter = service.registerUpDownCounter(def);
        assertNotNull(counter);
    }

    @Test
    void getUpDownCounter_beforeRegistration_returnsNull() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertNull(service.getUpDownCounter("test.ud.absent"));
    }

    @Test
    void getUpDownCounter_afterRegistration_returnsSameInstance() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.ud2", "desc", "rows", InstrumentType.UPDOWN_COUNTER);
        IUpDownCounter registered = service.registerUpDownCounter(def);
        assertSame(registered, service.getUpDownCounter("test.ud2"));
    }

    // ── registerIncreasingCounter / getIncreasingCounter ──────────────────────

    @Test
    void registerIncreasingCounter_createsEntry() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.ic", "desc", "rows", InstrumentType.COUNTER);
        IIncreasingCounter counter = service.registerIncreasingCounter(def);
        assertNotNull(counter);
    }

    @Test
    void getIncreasingCounter_beforeRegistration_returnsNull() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertNull(service.getIncreasingCounter("test.ic.absent"));
    }

    @Test
    void getIncreasingCounter_afterRegistration_returnsSameInstance() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.ic2", "desc", "rows", InstrumentType.COUNTER);
        IIncreasingCounter registered = service.registerIncreasingCounter(def);
        assertSame(registered, service.getIncreasingCounter("test.ic2"));
    }

    // ── registerDoubleGauge / getDoubleGauge ──────────────────────────────────

    @Test
    void registerDoubleGauge_createsEntry() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.dg", "desc", "percent", InstrumentType.DOUBLE_GAUGE);
        ISymDoubleGauge gauge = service.registerDoubleGauge(def);
        assertNotNull(gauge);
    }

    @Test
    void getDoubleGauge_beforeRegistration_returnsNull() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertNull(service.getDoubleGauge("test.dg.absent"));
    }

    @Test
    void getDoubleGauge_afterRegistration_returnsSameInstance() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.dg2", "desc", "percent", InstrumentType.DOUBLE_GAUGE);
        ISymDoubleGauge registered = service.registerDoubleGauge(def);
        assertSame(registered, service.getDoubleGauge("test.dg2"));
    }

    // ── registerLongGauge / getLongGauge ──────────────────────────────────────

    @Test
    void registerLongGauge_byDefinition_createsEntry() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.lg", "desc", "connections", InstrumentType.LONG_GAUGE);
        ISymLongGauge gauge = service.registerLongGauge(def);
        assertNotNull(gauge);
    }

    @Test
    void getLongGauge_beforeRegistration_returnsNull() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertNull(service.getLongGauge("test.lg.absent"));
    }

    @Test
    void getLongGauge_afterRegistration_returnsSameInstance() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.lg2", "desc", "connections", InstrumentType.LONG_GAUGE);
        ISymLongGauge registered = service.registerLongGauge(def);
        assertSame(registered, service.getLongGauge("test.lg2"));
    }

    @Test
    void registerLongGauge_byMetricIdAndAttrs_createsEntry() {
        HostMetricsService service = new HostMetricsService(manager, false);
        // METRIC_ID_RUNTIME_DBPOOL_IDLE is a LONG_GAUGE already registered in factory
        ISymLongGauge gauge = service.registerLongGauge(
                org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_RUNTIME_DBPOOL_IDLE, List.of());
        assertNotNull(gauge);
    }

    // ── getAllMetrics ─────────────────────────────────────────────────────────

    @Test
    void getAllMetrics_afterRegistrations_containsAllRegistered() {
        HostMetricsService service = new HostMetricsService(manager, false);
        service.registerUpDownCounter(new SymMetricDefinition("m.ud", "d", "r", InstrumentType.UPDOWN_COUNTER));
        service.registerDoubleGauge(new SymMetricDefinition("m.dg", "d", "r", InstrumentType.DOUBLE_GAUGE));
        assertTrue(service.getAllMetrics().size() >= 2);
    }

    @Test
    void getAllMetrics_newService_isEmpty() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertTrue(service.getAllMetrics().isEmpty());
    }

    // ── resetGaugesToZero ─────────────────────────────────────────────────────

    @Test
    void resetGaugesToZero_setsDoubleGaugesToZero() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("rg.dg", "d", "r", InstrumentType.DOUBLE_GAUGE);
        ISymDoubleGauge gauge = service.registerDoubleGauge(def);
        gauge.setValue(42.0);
        service.resetGaugesToZero();
        assertTrue(gauge.getValue() == 0.0);
    }

    @Test
    void resetGaugesToZero_setsLongGaugesToZero() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("rg.lg", "d", "r", InstrumentType.LONG_GAUGE);
        ISymLongGauge gauge = service.registerLongGauge(def);
        gauge.setValue(99L);
        service.resetGaugesToZero();
        assertTrue(gauge.getValue() == 0L);
    }

    // ── attribute-scoped instrument key ───────────────────────────────────────

    @Test
    void attributeScopedRegistration_differentAttrs_createsDistinctEntries() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("attr.lg", "d", "r", InstrumentType.LONG_GAUGE);
        List<MetricAttribute> attrsA = List.of(new MetricAttribute("channel", "ch1"));
        List<MetricAttribute> attrsB = List.of(new MetricAttribute("channel", "ch2"));
        ISymLongGauge gaugeA = service.registerLongGauge(def, attrsA);
        ISymLongGauge gaugeB = service.registerLongGauge(def, attrsB);
        assertFalse(gaugeA == gaugeB);
        assertTrue(service.getAllMetrics().size() >= 2);
    }
}
