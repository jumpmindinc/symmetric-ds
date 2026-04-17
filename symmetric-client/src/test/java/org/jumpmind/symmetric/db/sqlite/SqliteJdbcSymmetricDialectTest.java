/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.db.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.sqlite.SqliteDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqliteJdbcSymmetricDialectTest {
    private SqliteJdbcSymmetricDialect dialect;
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setup() {
        parameterService = mock(ParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn("3.39.0");
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getDdlBuilder()).thenReturn(new SqliteDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(parameterService.getString(ParameterConstants.SQLITE_TRIGGER_FUNCTION_TO_USE)).thenReturn(null);
        dialect = new SqliteJdbcSymmetricDialect(parameterService, platform);
    }

    @Test
    void testGetBinaryEncoding() {
        assertEquals(BinaryEncoding.HEX, dialect.getBinaryEncoding());
    }

    @Test
    void testIsBlobSyncSupported() {
        assertTrue(dialect.isBlobSyncSupported());
    }

    @Test
    void testIsClobSyncSupported() {
        assertTrue(dialect.isClobSyncSupported());
    }

    @Test
    void testIsTransactionIdOverrideSupported() {
        assertFalse(dialect.isTransactionIdOverrideSupported());
    }

    @Test
    void testCanGapsOccurInCapturedDataIds() {
        assertFalse(dialect.canGapsOccurInCapturedDataIds());
    }

    @Test
    void testGetSyncTriggersExpressionWithoutFunctionOverride() {
        String expr = dialect.getSyncTriggersExpression();
        assertTrue(expr.contains("sym_context"), "Should reference context table");
        assertTrue(expr.contains(SqliteSymmetricDialect.SYNC_TRIGGERS_DISABLED_USER_VARIABLE));
    }

    @Test
    void testGetSyncTriggersExpressionWithFunctionOverride() {
        when(parameterService.getString(ParameterConstants.SQLITE_TRIGGER_FUNCTION_TO_USE)).thenReturn("my_func");
        SqliteJdbcSymmetricDialect d = new SqliteJdbcSymmetricDialect(parameterService, platform);
        String expr = d.getSyncTriggersExpression();
        assertTrue(expr.contains("my_func()"), "Should call the configured SQLite function");
        assertTrue(expr.contains("not like 'DISABLED%'"));
    }

    @Test
    void testDoesTriggerExistOnPlatformTrue() {
        String expectedSql = "select count(*) from sqlite_master where type='trigger' and name=? and tbl_name=? COLLATE NOCASE";
        when(sqlTemplate.queryForInt(expectedSql, "MY_TRIGGER", "MY_TABLE")).thenReturn(1);
        assertTrue(dialect.doesTriggerExistOnPlatform(null, null, null, "MY_TABLE", "MY_TRIGGER"));
    }

    @Test
    void testDoesTriggerExistOnPlatformFalse() {
        String expectedSql = "select count(*) from sqlite_master where type='trigger' and name=? and tbl_name=? COLLATE NOCASE";
        when(sqlTemplate.queryForInt(expectedSql, "MY_TRIGGER", "MY_TABLE")).thenReturn(0);
        assertFalse(dialect.doesTriggerExistOnPlatform(null, null, null, "MY_TABLE", "MY_TRIGGER"));
    }

    @Test
    void testDisableSyncTriggersWithFunctionOverrideCallsExecuteCallback() {
        when(parameterService.getString(ParameterConstants.SQLITE_TRIGGER_FUNCTION_TO_USE)).thenReturn("sym_fn");
        SqliteJdbcSymmetricDialect dialect = new SqliteJdbcSymmetricDialect(parameterService, platform);
        JdbcSqlTransaction transaction = mock(JdbcSqlTransaction.class);
        dialect.disableSyncTriggers(transaction, "node1");
        verify(transaction).executeCallback(any());
    }

    @Test
    void testEnableSyncTriggersWithFunctionOverrideCallsExecuteCallback() {
        when(parameterService.getString(ParameterConstants.SQLITE_TRIGGER_FUNCTION_TO_USE)).thenReturn("sym_fn");
        SqliteJdbcSymmetricDialect dialect = new SqliteJdbcSymmetricDialect(parameterService, platform);
        JdbcSqlTransaction transaction = mock(JdbcSqlTransaction.class);
        dialect.enableSyncTriggers(transaction);
        verify(transaction).executeCallback(any());
    }
}
