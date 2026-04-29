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

import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_DATA_ROUTED;
import static org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.InvalidMetricDataException;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricDefinitionFactoryTest {
    private MetricDefinitionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MetricDefinitionFactory();
    }
    // ── getDefinition ─────────────────────────────────────────────────────────

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
    // ── register ─────────────────────────────────────────────────────────────

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
    // ── registerDefaultMetric ─────────────────────────────────────────────────

    @Test
    void registerDefaultMetric_addsToRegistryAndDefaultList() {
        int sizeBefore = factory.getDefaultMetrics().size();
        SymMetricDefinition extra = new SymMetricDefinition("extra.metric", "Extra", "rows", InstrumentType.COUNTER);
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
    // ── getDefaultMetrics ─────────────────────────────────────────────────────

    @Test
    void getDefaultMetrics_isNotEmpty() {
        assertFalse(factory.getDefaultMetrics().isEmpty());
    }

    @Test
    void getDefaultMetrics_returnsUnmodifiableList() {
        List<SymMetricDefinition> metrics = factory.getDefaultMetrics();
        assertThrows(UnsupportedOperationException.class,
                () -> metrics.add(new SymMetricDefinition("x", "x", "x", InstrumentType.COUNTER)));
    }

    @Test
    void allDefaultMetricIds_areRetrievableByGetDefinition() {
        for (SymMetricDefinition def : factory.getDefaultMetrics()) {
            SymMetricDefinition retrieved = factory.getDefinition(def.id());
            assertEquals(def.id(), retrieved.id());
        }
    }
    // ── getDefaultContexts / registerDefaultContext ───────────────────────────

    @Test
    void getDefaultContexts_isNotEmpty() {
        assertFalse(factory.getDefaultContexts().isEmpty());
    }

    @Test
    void getDefaultContexts_returnsUnmodifiableList() {
        List<ContextDefinition> contexts = factory.getDefaultContexts();
        assertThrows(UnsupportedOperationException.class,
                () -> contexts.add(new ContextDefinition(1L, List.of())));
    }

    @Test
    void registerDefaultContext_addsToDefaultContextList() {
        int sizeBefore = factory.getDefaultContexts().size();
        factory.registerDefaultContext(new ContextDefinition(99_999_999_999L, List.of()));
        assertEquals(sizeBefore + 1, factory.getDefaultContexts().size());
    }

    @Test
    void registerDefaultContext_nullArgs_isNoOp() {
        int sizeBefore = factory.getDefaultContexts().size();
        factory.registerDefaultContext((ContextDefinition[]) null);
        assertEquals(sizeBefore, factory.getDefaultContexts().size());
    }
}
