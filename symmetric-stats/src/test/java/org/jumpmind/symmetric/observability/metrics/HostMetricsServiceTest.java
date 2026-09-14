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

import static org.junit.jupiter.api.Assertions.*;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.observability.interfaces.IIncreasingCounter;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
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
                org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_RUNTIME_DBPOOL_IDLE, MetricAttributeList.of());
        assertNotNull(gauge);
    }

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

    @Test
    void resetGaugesToZero_setsDoubleGaugesToZero() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("rg.dg", "d", "r", InstrumentType.DOUBLE_GAUGE);
        ISymDoubleGauge gauge = service.registerDoubleGauge(def);
        gauge.setValue(42.0);
        service.resetGaugesToZero();
        assertEquals(0.0, gauge.getValue());
    }

    @Test
    void resetGaugesToZero_setsLongGaugesToZero() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("rg.lg", "d", "r", InstrumentType.LONG_GAUGE);
        ISymLongGauge gauge = service.registerLongGauge(def);
        gauge.setValue(99L);
        service.resetGaugesToZero();
        assertEquals(0L, gauge.getValue());
    }

    @Test
    void attributeScopedRegistration_differentAttrs_createsDistinctEntries() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("attr.lg", "d", "r", InstrumentType.LONG_GAUGE);
        MetricAttributeList attrsA = MetricAttributeList.of(new MetricAttribute("channel", "ch1"));
        MetricAttributeList attrsB = MetricAttributeList.of(new MetricAttribute("channel", "ch2"));
        ISymLongGauge gaugeA = service.registerLongGauge(def, attrsA);
        ISymLongGauge gaugeB = service.registerLongGauge(def, attrsB);
        assertNotEquals(gaugeA, gaugeB);
        assertTrue(service.getAllMetrics().size() >= 2);
    }

    @Test
    void registerUpDownCounter_otelEnabled_createsOtelHandle() {
        HostMetricsService service = new HostMetricsService(manager, true);
        SymMetricDefinition def = new SymMetricDefinition("otel.ud", "desc", "rows", InstrumentType.UPDOWN_COUNTER);
        assertNotNull(service.registerUpDownCounter(def));
    }

    @Test
    void registerUpDownCounter_withAttrs_createsEntryRetrievableWithSameAttrs() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.ud.attrs", "desc", "rows", InstrumentType.UPDOWN_COUNTER);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        IUpDownCounter counter = service.registerUpDownCounter(def, attrs);
        assertNotNull(counter);
        assertSame(counter, service.getUpDownCounter("test.ud.attrs", attrs));
    }

    @Test
    void registerIncreasingCounter_withAttrs_createsEntryRetrievableWithSameAttrs() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.ic.attrs", "desc", "rows", InstrumentType.COUNTER);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        IIncreasingCounter counter = service.registerIncreasingCounter(def, attrs);
        assertNotNull(counter);
        assertSame(counter, service.getIncreasingCounter("test.ic.attrs", attrs));
    }

    @Test
    void registerIncreasingCounter_otelEnabled_createsOtelHandle() {
        HostMetricsService service = new HostMetricsService(manager, true);
        SymMetricDefinition def = new SymMetricDefinition("otel.ic", "desc", "rows", InstrumentType.COUNTER);
        assertNotNull(service.registerIncreasingCounter(def));
    }

    @Test
    void getIncreasingCounter_withAttrs_beforeRegistration_returnsNull() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertNull(service.getIncreasingCounter("test.ic.absent", MetricAttributeList.of(new MetricAttribute("channel", "x"))));
    }

    @Test
    void registerDoubleGauge_withAttrs_createsEntryRetrievableWithSameAttrs() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.dg.attrs", "desc", "percent", InstrumentType.DOUBLE_GAUGE);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        ISymDoubleGauge gauge = service.registerDoubleGauge(def, attrs);
        assertNotNull(gauge);
        assertSame(gauge, service.getDoubleGauge("test.dg.attrs", attrs));
    }

    @Test
    void registerDoubleGauge_otelEnabled_createsOtelHandle() {
        HostMetricsService service = new HostMetricsService(manager, true);
        SymMetricDefinition def = new SymMetricDefinition("otel.dg", "desc", "percent", InstrumentType.DOUBLE_GAUGE);
        assertNotNull(service.registerDoubleGauge(def));
    }

    @Test
    void registerLongGauge_withAttrs_createsEntryRetrievableWithSameAttrs() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.lg.attrs2", "desc", "connections", InstrumentType.LONG_GAUGE);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        ISymLongGauge gauge = service.registerLongGauge(def, attrs);
        assertNotNull(gauge);
        assertSame(gauge, service.getLongGauge("test.lg.attrs2", attrs));
    }

    @Test
    void registerLongGauge_otelEnabled_createsOtelHandle() {
        HostMetricsService service = new HostMetricsService(manager, true);
        SymMetricDefinition def = new SymMetricDefinition("otel.lg", "desc", "connections", InstrumentType.LONG_GAUGE);
        assertNotNull(service.registerLongGauge(def));
    }

    @Test
    void getLongGauge_withAttrs_beforeRegistration_returnsNull() {
        HostMetricsService service = new HostMetricsService(manager, false);
        assertNull(service.getLongGauge("test.lg.absent", MetricAttributeList.of(new MetricAttribute("channel", "x"))));
    }

    @Test
    void instrumentKey_attrWithNullName_usesEmptyStringInKey() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.nullname", "desc", "rows", InstrumentType.LONG_GAUGE);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute(null, "val"));
        assertNotNull(service.registerLongGauge(def, attrs));
    }

    @Test
    void instrumentKey_attrWithNullValue_usesEmptyStringInKey() {
        HostMetricsService service = new HostMetricsService(manager, false);
        SymMetricDefinition def = new SymMetricDefinition("test.nullval", "desc", "rows", InstrumentType.LONG_GAUGE);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", null));
        assertNotNull(service.registerLongGauge(def, attrs));
    }

    @Test
    void buildInstrumentAttributes_withNonEmptyAttrs_mergesAttributesForOtelHandle() {
        HostMetricsService service = new HostMetricsService(manager, true);
        SymMetricDefinition def = new SymMetricDefinition("otel.ud.merge", "desc", "rows", InstrumentType.UPDOWN_COUNTER);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        assertNotNull(service.registerUpDownCounter(def, attrs));
    }

    @Test
    void buildInstrumentAttributes_attrWithNullNameOrValue_isSkippedFromOtelAttributes() {
        HostMetricsService service = new HostMetricsService(manager, true);
        SymMetricDefinition def = new SymMetricDefinition("otel.ud.nullattr", "desc", "rows", InstrumentType.UPDOWN_COUNTER);
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute(null, null));
        assertNotNull(service.registerUpDownCounter(def, attrs));
    }
}
