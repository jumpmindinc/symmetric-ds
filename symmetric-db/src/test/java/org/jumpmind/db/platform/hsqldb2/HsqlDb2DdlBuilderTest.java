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
package org.jumpmind.db.platform.hsqldb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.ColumnSizeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HsqlDb2DdlBuilderTest {
    private HsqlDb2DdlBuilder ddlBuilder;

    @BeforeEach
    void setup() {
        ddlBuilder = new HsqlDb2DdlBuilder();
        ddlBuilder.setDelimitedIdentifierModeOn(false);
    }

    @Test
    void testConstructor_configuresDatabaseInfo() {
        assertEquals("SMALLINT", ddlBuilder.getDatabaseInfo().getNativeType(Types.TINYINT));
        assertEquals("DOUBLE", ddlBuilder.getDatabaseInfo().getNativeType(Types.FLOAT));
        assertEquals("LONGVARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.CLOB));
        assertTrue(ddlBuilder.getDatabaseInfo().hasSize(Types.TIMESTAMP));
        assertTrue(ddlBuilder.getDatabaseInfo().hasSize(Types.TIME));
        assertEquals(Integer.MAX_VALUE, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.VARCHAR));
        assertEquals(3, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.TIMESTAMP));
        assertTrue(ddlBuilder.getDatabaseInfo().isNonBlankCharColumnSpacePadded());
        assertTrue(ddlBuilder.getDatabaseInfo().isBlankCharColumnSpacePadded());
        assertFalse(ddlBuilder.getDatabaseInfo().isCharColumnSpaceTrimmed());
        assertFalse(ddlBuilder.getDatabaseInfo().isEmptyStringNulled());
        assertTrue(ddlBuilder.getDatabaseInfo().isGeneratedColumnsSupported());
        assertFalse(ddlBuilder.getDatabaseInfo().isNonPKIdentityColumnsSupported());
        assertFalse(ddlBuilder.getDatabaseInfo().isIdentityOverrideAllowed());
    }

    @Test
    void testDropTable_writesDropTableIfExists() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.dropTable(new Table("TEST_TABLE"), ddl, false, false);
        assertTrue(ddl.toString().startsWith("DROP TABLE TEST_TABLE IF EXISTS"));
    }

    @Test
    void testWriteGeneratedColumn_blankDefinition_writesTypeOnly() {
        Column column = new Column("AMOUNT", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeGeneratedColumn(table, column, ddl);
        assertFalse(ddl.toString().contains("GENERATED ALWAYS AS"));
        assertTrue(ddl.toString().contains("AMOUNT"));
    }

    @Test
    void testWriteGeneratedColumn_definitionWithoutParens_wrapsInParens() {
        Column column = new Column("TOTAL", false, Types.INTEGER, 0, 0);
        column.addPlatformColumn(new PlatformColumn());
        column.setDefaultValue("COL1 + COL2");
        Table table = new Table("TEST_TABLE", column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeGeneratedColumn(table, column, ddl);
        assertTrue(ddl.toString().contains("GENERATED ALWAYS AS (COL1 + COL2)"));
    }

    @Test
    void testWriteGeneratedColumn_definitionWithParens_writesAsIs() {
        Column column = new Column("TOTAL", false, Types.INTEGER, 0, 0);
        column.addPlatformColumn(new PlatformColumn());
        column.setDefaultValue("(COL1 + COL2)");
        Table table = new Table("TEST_TABLE", column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeGeneratedColumn(table, column, ddl);
        assertTrue(ddl.toString().contains("GENERATED ALWAYS AS (COL1 + COL2)"));
        assertFalse(ddl.toString().contains("((COL1"));
    }

    @Test
    void testGetSelectLastIdentityValues_returnsCallIdentity() {
        assertEquals("CALL IDENTITY()", ddlBuilder.getSelectLastIdentityValues(new Table("TEST_TABLE")));
    }

    @Test
    void testShouldGeneratePrimaryKeys_singleAutoIncrementColumn_returnsFalse() {
        Column column = new Column("ID", true, Types.INTEGER, 0, 0);
        column.setAutoIncrement(true);
        assertFalse(ddlBuilder.shouldGeneratePrimaryKeys(new Column[] { column }));
    }

    @Test
    void testShouldGeneratePrimaryKeys_singleNonAutoIncrementColumn_returnsTrue() {
        Column column = new Column("ID", true, Types.INTEGER, 0, 0);
        assertTrue(ddlBuilder.shouldGeneratePrimaryKeys(new Column[] { column }));
    }

    @Test
    void testShouldGeneratePrimaryKeys_multipleColumns_returnsTrue() {
        Column column1 = new Column("ID1", true, Types.INTEGER, 0, 0);
        Column column2 = new Column("ID2", true, Types.INTEGER, 0, 0);
        column1.setAutoIncrement(true);
        assertTrue(ddlBuilder.shouldGeneratePrimaryKeys(new Column[] { column1, column2 }));
    }

    @Test
    void testShouldGeneratePrimaryKeys_nullColumns_returnsTrue() {
        assertTrue(ddlBuilder.shouldGeneratePrimaryKeys(null));
    }

    @Test
    void testProcessTableStructureChanges_removePrimaryKeyColumn_removesChangeWithoutWritingDdl() {
        Column pkColumn = new Column("ID", true, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", pkColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new RemoveColumnChange(table, pkColumn));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
        assertEquals(0, ddl.length());
    }

    @Test
    void testProcessTableStructureChanges_varcharSizeChangeToZero_removesChange() {
        Column column = new Column("NAME", false, Types.VARCHAR, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new ColumnSizeChange(table, column, 0, 0));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
    }

    @Test
    void testProcessTableStructureChanges_varcharSizeChangeToNonZero_leavesChangeUnprocessed() {
        Column column = new Column("NAME", false, Types.VARCHAR, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new ColumnSizeChange(table, column, 100, 0));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertEquals(1, changes.size());
    }

    @Test
    void testProcessTableStructureChanges_varcharToLongVarcharDataTypeChange_removesChange() {
        Column column = new Column("NAME", false, Types.VARCHAR, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new ColumnDataTypeChange(table, column, Types.LONGVARCHAR));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
    }

    @Test
    void testProcessTableStructureChanges_dataTypeChangeToBigInt_isNotRemoved() {
        Column column = new Column("AMOUNT", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new ColumnDataTypeChange(table, column, Types.BIGINT));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertEquals(1, changes.size());
        assertEquals(0, ddl.length());
    }

    @Test
    void testProcessTableStructureChanges_addAndRemoveColumn_processesAndRemovesRecognizedChanges() {
        Column existingColumn = new Column("OLD_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", existingColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Column newColumn = new Column("NEW_COL", false, Types.INTEGER, 0, 0);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new AddColumnChange(table, newColumn, null, null));
        changes.add(new RemoveColumnChange(table, existingColumn));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
        assertTrue(ddl.toString().contains("ADD COLUMN"));
        assertTrue(ddl.toString().contains("NEW_COL"));
        assertTrue(ddl.toString().contains("DROP COLUMN"));
        assertTrue(ddl.toString().contains("OLD_COL"));
    }

    @Test
    void testProcessTableStructureChanges_copyColumnValueChange_processesAndRemovesChange() {
        Column sourceColumn = new Column("SRC_COL", false, Types.INTEGER, 0, 0);
        Column targetColumn = new Column("TGT_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", sourceColumn, targetColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new CopyColumnValueChange(table, sourceColumn, targetColumn));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
        assertTrue(ddl.toString().contains("UPDATE"));
    }

    @Test
    void testProcessChange_addColumnChange_withNextColumn_writesBeforeClause() {
        Column existingColumn = new Column("EXISTING_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", existingColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Column newColumn = new Column("NEW_COL", false, Types.INTEGER, 0, 0);
        AddColumnChange change = new AddColumnChange(table, newColumn, null, existingColumn);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, currentModel, change, ddl);
        assertTrue(ddl.toString().contains("ADD COLUMN"));
        assertTrue(ddl.toString().contains("NEW_COL"));
        assertTrue(ddl.toString().contains("BEFORE"));
        assertTrue(ddl.toString().contains("EXISTING_COL"));
    }

    @Test
    void testProcessChange_addColumnChange_withoutNextColumn_omitsBeforeClause() {
        Table table = new Table("TEST_TABLE", new Column("ID", true, Types.INTEGER, 0, 0));
        Database currentModel = new Database();
        currentModel.addTable(table);
        Column newColumn = new Column("NEW_COL", false, Types.INTEGER, 0, 0);
        AddColumnChange change = new AddColumnChange(table, newColumn, null, null);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, currentModel, change, ddl);
        assertTrue(ddl.toString().contains("ADD COLUMN"));
        assertFalse(ddl.toString().contains("BEFORE"));
    }

    @Test
    void testProcessChange_removeColumnChange_writesDropColumnStatement() {
        Column column = new Column("OLD_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        RemoveColumnChange change = new RemoveColumnChange(table, column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, currentModel, change, ddl);
        assertTrue(ddl.toString().contains("ALTER TABLE"));
        assertTrue(ddl.toString().contains("DROP COLUMN"));
        assertTrue(ddl.toString().contains("OLD_COL"));
    }

    @Test
    void testWriteExternalIndexDropStmt_writesDropIndexStatement() {
        StringBuilder ddl = new StringBuilder();
        IIndex index = new NonUniqueIndex("IDX_TEST");
        index.addColumn(new IndexColumn("COL1"));
        ddlBuilder.writeExternalIndexDropStmt(new Table("TEST_TABLE"), index, ddl);
        assertTrue(ddl.toString().startsWith("DROP INDEX IDX_TEST"));
    }
}
