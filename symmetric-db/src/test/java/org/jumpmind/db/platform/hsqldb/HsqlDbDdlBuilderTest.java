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
package org.jumpmind.db.platform.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.CopyColumnValueChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HsqlDbDdlBuilderTest {
    private HsqlDbDdlBuilder ddlBuilder;

    @BeforeEach
    void setup() {
        ddlBuilder = new HsqlDbDdlBuilder();
        ddlBuilder.setDelimitedIdentifierModeOn(false);
    }

    @Test
    void testConstructor_configuresDatabaseInfo() {
        assertFalse(ddlBuilder.getDatabaseInfo().isNonPKIdentityColumnsSupported());
        assertFalse(ddlBuilder.getDatabaseInfo().isIdentityOverrideAllowed());
        assertTrue(ddlBuilder.getDatabaseInfo().isSystemForeignKeyIndicesAlwaysNonUnique());
        assertEquals("SMALLINT", ddlBuilder.getDatabaseInfo().getNativeType(Types.TINYINT));
        assertEquals("DOUBLE", ddlBuilder.getDatabaseInfo().getNativeType(Types.FLOAT));
        assertEquals("LONGVARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.CLOB));
        assertEquals("LONGVARBINARY", ddlBuilder.getDatabaseInfo().getNativeType(Types.BLOB));
        assertTrue(ddlBuilder.getDatabaseInfo().hasSize(Types.TIMESTAMP));
        assertTrue(ddlBuilder.getDatabaseInfo().hasSize(Types.TIME));
        assertEquals(Integer.MAX_VALUE, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.VARCHAR));
        assertEquals(3, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.TIMESTAMP));
        assertEquals(0, ddlBuilder.getDatabaseInfo().getDefaultSize(Types.TIME));
        assertTrue(ddlBuilder.getDatabaseInfo().isNonBlankCharColumnSpacePadded());
        assertTrue(ddlBuilder.getDatabaseInfo().isBlankCharColumnSpacePadded());
        assertFalse(ddlBuilder.getDatabaseInfo().isCharColumnSpaceTrimmed());
        assertFalse(ddlBuilder.getDatabaseInfo().isEmptyStringNulled());
    }

    @Test
    void testDropTable_writesDropTableIfExists() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.dropTable(new Table("TEST_TABLE"), ddl, false, false);
        assertTrue(ddl.toString().startsWith("DROP TABLE TEST_TABLE IF EXISTS"));
    }

    @Test
    void testGetSelectLastIdentityValues_returnsCallIdentity() {
        assertEquals("CALL IDENTITY()", ddlBuilder.getSelectLastIdentityValues(new Table("TEST_TABLE")));
    }

    @Test
    void testProcessTableStructureChanges_removePrimaryKeyColumn_returnsWithoutProcessingChanges() {
        Column pkColumn = new Column("ID", true, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", pkColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new RemoveColumnChange(table, pkColumn));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertEquals(1, changes.size());
        assertEquals(0, ddl.length());
    }

    @Test
    void testProcessTableStructureChanges_removeNonPrimaryKeyColumn_writesDropColumnAndRemovesChange() {
        Column column = new Column("NAME", false, Types.VARCHAR, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new RemoveColumnChange(table, column));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
        assertTrue(ddl.toString().contains("DROP COLUMN"));
        assertTrue(ddl.toString().contains("NAME"));
    }

    @Test
    void testProcessTableStructureChanges_addColumn_writesAddColumnAndRemovesChange() {
        Column existingColumn = new Column("EXISTING_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", existingColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Column newColumn = new Column("NEW_COL", false, Types.INTEGER, 0, 0);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new AddColumnChange(table, newColumn, null, null));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(changes.isEmpty());
        assertTrue(ddl.toString().contains("ADD COLUMN"));
        assertTrue(ddl.toString().contains("NEW_COL"));
    }

    @Test
    void testProcessTableStructureChanges_addMultipleColumns_processesInReverseOrder() {
        Column existingColumn = new Column("EXISTING_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", existingColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Column firstNewColumn = new Column("FIRST_COL", false, Types.INTEGER, 0, 0);
        Column secondNewColumn = new Column("SECOND_COL", false, Types.INTEGER, 0, 0);
        List<TableChange> changes = new ArrayList<>();
        changes.add(new AddColumnChange(table, firstNewColumn, null, null));
        changes.add(new AddColumnChange(table, secondNewColumn, null, null));
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processTableStructureChanges(currentModel, currentModel, table, table, changes, ddl);
        assertTrue(ddl.indexOf("SECOND_COL") < ddl.indexOf("FIRST_COL"));
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
    void testProcessTableStructureChanges_unrecognizedChangeType_leftUnprocessed() {
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
    void testProcessChange_addColumnChange_appliesNewColumnToModel() {
        Column existingColumn = new Column("ID", true, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", existingColumn);
        Database currentModel = new Database();
        currentModel.addTable(table);
        Column newColumn = new Column("NEW_COL", false, Types.INTEGER, 0, 0);
        AddColumnChange change = new AddColumnChange(table, newColumn, null, null);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, currentModel, change, ddl);
        Table updatedTable = currentModel.findTable("TEST_TABLE", false);
        assertNotNull(updatedTable.findColumn("NEW_COL", false));
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
    void testProcessChange_removeColumnChange_appliesChangeToModel() {
        Column column = new Column("OLD_COL", false, Types.INTEGER, 0, 0);
        Table table = new Table("TEST_TABLE", column);
        Database currentModel = new Database();
        currentModel.addTable(table);
        RemoveColumnChange change = new RemoveColumnChange(table, column);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.processChange(currentModel, currentModel, change, ddl);
        Table updatedTable = currentModel.findTable("TEST_TABLE", false);
        assertNull(updatedTable.findColumn("OLD_COL", false));
    }
}
