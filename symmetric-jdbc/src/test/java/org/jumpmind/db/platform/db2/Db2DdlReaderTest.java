/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.platform.db2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Db2DdlReaderTest {
    private Db2DatabasePlatform platform;
    private Db2DdlReader ddlReader;

    @BeforeEach
    void setUp() {
        platform = mock(Db2DatabasePlatform.class);
        when(platform.getName()).thenReturn("db2");
        ddlReader = new Db2DdlReader(platform);
    }

    @Test
    void testDefaultCatalogReturnsNull() {
        Db2DatabasePlatform realPlatform = mock(Db2DatabasePlatform.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        assertNull(realPlatform.getDefaultCatalog());
    }

    @Test
    void testMapLongVarcharReturnsLongVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "LONG VARCHAR");
        Integer result = ddlReader.mapUnknownJdbcTypeForColumn(values);
        assertEquals(Types.LONGVARCHAR, result);
    }

    @Test
    void testMapClobReturnsLongVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "CLOB");
        Integer result = ddlReader.mapUnknownJdbcTypeForColumn(values);
        assertEquals(Types.LONGVARCHAR, result);
    }

    @Test
    void testMapDbclobReturnsLongVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "DBCLOB");
        Integer result = ddlReader.mapUnknownJdbcTypeForColumn(values);
        assertEquals(Types.LONGVARCHAR, result);
    }

    @Test
    void testMapRowidReturnsVarchar() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "ROWID");
        Integer result = ddlReader.mapUnknownJdbcTypeForColumn(values);
        assertEquals(Types.VARCHAR, result);
    }

    @Test
    void testMapXmlReturnsSqlXml() {
        Map<String, Object> values = new HashMap<>();
        values.put("TYPE_NAME", "XML");
        Integer result = ddlReader.mapUnknownJdbcTypeForColumn(values);
        assertEquals(Types.SQLXML, result);
    }

    @Test
    void testReadColumnFallsBackToNameKey() throws SQLException {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        when(metaData.getSchemaPattern()).thenReturn("TESTSCHEMA");
        Map<String, Object> values = new HashMap<>();
        values.put("NAME", "MY_COLUMN");
        values.put("TYPE_NAME", "VARCHAR");
        values.put("DATA_TYPE", Types.VARCHAR);
        values.put("COLUMN_SIZE", "100");
        values.put("DECIMAL_DIGITS", 0);
        values.put("NUM_PREC_RADIX", 10);
        values.put("IS_NULLABLE", "YES");
        values.put("IS_AUTOINCREMENT", "NO");
        var column = ddlReader.readColumn(metaData, values);
        assertNotNull(column);
        assertEquals("MY_COLUMN", column.getName());
    }

    @Test
    void testReadColumnUsesStandardKeyWhenPresent() throws SQLException {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        when(metaData.getSchemaPattern()).thenReturn("TESTSCHEMA");
        Map<String, Object> values = new HashMap<>();
        values.put("COLUMN_NAME", "STANDARD_COL");
        values.put("NAME", "ALT_COL");
        values.put("TYPE_NAME", "VARCHAR");
        values.put("DATA_TYPE", Types.VARCHAR);
        values.put("COLUMN_SIZE", "100");
        values.put("DECIMAL_DIGITS", 0);
        values.put("NUM_PREC_RADIX", 10);
        values.put("IS_NULLABLE", "YES");
        values.put("IS_AUTOINCREMENT", "NO");
        var column = ddlReader.readColumn(metaData, values);
        assertNotNull(column);
        assertEquals("STANDARD_COL", column.getName());
    }

    @Test
    void testReadForeignKeyFallsBackToReftbname() throws SQLException {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        Map<String, Object> values = new HashMap<>();
        values.put("FK_NAME", "FK_TEST");
        values.put("REFTBNAME", "PARENT_TABLE");
        values.put("COLNAME", "PARENT_ID");
        values.put("FKCOLUMN_NAME", "CHILD_FK_COL");
        values.put("KEY_SEQ", (short) 1);
        Map<String, ForeignKey> knownFks = new HashMap<>();
        ddlReader.readForeignKey(metaData, values, knownFks);
        assertEquals(1, knownFks.size());
        ForeignKey fk = knownFks.get("FK_TEST");
        assertNotNull(fk);
        assertEquals("PARENT_TABLE", fk.getForeignTableName());
        assertEquals("PARENT_ID", fk.getFirstReference().getForeignColumnName());
    }

    @Test
    void testReadForeignKeyUsesStandardKeysWhenPresent() throws SQLException {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        Map<String, Object> values = new HashMap<>();
        values.put("FK_NAME", "FK_TEST2");
        values.put("PKTABLE_NAME", "STANDARD_PARENT");
        values.put("REFTBNAME", "ALT_PARENT");
        values.put("PKCOLUMN_NAME", "STANDARD_PK_COL");
        values.put("COLNAME", "ALT_PK_COL");
        values.put("FKCOLUMN_NAME", "CHILD_FK_COL");
        values.put("KEY_SEQ", (short) 1);
        Map<String, ForeignKey> knownFks = new HashMap<>();
        ddlReader.readForeignKey(metaData, values, knownFks);
        assertEquals(1, knownFks.size());
        ForeignKey fk = knownFks.get("FK_TEST2");
        assertNotNull(fk);
        assertEquals("STANDARD_PARENT", fk.getForeignTableName());
        assertEquals("STANDARD_PK_COL", fk.getFirstReference().getForeignColumnName());
    }

    @Test
    void testReadForeignKeySkipsWhenNoTableName() throws SQLException {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        Map<String, Object> values = new HashMap<>();
        values.put("FK_NAME", "FK_ORPHAN");
        values.put("FKCOLUMN_NAME", "CHILD_FK_COL");
        values.put("KEY_SEQ", (short) 1);
        Map<String, ForeignKey> knownFks = new HashMap<>();
        ddlReader.readForeignKey(metaData, values, knownFks);
        assertTrue(knownFks.isEmpty());
    }

    @Test
    void testReadForeignKeyFallsBackToTbname() throws SQLException {
        DatabaseMetaDataWrapper metaData = mock(DatabaseMetaDataWrapper.class);
        Map<String, Object> values = new HashMap<>();
        values.put("FK_NAME", "FK_TBNAME");
        values.put("TBNAME", "TBNAME_PARENT");
        values.put("PKCOLUMN_NAME", "PK_COL");
        values.put("FKCOLUMN_NAME", "FK_COL");
        values.put("KEY_SEQ", (short) 1);
        Map<String, ForeignKey> knownFks = new HashMap<>();
        ddlReader.readForeignKey(metaData, values, knownFks);
        assertEquals(1, knownFks.size());
        ForeignKey fk = knownFks.get("FK_TBNAME");
        assertEquals("TBNAME_PARENT", fk.getForeignTableName());
    }
}
