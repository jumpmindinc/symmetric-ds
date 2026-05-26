package org.jumpmind.symmetric.db;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Types;
import java.util.HashMap;

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.MockParameterService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AbstractTriggerTemplateTest {
    private static IDatabasePlatform platform;
    private static MockAbstractSymmetricDialect symmetricDialect;
    private static WrapperAbstractTriggerTemplate triggerTemplate;
    private static String tablePrefix = "TST";
    private static String defaultCatalog = "C_TEST";
    private static String defaultSchema = "S_TEST";
    private static Channel channel = new Channel("CH_TEST", 0);

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
        symmetricDialect = new AbstractTriggerTemplateTest.MockAbstractSymmetricDialect(platform);
        triggerTemplate = new AbstractTriggerTemplateTest.WrapperAbstractTriggerTemplate(symmetricDialect);
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
    }

    /**
     * Helper builds a table where odd-numbered columns are NULLable and is part of the unique index
     * 
     */
    public Table buildTable_UniqueIndexWithNullableColumns(String tableName, int indexColumnCount, int additionalColumnCount) {
        Column[] allColumns = new Column[1 + indexColumnCount + additionalColumnCount];
        IIndex uniqueIndex = new UniqueIndex("unique_with_nullable_columns");
        int currentColumnNo = 0;
        for (int indexColumnNo = 0; indexColumnNo < indexColumnCount; indexColumnNo++) {
            String columnName = String.format("col%d_indexed", currentColumnNo + 1);
            Column indexColumn = new Column(columnName);
            indexColumn.setMappedType("INTEGER");
            indexColumn.setJdbcTypeCode(Types.INTEGER);
            indexColumn.setJdbcTypeName("INTEGER");
            indexColumn.setPrimaryKey(true);
            if (currentColumnNo % 2 == 0) {
                columnName += "_nullable";
                indexColumn.setName(columnName);
                indexColumn.setRequired(false);
            }
            allColumns[currentColumnNo++] = indexColumn;
            uniqueIndex.addColumn(new IndexColumn(indexColumn));
        }
        for (int additionalColumnNo = 0; additionalColumnNo < additionalColumnCount; additionalColumnNo++) {
            String columnName = String.format("col%d", currentColumnNo + 1);
            Column indexColumn = new Column(columnName);
            indexColumn.setMappedType("INTEGER");
            indexColumn.setJdbcTypeCode(Types.INTEGER);
            indexColumn.setJdbcTypeName("INTEGER");
            if (currentColumnNo % 2 == 0) {
                columnName += "_nullable";
                indexColumn.setName(columnName);
            } else {
                indexColumn.setRequired(true);
            }
            allColumns[currentColumnNo++] = indexColumn;
        }
        Table table = new Table(defaultCatalog + "." + defaultSchema + "." + tableName, allColumns);
        table.addIndex(uniqueIndex);
        return table;
    }

    @Test
    void testPrimaryKeyJoin_UniqueIndexWith1NullableColumn() {
        // Arrange
        Table table = buildTable_UniqueIndexWithNullableColumns("TBL1", 1, 2);
        Trigger trigger = new Trigger(table.getFullyQualifiedName(), channel.getChannelId());
        TriggerHistory triggerHistory = new TriggerHistory(table, trigger, triggerTemplate);
        Table targetTable = table.copyAndFilterColumns(triggerHistory.getParsedColumnNames(), triggerHistory.getParsedPkColumnNames(), true, false);
        String uniqueColumnName = table.getColumn(0).getName();
        String templateDdl = "$(oldNewPrimaryKeyJoin)";
        // Act
        String triggerDdl = triggerTemplate.replaceTemplateVariables(DataEventType.UPDATE, trigger, triggerHistory, channel, tablePrefix, table, targetTable,
                defaultCatalog, defaultSchema, templateDdl);
        // Assert
        String expectedPkJoin = "(deleted.\"" + uniqueColumnName + "\"=inserted.\"" + uniqueColumnName + "\" or (deleted.\"" + uniqueColumnName
                + "\" is null and inserted.\"" + uniqueColumnName + "\" is null))";
        // System.out.println("EXPECTED> " + expectedPkJoin);
        // System.out.println("RESULT==> " + triggerDdl);
        assertNotNull(triggerDdl);
        assertTrue(triggerDdl.equals(expectedPkJoin));
    }

    @Test
    void testPrimaryKeyJoin_UniqueIndexWith2NullableColumns() {
        // Arrange
        Table table = buildTable_UniqueIndexWithNullableColumns("TBL2", 2, 3);
        Trigger trigger = new Trigger(table.getFullyQualifiedName(), channel.getChannelId());
        TriggerHistory triggerHistory = new TriggerHistory(table, trigger, triggerTemplate);
        Table targetTable = table.copyAndFilterColumns(triggerHistory.getParsedColumnNames(), triggerHistory.getParsedPkColumnNames(), true, false);
        String uniqueColumnName = table.getColumn(0).getName();
        String templateDdl = "$(oldNewPrimaryKeyJoin)";
        // Act
        String triggerDdl = triggerTemplate.replaceTemplateVariables(DataEventType.UPDATE, trigger, triggerHistory, channel, tablePrefix, table, targetTable,
                defaultCatalog, defaultSchema, templateDdl);
        // Assert
        String expectedPkJoin = "(deleted.\"" + uniqueColumnName + "\"=inserted.\"" + uniqueColumnName + "\" or (deleted.\"" + uniqueColumnName
                + "\" is null and inserted.\"" + uniqueColumnName + "\" is null)) and deleted.\"col2_indexed\"=inserted.\"col2_indexed\"";
        // System.out.println("EXPECTED> " + expectedPkJoin);
        // System.out.println("RESULT==> " + triggerDdl);
        assertNotNull(triggerDdl);
        assertTrue(triggerDdl.equals(expectedPkJoin));
    }

    @Test
    void testPrimaryKeyJoinVar_UniqueIndexWith2NullableColumns() {
        // Arrange
        Table table = buildTable_UniqueIndexWithNullableColumns("TBL3", 2, 3);
        Trigger trigger = new Trigger(table.getFullyQualifiedName(), channel.getChannelId());
        TriggerHistory triggerHistory = new TriggerHistory(table, trigger, triggerTemplate);
        Table targetTable = table.copyAndFilterColumns(triggerHistory.getParsedColumnNames(), triggerHistory.getParsedPkColumnNames(), true, false);
        String uniqueColumnName = table.getColumn(0).getName();
        String templateDdl = "$(varOldPrimaryKeyJoin)";
        // Act
        String triggerDdl = triggerTemplate.replaceTemplateVariables(DataEventType.UPDATE, trigger, triggerHistory, channel, tablePrefix, table, targetTable,
                defaultCatalog, defaultSchema, templateDdl);
        // Assert
        String expectedPkJoin = "(deleted.\"" + uniqueColumnName + "\"=@oldpk0 or (deleted.\"" + uniqueColumnName
                + "\" is null and @oldpk0 is null)) and deleted.\"col2_indexed\"=@oldpk1";
        // System.out.println("EXPECTED> " + expectedPkJoin);
        // System.out.println("RESULT==> " + triggerDdl);
        assertNotNull(triggerDdl);
        assertTrue(triggerDdl.equals(expectedPkJoin));
    }

    @Test
    void testPrimaryKeyJoinVar_UniqueIndexWith1NullableColumn() {
        // Arrange
        Table table = buildTable_UniqueIndexWithNullableColumns("TBL4", 1, 3);
        Trigger trigger = new Trigger(table.getFullyQualifiedName(), channel.getChannelId());
        TriggerHistory triggerHistory = new TriggerHistory(table, trigger, triggerTemplate);
        Table targetTable = table.copyAndFilterColumns(triggerHistory.getParsedColumnNames(), triggerHistory.getParsedPkColumnNames(), true, false);
        String uniqueColumnName = table.getColumn(0).getName();
        String templateDdl = "$(varOldPrimaryKeyJoin)";
        // Act
        String triggerDdl = triggerTemplate.replaceTemplateVariables(DataEventType.UPDATE, trigger, triggerHistory, channel, tablePrefix, table, targetTable,
                defaultCatalog, defaultSchema, templateDdl);
        // Assert
        // OLD> String expectedPkJoin = "deleted.\"" + uniqueColumnName + "\"=@oldpk0";
        String expectedPkJoin = "(deleted.\"" + uniqueColumnName + "\"=@oldpk0 or (deleted.\"" + uniqueColumnName
                + "\" is null and @oldpk0 is null))";
        // System.out.println("EXPECTED> " + expectedPkJoin);
        // System.out.println("RESULT==> " + triggerDdl);
        assertNotNull(triggerDdl);
        assertTrue(triggerDdl.equals(expectedPkJoin));
    }

    public static class WrapperAbstractTriggerTemplate extends AbstractTriggerTemplate {
        public WrapperAbstractTriggerTemplate(MockAbstractSymmetricDialect symmetricDialect) {
            super(symmetricDialect);
            sqlTemplates = new HashMap<String, String>();
            // Set up borrowed from the AseTriggerTemplate:
            String quote = "\"";
            emptyColumnTemplate = "null";
            triggerConcatCharacter = "+";
            newTriggerValue = "inserted";
            oldTriggerValue = "deleted";
            oldColumnPrefix = "";
            newColumnPrefix = "";
            stringColumnTemplate = "case when $(tableAlias)." + quote + "$(columnName)" + quote
                    + " is null then null else '\"' + str_replace(str_replace($(tableAlias)." + quote + "$(columnName)" + quote
                    + ",'\\','\\\\'),'\"','\\\"') + '\"' end";
            numberColumnTemplate = "case when $(tableAlias)." + quote + "$(columnName)" + quote
                    + " is null then null else ('\"' + convert(varchar,$(tableAlias)."
                    + quote + "$(columnName)" + quote + ") + '\"') end";
        }

        public void putSqlTemplate(String templateName, String templateSql) {
            sqlTemplates.put(templateName, templateSql);
        }
    }

    public static class MockAbstractSymmetricDialect extends AbstractSymmetricDialect {
        private static IParameterService parameterService = new MockParameterService(
                ParameterConstants.EXTERNAL_ID_IS_UNIQUE, "true");

        public MockAbstractSymmetricDialect(IDatabasePlatform platform) {
            super(parameterService, platform);
        }

        @Override
        public void cleanDatabase() {
            // TODO Auto-generated method stub
        }

        @Override
        public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
            // TODO Auto-generated method stub
        }

        @Override
        public void enableSyncTriggers(ISqlTransaction transaction) {
            // TODO Auto-generated method stub
        }

        @Override
        public String getSyncTriggersExpression() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void dropRequiredDatabaseObjects() {
            // TODO Auto-generated method stub
        }

        @Override
        public BinaryEncoding getBinaryEncoding() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        protected boolean doesTriggerExistOnPlatform(StringBuilder seqlBuffer, String catalogName, String schema, String tableName, String triggerName) {
            // TODO Auto-generated method stub
            return false;
        }
    }
}