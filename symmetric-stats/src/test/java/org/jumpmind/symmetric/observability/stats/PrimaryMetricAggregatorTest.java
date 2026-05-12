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
package org.jumpmind.symmetric.observability.stats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.metrics.MetricsManager;
import org.jumpmind.symmetric.observability.metrics.TestMetricsManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the lifecycle and direct methods of {@link PrimaryMetricAggregator}.
 * <p>
 * The aggregator holds a static {@code AtomicReference<Thread>}, so each test must stop the aggregator in {@code @AfterEach} and wait for the daemon thread to
 * exit before the next test starts — otherwise {@code isRunning()} would reflect the previous test's thread state.
 */
class PrimaryMetricAggregatorTest {
    private PrimaryMetricAggregator aggregator;
    private MetricsManager metricsManager;

    @BeforeEach
    void setUp() {
        metricsManager = TestMetricsManagerFactory.create();
        aggregator = new PrimaryMetricAggregator(metricsManager, "test-host");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        aggregator.stop();
        aggregator.awaitStop(2000);
    }

    @Test
    void isRunning_beforeStart_returnsFalse() {
        assertFalse(aggregator.isRunning());
    }

    @Test
    void isRunning_afterStart_returnsTrue() {
        aggregator.start();
        assertTrue(aggregator.isRunning());
    }

    @Test
    void start_calledTwice_isNoOp_stillRunning() {
        aggregator.start();
        aggregator.start(); // second call must be ignored
        assertTrue(aggregator.isRunning());
    }

    @Test
    void stop_whenNotRunning_doesNotThrow() {
        assertDoesNotThrow(() -> aggregator.stop());
    }

    @Test
    void stop_afterStart_eventuallyStopsRunning() throws InterruptedException {
        aggregator.start();
        aggregator.stop();
        aggregator.awaitStop(2000);
        assertFalse(aggregator.isRunning());
    }
    // ── processAll / closeAll ─────────────────────────────────────────────────

    @Test
    void processAll_withEmptyMetricsManager_doesNotThrow() {
        assertDoesNotThrow(() -> aggregator.processAllMetrics());
    }

    @Test
    void closeAll_withEmptyMetricsManager_doesNotThrow() {
        assertDoesNotThrow(() -> aggregator.closeAllMetrics());
    }
    // ── processAll with a registered service ──────────────────────────────────

    @Test
    void processAll_withRegisteredService_callsMetricProcessing() {
        IEngineMetricsService svc = mock(IEngineMetricsService.class);
        when(svc.getEngineName()).thenReturn("test-engine");
        when(svc.getAllMetrics()).thenReturn(List.of());
        metricsManager.register(svc);
        aggregator.processAllMetrics();
        verify(svc).getAllMetrics();
        verify(svc).saveCompletedIntervalStats();
    }
    // ── closeAll with a registered service ───────────────────────────────────

    @Test
    void closeAll_withRegisteredService_callsShutdown() {
        IEngineMetricsService svc = mock(IEngineMetricsService.class);
        when(svc.getEngineName()).thenReturn("test-engine");
        metricsManager.register(svc);
        aggregator.closeAllMetrics();
        verify(svc).shutdown();
    }
}
