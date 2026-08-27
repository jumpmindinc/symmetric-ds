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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.ChannelDataCreateTimeRange;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RefreshDataCreateTimeMetricsJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IRouterService routerService;
    private IStatisticManager statisticManager;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        routerService = mock(IRouterService.class);
        statisticManager = mock(IStatisticManager.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        when(engine.getRouterService()).thenReturn(routerService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
    }

    private RefreshDataCreateTimeMetricsJob newJob() {
        return new RefreshDataCreateTimeMetricsJob(engine, taskScheduler);
    }

    @Test
    void getDefaults_returnsNonNull() {
        assertNotNull(newJob().getDefaults());
    }

    @Test
    void isRateLimited_returnsTrue() {
        assertTrue(newJob().isRateLimited());
    }

    @Test
    void getMinSchedulePeriodMs_matchesEveryFifteenMinutes() {
        long expected = Long.parseLong(JobDefaults.EVERY_FIFTEEN_MINUTES);
        assertEquals(expected, newJob().getMinSchedulePeriodMs());
    }

    @Test
    void doJob_callsFindUnroutedDataCreateTimeRangeByChannel() {
        when(routerService.findUnroutedDataCreateTimeRangeByChannel()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> newJob().doJob(false));
        verify(routerService).findUnroutedDataCreateTimeRangeByChannel();
    }

    @Test
    void doJob_emptyList_noStatisticManagerCallsMade() throws Exception {
        when(routerService.findUnroutedDataCreateTimeRangeByChannel()).thenReturn(Collections.emptyList());
        newJob().doJob(false);
        verify(statisticManager, never()).setDataUnroutedMinCreateTime(any(), any());
        verify(statisticManager, never()).setDataUnroutedMaxCreateTime(any(), any());
    }

    @Test
    void doJob_singleChannelRange_setsMinAndMaxCreateTime() throws Exception {
        Date minTime = new Date(1000L);
        Date maxTime = new Date(2000L);
        when(routerService.findUnroutedDataCreateTimeRangeByChannel())
                .thenReturn(List.of(new ChannelDataCreateTimeRange("chan1", minTime, maxTime)));
        newJob().doJob(false);
        verify(statisticManager).setDataUnroutedMinCreateTime("chan1", minTime);
        verify(statisticManager).setDataUnroutedMaxCreateTime("chan1", maxTime);
    }

    @Test
    void doJob_multipleChannelRanges_setsEachChannel() throws Exception {
        Date minTime1 = new Date(1000L);
        Date maxTime1 = new Date(2000L);
        Date minTime2 = new Date(3000L);
        Date maxTime2 = new Date(4000L);
        when(routerService.findUnroutedDataCreateTimeRangeByChannel())
                .thenReturn(List.of(
                        new ChannelDataCreateTimeRange("chan1", minTime1, maxTime1),
                        new ChannelDataCreateTimeRange("chan2", minTime2, maxTime2)));
        newJob().doJob(false);
        verify(statisticManager).setDataUnroutedMinCreateTime("chan1", minTime1);
        verify(statisticManager).setDataUnroutedMaxCreateTime("chan1", maxTime1);
        verify(statisticManager).setDataUnroutedMinCreateTime("chan2", minTime2);
        verify(statisticManager).setDataUnroutedMaxCreateTime("chan2", maxTime2);
    }
}
