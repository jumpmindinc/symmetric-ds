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
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.IPrimaryMetricAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;

class MetricsManagerTest {
    private MetricsManager manager;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
    }

    @Test
    void getMetricDefinitionFactory_returnsNonNull() {
        assertNotNull(manager.getMetricDefinitionFactory());
    }

    @Test
    void getEngineMetricsServices_initiallyEmpty() {
        assertTrue(manager.getEngineMetricsServices().isEmpty());
    }

    @Test
    void register_addsService_getEngineMetricsServices_containsIt() {
        IEngineMetricsService svc = mock(IEngineMetricsService.class);
        manager.register(svc);
        assertTrue(manager.getEngineMetricsServices().contains(svc));
    }

    @Test
    void unregister_removesService() {
        IEngineMetricsService svc = mock(IEngineMetricsService.class);
        manager.register(svc);
        manager.unregister(svc);
        assertTrue(manager.getEngineMetricsServices().isEmpty());
    }

    @Test
    void getHostMetricsService_returnsHostMetricsService() {
        assertNotNull(manager.getHostMetricsService());
    }

    @Test
    void getHostMetricsService_calledTwice_returnsSameInstance() {
        HostMetricsService first = manager.getHostMetricsService();
        HostMetricsService second = manager.getHostMetricsService();
        assertSame(first, second);
    }

    @Test
    void getAggregator_initiallyNull() {
        assertNull(manager.getAggregator());
    }

    @Test
    void shutdown_whenAggregatorIsNull_doesNotThrow() {
        assertDoesNotThrow(manager::shutdown);
    }

    @Test
    void shutdown_whenHostMetricsServiceIsNull_doesNotThrow() {
        // Fresh manager — host metrics service not yet initialized
        MetricsManager freshManager = TestMetricsManagerFactory.create();
        assertDoesNotThrow(freshManager::shutdown);
    }

    @Test
    void constructor_withNoopOpenTelemetry_createsUsableManager() {
        MetricsManager m = new MetricsManager(OpenTelemetry.noop(), MetricsManager.getServerProperties());
        assertNotNull(m);
        assertNotNull(m.getMetricDefinitionFactory());
    }

    @Test
    void isOtelPublishingEnabled_propertyAbsent_defaultsToTrue() {
        System.clearProperty(ParameterConstants.OTEL_METRICS_ENABLED);
        MetricsManager m = new MetricsManager(OpenTelemetry.noop(), MetricsManager.getServerProperties());
        assertTrue(m.getHostMetricsService().isOtelPublishingEnabled());
    }

    @Test
    void isOtelPublishingEnabled_propertySetToFalse_returnsFalse() {
        System.setProperty(ParameterConstants.OTEL_METRICS_ENABLED, "false");
        try {
            MetricsManager m = new MetricsManager(OpenTelemetry.noop(), MetricsManager.getServerProperties());
            assertFalse(m.getHostMetricsService().isOtelPublishingEnabled());
        } finally {
            System.clearProperty(ParameterConstants.OTEL_METRICS_ENABLED);
        }
    }

    @Test
    void isOtelPublishingEnabled_propertySetToTrue_returnsTrue() {
        System.setProperty(ParameterConstants.OTEL_METRICS_ENABLED, "true");
        try {
            MetricsManager m = new MetricsManager(OpenTelemetry.noop(), MetricsManager.getServerProperties());
            assertTrue(m.getHostMetricsService().isOtelPublishingEnabled());
        } finally {
            System.clearProperty(ParameterConstants.OTEL_METRICS_ENABLED);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void getGlobalInstance_whenAlreadySet_returnsCachedInstance() throws Exception {
        Field f = MetricsManager.class.getDeclaredField("globalInstance");
        f.setAccessible(true);
        AtomicReference<MetricsManager> ref = (AtomicReference<MetricsManager>) f.get(null);
        MetricsManager prev = ref.get();
        MetricsManager preloaded = TestMetricsManagerFactory.create();
        ref.set(preloaded);
        try {
            assertSame(preloaded, MetricsManager.getGlobalInstance());
            assertSame(preloaded, MetricsManager.getGlobalInstance()); // cached — same every call
        } finally {
            ref.set(prev);
        }
    }

    @Test
    void getOpenTelemetry_returnsNonNull() {
        assertNotNull(manager.getOpenTelemetry());
    }

    @Test
    void getOpenTelemetry_afterShutdown_returnsNull() {
        manager.shutdown();
        assertNull(manager.getOpenTelemetry());
    }

    @Test
    void getOtelMeter_returnsNonNull() {
        assertNotNull(manager.getOtelMeter());
    }

    @Test
    void getOtelMeter_afterShutdown_returnsNull() {
        manager.shutdown();
        assertNull(manager.getOtelMeter());
    }

    @Test
    void createDoubleGauge_returnsNonNull() {
        assertNotNull(manager.createDoubleGauge("t.dg", "desc", "rows", () -> 1.0, Attributes.empty()));
    }

    @Test
    void createObservableDoubleGauge_returnsNonNull() {
        assertNotNull(manager.createObservableDoubleGauge("t.odg", "desc", "rows", () -> 1.0));
    }

    @Test
    void createLongGauge_withAttributes_returnsNonNull() {
        assertNotNull(manager.createLongGauge("t.lg", "desc", "rows", () -> 1L, Attributes.empty()));
    }

    @Test
    void createObservableLongGauge_returnsNonNull() {
        assertNotNull(manager.createObservableLongGauge("t.olg", "desc", "rows", () -> 1L));
    }

    @Test
    void createIncreasingCounter_returnsNonNull() {
        assertNotNull(manager.createIncreasingCounter("t.ic", "desc", "rows", () -> 1L, Attributes.empty()));
    }

    @Test
    void createUpDownCounter_returnsNonNull() {
        assertNotNull(manager.createUpDownCounter("t.udc", "desc", "rows", () -> 1L, Attributes.empty()));
    }

    @Test
    void createHistogram_returnsNonNull() {
        assertNotNull(manager.createHistogram("t.hist", "desc", "ms"));
    }

    @Test
    void startAggregation_createsAggregator() {
        manager.startAggregation();
        try {
            assertNotNull(manager.getAggregator());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void startAggregation_calledTwice_returnsSameAggregatorInstance() {
        manager.startAggregation();
        try {
            IPrimaryMetricAggregator first = manager.getAggregator();
            manager.startAggregation();
            assertSame(first, manager.getAggregator());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void shutdown_withActiveAggregator_stopsItAndSetsNull() {
        manager.startAggregation();
        assertNotNull(manager.getAggregator());
        manager.shutdown();
        assertNull(manager.getAggregator());
    }

    @Test
    void shutdown_withHostMetricsServiceInitialized_doesNotThrow() {
        manager.getHostMetricsService();
        assertDoesNotThrow(() -> manager.shutdown());
    }

    @Test
    void unregister_serviceNotRegistered_isNoOp() {
        IEngineMetricsService svc = mock(IEngineMetricsService.class);
        assertDoesNotThrow(() -> manager.unregister(svc));
        assertTrue(manager.getEngineMetricsServices().isEmpty());
    }

    @Test
    void isOtelPublishingEnabled_uppercaseTrueProperty_returnsTrue() {
        System.setProperty(ParameterConstants.OTEL_METRICS_ENABLED, "TRUE");
        try {
            MetricsManager m = new MetricsManager(OpenTelemetry.noop(), MetricsManager.getServerProperties());
            assertTrue(m.getHostMetricsService().isOtelPublishingEnabled());
        } finally {
            System.clearProperty(ParameterConstants.OTEL_METRICS_ENABLED);
        }
    }
}
