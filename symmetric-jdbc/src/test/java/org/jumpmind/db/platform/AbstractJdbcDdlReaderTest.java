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
package org.jumpmind.db.platform;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class AbstractJdbcDdlReaderTest {
    @Test
    void testReadExportedKey() throws SQLException {
        AbstractJdbcDdlReader ddlReader = mock(AbstractJdbcDdlReader.class, Answers.CALLS_REAL_METHODS);
        Map<String, Object> metadataMap = new HashMap<String, Object>();
        metadataMap.put("FK_NAME", "test_fk");
        metadataMap.put("FKTABLE_NAME", "test_table");
        metadataMap.put("fktable_cat", "test_catalog_lowercase");
        metadataMap.put("fktable_schem", "test_schema_lowercase");
        metadataMap.put("FKCOLUMN_NAME", "foreign_col");
        metadataMap.put("PKCOLUMN_NAME", "local_col");
        metadataMap.put("KEY_SEQ", (short) 123);
        Map<String, ForeignKey> fkMap = new HashMap<String, ForeignKey>();
        ddlReader.readExportedKey(null, metadataMap, fkMap);
        assertEquals(1, fkMap.size());
        ForeignKey fk = fkMap.get("test_fk");
        assertEquals("test_fk", fk.getName());
        assertEquals("test_table", fk.getForeignTableName());
        assertEquals("test_catalog_lowercase", fk.getForeignTableCatalog());
        assertEquals("test_schema_lowercase", fk.getForeignTableSchema());
        assertEquals(1, fk.getReferenceCount());
        Reference reference = fk.getFirstReference();
        assertEquals("foreign_col", reference.getForeignColumnName());
        assertEquals("local_col", reference.getLocalColumnName());
        assertEquals(123, reference.getSequenceValue());
        metadataMap.put("FKTABLE_CAT", "TEST_CATALOG_UPPERCASE");
        metadataMap.put("FKTABLE_SCHEM", "TEST_SCHEMA_UPPERCASE");
        fkMap.clear();
        ddlReader.readExportedKey(null, metadataMap, fkMap);
        assertEquals(1, fkMap.size());
        fk = fkMap.get("test_fk");
        assertEquals("TEST_CATALOG_UPPERCASE", fk.getForeignTableCatalog());
        assertEquals("TEST_SCHEMA_UPPERCASE", fk.getForeignTableSchema());
        ddlReader.readExportedKey(null, metadataMap, fkMap);
        assertEquals(1, fkMap.size());
    }

    @Test
    void testReadForeignKey() throws SQLException {
        AbstractJdbcDdlReader ddlReader = mock(AbstractJdbcDdlReader.class, Answers.CALLS_REAL_METHODS);
        Map<String, Object> metadataMap = new HashMap<String, Object>();
        metadataMap.put("FK_NAME", "test_fk");
        metadataMap.put("PKTABLE_NAME", "test_table");
        metadataMap.put("pktable_cat", "test_catalog_lowercase");
        metadataMap.put("pktable_schem", "test_schema_lowercase");
        metadataMap.put("PKCOLUMN_NAME", "foreign_col");
        metadataMap.put("FKCOLUMN_NAME", "local_col");
        metadataMap.put("KEY_SEQ", (short) 123);
        Map<String, ForeignKey> fkMap = new HashMap<String, ForeignKey>();
        ddlReader.readForeignKey(null, metadataMap, fkMap);
        assertEquals(1, fkMap.size());
        ForeignKey fk = fkMap.get("test_fk");
        assertEquals("test_fk", fk.getName());
        assertEquals("test_table", fk.getForeignTableName());
        assertEquals("test_catalog_lowercase", fk.getForeignTableCatalog());
        assertEquals("test_schema_lowercase", fk.getForeignTableSchema());
        assertEquals(1, fk.getReferenceCount());
        Reference reference = fk.getFirstReference();
        assertEquals("foreign_col", reference.getForeignColumnName());
        assertEquals("local_col", reference.getLocalColumnName());
        assertEquals(123, reference.getSequenceValue());
        metadataMap.put("PKTABLE_CAT", "TEST_CATALOG_UPPERCASE");
        metadataMap.put("PKTABLE_SCHEM", "TEST_SCHEMA_UPPERCASE");
        fkMap.clear();
        ddlReader.readForeignKey(null, metadataMap, fkMap);
        assertEquals(1, fkMap.size());
        fk = fkMap.get("test_fk");
        assertEquals("TEST_CATALOG_UPPERCASE", fk.getForeignTableCatalog());
        assertEquals("TEST_SCHEMA_UPPERCASE", fk.getForeignTableSchema());
        ddlReader.readForeignKey(null, metadataMap, fkMap);
        assertEquals(1, fkMap.size());
    }
}
