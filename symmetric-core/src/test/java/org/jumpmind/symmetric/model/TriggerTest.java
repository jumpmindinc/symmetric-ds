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
package org.jumpmind.symmetric.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.Test;

class TriggerTest {
    @Test
    void testFilterExcludedColumnsForNull() throws Exception {
        Trigger trigger = new Trigger();
        assertArrayEquals("Expected empty column array", new Column[0], trigger.filterExcludedColumns(null));
    }

    @Test
    void testFilterExcludedColumns() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setExcludedColumnNames("col1, col3, col5");
        Column[] param = buildColumnArray();
        Column[] expected = new Column[] { param[1], param[3], param[5] };
        assertArrayEquals("Excluded wrong set of columns", expected, trigger.filterExcludedColumns(param));
    }

    @Test
    void testFilterIncludedColumnsForNull() throws Exception {
        Trigger trigger = new Trigger();
        assertArrayEquals("Expected empty column array", new Column[0], trigger.filterIncludedColumns(null));
    }

    @Test
    void testFilterIncludedColumns() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setIncludedColumnNames("col1, col3, col5");
        Column[] param = buildColumnArray();
        Column[] expected = new Column[] { param[0], param[2], param[4] };
        assertArrayEquals("Included wrong set of columns", expected, trigger.filterIncludedColumns(param));
    }

    @Test
    void testFilterExcludedAndIncludedColumnsMutuallyExclusive() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setIncludedColumnNames("col1, col2");
        trigger.setExcludedColumnNames("col3, col4");
        Column[] param = buildColumnArray();
        Column[] expected = new Column[] { param[0], param[1] };
        assertArrayEquals("Filtered wrong set of columns", expected, trigger.filterExcludedAndIncludedColumns(param));
    }

    @Test
    void testFilterExcludedAndIncludedColumnsOverlap() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setIncludedColumnNames("col1, col2");
        trigger.setExcludedColumnNames("col2, col3");
        Column[] param = buildColumnArray();
        Column[] expected = new Column[] { param[0] };
        assertArrayEquals("Filtered wrong set of columns", expected, trigger.filterExcludedAndIncludedColumns(param));
    }

    @Test
    void testFilterExcludedAndIncludedColumnsExcludeOnly() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setExcludedColumnNames("col3, col4");
        trigger.setIncludedColumnNames("");
        Column[] param = buildColumnArray();
        Column[] expected = new Column[] { param[0], param[1], param[4], param[5] };
        assertArrayEquals("Filtered wrong set of columns", expected, trigger.filterExcludedAndIncludedColumns(param));
    }

    @Test
    void testFilterExcludedAndIncludedColumnsIncludeOnly() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setExcludedColumnNames("");
        trigger.setIncludedColumnNames("col1, col2");
        Column[] param = buildColumnArray();
        Column[] expected = new Column[] { param[0], param[1] };
        assertArrayEquals("Filtered wrong set of columns", expected, trigger.filterExcludedAndIncludedColumns(param));
    }

    @Test
    void testQualifiedSourceTableName() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceCatalogName("catalogTest");
        trigger.setSourceTableName("testTable");
        String qualifiedSourceTableName = trigger.qualifiedSourceTableName();
        String actualQualifiedSourceTableName = "catalogTest" + "." + "schemaTest" + "." + "testTable";
        assertEquals(actualQualifiedSourceTableName, qualifiedSourceTableName);
    }

    @Test
    void testQualifiedSourceTablePrefix() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceCatalogName("catalogTest");
        String schemaPlus = (trigger.getSourceSchemaName() != null ? trigger.getSourceSchemaName() + "." : "");
        String catalogPlus = (trigger.getSourceCatalogName() != null ? trigger.getSourceCatalogName() + "." : "") + schemaPlus;
        String actualQualifiedSourceTablePrefix = "catalogTest" + "." + "schemaTest" + ".";
        assertEquals(actualQualifiedSourceTablePrefix, catalogPlus);
    }

    @Test
    void testNullOutBlankFieldsBlankCatalogAndSchema() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setSourceCatalogName("");
        trigger.setSourceSchemaName("");
        trigger.setExternalSelect("         ");
        trigger.setExcludedColumnNames("         ");
        trigger.setIncludedColumnNames("         ");
        trigger.setSyncKeyNames("         ");
        trigger.setChannelExpression("         ");
        trigger.setCustomBeforeInsertText("         ");
        trigger.setCustomBeforeUpdateText("         ");
        trigger.setCustomBeforeDeleteText("         ");
        trigger.setCustomOnInsertText("         ");
        trigger.setCustomOnUpdateText("         ");
        trigger.setCustomOnDeleteText("         ");
        trigger.nullOutBlankFields();
        assertEquals(null, trigger.getSourceCatalogName());
        assertEquals(null, trigger.getSourceCatalogNameUnescaped());
        assertFalse(trigger.isSourceCatalogNameWildCarded());
        assertEquals(null, trigger.getSourceSchemaName());
        assertEquals(null, trigger.getSourceSchemaNameUnescaped());
        assertFalse(trigger.isSourceSchemaNameWildCarded());
        assertEquals(null, trigger.getExternalSelect());
        assertEquals(null, trigger.getExcludedColumnNames());
        assertEquals(null, trigger.getIncludedColumnNames());
        assertEquals(null, trigger.getSyncKeyNames());
        assertEquals(null, trigger.getChannelExpression());
        assertEquals(null, trigger.getCustomBeforeInsertText());
        assertEquals(null, trigger.getCustomBeforeUpdateText());
        assertEquals(null, trigger.getCustomBeforeDeleteText());
        assertEquals(null, trigger.getCustomOnInsertText());
        assertEquals(null, trigger.getCustomOnUpdateText());
        assertEquals(null, trigger.getCustomOnDeleteText());
        assertEquals("1=1", trigger.getSyncOnInsertCondition());
        assertEquals("1=1", trigger.getSyncOnDeleteCondition());
        assertEquals("1=1", trigger.getSyncOnUpdateCondition());
    }

    @Test
    void testNullOutBlankFieldsNotBlankCatalogAndSchema() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setSourceCatalogName("testCatalog");
        trigger.setSourceSchemaName("testSchema");
        trigger.nullOutBlankFields();
        assertEquals("testCatalog", trigger.getSourceCatalogName());
        assertEquals("testSchema", trigger.getSourceSchemaName());
    }

    @Test
    void testGetSyncKeysColumnsForTable() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        Column testColumn = new Column();
        testColumn.setName("Test1");
        testColumn.setPrimaryKey(true);
        testTable.addColumn(testColumn);
        trigger.setSyncKeyNames("Test1");
        Column actualColumn[] = trigger.getSyncKeysColumnsForTable(testTable);
        Column expectedColumn[] = { testColumn };
        assertArrayEquals(expectedColumn, actualColumn);
    }

    @Test
    void testOrderColumnsForTable() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        Column nonPKColumn = new Column();
        Column pkColumn = new Column();
        nonPKColumn.setName("nonPK");
        nonPKColumn.setPrimaryKey(false);
        testTable.addColumn(nonPKColumn);
        pkColumn.setName("pk1");
        pkColumn.setPrimaryKey(true);
        testTable.addColumn(pkColumn);
        Column actualColumn[] = trigger.orderColumnsForTable(testTable);
        Column expectedColumn[] = { pkColumn, nonPKColumn };
        assertArrayEquals(expectedColumn, actualColumn);
    }

    @Test
    void testNullOrderColumnsForTable() throws Exception {
        Trigger trigger = new Trigger();
        Column actualColumn[] = trigger.orderColumnsForTable(null);
        Column expectedColumn[] = {};
        assertArrayEquals(expectedColumn, actualColumn);
    }

    @Test
    void testTwoPKOrderColumnsForTable() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        Column nonPKColumn = new Column("nonPK", false);
        Column pkColumn = new Column("pk1", true);
        Column pkColumn2 = new Column("pk2", true);
        testTable.addColumn(pkColumn2);
        testTable.addColumn(nonPKColumn);
        testTable.addColumn(pkColumn);
        Column actualColumn[] = trigger.orderColumnsForTable(testTable);
        Column expectedColumn[] = { pkColumn2, pkColumn, nonPKColumn };
        assertArrayEquals(expectedColumn, actualColumn);
    }

    @Test
    void testLongNotPKOrderColumnsForTable() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        Column nonPKColumn = new Column("nonPK", false);
        Column pkColumn = new Column("pk1", true);
        Column longColumn = new Column("longColumn", false);
        longColumn.setJdbcTypeName("LONG");
        testTable.addColumn(longColumn);
        testTable.addColumn(nonPKColumn);
        testTable.addColumn(pkColumn);
        Column actualColumn[] = trigger.orderColumnsForTable(testTable);
        Column expectedColumn[] = { pkColumn, nonPKColumn, longColumn };
        assertArrayEquals(expectedColumn, actualColumn);
    }

    @Test
    void testAllNullToHashedValue() throws Exception {
        Trigger trigger = new Trigger();
        long test = trigger.toHashedValue();
        long actualHashedValue = trigger.getTriggerId() != null ? trigger.getTriggerId().hashCode() : 0;
        actualHashedValue += trigger.getChannelId().hashCode();
        actualHashedValue += trigger.isSyncOnUpdate() ? "syncOnUpdate".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnInsert() ? "syncOnInsert".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnDelete() ? "syncOnDelete".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnIncomingBatch() ? "syncOnIncomingBatch".hashCode() : 0;
        actualHashedValue += trigger.isUseStreamLobs() ? "useStreamLobs".hashCode() : 0;
        actualHashedValue += trigger.isUseCaptureLobs() ? "useCaptureLobs".hashCode() : 0;
        actualHashedValue += trigger.isUseCaptureOldData() ? "useCaptureOldData".hashCode() : 0;
        actualHashedValue += trigger.isUseHandleKeyUpdates() ? "useHandleKeyUpdates".hashCode() : 0;
        actualHashedValue += trigger.getSyncOnUpdateCondition().hashCode();
        actualHashedValue += trigger.getSyncOnInsertCondition().hashCode();
        actualHashedValue += trigger.getSyncOnDeleteCondition().hashCode();
        assertEquals(actualHashedValue, test);
    }

    @Test
    void testNoNullToHashedValue() throws Exception {
        Trigger trigger = new Trigger();
        long actualHashedValue = trigger.getTriggerId() != null ? trigger.getTriggerId().hashCode() : 0;
        trigger.setSourceTableName("true");
        trigger.setChannelId("true");
        trigger.setSourceSchemaName("true");
        trigger.setSourceCatalogName("true");
        trigger.setSyncOnUpdate(true);
        trigger.setSyncOnInsert(true);
        trigger.setSyncOnDelete(true);
        trigger.setSyncOnIncomingBatch(true);
        trigger.setUseStreamLobs(true);
        trigger.setUseCaptureLobs(true);
        trigger.setUseCaptureOldData(true);
        trigger.setUseHandleKeyUpdates(true);
        trigger.setNameForInsertTrigger("true");
        trigger.setNameForUpdateTrigger("true");
        trigger.setNameForDeleteTrigger("true");
        trigger.setSyncOnUpdateCondition("true");
        trigger.setSyncOnInsertCondition("true");
        trigger.setSyncOnDeleteCondition("true");
        trigger.setCustomBeforeUpdateText("true");
        trigger.setCustomBeforeInsertText("true");
        trigger.setCustomBeforeDeleteText("true");
        trigger.setCustomOnUpdateText("true");
        trigger.setCustomOnInsertText("true");
        trigger.setCustomOnDeleteText("true");
        trigger.setExcludedColumnNames("true");
        trigger.setExternalSelect("true");
        trigger.setTxIdExpression("true");
        trigger.setSyncKeyNames("true");
        actualHashedValue += trigger.getSourceTableName().hashCode();
        actualHashedValue += trigger.getChannelId().hashCode();
        actualHashedValue += trigger.getSourceSchemaName().hashCode();
        actualHashedValue += trigger.getSourceCatalogName().hashCode();
        actualHashedValue += trigger.isSyncOnUpdate() ? "syncOnUpdate".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnInsert() ? "syncOnInsert".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnDelete() ? "syncOnDelete".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnIncomingBatch() ? "syncOnIncomingBatch".hashCode() : 0;
        actualHashedValue += trigger.isUseStreamLobs() ? "useStreamLobs".hashCode() : 0;
        actualHashedValue += trigger.isUseCaptureLobs() ? "useCaptureLobs".hashCode() : 0;
        actualHashedValue += trigger.isUseCaptureOldData() ? "useCaptureOldData".hashCode() : 0;
        actualHashedValue += trigger.isUseHandleKeyUpdates() ? "useHandleKeyUpdates".hashCode() : 0;
        actualHashedValue += trigger.getNameForInsertTrigger().hashCode();
        actualHashedValue += trigger.getNameForUpdateTrigger().hashCode();
        actualHashedValue += trigger.getNameForDeleteTrigger().hashCode();
        actualHashedValue += trigger.getSyncOnUpdateCondition().hashCode();
        actualHashedValue += trigger.getSyncOnInsertCondition().hashCode();
        actualHashedValue += trigger.getSyncOnDeleteCondition().hashCode();
        actualHashedValue += trigger.getCustomBeforeUpdateText().hashCode();
        actualHashedValue += trigger.getCustomBeforeInsertText().hashCode();
        actualHashedValue += trigger.getCustomBeforeDeleteText().hashCode();
        actualHashedValue += trigger.getCustomOnUpdateText().hashCode();
        actualHashedValue += trigger.getCustomOnInsertText().hashCode();
        actualHashedValue += trigger.getCustomOnDeleteText().hashCode();
        actualHashedValue += trigger.getExcludedColumnNames().hashCode();
        actualHashedValue += trigger.getExternalSelect().hashCode();
        actualHashedValue += trigger.getTxIdExpression().hashCode();
        actualHashedValue += trigger.getSyncKeyNames().hashCode();
        long test = trigger.toHashedValue();
        assertEquals(actualHashedValue, test);
    }

    @Test
    void testNoNullSomeFalseToHashedValue() throws Exception {
        Trigger trigger = new Trigger();
        long actualHashedValue = trigger.getTriggerId() != null ? trigger.getTriggerId().hashCode() : 0;
        trigger.setSourceTableName("true");
        trigger.setChannelId("true");
        trigger.setSourceSchemaName("true");
        trigger.setSourceCatalogName("true");
        trigger.setSyncOnUpdate(false);
        trigger.setSyncOnInsert(false);
        trigger.setSyncOnDelete(false);
        trigger.setSyncOnIncomingBatch(false);
        trigger.setUseStreamLobs(false);
        trigger.setUseCaptureLobs(false);
        trigger.setUseCaptureOldData(false);
        trigger.setUseHandleKeyUpdates(false);
        trigger.setNameForInsertTrigger("true");
        trigger.setNameForUpdateTrigger("true");
        trigger.setNameForDeleteTrigger("true");
        trigger.setSyncOnUpdateCondition("true");
        trigger.setSyncOnInsertCondition("true");
        trigger.setSyncOnDeleteCondition("true");
        trigger.setCustomBeforeUpdateText("true");
        trigger.setCustomBeforeInsertText("true");
        trigger.setCustomBeforeDeleteText("true");
        trigger.setCustomOnUpdateText("true");
        trigger.setCustomOnInsertText("true");
        trigger.setCustomOnDeleteText("true");
        trigger.setExcludedColumnNames("true");
        trigger.setExternalSelect("true");
        trigger.setTxIdExpression("true");
        trigger.setSyncKeyNames("true");
        actualHashedValue += trigger.getSourceTableName().hashCode();
        actualHashedValue += trigger.getChannelId().hashCode();
        actualHashedValue += trigger.getSourceSchemaName().hashCode();
        actualHashedValue += trigger.getSourceCatalogName().hashCode();
        actualHashedValue += trigger.isSyncOnUpdate() ? "syncOnUpdate".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnInsert() ? "syncOnInsert".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnDelete() ? "syncOnDelete".hashCode() : 0;
        actualHashedValue += trigger.isSyncOnIncomingBatch() ? "syncOnIncomingBatch".hashCode() : 0;
        actualHashedValue += trigger.isUseStreamLobs() ? "useStreamLobs".hashCode() : 0;
        actualHashedValue += trigger.isUseCaptureLobs() ? "useCaptureLobs".hashCode() : 0;
        actualHashedValue += trigger.isUseCaptureOldData() ? "useCaptureOldData".hashCode() : 0;
        actualHashedValue += trigger.isUseHandleKeyUpdates() ? "useHandleKeyUpdates".hashCode() : 0;
        actualHashedValue += trigger.getNameForInsertTrigger().hashCode();
        actualHashedValue += trigger.getNameForUpdateTrigger().hashCode();
        actualHashedValue += trigger.getNameForDeleteTrigger().hashCode();
        actualHashedValue += trigger.getSyncOnUpdateCondition().hashCode();
        actualHashedValue += trigger.getSyncOnInsertCondition().hashCode();
        actualHashedValue += trigger.getSyncOnDeleteCondition().hashCode();
        actualHashedValue += trigger.getCustomBeforeUpdateText().hashCode();
        actualHashedValue += trigger.getCustomBeforeInsertText().hashCode();
        actualHashedValue += trigger.getCustomBeforeDeleteText().hashCode();
        actualHashedValue += trigger.getCustomOnUpdateText().hashCode();
        actualHashedValue += trigger.getCustomOnInsertText().hashCode();
        actualHashedValue += trigger.getCustomOnDeleteText().hashCode();
        actualHashedValue += trigger.getExcludedColumnNames().hashCode();
        actualHashedValue += trigger.getExternalSelect().hashCode();
        actualHashedValue += trigger.getTxIdExpression().hashCode();
        actualHashedValue += trigger.getSyncKeyNames().hashCode();
        long test = trigger.toHashedValue();
        assertEquals(actualHashedValue, test);
    }

    @Test
    void testMatches() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        String defaultCatalog = "catalogTest";
        String defaultSchema = "schemaTest";
        testTable.setCatalog("catalogTest");
        testTable.setSchema("schemaTest");
        testTable.setName("testTable");
        trigger.setSourceCatalogName("catalogTest");
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceTableName("testTable");
        boolean ignoreCase = false;
        boolean testMatches = trigger.matches(testTable, defaultCatalog, defaultSchema, ignoreCase);
        assertTrue(testMatches);
    }

    @Test
    void testWildCardMatches() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        String defaultCatalog = "catalogTest";
        String defaultSchema = "schemaTest";
        testTable.setCatalog("catalog*Test");
        testTable.setSchema("schema*Test");
        testTable.setName("test*Table");
        trigger.setSourceCatalogName("catalog*Test");
        trigger.setSourceSchemaName("schema*Test");
        trigger.setSourceTableName("test*Table");
        boolean ignoreCase = false;
        boolean testMatches = trigger.matches(testTable, defaultCatalog, defaultSchema, ignoreCase);
        assertTrue(testMatches);
    }

    @Test
    void testWildCardFalseMatches() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        String defaultCatalog = "catalotTest";
        String defaultSchema = "schemaTest";
        testTable.setCatalog("catalogWildCardTest");
        testTable.setSchema("schema*Test");
        testTable.setName("test*Table");
        trigger.setSourceCatalogName("catalog*Test");
        trigger.setSourceSchemaName("schema*Test");
        trigger.setSourceTableName("test*Table");
        boolean ignoreCase = false;
        boolean testMatches = trigger.matches(testTable, defaultCatalog, defaultSchema, ignoreCase);
        assertTrue(testMatches);
    }

    @Test
    void testFalseWildCardMatches() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        String defaultCatalog = "catalogTest";
        String defaultSchema = "schemaTest";
        testTable.setCatalog("catalog*Test");
        testTable.setSchema("schemaTest");
        testTable.setName("testTable");
        trigger.setSourceCatalogName("catalogTest");
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceTableName("testTable");
        boolean ignoreCase = false;
        boolean testMatches = trigger.matches(testTable, defaultCatalog, defaultSchema, ignoreCase);
        assertFalse(testMatches);
    }

    @Test
    void testBlankTriggerSchemaCatalogMatches() throws Exception {
        Trigger trigger = new Trigger();
        Table testTable = new Table();
        String defaultCatalog = "catalogTest";
        String defaultSchema = "schemaTest";
        testTable.setCatalog("catalogTest");
        testTable.setSchema("schemaTest");
        testTable.setName("testTable");
        trigger.setSourceTableName("testTable");
        boolean ignoreCase = false;
        boolean testMatches = trigger.matches(testTable, defaultCatalog, defaultSchema, ignoreCase);
        assertTrue(testMatches);
    }

    @Test
    void testTriggerToTriggerTrueMatches() throws Exception {
        Trigger trigger = new Trigger();
        Trigger testTrigger = new Trigger();
        trigger.setSourceCatalogName("catalogTest");
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceTableName("testTable");
        testTrigger.setSourceCatalogName("catalogTest");
        testTrigger.setSourceSchemaName("schemaTest");
        testTrigger.setSourceTableName("testTable");
        boolean testMatches = trigger.matches(testTrigger);
        assertTrue(testMatches);
    }

    @Test
    void testTriggerToTriggerFalseMatches() throws Exception {
        Trigger trigger = new Trigger();
        Trigger testTrigger = new Trigger();
        trigger.setSourceCatalogName("catalogTest");
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceTableName("testTable");
        testTrigger.setSourceCatalogName("");
        testTrigger.setSourceSchemaName("");
        testTrigger.setSourceTableName("");
        boolean testMatches = trigger.matches(testTrigger);
        assertFalse(testMatches);
    }

    @Test
    void testToStringOverride() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setTriggerId("testId");
        String test = trigger.toString();
        assertEquals("testId", test);
    }

    @Test
    void testNullToStringOverride() throws Exception {
        Trigger trigger = new Trigger();
        String test = trigger.toString();
        assertEquals(String.valueOf(trigger), test);
    }

    @Test
    void testTriggerCopy() throws Exception {
        Trigger trigger = new Trigger();
        trigger.setSourceCatalogName("catalogTest");
        trigger.setSourceSchemaName("schemaTest");
        trigger.setSourceTableName("testTable");
        Trigger test = trigger.copy();
        assertTrue(EqualsBuilder.reflectionEquals(trigger, test));
    }

    public Column[] buildColumnArray() {
        Column c1 = new Column("col1", false, Types.INTEGER, 50, 0);
        Column c2 = new Column("col2", false, Types.INTEGER, 50, 0);
        Column c3 = new Column("col3", false, Types.INTEGER, 50, 0);
        Column c4 = new Column("col4", false, Types.INTEGER, 50, 0);
        Column c5 = new Column("col5", false, Types.INTEGER, 50, 0);
        Column c6 = new Column("col6", false, Types.INTEGER, 50, 0);
        return new Column[] { c1, c2, c3, c4, c5, c6 };
    }
}
