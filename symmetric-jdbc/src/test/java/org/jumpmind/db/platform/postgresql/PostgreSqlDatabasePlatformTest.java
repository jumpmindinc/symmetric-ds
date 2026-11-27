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
package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.junit.jupiter.api.Test;

class PostgreSqlDatabasePlatformTest {
    private final PostgreSqlDatabasePlatform platform = mock(PostgreSqlDatabasePlatform.class, CALLS_REAL_METHODS);

    @Test
    void testGetClassName() {
        assertEquals(PostgreSqlDatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testIsMissingCitextExtensionError_citextDirectMessage() {
        assertTrue(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: type \"citext\" does not exist")));
    }

    @Test
    void testIsMissingCitextExtensionError_unrelatedError() {
        assertFalse(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: relation \"users\" already exists")));
    }

    @Test
    void testIsMissingCitextExtensionError_permissionDeniedError() {
        assertFalse(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: permission denied to create extension \"citext\"")));
    }

    @Test
    void testIsMissingCitextExtensionError_nullMessage() {
        assertFalse(platform.isMissingCitextExtensionError(new RuntimeException()));
    }

    @Test
    void testGetDefaultCatalog_returnsCurrentDatabase() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject("select current_database()", String.class))
                .thenReturn("test_database");
        String catalog = platform.getDefaultCatalog();
        assertNotNull(catalog);
        assertEquals("test_database", catalog);
        verify(sqlTemplate).queryForObject("select current_database()", String.class);
    }

    @Test
    void testGetDefaultCatalog_cachesCatalogValue() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject("select current_database()", String.class))
                .thenReturn("test_database");
        String catalog1 = platform.getDefaultCatalog();
        String catalog2 = platform.getDefaultCatalog();
        assertEquals("test_database", catalog1);
        assertEquals("test_database", catalog2);
        verify(sqlTemplate, times(1)).queryForObject(anyString(), eq(String.class));
    }

    private static Column arrayColumn(boolean required) {
        Column column = new Column("item_code");
        column.setMappedTypeCode(Types.ARRAY);
        column.setJdbcTypeName("_int8");
        column.setRequired(required);
        return column;
    }

    @Test
    void testCreateArray_blankValueForRequiredColumnFailsWithColumnName() {
        Column requiredColumn = arrayColumn(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> platform.createArray(requiredColumn, ""));
        assertTrue(ex.getMessage().contains("item_code"));
        assertTrue(ex.getMessage().contains("Blank value for required array column"));
    }

    @Test
    void testCreateArray_whitespaceValueForRequiredColumnFails() {
        Column requiredColumn = arrayColumn(true);
        assertThrows(IllegalArgumentException.class, () -> platform.createArray(requiredColumn, "  "));
    }

    @Test
    void testCreateArray_blankValueForOptionalColumnReturnsNull() {
        assertNull(platform.createArray(arrayColumn(false), ""));
    }

    @Test
    void testCreateArray_nonBlankValueBindsArrayLiteral() throws SQLException {
        Array array = platform.createArray(arrayColumn(true), "{12}");
        assertNotNull(array);
        assertEquals("{12}", array.toString());
        assertEquals("int8", array.getBaseTypeName());
        assertEquals(Types.INTEGER, array.getBaseType());
    }

    @Test
    void testCreateArray_emptyArrayLiteralForRequiredColumnBinds() {
        Array array = platform.createArray(arrayColumn(true), "{}");
        assertEquals("{}", array.toString());
    }

    @Test
    void testGetTransactions_versionBelow96ReturnsEmptyListWithoutQuery() {
        ISqlTemplate sqlTemplate = sqlTemplateForVersion(9, 5);
        assertEquals(Collections.emptyList(), platform.getTransactions());
        verify(sqlTemplate, never()).query(anyString());
    }

    @Test
    void testGetTransactions_version8ReturnsEmptyListWithoutQuery() {
        ISqlTemplate sqlTemplate = sqlTemplateForVersion(8, 4);
        assertEquals(Collections.emptyList(), platform.getTransactions());
        verify(sqlTemplate, never()).query(anyString());
    }

    @Test
    void testGetTransactions_version96UsesBlockingPids() {
        ISqlTemplate sqlTemplate = sqlTemplateForVersion(9, 6);
        when(sqlTemplate.query(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL)).thenReturn(Collections.emptyList());
        assertEquals(Collections.emptyList(), platform.getTransactions());
        verify(sqlTemplate).query(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL);
    }

    @Test
    void testGetTransactions_version18UsesBlockingPids() {
        ISqlTemplate sqlTemplate = sqlTemplateForVersion(18, 0);
        when(sqlTemplate.query(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL)).thenReturn(Collections.emptyList());
        platform.getTransactions();
        verify(sqlTemplate).query(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL);
    }

    @Test
    void testTransactionsSql_derivesBlockingFromLockWaitsOnly() {
        assertTrue(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL.contains("pg_blocking_pids(a.pid)"));
        assertTrue(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL.contains("a.wait_event_type = 'Lock'"));
        assertFalse(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL.contains("pg_locks"));
    }

    @Test
    void testGetTransactions_mapsUnblockedAndBlockedSessions() {
        ISqlTemplate sqlTemplate = sqlTemplateForVersion(18, 0);
        Row holder = transactionRow("101", null, "idle in transaction");
        Row waiter = transactionRow("202", "101", "active");
        when(sqlTemplate.query(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL)).thenReturn(Arrays.asList(holder, waiter));
        List<Transaction> transactions = platform.getTransactions();
        assertEquals(2, transactions.size());
        assertEquals("101", transactions.get(0).getId());
        assertEquals("", transactions.get(0).getBlockingId());
        assertEquals("idle in transaction", transactions.get(0).getStatus());
        assertEquals("202", transactions.get(1).getId());
        assertEquals("101", transactions.get(1).getBlockingId());
        assertEquals("symmetric", transactions.get(1).getUsername());
        assertEquals("10.0.0.5", transactions.get(1).getRemoteIp());
        assertEquals("app-host", transactions.get(1).getRemoteHost());
        assertEquals("update sym_node_host set heartbeat_time = now()", transactions.get(1).getText());
    }

    @Test
    void testGetTransactions_skipsRowWithoutQueryStart() {
        ISqlTemplate sqlTemplate = sqlTemplateForVersion(18, 0);
        Row row = transactionRow("101", null, "active");
        row.put("query_start", null);
        when(sqlTemplate.query(PostgreSqlDatabasePlatform.TRANSACTIONS_SQL)).thenReturn(Collections.singletonList(row));
        assertEquals(Collections.emptyList(), platform.getTransactions());
    }

    private ISqlTemplate sqlTemplateForVersion(int majorVersion, int minorVersion) {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(sqlTemplate.getDatabaseMajorVersion()).thenReturn(majorVersion);
        when(sqlTemplate.getDatabaseMinorVersion()).thenReturn(minorVersion);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        return sqlTemplate;
    }

    private Row transactionRow(String pid, String blockingPid, String state) {
        Row row = new Row(8);
        row.put("pid", pid);
        row.put("usename", "symmetric");
        row.put("client_addr", "10.0.0.5");
        row.put("client_hostname", "app-host");
        row.put("state", state);
        row.put("blockingPid", blockingPid);
        row.put("query_start", "2026-11-27 10:15:30.123456-05");
        row.put("query", "update sym_node_host set heartbeat_time = now()");
        return row;
    }
}
