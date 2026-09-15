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
package org.jumpmind.db.platform.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.sqlite.SqliteDdlReader.ColumnMapper;
import org.jumpmind.db.platform.sqlite.SqliteDdlReader.IndexColumnMapper;
import org.jumpmind.db.platform.sqlite.SqliteDdlReader.IndexMapper;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqliteDdlReaderTest {
    private SqliteDdlReader reader;
    private ColumnMapper columnMapper;

    @BeforeEach
    void setUp() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        reader = new SqliteDdlReader(platform);
        columnMapper = new ColumnMapper();
    }

    @Test
    void testGetCatalogNames_isEmpty() {
        assertTrue(reader.getCatalogNames().isEmpty());
    }

    @Test
    void testGetSchemaNames_isEmpty() {
        assertTrue(reader.getSchemaNames("main").isEmpty());
    }

    @Test
    void testGetRelationTypes_isEmpty() {
        assertTrue(reader.getRelationTypes().isEmpty());
    }

    @Test
    void testGetColumnNames_isEmpty() {
        assertTrue(reader.getColumnNames("main", null, "item").isEmpty());
    }

    @Test
    void testToJdbcType_withIntegerVariants() {
        assertEquals(TypeMap.INTEGER, columnMapper.toJdbcType("INTEGER"));
        assertEquals(TypeMap.INTEGER, columnMapper.toJdbcType("int"));
    }

    @Test
    void testToJdbcType_withNumeric() {
        assertEquals(TypeMap.NUMERIC, columnMapper.toJdbcType("NUMERIC"));
    }

    @Test
    void testToJdbcType_withBlob() {
        assertEquals(TypeMap.BLOB, columnMapper.toJdbcType("BLOB"));
    }

    @Test
    void testToJdbcType_withClob() {
        assertEquals(TypeMap.CLOB, columnMapper.toJdbcType("CLOB"));
    }

    @Test
    void testToJdbcType_withTextAndCharVariants() {
        assertEquals(TypeMap.VARCHAR, columnMapper.toJdbcType("TEXT"));
        assertEquals(TypeMap.VARCHAR, columnMapper.toJdbcType("VARCHAR(50)"));
        assertEquals(TypeMap.VARCHAR, columnMapper.toJdbcType("NCHAR"));
    }

    @Test
    void testToJdbcType_withFloatingPointVariants() {
        assertEquals(TypeMap.FLOAT, columnMapper.toJdbcType("FLOAT"));
        assertEquals(TypeMap.DOUBLE, columnMapper.toJdbcType("DOUBLE"));
        assertEquals(TypeMap.REAL, columnMapper.toJdbcType("REAL"));
        assertEquals(TypeMap.DECIMAL, columnMapper.toJdbcType("DECIMAL(10,2)"));
    }

    @Test
    void testToJdbcType_withTemporalVariants() {
        assertEquals(TypeMap.DATE, columnMapper.toJdbcType("DATE"));
        assertEquals(TypeMap.TIMESTAMP, columnMapper.toJdbcType("TIMESTAMP"));
        assertEquals(TypeMap.TIME, columnMapper.toJdbcType("TIME"));
    }

    @Test
    void testToJdbcType_withNull() {
        assertEquals(TypeMap.VARCHAR, columnMapper.toJdbcType(null));
    }

    @Test
    void testToJdbcType_withUnrecognizedType() {
        assertEquals(TypeMap.VARCHAR, columnMapper.toJdbcType("SOMETHING_ELSE"));
    }

    @Test
    void testScrubDefaultValue_stripsSurroundingQuotes() {
        assertEquals("abc", columnMapper.scrubDefaultValue("'abc'"));
    }

    @Test
    void testScrubDefaultValue_leavesUnquotedValue() {
        assertEquals("abc", columnMapper.scrubDefaultValue("abc"));
    }

    @Test
    void testScrubDefaultValue_withNull() {
        assertNull(columnMapper.scrubDefaultValue(null));
    }

    @Test
    void testScrubDefaultValue_leavesLeadingQuoteOnly() {
        assertEquals("'abc", columnMapper.scrubDefaultValue("'abc"));
    }

    @Test
    void testColumnMapper_mapRow() {
        Row row = new Row(5);
        row.put("name", "ID");
        row.put("type", "INTEGER");
        row.put("pk", 1);
        row.put("notnull", 1);
        row.put("dflt_value", "'0'");
        row.put("hidden", 0);
        Column column = columnMapper.mapRow(row);
        assertEquals("ID", column.getName());
        assertEquals(TypeMap.INTEGER, column.getMappedType());
        assertTrue(column.isPrimaryKey());
        assertTrue(column.isRequired());
        assertEquals("0", column.getDefaultValue());
        assertFalse(column.isGenerated());
    }

    @Test
    void testColumnMapper_mapRow_marksGeneratedColumn() {
        Row row = new Row(5);
        row.put("name", "TOTAL");
        row.put("type", "INTEGER");
        row.put("pk", 0);
        row.put("notnull", 0);
        row.put("dflt_value", null);
        row.put("hidden", 2);
        Column column = columnMapper.mapRow(row);
        assertTrue(column.isGenerated());
        assertFalse(column.isPrimaryKey());
        assertFalse(column.isRequired());
        assertNull(column.getDefaultValue());
    }

    @Test
    void testIndexMapper_mapRow_withUniqueIndex() {
        Row row = new Row(2);
        row.put("name", "IDX_ITEM");
        row.put("unique", 1);
        IIndex index = new IndexMapper().mapRow(row);
        assertEquals("IDX_ITEM", index.getName());
        assertTrue(index.isUnique());
    }

    @Test
    void testIndexMapper_mapRow_withNonUniqueIndex() {
        Row row = new Row(2);
        row.put("name", "IDX_ITEM");
        row.put("unique", 0);
        IIndex index = new IndexMapper().mapRow(row);
        assertEquals("IDX_ITEM", index.getName());
        assertFalse(index.isUnique());
    }

    @Test
    void testIndexColumnMapper_mapRow() {
        Row row = new Row(2);
        row.put("name", "ID");
        row.put("seqno", 3);
        IndexColumn column = new IndexColumnMapper().mapRow(row);
        assertEquals("ID", column.getName());
        assertEquals(3, column.getOrdinalPosition());
    }
}
