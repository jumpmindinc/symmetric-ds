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
package org.jumpmind.db.platform.raima;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import javax.sql.DataSource;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RaimaDatabasePlatformTest {
    private RaimaDatabasePlatform platform;
    private DataSource dataSource;
    private SqlTemplateSettings settings;

    @BeforeEach
    void setup() {
        dataSource = mock(DataSource.class);
        settings = mock(SqlTemplateSettings.class);
        platform = new RaimaDatabasePlatform(dataSource, settings);
    }

    @Test
    void testConstructor_configuresPlatform() {
        assertFalse(platform.getTruncateSql(null).startsWith("truncate"),
                "Raima doesn't support truncate, so the truncate SQL should fall back to a delete statement");
        assertTrue(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
    }

    @Test
    void testCreateDdlBuilder_returnsRaimaDdlBuilder() {
        assertNotNull(platform.createDdlBuilder());
        assertTrue(platform.createDdlBuilder() instanceof RaimaDdlBuilder);
    }

    @Test
    void testCreateDdlReader_returnsRaimaDdlReader() {
        assertNotNull(platform.createDdlReader());
        assertTrue(platform.createDdlReader() instanceof RaimaDdlReader);
    }

    @Test
    void testCreateSqlTemplate_returnsRaimaJdbcSqlTemplate() {
        ISqlTemplate result = platform.getSqlTemplate();
        assertNotNull(result);
        assertTrue(result instanceof RaimaJdbcSqlTemplate);
    }

    @Test
    void testCreateSqlTemplateDirty_returnsSameInstanceAsSqlTemplate() {
        assertSame(platform.getSqlTemplate(), platform.getSqlTemplateDirty());
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.RAIMA, platform.getName());
    }

    @Test
    void testGetClassName() {
        RaimaDatabasePlatform mockedPlatform = mock(RaimaDatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(RaimaDatabasePlatform.class.getName(), mockedPlatform.getClassName());
    }

    @Test
    void testGetClassName_onRealInstance() {
        assertEquals(RaimaDatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testGetDefaultSchema() {
        assertNull(platform.getDefaultSchema());
    }

    @Test
    void testGetDefaultCatalog() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testGetCreateSymTriggerPermission_success() throws SqlException {
        RaimaDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        doReturn(sqlTemplateMock).when(spyPlatform).getSqlTemplate();
        PermissionResult result = spyPlatform.getCreateSymTriggerPermission();
        assertEquals(Status.PASS, result.getStatus());
    }

    @Test
    void testGetCreateSymTriggerPermission_failure() throws SqlException {
        RaimaDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        doReturn(sqlTemplateMock).when(spyPlatform).getSqlTemplate();
        SqlException exception = new SqlException("no permission");
        doThrow(exception).when(sqlTemplateMock).update(anyString());
        PermissionResult result = spyPlatform.getCreateSymTriggerPermission();
        assertEquals(Status.FAIL, result.getStatus());
        assertSame(exception, result.getException());
        assertNotNull(result.getSolution());
    }

    @Test
    void testGetDropSymTriggerPermission_success() throws SqlException {
        RaimaDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        doReturn(sqlTemplateMock).when(spyPlatform).getSqlTemplate();
        PermissionResult result = spyPlatform.getDropSymTriggerPermission();
        assertEquals(Status.PASS, result.getStatus());
    }

    @Test
    void testGetDropSymTriggerPermission_failure() throws SqlException {
        RaimaDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        doReturn(sqlTemplateMock).when(spyPlatform).getSqlTemplate();
        SqlException exception = new SqlException("no permission");
        doThrow(exception).when(sqlTemplateMock).update(anyString());
        PermissionResult result = spyPlatform.getDropSymTriggerPermission();
        assertEquals(Status.FAIL, result.getStatus());
        assertSame(exception, result.getException());
        assertNotNull(result.getSolution());
    }

    @Test
    void testGetTruncateSql_fallsBackToDeleteStatement() {
        Table table = new Table("test_table");
        String truncateSql = platform.getTruncateSql(table);
        String deleteSql = platform.getDeleteSql(table);
        assertEquals(deleteSql, truncateSql);
    }
}
