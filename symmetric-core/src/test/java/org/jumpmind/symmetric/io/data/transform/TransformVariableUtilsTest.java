package org.jumpmind.symmetric.io.data.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransformVariableUtilsTest {
    private DataContext context;
    private Batch batch;
    private CsvData csvData;
    private TransformedData data;

    @BeforeEach
    void setup() {
        context = mock(DataContext.class);
        batch = mock(Batch.class);
        csvData = mock(CsvData.class);
        data = mock(TransformedData.class);
        when(context.getBatch()).thenReturn(batch);
        when(context.getData()).thenReturn(csvData);
    }

    @Test
    void testIsVariableExpression_withValidSyntax_returnsTrue() {
        assertTrue(TransformVariableUtils.isVariableExpression("$(" + TransformVariableUtils.OPTION_TIMESTAMP + ")"));
    }

    @Test
    void testIsVariableExpression_withSingleCharName_returnsTrue() {
        assertTrue(TransformVariableUtils.isVariableExpression("$(x)"));
    }

    @Test
    void testIsVariableExpression_withoutDollarPrefix_returnsFalse() {
        assertFalse(TransformVariableUtils.isVariableExpression(TransformVariableUtils.OPTION_TIMESTAMP));
    }

    @Test
    void testIsVariableExpression_withEmptyBody_returnsFalse() {
        assertFalse(TransformVariableUtils.isVariableExpression("$()"));
    }

    @Test
    void testIsVariableExpression_withNull_returnsFalse() {
        assertFalse(TransformVariableUtils.isVariableExpression(null));
    }

    @Test
    void testIsVariableExpression_withEmptyString_returnsFalse() {
        assertFalse(TransformVariableUtils.isVariableExpression(""));
    }

    @Test
    void testIsVariableExpression_withOnlyPrefix_returnsFalse() {
        assertFalse(TransformVariableUtils.isVariableExpression("$("));
    }

    @Test
    void testIsVariableExpression_withOnlySuffix_returnsFalse() {
        assertFalse(TransformVariableUtils.isVariableExpression(")"));
    }

    @Test
    void testExtractVariableName_returnsNameWithoutWrapper() {
        assertEquals(TransformVariableUtils.OPTION_TIMESTAMP,
                TransformVariableUtils.extractVariableName("$(" + TransformVariableUtils.OPTION_TIMESTAMP + ")"));
    }

    @Test
    void testExtractVariableName_returnsSingleCharName() {
        assertEquals("x", TransformVariableUtils.extractVariableName("$(x)"));
    }

    @Test
    void testGetOptions_containsAllExpectedOptions() {
        String[] options = TransformVariableUtils.getOptions();
        assertNotNull(options);
        assertTrue(options.length > 0);
        assertContains(options, TransformVariableUtils.OPTION_TIMESTAMP);
        assertContains(options, TransformVariableUtils.OPTION_TIMESTAMP_UTC);
        assertContains(options, TransformVariableUtils.OPTION_DATE);
        assertContains(options, TransformVariableUtils.OPTION_SOURCE_NODE_ID);
        assertContains(options, TransformVariableUtils.OPTION_TARGET_NODE_ID);
        assertContains(options, TransformVariableUtils.OPTION_SOURCE_NODE_ID_FROM_DATA);
        assertContains(options, TransformVariableUtils.OPTION_NULL);
        assertContains(options, TransformVariableUtils.OPTION_OLD_VALUE);
        assertContains(options, TransformVariableUtils.OPTION_SOURCE_TABLE_NAME);
        assertContains(options, TransformVariableUtils.OPTION_SOURCE_CATALOG_NAME);
        assertContains(options, TransformVariableUtils.OPTION_SOURCE_SCHEMA_NAME);
        assertContains(options, TransformVariableUtils.OPTION_SOURCE_DML_TYPE);
        assertContains(options, TransformVariableUtils.OPTION_BATCH_ID);
        assertContains(options, TransformVariableUtils.OPTION_BATCH_START_TIME);
        assertContains(options, TransformVariableUtils.OPTION_DELETE_INDICATOR_FLAG);
    }

    @Test
    void testResolveExpression_withVariableSyntax_resolvesVariable() {
        String result = TransformVariableUtils.resolveExpression("$(" + TransformVariableUtils.OPTION_TIMESTAMP + ")",
                context, data, null);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
    }

    @Test
    void testResolveExpression_withPlainString_returnsAsIs() {
        assertEquals("DEFAULT", TransformVariableUtils.resolveExpression("DEFAULT", context, data, null));
    }

    @Test
    void testResolveExpression_withNull_returnsNull() {
        assertNull(TransformVariableUtils.resolveExpression(null, context, data, null));
    }

    @Test
    void testResolveExpression_withEmptyString_returnsEmptyString() {
        assertEquals("", TransformVariableUtils.resolveExpression("", context, data, null));
    }

    @Test
    void testResolveExpression_withUnknownVariable_returnsNull() {
        assertNull(TransformVariableUtils.resolveExpression("$(unknown_variable)", context, data, null));
    }

    @Test
    void testResolveVariable_systemTimestamp_returnsFormattedTimestamp() {
        String result = TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_TIMESTAMP, context, data, null);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                "Expected pattern yyyy-MM-dd HH:mm:ss.SSS but was: " + result);
    }

    @Test
    void testResolveVariable_systemTimestamp_isCaseInsensitive() {
        String result = TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_TIMESTAMP.toUpperCase(),
                context, data, null);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
    }

    @Test
    void testResolveVariable_systemTimestampUtc_returnsUtcTimestamp() {
        String result = TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_TIMESTAMP_UTC, context, data, null);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                "Expected pattern yyyy-MM-dd HH:mm:ss.SSS but was: " + result);
    }

    @Test
    void testResolveVariable_systemDate_returnsFormattedDate() {
        String result = TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_DATE, context, data, null);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
                "Expected pattern yyyy-MM-dd but was: " + result);
    }

    @Test
    void testResolveVariable_sourceNodeId_returnsFromBatch() {
        when(batch.getSourceNodeId()).thenReturn("node-001");
        assertEquals("node-001", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_SOURCE_NODE_ID,
                context, data, null));
    }

    @Test
    void testResolveVariable_sourceNodeId_isCaseInsensitive() {
        when(batch.getSourceNodeId()).thenReturn("node-001");
        assertEquals("node-001", TransformVariableUtils
                .resolveVariable(TransformVariableUtils.OPTION_SOURCE_NODE_ID.toUpperCase(), context, data, null));
    }

    @Test
    void testResolveVariable_targetNodeId_returnsFromBatch() {
        when(batch.getTargetNodeId()).thenReturn("node-002");
        assertEquals("node-002", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_TARGET_NODE_ID,
                context, data, null));
    }

    @Test
    void testResolveVariable_sourceNodeIdFromData_returnsFromCsvData() {
        when(csvData.getAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID)).thenReturn("node-003");
        assertEquals("node-003", TransformVariableUtils
                .resolveVariable(TransformVariableUtils.OPTION_SOURCE_NODE_ID_FROM_DATA, context, data, null));
    }

    @Test
    void testResolveVariable_oldColumnValue_returnsOldValue() {
        assertEquals("original", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_OLD_VALUE,
                context, data, "original"));
    }

    @Test
    void testResolveVariable_oldColumnValue_returnsNullWhenOldValueIsNull() {
        assertNull(TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_OLD_VALUE, context, data, null));
    }

    @Test
    void testResolveVariable_oldColumnValue_isCaseInsensitive() {
        assertEquals("val", TransformVariableUtils
                .resolveVariable(TransformVariableUtils.OPTION_OLD_VALUE.toUpperCase(), context, data, "val"));
    }

    @Test
    void testResolveVariable_nullOption_returnsNull() {
        assertNull(TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_NULL, context, data, "anything"));
    }

    @Test
    void testResolveVariable_sourceTableName_returnsFromTriggerHistory() {
        mockTriggerHistory("MY_TABLE", "MY_CATALOG", "MY_SCHEMA");
        assertEquals("MY_TABLE", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_SOURCE_TABLE_NAME,
                context, data, null));
    }

    @Test
    void testResolveVariable_sourceCatalogName_returnsFromTriggerHistory() {
        mockTriggerHistory("MY_TABLE", "MY_CATALOG", "MY_SCHEMA");
        assertEquals("MY_CATALOG", TransformVariableUtils
                .resolveVariable(TransformVariableUtils.OPTION_SOURCE_CATALOG_NAME, context, data, null));
    }

    @Test
    void testResolveVariable_sourceSchemaName_returnsFromTriggerHistory() {
        mockTriggerHistory("MY_TABLE", "MY_CATALOG", "MY_SCHEMA");
        assertEquals("MY_SCHEMA", TransformVariableUtils
                .resolveVariable(TransformVariableUtils.OPTION_SOURCE_SCHEMA_NAME, context, data, null));
    }

    @Test
    void testResolveVariable_sourceTableName_withNullContextData_returnsNull() {
        when(context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA)).thenReturn(null);
        assertNull(TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_SOURCE_TABLE_NAME, context, data, null));
    }

    @Test
    void testResolveVariable_sourceTableName_withNullTriggerHistory_returnsNull() {
        Data modelData = mock(Data.class);
        when(context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA)).thenReturn(modelData);
        when(modelData.getTriggerHistory()).thenReturn(null);
        assertNull(TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_SOURCE_TABLE_NAME, context, data, null));
    }

    @Test
    void testResolveVariable_sourceDmlType_returnsAsString() {
        when(data.getSourceDmlType()).thenReturn(DataEventType.INSERT);
        assertEquals(DataEventType.INSERT.toString(),
                TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_SOURCE_DML_TYPE, context, data, null));
    }

    @Test
    void testResolveVariable_sourceDmlType_forDelete_returnsDeleteString() {
        when(data.getSourceDmlType()).thenReturn(DataEventType.DELETE);
        assertEquals(DataEventType.DELETE.toString(),
                TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_SOURCE_DML_TYPE, context, data, null));
    }

    @Test
    void testResolveVariable_batchId_returnsAsString() {
        when(batch.getBatchId()).thenReturn(42L);
        assertEquals("42", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_BATCH_ID, context, data, null));
    }

    @Test
    void testResolveVariable_batchStartTime_returnsFormattedTimestamp() {
        when(batch.getStartTime()).thenReturn(new Date(0));
        String result = TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_BATCH_START_TIME, context, data, null);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                "Expected pattern yyyy-MM-dd HH:mm:ss.SSS but was: " + result);
    }

    @Test
    void testResolveVariable_deleteIndicatorFlag_returnsYForDelete() {
        when(data.getSourceDmlType()).thenReturn(DataEventType.DELETE);
        assertEquals("Y", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_DELETE_INDICATOR_FLAG,
                context, data, null));
    }

    @Test
    void testResolveVariable_deleteIndicatorFlag_returnsNForInsert() {
        when(data.getSourceDmlType()).thenReturn(DataEventType.INSERT);
        assertEquals("N", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_DELETE_INDICATOR_FLAG,
                context, data, null));
    }

    @Test
    void testResolveVariable_deleteIndicatorFlag_returnsNForUpdate() {
        when(data.getSourceDmlType()).thenReturn(DataEventType.UPDATE);
        assertEquals("N", TransformVariableUtils.resolveVariable(TransformVariableUtils.OPTION_DELETE_INDICATOR_FLAG,
                context, data, null));
    }

    @Test
    void testResolveVariable_unknownVariable_returnsNull() {
        assertNull(TransformVariableUtils.resolveVariable("not_a_variable", context, data, null));
    }

    @Test
    void testResolveVariable_nullVariable_returnsNull() {
        assertNull(TransformVariableUtils.resolveVariable(null, context, data, null));
    }

    private void mockTriggerHistory(String tableName, String catalogName, String schemaName) {
        Data modelData = mock(Data.class);
        TriggerHistory triggerHistory = mock(TriggerHistory.class);
        when(context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA)).thenReturn(modelData);
        when(modelData.getTriggerHistory()).thenReturn(triggerHistory);
        when(triggerHistory.getSourceTableName()).thenReturn(tableName);
        when(triggerHistory.getSourceCatalogName()).thenReturn(catalogName);
        when(triggerHistory.getSourceSchemaName()).thenReturn(schemaName);
    }

    private static void assertContains(String[] array, String value) {
        for (String item : array) {
            if (item.equals(value)) {
                return;
            }
        }
        throw new AssertionError("Expected array to contain <" + value + ">");
    }
}
