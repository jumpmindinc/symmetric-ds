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
package org.jumpmind.symmetric.db;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SoftwareUpgradeListenerTest {
    private SoftwareUpgradeListener listener;
    private IParameterService parameterService;
    private INodeService nodeService;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        listener = new SoftwareUpgradeListener();
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        nodeService = mock(INodeService.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSqlTemplate()).thenReturn(sqlTemplate);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        listener.setSymmetricEngine(engine);
    }

    private void upgrade(String databaseVersion, String softwareVersion) {
        ModuleManager mockModuleManager = mock(ModuleManager.class);
        try (MockedStatic<ModuleManager> mockedStatic = mockStatic(ModuleManager.class)) {
            mockedStatic.when(ModuleManager::getInstance).thenReturn(mockModuleManager);
            listener.upgrade(databaseVersion, softwareVersion);
        }
    }

    @Test
    void testUpgrade_priorTo3_18_doesNotSaveClusterLockingEnabledToDatabase() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        upgrade("3.17.0", "3.18.0");
        verify(parameterService, never()).saveParameter(eq(ParameterConstants.CLUSTER_LOCKING_ENABLED), any(), anyString());
        verify(parameterService, never()).saveParameter(anyString(), anyString(), eq(ParameterConstants.CLUSTER_LOCKING_ENABLED), any(), anyString());
    }

    @Test
    void testUpgrade_beforeVersion3_13_deletesNodeHostWhenClusterLockingWasEnabled() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        when(nodeService.findIdentityNodeId()).thenReturn("test-node");
        upgrade("3.12.0", "3.18.0");
        verify(nodeService).deleteNodeHost("test-node");
    }

    @Test
    void testUpgrade_at3_8_0_updatesChannelBatchSize() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(false);
        upgrade("3.8.0", "3.18.0");
        verify(sqlTemplate).update(anyString());
    }
}
