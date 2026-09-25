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
package org.jumpmind.symmetric.io.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.csv.CsvReader;
import org.jumpmind.symmetric.csv.CsvWriter;
import org.junit.jupiter.api.Test;

class CsvUtilsTest {
    @Test
    void testLastElementIsNull() {
        String[] tokens = CsvUtils.tokenizeCsvData(
                "\"01493931\",\"0\",\"01493931\",,\"UktNQzIxMAD/////AAAABXV1aWQAAAAAEG3jUZmt5UvpiUdFsbLEkJT/////AAAAA2l2AAAAABClDAHak0h0ENSr3PGH8qIU/////wAAAAVjc3VtAAAAACBjkvrLppvDkY1EbaURqm2kpvmcg/j9eUxztrCe4JHXpH0suPSRvgP6LtpJMaH1HZc=\",\"Trevor Lewis\",\"Lewis\",\"Trevorr\",,\"02\",,\"1\",\"16\",,\"0\",,\"0\",\"30683\",\"0\",\"2010-05-18 15:43:21\",\"0\",\"1987-09-24 00:00:00\",\"0\",,\"PS\",\"2009-11-14 21:49:35\",\"2009-11-14 21:49:35\",\"0023\",,\"1\",");
        assertNull(tokens[tokens.length - 1]);
    }

    @Test
    void testEscapeDoubledSingleQuote() {
        assertEquals("'L\\'' Hospitalet',,'277000043'\n", CsvUtils.escapeCsvData(new String[] { "L\\' Hospitalet", null, "277000043" }, '\n', '\'',
                CsvWriter.ESCAPE_MODE_DOUBLED));
    }

    @Test
    void testLineFeedsInCsv() {
        String line = "\"test\",\"line\nfeed\"";
        String[] tokens = CsvUtils.tokenizeCsvData(line);
        assertEquals("test", tokens[0]);
        assertEquals("line\nfeed", tokens[1]);
    }

    @Test
    void testEscapingLineFeedsInCsv() {
        String[] tokens = new String[] { "test", "line\nfeed" };
        String line = CsvUtils.escapeCsvData(tokens);
        String[] newTokens = CsvUtils.tokenizeCsvData(line);
        assertEquals(tokens[0], newTokens[0]);
        assertEquals(tokens[1], newTokens[1]);
    }

    @Test
    void testTokenizeCsvData_withNull() {
        assertNull(CsvUtils.tokenizeCsvData(null));
    }

    @Test
    void testGetCsvReaderDquote() throws IOException {
        try (StringReader reader = new StringReader("\"say \"\"hello\"\"\",\"world\"");
                CsvReader csvReader = CsvUtils.getCsvReaderDquote(reader)) {
            csvReader.readRecord();
            assertEquals("say \"hello\"", csvReader.getValues()[0]);
            assertEquals("world", csvReader.getValues()[1]);
        }
    }

    @Test
    void testEscapeCsvData_withSingleValue() {
        assertEquals("back\\\\slash", CsvUtils.escapeCsvData("back\\slash"));
    }

    @Test
    void testEscapeAndQuoteCsvData() {
        assertEquals("\"has,comma\"", CsvUtils.escapeAndQuoteCsvData("has,comma"));
    }

    @Test
    void testEscapeCsvData_withRecordDelimiterAndTextQualifier() {
        assertEquals("'one','two'\n", CsvUtils.escapeCsvData(new String[] { "one", "two" }, '\n', '\''));
    }

    @Test
    void testEscapeCsvData_withNullString() {
        assertEquals("'one',\\N\n", CsvUtils.escapeCsvData(new String[] { "one", null }, '\n', '\'', CsvWriter.ESCAPE_MODE_BACKSLASH, "\\N"));
    }

    @Test
    void testWrite() {
        StringWriter writer = new StringWriter();
        assertEquals(9, CsvUtils.write(writer, "insert", ", ", "1"));
        assertEquals("insert, 1", writer.toString());
    }

    @Test
    void testWrite_throwsWhenWriterFails() throws IOException {
        Writer writer = failingWriter();
        assertThrows(IoException.class, () -> CsvUtils.write(writer, "data"));
    }

    @Test
    void testWriteSql() {
        StringWriter writer = new StringWriter();
        CsvUtils.writeSql("update item set name = 'x'", writer);
        assertEquals("sql, update item set name = 'x'" + CsvUtils.LINE_SEPARATOR, writer.toString());
    }

    @Test
    void testWriteBsh() {
        StringWriter writer = new StringWriter();
        CsvUtils.writeBsh("print(1);", writer);
        assertEquals("bsh, print(1);" + CsvUtils.LINE_SEPARATOR, writer.toString());
    }

    @Test
    void testWriteLineFeed() {
        StringWriter writer = new StringWriter();
        CsvUtils.writeLineFeed(writer);
        assertEquals(CsvUtils.LINE_SEPARATOR, writer.toString());
    }

    @Test
    void testWriteLineFeed_throwsWhenWriterFails() throws IOException {
        Writer writer = failingWriter();
        assertThrows(IoException.class, () -> CsvUtils.writeLineFeed(writer));
    }

    private Writer failingWriter() throws IOException {
        Writer writer = mock(Writer.class);
        doThrow(new IOException("failed")).when(writer).write(anyString());
        return writer;
    }
}
