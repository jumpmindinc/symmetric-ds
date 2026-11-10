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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClickHouseDdlReaderTest {
    private ClickHouseDdlReader ddlReader;

    @BeforeEach
    void setUp() {
        IDatabasePlatform platformMock = mock(IDatabasePlatform.class);
        when(platformMock.getName()).thenReturn("clickhouse");
        ddlReader = new ClickHouseDdlReader(platformMock);
    }

    @Test
    void testConstructor_clearsDefaultPatterns() {
        assertNull(ddlReader.getDefaultCatalogPattern());
        assertNull(ddlReader.getDefaultSchemaPattern());
        assertNull(ddlReader.getDefaultTablePattern());
    }

    @Test
    void testStripWrappers() {
        assertEquals("Int32", ClickHouseDdlReader.stripWrappers("Int32"));
        assertEquals("Decimal(10, 2)", ClickHouseDdlReader.stripWrappers("Nullable(Decimal(10, 2))"));
        assertEquals("String", ClickHouseDdlReader.stripWrappers("LowCardinality(Nullable(String))"));
        assertNull(ClickHouseDdlReader.stripWrappers(null));
    }

    @Test
    void testBaseTypeName() {
        assertEquals("Decimal", ClickHouseDdlReader.baseTypeName("Decimal(10, 2)"));
        assertEquals("DateTime64", ClickHouseDdlReader.baseTypeName("DateTime64(6)"));
        assertEquals("Int32", ClickHouseDdlReader.baseTypeName("Int32"));
        assertNull(ClickHouseDdlReader.baseTypeName(null));
    }

    @Test
    void testCleanKeyName() {
        assertEquals("id", ClickHouseDdlReader.cleanKeyName("id"));
        assertEquals("id", ClickHouseDdlReader.cleanKeyName("(id)"));
        assertEquals("id", ClickHouseDdlReader.cleanKeyName("( id )"));
        assertEquals("id", ClickHouseDdlReader.cleanKeyName("\"id\""));
        assertEquals("id", ClickHouseDdlReader.cleanKeyName("(`id`)"));
        assertNull(ClickHouseDdlReader.cleanKeyName(null));
    }

    @Test
    void testReadColumn_nullableDecimal_stripsWrapperAndKeepsPrecision() throws Exception {
        Column column = readColumn(values("Nullable(Decimal(10, 2))", Types.DECIMAL, "10", 2, "YES"));
        assertEquals("Decimal(10, 2)", column.getJdbcTypeName());
        assertEquals(Types.DECIMAL, column.getMappedTypeCode());
        assertFalse(column.isRequired());
        PlatformColumn platformColumn = column.findPlatformColumn("clickhouse");
        assertEquals("Decimal", platformColumn.getType());
        assertEquals(10, platformColumn.getSize());
        assertEquals(2, platformColumn.getDecimalDigits());
    }

    @Test
    void testReadColumn_notNullColumn_isRequired() throws Exception {
        Column column = readColumn(values("Int32", Types.INTEGER, "4", 0, "NO"));
        assertEquals("Int32", column.getJdbcTypeName());
        assertTrue(column.isRequired());
    }

    @Test
    void testReadColumn_string_hasNoSizeInPlatformType() throws Exception {
        Column column = readColumn(values("Nullable(String)", Types.VARCHAR, "0", 0, "YES"));
        assertEquals("String", column.findPlatformColumn("clickhouse").getType());
    }

    @Test
    void testReadColumn_binaryComment_isReportedAsVarbinary() throws Exception {
        Map<String, Object> values = values("Nullable(String)", Types.VARCHAR, "0", 0, "YES");
        values.put("REMARKS", "symds:binary");
        Column column = readColumn(values);
        assertEquals(Types.VARBINARY, column.getMappedTypeCode());
        assertEquals(Types.VARBINARY, column.getJdbcTypeCode());
        assertEquals("String", column.findPlatformColumn("clickhouse").getType());
    }

    @Test
    void testReadColumn_otherCommentOrNone_staysVarchar() throws Exception {
        Map<String, Object> values = values("Nullable(String)", Types.VARCHAR, "0", 0, "YES");
        values.put("REMARKS", "some other comment");
        assertEquals(Types.VARCHAR, readColumn(values).getMappedTypeCode());
        assertEquals(Types.VARCHAR, readColumn(values("Nullable(String)", Types.VARCHAR, "0", 0, "YES")).getMappedTypeCode());
    }

    @Test
    void testReadColumn_serialIdDefaultBecomesAutoIncrement() throws Exception {
        Map<String, Object> values = values("Int64", Types.BIGINT, "19", 0, "NO");
        values.put("COLUMN_DEF", "generateSerialID('item_id')");
        Column column = readColumn(values);
        assertTrue(column.isAutoIncrement());
        assertNull(column.getDefaultValue());
    }

    @Test
    void testPostprocess_textDefaultIsUnescapedAndQuoted() {
        Table table = tableWithDefault(Types.VARCHAR, "'it\\'s'");
        ddlReader.postprocessRelationFromDatabase(table);
        assertEquals("'it''s'", table.getColumn(0).getDefaultValue());
    }

    @Test
    void testPostprocess_dateDefaultIsQuoted() {
        Table table = tableWithDefault(Types.DATE, "'2020-01-02'");
        ddlReader.postprocessRelationFromDatabase(table);
        assertEquals("'2020-01-02'", table.getColumn(0).getDefaultValue());
    }

    @Test
    void testPostprocess_numericDefaultIsLeftAlone() {
        Table table = tableWithDefault(Types.INTEGER, "5");
        ddlReader.postprocessRelationFromDatabase(table);
        assertEquals("5", table.getColumn(0).getDefaultValue());
    }

    @Test
    void testReadColumn_emptyDefaultMeansNoDefault() throws Exception {
        Map<String, Object> values = values("String", Types.VARCHAR, "0", 0, "NO");
        values.put("COLUMN_DEF", "");
        assertNull(readColumn(values).getDefaultValue());
    }

    @Test
    void testUnescape() {
        assertEquals("a'b\\c\nd", ClickHouseDdlReader.unescape("a\\'b\\\\c\\nd"));
        assertEquals("plain", ClickHouseDdlReader.unescape("plain"));
    }

    private static Table tableWithDefault(int type, String defaultValue) {
        Column column = new Column("c");
        column.setMappedTypeCode(type);
        column.setDefaultValue(defaultValue);
        Table table = new Table("t");
        table.addColumn(column);
        return table;
    }

    private Column readColumn(Map<String, Object> values) throws Exception {
        return ddlReader.readColumn(null, values);
    }

    private Map<String, Object> values(String typeName, int jdbcType, String size, int digits, String nullable) {
        Map<String, Object> values = new HashMap<>();
        values.put("COLUMN_NAME", "c");
        values.put("TYPE_NAME", typeName);
        values.put("DATA_TYPE", jdbcType);
        values.put("NUM_PREC_RADIX", 10);
        values.put("COLUMN_SIZE", size);
        values.put("DECIMAL_DIGITS", digits);
        values.put("IS_NULLABLE", nullable);
        return values;
    }
}
