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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class StageManagementJobTest {
    private ISymmetricEngine engine;
    private IStagingManager stagingManager;
    private IParameterService parameterService;
    private ThreadPoolTaskScheduler taskScheduler;
    private StageManagementJob stageManagementJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        stagingManager = mock(IStagingManager.class);
        parameterService = mock(IParameterService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        stageManagementJob = new StageManagementJob(engine, taskScheduler);
    }

    @Test
    void testDoJob_stagingManagerNull_doesNothing() {
        when(engine.getStagingManager()).thenReturn(null);
        assertDoesNotThrow(() -> stageManagementJob.doJob(false));
        assertEquals(0L, stageManagementJob.getProcessedCount());
    }

    @Test
    void testDoJob_stagingManagerPresent_setsProcessedCount() throws Exception {
        when(engine.getStagingManager()).thenReturn(stagingManager);
        when(parameterService.getLong(ParameterConstants.STREAM_TO_FILE_TIME_TO_LIVE_MS)).thenReturn(60000L);
        when(stagingManager.clean(60000L)).thenReturn(9L);
        stageManagementJob.doJob(false);
        verify(stagingManager).clean(60000L);
        assertEquals(9L, stageManagementJob.getProcessedCount());
    }
}
