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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RefreshCacheJobTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ITriggerRouterService triggerRouterService;
    private IGroupletService groupletService;
    private IConfigurationService configurationService;
    private ITransformService transformService;
    private IDataLoaderService dataLoaderService;
    private ILoadFilterService loadFilterService;
    private IFileSyncService fileSyncService;
    private ThreadPoolTaskScheduler taskScheduler;
    private RefreshCacheJob job;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        triggerRouterService = mock(ITriggerRouterService.class);
        groupletService = mock(IGroupletService.class);
        configurationService = mock(IConfigurationService.class);
        transformService = mock(ITransformService.class);
        dataLoaderService = mock(IDataLoaderService.class);
        loadFilterService = mock(ILoadFilterService.class);
        fileSyncService = mock(IFileSyncService.class);
        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getTransformService()).thenReturn(transformService);
        when(engine.getDataLoaderService()).thenReturn(dataLoaderService);
        when(engine.getLoadFilterService()).thenReturn(loadFilterService);
        when(engine.getFileSyncService()).thenReturn(fileSyncService);
        job = new RefreshCacheJob(engine, taskScheduler);
    }

    @Test
    void testGetDefaults() {
        assertNotNull(job.getDefaults());
    }

    @Test
    void testDoJob_refreshesAllCaches() throws Exception {
        job.doJob(false);
        verify(parameterService).refreshFromDatabase();
        verify(triggerRouterService).refreshFromDatabase();
        verify(groupletService).refreshFromDatabase();
        verify(configurationService).refreshFromDatabase();
        verify(transformService).refreshFromDatabase();
        verify(dataLoaderService).refreshFromDatabase();
        verify(loadFilterService).refreshFromDatabase();
        verify(fileSyncService).refreshFromDatabase();
    }
}
