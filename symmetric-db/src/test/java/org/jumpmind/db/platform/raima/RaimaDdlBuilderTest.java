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
package org.jumpmind.db.platform.raima;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RaimaDdlBuilderTest {
    private RaimaDdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        ddlBuilder = new RaimaDdlBuilder();
    }

    @Test
    void testConstructor_setsDatabaseInfo() {
        DatabaseInfo info = ddlBuilder.getDatabaseInfo();
        assertTrue(info.isSystemForeignKeyIndicesAlwaysNonUnique());
        assertEquals(128, info.getMaxTableNameLength());
        assertEquals(128, info.getMaxColumnNameLength());
        assertEquals(128, info.getMaxConstraintNameLength());
        assertEquals(128, info.getMaxForeignKeyNameLength());
        assertTrue(info.isNullAsDefaultValueRequired());
        assertFalse(info.isDefaultValuesForLongTypesSupported());
        assertTrue(info.isNonPKIdentityColumnsSupported());
        assertTrue(info.isAlterTableForDropUsed());
        assertEquals("", info.getDelimiterToken());
        assertEquals("CHAR", info.getNativeType(Types.CHAR));
        assertEquals("VARCHAR", info.getNativeType(Types.VARCHAR));
        assertEquals("WCHAR", info.getNativeType(Types.NCHAR));
        assertEquals("WVARCHAR", info.getNativeType(Types.NVARCHAR));
        assertEquals("LONG VARCHAR", info.getNativeType(Types.LONGVARCHAR));
        assertEquals("BOOLEAN", info.getNativeType(Types.BIT));
        assertEquals("DECIMAL", info.getNativeType(Types.NUMERIC));
        assertEquals(254, info.getDefaultSize(Types.CHAR));
        assertEquals(254, info.getDefaultSize(Types.VARCHAR));
        assertFalse(info.isNonBlankCharColumnSpacePadded());
        assertFalse(info.isBlankCharColumnSpacePadded());
        assertFalse(info.isCharColumnSpaceTrimmed());
        assertFalse(info.isEmptyStringNulled());
        assertFalse(info.isSyntheticDefaultValueForRequiredReturned());
    }

    @Test
    void testGetFullyQualifiedTableNameShorten_returnsTableName() {
        Table table = new Table("test_table");
        assertEquals("test_table", ddlBuilder.getFullyQualifiedTableNameShorten(table));
    }

    @Test
    void testAreColumnSizesTheSame_bothDecimalLargeSizeSameScale_returnsTrue() {
        Column source = new Column("amount", false, Types.DECIMAL, 10, 2);
        Column target = new Column("amount", false, Types.DECIMAL, 12, 2);
        assertTrue(ddlBuilder.areColumnSizesTheSame(source, target));
    }

    @Test
    void testAreColumnSizesTheSame_bothDecimalSmallSize_returnsFalse() {
        Column source = new Column("amount", false, Types.DECIMAL, 5, 2);
        Column target = new Column("amount", false, Types.DECIMAL, 5, 2);
        assertFalse(ddlBuilder.areColumnSizesTheSame(source, target));
    }

    @Test
    void testAreColumnSizesTheSame_bothDecimalDifferentScale_returnsFalse() {
        Column source = new Column("amount", false, Types.DECIMAL, 10, 2);
        Column target = new Column("amount", false, Types.DECIMAL, 10, 3);
        assertFalse(ddlBuilder.areColumnSizesTheSame(source, target));
    }

    @Test
    void testAreColumnSizesTheSame_nonDecimalColumns_delegatesToSuper() {
        Column source = new Column("name", false, Types.VARCHAR, 50, 0);
        Column target = new Column("name", false, Types.VARCHAR, 50, 0);
        assertTrue(ddlBuilder.areColumnSizesTheSame(source, target));
    }

    @Test
    void testDropTable_writesDropTableStatement() {
        Table table = new Table("test_table");
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.dropTable(table, ddl, false, false);
        String result = ddl.toString();
        assertTrue(result.startsWith("DROP TABLE test_table"));
        assertTrue(result.trim().endsWith(ddlBuilder.getDatabaseInfo().getSqlCommandDelimiter()));
    }

    @Test
    void testWriteColumnType_autoIncrementColumn_writesRowId() {
        Column column = new Column("id", true, Types.INTEGER, 0, 0);
        column.setAutoIncrement(true);
        Table table = new Table("test_table", column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnType(table, column, ddl);
        assertEquals("ROWID NOT NULL", ddl.toString());
    }

    @Test
    void testWriteColumnType_nonAutoIncrementColumn_delegatesToSuper() {
        Column column = new Column("name", false, Types.VARCHAR, 50, 0);
        Table table = new Table("test_table", column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnType(table, column, ddl);
        assertTrue(ddl.toString().contains("VARCHAR"));
        assertFalse(ddl.toString().contains("ROWID"));
    }

    @Test
    void testWriteColumnAutoIncrementStmt_writesNothing() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnAutoIncrementStmt(null, null, ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testGetSelectLastIdentityValues_returnsFixedSql() {
        Table table = new Table("test_table");
        assertEquals("SELECT LAST_INSERT_ID()", ddlBuilder.getSelectLastIdentityValues(table));
    }

    @Test
    void testGetSelectLastIdentityValues_returnsFixedSql_evenForNullTable() {
        assertEquals("SELECT LAST_INSERT_ID()", ddlBuilder.getSelectLastIdentityValues(null));
    }

    @Test
    void testProcessTableStructureChanges_routesEachChangeTypeAndClearsList() {
        Database currentModel = new Database();
        Database desiredModel = new Database();
        Column idCol = new Column("id", false, Types.INTEGER, 0, 0);
        Column removeCol = new Column("remove_col", false, Types.VARCHAR, 50, 0);
        Column changedColSource = new Column("changed_col", false, Types.VARCHAR, 50, 0);
        Column copySrc = new Column("copy_src", false, Types.INTEGER, 0, 0);
        Table sourceTable = new Table("test_table", idCol, removeCol, changedColSource, copySrc);
        currentModel.addTable(sourceTable);
        Column idColTarget = new Column("id", false, Types.INTEGER, 0, 0);
        Column addCol = new Column("add_col", false, Types.INTEGER, 0, 0);
        Column changedColTarget = new Column("changed_col", false, Types.VARCHAR, 100, 0);
        Column copyTarget = new Column("copy_target", false, Types.INTEGER, 0, 0);
        Table targetTable = new Table("test_table", idColTarget, addCol, changedColTarget, copyTarget);
        desiredModel.addTable(targetTable);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new RemoveColumnChange(sourceTable, removeCol));
        changes.add(new CopyColumnValueChange(sourceTable, copySrc, copyTarget));
        changes.add(new AddPrimaryKeyChange(sourceTable, new Column[] { idCol }));
        changes.add(new AddColumnChange(sourceTable, addCol, null, null));
        changes.add(new ColumnDataTypeChange(sourceTable, changedColSource, Types.VARCHAR));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, desiredModel, sourceTable, targetTable, changes, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("DROP COLUMN"), "Expected DROP COLUMN for RemoveColumnChange: " + result);
        assertTrue(result.contains("UPDATE"), "Expected UPDATE for CopyColumnValueChange: " + result);
        assertTrue(result.contains("ADD CONSTRAINT"), "Expected ADD CONSTRAINT for AddPrimaryKeyChange: " + result);
        assertTrue(result.contains("ADD COLUMN"), "Expected ADD COLUMN for AddColumnChange: " + result);
        assertTrue(result.contains("MODIFY COLUMN"), "Expected MODIFY COLUMN for generic ColumnChange: " + result);
        assertTrue(changes.isEmpty(), "All handled changes should be removed from the list");
    }

    @Test
    void testWriteExternalForeignKeyCreateStmt_writesAlterTableAddConstraint() {
        Column localColumn = new Column("parent_id", false, Types.INTEGER, 0, 0);
        Column foreignColumn = new Column("id", true, Types.INTEGER, 0, 0);
        Table table = new Table("child_table", localColumn);
        ForeignKey key = new ForeignKey("fk_test", "parent_table");
        key.addReference(new Reference(localColumn, foreignColumn));
        Database database = new Database();
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeExternalForeignKeyCreateStmt(database, table, key, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("ADD CONSTRAINT"));
        assertTrue(result.contains("FOREIGN KEY"));
        assertTrue(result.contains("REFERENCES"));
        assertTrue(result.contains("parent_table"));
    }

    @Test
    void testWriteExternalForeignKeyCreateStmt_nullForeignTable_writesNothing() {
        Column localColumn = new Column("parent_id", false, Types.INTEGER, 0, 0);
        Table table = new Table("child_table", localColumn);
        ForeignKey key = new ForeignKey("fk_test");
        Database database = new Database();
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeExternalForeignKeyCreateStmt(database, table, key, ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testWriteExternalForeignKeyDropStmt_writesDropConstraint() {
        Column localColumn = new Column("parent_id", false, Types.INTEGER, 0, 0);
        Table table = new Table("child_table", localColumn);
        ForeignKey key = new ForeignKey("fk_test", "parent_table");
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeExternalForeignKeyDropStmt(table, key, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("ALTER TABLE"));
        assertTrue(result.contains("DROP CONSTRAINT"));
    }

    @Test
    void testWriteExternalIndexDropStmt_writesDropIndex() {
        Table table = new Table("test_table");
        NonUniqueIndex index = new NonUniqueIndex("idx_test");
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeExternalIndexDropStmt(table, index, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("ALTER TABLE"));
        assertTrue(result.contains("DROP INDEX"));
        assertTrue(result.contains("idx_test"));
    }

    @Test
    void testProcessChange_addColumn_writesAddColumnStatement() {
        Column existingCol = new Column("id", true, Types.INTEGER, 0, 0);
        Table table = new Table("test_table", existingCol);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Database desiredModel = new Database();
        Column newColumn = new Column("new_col", false, Types.VARCHAR, 50, 0);
        AddColumnChange change = new AddColumnChange(table, newColumn, null, null);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, desiredModel, change, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("ALTER TABLE"));
        assertTrue(result.contains("ADD COLUMN"));
        assertTrue(result.contains("new_col"));
    }

    @Test
    void testProcessChange_removeColumn_writesDropColumnStatement() {
        Column removeColumn = new Column("old_col", false, Types.VARCHAR, 50, 0);
        Table table = new Table("test_table", removeColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Database desiredModel = new Database();
        RemoveColumnChange change = new RemoveColumnChange(table, removeColumn);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, desiredModel, change, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("ALTER TABLE"));
        assertTrue(result.contains("DROP COLUMN"));
        assertTrue(result.contains("old_col"));
    }

    @Test
    void testProcessColumnChange_writesModifyColumnStatement() {
        Column sourceColumn = new Column("changed_col", false, Types.VARCHAR, 50, 0);
        Column targetColumn = new Column("changed_col", false, Types.VARCHAR, 100, 0);
        Table sourceTable = new Table("test_table", sourceColumn);
        Table targetTable = new Table("test_table", targetColumn);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn, ddl);
        String result = ddl.toString();
        assertTrue(result.contains("ALTER TABLE"));
        assertTrue(result.contains("MODIFY COLUMN"));
        assertTrue(result.contains("changed_col"));
    }

    @Test
    void testProcessColumnChange_autoIncrementTarget_restoredAfterWrite() {
        Column sourceColumn = new Column("id_col", false, Types.INTEGER, 0, 0);
        Column targetColumn = new Column("id_col", true, Types.INTEGER, 0, 0);
        targetColumn.setAutoIncrement(true);
        Table sourceTable = new Table("test_table", sourceColumn);
        Table targetTable = new Table("test_table", targetColumn);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processColumnChange(sourceTable, targetTable, sourceColumn, targetColumn, ddl);
        assertTrue(targetColumn.isAutoIncrement(), "Auto-increment flag should be restored after writing the column");
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyUpdate_setDefault_suppressed() {
        ForeignKey key = new ForeignKey("fk_test", "parent_table");
        key.setOnUpdateAction(ForeignKeyAction.SETDEFAULT);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyUpdate_cascade_delegatesToSuper() {
        ForeignKey key = new ForeignKey("fk_test", "parent_table");
        key.setOnUpdateAction(ForeignKeyAction.CASCADE);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        assertEquals(" ON UPDATE CASCADE", ddl.toString());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyDelete_setDefault_suppressed() {
        ForeignKey key = new ForeignKey("fk_test", "parent_table");
        key.setOnDeleteAction(ForeignKeyAction.SETDEFAULT);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyDelete_cascade_delegatesToSuper() {
        ForeignKey key = new ForeignKey("fk_test", "parent_table");
        key.setOnDeleteAction(ForeignKeyAction.CASCADE);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        assertEquals(" ON DELETE CASCADE", ddl.toString());
    }
}
