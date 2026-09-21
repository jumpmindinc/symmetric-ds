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
package org.jumpmind.db.platform.sqlanywhere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAnywhereDdlBuilderTest {
    private static final String TABLE_NAME = "item";
    private SqlAnywhereDdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        ddlBuilder = new SqlAnywhereDdlBuilder();
    }

    @Test
    void testConstructor_setsIdentifierLimits() {
        assertEquals(128, ddlBuilder.getDatabaseInfo().getMaxTableNameLength());
        assertEquals(128, ddlBuilder.getDatabaseInfo().getMaxColumnNameLength());
        assertEquals(128, ddlBuilder.getDatabaseInfo().getMaxConstraintNameLength());
        assertEquals(128, ddlBuilder.getDatabaseInfo().getMaxForeignKeyNameLength());
    }

    @Test
    void testConstructor_setsCommentAndDelimiterTokens() {
        assertEquals("/*", ddlBuilder.getDatabaseInfo().getCommentPrefix());
        assertEquals("*/", ddlBuilder.getDatabaseInfo().getCommentSuffix());
        assertEquals("\"", ddlBuilder.getDatabaseInfo().getDelimiterToken());
    }

    @Test
    void testConstructor_setsBehaviorFlags() {
        assertTrue(ddlBuilder.getDatabaseInfo().isNullAsDefaultValueRequired());
        assertTrue(ddlBuilder.getDatabaseInfo().isDateOverridesToTimestamp());
        assertTrue(ddlBuilder.getDatabaseInfo().isRequiresAutoCommitForDdl());
        assertTrue(ddlBuilder.getDatabaseInfo().isRequiredCharColumnEmptyStringSameAsNull());
        assertFalse(ddlBuilder.getDatabaseInfo().isAutoIncrementUpdateAllowed());
        assertFalse(ddlBuilder.getDatabaseInfo().isEmptyStringNulled());
        assertFalse(ddlBuilder.getDatabaseInfo().isNonBlankCharColumnSpacePadded());
        assertFalse(ddlBuilder.getDatabaseInfo().isBlankCharColumnSpacePadded());
        assertFalse(ddlBuilder.getDatabaseInfo().isCharColumnSpaceTrimmed());
    }

    @Test
    void testConstructor_mapsLobAndBinaryTypesToImage() {
        assertEquals("IMAGE", ddlBuilder.getDatabaseInfo().getNativeType(Types.ARRAY));
        assertEquals("IMAGE", ddlBuilder.getDatabaseInfo().getNativeType(Types.BLOB));
        assertEquals("IMAGE", ddlBuilder.getDatabaseInfo().getNativeType(Types.LONGVARBINARY));
        assertEquals("IMAGE", ddlBuilder.getDatabaseInfo().getNativeType(Types.JAVA_OBJECT));
        assertEquals("IMAGE", ddlBuilder.getDatabaseInfo().getNativeType(Types.STRUCT));
    }

    @Test
    void testConstructor_mapsSmallIntegerTypesToSmallint() {
        assertEquals("SMALLINT", ddlBuilder.getDatabaseInfo().getNativeType(Types.BIT));
        assertEquals("SMALLINT", ddlBuilder.getDatabaseInfo().getNativeType(Types.TINYINT));
    }

    @Test
    void testConstructor_mapsCharacterAndDateTypes() {
        assertEquals("TEXT", ddlBuilder.getDatabaseInfo().getNativeType(Types.CLOB));
        assertEquals("TEXT", ddlBuilder.getDatabaseInfo().getNativeType(Types.LONGVARCHAR));
        assertEquals("LONG NVARCHAR", ddlBuilder.getDatabaseInfo().getNativeType(Types.LONGNVARCHAR));
        assertEquals("INT", ddlBuilder.getDatabaseInfo().getNativeType(Types.INTEGER));
        assertEquals("DOUBLE PRECISION", ddlBuilder.getDatabaseInfo().getNativeType(Types.DOUBLE));
        assertEquals("DATETIME", ddlBuilder.getDatabaseInfo().getNativeType(Types.DATE));
        assertEquals("DATETIME", ddlBuilder.getDatabaseInfo().getNativeType(Types.TIME));
        assertEquals("DATETIME", ddlBuilder.getDatabaseInfo().getNativeType(Types.TIMESTAMP));
        assertEquals("DATETIME", ddlBuilder.getDatabaseInfo().getNativeType(ColumnTypes.TIMESTAMPTZ));
    }

    @Test
    void testConstructor_setsDefaultSizes() {
        assertEquals(Integer.valueOf(254), ddlBuilder.getDatabaseInfo().getDefaultSize(Types.BINARY));
        assertEquals(Integer.valueOf(254), ddlBuilder.getDatabaseInfo().getDefaultSize(Types.VARBINARY));
        assertEquals(Integer.valueOf(254), ddlBuilder.getDatabaseInfo().getDefaultSize(Types.CHAR));
        assertEquals(Integer.valueOf(254), ddlBuilder.getDatabaseInfo().getDefaultSize(Types.VARCHAR));
    }

    @Test
    void testGetSelectLastIdentityValues() {
        assertEquals("SELECT @@IDENTITY", ddlBuilder.getSelectLastIdentityValues(new Table(TABLE_NAME)));
    }

    @Test
    void testGetQuotationOnStatement_whenDelimitedIdentifiersAreOn() {
        ddlBuilder.setDelimitedIdentifierModeOn(true);
        assertEquals("SET quoted_identifier on", ddlBuilder.getQuotationOnStatement());
    }

    @Test
    void testGetQuotationOnStatement_whenDelimitedIdentifiersAreOff() {
        ddlBuilder.setDelimitedIdentifierModeOn(false);
        assertEquals("", ddlBuilder.getQuotationOnStatement());
    }

    @Test
    void testCreateTables_emitsQuotationOnStatement() {
        ddlBuilder.setDelimitedIdentifierModeOn(true);
        assertTrue(ddlBuilder.createTables(databaseWithItemTable(), false).contains("SET quoted_identifier on"));
    }

    @Test
    void testCreateTables_writesNullForOptionalColumns() {
        String sql = ddlBuilder.createTables(databaseWithItemTable(), false);
        assertTrue(sql.contains("NOT NULL"));
        assertTrue(sql.contains("NULL"));
    }

    @Test
    void testWriteColumn_withRequiredColumn() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumn(new Table(TABLE_NAME), new Column("name", false, Types.VARCHAR, 100, 0), ddl);
        assertEquals("\"name\" VARCHAR(100) NULL", ddl.toString());
    }

    @Test
    void testWriteColumn_withAutoIncrementColumn_omitsNullability() {
        Column column = new Column("id", true, Types.INTEGER, 0, 0);
        column.setAutoIncrement(true);
        column.setRequired(true);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumn(new Table(TABLE_NAME), column, ddl);
        assertFalse(ddl.toString().contains("NOT NULL"));
        assertTrue(ddl.toString().contains("IDENTITY"));
    }

    @Test
    void testDropTable_guardsWithSysobjectsLookup() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.dropTable(new Table(TABLE_NAME), ddl, false, false);
        String sql = ddl.toString();
        assertTrue(sql.contains("IF EXISTS (SELECT 1 FROM dbo.sysobjects WHERE type = 'U' AND name = '" + TABLE_NAME + "')"));
        assertTrue(sql.contains("BEGIN"));
        assertTrue(sql.contains("DROP TABLE"));
        assertTrue(sql.contains("END"));
    }

    @Test
    void testWriteExternalIndexDropStmt_qualifiesIndexWithSchemaAndTable() {
        Table table = new Table(TABLE_NAME);
        table.setSchema("dba");
        IIndex index = new NonUniqueIndex("idx_item_name");
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeExternalIndexDropStmt(table, index, ddl);
        assertEquals("DROP INDEX dba.item.\"idx_item_name\"", ddl.toString().trim().replaceAll(";$", ""));
    }

    @Test
    void testWriteCastExpression_withSameNativeType_castsNothing() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCastExpression(new Column("a", false, Types.INTEGER, 0, 0), new Column("b", false, Types.INTEGER, 0, 0), ddl);
        assertEquals("\"a\"", ddl.toString());
    }

    @Test
    void testWriteCastExpression_withDifferentNativeType_convertsValue() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCastExpression(new Column("a", false, Types.INTEGER, 0, 0), new Column("b", false, Types.VARCHAR, 50, 0), ddl);
        assertEquals("CONVERT(VARCHAR,\"a\")", ddl.toString());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyUpdate_withNoAction_writesNothingAndRestoresAction() {
        ForeignKey key = new ForeignKey("fk_item_parent");
        key.setOnUpdateAction(ForeignKeyAction.NOACTION);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        assertEquals("", ddl.toString());
        assertEquals(ForeignKeyAction.NOACTION, key.getOnUpdateAction());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyUpdate_withCascade() {
        ForeignKey key = new ForeignKey("fk_item_parent");
        key.setOnUpdateAction(ForeignKeyAction.CASCADE);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyUpdate(key, ddl);
        assertEquals(" ON UPDATE CASCADE", ddl.toString());
        assertEquals(ForeignKeyAction.CASCADE, key.getOnUpdateAction());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyDelete_withNoAction_writesNothingAndRestoresAction() {
        ForeignKey key = new ForeignKey("fk_item_parent");
        key.setOnDeleteAction(ForeignKeyAction.NOACTION);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        assertEquals("", ddl.toString());
        assertEquals(ForeignKeyAction.NOACTION, key.getOnDeleteAction());
    }

    @Test
    void testWriteCascadeAttributesForForeignKeyDelete_withCascade() {
        ForeignKey key = new ForeignKey("fk_item_parent");
        key.setOnDeleteAction(ForeignKeyAction.CASCADE);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKeyDelete(key, ddl);
        assertEquals(" ON DELETE CASCADE", ddl.toString());
        assertEquals(ForeignKeyAction.CASCADE, key.getOnDeleteAction());
    }

    @Test
    void testAreMappedTypesTheSame_treatsCharAndVarcharAsInterchangeable() {
        Column charColumn = new Column("a", false, Types.CHAR, 10, 0);
        Column varcharColumn = new Column("b", false, Types.VARCHAR, 10, 0);
        assertTrue(ddlBuilder.areMappedTypesTheSame(charColumn, varcharColumn));
        assertTrue(ddlBuilder.areMappedTypesTheSame(varcharColumn, charColumn));
    }

    @Test
    void testAreMappedTypesTheSame_withUnrelatedTypes() {
        assertFalse(ddlBuilder.areMappedTypesTheSame(new Column("a", false, Types.CHAR, 10, 0), new Column("b", false, Types.INTEGER, 0, 0)));
    }

    @Test
    void testCreateUniqueIdentifier_containsOnlyHexAndUnderscores() {
        String identifier = ddlBuilder.createUniqueIdentifier();
        assertTrue(identifier.matches("[0-9a-f_]+"), "unexpected identifier: " + identifier);
        assertFalse(identifier.contains(":"));
        assertFalse(identifier.contains("-"));
    }

    @Test
    void testCreateUniqueIdentifier_isDistinctPerCall() {
        assertNotEquals(ddlBuilder.createUniqueIdentifier(), ddlBuilder.createUniqueIdentifier());
    }

    private Database databaseWithItemTable() {
        Column id = new Column("id", true, Types.INTEGER, 0, 0);
        id.setRequired(true);
        Column name = new Column("name", false, Types.VARCHAR, 100, 0);
        Table table = new Table(TABLE_NAME, id, name);
        Database database = new Database();
        database.addTable(table);
        return database;
    }
}
