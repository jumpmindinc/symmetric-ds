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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;

import org.jumpmind.db.model.Relation;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CsvTableDataReaderTest {
    private CsvTableDataReader openedReader;

    @AfterEach
    void tearDown() {
        if (openedReader != null) {
            openedReader.close();
        }
    }

    private static final String CATALOG = "cat";
    private static final String SCHEMA = "sch";
    private static final String TABLE = "item";

    @Test
    void testNextRelation_readsColumnNamesFromHeader() {
        CsvTableDataReader reader = open("id,name\n1,widget\n");
        Relation relation = reader.nextRelation();
        assertArrayEquals(new String[] { "id", "name" }, relation.getColumnNames());
    }

    @Test
    void testNextRelation_returnsNullOnSecondCall() {
        CsvTableDataReader reader = open("id,name\n1,widget\n");
        reader.nextRelation();
        assertNull(reader.nextRelation());
    }

    @Test
    void testNextRelation_carriesCatalogAndSchema() {
        CsvTableDataReader reader = open("id,name\n");
        Relation relation = reader.nextRelation();
        assertEquals(CATALOG, relation.getCatalog());
        assertEquals(SCHEMA, relation.getSchema());
        assertEquals(TABLE, relation.getName());
    }

    @Test
    void testNextBatch_returnsBatchOnceThenNull() {
        CsvTableDataReader reader = open("id,name\n");
        Batch batch = reader.nextBatch();
        assertNotNull(batch);
        assertEquals(BinaryEncoding.BASE64, batch.getBinaryEncoding());
        assertNull(reader.nextBatch());
    }

    @Test
    void testNextData_readsRowsAsInserts() {
        CsvTableDataReader reader = open("id,name\n1,widget\n2,gadget\n");
        reader.nextRelation();
        CsvData first = reader.nextData();
        assertEquals(DataEventType.INSERT, first.getDataEventType());
        assertArrayEquals(new String[] { "1", "widget" }, first.getParsedData(CsvData.ROW_DATA));
        assertArrayEquals(new String[] { "2", "gadget" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_returnsNullWhenExhausted() {
        CsvTableDataReader reader = open("id,name\n1,widget\n");
        reader.nextRelation();
        reader.nextData();
        assertNull(reader.nextData());
    }

    @Test
    void testNextData_marksBatchCompleteWhenExhausted() {
        CsvTableDataReader reader = open("id,name\n");
        Batch batch = reader.nextBatch();
        reader.nextRelation();
        reader.nextData();
        assertEquals(true, batch.isComplete());
    }

    @Test
    void testNextData_returnsNullBeforeRelationIsRead() {
        CsvTableDataReader reader = open("id,name\n1,widget\n");
        assertNull(reader.nextData());
    }

    @Test
    void testNextData_skipsCommentLines() {
        CsvTableDataReader reader = open("id,name\n#a comment\n1,widget\n");
        reader.nextRelation();
        assertArrayEquals(new String[] { "1", "widget" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_recordsReadByteCount() {
        CsvTableDataReader reader = open("id,name\n1,widget\n");
        Batch batch = reader.nextBatch();
        reader.nextRelation();
        reader.nextData();
        assertEquals(7, reader.getStatistics().get(batch).get(DataReaderStatistics.READ_BYTE_COUNT));
    }

    @Test
    void testNextData_tracksLineNumberInContext() {
        DataContext context = new DataContext();
        CsvTableDataReader reader = new CsvTableDataReader(BinaryEncoding.BASE64, CATALOG, SCHEMA, TABLE,
                new StringReader("id,name\n1,widget\n2,gadget\n"));
        reader.open(context);
        openedReader = reader;
        reader.nextRelation();
        reader.nextData();
        reader.nextData();
        assertEquals(2, context.get(AbstractRelationDataReader.CTX_LINE_NUMBER));
    }

    private CsvTableDataReader open(String csv) {
        CsvTableDataReader reader = new CsvTableDataReader(BinaryEncoding.BASE64, CATALOG, SCHEMA, TABLE, new StringReader(csv));
        reader.open(new DataContext());
        openedReader = reader;
        return reader;
    }
}
