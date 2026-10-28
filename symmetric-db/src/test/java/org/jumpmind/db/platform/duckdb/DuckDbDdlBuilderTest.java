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
package org.jumpmind.db.platform.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuckDbDdlBuilderTest {
    private DuckDbDdlBuilder ddlBuilder;
    private DatabaseInfo databaseInfo;

    @BeforeEach
    void setUp() {
        ddlBuilder = new DuckDbDdlBuilder();
        databaseInfo = ddlBuilder.getDatabaseInfo();
    }

    @Test
    void testConstructor_disablesUnsupportedFeatures() {
        assertFalse(databaseInfo.isTriggersSupported());
        assertFalse(databaseInfo.isForeignKeysSupported());
    }

    @Test
    void testConstructor_setsCharColumnPaddingBehavior() {
        assertFalse(databaseInfo.isNonBlankCharColumnSpacePadded());
        assertFalse(databaseInfo.isBlankCharColumnSpacePadded());
        assertFalse(databaseInfo.isCharColumnSpaceTrimmed());
        assertFalse(databaseInfo.isEmptyStringNulled());
    }

    @Test
    void testConstructor_mapsBitToBoolean() {
        assertEquals("BOOLEAN", databaseInfo.getNativeType(Types.BIT));
    }

    @Test
    void testConstructor_mapsFloatToDouble() {
        assertEquals("DOUBLE", databaseInfo.getNativeType(Types.FLOAT));
        assertEquals(Types.DOUBLE, databaseInfo.getTargetJdbcType(Types.FLOAT));
    }

    @Test
    void testConstructor_mapsLongVarcharAndClobToVarchar() {
        assertEquals("VARCHAR", databaseInfo.getNativeType(Types.LONGVARCHAR));
        assertEquals("VARCHAR", databaseInfo.getNativeType(Types.CLOB));
        assertEquals(Types.VARCHAR, databaseInfo.getTargetJdbcType(Types.CLOB));
    }

    @Test
    void testConstructor_mapsNationalCharacterTypesToVarchar() {
        assertEquals("VARCHAR", databaseInfo.getNativeType(Types.LONGNVARCHAR));
        assertEquals("VARCHAR", databaseInfo.getNativeType(Types.NCHAR));
        assertEquals("VARCHAR", databaseInfo.getNativeType(Types.NVARCHAR));
        assertEquals(Types.VARCHAR, databaseInfo.getTargetJdbcType(Types.NVARCHAR));
    }

    @Test
    void testConstructor_mapsBinaryTypesToBlob() {
        assertEquals("BLOB", databaseInfo.getNativeType(Types.BINARY));
        assertEquals("BLOB", databaseInfo.getNativeType(Types.VARBINARY));
        assertEquals("BLOB", databaseInfo.getNativeType(Types.LONGVARBINARY));
        assertEquals(Types.BLOB, databaseInfo.getTargetJdbcType(Types.VARBINARY));
    }

    @Test
    void testConstructor_mapsTimestampWithTimeZoneToTimestampTz() {
        assertEquals("TIMESTAMPTZ", databaseInfo.getNativeType(ColumnTypes.TIMESTAMPTZ));
        assertEquals("TIMESTAMPTZ", databaseInfo.getNativeType(Types.TIMESTAMP_WITH_TIMEZONE));
        assertEquals(ColumnTypes.TIMESTAMPTZ, databaseInfo.getTargetJdbcType(Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Test
    void testConstructor_setsMaxSizesForDecimalAndNumericTypes() {
        assertEquals(38, databaseInfo.getMaxSize("DECIMAL"));
        assertEquals(38, databaseInfo.getMaxSize("NUMERIC"));
    }

    @Test
    void testConstructor_disablesSizesForCharacterAndBinaryTypes() {
        assertFalse(databaseInfo.hasSize(Types.CHAR));
        assertFalse(databaseInfo.hasSize(Types.VARCHAR));
        assertFalse(databaseInfo.hasSize(Types.NCHAR));
        assertFalse(databaseInfo.hasSize(Types.NVARCHAR));
        assertFalse(databaseInfo.hasSize(Types.BINARY));
        assertFalse(databaseInfo.hasSize(Types.VARBINARY));
    }

    @Test
    void testGetSqlType_writesVarcharWithoutSize() {
        assertEquals("VARCHAR", ddlBuilder.getSqlType(new Column("name", false, Types.VARCHAR, 50, 0)));
    }

    @Test
    void testGetSqlType_writesVarbinaryAsBlobWithoutSize() {
        assertEquals("BLOB", ddlBuilder.getSqlType(new Column("data", false, Types.VARBINARY, 10, 0)));
    }

    @Test
    void testGetSqlType_keepsDecimalPrecisionAndScale() {
        assertEquals("DECIMAL(18,2)", ddlBuilder.getSqlType(new Column("amount", false, Types.DECIMAL, 18, 2)));
    }

    @Test
    void testGetSqlType_capsDecimalPrecisionAt38() {
        assertEquals("DECIMAL(38,10)", ddlBuilder.getSqlType(new Column("amount", false, Types.DECIMAL, 50, 10)));
    }

    @Test
    void testWriteColumnAutoIncrementStmt_writesNextvalDefault() {
        Table table = tableWithAutoIncrementId();
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnAutoIncrementStmt(table, table.getColumnWithName("id"), ddl);
        assertEquals(" DEFAULT nextval('\"item_id_seq\"')", ddl.toString());
    }

    @Test
    void testWriteColumnAutoIncrementStmt_withSchema_qualifiesSequenceName() {
        Table table = tableWithAutoIncrementId();
        table.setSchema("sales");
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnAutoIncrementStmt(table, table.getColumnWithName("id"), ddl);
        assertEquals(" DEFAULT nextval('\"sales\".\"item_id_seq\"')", ddl.toString());
    }

    @Test
    void testCreateTable_withAutoIncrementColumn_createsSequenceBeforeTable() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.createTable(tableWithAutoIncrementId(), ddl, false, false);
        assertEquals("""
                CREATE SEQUENCE "item_id_seq";
                CREATE TABLE "item"(
                    "id" INTEGER NOT NULL  DEFAULT nextval('"item_id_seq"'),
                    PRIMARY KEY ("id")
                );
                """, ddl.toString());
    }

    @Test
    void testCreateTable_withSchema_qualifiesSequenceAndTable() {
        Table table = tableWithAutoIncrementId();
        table.setSchema("sales");
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.createTable(table, ddl, false, false);
        assertEquals("""
                CREATE SEQUENCE "sales"."item_id_seq";
                CREATE TABLE "sales"."item"(
                    "id" INTEGER NOT NULL  DEFAULT nextval('"sales"."item_id_seq"'),
                    PRIMARY KEY ("id")
                );
                """, ddl.toString());
    }

    @Test
    void testCreateTable_withoutAutoIncrementColumn_createsNoSequence() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.createTable(new Table("item", new Column("id", true, Types.INTEGER, 10, 0)), ddl, false, false);
        assertFalse(ddl.toString().contains("SEQUENCE"));
    }

    @Test
    void testCreateTable_temporary_createsNoSequence() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.createTable(tableWithAutoIncrementId(), ddl, true, false);
        assertFalse(ddl.toString().contains("CREATE SEQUENCE"));
    }

    @Test
    void testCreateTable_recreate_createsNoSequence() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.createTable(tableWithAutoIncrementId(), ddl, false, true);
        assertFalse(ddl.toString().contains("CREATE SEQUENCE"));
    }

    @Test
    void testDropTable_withAutoIncrementColumn_dropsTableThenSequence() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.dropTable(tableWithAutoIncrementId(), ddl, false, false);
        assertEquals("DROP TABLE IF EXISTS \"item\";\nDROP SEQUENCE \"item_id_seq\";\n", ddl.toString());
    }

    @Test
    void testDropTable_recreate_dropsTableOnly() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.dropTable(tableWithAutoIncrementId(), ddl, false, true);
        assertEquals("DROP TABLE IF EXISTS \"item\";\n", ddl.toString());
    }

    private Table tableWithAutoIncrementId() {
        Column id = new Column("id", true, Types.INTEGER, 10, 0);
        id.setAutoIncrement(true);
        return new Table("item", id);
    }
}
