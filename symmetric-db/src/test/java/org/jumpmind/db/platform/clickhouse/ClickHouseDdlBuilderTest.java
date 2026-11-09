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
package org.jumpmind.db.platform.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jumpmind.db.model.PlatformColumn;

class ClickHouseDdlBuilderTest {
    private ClickHouseDdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        ddlBuilder = new ClickHouseDdlBuilder();
    }

    @Test
    void testConstructor_disablesUnsupportedFeatures() {
        DatabaseInfo info = ddlBuilder.getDatabaseInfo();
        assertFalse(info.isTriggersSupported());
        assertFalse(info.isIndicesSupported());
        assertFalse(info.isForeignKeysSupported());
        assertFalse(info.isPrimaryKeyEmbedded());
    }

    @Test
    void testCreateTable_compositeKey_usesKeyAsOrderBy() {
        Table table = table("orders", column("id", true, Types.INTEGER, 10, 0), column("region", true, Types.VARCHAR, 20, 0),
                column("total", false, Types.DECIMAL, 10, 2));
        String ddl = create(table);
        assertTrue(ddl.contains("CREATE TABLE \"orders\""), ddl);
        assertTrue(ddl.contains("\"id\" Int32"), ddl);
        assertTrue(ddl.contains("\"region\" String"), ddl);
        assertTrue(ddl.contains("\"total\" Nullable(Decimal(10, 2))"), ddl);
        assertTrue(ddl.contains("ENGINE = MergeTree ORDER BY (\"id\", \"region\")"), ddl);
    }

    @Test
    void testCreateTable_singleKey_hasNoParenthesesAroundKey() {
        // The JDBC driver reports an ORDER BY (id) key as "(id)", so a single key is written bare
        String ddl = create(table("item", column("id", true, Types.BIGINT, 19, 0), column("name", false, Types.VARCHAR, 50, 0)));
        assertTrue(ddl.contains("ORDER BY \"id\" SETTINGS"), ddl);
    }

    @Test
    void testCreateTable_withoutKey_ordersByTuple() {
        String ddl = create(table("log", column("a", false, Types.INTEGER, 10, 0), column("b", false, Types.VARCHAR, 50, 0)));
        assertTrue(ddl.contains("ORDER BY tuple() SETTINGS"), ddl);
        assertTrue(ddl.contains("\"a\" Nullable(Int32)"), ddl);
        assertTrue(ddl.contains("\"b\" Nullable(String)"), ddl);
    }

    @Test
    void testCreateTable_includesSettingsForLightweightUpdate() {
        String ddl = create(table("item", column("id", true, Types.INTEGER, 10, 0)));
        assertTrue(ddl.contains("SETTINGS enable_block_number_column = 1, enable_block_offset_column = 1"), ddl);
    }

    @Test
    void testCreateTable_keyColumnIsNeverNullable() {
        Column key = column("id", true, Types.INTEGER, 10, 0);
        key.setRequired(false);
        String ddl = create(table("item", key));
        assertTrue(ddl.contains("\"id\" Int32"), ddl);
        assertFalse(ddl.contains("Nullable(Int32)"), ddl);
    }

    @Test
    void testCreateTable_requiredColumnIsNotNullable() {
        Column name = column("name", false, Types.VARCHAR, 50, 0);
        name.setRequired(true);
        String ddl = create(table("item", column("id", true, Types.INTEGER, 10, 0), name));
        assertTrue(ddl.contains("\"name\" String"), ddl);
        assertFalse(ddl.contains("Nullable(String)"), ddl);
    }

    @Test
    void testCreateTable_doesNotEmitPrimaryKeyConstraintIndexOrForeignKey() {
        String ddl = create(table("item", column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0)));
        assertFalse(ddl.contains("PRIMARY KEY"), ddl);
        assertFalse(ddl.contains("CONSTRAINT"), ddl);
        assertFalse(ddl.contains("INDEX"), ddl);
    }

    @Test
    void testCreateTable_autoIncrementColumnDefaultsToSerialId() {
        Column id = column("id", true, Types.INTEGER, 10, 0);
        id.setAutoIncrement(true);
        String ddl = create(table("item", id));
        assertTrue(ddl.contains("\"id\" Int32 DEFAULT generateSerialID('item_id')"), ddl);
        assertFalse(ddl.contains("IDENTITY"), ddl);
    }

    @Test
    void testCreateTable_autoIncrementOnNonIntegerColumnIsIgnored() {
        Column code = column("code", false, Types.VARCHAR, 10, 0);
        code.setAutoIncrement(true);
        assertFalse(create(table("item", column("id", true, Types.INTEGER, 10, 0), code)).contains("generateSerialID"));
    }

    @Test
    void testCreateTable_writesLiteralDefaults() {
        Column name = withDefault(column("name", false, Types.VARCHAR, 50, 0), "'it''s'");
        Column qty = withDefault(column("qty", false, Types.INTEGER, 10, 0), "5");
        Column price = withDefault(column("price", false, Types.DECIMAL, 10, 2), "1.50");
        Column active = withDefault(column("active", false, Types.BIT, 1, 0), "1");
        Column day = withDefault(column("day", false, Types.DATE, 0, 0), "'2020-01-02'");
        Column created = withDefault(column("created", false, Types.TIMESTAMP, 26, 6), "'2020-01-02 03:04:05'");
        String ddl = create(table("item", column("id", true, Types.INTEGER, 10, 0), name, qty, price, active, day, created));
        assertTrue(ddl.contains("\"name\" Nullable(String) DEFAULT 'it\\'s'"), ddl);
        assertTrue(ddl.contains("\"qty\" Nullable(Int32) DEFAULT 5"), ddl);
        assertTrue(ddl.contains("DEFAULT 1.5"), ddl);
        assertTrue(ddl.contains("\"active\" Nullable(Bool) DEFAULT true"), ddl);
        assertTrue(ddl.contains("DEFAULT '2020-01-02'"), ddl);
        assertTrue(ddl.contains("DEFAULT '2020-01-02 03:04:05.000000'"), ddl);
    }

    @Test
    void testCreateTable_unquotedTextDefaultIsAccepted() {
        assertTrue(create(table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("name", false, Types.VARCHAR, 50, 0), "abc")))
                .contains("DEFAULT 'abc'"));
    }

    @Test
    void testCreateTable_skipsDefaultsThatAreNotLiterals() {
        for (String expression : new String[] { "CURRENT_TIMESTAMP", "now()", "nextval('seq')", "'abc'::character varying", "NULL", "" }) {
            String ddl = create(table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("name", false, Types.VARCHAR, 50, 0), expression)));
            assertFalse(ddl.contains("DEFAULT"), expression + " " + ddl);
        }
        String ddl = create(table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("qty", false, Types.INTEGER, 10, 0), "seq_next()"),
                withDefault(column("created", false, Types.TIMESTAMP, 26, 6), "CURRENT_TIMESTAMP")));
        assertFalse(ddl.contains("DEFAULT"), ddl);
    }

    @Test
    void testCreateTable_binaryColumnHasNoDefault() {
        assertFalse(create(table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("data", false, Types.VARBINARY, 10, 0), "'abc'")))
                .contains("DEFAULT"));
    }

    @Test
    void testAlter_addColumnWithDefault() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("status", false, Types.VARCHAR, 10, 0), "'new'"));
        assertTrue(alter(current, desired).contains("ADD COLUMN \"status\" Nullable(String) DEFAULT 'new'"));
    }

    @Test
    void testAlter_changedDefaultUsesModifyColumn() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("qty", false, Types.INTEGER, 10, 0), "1"));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("qty", false, Types.INTEGER, 10, 0), "2"));
        assertTrue(alter(current, desired).contains("MODIFY COLUMN \"qty\" Nullable(Int32) DEFAULT 2"));
    }

    @Test
    void testAlter_sameDefaultInDifferentFormsIsNotModified() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("price", false, Types.DECIMAL, 10, 2), "1.5"));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), withDefault(column("price", false, Types.DECIMAL, 10, 2), "1.50"));
        assertEquals("", alter(current, desired).trim());
    }

    private static Column withDefault(Column column, String defaultValue) {
        column.setDefaultValue(defaultValue);
        return column;
    }

    @Test
    void testSqlType_mapsJdbcTypes() {
        assertEquals("Int8", ddlBuilder.getSqlType(column("c", false, Types.TINYINT, 3, 0)));
        assertEquals("Int16", ddlBuilder.getSqlType(column("c", false, Types.SMALLINT, 5, 0)));
        assertEquals("Int32", ddlBuilder.getSqlType(column("c", false, Types.INTEGER, 10, 0)));
        assertEquals("Int64", ddlBuilder.getSqlType(column("c", false, Types.BIGINT, 19, 0)));
        assertEquals("Float32", ddlBuilder.getSqlType(column("c", false, Types.REAL, 0, 0)));
        assertEquals("Float64", ddlBuilder.getSqlType(column("c", false, Types.DOUBLE, 0, 0)));
        assertEquals("Float64", ddlBuilder.getSqlType(column("c", false, Types.FLOAT, 0, 0)));
        assertEquals("Bool", ddlBuilder.getSqlType(column("c", false, Types.BIT, 1, 0)));
        assertEquals("Bool", ddlBuilder.getSqlType(column("c", false, Types.BOOLEAN, 1, 0)));
        assertEquals("Date32", ddlBuilder.getSqlType(column("c", false, Types.DATE, 0, 0)));
        assertEquals("DateTime64(6)", ddlBuilder.getSqlType(column("c", false, Types.TIMESTAMP, 0, 0)));
    }

    @Test
    void testSqlType_timestampTypeWithoutPrecisionFromPlatformColumnKeepsMicroseconds() {
        Column column = column("c", false, Types.TIMESTAMP, 26, 3);
        column.addPlatformColumn(new PlatformColumn("clickhouse", "DateTime64", -1, -1, null));
        assertEquals("DateTime64(6)", ddlBuilder.getSqlType(column));
    }

    @Test
    void testSqlType_textAndBinaryTypesAreStringWithoutSize() {
        for (int type : new int[] { Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.CLOB, Types.BINARY, Types.VARBINARY, Types.BLOB, Types.TIME }) {
            assertEquals("String", ddlBuilder.getSqlType(column("c", false, type, 100, 0)), "type " + type);
        }
    }

    @Test
    void testSqlType_decimalKeepsPrecisionAndScale() {
        assertEquals("Decimal(10, 2)", ddlBuilder.getSqlType(column("c", false, Types.DECIMAL, 10, 2)));
        assertEquals("Decimal(18, 0)", ddlBuilder.getSqlType(column("c", false, Types.NUMERIC, 18, 0)));
    }

    @Test
    void testSqlType_decimalWithoutPrecisionUsesDefault() {
        assertEquals("Decimal(38, 0)", ddlBuilder.getSqlType(column("c", false, Types.DECIMAL, 0, 0)));
    }

    @Test
    void testSqlType_decimalAbovePrecisionLimitIsClamped() {
        assertEquals("Decimal(38, 10)", ddlBuilder.getSqlType(column("c", false, Types.DECIMAL, 65535, 32767)));
    }

    @Test
    void testSqlType_decimalScaleNeverExceedsPrecision() {
        assertEquals("Decimal(5, 5)", ddlBuilder.getSqlType(column("c", false, Types.DECIMAL, 5, 9)));
    }

    @Test
    void testAlter_addColumn_usesAlterTableAddColumn() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), column("note", false, Types.VARCHAR, 50, 0));
        String ddl = alter(current, desired);
        assertTrue(ddl.contains("ALTER TABLE \"item\""), ddl);
        assertTrue(ddl.contains("ADD COLUMN \"note\" Nullable(String)"), ddl);
        assertFalse(ddl.contains("CREATE TABLE"), ddl);
    }

    @Test
    void testAlter_dropColumn_usesAlterTableDropColumn() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), column("note", false, Types.VARCHAR, 50, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0));
        String ddl = alter(current, desired);
        assertTrue(ddl.contains("DROP COLUMN \"note\""), ddl);
        assertFalse(ddl.contains("CREATE TABLE"), ddl);
    }

    @Test
    void testAlter_changeColumnType_usesModifyColumn() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), column("qty", false, Types.INTEGER, 10, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), column("qty", false, Types.BIGINT, 19, 0));
        String ddl = alter(current, desired);
        assertTrue(ddl.contains("MODIFY COLUMN \"qty\" Nullable(Int64)"), ddl);
        assertFalse(ddl.contains("CREATE TABLE"), ddl);
    }

    @Test
    void testAlter_primaryKeyChange_isNotAppliedAndTableIsNotRebuilt() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), column("code", false, Types.VARCHAR, 10, 0));
        Table desired = table("item", column("id", false, Types.INTEGER, 10, 0), column("code", true, Types.VARCHAR, 10, 0));
        String ddl = alter(current, desired);
        assertFalse(ddl.contains("CREATE TABLE"), ddl);
        assertFalse(ddl.contains("DROP TABLE"), ddl);
        assertFalse(ddl.contains("PRIMARY KEY"), ddl);
    }

    @Test
    void testAlter_identicalTables_producesNoDdl() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        assertEquals("", alter(current, desired).trim());
    }

    private String create(Table table) {
        Database database = new Database();
        database.addTable(table);
        return ddlBuilder.createTables(database, false);
    }

    private String alter(Table current, Table desired) {
        Database currentModel = new Database();
        currentModel.addTable(current);
        Database desiredModel = new Database();
        desiredModel.addTable(desired);
        return ddlBuilder.alterDatabase(currentModel, desiredModel);
    }

    private static Column column(String name, boolean primaryKey, int type, int size, int scale) {
        return new Column(name, primaryKey, type, size, scale);
    }

    private static Table table(String name, Column... columns) {
        Table table = new Table(name);
        for (Column column : columns) {
            table.addColumn(column);
        }
        return table;
    }

    @Test
    void testCreateTable_binaryColumnsGetTheBinaryComment() {
        String ddl = create(table("files", column("id", true, Types.INTEGER, 10, 0), column("a", false, Types.BINARY, 16, 0),
                column("b", false, Types.VARBINARY, 100, 0), column("c", false, Types.BLOB, 0, 0), column("d", false, Types.LONGVARBINARY, 0, 0)));
        assertTrue(ddl.contains("\"a\" Nullable(String) COMMENT 'symds:binary'"), ddl);
        assertTrue(ddl.contains("\"b\" Nullable(String) COMMENT 'symds:binary'"), ddl);
        assertTrue(ddl.contains("\"c\" Nullable(String) COMMENT 'symds:binary'"), ddl);
        assertTrue(ddl.contains("\"d\" Nullable(String) COMMENT 'symds:binary'"), ddl);
    }

    @Test
    void testCreateTable_textColumnsHaveNoComment() {
        String ddl = create(table("docs", column("id", true, Types.INTEGER, 10, 0), column("a", false, Types.VARCHAR, 50, 0),
                column("b", false, Types.CLOB, 0, 0), column("c", false, Types.CHAR, 10, 0)));
        assertFalse(ddl.contains("COMMENT"), ddl);
    }

    @Test
    void testAlter_addBinaryColumn_includesComment() {
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), column("data", false, Types.BLOB, 0, 0));
        assertTrue(alter(current, desired).contains("ADD COLUMN \"data\" Nullable(String) COMMENT 'symds:binary'"));
    }

    @Test
    void testAlter_sameDefinitionWithDifferentJdbcType_isNotModified() {
        // A binary column read back from ClickHouse is VARBINARY, while the source says BLOB. Both are created the same way.
        Table current = table("item", column("id", true, Types.INTEGER, 10, 0), column("data", false, Types.VARBINARY, 0, 0));
        Table desired = table("item", column("id", true, Types.INTEGER, 10, 0), column("data", false, Types.BLOB, 0, 0));
        assertEquals("", alter(current, desired).trim());
    }
}
