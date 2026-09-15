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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CsvDquoteDataReaderTest {
    private CsvDquoteDataReader openedReader;

    @AfterEach
    void tearDown() {
        if (openedReader != null) {
            openedReader.close();
        }
    }

    @Test
    void testNextRelation_readsColumnNamesFromHeader() {
        CsvDquoteDataReader reader = open("id,name\n1,widget\n");
        assertArrayEquals(new String[] { "id", "name" }, reader.nextRelation().getColumnNames());
    }

    @Test
    void testNextData_readsPlainRow() {
        CsvDquoteDataReader reader = open("id,name\n1,widget\n");
        reader.nextRelation();
        assertArrayEquals(new String[] { "1", "widget" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_treatsDoubledQuoteAsEscapedQuote() {
        CsvDquoteDataReader reader = open("id,name\n1,\"say \"\"hi\"\"\"\n");
        reader.nextRelation();
        assertArrayEquals(new String[] { "1", "say \"hi\"" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_keepsBackslashLiteral() {
        CsvDquoteDataReader reader = open("id,path\n1,\"c:\\temp\"\n");
        reader.nextRelation();
        assertArrayEquals(new String[] { "1", "c:\\temp" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_readsQuotedValueContainingComma() {
        CsvDquoteDataReader reader = open("id,name\n1,\"widget, large\"\n");
        reader.nextRelation();
        assertArrayEquals(new String[] { "1", "widget, large" }, reader.nextData().getParsedData(CsvData.ROW_DATA));
    }

    @Test
    void testNextData_returnsNullWhenExhausted() {
        CsvDquoteDataReader reader = open("id,name\n");
        reader.nextRelation();
        assertNull(reader.nextData());
    }

    private CsvDquoteDataReader open(String csv) {
        CsvDquoteDataReader reader = new CsvDquoteDataReader(BinaryEncoding.BASE64, "cat", "sch", "item", new StringReader(csv));
        reader.open(new DataContext());
        openedReader = reader;
        return reader;
    }
}
