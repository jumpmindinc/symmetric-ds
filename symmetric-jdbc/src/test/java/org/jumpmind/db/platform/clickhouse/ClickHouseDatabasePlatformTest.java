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
package org.jumpmind.db.platform.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClickHouseDatabasePlatformTest {
    private ClickHouseDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        platform = new ClickHouseDatabasePlatform(mock(DataSource.class), new SqlTemplateSettings());
    }

    @Test
    void testGetName_returnsClickHouseConstant() {
        assertEquals(DatabaseNamesConstants.CLICKHOUSE, platform.getName());
    }

    @Test
    void testGetClassName() {
        ClickHouseDatabasePlatform mockPlatform = mock(ClickHouseDatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(ClickHouseDatabasePlatform.class.getName(), mockPlatform.getClassName());
    }

    @Test
    void testJdbcConstants() {
        assertEquals("com.clickhouse.jdbc.ClickHouseDriver", ClickHouseDatabasePlatform.JDBC_DRIVER);
        assertEquals("clickhouse", ClickHouseDatabasePlatform.JDBC_SUBPROTOCOL);
        assertEquals("ch", ClickHouseDatabasePlatform.JDBC_SUBPROTOCOL_SHORT);
    }

    @Test
    void testCreatesClickHouseDdlBuilderReaderAndSqlTemplate() {
        assertTrue(platform.getDdlBuilder() instanceof ClickHouseDdlBuilder);
        assertTrue(platform.getDdlReader() instanceof ClickHouseDdlReader);
        assertTrue(platform.getSqlTemplate() instanceof ClickHouseJdbcSqlTemplate);
    }

    @Test
    void testDirtySqlTemplate_isAlsoClickHouseTemplate() {
        assertTrue(platform.getSqlTemplateDirty() instanceof ClickHouseJdbcSqlTemplate);
    }

    @Test
    void testGetDefaultCatalog_returnsNull() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testGetDefaultSchema_isTheCurrentDatabase() {
        ClickHouseDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("select currentDatabase()", String.class)).thenReturn("analytics");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        assertEquals("analytics", spyPlatform.getDefaultSchema());
    }

    @Test
    void testGetDefaultSchema_cachesValueAfterFirstLookup() {
        ClickHouseDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("select currentDatabase()", String.class)).thenReturn("analytics");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        spyPlatform.getDefaultSchema();
        spyPlatform.getDefaultSchema();
        verify(sqlTemplateMock).queryForObject("select currentDatabase()", String.class);
    }

    @Test
    void testSymTablePermissions_areUnimplemented() {
        assertEquals(Status.UNIMPLEMENTED, platform.getCreateSymTablePermission(null).getStatus());
        assertEquals(Status.UNIMPLEMENTED, platform.getDropSymTablePermission().getStatus());
        assertEquals(Status.UNIMPLEMENTED, platform.getAlterSymTablePermission(null).getStatus());
        assertEquals(Status.UNIMPLEMENTED, platform.getDropSymTriggerPermission().getStatus());
    }

    @Test
    void testMassageForLimitOffset_appendsLimitAndOffset() {
        assertTrue(platform.supportsLimitOffset());
        assertEquals("select * from t limit 10 offset 20", platform.massageForLimitOffset("select * from t;", 10, 20));
    }
}
