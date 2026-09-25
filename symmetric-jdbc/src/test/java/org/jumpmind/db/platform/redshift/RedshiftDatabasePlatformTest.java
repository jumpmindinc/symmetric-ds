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
package org.jumpmind.db.platform.redshift;

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
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedshiftDatabasePlatformTest {
    private DataSource dataSourceMock;
    private RedshiftDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        dataSourceMock = mock(DataSource.class);
        platform = new RedshiftDatabasePlatform(dataSourceMock, new SqlTemplateSettings());
    }

    @Test
    void testConstructor_overridesQueryTimeoutToZero() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setQueryTimeout(30);
        new RedshiftDatabasePlatform(dataSourceMock, settings);
        assertEquals(0, settings.getQueryTimeout());
    }

    @Test
    void testOverrideSettings_withNullSettings_createsSettingsWithZeroTimeout() {
        SqlTemplateSettings settings = RedshiftDatabasePlatform.overrideSettings(null);
        assertEquals(0, settings.getQueryTimeout());
    }

    @Test
    void testGetDdlBuilder_returnsRedshiftDdlBuilder() {
        assertTrue(platform.getDdlBuilder() instanceof RedshiftDdlBuilder);
    }

    @Test
    void testGetDdlReader_returnsRedshiftDdlReader() {
        assertTrue(platform.getDdlReader() instanceof RedshiftDdlReader);
    }

    @Test
    void testGetName_returnsRedshiftConstant() {
        assertEquals(DatabaseNamesConstants.REDSHIFT, platform.getName());
    }

    @Test
    void testGetClassName() {
        RedshiftDatabasePlatform mockPlatform = mock(RedshiftDatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(RedshiftDatabasePlatform.class.getName(), mockPlatform.getClassName());
    }

    @Test
    void testGetDefaultCatalog_returnsNull() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testGetDefaultSchema_queriesCurrentSchemaWhenNotCached() {
        RedshiftDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("select current_schema()", String.class)).thenReturn("analytics");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        assertEquals("analytics", spyPlatform.getDefaultSchema());
    }

    @Test
    void testGetDefaultSchema_cachesValueAfterFirstLookup() {
        RedshiftDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("select current_schema()", String.class)).thenReturn("analytics");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        spyPlatform.getDefaultSchema();
        spyPlatform.getDefaultSchema();
        verify(sqlTemplateMock).queryForObject("select current_schema()", String.class);
    }

    @Test
    void testGetSqlScriptReplacementTokens_mapsCurrentTimestampToSysdate() {
        assertEquals("sysdate", platform.getSqlScriptReplacementTokens().get("current_timestamp"));
    }

    @Test
    void testIsClob_forClobType_returnsTrue() {
        assertTrue(platform.isClob(Types.CLOB));
    }

    @Test
    void testIsClob_forNonClobType_returnsFalse() {
        assertFalse(platform.isClob(Types.VARCHAR));
    }

    @Test
    void testGetCreateSymTablePermission_returnsUnimplemented() {
        PermissionResult result = platform.getCreateSymTablePermission(null);
        assertEquals(Status.UNIMPLEMENTED, result.getStatus());
    }

    @Test
    void testGetDropSymTablePermission_returnsUnimplemented() {
        assertEquals(Status.UNIMPLEMENTED, platform.getDropSymTablePermission().getStatus());
    }

    @Test
    void testGetAlterSymTablePermission_returnsUnimplemented() {
        assertEquals(Status.UNIMPLEMENTED, platform.getAlterSymTablePermission(null).getStatus());
    }

    @Test
    void testGetDropSymTriggerPermission_returnsUnimplemented() {
        assertEquals(Status.UNIMPLEMENTED, platform.getDropSymTriggerPermission().getStatus());
    }

    @Test
    void testSupportsLimitOffset_returnsTrue() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset_appendsLimitAndOffset() {
        assertEquals("select * from item limit 10 offset 20", platform.massageForLimitOffset("select * from item", 10, 20));
    }

    @Test
    void testMassageForLimitOffset_stripsTrailingSemicolon() {
        assertEquals("select * from item limit 10 offset 20", platform.massageForLimitOffset("select * from item;", 10, 20));
    }
}
