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
package org.jumpmind.db.platform.hsqldb;

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

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HsqlDbDatabasePlatformTest {
    private HsqlDbDatabasePlatform platform;
    private DataSource dataSource;
    private SqlTemplateSettings settings;

    @BeforeEach
    void setup() {
        dataSource = mock(DataSource.class);
        settings = mock(SqlTemplateSettings.class);
        platform = new HsqlDbDatabasePlatform(dataSource, settings);
    }

    @Test
    void testConstants_matchHsqlDbJdbcDriverAndSubprotocol() {
        assertEquals("org.hsqldb.jdbcDriver", HsqlDbDatabasePlatform.JDBC_DRIVER);
        assertEquals("hsqldb", HsqlDbDatabasePlatform.JDBC_SUBPROTOCOL);
    }

    @Test
    void testCreateDdlBuilder_returnsHsqlDbDdlBuilder() {
        assertTrue(platform.getDdlBuilder() instanceof HsqlDbDdlBuilder);
    }

    @Test
    void testCreateDdlReader_returnsHsqlDbDdlReader() {
        assertTrue(platform.getDdlReader() instanceof HsqlDbDdlReader);
    }

    @Test
    void testCreateSqlTemplate_returnsHsqlDbJdbcSqlTemplate() {
        assertTrue(platform.getSqlTemplate() instanceof HsqlDbJdbcSqlTemplate);
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.HSQLDB, platform.getName());
    }

    @Test
    void testGetClassName() {
        HsqlDbDatabasePlatform mockedPlatform = mock(HsqlDbDatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(HsqlDbDatabasePlatform.class.getName(), mockedPlatform.getClassName());
    }

    @Test
    void testGetDefaultCatalog_returnsNull() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testGetDefaultSchema_returnsNull() {
        assertNull(platform.getDefaultSchema());
    }

    @Test
    void testGetCreateSymTriggerPermission_whenUpdateSucceeds_returnsPassStatus() throws SqlException {
        HsqlDbDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        doReturn(sqlTemplateMock).when(spyPlatform).getSqlTemplate();
        PermissionResult result = spyPlatform.getCreateSymTriggerPermission();
        assertEquals(Status.PASS, result.getStatus());
    }

    @Test
    void testGetCreateSymTriggerPermission_whenUpdateThrows_returnsFailWithSolution() throws SqlException {
        HsqlDbDatabasePlatform spyPlatform = spy(platform);
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
    void testSupportsMultiThreadedTransactions_returnsFalse() {
        assertFalse(platform.supportsMultiThreadedTransactions());
    }

    @Test
    void testSupportsLimitOffset_returnsTrue() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset_sqlEndsWithSemicolon_stripsSemicolonBeforeAppending() {
        String result = platform.massageForLimitOffset("SELECT * FROM TEST_TABLE;", 10, 20);
        assertEquals("SELECT * FROM TEST_TABLE limit 10 offset 20", result);
    }

    @Test
    void testMassageForLimitOffset_sqlWithoutSemicolon_appendsLimitAndOffset() {
        String result = platform.massageForLimitOffset("SELECT * FROM TEST_TABLE", 10, 20);
        assertEquals("SELECT * FROM TEST_TABLE limit 10 offset 20", result);
    }
}
