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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.ChannelDataCreateTimeRange;
import org.jumpmind.symmetric.model.ChannelDataUnroutedCount;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RefreshUnroutedDataMetricsJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IRouterService routerService;
    private IConfigurationService configurationService;
    private IStatisticManager statisticManager;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        routerService = mock(IRouterService.class);
        configurationService = mock(IConfigurationService.class);
        statisticManager = mock(IStatisticManager.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        when(engine.getRouterService()).thenReturn(routerService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(routerService.findUnroutedDataCreateTimeRangeByChannel()).thenReturn(Collections.emptyList());
    }

    private RefreshUnroutedDataMetricsJob newJob() {
        return new RefreshUnroutedDataMetricsJob(engine, taskScheduler);
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

    @Test
    void doJob_collectStatsUnroutedDisabled_noSetDataUnRoutedCalls() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_COLLECT_STATS_UNROUTED)).thenReturn(false);
        newJob().doJob(false);
        verify(routerService, never()).findUnroutedDataCountByChannel();
        verify(statisticManager, never()).setDataUnRouted(any(), anyLong());
    }

    @Test
    void doJob_collectStatsUnroutedEnabled_setsRealCountAndZeroesAbsentChannels() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_COLLECT_STATS_UNROUTED)).thenReturn(true);
        when(configurationService.getNodeChannels(false)).thenReturn(
                List.of(new NodeChannel("chan1"), new NodeChannel("chan2")));
        when(routerService.findUnroutedDataCountByChannel())
                .thenReturn(List.of(new ChannelDataUnroutedCount("chan1", 7L)));
        newJob().doJob(false);
        verify(statisticManager).setDataUnRouted("chan1", 7L);
        verify(statisticManager).setDataUnRouted("chan2", 0L);
    }

    @Test
    void doJob_collectStatsUnroutedEnabled_emptyResult_zeroesAllConfiguredChannels() throws Exception {
        when(parameterService.is(ParameterConstants.ROUTING_COLLECT_STATS_UNROUTED)).thenReturn(true);
        when(configurationService.getNodeChannels(false)).thenReturn(
                List.of(new NodeChannel("chan1"), new NodeChannel("chan2")));
        when(routerService.findUnroutedDataCountByChannel()).thenReturn(Collections.emptyList());
        newJob().doJob(false);
        verify(statisticManager).setDataUnRouted("chan1", 0L);
        verify(statisticManager).setDataUnRouted("chan2", 0L);
    }
}
