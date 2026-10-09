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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.junit.jupiter.api.Test;

class ParameterServiceTest {
    private static final String ENGINE_NAME = "myEngine";
    private static final String SOURCE_KEY = "some.source.key";

    @Test
    void getString_newConstructorOverload_sourcesBaseLayerFromStartupParameterService() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        IStartupParameterService startupParameterService = mock(IStartupParameterService.class);
        TypedProperties startupProperties = new TypedProperties();
        startupProperties.setProperty("some.startup.key", "startup-value");
        when(startupParameterService.asTypedProperties("myEngine")).thenReturn(startupProperties);
        ParameterService parameterService = new ParameterService(startupParameterService, "myEngine", platform, factory, "sym_");
        assertEquals("startup-value", parameterService.getString("some.startup.key"));
    }

    @Test
    void getString_legacyThreeArgConstructor_sourcesBaseLayerFromFactoryReload() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        TypedProperties fileProperties = new TypedProperties();
        fileProperties.setProperty("some.file.key", "file-value");
        when(factory.reload()).thenReturn(fileProperties);
        ParameterService parameterService = new ParameterService(platform, factory, "sym_");
        assertEquals("file-value", parameterService.getString("some.file.key"));
    }

    @Test
    void testRefreshFromDatabase_withChangedSources_rereadsParametersAndReturnsTrue() {
        IStartupParameterService startupParameterService = mock(IStartupParameterService.class);
        when(startupParameterService.asTypedProperties(ENGINE_NAME)).thenReturn(startupProperties("first-value"))
                .thenReturn(startupProperties("second-value"));
        when(startupParameterService.refreshSources(ENGINE_NAME)).thenReturn(true);
        ParameterService parameterService = newParameterService(startupParameterService, null);
        assertEquals("first-value", parameterService.getString(SOURCE_KEY));
        assertTrue(parameterService.refreshFromDatabase());
        assertEquals("second-value", parameterService.getString(SOURCE_KEY));
    }

    @Test
    void testRefreshFromDatabase_withNothingChanged_returnsFalseWithoutRereading() {
        IStartupParameterService startupParameterService = mock(IStartupParameterService.class);
        when(startupParameterService.asTypedProperties(ENGINE_NAME)).thenReturn(startupProperties("first-value"));
        when(startupParameterService.refreshSources(ENGINE_NAME)).thenReturn(false);
        ParameterService parameterService = newParameterService(startupParameterService, null);
        parameterService.getString(SOURCE_KEY);
        assertFalse(parameterService.refreshFromDatabase());
        verify(startupParameterService, times(1)).asTypedProperties(ENGINE_NAME);
    }

    @Test
    void testRefreshFromDatabase_withSourcesAndDatabaseChanged_rereadsOnce() {
        IStartupParameterService startupParameterService = mock(IStartupParameterService.class);
        when(startupParameterService.asTypedProperties(ENGINE_NAME)).thenReturn(startupProperties("first-value"))
                .thenReturn(startupProperties("second-value"));
        when(startupParameterService.refreshSources(ENGINE_NAME)).thenReturn(true);
        ParameterService parameterService = newParameterService(startupParameterService, new Date());
        parameterService.getString(SOURCE_KEY);
        assertTrue(parameterService.refreshFromDatabase());
        assertEquals("second-value", parameterService.getString(SOURCE_KEY));
        verify(startupParameterService, times(2)).asTypedProperties(ENGINE_NAME);
    }

    @Test
    void testRefreshFromDatabase_withoutStartupParameterService_returnsFalse() {
        ParameterService parameterService = newParameterService(null, null);
        assertFalse(parameterService.refreshFromDatabase());
    }

    private ParameterService newParameterService(IStartupParameterService startupParameterService, Date maxLastUpdateTime) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject(anyString(), eq(Date.class))).thenReturn(maxLastUpdateTime);
        ITypedPropertiesFactory factory = mock(ITypedPropertiesFactory.class);
        when(factory.reload()).thenReturn(new TypedProperties());
        return startupParameterService != null ? new ParameterService(startupParameterService, ENGINE_NAME, platform, factory, "sym_")
                : new ParameterService(platform, factory, "sym_");
    }

    private TypedProperties startupProperties(String value) {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(SOURCE_KEY, value);
        properties.setProperty(ParameterConstants.PARAMETER_REFRESH_PERIOD_IN_MS, "600000");
        return properties;
    }
}
