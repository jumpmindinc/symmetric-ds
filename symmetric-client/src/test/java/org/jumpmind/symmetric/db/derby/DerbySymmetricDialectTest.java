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
package org.jumpmind.symmetric.db.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.derby.DerbyDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DerbySymmetricDialectTest {
    private static final String TABLE_PREFIX = "sym";
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private DatabaseInfo databaseInfo;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        databaseInfo = new DatabaseInfo();
        when(parameterService.getTablePrefix()).thenReturn(TABLE_PREFIX);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(new DerbyDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
        when(platform.getDefaultSchema()).thenReturn("");
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn("10.15");
    }

    @Test
    void testSupportsTransactionId() {
        assertTrue(newDialect().supportsTransactionId());
    }

    @Test
    void testIsBlobSyncSupported() {
        assertTrue(newDialect().isBlobSyncSupported());
    }

    @Test
    void testIsClobSyncSupported() {
        assertTrue(newDialect().isClobSyncSupported());
    }

    @Test
    void testNeedsToSelectLobData() {
        assertTrue(newDialect().needsToSelectLobData());
    }

    @Test
    void testGetBinaryEncoding() {
        assertEquals(BinaryEncoding.BASE64, newDialect().getBinaryEncoding());
    }

    @Test
    void testGetDatabaseTimeSQL() {
        assertEquals("values current_timestamp", newDialect().getDatabaseTimeSQL());
    }

    @Test
    void testGetSyncTriggersExpression() {
        assertEquals("sym_sync_triggers_disabled() = 0", newDialect().getSyncTriggersExpression());
    }

    @Test
    void testGetTransactionTriggerExpression() {
        assertEquals("sym_transaction_id()", newDialect().getTransactionTriggerExpression("cat", "sch", new Trigger()));
    }

    @Test
    void testConstructor_enablesGeneratedColumnsForNewerDerby() {
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn("10.5");
        newDialect();
        assertTrue(databaseInfo.isGeneratedColumnsSupported());
    }

    @Test
    void testConstructor_disablesGeneratedColumnsForOlderDerby() {
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn("10.4");
        newDialect();
        assertFalse(databaseInfo.isGeneratedColumnsSupported());
    }

    @Test
    void testDisableSyncTriggers_withNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        newDialect().disableSyncTriggers(transaction, "store-001");
        inOrder.verify(transaction).queryForObject("values sym_sync_triggers_set_disabled(1)", Integer.class);
        inOrder.verify(transaction).queryForObject("values sym_sync_node_set_disabled('store-001')", String.class);
    }

    @Test
    void testDisableSyncTriggers_withoutNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        newDialect().disableSyncTriggers(transaction, null);
        verify(transaction).queryForObject("values sym_sync_triggers_set_disabled(1)", Integer.class);
        verifyNoMoreInteractions(transaction);
    }

    @Test
    void testEnableSyncTriggers() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        newDialect().enableSyncTriggers(transaction);
        inOrder.verify(transaction).queryForObject("values sym_sync_triggers_set_disabled(0)", Integer.class);
        inOrder.verify(transaction).queryForObject("values sym_sync_node_set_disabled(null)", String.class);
    }

    @Test
    void testDoesTriggerExistOnPlatform() {
        String sql = "select count(*) from sys.systriggers where triggername = ?";
        when(sqlTemplate.queryForInt(sql, new Object[] { "SYM_ON_I_FOR_ITEM" })).thenReturn(1);
        assertTrue(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "sym_on_i_for_item"));
    }

    @Test
    void testDoesTriggerExistOnPlatform_whenMissing() {
        String sql = "select count(*) from sys.systriggers where triggername = ?";
        when(sqlTemplate.queryForInt(sql, new Object[] { "SYM_ON_I_FOR_ITEM" })).thenReturn(0);
        assertFalse(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "sym_on_i_for_item"));
    }

    @Test
    void testTruncateTable() {
        newDialect().truncateTable("item");
        verify(sqlTemplate).update("delete from item");
    }

    @Test
    void testCreateRequiredDatabaseObjectsImpl_whenNothingInstalled() {
        when(sqlTemplate.queryForInt(anyString())).thenReturn(0);
        StringBuilder ddl = new StringBuilder();
        newDialect().createRequiredDatabaseObjectsImpl(ddl);
        String sql = ddl.toString();
        assertTrue(sql.contains("CREATE FUNCTION sym_escape("));
        assertTrue(sql.contains("CREATE FUNCTION sym_clob_to_string("));
        assertTrue(sql.contains("CREATE FUNCTION sym_blob_to_string("));
        assertTrue(sql.contains("CREATE FUNCTION sym_transaction_id("));
        assertTrue(sql.contains("CREATE FUNCTION sym_sync_triggers_disabled("));
        assertTrue(sql.contains("CREATE FUNCTION sym_sync_triggers_set_disabled("));
        assertTrue(sql.contains("CREATE FUNCTION sym_sync_node_set_disabled("));
        assertTrue(sql.contains("CREATE PROCEDURE sym_save_data("));
    }

    @Test
    void testCreateRequiredDatabaseObjectsImpl_whenAlreadyInstalled() {
        when(sqlTemplate.queryForInt(anyString())).thenReturn(1);
        StringBuilder ddl = new StringBuilder();
        newDialect().createRequiredDatabaseObjectsImpl(ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testDropRequiredDatabaseObjects_whenInstalled() {
        when(sqlTemplate.queryForInt(anyString())).thenReturn(1);
        newDialect().dropRequiredDatabaseObjects();
        verify(sqlTemplate).update("DROP FUNCTION sym_escape");
        verify(sqlTemplate).update("DROP FUNCTION sym_clob_to_string");
        verify(sqlTemplate).update("DROP FUNCTION sym_blob_to_string");
        verify(sqlTemplate).update("DROP FUNCTION sym_transaction_id");
        verify(sqlTemplate).update("DROP FUNCTION sym_sync_triggers_disabled");
        verify(sqlTemplate).update("DROP FUNCTION sym_sync_triggers_set_disabled");
        verify(sqlTemplate).update("DROP FUNCTION sym_sync_node_set_disabled");
        verify(sqlTemplate).update("DROP PROCEDURE sym_save_data");
    }

    @Test
    void testDropRequiredDatabaseObjects_whenNotInstalled() {
        when(sqlTemplate.queryForInt(anyString())).thenReturn(0);
        newDialect().dropRequiredDatabaseObjects();
        verify(sqlTemplate, never()).update(anyString());
    }

    private DerbySymmetricDialect newDialect() {
        return new DerbySymmetricDialect(parameterService, platform);
    }
}
