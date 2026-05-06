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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymMetric;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.Attributes;

class AbstractMetricsServiceTest {
    private static class TestService extends AbstractMetricsService {
        TestService(MetricsManager manager) {
            super(manager, Attributes.empty(), false);
        }

        @Override
        public void saveCompletedIntervalStats() {
        }
    }

    private MetricsManager manager;
    private TestService service;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
        service = new TestService(manager);
    }

    @Test
    void initRepository_isNoOp_doesNotThrow() {
        assertDoesNotThrow(service::initRepository);
    }

    @Test
    void shutdown_withNonOpenMetric_skipsCloseAndClearsMetrics() {
        service.registerUpDownCounter(new SymMetricDefinition("t.ud", "d", "r", InstrumentType.UPDOWN_COUNTER));
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
    void resetGaugesToZero_withDisabledMetric_skipsIt() {
        ISymDoubleGauge gauge = service.registerDoubleGauge(
                new SymMetricDefinition("t.dg", "d", "r", InstrumentType.DOUBLE_GAUGE));
        gauge.setValue(10.0);
        ((AbstractQueuedMetric) gauge).isMetricEnabled = false;
        service.resetGaugesToZero();
        assertEquals(10.0, gauge.getValue());
    }
}
