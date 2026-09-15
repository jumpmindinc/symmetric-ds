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
package org.jumpmind.symmetric.io.data.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.util.Map;

import org.jumpmind.db.model.Relation;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SymXmlDataReaderTest {
    private SymXmlDataReader openedReader;

    @AfterEach
    void tearDown() {
        if (openedReader != null) {
            openedReader.close();
        }
    }

    @Test
    void testNextRelation_readsEntityName() {
        SymXmlDataReader reader = open(batchXml("I", "<data key=\"ID\">1</data>"));
        reader.nextBatch();
        assertEquals("ITEM", reader.nextRelation().getName());
    }

    @Test
    void testNextRelation_readsCatalogAndSchema() {
        SymXmlDataReader reader = open("<batch id=\"1\"><row entity=\"ITEM\" catalog=\"cat\" schema=\"sch\" dml=\"I\">"
                + "<data key=\"ID\">1</data></row></batch>");
        reader.nextBatch();
        Relation relation = reader.nextRelation();
        assertEquals("cat", relation.getCatalog());
        assertEquals("sch", relation.getSchema());
    }

    @Test
    void testNextData_readsInsertRow() {
        SymXmlDataReader reader = open(batchXml("I", "<data key=\"ID\">1</data><data key=\"NAME\">widget</data>"));
        reader.nextBatch();
        Relation relation = reader.nextRelation();
        CsvData data = reader.nextData();
        assertEquals(DataEventType.INSERT, data.getDataEventType());
        Map<String, String> values = data.toColumnNameValuePairs(relation.getColumnNames(), CsvData.ROW_DATA);
        assertEquals("1", values.get("ID"));
        assertEquals("widget", values.get("NAME"));
    }

    @Test
    void testNextData_readsUpdateDml() {
        assertEquals(DataEventType.UPDATE, readSingleData("U").getDataEventType());
    }

    @Test
    void testNextData_readsDeleteDml() {
        assertEquals(DataEventType.DELETE, readSingleData("D").getDataEventType());
    }

    @Test
    void testNextData_readsCreateDml() {
        assertEquals(DataEventType.CREATE, readSingleData("C").getDataEventType());
    }

    @Test
    void testNextData_readsSqlDml() {
        assertEquals(DataEventType.SQL, readSingleData("S").getDataEventType());
    }

    @Test
    void testNextData_readsBshDml() {
        assertEquals(DataEventType.BSH, readSingleData("B").getDataEventType());
    }

    @Test
    void testNextData_readsReloadDml() {
        assertEquals(DataEventType.RELOAD, readSingleData("R").getDataEventType());
    }

    @Test
    void testNextData_readsNilValueAsNull() {
        SymXmlDataReader reader = open(batchXml("I",
                "<data key=\"ID\">1</data><data key=\"NAME\" xsi:nil=\"true\" />"));
        reader.nextBatch();
        Relation relation = reader.nextRelation();
        Map<String, String> values = reader.nextData().toColumnNameValuePairs(relation.getColumnNames(), CsvData.ROW_DATA);
        assertNull(values.get("NAME"));
    }

    @Test
    void testNextBatch_returnsBatchForWellFormedDocument() {
        SymXmlDataReader reader = open(batchXml("I", "<data key=\"ID\">1</data>"));
        assertNotNull(reader.nextBatch());
    }

    @Test
    void testNextData_returnsNullWhenExhausted() {
        SymXmlDataReader reader = open(batchXml("I", "<data key=\"ID\">1</data>"));
        reader.nextBatch();
        reader.nextRelation();
        reader.nextData();
        assertNull(reader.nextData());
    }

    private CsvData readSingleData(String dml) {
        SymXmlDataReader reader = open(batchXml(dml, "<data key=\"ID\">1</data>"));
        reader.nextBatch();
        reader.nextRelation();
        return reader.nextData();
    }

    private String batchXml(String dml, String dataElements) {
        return "<batch id=\"1\"><row entity=\"ITEM\" dml=\"" + dml + "\">" + dataElements + "</row></batch>";
    }

    private SymXmlDataReader open(String xml) {
        SymXmlDataReader reader = new SymXmlDataReader(new StringReader(xml));
        reader.open(new DataContext());
        openedReader = reader;
        return reader;
    }
}
