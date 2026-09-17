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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.ILogMinerService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class LogMinerJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IExtensionService extensionService;
    private ThreadPoolTaskScheduler taskScheduler;
    private LogMinerJob logMinerJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        extensionService = mock(IExtensionService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        logMinerJob = new LogMinerJob(engine, taskScheduler);
    }

    @Test
    void testDoJob_withNullEngine_doesNothing() {
        logMinerJob.setEngine(null);
        assertDoesNotThrow(() -> logMinerJob.doJob(false));
    }

    @Test
    void testDoJob_withNoLogMinerServiceExtension_doesNothing() throws Exception {
        when(extensionService.getExtensionPoint(ILogMinerService.class)).thenReturn(null);
        assertDoesNotThrow(() -> logMinerJob.doJob(false));
    }

    @Test
    void testDoJob_withLogMinerServiceExtension_callsMineData() throws Exception {
        ILogMinerService logMinerService = mock(ILogMinerService.class);
        when(extensionService.getExtensionPoint(ILogMinerService.class)).thenReturn(logMinerService);
        logMinerJob.doJob(true);
        verify(logMinerService).mineData(false);
    }
}
