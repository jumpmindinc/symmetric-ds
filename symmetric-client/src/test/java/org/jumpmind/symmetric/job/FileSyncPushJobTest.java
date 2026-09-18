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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class FileSyncPushJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IFileSyncService fileSyncService;
    private ThreadPoolTaskScheduler taskScheduler;
    private FileSyncPushJob fileSyncPushJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        fileSyncService = mock(IFileSyncService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getFileSyncService()).thenReturn(fileSyncService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        fileSyncPushJob = new FileSyncPushJob(engine, taskScheduler);
    }

    @Test
    void testDoJob_callsPushFilesToNodes() throws Exception {
        fileSyncPushJob.doJob(true);
        verify(fileSyncService).pushFilesToNodes(true);
    }

    @Test
    void testDoJob_withForceFalse() throws Exception {
        fileSyncPushJob.doJob(false);
        verify(fileSyncService).pushFilesToNodes(false);
    }

    @Test
    void testGetDefaults_whenFileSyncEnabled() {
        when(parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)).thenReturn(true);
        assertTrue(fileSyncPushJob.getDefaults().isEnabled());
    }

    @Test
    void testGetDefaults_whenFileSyncDisabled() {
        when(parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)).thenReturn(false);
        assertFalse(fileSyncPushJob.getDefaults().isEnabled());
    }
}
