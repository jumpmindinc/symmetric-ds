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
package org.jumpmind.db.platform.db2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.Test;

class Db2DatabasePlatformTest {
    @Test
    void testConstructor_newerVersion_keepsTruncateSupported() throws SQLException {
        Db2DatabasePlatform platform = createPlatform(10, 0);
        String sql = platform.getTruncateSql(new Table("TEST_TABLE", new Column("ID")));
        assertTrue(sql.contains("truncate table"));
        assertTrue(sql.contains("reuse storage immediate"));
    }

    @Test
    void testConstructor_versionAtSupportedBoundary_keepsTruncateSupported() throws SQLException {
        Db2DatabasePlatform platform = createPlatform(9, 7);
        String sql = platform.getTruncateSql(new Table("TEST_TABLE", new Column("ID")));
        assertTrue(sql.contains("reuse storage immediate"));
    }

    @Test
    void testConstructor_olderVersion_disablesTruncateSupportSoSuperFallsBackToDelete() throws SQLException {
        Db2DatabasePlatform platform = createPlatform(9, 6);
        String sql = platform.getTruncateSql(new Table("TEST_TABLE", new Column("ID")));
        assertFalse(sql.contains("truncate table"));
        assertTrue(sql.contains("delete from"));
    }

    @Test
    void testCreateDdlBuilder_returnsDb2DdlBuilder() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        IDdlBuilder ddlBuilder = platform.createDdlBuilder();
        assertTrue(ddlBuilder instanceof Db2DdlBuilder);
    }

    @Test
    void testCreateDdlReader_returnsDb2DdlReader() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        IDdlReader ddlReader = platform.createDdlReader();
        assertTrue(ddlReader instanceof Db2DdlReader);
    }

    @Test
    void testCreateSqlTemplate_returnsDb2JdbcSqlTemplate() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        when(platform.getDdlBuilder()).thenReturn(new Db2DdlBuilder());
        ISqlTemplate sqlTemplate = platform.createSqlTemplate();
        assertTrue(sqlTemplate instanceof Db2JdbcSqlTemplate);
    }

    @Test
    void testAllowsUniqueIndexDuplicatesWithNulls_returnsFalse() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        assertFalse(platform.allowsUniqueIndexDuplicatesWithNulls());
    }

    @Test
    void testGetName_returnsDb2Constant() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(DatabaseNamesConstants.DB2, platform.getName());
    }

    @Test
    void testGetClassName() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(Db2DatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testGetDefaultSchema_blank_queriesCurrentSchema() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject("select CURRENT SCHEMA from sysibm.sysdummy1", String.class))
                .thenReturn("DB2INST1");
        String schema = platform.getDefaultSchema();
        assertEquals("DB2INST1", schema);
        verify(sqlTemplate).queryForObject("select CURRENT SCHEMA from sysibm.sysdummy1", String.class);
    }

    @Test
    void testGetDefaultSchema_calledTwice_cachesValueAndQueriesOnlyOnce() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject("select CURRENT SCHEMA from sysibm.sysdummy1", String.class))
                .thenReturn("DB2INST1");
        String firstCall = platform.getDefaultSchema();
        String secondCall = platform.getDefaultSchema();
        assertEquals("DB2INST1", firstCall);
        assertEquals("DB2INST1", secondCall);
        verify(sqlTemplate, times(1)).queryForObject(anyString(), eq(String.class));
    }

    @Test
    void testGetDefaultCatalog_returnsEmptyString() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals("", platform.getDefaultCatalog());
    }

    @Test
    void testCanColumnBeUsedInWhereClause_binaryColumn_returnsFalse() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        Column column = new Column("DATA", false, Types.BINARY, 0, 0);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_nonBinaryColumn_returnsTrue() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        Column column = new Column("ID", false, Types.INTEGER, 0, 0);
        assertTrue(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testGetCreateSymTriggerPermission_success_returnsPassStatus() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(new Db2DdlBuilder());
        PermissionResult result = platform.getCreateSymTriggerPermission();
        assertEquals(Status.PASS, result.getStatus());
        verify(sqlTemplate, times(1)).update(anyString());
    }

    @Test
    void testGetCreateSymTriggerPermission_failure_returnsFailStatusWithSolution() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(new Db2DdlBuilder());
        SqlException sqlException = new SqlException("no permission");
        doThrow(sqlException).when(sqlTemplate).update(anyString());
        PermissionResult result = platform.getCreateSymTriggerPermission();
        assertEquals(Status.FAIL, result.getStatus());
        assertNotNull(result.getSolution());
        assertEquals(sqlException, result.getException());
    }

    @Test
    void testGetTruncateSql_appendsReuseStorageSuffix() throws SQLException {
        Db2DatabasePlatform platform = createPlatform(11, 5);
        String sql = platform.getTruncateSql(new Table("TEST_TABLE", new Column("ID")));
        assertTrue(sql.startsWith("truncate table"));
        assertTrue(sql.endsWith("reuse storage immediate"));
    }

    @Test
    void testSupportsLimitOffset_returnsTrue() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset_stripsTrailingSemicolon() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        platform.majorVersion = 12;
        platform.minorVersion = 0;
        String result = platform.massageForLimitOffset("select * from t;", 10, 0);
        assertEquals("select * from t limit 10 offset 0", result);
    }

    @Test
    void testMassageForLimitOffset_majorVersion12_usesNativeLimitOffsetSyntax() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        platform.majorVersion = 12;
        platform.minorVersion = 0;
        String result = platform.massageForLimitOffset("select * from t", 10, 5);
        assertEquals("select * from t limit 10 offset 5", result);
    }

    @Test
    void testMassageForLimitOffset_majorVersion11MinorVersion1_usesNativeLimitOffsetSyntax() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        platform.majorVersion = 11;
        platform.minorVersion = 1;
        String result = platform.massageForLimitOffset("select * from t", 10, 5);
        assertEquals("select * from t limit 10 offset 5", result);
    }

    @Test
    void testMassageForLimitOffset_majorVersion11MinorVersion0_usesRowNumberSyntax() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        platform.majorVersion = 11;
        platform.minorVersion = 0;
        String result = platform.massageForLimitOffset("select a, b from t order by a", 10, 0);
        assertEquals("select * from (select a, b, ROW_NUMBER() over (order by a) as RowNum from t) where RowNum between 1 and 10", result);
    }

    @Test
    void testMassageForLimitOffset_olderVersion_usesRowNumberSyntax() {
        Db2DatabasePlatform platform = mock(Db2DatabasePlatform.class, CALLS_REAL_METHODS);
        platform.majorVersion = 9;
        platform.minorVersion = 7;
        String result = platform.massageForLimitOffset("select a, b from t order by a", 10, 20);
        assertEquals("select * from (select a, b, ROW_NUMBER() over (order by a) as RowNum from t) where RowNum between 21 and 30", result);
    }

    private Db2DatabasePlatform createPlatform(int majorVersion, int minorVersion) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseMajorVersion()).thenReturn(majorVersion);
        when(metaData.getDatabaseMinorVersion()).thenReturn(minorVersion);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        return new Db2DatabasePlatform(dataSource, settings);
    }
}
