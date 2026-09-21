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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;

class JdbcSqlTransactionTest {
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
        sqlTemplateMock.logSqlBuilder = new LogSqlBuilder();
        when(sqlTemplateMock.getDataSource()).thenReturn(dataSourceMock);
        when(dataSourceMock.getConnection()).thenReturn(connectionMock);
        when(sqlTemplateMock.getSettings()).thenReturn(settings);
    }

    @Test
    void testConstructor_singleArg_defaultsToManualCommit() throws SQLException {
        new JdbcSqlTransaction(sqlTemplateMock);
        verify(connectionMock).setAutoCommit(false);
    }

    @Test
    void testConstructor_withAutoCommitTrue_setsAutoCommitTrue() throws SQLException {
        new JdbcSqlTransaction(sqlTemplateMock, true);
        verify(connectionMock).setAutoCommit(true);
    }

    @Test
    void testConstructor_readsBatchSizeFromSettings() {
        settings.setBatchSize(50);
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        assertEquals(50, tx.getBatchSize());
    }

    @Test
    void testAddSqlTransactionListener_notifiedOnCommit() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        ISqlTransactionListener listenerMock = mock(ISqlTransactionListener.class);
        tx.addSqlTransactionListener(listenerMock);
        tx.commit();
        verify(listenerMock).transactionCommitted();
    }

    @Test
    void testInit_whenConnectionAlreadyOpen_closesPreviousConnection() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.init();
        verify(connectionMock).close();
        verify(dataSourceMock, times(2)).getConnection();
    }

    @Test
    void testInit_whenGetConnectionThrows_translatesAndThrows() throws SQLException {
        when(dataSourceMock.getConnection()).thenThrow(new SQLException("no connection"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> new JdbcSqlTransaction(sqlTemplateMock));
        assertSame(translated, thrown);
    }

    @Test
    void testSetInBatchMode_withOpenConnection_setsFlag() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.setInBatchMode(true);
        assertTrue(tx.isInBatchMode());
    }

    @Test
    void testSetInBatchMode_withoutOpenConnection_doesNothing() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.close();
        tx.setInBatchMode(true);
        assertFalse(tx.isInBatchMode());
    }

    @Test
    void testIsInBatchMode_defaultsFalse() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        assertFalse(tx.isInBatchMode());
    }

    @Test
    void testCommit_withoutAutoCommit_commitsAndNotifiesListeners() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        ISqlTransactionListener listenerMock = mock(ISqlTransactionListener.class);
        tx.addSqlTransactionListener(listenerMock);
        tx.commit();
        verify(connectionMock).commit();
        verify(listenerMock).transactionCommitted();
    }

    @Test
    void testCommit_withAutoCommit_doesNotCallConnectionCommit() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock, true);
        tx.commit();
        verify(connectionMock, never()).commit();
    }

    @Test
    void testCommit_withPendingBatchInBatchMode_flushesBeforeCommitting() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeBatch()).thenReturn(new int[] { 1 });
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        tx.commit();
        verify(pstmtMock).executeBatch();
    }

    @Test
    void testCommit_whenSQLExceptionOccurs_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        doThrow(new SQLException("boom")).when(connectionMock).commit();
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, tx::commit);
        assertSame(translated, thrown);
    }

    @Test
    void testCommit_withoutOpenConnection_doesNothing() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.close();
        assertDoesNotThrow(tx::commit);
    }

    @Test
    void testRollback_noArg_clearsMarkersNotifiesListenersAndReinitializes() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        ISqlTransactionListener listenerMock = mock(ISqlTransactionListener.class);
        tx.addSqlTransactionListener(listenerMock);
        tx.rollback();
        verify(connectionMock).rollback();
        verify(listenerMock).transactionRolledBack();
        assertTrue(tx.getUnflushedMarkers(false).isEmpty());
        verify(dataSourceMock, times(2)).getConnection();
    }

    @Test
    void testRollback_withClearMarkersFalse_keepsMarkers() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        tx.rollback(false);
        verify(connectionMock).rollback();
        assertFalse(tx.getUnflushedMarkers(false).isEmpty());
    }

    @Test
    void testRollback_withAutoCommit_doesNotCallConnectionRollback() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock, true);
        tx.rollback(true);
        verify(connectionMock, never()).rollback();
    }

    @Test
    void testRollback_whenSQLExceptionOccurs_doesNotThrow() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        doThrow(new SQLException("boom")).when(connectionMock).rollback();
        assertDoesNotThrow(() -> tx.rollback(true));
    }

    @Test
    void testRollback_withoutOpenConnection_doesNothing() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.close();
        assertDoesNotThrow(() -> tx.rollback(true));
    }

    @Test
    void testClose_releasesConnectionAndRestoresAutoCommit() throws SQLException {
        when(connectionMock.getAutoCommit()).thenReturn(true);
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.close();
        verify(connectionMock).setAutoCommit(true);
        verify(connectionMock).close();
        assertNull(tx.getConnection());
    }

    @Test
    void testClose_whenAlreadyClosed_doesNothing() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.close();
        assertDoesNotThrow(tx::close);
    }

    @Test
    void testClose_withOpenPreparedStatement_closesIt() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.close();
        verify(pstmtMock).close();
    }

    @Test
    void testClose_whenSetAutoCommitThrows_stillClosesConnection() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        doThrow(new SQLException("boom")).when(connectionMock).setAutoCommit(anyBoolean());
        tx.close();
        verify(connectionMock).close();
    }

    @Test
    void testFlush_withMarkersAndPstmt_executesBatchAndClearsMarkers() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeBatch()).thenReturn(new int[] { 1 });
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        int rowsUpdated = tx.flush();
        assertEquals(1, rowsUpdated);
        assertTrue(tx.getUnflushedMarkers(false).isEmpty());
    }

    @Test
    void testFlush_withoutMarkersOrPstmt_returnsZero() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        assertEquals(0, tx.flush());
    }

    @Test
    void testFlush_withSuccessNoInfo_normalizesToOne() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeBatch()).thenReturn(new int[] { Statement.SUCCESS_NO_INFO });
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(1, tx.flush());
    }

    @Test
    void testFlush_whenBatchUpdateException_removesSuccessfulMarkersAndTranslates() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.setBatchSize(10);
        tx.addRow("marker1", new Object[] { "a" }, new int[] { Types.VARCHAR });
        tx.addRow("marker2", new Object[] { "b" }, new int[] { Types.VARCHAR });
        BatchUpdateException batchEx = new BatchUpdateException(new int[] { 1, Statement.EXECUTE_FAILED });
        when(pstmtMock.executeBatch()).thenThrow(batchEx);
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(batchEx)).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, tx::flush);
        assertSame(translated, thrown);
        assertEquals(List.of("marker2"), tx.getUnflushedMarkers(false));
    }

    @Test
    void testFlush_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        when(pstmtMock.executeBatch()).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, tx::flush);
        assertSame(translated, thrown);
    }

    @Test
    void testQueryForRow_returnsFirstRow() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        ResultSet resultSetMock = mockSingleColumnResultSet("value", "a", "b");
        when(pstmtMock.executeQuery()).thenReturn(resultSetMock);
        Row row = tx.queryForRow("select value from foo");
        assertEquals("a", row.getString("value"));
    }

    @Test
    void testQueryForRow_returnsNullWhenEmpty() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        ResultSet resultSetMock = mockSingleColumnResultSet("value");
        when(pstmtMock.executeQuery()).thenReturn(resultSetMock);
        assertNull(tx.queryForRow("select value from foo"));
    }

    @Test
    void testQueryForInt_returnsValue() {
        JdbcSqlTransaction tx = spyQueryForObject(5);
        assertEquals(5, tx.queryForInt("select 1"));
    }

    @Test
    void testQueryForInt_returnsMinValueWhenNull() {
        JdbcSqlTransaction tx = spyQueryForObject(null);
        assertEquals(Integer.MIN_VALUE, tx.queryForInt("select 1"));
    }

    @Test
    void testQueryForLong_returnsValue() {
        JdbcSqlTransaction tx = spyQueryForObject(5L);
        assertEquals(5L, tx.queryForLong("select 1"));
    }

    @Test
    void testQueryForLong_returnsMinValueWhenNull() {
        JdbcSqlTransaction tx = spyQueryForObject(null);
        assertEquals(Long.MIN_VALUE, tx.queryForLong("select 1"));
    }

    @Test
    void testQueryForObject_withArgs_usesPreparedStatement() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select ?")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(sqlTemplateMock.getObjectFromResultSet(rsMock, Integer.class)).thenReturn(5);
        Integer result = tx.queryForObject("select ?", Integer.class, 1);
        assertEquals(5, result);
        verify(sqlTemplateMock).setValues(pstmtMock, new Object[] { 1 });
    }

    @Test
    void testQueryForObject_withoutArgs_usesStatement() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        Statement stmtMock = mock(Statement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.executeQuery("select 1")).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        Integer result = tx.queryForObject("select 1", Integer.class);
        assertNull(result);
        verify(connectionMock, never()).prepareStatement(anyString());
    }

    @Test
    void testQueryForObject_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.executeQuery("select 1")).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> tx.queryForObject("select 1", Integer.class));
        assertSame(translated, thrown);
    }

    @Test
    void testQuery_withNamedParams_substitutesAndReturnsMappedRows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        ResultSet resultSetMock = mockSingleColumnResultSet("value", "a");
        when(pstmtMock.executeQuery()).thenReturn(resultSetMock);
        Map<String, Object> params = new HashMap<>();
        params.put("x", 5);
        List<String> result = tx.query("select value from foo where id = :x", row -> row.getString("value"), params);
        assertEquals(List.of("a"), result);
    }

    @Test
    void testQuery_withArgsAndTypes_returnsMappedRows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("select value from foo")).thenReturn(pstmtMock);
        ResultSet resultSetMock = mockSingleColumnResultSet("value", "a", "b");
        when(pstmtMock.executeQuery()).thenReturn(resultSetMock);
        List<String> result = tx.query("select value from foo", row -> row.getString("value"), null, null);
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void testQuery_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("select value from foo")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> tx.query("select value from foo", row -> row, null, null));
        assertSame(translated, thrown);
    }

    @Test
    void testExecute_withoutResults_returnsUpdateCount() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("update foo")).thenReturn(false);
        when(stmtMock.getUpdateCount()).thenReturn(3);
        assertEquals(3, tx.execute("update foo"));
    }

    @Test
    void testExecute_withResults_drainsResultSetAndReturnsUpdateCount() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        Statement stmtMock = mock(Statement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("select 1")).thenReturn(true);
        when(stmtMock.getResultSet()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true, false);
        when(stmtMock.getUpdateCount()).thenReturn(-1);
        assertEquals(-1, tx.execute("select 1"));
        verify(rsMock, times(2)).next();
    }

    @Test
    void testExecute_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("update foo")).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> tx.execute("update foo"));
        assertSame(translated, thrown);
    }

    @Test
    void testPrepareAndExecute_withArgsAndTypes_delegatesToExecutePreparedUpdate() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update foo set bar = ?")).thenReturn(pstmtMock);
        when(pstmtMock.executeUpdate()).thenReturn(2);
        int result = tx.prepareAndExecute("update foo set bar = ?", new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(2, result);
        verify(sqlTemplateMock).setValues(pstmtMock, new Object[] { "value" }, new int[] { Types.VARCHAR }, sqlTemplateMock.getLobHandler());
    }

    @Test
    void testPrepareAndExecute_withNamedParamsMap_delegatesToSpringTemplate() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeUpdate()).thenReturn(4);
        Map<String, Object> params = new HashMap<>();
        params.put("bar", "value");
        int result = tx.prepareAndExecute("update foo set bar = :bar", params);
        assertEquals(4, result);
    }

    @Test
    void testPrepareAndExecute_withVarargs_setsValuesWhenPresent() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update foo set bar = ?")).thenReturn(pstmtMock);
        when(pstmtMock.execute()).thenReturn(false);
        when(pstmtMock.getUpdateCount()).thenReturn(1);
        int result = tx.prepareAndExecute("update foo set bar = ?", "value");
        assertEquals(1, result);
        verify(sqlTemplateMock).setValues(pstmtMock, new Object[] { "value" });
    }

    @Test
    void testPrepareAndExecute_withVarargs_withResults_drainsResultSetAndReturnsUpdateCount() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select 1")).thenReturn(pstmtMock);
        when(pstmtMock.execute()).thenReturn(true);
        when(pstmtMock.getResultSet()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true, false);
        when(pstmtMock.getUpdateCount()).thenReturn(-1);
        int result = tx.prepareAndExecute("select 1");
        assertEquals(-1, result);
        verify(rsMock, times(2)).next();
    }

    @Test
    void testPrepareAndExecute_withVarargs_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update foo set bar = ?")).thenReturn(pstmtMock);
        when(pstmtMock.execute()).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> tx.prepareAndExecute("update foo set bar = ?", "value"));
        assertSame(translated, thrown);
    }

    @Test
    void testPrepareAndExecute_withNoVarargs_doesNotSetValues() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update foo")).thenReturn(pstmtMock);
        when(pstmtMock.execute()).thenReturn(false);
        when(pstmtMock.getUpdateCount()).thenReturn(1);
        tx.prepareAndExecute("update foo");
        verify(sqlTemplateMock, never()).setValues(any(PreparedStatement.class), any(Object[].class));
    }

    @Test
    void testExecuteCallback_returnsCallbackResult() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        String result = tx.executeCallback(con -> {
            assertSame(connectionMock, con);
            return "value";
        });
        assertEquals("value", result);
    }

    @Test
    void testExecuteCallback_translatesSQLException() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> tx.executeCallback(con -> {
            throw new SQLException("boom");
        }));
        assertSame(translated, thrown);
    }

    @Test
    void testPrepare_withNoPendingBatch_preparesStatement() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update foo set bar = ?")).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        verify(connectionMock).prepareStatement("update foo set bar = ?");
    }

    @Test
    void testPrepare_withPendingBatch_throwsIllegalStateException() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertThrows(IllegalStateException.class, () -> tx.prepare("update foo set baz = ?"));
    }

    @Test
    void testPrepare_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        when(connectionMock.prepareStatement(anyString())).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SqlException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class, () -> tx.prepare("update foo set bar = ?"));
        assertSame(translated, thrown);
    }

    @Test
    void testAddRow_inBatchMode_addsMarkerAndBatchesWithoutExecuting() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        int rowsUpdated = tx.addRow("marker1", new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(0, rowsUpdated);
        verify(pstmtMock).addBatch();
        verify(pstmtMock, never()).executeUpdate();
        assertEquals(List.of("marker1"), tx.getUnflushedMarkers(false));
    }

    @Test
    void testAddRow_inBatchMode_withNullMarker_generatesSequentialMarker() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(List.of(1), tx.getUnflushedMarkers(false));
    }

    @Test
    void testAddRow_inBatchMode_flushesWhenBatchSizeReached() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeBatch()).thenReturn(new int[] { 1 });
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.setBatchSize(1);
        int rowsUpdated = tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(1, rowsUpdated);
        assertTrue(tx.getUnflushedMarkers(false).isEmpty());
    }

    @Test
    void testAddRow_outsideBatchMode_executesImmediately() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeUpdate()).thenReturn(1);
        tx.prepare("update foo set bar = ?");
        int rowsUpdated = tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(1, rowsUpdated);
        verify(pstmtMock, never()).addBatch();
    }

    @Test
    void testAddRow_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        when(pstmtMock.executeUpdate()).thenThrow(new SQLException("boom"));
        tx.prepare("update foo set bar = ?");
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class,
                () -> tx.addRow(null, new Object[] { "value" }, new int[] { Types.VARCHAR }));
        assertSame(translated, thrown);
    }

    @Test
    void testSetBatchSize_getBatchSize_roundTrip() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        tx.setBatchSize(25);
        assertEquals(25, tx.getBatchSize());
    }

    @Test
    void testExecutePreparedUpdate_withoutAllowUpdatesWithResults_usesExecuteUpdate() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(pstmtMock.executeUpdate()).thenReturn(7);
        int result = tx.executePreparedUpdate(pstmtMock, "update foo", null, null);
        assertEquals(7, result);
    }

    @Test
    void testExecutePreparedUpdate_withAllowUpdatesWithResults_usesExecuteAllowingResults() throws SQLException {
        settings.setAllowUpdatesWithResults(true);
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(pstmtMock.execute()).thenReturn(false);
        when(pstmtMock.getUpdateCount()).thenReturn(3, -1);
        int result = tx.executePreparedUpdate(pstmtMock, "update foo", null, null);
        assertEquals(3, result);
    }

    @Test
    void testExecuteAllowingResults_accumulatesUpdateCountsAcrossResults() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(pstmtMock.execute()).thenReturn(false);
        when(pstmtMock.getUpdateCount()).thenReturn(2, 3, -1);
        when(pstmtMock.getMoreResults()).thenReturn(false, false);
        int result = tx.executeAllowingResults(pstmtMock);
        assertEquals(3, result);
    }

    @Test
    void testGetUnflushedMarkers_withClearTrue_clearsMarkers() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow("marker1", new Object[] { "value" }, new int[] { Types.VARCHAR });
        List<Object> markers = tx.getUnflushedMarkers(true);
        assertEquals(List.of("marker1"), markers);
        assertTrue(tx.getUnflushedMarkers(false).isEmpty());
    }

    @Test
    void testGetUnflushedMarkers_withClearFalse_keepsMarkers() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.addRow("marker1", new Object[] { "value" }, new int[] { Types.VARCHAR });
        tx.getUnflushedMarkers(false);
        assertEquals(List.of("marker1"), tx.getUnflushedMarkers(false));
    }

    @Test
    void testGetConnection_returnsUnderlyingConnection() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        assertSame(connectionMock, tx.getConnection());
    }

    @Test
    void testAllowInsertIntoAutoIncrementColumns_doesNotThrow() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        assertDoesNotThrow(() -> tx.allowInsertIntoAutoIncrementColumns(true, null, "\"", ".", "."));
    }

    @Test
    void testInsertWithGeneratedKey_delegatesToTemplate() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        when(sqlTemplateMock.insertWithGeneratedKey(connectionMock, "insert into foo values (?)", "id", "seq",
                new Object[] { "value" }, new int[] { Types.VARCHAR })).thenReturn(42L);
        long key = tx.insertWithGeneratedKey("insert into foo values (?)", "id", "seq",
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(42L, key);
    }

    @Test
    void testInsertWithGeneratedKey_whenSQLException_translatesAndThrows() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        when(sqlTemplateMock.insertWithGeneratedKey(any(Connection.class), anyString(), anyString(), anyString(),
                any(Object[].class), any(int[].class))).thenThrow(new SQLException("boom"));
        SqlException translated = new SqlException("translated");
        when(sqlTemplateMock.translate(any(SQLException.class))).thenReturn(translated);
        SqlException thrown = assertThrows(SqlException.class,
                () -> tx.insertWithGeneratedKey("insert into foo values (?)", "id", "seq", new Object[] { "value" }, new int[] { Types.VARCHAR }));
        assertSame(translated, thrown);
    }

    @Test
    void testGetLogSqlBuilder_setLogSqlBuilder_roundTrip() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        LogSqlBuilder logSqlBuilder = new LogSqlBuilder();
        tx.setLogSqlBuilder(logSqlBuilder);
        assertSame(logSqlBuilder, tx.getLogSqlBuilder());
    }

    @Test
    void testClearBatch_inBatchModeWithPstmt_clearsBatch() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        tx.clearBatch();
        verify(pstmtMock).clearBatch();
    }

    @Test
    void testClearBatch_notInBatchMode_doesNothing() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.clearBatch();
        verify(pstmtMock, never()).clearBatch();
    }

    @Test
    void testClearBatch_whenSQLException_logsAndDoesNotThrow() throws SQLException {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(anyString())).thenReturn(pstmtMock);
        tx.prepare("update foo set bar = ?");
        tx.setInBatchMode(true);
        doThrow(new SQLException("boom")).when(pstmtMock).clearBatch();
        assertDoesNotThrow(tx::clearBatch);
    }

    @Test
    void testIsAllowInsertIntoAutoIncrement_returnsFalse() {
        JdbcSqlTransaction tx = new JdbcSqlTransaction(sqlTemplateMock);
        assertFalse(tx.isAllowInsertIntoAutoIncrement());
    }

    private JdbcSqlTransaction spyQueryForObject(Object valueToReturn) {
        JdbcSqlTemplate localSqlTemplateMock = sqlTemplateMock;
        return new JdbcSqlTransaction(localSqlTemplateMock) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T queryForObject(String sql, Class<T> clazz, Object... args) {
                return (T) valueToReturn;
            }
        };
    }

    private ResultSet mockSingleColumnResultSet(String columnName, Object... values) throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(1);
        when(metaDataMock.getColumnLabel(1)).thenReturn(columnName);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("VARCHAR");
        Boolean[] nextResults = new Boolean[values.length + 1];
        Arrays.fill(nextResults, 0, values.length, Boolean.TRUE);
        nextResults[values.length] = Boolean.FALSE;
        OngoingStubbing<Boolean> nextStub = when(rsMock.next());
        for (boolean hasNext : nextResults) {
            nextStub = nextStub.thenReturn(hasNext);
        }
        if (values.length > 0) {
            OngoingStubbing<Object> objectStub = when(rsMock.getObject(1));
            for (Object value : values) {
                objectStub = objectStub.thenReturn(value);
            }
        }
        return rsMock;
    }
}
