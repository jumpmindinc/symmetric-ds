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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.junit.jupiter.api.Test;

class ParameterServiceTest {
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
}
