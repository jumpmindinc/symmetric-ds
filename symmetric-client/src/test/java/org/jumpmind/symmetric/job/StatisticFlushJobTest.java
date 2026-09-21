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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class StatisticFlushJobTest {
    private ISymmetricEngine engine;
    private IStatisticManager statisticManager;
    private IPurgeService purgeService;
    private IParameterService parameterService;
    private ThreadPoolTaskScheduler taskScheduler;
    private StatisticFlushJob job;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        statisticManager = mock(IStatisticManager.class);
        purgeService = mock(IPurgeService.class);
        parameterService = mock(IParameterService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getPurgeService()).thenReturn(purgeService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getLong(org.mockito.ArgumentMatchers.anyString(), anyLong())).thenReturn(60L);
        job = new StatisticFlushJob(engine, taskScheduler);
    }

    @Test
    void testGetDefaults() {
        assertNotNull(job.getDefaults());
    }

    @Test
    void testDoJob_withForceTrue_flushesAndPurges() throws Exception {
        job.doJob(true);
        verify(statisticManager).flush();
        verify(purgeService).purgeStats(true);
    }

    @Test
    void testDoJob_withForceFalse_flushesAndPurges() throws Exception {
        job.doJob(false);
        verify(statisticManager).flush();
        verify(purgeService).purgeStats(false);
    }
}
