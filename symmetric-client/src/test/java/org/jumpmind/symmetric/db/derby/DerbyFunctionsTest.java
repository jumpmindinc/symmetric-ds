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
package org.jumpmind.symmetric.db.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DerbyFunctionsTest {
    private ResultSet resultSet;
    private ResultSetMetaData metaData;

    @BeforeEach
    void setUp() throws SQLException {
        resultSet = mock(ResultSet.class);
        metaData = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
    }

    @Test
    void testEscape_withPlainString() {
        assertEquals("\"abc\"", DerbyFunctions.escape("abc"));
    }

    @Test
    void testEscape_withNull() {
        assertEquals("", DerbyFunctions.escape(null));
    }

    @Test
    void testEscape_withEmptyString() {
        assertEquals("\"\"", DerbyFunctions.escape(""));
    }

    @Test
    void testEscape_withBackslash() {
        assertEquals("\"a\\\\b\"", DerbyFunctions.escape("a\\b"));
    }

    @Test
    void testEscape_withDoubleQuote() {
        assertEquals("\"say \\\"hi\\\"\"", DerbyFunctions.escape("say \"hi\""));
    }

    @Test
    void testEscape_withBackslashBeforeQuote() {
        assertEquals("\"\\\\\\\"\"", DerbyFunctions.escape("\\\""));
    }

    @Test
    void testFindColumnIndex_withFirstColumn() throws SQLException {
        givenColumns("ID", "NAME");
        assertEquals(1, DerbyFunctions.findColumnIndex(metaData, "ID"));
    }

    @Test
    void testFindColumnIndex_withLastColumn() throws SQLException {
        givenColumns("ID", "NAME");
        assertEquals(2, DerbyFunctions.findColumnIndex(metaData, "NAME"));
    }

    @Test
    void testFindColumnIndex_withUnknownColumn() throws SQLException {
        givenColumns("ID", "NAME");
        assertEquals(-1, DerbyFunctions.findColumnIndex(metaData, "MISSING"));
    }

    @Test
    void testFindColumnIndex_withNoColumns() throws SQLException {
        when(metaData.getColumnCount()).thenReturn(0);
        assertEquals(-1, DerbyFunctions.findColumnIndex(metaData, "ID"));
    }

    @Test
    void testGetPrimaryKeyWhereString_withNumericColumn() throws SQLException {
        givenColumns("ID");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(resultSet.getObject(1)).thenReturn(42);
        assertEquals("\"ID\"=42", DerbyFunctions.getPrimaryKeyWhereString(new String[] { "ID" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_withStringColumn() throws SQLException {
        givenColumns("NAME");
        when(metaData.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(resultSet.getString(1)).thenReturn("Bob");
        assertEquals("\"NAME\"='Bob'", DerbyFunctions.getPrimaryKeyWhereString(new String[] { "NAME" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_escapesQuoteInStringColumn() throws SQLException {
        givenColumns("NAME");
        when(metaData.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(resultSet.getString(1)).thenReturn("O'Brien");
        assertEquals("\"NAME\"='O''Brien'", DerbyFunctions.getPrimaryKeyWhereString(new String[] { "NAME" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_withDateColumn() throws SQLException {
        givenColumns("CREATED");
        when(metaData.getColumnType(1)).thenReturn(Types.DATE);
        when(resultSet.getString(1)).thenReturn("2020-01-01");
        assertEquals("\"CREATED\"={d '2020-01-01'}", DerbyFunctions.getPrimaryKeyWhereString(new String[] { "CREATED" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_withTimestampColumn() throws SQLException {
        givenColumns("CREATED");
        when(metaData.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(resultSet.getString(1)).thenReturn("2020-01-01 10:00:00");
        assertEquals("\"CREATED\"={ts '2020-01-01 10:00:00'}",
                DerbyFunctions.getPrimaryKeyWhereString(new String[] { "CREATED" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_withMultipleColumns() throws SQLException {
        givenColumns("ID", "NAME");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaData.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(resultSet.getObject(1)).thenReturn(42);
        when(resultSet.getString(2)).thenReturn("Bob");
        assertEquals("\"ID\"=42 and \"NAME\"='Bob'",
                DerbyFunctions.getPrimaryKeyWhereString(new String[] { "ID", "NAME" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_skipsBinaryColumn() throws SQLException {
        givenColumns("ID", "PAYLOAD");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaData.getColumnType(2)).thenReturn(Types.VARBINARY);
        when(resultSet.getObject(1)).thenReturn(42);
        assertEquals("\"ID\"=42", DerbyFunctions.getPrimaryKeyWhereString(new String[] { "ID", "PAYLOAD" }, resultSet));
    }

    @Test
    void testGetPrimaryKeyWhereString_stripsQuotesFromColumnName() throws SQLException {
        givenColumns("ID");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(resultSet.getObject(1)).thenReturn(42);
        assertEquals("\"ID\"=42", DerbyFunctions.getPrimaryKeyWhereString(new String[] { "\"ID\"" }, resultSet));
    }

    // Defect pinned, not endorsed: an all-binary primary key leaves the builder empty and the
    // unconditional trailing-" and " trim underflows instead of returning an empty where clause.
    @Test
    void testGetPrimaryKeyWhereString_withOnlyBinaryColumns() throws SQLException {
        givenColumns("PAYLOAD");
        when(metaData.getColumnType(1)).thenReturn(Types.BINARY);
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> DerbyFunctions.getPrimaryKeyWhereString(new String[] { "PAYLOAD" }, resultSet));
    }

    @Test
    void testAppendCsvString_withSimpleColumns() throws SQLException {
        givenColumns("ID", "NAME");
        when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaData.getColumnType(2)).thenReturn(Types.VARCHAR);
        when(resultSet.getString(1)).thenReturn("42");
        when(resultSet.getString(2)).thenReturn("Bob");
        StringBuilder builder = new StringBuilder();
        DerbyFunctions.appendCsvString("ITEM", new String[] { "ID", "NAME" }, new String[] { "ID" }, resultSet, builder);
        assertEquals("\"42\",\"Bob\",", builder.toString());
    }

    @Test
    void testAppendCsvString_withNullValue() throws SQLException {
        givenColumns("NAME");
        when(metaData.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(resultSet.getString(1)).thenReturn(null);
        StringBuilder builder = new StringBuilder();
        DerbyFunctions.appendCsvString("ITEM", new String[] { "NAME" }, new String[] { "NAME" }, resultSet, builder);
        assertEquals(",", builder.toString());
    }

    @Test
    void testAppendCsvString_withBlankColumnName() throws SQLException {
        givenColumns("NAME");
        when(metaData.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(resultSet.getString(1)).thenReturn("Bob");
        StringBuilder builder = new StringBuilder();
        DerbyFunctions.appendCsvString("ITEM", new String[] { "", "NAME" }, new String[] { "NAME" }, resultSet, builder);
        assertEquals(",\"Bob\",", builder.toString());
    }

    @Test
    void testAppendCsvString_withUnknownColumnName() throws SQLException {
        givenColumns("NAME");
        StringBuilder builder = new StringBuilder();
        DerbyFunctions.appendCsvString("ITEM", new String[] { "MISSING" }, new String[] { "NAME" }, resultSet, builder);
        assertEquals(",", builder.toString());
    }

    @Test
    void testAppendCsvString_withNoColumns() throws SQLException {
        StringBuilder builder = new StringBuilder();
        DerbyFunctions.appendCsvString("ITEM", new String[0], new String[0], resultSet, builder);
        assertEquals("", builder.toString());
    }

    @Test
    void testBlobToString_withBlankWhereClause() throws SQLException {
        assertEquals("", DerbyFunctions.blobToString("PAYLOAD", "ITEM", "  "));
    }

    @Test
    void testBlobToString_withNullWhereClause() throws SQLException {
        assertEquals("", DerbyFunctions.blobToString("PAYLOAD", "ITEM", null));
    }

    @Test
    void testClobToString_withBlankWhereClause() throws SQLException {
        assertEquals("", DerbyFunctions.clobToString("NOTES", "ITEM", "  "));
    }

    @Test
    void testClobToString_withNullWhereClause() throws SQLException {
        assertEquals("", DerbyFunctions.clobToString("NOTES", "ITEM", null));
    }

    private void givenColumns(String... columnNames) throws SQLException {
        when(metaData.getColumnCount()).thenReturn(columnNames.length);
        for (int i = 0; i < columnNames.length; i++) {
            when(metaData.getColumnName(i + 1)).thenReturn(columnNames[i]);
        }
    }
}
