package org.jumpmind.symmetric.db.hana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.hana.HanaDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class HanaSymmetricDialectTest {
    private HanaSymmetricDialect dialect;
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setup() {
        parameterService = mock(ParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseMajorVersion()).thenReturn(1);
        when(parameterService.getTablePrefix()).thenReturn("");
        when(platform.getDdlBuilder()).thenReturn(new HanaDdlBuilder());
        dialect = new HanaSymmetricDialect(parameterService, platform);
    }

    @Test
    void testSupportsTransactionId() {
        assertTrue(dialect.supportsTransactionId());
    }

    @Test
    void testDisableSyncTriggersWithNodeId() {
        String nodeId = "source";
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        dialect.disableSyncTriggers(transaction, nodeId);
        inOrder.verify(transaction).prepareAndExecute("set '" + "sync_triggers_disabled" + "'='1'");
        inOrder.verify(transaction).prepareAndExecute("set '" + "sync_node_disabled" + "'='" + nodeId + "'");
    }

    @Test
    void testDisableSyncTriggersWithoutNodeId() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        dialect.disableSyncTriggers(transaction, null);
        verify(transaction).prepareAndExecute("set '" + "sync_triggers_disabled" + "'='1'");
        verifyNoMoreInteractions(transaction);
    }

    @Test
    void testEnableSyncTriggers() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        InOrder inOrder = inOrder(transaction);
        dialect.enableSyncTriggers(transaction);
        inOrder.verify(transaction).prepareAndExecute("set '" + "sync_triggers_disabled" + "'=''");
        inOrder.verify(transaction).prepareAndExecute("set '" + "sync_node_disabled" + "'=''");
    }

    @Test
    void testGetSyncTriggersExpression() {
        assertEquals("SESSION_CONTEXT('" + "sync_triggers_disabled" + "')  is null", dialect.getSyncTriggersExpression());
    }

    @Test
    void testGetBinaryEncoding() {
        assertEquals(BinaryEncoding.HEX, dialect.getBinaryEncoding());
    }

    @Test
    void testDoesTriggerExistOnPlatform() {
        String tableName = "TEST_TABLE";
        String triggerName = "TEST_TRIGGER";
        String expectedSql = "select count(*) from triggers where trigger_name like ? and subject_table_name like ?";
        Object[] expectedArgs = new Object[] { triggerName, tableName.toUpperCase() };
        when(sqlTemplate.queryForInt(expectedSql, expectedArgs)).thenReturn(1);
        assertTrue(dialect.doesTriggerExistOnPlatform(null, null, null, tableName, triggerName));
        verify(sqlTemplate, times(1)).queryForInt(expectedSql, expectedArgs);
    }

    @Test
    void testDoesTriggerExistOnPlatformFalse() {
        String tableName = "TEST_TABLE";
        String triggerName = "TEST_TRIGGER";
        String expectedSql = "select count(*) from triggers where trigger_name like ? and subject_table_name like ?";
        Object[] expectedArgs = new Object[] { triggerName, tableName.toUpperCase() };
        when(sqlTemplate.queryForInt(expectedSql, expectedArgs)).thenReturn(0);
        assertFalse(dialect.doesTriggerExistOnPlatform(null, null, null, tableName, triggerName));
        verify(sqlTemplate, times(1)).queryForInt(expectedSql, expectedArgs);
    }

    @Test
    void testGetDatabaseTimeSQL() {
        assertEquals("select current_timestamp from dummy", dialect.getDatabaseTimeSQL());
    }

    @Test
    void testGetTransactionTriggerExpression() {
        String tablePrefix = "SYM";
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        assertEquals(tablePrefix + "_" + "transaction_id()", dialect.getTransactionTriggerExpression("", "", new Trigger()));
    }
}
