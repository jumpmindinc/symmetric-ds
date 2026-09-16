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
package org.jumpmind.symmetric.db.ingres;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.ingres.IngresDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class IngresSymmetricDialectTest {
    private static final String TABLE_PREFIX = "sym";
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn(TABLE_PREFIX);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(new IngresDdlBuilder());
    }

    @Test
    void testConstructor_setsIngresTriggerTemplate() {
        assertEquals(IngresSqlTriggerTemplate.class, newDialect().getTriggerTemplate().getClass());
    }

    @Test
    void testSupportsTransactionId() {
        assertTrue(newDialect().supportsTransactionId());
    }

    @Test
    void testCleanDatabase_isNoOp() {
        assertDoesNotThrow(() -> newDialect().cleanDatabase());
    }

    @Test
    void testDisableSyncTriggers_withNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        newDialect().disableSyncTriggers(transaction, "store-001");
        inOrder.verify(transaction).prepareAndExecute(
                "delete from sym_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'");
        inOrder.verify(transaction).prepareAndExecute(
                "insert into sym_context (name, context_value, create_time, last_update_time)  values(DBMSINFO('session_id') || ':synctriggersdisabled', '1', current_timestamp, current_timestamp)");
        inOrder.verify(transaction).prepareAndExecute(
                "delete from sym_context where name = DBMSINFO('session_id') || ':sourcenode'");
        inOrder.verify(transaction).prepareAndExecute(
                "insert into sym_context (name, context_value, create_time, last_update_time)  values(DBMSINFO('session_id') || ':sourcenode', 'store-001', current_timestamp, current_timestamp)");
    }

    @Test
    void testDisableSyncTriggers_withNullNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        newDialect().disableSyncTriggers(transaction, null);
        verify(transaction).prepareAndExecute(
                "insert into sym_context (name, context_value, create_time, last_update_time)  values(DBMSINFO('session_id') || ':sourcenode', 'null', current_timestamp, current_timestamp)");
    }

    @Test
    void testEnableSyncTriggers() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        newDialect().enableSyncTriggers(transaction);
        inOrder.verify(transaction).prepareAndExecute(
                "delete from sym_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'");
        inOrder.verify(transaction).prepareAndExecute(
                "delete from sym_context where name = DBMSINFO('session_id') || ':sourcenode'");
    }

    @Test
    void testGetSyncTriggersExpression() {
        assertEquals("((var_sync_triggers_disabled is null) OR (var_sync_triggers_disabled = '0'))",
                newDialect().getSyncTriggersExpression());
    }

    @Test
    void testDropRequiredDatabaseObjects_isNoOp() {
        assertDoesNotThrow(() -> newDialect().dropRequiredDatabaseObjects());
    }

    @Test
    void testGetBinaryEncoding() {
        assertEquals(BinaryEncoding.HEX, newDialect().getBinaryEncoding());
    }

    @Test
    void testDoesTriggerExistOnPlatform_whenFound() {
        when(sqlTemplate.queryForInt("select count(*) from iirule where rule_name = ? ",
                new Object[] { "sym_on_i_for_item" })).thenReturn(1);
        assertTrue(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "SYM_ON_I_FOR_ITEM"));
    }

    @Test
    void testDoesTriggerExistOnPlatform_whenMissing() {
        when(sqlTemplate.queryForInt("select count(*) from iirule where rule_name = ? ",
                new Object[] { "sym_on_i_for_item" })).thenReturn(0);
        assertFalse(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "SYM_ON_I_FOR_ITEM"));
    }

    @Test
    void testRequiresAutoCommitFalseToSetFetchSize() {
        assertTrue(newDialect().requiresAutoCommitFalseToSetFetchSize());
    }

    @Test
    void testNeedsToSelectLobData() {
        assertTrue(newDialect().needsToSelectLobData());
    }

    @Test
    void testTruncateTable() {
        newDialect().truncateTable("item");
        verify(sqlTemplate).update("modify item to truncated");
    }

    @Test
    void testIsTransactionIdOverrideSupported() {
        assertFalse(newDialect().isTransactionIdOverrideSupported());
    }

    private IngresSymmetricDialect newDialect() {
        return new IngresSymmetricDialect(parameterService, platform);
    }
}
