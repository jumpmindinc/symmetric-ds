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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.IOfflinePushService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class OfflinePushJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IOfflinePushService offlinePushService;
    private ThreadPoolTaskScheduler taskScheduler;
    private OfflinePushJob job;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        offlinePushService = mock(IOfflinePushService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        when(engine.getOfflinePushService()).thenReturn(offlinePushService);
        job = new OfflinePushJob(engine, taskScheduler);
    }

    @Test
    void testGetDefaults() {
        JobDefaults defaults = job.getDefaults();
        assertNotNull(defaults);
        assertFalse(defaults.isEnabled());
    }

    @Test
    void testDoJob_withEngine_callsPushDataAndReadsProcessedCount() throws Exception {
        RemoteNodeStatuses statuses = mock(RemoteNodeStatuses.class);
        when(offlinePushService.pushData(true)).thenReturn(statuses);
        job.doJob(true);
        verify(offlinePushService).pushData(true);
        verify(statuses).getDataProcessedCount();
    }

    @Test
    void testDoJob_withNullEngine_doesNothing() {
        job.setEngine(null);
        assertDoesNotThrow(() -> job.doJob(false));
    }
}
