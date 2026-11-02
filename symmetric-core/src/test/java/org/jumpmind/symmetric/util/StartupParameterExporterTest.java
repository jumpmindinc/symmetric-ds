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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Properties;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.model.StartupParameter;
import org.jumpmind.symmetric.model.StartupParameter.Source;
import org.jumpmind.symmetric.model.StartupParameter.Type;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.junit.jupiter.api.Test;

class StartupParameterExporterTest {
    private final IStartupParameterService startupParameterService = mock(IStartupParameterService.class);
    private final Properties targetProperties = new Properties();
    private final StartupParameterExporter exporter = new StartupParameterExporter(targetProperties);

    @Test
    void exportMissing_valueFromServerProperties_isCopiedToTarget() {
        when(startupParameterService.getGlobalParameter(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(new StartupParameter(
                IStartupParameterService.GLOBAL_ENGINE_NAME, ParameterConstants.CLUSTER_LOCKING_ENABLED, Type.BOOLEAN, "true", "false",
                Source.SYMMETRIC_SERVER_PROPERTIES, false));
        exporter.exportMissing(startupParameterService, List.of(ParameterConstants.CLUSTER_LOCKING_ENABLED));
        assertEquals("true", targetProperties.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void exportMissing_targetAlreadySet_keepsTargetValue() {
        targetProperties.setProperty(ServerConstants.CLUSTER_SERVER_ID, "from-jvm");
        when(startupParameterService.getGlobalParameter(ServerConstants.CLUSTER_SERVER_ID)).thenReturn(new StartupParameter(
                IStartupParameterService.GLOBAL_ENGINE_NAME, ServerConstants.CLUSTER_SERVER_ID, Type.STRING, "from-server-properties", null,
                Source.SYMMETRIC_SERVER_PROPERTIES, false));
        exporter.exportMissing(startupParameterService, List.of(ServerConstants.CLUSTER_SERVER_ID));
        assertEquals("from-jvm", targetProperties.getProperty(ServerConstants.CLUSTER_SERVER_ID));
    }

    @Test
    void exportMissing_parameterAtDefault_leavesTargetUnset() {
        when(startupParameterService.getGlobalParameter(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(new StartupParameter(
                IStartupParameterService.GLOBAL_ENGINE_NAME, ParameterConstants.CLUSTER_LOCKING_ENABLED, Type.BOOLEAN, null, "false", Source.DEFAULT,
                false));
        exporter.exportMissing(startupParameterService, List.of(ParameterConstants.CLUSTER_LOCKING_ENABLED));
        assertNull(targetProperties.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void exportMissing_blankValue_leavesTargetUnset() {
        when(startupParameterService.getGlobalParameter(ServerConstants.CLUSTER_PARTITION_ID)).thenReturn(new StartupParameter(
                IStartupParameterService.GLOBAL_ENGINE_NAME, ServerConstants.CLUSTER_PARTITION_ID, Type.STRING, "", null,
                Source.SYMMETRIC_SERVER_PROPERTIES, false));
        exporter.exportMissing(startupParameterService, List.of(ServerConstants.CLUSTER_PARTITION_ID));
        assertNull(targetProperties.getProperty(ServerConstants.CLUSTER_PARTITION_ID));
    }

    @Test
    void exportMissing_globalNotRegistered_leavesTargetUnset() {
        exporter.exportMissing(startupParameterService, List.of(ServerConstants.CLUSTER_SERVER_ID));
        assertNull(targetProperties.getProperty(ServerConstants.CLUSTER_SERVER_ID));
    }
}
