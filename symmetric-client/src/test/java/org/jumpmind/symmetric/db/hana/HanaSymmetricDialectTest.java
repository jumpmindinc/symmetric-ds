package org.jumpmind.symmetric.db.hana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.hana.HanaDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.model.Trigger;
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
        inOrder.verify(transaction).prepareAndExecute("unset 'sync_triggers_disabled'");
        inOrder.verify(transaction).prepareAndExecute("unset 'sync_node_disabled'");
        verifyNoMoreInteractions(transaction);
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
    void testDoesTriggerExistOnPlatformInGivenSchema() {
        Object[] expectedArgs = new Object[] { "SYM_ON_I_FOR_TEST", "Test_Mixed_Case", "TEST_MIXED_CASE", "App", "APP" };
        when(sqlTemplate.queryForInt(HanaSymmetricDialect.SQL_TRIGGER_EXISTS_IN_SCHEMA, expectedArgs)).thenReturn(1);
        assertTrue(dialect.doesTriggerExistOnPlatform(null, null, "App", "Test_Mixed_Case", "SYM_ON_I_FOR_TEST"));
        verify(sqlTemplate, times(1)).queryForInt(HanaSymmetricDialect.SQL_TRIGGER_EXISTS_IN_SCHEMA, expectedArgs);
    }

    @Test
    void testDoesTriggerExistOnPlatformUsesDefaultSchema() {
        when(platform.getDefaultSchema()).thenReturn("SYM");
        Object[] expectedArgs = new Object[] { "SYM_ON_I_FOR_TEST", "TEST_TABLE", "TEST_TABLE", "SYM", "SYM" };
        when(sqlTemplate.queryForInt(HanaSymmetricDialect.SQL_TRIGGER_EXISTS_IN_SCHEMA, expectedArgs)).thenReturn(1);
        assertTrue(dialect.doesTriggerExistOnPlatform(null, null, null, "TEST_TABLE", "SYM_ON_I_FOR_TEST"));
    }

    @Test
    void testDoesTriggerExistOnPlatformWithoutSchema() {
        Object[] expectedArgs = new Object[] { "SYM_ON_I_FOR_TEST", "test_table", "TEST_TABLE" };
        when(sqlTemplate.queryForInt(HanaSymmetricDialect.SQL_TRIGGER_EXISTS, expectedArgs)).thenReturn(0);
        assertFalse(dialect.doesTriggerExistOnPlatform(null, null, null, "test_table", "SYM_ON_I_FOR_TEST"));
        verify(sqlTemplate, times(1)).queryForInt(HanaSymmetricDialect.SQL_TRIGGER_EXISTS, expectedArgs);
    }

    @Test
    void testTriggerExistsSqlMatchesExactAndUpperCaseTableName() {
        assertEquals("select count(*) from triggers where upper(trigger_name) = upper(?) and subject_table_name in (?, ?) and schema_name in (?, ?)",
                HanaSymmetricDialect.SQL_TRIGGER_EXISTS_IN_SCHEMA);
    }

    @Test
    void testGetDatabaseTimeSQL() {
        assertEquals("select current_timestamp from dummy", dialect.getDatabaseTimeSQL());
    }

    @Test
    void testGetTransactionTriggerExpression() {
        String tablePrefix = "SYM";
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        assertEquals("$(defaultSchema)" + tablePrefix + "_" + "transaction_id()", dialect.getTransactionTriggerExpression("", "", new Trigger()));
    }

    @Test
    void testIsUpdateTransactionSupportedOnHana2() {
        assertTrue(createDialect(2, "2.00.088.00.1760424921").isUpdateTransactionSupported());
    }

    @Test
    void testIsUpdateTransactionSupportedOnHana1Sps10() {
        assertTrue(createDialect(1, "1.00.100.00.1434512907").isUpdateTransactionSupported());
    }

    @Test
    void testIsUpdateTransactionSupportedOnHana1Sps09() {
        assertFalse(createDialect(1, "1.00.097.00.1434028111").isUpdateTransactionSupported());
    }

    @Test
    void testIsUpdateTransactionSupportedOnHana1UnknownRevision() {
        assertFalse(createDialect(1, null).isUpdateTransactionSupported());
    }

    @Test
    void testParseRevision() {
        assertEquals(88, HanaSymmetricDialect.parseRevision("2.00.088.00.1760424921"));
        assertEquals(122, HanaSymmetricDialect.parseRevision("1.00.122.05"));
        assertEquals(-1, HanaSymmetricDialect.parseRevision("2.00"));
        assertEquals(-1, HanaSymmetricDialect.parseRevision("1.00.abc.00"));
        assertEquals(-1, HanaSymmetricDialect.parseRevision(null));
    }

    @Test
    void testGetTransactionIdFunctionBodyOnHana2() {
        assertEquals(HanaSymmetricDialect.UPDATE_TRANSACTION_BODY, createDialect(2, "2.00.088.00").getTransactionIdFunctionBody());
    }

    @Test
    void testGetTransactionIdFunctionBodyOnHana1Sps09() {
        assertEquals(HanaSymmetricDialect.TRANSACTION_SLOT_BODY, createDialect(1, "1.00.097.00").getTransactionIdFunctionBody());
    }

    @Test
    void testGetTransactionIdFunctionName() {
        when(parameterService.getTablePrefix()).thenReturn("sym");
        assertEquals("sym_transaction_id", dialect.getTransactionIdFunctionName());
    }

    @Test
    void testCreateRequiredDatabaseObjectsReplacesOutdatedFunction() {
        HanaSymmetricDialect hana2Dialect = createDialect(2, "2.00.088.00");
        when(parameterService.getTablePrefix()).thenReturn("sym");
        String expectedEqualsSql = "select count(*) from functions where schema_name = current_schema and function_name = upper('sym_transaction_id')"
                + " and definition like ?";
        when(sqlTemplate.queryForInt(expectedEqualsSql, "%" + HanaSymmetricDialect.UPDATE_TRANSACTION_BODY + "%")).thenReturn(0);
        hana2Dialect.createRequiredDatabaseObjectsImpl(null);
        verify(sqlTemplate).update("CREATE OR REPLACE function sym_transaction_id returns output1 varchar(50) LANGUAGE SQLSCRIPT AS begin "
                + "output1 := to_varchar(CURRENT_UPDATE_TRANSACTION()); end");
    }

    @Test
    void testCreateRequiredDatabaseObjectsKeepsCurrentFunction() {
        HanaSymmetricDialect hana2Dialect = createDialect(2, "2.00.088.00");
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(sqlTemplate.queryForInt(anyString(), (Object) any())).thenReturn(1);
        hana2Dialect.createRequiredDatabaseObjectsImpl(null);
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testCreateRequiredDatabaseObjectsInstallsSlotFunctionOnHana1Sps09() {
        HanaSymmetricDialect hana1Dialect = createDialect(1, "1.00.097.00");
        when(parameterService.getTablePrefix()).thenReturn("sym");
        hana1Dialect.createRequiredDatabaseObjectsImpl(null);
        verify(sqlTemplate).update("CREATE OR REPLACE function sym_transaction_id returns output1 varchar(50) LANGUAGE SQLSCRIPT AS begin "
                + HanaSymmetricDialect.TRANSACTION_SLOT_BODY + " end");
    }

    @Test
    void testDropRequiredDatabaseObjectsDropsInstalledFunction() {
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(sqlTemplate.queryForInt("select count(*) from functions where schema_name = current_schema and function_name = upper('sym_transaction_id')"))
                .thenReturn(1);
        dialect.dropRequiredDatabaseObjects();
        verify(sqlTemplate).update("DROP FUNCTION sym_transaction_id");
    }

    @Test
    void testDropRequiredDatabaseObjectsSkipsMissingFunction() {
        when(parameterService.getTablePrefix()).thenReturn("sym");
        dialect.dropRequiredDatabaseObjects();
        verify(sqlTemplate, never()).update(anyString());
    }

    private HanaSymmetricDialect createDialect(int majorVersion, String productVersion) {
        when(sqlTemplate.getDatabaseMajorVersion()).thenReturn(majorVersion);
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn(productVersion);
        return new HanaSymmetricDialect(parameterService, platform);
    }
}
