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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IOfflinePullService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class OfflinePullJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IOfflinePullService offlinePullService;
    private ThreadPoolTaskScheduler taskScheduler;
    private OfflinePullJob job;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        offlinePullService = mock(IOfflinePullService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        when(engine.getOfflinePullService()).thenReturn(offlinePullService);
        job = new OfflinePullJob(engine, taskScheduler);
    }

    @Test
    void testGetDefaults() {
        JobDefaults defaults = job.getDefaults();
        assertNotNull(defaults);
        assertFalse(defaults.isRequiresRegisteration());
        assertFalse(defaults.isEnabled());
    }

    @Test
    void testDoJob_withForceTrue() throws Exception {
        job.doJob(true);
        verify(offlinePullService).pullData(true);
    }

    @Test
    void testDoJob_withForceFalse() throws Exception {
        job.doJob(false);
        verify(offlinePullService).pullData(false);
        verify(offlinePullService, never()).pullData(true);
    }
}
