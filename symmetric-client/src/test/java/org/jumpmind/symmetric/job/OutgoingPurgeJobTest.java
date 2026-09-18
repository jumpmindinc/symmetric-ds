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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class OutgoingPurgeJobTest {
    private ISymmetricEngine engine;
    private IPurgeService purgeService;
    private IParameterService parameterService;
    private ThreadPoolTaskScheduler taskScheduler;
    private OutgoingPurgeJob outgoingPurgeJob;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        purgeService = mock(IPurgeService.class);
        parameterService = mock(IParameterService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getPurgeService()).thenReturn(purgeService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getExternalId()).thenReturn("test-node");
        when(parameterService.getInt(anyString())).thenReturn(10000);
        outgoingPurgeJob = new OutgoingPurgeJob(engine, taskScheduler);
    }

    @Test
    void testDoJob_setsProcessedCount() throws Exception {
        when(purgeService.purgeOutgoing(false)).thenReturn(42L);
        outgoingPurgeJob.doJob(false);
        verify(purgeService).purgeOutgoing(false);
        assertEquals(42L, outgoingPurgeJob.getProcessedCount());
    }

    @Test
    void testDoJob_forced() throws Exception {
        when(purgeService.purgeOutgoing(true)).thenReturn(7L);
        outgoingPurgeJob.doJob(true);
        verify(purgeService).purgeOutgoing(true);
        assertEquals(7L, outgoingPurgeJob.getProcessedCount());
    }

    @Test
    void testGetDeprecatedStartParameter() {
        assertEquals(ParameterConstants.START_PURGE_JOB_38, outgoingPurgeJob.getDeprecatedStartParameter());
    }
}
