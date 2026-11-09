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
package org.jumpmind.symmetric.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.ClickHouseDatabaseWriter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClickHouseDataLoaderFactoryTest {
    private ISymmetricDialect symmetricDialect;
    private ClickHouseDataLoaderFactory factory;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(extensionService.getExtensionPointList(IAlterDatabaseInterceptor.class)).thenReturn(new ArrayList<IAlterDatabaseInterceptor>());
        IDatabasePlatform sourcePlatform = mock(IDatabasePlatform.class);
        when(sourcePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(symmetricDialect.getPlatform()).thenReturn(sourcePlatform);
        IDatabasePlatform targetPlatform = mock(IDatabasePlatform.class);
        when(targetPlatform.getName()).thenReturn(DatabaseNamesConstants.CLICKHOUSE);
        when(targetPlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(symmetricDialect.getTargetPlatform()).thenReturn(targetPlatform);
        when(symmetricDialect.getTablePrefix()).thenReturn("sym");
        when(parameterService.getAllParameters()).thenReturn(new TypedProperties());
        factory = new ClickHouseDataLoaderFactory();
        factory.setSymmetricEngine(engine);
    }

    private IDatabasePlatform platform(String name) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(name);
        return platform;
    }

    @Test
    void testGetTypeName() {
        assertEquals("clickhouse", factory.getTypeName());
    }

    @Test
    void testIsPlatformSupported_onlyForClickHouse() {
        assertTrue(factory.isPlatformSupported(platform(DatabaseNamesConstants.CLICKHOUSE)));
        assertFalse(factory.isPlatformSupported(platform(DatabaseNamesConstants.POSTGRESQL)));
        assertFalse(factory.isPlatformSupported(platform("h2")));
    }

    @Test
    void testGetDataWriter_returnsClickHouseDatabaseWriter() {
        IDataWriter writer = factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof ClickHouseDatabaseWriter);
    }

    @Test
    void testIsRegisteredAsAnExtensionPoint() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/symmetric-ext-points.xml")) {
            assertNotNull(in);
            String xml = new String(in.readAllBytes());
            assertTrue(xml.contains("org.jumpmind.symmetric.io.ClickHouseDataLoaderFactory"));
        }
    }
}
