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
package org.jumpmind.symmetric.db.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.hsqldb.HsqlDbDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class HsqlDbSymmetricDialectTest {
    private static final String TABLE_PREFIX = "sym";
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        Table dualTable = new Table("DUAL");
        when(parameterService.getTablePrefix()).thenReturn(TABLE_PREFIX);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(new HsqlDbDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(platform.getDefaultSchema()).thenReturn("");
        when(platform.getRelationFromCache(isNull(), isNull(), anyString(), anyBoolean())).thenReturn(dualTable);
    }

    @Test
    void testSupportsTransactionId() {
        assertFalse(newDialect().supportsTransactionId());
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
    void testCanGapsOccurInCapturedDataIds() {
        assertFalse(newDialect().canGapsOccurInCapturedDataIds());
    }

    @Test
    void testIsNonBlankCharColumnSpacePadded() {
        assertTrue(newDialect().isNonBlankCharColumnSpacePadded());
    }

    @Test
    void testIsCharColumnSpaceTrimmed() {
        assertFalse(newDialect().isCharColumnSpaceTrimmed());
    }

    @Test
    void testIsEmptyStringNulled() {
        assertFalse(newDialect().isEmptyStringNulled());
    }

    @Test
    void testGetBinaryEncoding() {
        assertEquals(BinaryEncoding.BASE64, newDialect().getBinaryEncoding());
    }

    @Test
    void testGetDatabaseTimeSQL() {
        assertEquals("SELECT current_timestamp FROM INFORMATION_SCHEMA.SYSTEM_USERS", newDialect().getDatabaseTimeSQL());
    }

    @Test
    void testGetSyncTriggersExpression() {
        assertEquals(" sym_get_session(''sync_prevented'') is null ", newDialect().getSyncTriggersExpression());
    }

    @Test
    void testGetTransactionTriggerExpression_isAlwaysNull() {
        assertEquals("null", newDialect().getTransactionTriggerExpression("cat", "sch", new Trigger()));
    }

    @Test
    void testConstructor_configuresWriteDelayAndTableType() {
        newDialect();
        verify(sqlTemplate).update("SET WRITE_DELAY 100 MILLIS");
        verify(sqlTemplate).update("SET PROPERTY \"hsqldb.default_table_type\" 'cached'");
        verify(sqlTemplate).update("SET PROPERTY \"sql.enforce_strict_size\" true");
    }

    @Test
    void testConstructor_createsDualTableWhenMissing() {
        when(platform.getRelationFromCache(isNull(), isNull(), anyString(), anyBoolean())).thenReturn(null);
        newDialect();
        verify(sqlTemplate).update("CREATE MEMORY TABLE DUAL(DUMMY VARCHAR(1))");
        verify(sqlTemplate).update("INSERT INTO DUAL VALUES(NULL)");
        verify(sqlTemplate).update("SET TABLE DUAL READONLY TRUE");
    }

    @Test
    void testConstructor_skipsDualTableWhenPresent() {
        newDialect();
        verify(sqlTemplate, never()).update("CREATE MEMORY TABLE DUAL(DUMMY VARCHAR(1))");
    }

    @Test
    void testDisableSyncTriggers_withNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        newDialect().disableSyncTriggers(transaction, "store-001");
        inOrder.verify(transaction).prepareAndExecute("CALL sym_set_session('sync_prevented','1')");
        inOrder.verify(transaction).prepareAndExecute("CALL sym_set_session('node_value','store-001')");
    }

    @Test
    void testDisableSyncTriggers_withoutNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        newDialect().disableSyncTriggers(transaction, null);
        verify(transaction).prepareAndExecute("CALL sym_set_session('node_value','null')");
    }

    @Test
    void testEnableSyncTriggers() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        newDialect().enableSyncTriggers(transaction);
        inOrder.verify(transaction).prepareAndExecute("CALL sym_set_session('sync_prevented',null)");
        inOrder.verify(transaction).prepareAndExecute("CALL sym_set_session('node_value',null)");
    }

    @Test
    void testDoesTriggerExistOnPlatform_whenTriggerFound() {
        stubTriggerLookup(1, 0);
        assertTrue(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "SYM_ON_I_FOR_ITEM"));
    }

    @Test
    void testDoesTriggerExistOnPlatform_whenOnlyConfigTableFound() {
        stubTriggerLookup(0, 1);
        assertTrue(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "SYM_ON_I_FOR_ITEM"));
    }

    @Test
    void testDoesTriggerExistOnPlatform_whenMissing() {
        stubTriggerLookup(0, 0);
        assertFalse(newDialect().doesTriggerExistOnPlatform(null, "cat", "sch", "item", "SYM_ON_I_FOR_ITEM"));
    }

    @Test
    void testTruncateTable() {
        newDialect().truncateTable("item");
        verify(sqlTemplate).update("delete from item");
    }

    @Test
    void testRemoveTrigger_logsToBufferWithoutExecuting() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        StringBuilder sqlBuffer = new StringBuilder();
        newDialect().removeTrigger(sqlBuffer, "cat", "sch", "SYM_ON_I_FOR_ITEM", "item", transaction);
        assertTrue(sqlBuffer.toString().contains("DROP TRIGGER SYM_ON_I_FOR_ITEM"));
        assertTrue(sqlBuffer.toString().contains("DROP TABLE IF EXISTS SYM_ON_I_FOR_ITEM_CONFIG"));
        verify(transaction, never()).execute(anyString());
    }

    @Test
    void testRemoveTrigger_executesWhenAutoSyncTriggersEnabled() {
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)).thenReturn(true);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        newDialect().removeTrigger(null, "cat", "sch", "SYM_ON_I_FOR_ITEM", "item", transaction);
        verify(transaction).execute("DROP TRIGGER SYM_ON_I_FOR_ITEM");
        verify(transaction).execute("DROP TABLE IF EXISTS SYM_ON_I_FOR_ITEM_CONFIG");
    }

    @Test
    void testRemoveTrigger_skipsExecutionWhenAutoSyncTriggersDisabled() {
        when(parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)).thenReturn(false);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        newDialect().removeTrigger(null, "cat", "sch", "SYM_ON_I_FOR_ITEM", "item", transaction);
        verify(transaction, never()).execute(anyString());
    }

    @Test
    void testCreateRequiredDatabaseObjectsImpl_whenNothingInstalled() {
        when(sqlTemplate.queryForInt(anyString())).thenReturn(0);
        StringBuilder ddl = new StringBuilder();
        newDialect().createRequiredDatabaseObjectsImpl(ddl);
        String sql = ddl.toString();
        assertTrue(sql.contains("CREATE ALIAS sym_base_64_encode for \"org.jumpmind.symmetric.db.hsqldb.HsqlDbFunctions.encodeBase64\""));
        assertTrue(sql.contains("CREATE ALIAS sym_set_session for \"org.jumpmind.symmetric.db.hsqldb.HsqlDbFunctions.setSession\""));
        assertTrue(sql.contains("CREATE ALIAS sym_get_session for \"org.jumpmind.symmetric.db.hsqldb.HsqlDbFunctions.getSession\""));
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
        verify(sqlTemplate).update("DROP ALIAS sym_base_64_encode");
        verify(sqlTemplate).update("DROP ALIAS sym_set_session");
        verify(sqlTemplate).update("DROP ALIAS sym_get_session");
    }

    @Test
    void testDropRequiredDatabaseObjects_whenNotInstalled() {
        when(sqlTemplate.queryForInt(anyString())).thenReturn(0);
        newDialect().dropRequiredDatabaseObjects();
        verify(sqlTemplate, never()).update("DROP ALIAS sym_base_64_encode");
    }

    private void stubTriggerLookup(int triggerCount, int configTableCount) {
        when(sqlTemplate.queryForInt("select count(*) from INFORMATION_SCHEMA.SYSTEM_TRIGGERS WHERE TRIGGER_NAME = ?",
                new Object[] { "SYM_ON_I_FOR_ITEM" })).thenReturn(triggerCount);
        when(sqlTemplate.queryForInt("select count(*) from INFORMATION_SCHEMA.SYSTEM_TABLES WHERE TABLE_NAME = ?",
                new Object[] { "SYM_ON_I_FOR_ITEM_CONFIG" })).thenReturn(configTableCount);
    }

    private HsqlDbSymmetricDialect newDialect() {
        return new HsqlDbSymmetricDialect(parameterService, platform);
    }
}
