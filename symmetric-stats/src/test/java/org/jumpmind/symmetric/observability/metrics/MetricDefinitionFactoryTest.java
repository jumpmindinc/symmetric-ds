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
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_BATCHES_OUTGOING;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_CREATE_TIME_MIN;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_ROUTED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_UNROUTED_CHANNEL;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_RUNTIME_DBPOOL_IDLE;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_UTILIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.InvalidMetricDataException;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.repository.MetricsRepository;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricDefinitionFactoryTest {
    private MetricDefinitionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MetricDefinitionFactory();
    }

    @Test
    void getDefinition_knownMetricId_returnsDefinition() {
        SymMetricDefinition def = factory.getDefinition(METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS);
        assertNotNull(def);
        assertEquals(METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS, def.id());
    }

    @Test
    void getDefinition_channelScopedMetricId_returnsDefinition() {
        SymMetricDefinition def = factory.getDefinition(METRIC_ID_DATA_ROUTED);
        assertNotNull(def);
        assertEquals(METRIC_ID_DATA_ROUTED, def.id());
    }

    @Test
    void getDefinition_unknownMetricId_throwsInvalidMetricDataException() {
        assertThrows(InvalidMetricDataException.class, () -> factory.getDefinition("no.such.metric"));
    }

    @Test
    void register_null_throwsInvalidMetricDataException() {
        assertThrows(InvalidMetricDataException.class, () -> factory.register(null));
    }

    @Test
    void register_customDefinition_retrievableByGetDefinition() {
        SymMetricDefinition custom = new SymMetricDefinition("custom.metric", "Custom desc", "units", InstrumentType.UPDOWN_COUNTER);
        factory.register(custom);
        assertEquals("custom.metric", factory.getDefinition("custom.metric").id());
    }

    @Test
    void registerDefaultMetric_addsToRegistryAndDefaultList() {
        int sizeBefore = factory.getDefaultMetrics().size();
        SymMetricDefinition extra = new SymMetricDefinition("extra.metric", "Extra", "{row}", InstrumentType.COUNTER);
        factory.registerDefaultMetric(extra);
        assertEquals(sizeBefore + 1, factory.getDefaultMetrics().size());
        assertEquals("extra.metric", factory.getDefinition("extra.metric").id());
    }

    @Test
    void registerDefaultMetric_nullArgs_isNoOp() {
        int sizeBefore = factory.getDefaultMetrics().size();
        factory.registerDefaultMetric((SymMetricDefinition[]) null);
        assertEquals(sizeBefore, factory.getDefaultMetrics().size());
    }

    @Test
    void registerDefaultMetric_emptyArgs_isNoOp() {
        int sizeBefore = factory.getDefaultMetrics().size();
        factory.registerDefaultMetric();
        assertEquals(sizeBefore, factory.getDefaultMetrics().size());
    }

    @Test
    void getDefaultMetrics_isNotEmpty() {
        assertFalse(factory.getDefaultMetrics().isEmpty());
    }

    @Test
    void getDefaultMetrics_returnsUnmodifiableList() {
        List<SymMetricDefinition> metrics = factory.getDefaultMetrics();
        SymMetricDefinition testMetricDefinition = new SymMetricDefinition("x", "x", "x", InstrumentType.COUNTER);
        assertThrows(UnsupportedOperationException.class, () -> metrics.add(testMetricDefinition));
    }

    @Test
    void allDefaultMetricIds_areRetrievableByGetDefinition() {
        for (SymMetricDefinition def : factory.getDefaultMetrics()) {
            SymMetricDefinition retrieved = factory.getDefinition(def.id());
            assertEquals(def.id(), retrieved.id());
        }
    }

    @Test
    void getDefaultContexts_isNotEmpty() {
        assertFalse(factory.getDefaultContexts().isEmpty());
    }

    @Test
    void getDefaultContexts_returnsUnmodifiableList() {
        List<ContextDefinition> contexts = factory.getDefaultContexts();
        ContextDefinition testContextDefinition = new ContextDefinition(1L, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES));
        assertThrows(UnsupportedOperationException.class, () -> contexts.add(testContextDefinition));
    }

    @Test
    void registerDefaultContext_addsToDefaultContextList() {
        int sizeBefore = factory.getDefaultContexts().size();
        factory.registerDefaultContext(new ContextDefinition(99_999_999_999L, new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES)));
        assertEquals(sizeBefore + 1, factory.getDefaultContexts().size());
    }

    @Test
    void registerDefaultContext_nullArgs_isNoOp() {
        int sizeBefore = factory.getDefaultContexts().size();
        factory.registerDefaultContext((ContextDefinition[]) null);
        assertEquals(sizeBefore, factory.getDefaultContexts().size());
    }

    @Test
    void registerDefaultContext_emptyArgs_isNoOp() {
        int sizeBefore = factory.getDefaultContexts().size();
        factory.registerDefaultContext();
        assertEquals(sizeBefore, factory.getDefaultContexts().size());
    }

    @Test
    void registerDefaultMetric_nullElementInArray_skipsNull() {
        int sizeBefore = factory.getDefaultMetrics().size();
        SymMetricDefinition valid = new SymMetricDefinition("m.valid", "desc", "{row}", InstrumentType.COUNTER);
        factory.registerDefaultMetric(valid, null);
        assertEquals(sizeBefore + 1, factory.getDefaultMetrics().size());
        assertNotNull(factory.getDefinition("m.valid"));
    }

    @Test
    void initializeMetrics_registersMetricsOnService() {
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        int count = factory.initializeMetrics(service);
        assertTrue(count > 0);
        assertFalse(service.getAllMetrics().isEmpty());
    }

    @Test
    void initializeMetrics_histogramType_isSkippedGracefully() {
        // HISTOGRAM type hits the "default ->" switch case (log warn + continue); no exception.
        factory.register(new SymMetricDefinition("test.histogram", "hist", "ms", InstrumentType.HISTOGRAM));
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        int count = factory.initializeMetrics(service);
        assertTrue(count > 0); // other (non-histogram) metrics still registered
    }

    @Test
    void initializeMetrics_updownCounterNonChannel_isRegisteredOnService() {
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        // METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS is a non-channel UPDOWN_COUNTER
        assertNotNull(service.getUpDownCounter(METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS));
    }

    @Test
    void initializeMetrics_doubleGaugeNonChannel_isRegisteredOnService() {
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        // METRIC_ID_SERVER_CONNECTIONS_UTILIZATION is a non-channel DOUBLE_GAUGE
        assertNotNull(service.getDoubleGauge(METRIC_ID_SERVER_CONNECTIONS_UTILIZATION));
    }

    @Test
    void initializeMetrics_longGaugeNonChannel_isRegisteredOnService() {
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        // METRIC_ID_RUNTIME_DBPOOL_IDLE is a non-channel LONG_GAUGE
        assertNotNull(service.getLongGauge(METRIC_ID_RUNTIME_DBPOOL_IDLE));
    }

    @Test
    void initializeMetrics_counterTypeNonChannel_isRegisteredOnService() {
        // Register a COUNTER (monotonic) metric to hit the COUNTER case in the switch
        factory.registerDefaultMetric(new SymMetricDefinition("test.nc.counter", "test", "{row}", InstrumentType.COUNTER));
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        assertNotNull(service.getIncreasingCounter("test.nc.counter"));
    }

    @Test
    void initializeMetrics_nodeScopedMetric_isNotRegisteredOnService() {
        // METRIC_ID_BATCHES_OUTGOING is node-scoped; both sub-methods skip it
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        assertNull(service.getLongGauge(METRIC_ID_BATCHES_OUTGOING));
    }

    @Test
    void initializeMetrics_updownCounterChannel_registeredPerChannel() {
        // METRIC_ID_DATA_ROUTED is a channel-scoped UPDOWN_COUNTER
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        MetricAttributeList defaultAttrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "default"));
        assertNotNull(service.getUpDownCounter(METRIC_ID_DATA_ROUTED, defaultAttrs));
    }

    @Test
    void initializeMetrics_longGaugeChannel_registeredPerChannel() {
        // METRIC_ID_DATA_CREATE_TIME_MIN is a channel-scoped LONG_GAUGE
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        MetricAttributeList configAttrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "config"));
        assertNotNull(service.getLongGauge(METRIC_ID_DATA_CREATE_TIME_MIN, configAttrs));
    }

    @Test
    void initializeMetrics_doubleGaugeChannel_registeredPerChannel() {
        // METRIC_ID_DATA_UNROUTED_CHANNEL is a channel-scoped DOUBLE_GAUGE
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        MetricAttributeList reloadAttrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "reload"));
        assertNotNull(service.getDoubleGauge(METRIC_ID_DATA_UNROUTED_CHANNEL, reloadAttrs));
    }

    @Test
    void initializeMetrics_channelMetric_registeredForMultipleChannels() {
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        // Both "default" and "heartbeat" channels should have DATA_ROUTED registered
        MetricAttributeList heartbeatAttrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "heartbeat"));
        assertNotNull(service.getUpDownCounter(METRIC_ID_DATA_ROUTED, heartbeatAttrs));
    }

    @Test
    void initializeMetrics_channelMetric_notRegisteredWithoutChannelAttr() {
        // Without channel attribute the unscoped key should be absent (only channel-scoped versions exist)
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        assertNull(service.getUpDownCounter(METRIC_ID_DATA_ROUTED));
    }

    @Test
    void initializeMetrics_channelMetric_customChannelNotPreRegistered() {
        MetricsManager svcManager = TestMetricsManagerFactory.create();
        HostMetricsService service = new HostMetricsService(svcManager, false);
        factory.initializeMetrics(service);
        MetricAttributeList customChannelAttrs = MetricAttributeList.of(new MetricAttribute(CHANNEL, "my_custom_channel"));
        assertNull(service.getUpDownCounter(METRIC_ID_DATA_ROUTED, customChannelAttrs));
        assertNotNull(service.registerUpDownCounter(METRIC_ID_DATA_ROUTED, customChannelAttrs));
        assertNotNull(service.getUpDownCounter(METRIC_ID_DATA_ROUTED, customChannelAttrs));
    }
}
