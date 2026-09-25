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
package org.jumpmind.db.platform.sqlanywhere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAnywhereDatabasePlatformTest {
    private SqlAnywhereDatabasePlatform platform;

    @BeforeEach
    void setUp() throws SQLException {
        platform = new SqlAnywhereDatabasePlatform(newDataSourceMock(), new SqlTemplateSettings());
    }

    @Test
    void testGetClassName() {
        SqlAnywhereDatabasePlatform mockedPlatform = mock(SqlAnywhereDatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(SqlAnywhereDatabasePlatform.class.getName(), mockedPlatform.getClassName());
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.SQLANYWHERE, platform.getName());
    }

    @Test
    void testJdbcConstants() {
        assertEquals("com.sybase.jdbc4.jdbc.SybDriver", SqlAnywhereDatabasePlatform.JDBC_DRIVER);
        assertEquals("sybase:Tds", SqlAnywhereDatabasePlatform.JDBC_SUBPROTOCOL);
        assertEquals("sybase", SqlAnywhereDatabasePlatform.JDBC_SUBPROTOCOL_SHORT);
        assertEquals(2147483647L, SqlAnywhereDatabasePlatform.MAX_TEXT_SIZE);
    }

    @Test
    void testCreateDdlBuilder() {
        assertInstanceOf(SqlAnywhereDdlBuilder.class, platform.createDdlBuilder());
    }

    @Test
    void testCreateDdlReader() {
        assertInstanceOf(SqlAnywhereDdlReader.class, platform.createDdlReader());
    }

    @Test
    void testCreateSqlTemplate() {
        assertInstanceOf(SqlAnywhereJdbcSqlTemplate.class, platform.createSqlTemplate());
    }

    @Test
    void testGetSqlScriptReplacementTokens_mapsCurrentTimestamp() {
        assertEquals("getdate()", platform.getSqlScriptReplacementTokens().get("current_timestamp"));
    }

    @Test
    void testSupportsLimitOffset() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset_rewritesSelect() {
        assertEquals("select top 10 start at 21 * from item", platform.massageForLimitOffset("select * from item", 10, 20));
    }

    @Test
    void testMassageForLimitOffset_ignoresCaseOfSelect() {
        assertEquals("select top 5 start at 1 * from item", platform.massageForLimitOffset("SELECT * from item", 5, 0));
    }

    @Test
    void testGetDefaultCatalog_queriesDbName() {
        ISqlTemplate sqlTemplateMock = sqlTemplateReturning("select DB_NAME()", "corp");
        assertEquals("corp", platformWith(sqlTemplateMock).getDefaultCatalog());
    }

    @Test
    void testGetDefaultSchema_queriesUserName() {
        ISqlTemplate sqlTemplateMock = sqlTemplateReturning("select USER_NAME()", "dba");
        assertEquals("dba", platformWith(sqlTemplateMock).getDefaultSchema());
    }

    @Test
    void testGetCreateSymTriggerPermission_whenUpdateSucceeds() {
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        PermissionResult result = platformWith(sqlTemplateMock).getCreateSymTriggerPermission();
        assertEquals(PermissionType.CREATE_TRIGGER, result.getPermissionType());
        assertEquals(Status.PASS, result.getStatus());
        verify(sqlTemplateMock).update(result.getTestDetails());
    }

    @Test
    void testGetCreateSymTriggerPermission_whenUpdateFails() {
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        SqlException failure = new SqlException("no permission");
        when(sqlTemplateMock.update(anyString())).thenThrow(failure);
        PermissionResult result = platformWith(sqlTemplateMock).getCreateSymTriggerPermission();
        assertEquals(failure, result.getException());
        assertEquals("Grant CREATE TRIGGER permission or TRIGGER permission", result.getSolution());
    }

    private SqlAnywhereDatabasePlatform platformWith(ISqlTemplate sqlTemplateMock) {
        SqlAnywhereDatabasePlatform platformMock = mock(SqlAnywhereDatabasePlatform.class, CALLS_REAL_METHODS);
        doReturn(sqlTemplateMock).when(platformMock).getSqlTemplate();
        doReturn(new SqlAnywhereDdlBuilder().getDatabaseInfo()).when(platformMock).getDatabaseInfo();
        return platformMock;
    }

    private ISqlTemplate sqlTemplateReturning(String sql, String value) {
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject(sql, String.class)).thenReturn(value);
        return sqlTemplateMock;
    }

    private DataSource newDataSourceMock() throws SQLException {
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(metaDataMock.getJDBCMajorVersion()).thenReturn(4);
        when(metaDataMock.getURL()).thenReturn("jdbc:sybase:Tds:localhost:2638");
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        DataSource dataSourceMock = mock(DataSource.class);
        when(dataSourceMock.getConnection()).thenReturn(connectionMock);
        return dataSourceMock;
    }
}
