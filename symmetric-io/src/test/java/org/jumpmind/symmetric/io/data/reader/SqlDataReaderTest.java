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

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SqlDataReaderTest {
    private SqlDataReader openedReader;

    @AfterEach
    void tearDown() {
        if (openedReader != null) {
            openedReader.close();
        }
    }

    @Test
    void testNextData_readsSingleStatement() {
        SqlDataReader reader = open("delete from item;");
        CsvData data = reader.nextData();
        assertEquals(DataEventType.SQL, data.getDataEventType());
        assertArrayEquals(new String[] { "delete from item" }, data.getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_readsStatementsBeforeRelationIsRequested() {
        SqlDataReader reader = open("delete from item;");
        assertNotNull(reader.nextData());
    }

    @Test
    void testNextData_readsMultipleStatements() {
        SqlDataReader reader = open("delete from item;\nupdate item set x = 1;");
        assertArrayEquals(new String[] { "delete from item" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
        assertArrayEquals(new String[] { "update item set x = 1" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_returnsNullWhenExhausted() {
        SqlDataReader reader = open("delete from item;");
        reader.nextData();
        assertNull(reader.nextData());
    }

    @Test
    void testNextData_withEmptyScript() {
        assertNull(open("").nextData());
    }

    @Test
    void testNextData_marksBatchCompleteWhenExhausted() {
        SqlDataReader reader = open("delete from item;");
        Batch batch = reader.nextBatch();
        reader.nextData();
        reader.nextData();
        assertEquals(true, batch.isComplete());
    }

    @Test
    void testNextBatch_usesHexBinaryEncoding() {
        assertEquals(BinaryEncoding.HEX, open("delete from item;").nextBatch().getBinaryEncoding());
    }

    @Test
    void testNextRelation_isNullForSqlScripts() {
        assertNull(open("delete from item;").nextRelation());
    }

    @Test
    void testConstructor_fromInputStream() {
        SqlDataReader reader = new SqlDataReader(new ByteArrayInputStream("delete from item;".getBytes(StandardCharsets.UTF_8)));
        reader.open(new DataContext());
        openedReader = reader;
        assertArrayEquals(new String[] { "delete from item" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    private SqlDataReader open(String sql) {
        SqlDataReader reader = new SqlDataReader(new StringReader(sql));
        reader.open(new DataContext());
        openedReader = reader;
        return reader;
    }
}
