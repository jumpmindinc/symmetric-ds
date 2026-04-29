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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsManagerTest {
    private MetricsManager manager;

    @BeforeEach
    void setUp() {
        manager = TestMetricsManagerFactory.create();
    }

    // ── getMetricDefinitionFactory ────────────────────────────────────────────

    @Test
    void getMetricDefinitionFactory_returnsNonNull() {
        assertNotNull(manager.getMetricDefinitionFactory());
    }

    // ── getEngineMetricsServices ──────────────────────────────────────────────

    @Test
    void getEngineMetricsServices_initiallyEmpty() {
        assertTrue(manager.getEngineMetricsServices().isEmpty());
    }

    // ── register / unregister ─────────────────────────────────────────────────

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

    // ── getHostMetricsService ─────────────────────────────────────────────────

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

    // ── getAggregator ─────────────────────────────────────────────────────────

    @Test
    void getAggregator_initiallyNull() {
        assertNull(manager.getAggregator());
    }

    // ── shutdown ──────────────────────────────────────────────────────────────

    @Test
    void shutdown_whenAggregatorIsNull_doesNotThrow() {
        assertDoesNotThrow(() -> manager.shutdown());
    }

    @Test
    void shutdown_whenHostMetricsServiceIsNull_doesNotThrow() {
        // Fresh manager — host metrics service not yet initialized
        MetricsManager freshManager = TestMetricsManagerFactory.create();
        assertDoesNotThrow(() -> freshManager.shutdown());
    }
}
