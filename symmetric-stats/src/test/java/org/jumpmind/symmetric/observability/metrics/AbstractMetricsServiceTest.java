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

import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.CHANNEL;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_CREATE_TIME_MIN;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_ROUTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.InvalidMetricDataException;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractMetricsServiceTest {
    private MetricsManager manager;
    private MetricsServiceUnderTest service;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
        service = new MetricsServiceUnderTest(manager);
    }

    @Test
    void initRepository_isNoOp_doesNotThrow() {
        assertDoesNotThrow(service::initRepository);
    }

    @Test
    void shutdown_withNonOpenMetric_skipsCloseAndClearsMetrics() {
        service.registerUpDownCounter(new SymMetricDefinition("test.upd_counter", "d", "r", InstrumentType.UPDOWN_COUNTER));
        service.getAllMetrics().iterator().next().close();
        assertDoesNotThrow(service::shutdown);
        assertTrue(service.getAllMetrics().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_whenMetricCloseThrows_doesNotPropagateExceptionAndClearsMetrics() throws Exception {
        Field f = AbstractMetricsService.class.getDeclaredField("metrics");
        f.setAccessible(true);
        ConcurrentHashMap<String, ISymMetric> metricsMap = (ConcurrentHashMap<String, ISymMetric>) f.get(service);
        ISymMetric badMetric = mock(ISymMetric.class);
        when(badMetric.isOpen()).thenReturn(true);
        when(badMetric.getMetricId()).thenReturn("bad.metric");
        doThrow(new RuntimeException("close failed")).when(badMetric).close();
        metricsMap.put("bad.metric", badMetric);
        assertDoesNotThrow(service::shutdown);
        assertTrue(service.getAllMetrics().isEmpty());
    }

    @Test
    void resetGaugesToZero_withDefaultMetric_zero() {
        ISymDoubleGauge gauge = service.registerDoubleGauge(
                new SymMetricDefinition("test.enabled.double_gauge", "d", "r", InstrumentType.DOUBLE_GAUGE));
        gauge.setValue(11.0);
        ((AbstractQueuedMetric) gauge).isMetricEnabled = true;
        service.resetGaugesToZero();
        assertEquals(0.0, gauge.getValue());
    }

    /**
     * Disabled metrics are NOT reset to zero, because that would create a new observation
     */
    @Test
    void resetGaugesToZero_withDisabledMetric_skipsIt() {
        ISymDoubleGauge gauge = service.registerDoubleGauge(
                new SymMetricDefinition("test.disabled.double_gauge", "d", "r", InstrumentType.DOUBLE_GAUGE));
        gauge.setValue(10.0);
        ((AbstractQueuedMetric) gauge).isMetricEnabled = false;
        service.resetGaugesToZero();
        assertEquals(10.0, gauge.getValue());
    }

    @Test
    void registerUpDownCounter_byMetricIdForUnknownChannel_createsAndReturnsIdempotentCounter() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "custom_channel_not_in_default_contexts"));
        assertNull(service.getUpDownCounter(METRIC_ID_DATA_ROUTED, attrs));
        IUpDownCounter counter = service.registerUpDownCounter(METRIC_ID_DATA_ROUTED, attrs);
        assertNotNull(counter);
        counter.add(5L);
        assertEquals(5L, counter.getValue());
        assertSame(counter, service.registerUpDownCounter(METRIC_ID_DATA_ROUTED, attrs));
    }

    @Test
    void registerUpDownCounter_byUnregisteredMetricId_throwsInvalidMetricDataException() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "custom_channel"));
        assertThrows(InvalidMetricDataException.class, () -> service.registerUpDownCounter("no.such.metric", attrs));
    }

    @Test
    void registerLongGauge_byMetricIdForUnknownChannel_createsAndReturnsIdempotentGauge() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "custom_channel_not_in_default_contexts"));
        assertNull(service.getLongGauge(METRIC_ID_DATA_CREATE_TIME_MIN, attrs));
        ISymLongGauge gauge = service.registerLongGauge(METRIC_ID_DATA_CREATE_TIME_MIN, attrs);
        assertNotNull(gauge);
        gauge.setValue(42L);
        assertEquals(42L, gauge.getValue());
        assertSame(gauge, service.registerLongGauge(METRIC_ID_DATA_CREATE_TIME_MIN, attrs));
    }

    @Test
    void registerLongGauge_byUnregisteredMetricId_throwsInvalidMetricDataException() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "custom_channel"));
        assertThrows(InvalidMetricDataException.class, () -> service.registerLongGauge("no.such.metric", attrs));
    }

    static class MetricsServiceUnderTest extends AbstractMetricsService {
        MetricsServiceUnderTest(MetricsManager manager) {
            super(manager, Attributes.empty(), false);
        }

        @Override
        public void saveCompletedIntervalStats() {
            // no persistence needed in tests
        }
    }
}
