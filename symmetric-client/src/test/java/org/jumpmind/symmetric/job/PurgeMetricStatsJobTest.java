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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class PurgeMetricStatsJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
    }

    private PurgeMetricStatsJob newJob() {
        return new PurgeMetricStatsJob(engine, taskScheduler);
    }
    // ── getDefaults ───────────────────────────────────────────────────────────

    @Test
    void getDefaults_returnsNonNull() {
        assertNotNull(newJob().getDefaults());
    }
    // ── doJob ─────────────────────────────────────────────────────────────────

    @Test
    void doJob_nullMetricsService_doesNotThrow() {
        when(engine.getMetricsService()).thenReturn(null);
        assertDoesNotThrow(() -> newJob().doJob(true));
    }

    @Test
    void doJob_withMetricsService_callsPurgeMetricStats() throws Exception {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        when(engine.getMetricsService()).thenReturn(metricsService);
        newJob().doJob(true);
        verify(metricsService).purgeMetricStats(true);
    }
}
