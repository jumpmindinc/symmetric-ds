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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class WatchdogJobTest {
    private ISymmetricEngine engine;
    private IClusterService clusterService;
    private INodeService nodeService;
    private IParameterService parameterService;
    private ThreadPoolTaskScheduler taskScheduler;
    private WatchdogJob watchdogJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        clusterService = mock(IClusterService.class);
        nodeService = mock(INodeService.class);
        parameterService = mock(IParameterService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        watchdogJob = new WatchdogJob(engine, taskScheduler);
    }

    @Test
    void testDoJob_lockAcquired() throws Exception {
        when(clusterService.lock(ClusterConstants.WATCHDOG)).thenReturn(true);
        watchdogJob.doJob(false);
        verify(nodeService).checkForOfflineNodes();
        verify(clusterService).unlock(ClusterConstants.WATCHDOG);
    }

    @Test
    void testDoJob_lockNotAcquired() throws Exception {
        when(clusterService.lock(ClusterConstants.WATCHDOG)).thenReturn(false);
        watchdogJob.doJob(false);
        verify(nodeService, never()).checkForOfflineNodes();
        verify(clusterService, never()).unlock(ClusterConstants.WATCHDOG);
    }
}
