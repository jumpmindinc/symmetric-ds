package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.junit.jupiter.api.Test;

class SymmetricUtilsTest {
    // --- quote ---
    @Test
    void testQuoteWithDelimiter() {
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseInfo dbInfo = new DatabaseInfo();
        dbInfo.setDelimiterToken("\"");
        when(dialect.getPlatform()).thenReturn(platform);
        when(platform.getDatabaseInfo()).thenReturn(dbInfo);
        assertEquals("\"myTable\"", SymmetricUtils.quote(dialect, "myTable"));
    }

    @Test
    void testQuoteWithBlankDelimiter() {
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseInfo dbInfo = new DatabaseInfo();
        dbInfo.setDelimiterToken("");
        when(dialect.getPlatform()).thenReturn(platform);
        when(platform.getDatabaseInfo()).thenReturn(dbInfo);
        assertEquals("myTable", SymmetricUtils.quote(dialect, "myTable"));
    }
    // --- replaceNodeVariables ---

    @Test
    void testReplaceNodeVariablesBothNodes() {
        Node source = new Node();
        source.setNodeId("src-001");
        source.setExternalId("ext-src");
        source.setNodeGroupId("group-src");
        Node target = new Node();
        target.setNodeId("tgt-001");
        target.setExternalId("ext-tgt");
        target.setNodeGroupId("group-tgt");
        String result = SymmetricUtils.replaceNodeVariables(source, target,
                "$(sourceNodeId)/$(targetNodeId)/$(sourceNodeGroupId)/$(targetNodeGroupId)");
        assertEquals("src-001/group-tgt/group-src/group-tgt", result);
    }

    @Test
    void testReplaceNodeVariablesNullSourceNode() {
        Node target = new Node();
        target.setNodeId("tgt-001");
        target.setExternalId("ext-tgt");
        target.setNodeGroupId("group-tgt");
        String result = SymmetricUtils.replaceNodeVariables(null, target,
                "$(targetExternalId)");
        assertEquals("ext-tgt", result);
    }

    @Test
    void testReplaceNodeVariablesNullTargetNode() {
        Node source = new Node();
        source.setNodeId("src-001");
        source.setExternalId("ext-src");
        source.setNodeGroupId("group-src");
        String result = SymmetricUtils.replaceNodeVariables(source, null,
                "$(sourceNodeId)/$(sourceExternalId)");
        assertEquals("src-001/ext-src", result);
    }

    @Test
    void testReplaceNodeVariablesBothNull() {
        String input = "no variables here";
        assertEquals(input, SymmetricUtils.replaceNodeVariables(null, null, input));
    }
    // --- replaceCatalogSchemaVariables ---

    @Test
    void testReplaceCatalogSchemaVariablesAllProvided() {
        String result = SymmetricUtils.replaceCatalogSchemaVariables(
                "mycat", "defaultcat", "myschema", "defaultschema",
                "$(sourceCatalogName).$(sourceSchemaName).table");
        assertEquals("mycat.myschema.table", result);
    }

    @Test
    void testReplaceCatalogSchemaVariablesNullCatalogFallsToDefault() {
        String result = SymmetricUtils.replaceCatalogSchemaVariables(
                null, "defaultcat", "myschema", "defaultschema",
                "$(sourceCatalogName).$(sourceSchemaName).table");
        assertEquals("defaultcat.myschema.table", result);
    }

    @Test
    void testReplaceCatalogSchemaVariablesNullSchemaFallsToDefault() {
        String result = SymmetricUtils.replaceCatalogSchemaVariables(
                "mycat", "defaultcat", null, "defaultschema",
                "$(sourceCatalogName).$(sourceSchemaName).table");
        assertEquals("mycat.defaultschema.table", result);
    }

    @Test
    void testReplaceCatalogSchemaVariablesBothNullNoDefault() {
        String input = "$(sourceCatalogName).$(sourceSchemaName).table";
        String result = SymmetricUtils.replaceCatalogSchemaVariables(
                null, null, null, null, input);
        assertEquals(input, result);
    }
    // --- substituteScripts ---

    @Test
    void testSubstituteScriptsNoBacktick() {
        String input = "no script here";
        assertEquals(input, SymmetricUtils.substituteScripts(input, null));
    }

    @Test
    void testSubstituteScriptsSimpleExpression() {
        String result = SymmetricUtils.substituteScripts("prefix`1 + 1`suffix", null);
        assertEquals("prefix2suffix", result);
    }

    @Test
    void testSubstituteScriptsWithReplacementValues() {
        Map<String, String> values = new HashMap<>();
        values.put("externalId", "node42");
        String result = SymmetricUtils.substituteScripts("`externalId`", values);
        assertEquals("node42", result);
    }

    @Test
    void testSubstituteScriptsSingleBacktickNoOp() {
        String input = "only`one";
        assertEquals(input, SymmetricUtils.substituteScripts(input, null));
    }
    // --- getDeploymentSubType ---

    @Test
    void testGetDeploymentSubTypeNullProperties() {
        assertEquals(Constants.DEPLOYMENT_SUB_TYPE_TRIGGER_BASED,
                SymmetricUtils.getDeploymentSubType(null));
    }

    @Test
    void testGetDeploymentSubTypeDefaultTriggerBased() {
        assertEquals(Constants.DEPLOYMENT_SUB_TYPE_TRIGGER_BASED,
                SymmetricUtils.getDeploymentSubType(new Properties()));
    }

    @Test
    void testGetDeploymentSubTypeLoadOnly() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.NODE_LOAD_ONLY, "true");
        assertEquals(Constants.DEPLOYMENT_SUB_TYPE_LOAD_ONLY,
                SymmetricUtils.getDeploymentSubType(props));
    }

    @Test
    void testGetDeploymentSubTypeExtractOnly() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.NODE_LOAD_ONLY, "true");
        props.setProperty("db.url", "jdbc:h2:file:/some/path/extract-only");
        assertEquals(Constants.DEPLOYMENT_SUB_TYPE_EXTRACT_ONLY,
                SymmetricUtils.getDeploymentSubType(props));
    }

    @Test
    void testGetDeploymentSubTypeLogBased() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.NODE_LOAD_ONLY, "true");
        props.setProperty(ParameterConstants.START_LOG_MINER_JOB, "true");
        assertEquals(Constants.DEPLOYMENT_SUB_TYPE_LOG_BASED,
                SymmetricUtils.getDeploymentSubType(props));
    }

    @Test
    void testGetDeploymentSubTypeTimeBased() {
        Properties props = new Properties();
        props.setProperty(ParameterConstants.NODE_LOAD_ONLY, "true");
        props.setProperty(ParameterConstants.START_LOG_MINER_JOB, "true");
        props.setProperty(ParameterConstants.CAPTURE_TYPE_TIME_BASED, "true");
        assertEquals(Constants.DEPLOYMENT_SUB_TYPE_TIME_BASED,
                SymmetricUtils.getDeploymentSubType(props));
    }
    // --- filterTransactions ---

    @Test
    void testFilterTransactionsUserMatchNotBlocking() {
        Transaction t = new Transaction("1", "user1", null, new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        List<Transaction> filtered = new ArrayList<>();
        // isBlocking=false, blockingTransaction=null → exits early
        boolean result = SymmetricUtils.filterTransactions(t, txMap, filtered, "user1", false, false);
        assertFalse(result);
        assertTrue(filtered.isEmpty());
    }

    @Test
    void testFilterTransactionsUserMatchIsBlocking() {
        Transaction t = new Transaction("1", "user1", null, new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        List<Transaction> filtered = new ArrayList<>();
        boolean result = SymmetricUtils.filterTransactions(t, txMap, filtered, "user1", false, true);
        assertTrue(result);
        assertTrue(filtered.contains(t));
    }

    @Test
    void testFilterTransactionsIsBlockingUserFlag() {
        Transaction t = new Transaction("1", "other", null, new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        List<Transaction> filtered = new ArrayList<>();
        boolean result = SymmetricUtils.filterTransactions(t, txMap, filtered, "user1", true, true);
        assertTrue(result);
        assertTrue(filtered.contains(t));
    }

    @Test
    void testFilterTransactionsBlockingChainIncluded() {
        Transaction t1 = new Transaction("t1", "user1", null, new Date(), "");
        Transaction t2 = new Transaction("t2", "other", "t1", new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        txMap.put("t1", t1);
        txMap.put("t2", t2);
        List<Transaction> filtered = new ArrayList<>();
        boolean result = SymmetricUtils.filterTransactions(t2, txMap, filtered, "user1", false, false);
        assertTrue(result);
        assertTrue(filtered.contains(t1));
        assertTrue(filtered.contains(t2));
    }

    @Test
    void testFilterTransactionsAlreadyInFiltered() {
        Transaction t = new Transaction("1", "user1", null, new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        List<Transaction> filtered = new ArrayList<>();
        filtered.add(t);
        boolean result = SymmetricUtils.filterTransactions(t, txMap, filtered, "user1", false, true);
        assertTrue(result);
        assertEquals(1, filtered.size());
    }

    @Test
    void testFilterTransactionsLevelExceeded() {
        Transaction t = new Transaction("1", "user1", null, new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        List<Transaction> filtered = new ArrayList<>();
        boolean result = SymmetricUtils.filterTransactions(t, txMap, filtered, "user1", false, true, 501);
        assertFalse(result);
        assertTrue(filtered.isEmpty());
    }

    @Test
    void testFilterTransactionsNoMatchNoBlocker() {
        Transaction t = new Transaction("1", "other", null, new Date(), "");
        Map<String, Transaction> txMap = new HashMap<>();
        List<Transaction> filtered = new ArrayList<>();
        boolean result = SymmetricUtils.filterTransactions(t, txMap, filtered, "user1", false, true);
        assertFalse(result);
        assertTrue(filtered.isEmpty());
    }
    // --- randomString ---

    @Test
    void testRandomStringNotNull() {
        assertNotNull(SymmetricUtils.randomString(10));
    }

    @Test
    void testRandomStringLengthWithinBounds() {
        for (int i = 0; i < 100; i++) {
            String s = SymmetricUtils.randomString(10);
            assertTrue(s.length() >= 1 && s.length() < 10,
                    "Length " + s.length() + " out of bounds");
        }
    }

    @Test
    void testRandomStringOnlyAlphaChars() {
        for (int i = 0; i < 50; i++) {
            String s = SymmetricUtils.randomString(20);
            assertTrue(s.matches("[A-Za-z]+"), "Non-alpha chars in: " + s);
        }
    }

    @Test
    void testRandomStringMaxLengthOne() {
        for (int i = 0; i < 20; i++) {
            String s = SymmetricUtils.randomString(1);
            assertEquals(1, s.length());
        }
    }
}
