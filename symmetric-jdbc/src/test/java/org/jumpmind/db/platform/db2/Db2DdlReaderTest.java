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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Db2DdlReaderTest {
    private static final String SQL_WITH_SCHEMA = "SELECT NAME, IDENTITY, GENERATED FROM SYSIBM.SYSCOLUMNS WHERE TBNAME=? AND TBCREATOR=?";
    private static final String SQL_WITHOUT_SCHEMA = "SELECT NAME, IDENTITY, GENERATED FROM SYSIBM.SYSCOLUMNS WHERE TBNAME=?";
    private static final String TABLE_NAME = "TEST_TABLE";
    private static final String SCHEMA_NAME = "DB2INST1";
    private static final String COLUMN_NAME = "ID";
    private Db2DatabasePlatform platform;
    private Db2DdlReader ddlReader;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private DatabaseMetaDataWrapper metaData;

    @BeforeEach
    void setup() throws SQLException {
        platform = mock(Db2DatabasePlatform.class);
        ddlReader = new Db2DdlReader(platform);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        metaData = new DatabaseMetaDataWrapper();
        metaData.setSchemaPattern(SCHEMA_NAME);
    }

    @Test
    void testEnhanceTableMetaData_identityColumn() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "Y", "D");
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertTrue(column.isAutoIncrement());
        assertFalse(column.isGenerated());
        verify(connection).prepareStatement(SQL_WITH_SCHEMA);
        verify(statement).setString(1, TABLE_NAME);
        verify(statement).setString(2, SCHEMA_NAME);
    }

    @Test
    void testEnhanceTableMetaData_nonIdentityColumn() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "N", "");
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_nullIdentityValue() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, null, "");
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_nullGeneratedValue() throws Exception {
        Column column = new Column(COLUMN_NAME);
        column.setGenerated(true);
        stubRow(COLUMN_NAME, "Y", null);
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertTrue(column.isAutoIncrement());
        assertTrue(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_identitySetGeneratedAlways() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "Y", "A");
        column.setGenerated(true);
        column.setPrimaryKey(true);
        ddlReader.enhanceTableMetaData(connection, metaData, mockTable(column));
        assertTrue(column.isAutoIncrement());
        assertTrue(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_columnNotOnTable() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow("MISSING_COLUMN", "Y", "D");
        Table table = mock(Table.class);
        when(table.getColumnWithName("MISSING_COLUMN")).thenReturn(null);
        ddlReader.enhanceTableMetaData(connection, metaData, table);
        assertFalse(column.isAutoIncrement());
        assertFalse(column.isGenerated());
    }

    @Test
    void testEnhanceTableMetaData_noSchemaPattern() throws Exception {
        Column column = new Column(COLUMN_NAME);
        stubRow(COLUMN_NAME, "Y", "D");
        ddlReader.enhanceTableMetaData(connection, new DatabaseMetaDataWrapper(), mockTable(column));
        assertTrue(column.isAutoIncrement());
        verify(connection).prepareStatement(SQL_WITHOUT_SCHEMA);
        verify(statement).setString(1, TABLE_NAME);
        verify(statement, never()).setString(2, SCHEMA_NAME);
    }

    private void stubRow(String columnName, String identity, String generated) throws SQLException {
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString(1)).thenReturn(columnName);
        when(resultSet.getString(2)).thenReturn(identity);
        when(resultSet.getString(3)).thenReturn(generated);
    }

    private Table mockTable(Column column) {
        Table table = mock(Table.class);
        when(table.getName()).thenReturn(TABLE_NAME);
        when(table.getColumnWithName(column.getName())).thenReturn(column);
        return table;
    }
}
