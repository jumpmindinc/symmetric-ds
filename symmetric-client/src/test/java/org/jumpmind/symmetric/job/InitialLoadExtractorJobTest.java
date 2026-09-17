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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class InitialLoadExtractorJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IDataExtractorService dataExtractorService;
    private IDataExtractorService fileSyncExtractorService;
    private ThreadPoolTaskScheduler taskScheduler;
    private InitialLoadExtractorJob initialLoadExtractorJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        fileSyncExtractorService = mock(IDataExtractorService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(engine.getFileSyncExtractorService()).thenReturn(fileSyncExtractorService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        initialLoadExtractorJob = new InitialLoadExtractorJob(engine, taskScheduler);
    }

    @Test
    void testDoJob_useExtractJobDisabled_noServicesQueued() throws Exception {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)).thenReturn(false);
        initialLoadExtractorJob.doJob(true);
        verify(dataExtractorService, never()).queueWork(anyBoolean());
        verify(fileSyncExtractorService, never()).queueWork(anyBoolean());
    }

    @Test
    void testDoJob_useExtractJobEnabled_fileSyncDisabled_onlyDataExtractorQueued() throws Exception {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)).thenReturn(true);
        when(parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)).thenReturn(false);
        initialLoadExtractorJob.doJob(true);
        verify(dataExtractorService).queueWork(true);
        verify(fileSyncExtractorService, never()).queueWork(anyBoolean());
    }

    @Test
    void testDoJob_useExtractJobEnabled_fileSyncEnabled_bothQueued() throws Exception {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)).thenReturn(true);
        when(parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)).thenReturn(true);
        initialLoadExtractorJob.doJob(false);
        verify(dataExtractorService).queueWork(false);
        verify(fileSyncExtractorService).queueWork(false);
    }
}
