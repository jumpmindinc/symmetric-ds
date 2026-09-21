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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.exception.IoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcSqlReadCursorTest {
    private JdbcSqlTemplate sqlTemplateMock;
    private DataSource dataSourceMock;
    private Connection connectionMock;
    private SqlTemplateSettings settings;

    @BeforeEach
    void setUp() throws SQLException {
        sqlTemplateMock = mock(JdbcSqlTemplate.class);
        dataSourceMock = mock(DataSource.class);
        connectionMock = mock(Connection.class);
        settings = new SqlTemplateSettings();
        when(sqlTemplateMock.getDataSource()).thenReturn(dataSourceMock);
        when(dataSourceMock.getConnection()).thenReturn(connectionMock);
        when(sqlTemplateMock.getSettings()).thenReturn(settings);
        when(sqlTemplateMock.getIsolationLevel()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connectionMock.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    void testConstructor_noArg_startsWithNoResultSet() {
        JdbcSqlReadCursor<Object> cursor = new JdbcSqlReadCursor<>();
        assertNull(cursor.rs);
        assertNull(cursor.c);
    }

    @Test
    void testConstructor_withoutConnectionHandler_executesQuery() throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null);
        assertSame(resultSetMock, cursor.rs);
        cursor.close();
    }

    @Test
    void testConstructor_withValues_usesPreparedStatementAndSetsValues() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        Object[] values = { 1 };
        int[] types = { Types.INTEGER };
        when(connectionMock.prepareStatement(eq("select ?"), anyInt(), anyInt())).thenReturn(preparedStatementMock);
        when(preparedStatementMock.executeQuery()).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select ?", values, types, null, false);
        verify(sqlTemplateMock).setValues(preparedStatementMock, values, types, sqlTemplateMock.getLobHandler());
        assertSame(resultSetMock, cursor.rs);
        cursor.close();
    }

    @Test
    void testConstructor_withoutValues_usesStatementAndExecutesSql() throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        verify(connectionMock, never()).prepareStatement(anyString(), anyInt(), anyInt());
        assertSame(resultSetMock, cursor.rs);
        cursor.close();
    }

    @Test
    void testConstructor_withConnectionHandler_callsBeforeOnConnection() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, handlerMock, false);
        verify(handlerMock).before(connectionMock);
        cursor.close();
    }

    @Test
    void testConstructor_whenIsolationLevelDiffers_setsIsolationLevel() throws SQLException {
        when(connectionMock.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_UNCOMMITTED);
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        verify(connectionMock).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        cursor.close();
    }

    @Test
    void testConstructor_whenIsolationLevelMatches_doesNotSetIsolationLevel() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        verify(connectionMock, never()).setTransactionIsolation(anyInt());
        cursor.close();
    }

    @Test
    void testConstructor_whenRequiresAutoCommitFalse_setsAutoCommitFalse() throws SQLException {
        when(sqlTemplateMock.isRequiresAutoCommitFalseToSetFetchSize()).thenReturn(true);
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        verify(connectionMock).setAutoCommit(false);
        cursor.close();
    }

    @Test
    void testConstructor_whenNotRequiresAutoCommitFalse_doesNotChangeAutoCommit() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        verify(connectionMock, never()).setAutoCommit(false);
        cursor.close();
    }

    @Test
    void testConstructor_whenSqliteEmptyResultException_swallowsException() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenThrow(new SQLException("query does not return results"));
        JdbcSqlReadCursor<Row> cursor = assertDoesNotThrow(
                () -> new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false));
        assertNull(cursor.rs);
    }

    @Test
    void testConstructor_whenOtherSQLExceptionDuringExecute_translatesAndThrowsAndClosesResources() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenThrow(new SQLException("syntax error"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(anyString(), any(Throwable.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class,
                () -> new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false));
        assertSame(translated, thrown);
        verify(statementMock).close();
        verify(connectionMock).close();
    }

    @Test
    void testConstructor_whenGetConnectionThrows_translatesAndThrows() throws SQLException {
        when(dataSourceMock.getConnection()).thenThrow(new SQLException("no connection"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(anyString(), any(Throwable.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class,
                () -> new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false));
        assertSame(translated, thrown);
    }

    @Test
    void testConstructor_whenNonSQLExceptionOccurs_translatesAndThrows() {
        when(sqlTemplateMock.getSettings()).thenThrow(new IoException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(anyString(), any(Throwable.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class,
                () -> new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false));
        assertSame(translated, thrown);
    }

    @Test
    void testNext_returnsMappedRowsInOrder() throws SQLException {
        JdbcSqlReadCursor<String> cursor = createCursorWithRows("a", "b");
        assertEquals("a", cursor.next());
        assertEquals("b", cursor.next());
        cursor.close();
    }

    @Test
    void testNext_skipsRowsWhereMapperReturnsNull() throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        when(resultSetMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(1);
        when(metaDataMock.getColumnLabel(1)).thenReturn("value");
        when(metaDataMock.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("VARCHAR");
        when(resultSetMock.next()).thenReturn(true, true, true, false);
        when(resultSetMock.getObject(1)).thenReturn("skip", "a", "skip");
        JdbcSqlReadCursor<String> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock,
                row -> "skip".equals(row.getString("value")) ? null : row.getString("value"),
                "select 1", null, null, null, false);
        assertEquals("a", cursor.next());
        assertNull(cursor.next());
        cursor.close();
    }

    @Test
    void testNext_returnsNullAndClosesResultSetWhenExhausted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        when(resultSetMock.next()).thenReturn(false);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        assertNull(cursor.next());
        verify(resultSetMock).close();
        cursor.close();
    }

    @Test
    void testNext_whenSQLExceptionOccurs_translatesAndThrows() throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        when(resultSetMock.next()).thenThrow(new SQLException("read failed"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(Throwable.class))).thenReturn(translated);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        SqlException thrown = assertThrows(SqlException.class, cursor::next);
        assertSame(translated, thrown);
    }

    @Test
    void testClose_withConnectionHandler_callsAfterAndReleasesResources() throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, handlerMock, false);
        cursor.close();
        verify(handlerMock).after(connectionMock);
        verify(resultSetMock).close();
        verify(statementMock).close();
        verify(connectionMock).close();
    }

    @Test
    void testClose_withoutConnectionHandler_doesNotThrow() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        JdbcSqlReadCursor<Row> cursor = new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row, "select 1", null, null, null, false);
        assertDoesNotThrow(cursor::close);
    }

    private JdbcSqlReadCursor<String> createCursorWithRows(String first, String second) throws SQLException {
        Statement statementMock = mock(Statement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(statementMock);
        when(statementMock.executeQuery("select 1")).thenReturn(resultSetMock);
        when(resultSetMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(1);
        when(metaDataMock.getColumnLabel(1)).thenReturn("value");
        when(metaDataMock.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("VARCHAR");
        when(resultSetMock.next()).thenReturn(true, true, false);
        when(resultSetMock.getObject(1)).thenReturn(first, second);
        return new JdbcSqlReadCursor<>(sqlTemplateMock, row -> row.getString("value"), "select 1", null, null, null, false);
    }
}
