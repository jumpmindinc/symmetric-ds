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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class DuckDbDdlReaderTest {
    private Connection connection;
    private DuckDbDatabasePlatform platform;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        platform = new DuckDbDatabasePlatform(new SingleConnectionDataSource(connection, true), new SqlTemplateSettings());
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void testReadIndices_uniqueIndex_isReadAsUnique() throws SQLException {
        platform.getSqlTemplate().update("CREATE TABLE item (id INTEGER, sku VARCHAR)");
        platform.getSqlTemplate().update("CREATE UNIQUE INDEX idx_item_sku ON item (sku)");
        List<IIndex> indices = readIndices("item");
        assertEquals(1, indices.size());
        assertEquals("idx_item_sku", indices.get(0).getName());
        assertTrue(indices.get(0).isUnique());
    }

    @Test
    void testReadIndices_nonUniqueIndex_isReadAsNonUnique() throws SQLException {
        platform.getSqlTemplate().update("CREATE TABLE item (id INTEGER, sku VARCHAR)");
        platform.getSqlTemplate().update("CREATE INDEX idx_item_sku ON item (sku)");
        List<IIndex> indices = readIndices("item");
        assertEquals(1, indices.size());
        assertFalse(indices.get(0).isUnique());
    }

    @Test
    void testReadIndices_multiColumnIndex_keepsColumnOrderAndUnquotesNames() throws SQLException {
        platform.getSqlTemplate().update("CREATE TABLE item (id INTEGER, \"name\" VARCHAR, qty INTEGER)");
        platform.getSqlTemplate().update("CREATE INDEX idx_item_qty_name ON item (qty, \"name\")");
        IndexColumn[] columns = readIndices("item").get(0).getColumns();
        assertEquals(2, columns.length);
        assertEquals("qty", columns[0].getName());
        assertEquals(1, columns[0].getOrdinalPosition());
        assertEquals("name", columns[1].getName());
        assertEquals(2, columns[1].getOrdinalPosition());
    }

    @Test
    void testReadIndices_onlyReturnsIndexesForRequestedTableAndSchema() throws SQLException {
        platform.getSqlTemplate().update("CREATE SCHEMA other");
        platform.getSqlTemplate().update("CREATE TABLE main.item (sku VARCHAR)");
        platform.getSqlTemplate().update("CREATE INDEX idx_main_item ON main.item (sku)");
        platform.getSqlTemplate().update("CREATE TABLE main.other_item (sku VARCHAR)");
        platform.getSqlTemplate().update("CREATE INDEX idx_main_other_item ON main.other_item (sku)");
        platform.getSqlTemplate().update("CREATE TABLE other.item (sku VARCHAR)");
        platform.getSqlTemplate().update("CREATE INDEX idx_other_item ON other.item (sku)");
        List<String> actual = readIndices("item").stream().map(IIndex::getName).toList();
        assertEquals(List.of("idx_main_item"), actual);
    }

    @Test
    void testReadIndices_tableWithoutIndexes_returnsEmpty() throws SQLException {
        platform.getSqlTemplate().update("CREATE TABLE item (id INTEGER)");
        assertTrue(readIndices("item").isEmpty());
    }

    @Test
    void testReadColumn_nextvalDefault_marksAutoIncrementAndClearsDefault() throws SQLException {
        Column column = readColumn("nextval('\"main\".\"item_id_seq\"')");
        assertTrue(column.isAutoIncrement());
        assertNull(column.getDefaultValue());
    }

    @Test
    void testReadColumn_literalDefault_isKept() throws SQLException {
        Column column = readColumn("0");
        assertFalse(column.isAutoIncrement());
        assertEquals("0", column.getDefaultValue());
    }

    @Test
    void testReadColumn_noDefault_isNotAutoIncrement() throws SQLException {
        Column column = readColumn(null);
        assertFalse(column.isAutoIncrement());
        assertNull(column.getDefaultValue());
    }

    private List<IIndex> readIndices(String tableName) throws SQLException {
        DatabaseMetaDataWrapper metaData = new DatabaseMetaDataWrapper();
        metaData.setSchemaPattern(platform.getDefaultSchema());
        return new ArrayList<>(((DuckDbDdlReader) platform.getDdlReader()).readIndices(connection, metaData, tableName));
    }

    private Column readColumn(String columnDef) throws SQLException {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("COLUMN_NAME", "id");
        values.put("TYPE_NAME", "INTEGER");
        values.put("DATA_TYPE", Integer.valueOf(Types.INTEGER));
        values.put("NUM_PREC_RADIX", Integer.valueOf(10));
        values.put("COLUMN_SIZE", "32");
        values.put("DECIMAL_DIGITS", Integer.valueOf(0));
        values.put("COLUMN_DEF", columnDef);
        values.put("IS_NULLABLE", "YES");
        return ((DuckDbDdlReader) platform.getDdlReader()).readColumn(null, values);
    }
}
