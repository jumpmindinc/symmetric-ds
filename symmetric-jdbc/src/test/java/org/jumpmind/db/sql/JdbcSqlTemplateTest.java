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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import oracle.sql.DATE;
import oracle.sql.TIMESTAMP;
import oracle.sql.TIMESTAMPLTZ;
import oracle.sql.TIMESTAMPTZ;

import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.platform.DatabaseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.SqlTypeValue;

class JdbcSqlTemplateTest {
    private DataSource dataSourceMock;
    private SqlTemplateSettings settings;
    private SymmetricLobHandler lobHandler;
    private DatabaseInfo databaseInfo;
    private Connection connectionMock;

    @BeforeEach
    void setUp() throws SQLException {
        dataSourceMock = mock(DataSource.class);
        settings = new SqlTemplateSettings();
        lobHandler = new SymmetricLobHandler();
        databaseInfo = new DatabaseInfo();
        connectionMock = mock(Connection.class);
        when(dataSourceMock.getConnection()).thenReturn(connectionMock);
    }

    @Test
    void testConstructor_withNullSettings_usesDefaultSettings() {
        JdbcSqlTemplate template = new JdbcSqlTemplate(dataSourceMock, null, lobHandler, databaseInfo);
        assertNotNull(template.getSettings());
    }

    @Test
    void testConstructor_withNullLobHandler_usesDefaultLobHandler() {
        JdbcSqlTemplate template = new JdbcSqlTemplate(dataSourceMock, settings, null, databaseInfo);
        assertNotNull(template.getLobHandler());
    }

    @Test
    void testConstructor_withOverrideIsolationLevel_usesOverride() {
        settings.setOverrideIsolationLevel(Connection.TRANSACTION_SERIALIZABLE);
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, template.getIsolationLevel());
    }

    @Test
    void testConstructor_withoutOverrideIsolationLevel_usesDatabaseInfoMinIsolationLevel() {
        databaseInfo.setMinIsolationLevelToPreventPhantomReads(Connection.TRANSACTION_REPEATABLE_READ);
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, template.getIsolationLevel());
    }

    @Test
    void testConstructor_withLogSqlBuilderInSettings_usesProvidedLogSqlBuilder() {
        LogSqlBuilder logSqlBuilder = new LogSqlBuilder();
        settings.setLogSqlBuilder(logSqlBuilder);
        JdbcSqlTemplate template = createTemplate();
        assertSame(logSqlBuilder, template.logSqlBuilder);
    }

    @Test
    void testConstructor_readsIsEmptyStringNulledFromDatabaseInfo() {
        databaseInfo.setEmptyStringNulled(true);
        JdbcSqlTemplate template = createTemplate();
        assertTrue(template.isEmptyStringNulled);
    }

    @Test
    void testGetConnection_delegatesToDataSource() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        assertSame(connectionMock, template.getConnection());
    }

    @Test
    void testGetDataSource_returnsDataSource() {
        assertSame(dataSourceMock, createTemplate().getDataSource());
    }

    @Test
    void testIsRequiresAutoCommitFalseToSetFetchSize_defaultsFalse() {
        assertFalse(createTemplate().isRequiresAutoCommitFalseToSetFetchSize());
    }

    @Test
    void testSetSettings_getSettings_roundTrip() {
        JdbcSqlTemplate template = createTemplate();
        SqlTemplateSettings newSettings = new SqlTemplateSettings();
        template.setSettings(newSettings);
        assertSame(newSettings, template.getSettings());
    }

    @Test
    void testGetLobHandler_setLobHandler_roundTrip() {
        JdbcSqlTemplate template = createTemplate();
        SymmetricLobHandler newHandler = new SymmetricLobHandler();
        template.setLobHandler(newHandler);
        assertSame(newHandler, template.getLobHandler());
    }

    @Test
    void testQueryForCursor_withArgsAndTypes_createsCursor() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(stmtMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(stmtMock.executeQuery("select 1")).thenReturn(resultSetMock);
        ISqlReadCursor<Row> cursor = template.queryForCursor("select 1", row -> row, null, null);
        assertNotNull(cursor);
        cursor.close();
    }

    @Test
    void testQueryForCursor_withConnectionHandler_passesHandlerThrough() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(stmtMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(stmtMock.executeQuery("select 1")).thenReturn(resultSetMock);
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        ISqlReadCursor<Row> cursor = template.queryForCursor("select 1", row -> row, handlerMock, null, null);
        verify(handlerMock).before(connectionMock);
        cursor.close();
    }

    @Test
    void testQueryForCursor_withReturnLobObjects_createsCursor() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement(anyInt(), anyInt())).thenReturn(stmtMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(stmtMock.executeQuery("select 1")).thenReturn(resultSetMock);
        ISqlReadCursor<Row> cursor = template.queryForCursor("select 1", row -> row, true);
        assertNotNull(cursor);
        cursor.close();
    }

    @Test
    void testQueryForCursor_sixArg_createsCursorWithAllParameters() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement(eq("select ?"), anyInt(), anyInt())).thenReturn(pstmtMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(pstmtMock.executeQuery()).thenReturn(resultSetMock);
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        ISqlReadCursor<Row> cursor = template.queryForCursor("select ?", row -> row, handlerMock,
                new Object[] { 1 }, new int[] { Types.INTEGER }, true);
        assertNotNull(cursor);
        verify(handlerMock).before(connectionMock);
        cursor.close();
    }

    @Test
    void testGetIsolationLevel_setIsolationLevel_roundTrip() {
        JdbcSqlTemplate template = createTemplate();
        template.setIsolationLevel(Connection.TRANSACTION_SERIALIZABLE);
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, template.getIsolationLevel());
    }

    @Test
    void testQueryForObject_withRow_returnsMappedValue() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select name")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getString(1)).thenReturn("value");
        assertEquals("value", template.queryForObject("select name", String.class));
    }

    @Test
    void testQueryForObject_withNoRows_returnsNull() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select name")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        assertNull(template.queryForObject("select name", String.class));
    }

    @Test
    void testQueryForObject_whenSQLException_translatesAndClosesResources() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("select name")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenThrow(new SQLException("boom"));
        assertThrows(SqlException.class, () -> template.queryForObject("select name", String.class));
        verify(pstmtMock).close();
        verify(connectionMock).close();
    }

    @SuppressWarnings("deprecation")
    @Test
    void testQueryForBlob_deprecatedOverload_delegatesToPrimaryOverload() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        byte[] expected = { 1, 2, 3 };
        when(rsMock.getBytes(1)).thenReturn(expected);
        assertArrayEquals(expected, template.queryForBlob("select data"));
    }

    @Test
    void testQueryForBlob_withNoRows_returnsNull() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        assertNull(((ISqlTemplate) template).queryForBlob("select data", Types.BLOB, null));
    }

    @Test
    void testQueryForBlob_whenSQLException_translatesAndClosesResources() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenThrow(new SQLException("boom"));
        assertThrows(SqlException.class,
                () -> ((ISqlTemplate) template).queryForBlob("select data", Types.BLOB, null));
        verify(pstmtMock).close();
        verify(connectionMock).close();
    }

    @Test
    void testQueryForBlob_whenNeedsAutoCommitFalseForBlob_setsAutoCommitFalseThenRestoresTrue() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        when(lobHandlerMock.needsAutoCommitFalseForBlob(Types.BLOB, null)).thenReturn(true);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        ((ISqlTemplate) template).queryForBlob("select data", Types.BLOB, null);
        InOrder inOrder = inOrder(connectionMock);
        inOrder.verify(connectionMock).setAutoCommit(false);
        inOrder.verify(connectionMock).setAutoCommit(true);
    }

    @Test
    void testQueryForBlob_whenNeedsAutoCommitFalseForBlobAndSQLException_stillRestoresAutoCommitAndClosesResources()
            throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        when(lobHandlerMock.needsAutoCommitFalseForBlob(Types.BLOB, null)).thenReturn(true);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenThrow(new SQLException("boom"));
        assertThrows(SqlException.class,
                () -> ((ISqlTemplate) template).queryForBlob("select data", Types.BLOB, null));
        verify(connectionMock).setAutoCommit(false);
        verify(connectionMock).setAutoCommit(true);
        verify(pstmtMock).close();
        verify(connectionMock).close();
    }

    @SuppressWarnings("deprecation")
    @Test
    void testQueryForClob_deprecatedOverload_delegatesToPrimaryOverload() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getString(1)).thenReturn("clob value");
        assertEquals("clob value", template.queryForClob("select data"));
    }

    @Test
    void testQueryForClob_withNoRows_returnsNull() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        assertNull(((ISqlTemplate) template).queryForClob("select data", Types.CLOB, null));
    }

    @Test
    void testQueryForClob_whenSQLException_translatesAndClosesResources() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenThrow(new SQLException("boom"));
        assertThrows(SqlException.class,
                () -> ((ISqlTemplate) template).queryForClob("select data", Types.CLOB, null));
        verify(pstmtMock).close();
        verify(connectionMock).close();
    }

    @Test
    void testQueryForMap_withRow_returnsColumnValues() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(connectionMock.prepareStatement("select id, name")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(2);
        when(metaDataMock.getColumnName(1)).thenReturn("ID");
        when(metaDataMock.getColumnName(2)).thenReturn("NAME");
        when(rsMock.getObject(1)).thenReturn(1);
        when(rsMock.getObject(2)).thenReturn("Alice");
        Map<String, Object> result = template.queryForMap("select id, name");
        assertEquals(1, result.get("ID"));
        assertEquals("Alice", result.get("NAME"));
    }

    @Test
    void testQueryForMap_withNoRows_returnsNull() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("select id")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        assertNull(template.queryForMap("select id"));
    }

    @Test
    void testQueryForMap_withArgs_returnsColumnValues() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(connectionMock.prepareStatement("select id from t where id = ?")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(1);
        when(metaDataMock.getColumnName(1)).thenReturn("ID");
        when(rsMock.getObject(1)).thenReturn(5);
        Map<String, Object> result = template.queryForMap("select id from t where id = ?", 5);
        assertEquals(5, result.get("ID"));
    }

    @Test
    void testQueryForMap_withBlobValue_convertsToByteArray() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        Blob blobMock = mock(Blob.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(1);
        when(metaDataMock.getColumnName(1)).thenReturn("DATA");
        when(rsMock.getObject(1)).thenReturn(blobMock);
        byte[] blobBytes = { 9, 8, 7 };
        when(blobMock.getBinaryStream()).thenReturn(new ByteArrayInputStream(blobBytes));
        Map<String, Object> result = template.queryForMap("select data");
        assertArrayEquals(blobBytes, (byte[]) result.get("DATA"));
    }

    @Test
    void testQueryForMap_withClobValue_convertsToByteArray() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        Clob clobMock = mock(Clob.class);
        when(connectionMock.prepareStatement("select data")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnCount()).thenReturn(1);
        when(metaDataMock.getColumnName(1)).thenReturn("DATA");
        when(rsMock.getObject(1)).thenReturn(clobMock);
        when(clobMock.getCharacterStream()).thenReturn(new StringReader("clob text"));
        Map<String, Object> result = template.queryForMap("select data");
        byte[] expected = "clob text".getBytes(Charset.defaultCharset());
        assertArrayEquals(expected, (byte[]) result.get("DATA"));
    }

    @Test
    void testStartSqlTransaction_noArg_createsManualCommitTransaction() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ISqlTransaction tx = template.startSqlTransaction();
        assertNotNull(tx);
        verify(connectionMock).setAutoCommit(false);
        tx.close();
    }

    @Test
    void testStartSqlTransaction_withAutoCommit_createsAutoCommitTransaction() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ISqlTransaction tx = template.startSqlTransaction(true);
        assertNotNull(tx);
        verify(connectionMock).setAutoCommit(true);
        tx.close();
    }

    @Test
    void testGetUpdateCount_singleResult_returnsUpdateCount() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(stmtMock.getUpdateCount()).thenReturn(5, -1);
        when(stmtMock.getMoreResults()).thenReturn(false);
        assertEquals(5, template.getUpdateCount(stmtMock));
    }

    @Test
    void testGetUpdateCount_multipleResults_loopsUntilNoMoreResults() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(stmtMock.getUpdateCount()).thenReturn(5, 3, -1);
        when(stmtMock.getMoreResults()).thenReturn(true, false);
        assertEquals(3, template.getUpdateCount(stmtMock));
    }

    @Test
    void testUpdate_withNullArgs_executesStatementDirectlyAndReturnsUpdateCount() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.getUpdateCount()).thenReturn(3, -1);
        when(stmtMock.getMoreResults()).thenReturn(false);
        int result = template.update("update t set x = 1", null, null);
        assertEquals(3, result);
        verify(stmtMock).execute("update t set x = 1");
        verify(stmtMock).close();
    }

    @Test
    void testUpdate_withArgsAndNoTypes_setsValuesWithoutTypesAndReturnsUpdateCount() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update t set x = ?")).thenReturn(pstmtMock);
        when(pstmtMock.getUpdateCount()).thenReturn(1, -1);
        when(pstmtMock.getMoreResults()).thenReturn(false);
        int result = template.update("update t set x = ?", new Object[] { "value" }, null);
        assertEquals(1, result);
        verify(pstmtMock).execute();
        verify(pstmtMock).close();
    }

    @Test
    void testUpdate_withArgsAndTypes_setsValuesWithTypesAndReturnsUpdateCount() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update t set x = ?")).thenReturn(pstmtMock);
        when(pstmtMock.getUpdateCount()).thenReturn(1, -1);
        when(pstmtMock.getMoreResults()).thenReturn(false);
        int result = template.update("update t set x = ?", new Object[] { 5 }, new int[] { Types.INTEGER });
        assertEquals(1, result);
        verify(pstmtMock).execute();
        verify(pstmtMock).close();
    }

    @Test
    void testUpdate_withNullArgs_whenStatementExecuteThrowsSQLException_translatesAndClosesStatement() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        doThrow(new SQLException("boom")).when(stmtMock).execute("update t set x = 1");
        assertThrows(SqlException.class, () -> template.update("update t set x = 1", null, null));
        verify(stmtMock).close();
        verify(connectionMock).close();
    }

    @Test
    void testUpdate_withArgs_whenPreparedStatementExecuteThrowsSQLException_translatesAndClosesStatement() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("update t set x = ?")).thenReturn(pstmtMock);
        doThrow(new SQLException("boom")).when(pstmtMock).execute();
        assertThrows(SqlException.class,
                () -> template.update("update t set x = ?", new Object[] { "value" }, null));
        verify(pstmtMock).close();
        verify(connectionMock).close();
    }

    @Test
    void testUpdate_threeArgConvenience_executesEachStatementWithoutListener() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("insert into t values (1)")).thenReturn(false);
        when(stmtMock.getUpdateCount()).thenReturn(1);
        int result = template.update(true, true, 100, "insert into t values (1)");
        assertEquals(1, result);
    }

    @Test
    void testUpdate_fourArgConvenience_passesResultsListenerThrough() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("insert into t values (1)")).thenReturn(false);
        when(stmtMock.getUpdateCount()).thenReturn(1);
        ISqlResultsListener listenerMock = mock(ISqlResultsListener.class);
        template.update(true, true, 100, listenerMock, "insert into t values (1)");
        verify(listenerMock).sqlBefore("insert into t values (1)", 0);
        verify(listenerMock).sqlApplied("insert into t values (1)", 1, 0, 0);
    }

    @Test
    void testUpdate_engine_autoCommitTrue_doesNotCommit() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("insert into t values (1)")).thenReturn(false);
        when(stmtMock.getUpdateCount()).thenReturn(1);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("insert into t values (1)", (String) null);
        int result = template.update(true, true, true, true, 100, null, sourceMock);
        assertEquals(1, result);
        verify(connectionMock, never()).commit();
    }

    @Test
    void testUpdate_engine_autoCommitFalse_commitsAtCommitRateBoundaryAndAtEnd() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute(any(String.class))).thenReturn(false);
        when(stmtMock.getUpdateCount()).thenReturn(1);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("insert into t values (1)", "insert into t values (2)", null);
        int result = template.update(false, true, true, true, 1, null, sourceMock);
        assertEquals(2, result);
        verify(connectionMock, times(3)).commit();
    }

    @Test
    void testUpdate_engine_whenStatementThrowsAndFailOnError_rollsBackAndRethrows() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        doThrow(new SQLException("boom")).when(stmtMock).execute("insert into t values (1)");
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("insert into t values (1)", (String) null);
        ISqlResultsListener listenerMock = mock(ISqlResultsListener.class);
        assertThrows(SqlException.class,
                () -> template.update(false, true, true, true, 100, listenerMock, sourceMock));
        verify(connectionMock).rollback();
        verify(connectionMock).close();
        verify(listenerMock).sqlErrored(eq("insert into t values (1)"), any(SqlException.class), eq(0), eq(false), eq(false));
    }

    @Test
    void testUpdate_engine_whenStatementThrowsAndFailOnErrorFalse_logsAndContinues() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        doThrow(new SQLException("boom")).when(stmtMock).execute("insert into t values (1)");
        when(stmtMock.execute("insert into t values (2)")).thenReturn(false);
        when(stmtMock.getUpdateCount()).thenReturn(1);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("insert into t values (1)", "insert into t values (2)", null);
        int result = template.update(true, false, true, true, 100, null, sourceMock);
        assertEquals(1, result);
        verify(connectionMock, never()).rollback();
    }

    @Test
    void testUpdate_engine_whenDropStatementFailsAndFailOnDropsFalse_doesNotRethrow() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        doThrow(new SQLException("boom")).when(stmtMock).execute("drop table t");
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("drop table t", (String) null);
        ISqlResultsListener listenerMock = mock(ISqlResultsListener.class);
        int result = template.update(true, true, false, true, 100, listenerMock, sourceMock);
        assertEquals(0, result);
        verify(listenerMock).sqlErrored(eq("drop table t"), any(SqlException.class), eq(0), eq(true), eq(false));
    }

    @Test
    void testUpdate_engine_whenSequenceCreateFailsAndFailOnSequenceCreateFalse_doesNotRethrow() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        doThrow(new SQLException("boom")).when(stmtMock).execute("create sequence s");
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("create sequence s", (String) null);
        ISqlResultsListener listenerMock = mock(ISqlResultsListener.class);
        int result = template.update(true, true, true, false, 100, listenerMock, sourceMock);
        assertEquals(0, result);
        verify(listenerMock).sqlErrored(eq("create sequence s"), any(SqlException.class), eq(0), eq(false), eq(true));
    }

    @Test
    void testUpdate_engine_withResults_drainsResultSetAndReportsRowsRetrieved() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.execute("select 1")).thenReturn(true);
        when(stmtMock.getUpdateCount()).thenReturn(-1);
        when(stmtMock.getResultSet()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true, true, false);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("select 1", (String) null);
        ISqlResultsListener listenerMock = mock(ISqlResultsListener.class);
        template.update(true, true, true, true, 100, listenerMock, sourceMock);
        verify(listenerMock).sqlApplied("select 1", -1, 2, 0);
        verify(rsMock).close();
    }

    @Test
    void testUpdate_engine_blankStatement_isSkipped() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn("   ", (String) null);
        ISqlResultsListener listenerMock = mock(ISqlResultsListener.class);
        int result = template.update(true, true, true, true, 100, listenerMock, sourceMock);
        assertEquals(0, result);
        verify(stmtMock, never()).execute(any(String.class));
        verify(listenerMock, never()).sqlBefore(any(String.class), anyInt());
    }

    @Test
    void testUpdate_engine_finally_restoresOriginalAutoCommitSetting() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(connectionMock.getAutoCommit()).thenReturn(true);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn((String) null);
        template.update(false, true, true, true, 100, null, sourceMock);
        InOrder inOrder = inOrder(connectionMock);
        inOrder.verify(connectionMock).setAutoCommit(false);
        inOrder.verify(connectionMock).setAutoCommit(true);
    }

    @Test
    void testUpdate_engine_whenConnectionClosedInFinally_doesNotRestoreAutoCommit() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        Statement stmtMock = mock(Statement.class);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(connectionMock.isClosed()).thenReturn(true);
        ISqlStatementSource sourceMock = mock(ISqlStatementSource.class);
        when(sourceMock.readSqlStatement()).thenReturn((String) null);
        template.update(true, true, true, true, 100, null, sourceMock);
        verify(connectionMock, times(1)).setAutoCommit(true);
        verify(connectionMock, never()).setAutoCommit(false);
    }

    @Test
    void testTestConnection_doesNotThrowAndClosesConnection() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        assertDoesNotThrow(template::testConnection);
        verify(connectionMock).close();
    }

    @Test
    void testExecute_returnsCallbackResultAndClosesConnection() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        String result = template.execute(con -> {
            assertSame(connectionMock, con);
            return "value";
        });
        assertEquals("value", result);
        verify(connectionMock).close();
    }

    @Test
    void testExecute_whenCallbackThrowsSQLException_translatesAndThrowsAndClosesConnection() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        SqlException thrown = assertThrows(SqlException.class, () -> template.execute(con -> {
            throw new SQLException("boom");
        }));
        assertNotNull(thrown);
        verify(connectionMock).close();
    }

    @Test
    void testExecute_withoutThreadLocalHandler_doesNotInteractWithAnyHandler() {
        JdbcSqlTemplate template = createTemplate();
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        template.execute(con -> "value");
        verify(handlerMock, never()).before(any(Connection.class));
        verify(handlerMock, never()).after(any(Connection.class));
    }

    @Test
    void testExecute_withThreadLocalHandler_callsBeforeAndAfter() {
        JdbcSqlTemplate template = createTemplate();
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        template.setThreadLocalConnectionHandler(handlerMock);
        try {
            template.execute(con -> "value");
            verify(handlerMock).before(connectionMock);
            verify(handlerMock).after(connectionMock);
        } finally {
            template.clearThreadLocalConnectionHandler();
        }
    }

    @Test
    void testExecute_whenHandlerAfterThrows_doesNotPropagate() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        doThrow(new RuntimeException("boom")).when(handlerMock).after(connectionMock);
        template.setThreadLocalConnectionHandler(handlerMock);
        try {
            assertDoesNotThrow(() -> template.execute(con -> "value"));
            verify(connectionMock).close();
        } finally {
            template.clearThreadLocalConnectionHandler();
        }
    }

    @Test
    void testLookupColumnName_withNonEmptyLabel_returnsLabel() throws SQLException {
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnLabel(1)).thenReturn("myLabel");
        assertEquals("myLabel", JdbcSqlTemplate.lookupColumnName(metaDataMock, 1));
    }

    @Test
    void testLookupColumnName_withNullLabel_fallsBackToColumnName() throws SQLException {
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnLabel(1)).thenReturn(null);
        when(metaDataMock.getColumnName(1)).thenReturn("myColumn");
        assertEquals("myColumn", JdbcSqlTemplate.lookupColumnName(metaDataMock, 1));
    }

    @Test
    void testLookupColumnName_withEmptyLabel_fallsBackToColumnName() throws SQLException {
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnLabel(1)).thenReturn("");
        when(metaDataMock.getColumnName(1)).thenReturn("myColumn");
        assertEquals("myColumn", JdbcSqlTemplate.lookupColumnName(metaDataMock, 1));
    }

    @SuppressWarnings("deprecation")
    @Test
    void testGetResultSetValue_deprecatedOverload_delegatesToPrimaryOverload() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("INTEGER");
        when(rsMock.getObject(1)).thenReturn(7);
        assertEquals(7, JdbcSqlTemplate.getResultSetValue(rsMock, 1, false));
        verify(rsMock).getMetaData();
    }

    @Test
    void testGetResultSetValue_whenMetaDataNull_looksUpFromResultSet() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(rsMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("INTEGER");
        when(rsMock.getObject(1)).thenReturn(7);
        assertEquals(7, JdbcSqlTemplate.getResultSetValue(rsMock, null, 1, false, false));
        verify(rsMock).getMetaData();
    }

    @Test
    void testGetResultSetValue_readStringsAsBytesAndTextType_convertsBytesToString() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("VARCHAR");
        byte[] bytes = "hello".getBytes(Charset.defaultCharset());
        when(rsMock.getBytes(1)).thenReturn(bytes);
        Object result = JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, true, false);
        assertEquals("hello", result);
    }

    @Test
    void testGetResultSetValue_readStringsAsBytesAndTextTypeWithNullBytes_returnsNull() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("VARCHAR");
        when(rsMock.getBytes(1)).thenReturn(null);
        assertNull(JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, true, false));
    }

    @Test
    void testGetResultSetValue_withBlobAndNotReturnLobObjects_convertsToByteArray() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.BLOB);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("BLOB");
        Blob blobMock = mock(Blob.class);
        byte[] bytes = { 1, 2, 3 };
        when(blobMock.getBinaryStream()).thenReturn(new ByteArrayInputStream(bytes));
        when(rsMock.getObject(1)).thenReturn(blobMock);
        Object result = JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false);
        assertArrayEquals(bytes, (byte[]) result);
    }

    @Test
    void testGetResultSetValue_withBlobAndReturnLobObjects_keepsBlobObject() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.BLOB);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("BLOB");
        Blob blobMock = mock(Blob.class);
        when(rsMock.getObject(1)).thenReturn(blobMock);
        assertSame(blobMock, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, true));
    }

    @Test
    void testGetResultSetValue_withBlobStreamThrowingIOException_wrapsInSqlException() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.BLOB);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("BLOB");
        Blob blobMock = mock(Blob.class);
        InputStream throwingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        when(blobMock.getBinaryStream()).thenReturn(throwingStream);
        when(rsMock.getObject(1)).thenReturn(blobMock);
        SqlException thrown = assertThrows(SqlException.class,
                () -> JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void testGetResultSetValue_withClobAndNotReturnLobObjects_convertsToString() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.CLOB);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("CLOB");
        Clob clobMock = mock(Clob.class);
        when(clobMock.getCharacterStream()).thenReturn(new StringReader("clob value"));
        when(rsMock.getObject(1)).thenReturn(clobMock);
        Object result = JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false);
        assertEquals("clob value", result);
    }

    @Test
    void testGetResultSetValue_withClobAndReturnLobObjects_keepsClobObject() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.CLOB);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("CLOB");
        Clob clobMock = mock(Clob.class);
        when(rsMock.getObject(1)).thenReturn(clobMock);
        assertSame(clobMock, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, true));
    }

    @Test
    void testGetResultSetValue_withClobStreamThrowingIOException_wrapsInSqlException() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.CLOB);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("CLOB");
        Clob clobMock = mock(Clob.class);
        Reader throwingReader = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void close() {
                // no-op: this test double never needs to release resources
            }
        };
        when(clobMock.getCharacterStream()).thenReturn(throwingReader);
        when(rsMock.getObject(1)).thenReturn(clobMock);
        SqlException thrown = assertThrows(SqlException.class,
                () -> JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void testGetResultSetValue_withOracleTimestampClassName_usesGetTimestamp() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("TIMESTAMP");
        when(rsMock.getObject(1)).thenReturn(new TIMESTAMP());
        Timestamp expected = new Timestamp(System.currentTimeMillis());
        when(rsMock.getTimestamp(1)).thenReturn(expected);
        assertSame(expected, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withOracleTimestampTzClassName_usesGetString() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.TIMESTAMP_WITH_TIMEZONE);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("TIMESTAMPTZ");
        when(rsMock.getObject(1)).thenReturn(new TIMESTAMPTZ());
        when(rsMock.getString(1)).thenReturn("2024-01-01 00:00:00 +00:00");
        assertEquals("2024-01-01 00:00:00 +00:00",
                JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withOracleTimestampLtzClassName_usesGetString() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("TIMESTAMPLTZ");
        when(rsMock.getObject(1)).thenReturn(new TIMESTAMPLTZ());
        when(rsMock.getString(1)).thenReturn("2024-01-01 00:00:00");
        assertEquals("2024-01-01 00:00:00", JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withOracleDateClassNameAndTimestampColumnClass_usesGetTimestamp() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.DATE);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("DATE");
        when(metaDataMock.getColumnClassName(1)).thenReturn("java.sql.Timestamp");
        when(rsMock.getObject(1)).thenReturn(new DATE());
        Timestamp expected = new Timestamp(System.currentTimeMillis());
        when(rsMock.getTimestamp(1)).thenReturn(expected);
        assertSame(expected, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withOracleDateClassNameAndNonTimestampColumnClass_usesGetDate() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.DATE);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("DATE");
        when(metaDataMock.getColumnClassName(1)).thenReturn("oracle.sql.DATE");
        when(rsMock.getObject(1)).thenReturn(new DATE());
        java.sql.Date expected = new java.sql.Date(System.currentTimeMillis());
        when(rsMock.getDate(1)).thenReturn(expected);
        assertSame(expected, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withSqlDateAndTimestampColumnClass_convertsToTimestamp() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.DATE);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("DATE");
        when(metaDataMock.getColumnClassName(1)).thenReturn("java.sql.Timestamp");
        when(rsMock.getObject(1)).thenReturn(new java.sql.Date(System.currentTimeMillis()));
        Timestamp expected = new Timestamp(System.currentTimeMillis());
        when(rsMock.getTimestamp(1)).thenReturn(expected);
        assertSame(expected, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withSqlDateAndNonTimestampColumnClass_keepsDate() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.DATE);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("DATE");
        when(metaDataMock.getColumnClassName(1)).thenReturn("java.sql.Date");
        java.sql.Date dateValue = new java.sql.Date(System.currentTimeMillis());
        when(rsMock.getObject(1)).thenReturn(dateValue);
        assertSame(dateValue, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withTimestampAndTimestampTzType_convertsToString() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("timestamptz");
        when(rsMock.getObject(1)).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(rsMock.getString(1)).thenReturn("2024-01-01 00:00:00 +00:00");
        assertEquals("2024-01-01 00:00:00 +00:00",
                JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withTimestampAndOtherType_keepsTimestamp() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.TIMESTAMP);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("timestamp");
        Timestamp timestampValue = new Timestamp(System.currentTimeMillis());
        when(rsMock.getObject(1)).thenReturn(timestampValue);
        assertSame(timestampValue, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testGetResultSetValue_withOidJdbcTypeName_usesPostgresLobHandler() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.BIGINT);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("oid");
        when(rsMock.getObject(1)).thenReturn(12345L);
        Blob blobMock = mock(Blob.class);
        byte[] bytes = { 4, 5, 6 };
        when(blobMock.length()).thenReturn((long) bytes.length);
        when(blobMock.getBytes(1, bytes.length)).thenReturn(bytes);
        when(rsMock.getBlob(1)).thenReturn(blobMock);
        Object result = JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false);
        assertArrayEquals(bytes, (byte[]) result);
    }

    @Test
    void testGetResultSetValue_withUnitextJdbcTypeName_usesGetBytes() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("unitext");
        when(rsMock.getObject(1)).thenReturn("value");
        byte[] bytes = { 7, 8, 9 };
        when(rsMock.getBytes(1)).thenReturn(bytes);
        Object result = JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false);
        assertArrayEquals(bytes, (byte[]) result);
    }

    @Test
    void testGetResultSetValue_withPlainObject_returnsAsIs() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        ResultSetMetaData metaDataMock = mock(ResultSetMetaData.class);
        when(metaDataMock.getColumnType(1)).thenReturn(Types.INTEGER);
        when(metaDataMock.getColumnTypeName(1)).thenReturn("INTEGER");
        when(rsMock.getObject(1)).thenReturn(42);
        assertEquals(42, JdbcSqlTemplate.getResultSetValue(rsMock, metaDataMock, 1, false, false));
    }

    @Test
    void testCloseResultSet_closesResultSet() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        JdbcSqlTemplate.close(rsMock);
        verify(rsMock).close();
    }

    @Test
    void testCloseResultSet_withNull_doesNotThrow() {
        assertDoesNotThrow(() -> JdbcSqlTemplate.close((ResultSet) null));
    }

    @Test
    void testCloseResultSet_whenCloseThrows_doesNotPropagate() throws SQLException {
        ResultSet rsMock = mock(ResultSet.class);
        doThrow(new SQLException("boom")).when(rsMock).close();
        assertDoesNotThrow(() -> JdbcSqlTemplate.close(rsMock));
    }

    @Test
    void testClosePreparedStatement_closesStatement() throws SQLException {
        PreparedStatement psMock = mock(PreparedStatement.class);
        JdbcSqlTemplate.close(psMock);
        verify(psMock).close();
    }

    @Test
    void testClosePreparedStatement_whenCloseThrows_doesNotPropagate() throws SQLException {
        PreparedStatement psMock = mock(PreparedStatement.class);
        doThrow(new SQLException("boom")).when(psMock).close();
        assertDoesNotThrow(() -> JdbcSqlTemplate.close(psMock));
    }

    @Test
    void testCloseStatement_closesStatement() throws SQLException {
        Statement stmtMock = mock(Statement.class);
        JdbcSqlTemplate.close(stmtMock);
        verify(stmtMock).close();
    }

    @Test
    void testCloseStatement_whenCloseThrows_doesNotPropagate() throws SQLException {
        Statement stmtMock = mock(Statement.class);
        doThrow(new SQLException("boom")).when(stmtMock).close();
        assertDoesNotThrow(() -> JdbcSqlTemplate.close(stmtMock));
    }

    @Test
    void testCloseWithAutoCommit_restoresAutoCommitAndClosesConnection() throws SQLException {
        JdbcSqlTemplate.close(true, connectionMock);
        verify(connectionMock).setAutoCommit(true);
        verify(connectionMock).close();
    }

    @Test
    void testCloseWithAutoCommit_whenSetAutoCommitThrows_stillClosesConnection() throws SQLException {
        doThrow(new SQLException("boom")).when(connectionMock).setAutoCommit(true);
        JdbcSqlTemplate.close(true, connectionMock);
        verify(connectionMock).close();
    }

    @Test
    void testCloseWithIsolationLevel_whenLevelDiffers_setsIsolationLevel() throws SQLException {
        when(connectionMock.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_UNCOMMITTED);
        JdbcSqlTemplate.close(true, Connection.TRANSACTION_READ_COMMITTED, connectionMock);
        verify(connectionMock).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(connectionMock).close();
    }

    @Test
    void testCloseWithIsolationLevel_whenLevelMatches_doesNotSetIsolationLevel() throws SQLException {
        when(connectionMock.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        JdbcSqlTemplate.close(true, Connection.TRANSACTION_READ_COMMITTED, connectionMock);
        verify(connectionMock, never()).setTransactionIsolation(anyInt());
        verify(connectionMock).close();
    }

    @Test
    void testCloseConnection_closesConnection() throws SQLException {
        JdbcSqlTemplate.close(connectionMock);
        verify(connectionMock).close();
    }

    @Test
    void testCloseConnection_withNull_doesNotThrow() {
        assertDoesNotThrow(() -> JdbcSqlTemplate.close((Connection) null));
    }

    @Test
    void testCloseConnection_whenCloseThrows_doesNotPropagate() throws SQLException {
        doThrow(new SQLException("boom")).when(connectionMock).close();
        assertDoesNotThrow(() -> JdbcSqlTemplate.close(connectionMock));
    }

    @Test
    void testGetDatabaseMajorVersion_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getDatabaseMajorVersion()).thenReturn(12);
        assertEquals(12, template.getDatabaseMajorVersion());
    }

    @Test
    void testGetDatabaseMinorVersion_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getDatabaseMinorVersion()).thenReturn(3);
        assertEquals(3, template.getDatabaseMinorVersion());
    }

    @Test
    void testGetDatabaseProductName_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertEquals("PostgreSQL", template.getDatabaseProductName());
    }

    @Test
    void testIsStoresMixedCaseQuotedIdentifiers_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.storesMixedCaseQuotedIdentifiers()).thenReturn(true);
        assertTrue(template.isStoresMixedCaseQuotedIdentifiers());
    }

    @Test
    void testIsStoresUpperCaseIdentifiers_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.storesUpperCaseIdentifiers()).thenReturn(true);
        assertTrue(template.isStoresUpperCaseIdentifiers());
    }

    @Test
    void testIsStoresLowerCaseIdentifiers_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.storesLowerCaseIdentifiers()).thenReturn(true);
        assertTrue(template.isStoresLowerCaseIdentifiers());
    }

    @Test
    void testGetDatabaseProductVersion_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getDatabaseProductVersion()).thenReturn("14.2");
        assertEquals("14.2", template.getDatabaseProductVersion());
    }

    @Test
    void testGetDriverName_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getDriverName()).thenReturn("pgjdbc");
        assertEquals("pgjdbc", template.getDriverName());
    }

    @Test
    void testGetDriverVersion_returnsValueFromMetaData() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getDriverVersion()).thenReturn("42.5.0");
        assertEquals("42.5.0", template.getDriverVersion());
    }

    @Test
    void testGetSqlKeywords_splitsCommaSeparatedKeywords() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.getSQLKeywords()).thenReturn("FOO,BAR,BAZ");
        assertEquals(Set.of("FOO", "BAR", "BAZ"), template.getSqlKeywords());
    }

    @Test
    void testSupportsGetGeneratedKeys_cachesResultAcrossCalls() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(true);
        assertTrue(template.supportsGetGeneratedKeys());
        assertTrue(template.supportsGetGeneratedKeys());
        verify(dataSourceMock, times(1)).getConnection();
    }

    @Test
    void testSupportsReturningKeys_returnsFalse() {
        assertFalse(createTemplate().supportsReturningKeys());
    }

    @Test
    void testAllowsNullForIdentityColumn_returnsTrue() {
        assertTrue(createTemplate().allowsNullForIdentityColumn());
    }

    @Test
    void testGetSelectLastInsertIdSql_throwsUnsupportedOperationException() {
        JdbcSqlTemplate template = createTemplate();
        assertThrows(UnsupportedOperationException.class, () -> template.getSelectLastInsertIdSql("my_seq"));
    }

    @Test
    void testInsertWithGeneratedKey_publicOverload_delegatesAndReturnsGeneratedKey() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        template.supportsGetGeneratedKeys = true;
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)", new int[] { 1 })).thenReturn(pstmtMock);
        when(pstmtMock.getGeneratedKeys()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getLong(1)).thenReturn(42L);
        long key = template.insertWithGeneratedKey("insert into t (name) values (?)", "id", null,
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(42L, key);
        verify(connectionMock).close();
    }

    @Test
    void testInsertWithGeneratedKey_whenSupportsGetGeneratedKeysAndAllowsNull_preparesStatementWithColumnIndexesAndReturnsKey()
            throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(true);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)", new int[] { 1 })).thenReturn(pstmtMock);
        when(pstmtMock.getGeneratedKeys()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getLong(1)).thenReturn(7L);
        long key = template.insertWithGeneratedKey(connectionMock, "insert into t (name) values (?)", "id", null,
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(7L, key);
        verify(pstmtMock).execute();
    }

    @Test
    void testInsertWithGeneratedKey_whenSupportsGetGeneratedKeysAndDisallowsNull_rewritesSqlAndUsesReturnGeneratedKeysConstant()
            throws SQLException {
        JdbcSqlTemplate template = spy(createTemplate());
        doReturn(false).when(template).allowsNullForIdentityColumn();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(true);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)", Statement.RETURN_GENERATED_KEYS))
                .thenReturn(pstmtMock);
        when(pstmtMock.getGeneratedKeys()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getLong(1)).thenReturn(9L);
        long key = template.insertWithGeneratedKey(connectionMock, "insert into t (id,name) values (null,?)", "id",
                null, new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(9L, key);
    }

    @Test
    void testInsertWithGeneratedKey_whenSupportsReturningKeysAndAllowsNull_appendsReturningClauseAndExecutesQuery()
            throws SQLException {
        JdbcSqlTemplate template = spy(createTemplate());
        doReturn(true).when(template).supportsReturningKeys();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(false);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?) returning id")).thenReturn(pstmtMock);
        when(pstmtMock.executeQuery()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getLong(1)).thenReturn(11L);
        long key = template.insertWithGeneratedKey(connectionMock, "insert into t (name) values (?)", "id", null,
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(11L, key);
        verify(pstmtMock, never()).execute();
    }

    @Test
    void testInsertWithGeneratedKey_whenNoGeneratedKeySupport_usesSelectLastInsertIdSql() throws SQLException {
        JdbcSqlTemplate template = spy(createTemplate());
        doReturn("select last_insert_id()").when(template).getSelectLastInsertIdSql("seq1");
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(false);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        Statement stmtMock = mock(Statement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)")).thenReturn(pstmtMock);
        when(connectionMock.createStatement()).thenReturn(stmtMock);
        when(stmtMock.executeQuery("select last_insert_id()")).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        when(rsMock.getLong(1)).thenReturn(13L);
        long key = template.insertWithGeneratedKey(connectionMock, "insert into t (name) values (?)", "id", "seq1",
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(13L, key);
        verify(pstmtMock).execute();
        verify(stmtMock).close();
    }

    @Test
    void testInsertWithGeneratedKey_whenGeneratedKeysResultSetEmpty_returnsZero() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(true);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)", new int[] { 1 })).thenReturn(pstmtMock);
        when(pstmtMock.getGeneratedKeys()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(false);
        long key = template.insertWithGeneratedKey(connectionMock, "insert into t (name) values (?)", "id", null,
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        assertEquals(0L, key);
    }

    @Test
    void testInsertWithGeneratedKey_closesPreparedStatementAndGeneratedKeysResultSet() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(true);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)", new int[] { 1 })).thenReturn(pstmtMock);
        when(pstmtMock.getGeneratedKeys()).thenReturn(rsMock);
        when(rsMock.next()).thenReturn(true);
        template.insertWithGeneratedKey(connectionMock, "insert into t (name) values (?)", "id", null,
                new Object[] { "value" }, new int[] { Types.VARCHAR });
        verify(rsMock).close();
        verify(pstmtMock).close();
    }

    @Test
    void testInsertWithGeneratedKey_whenExecuteThrowsSQLException_stillClosesPreparedStatementAndPropagates()
            throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        when(metaDataMock.supportsGetGeneratedKeys()).thenReturn(true);
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        when(connectionMock.prepareStatement("insert into t (name) values (?)", new int[] { 1 })).thenReturn(pstmtMock);
        doThrow(new SQLException("boom")).when(pstmtMock).execute();
        assertThrows(SQLException.class, () -> template.insertWithGeneratedKey(connectionMock,
                "insert into t (name) values (?)", "id", null, new Object[] { "value" }, new int[] { Types.VARCHAR }));
        verify(pstmtMock).close();
    }

    @Test
    void testIsUniqueKeyViolation_whenNoCodesOrStatesConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.isUniqueKeyViolation(new SQLException("boom", "23000", 1062)));
    }

    @Test
    void testIsUniqueKeyViolation_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.primaryKeyViolationCodes = new int[] { 1062 };
        assertTrue(template.isUniqueKeyViolation(new SQLException("boom", null, 1062)));
    }

    @Test
    void testIsUniqueKeyViolation_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.primaryKeyViolationSqlStates = new String[] { "23000" };
        assertTrue(template.isUniqueKeyViolation(new SQLException("boom", "23000")));
    }

    @Test
    void testIsUniqueKeyViolation_whenMessagePartMatchesCaseInsensitively_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.primaryKeyViolationSqlStates = new String[] { "99999" };
        template.primaryKeyViolationMessageParts = new String[] { "DUPLICATE KEY" };
        assertTrue(template.isUniqueKeyViolation(new SQLException("Duplicate key found", "23000")));
    }

    @Test
    void testIsForeignKeyViolation_whenNoCodesOrStatesConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.isForeignKeyViolation(new SQLException("boom", "23000", 1451)));
    }

    @Test
    void testIsForeignKeyViolation_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyViolationCodes = new int[] { 1451 };
        assertTrue(template.isForeignKeyViolation(new SQLException("boom", null, 1451)));
    }

    @Test
    void testIsForeignKeyViolation_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyViolationSqlStates = new String[] { "23000" };
        assertTrue(template.isForeignKeyViolation(new SQLException("boom", "23000")));
    }

    @Test
    void testIsForeignKeyViolation_whenMessagePartMatchesCaseInsensitively_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyViolationSqlStates = new String[] { "99999" };
        template.foreignKeyViolationMessageParts = new String[] { "FOREIGN KEY CONSTRAINT" };
        assertTrue(template.isForeignKeyViolation(new SQLException("Foreign key constraint fails", "23000")));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_whenNothingConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.isForeignKeyChildExistsViolation(new SQLException("boom", "23000", 1451)));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyChildExistsViolationCodes = new int[] { 1451 };
        assertTrue(template.isForeignKeyChildExistsViolation(new SQLException("boom", null, 1451)));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyChildExistsViolationSqlStates = new String[] { "23000" };
        assertTrue(template.isForeignKeyChildExistsViolation(new SQLException("boom", "23000")));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_whenMessagePartMatchesCaseInsensitively_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyChildExistsViolationMessageParts = new String[] { "CHILD RECORDS EXIST" };
        assertTrue(template.isForeignKeyChildExistsViolation(new SQLException("child records exist for this row")));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_whenMessagePartWildcardRegexMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.foreignKeyChildExistsViolationMessageParts = new String[] { ".*cannot delete.*" };
        assertTrue(template.isForeignKeyChildExistsViolation(
                new SQLException("cannot delete parent row: child records exist")));
    }

    @Test
    void testIsDeadlock_whenNoCodesOrStatesConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.isDeadlock(new SQLException("boom", "40001", 1213)));
    }

    @Test
    void testIsDeadlock_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.deadlockCodes = new int[] { 1213 };
        assertTrue(template.isDeadlock(new SQLException("boom", null, 1213)));
    }

    @Test
    void testIsDeadlock_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.deadlockSqlStates = new String[] { "40001" };
        assertTrue(template.isDeadlock(new SQLException("boom", "40001")));
    }

    @Test
    void testIsDataTruncationViolation_whenNoCodesOrStatesConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.isDataTruncationViolation(new SQLException("boom", "22001", 1406)));
    }

    @Test
    void testIsDataTruncationViolation_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.dataTruncationCodes = new int[] { 1406 };
        assertTrue(template.isDataTruncationViolation(new SQLException("boom", null, 1406)));
    }

    @Test
    void testIsDataTruncationViolation_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.dataTruncationStates = new String[] { "22001" };
        assertTrue(template.isDataTruncationViolation(new SQLException("boom", "22001")));
    }

    @Test
    void testDoesObjectAlreadyExist_whenNoCodesOrStatesConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.doesObjectAlreadyExist(new SQLException("boom", "42S01", 1050)));
    }

    @Test
    void testDoesObjectAlreadyExist_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.objectAlreadyExistsCodes = new int[] { 1050 };
        assertTrue(template.doesObjectAlreadyExist(new SQLException("boom", null, 1050)));
    }

    @Test
    void testDoesObjectAlreadyExist_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.objectAlreadyExistsStates = new String[] { "42S01" };
        assertTrue(template.doesObjectAlreadyExist(new SQLException("boom", "42S01")));
    }

    @Test
    void testDoesObjectNotExist_whenNoCodesOrStatesConfigured_returnsFalse() {
        JdbcSqlTemplate template = createTemplate();
        assertFalse(template.doesObjectNotExist(new SQLException("boom", "42S02", 1051)));
    }

    @Test
    void testDoesObjectNotExist_whenErrorCodeMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.objectDoesNotExistCodes = new int[] { 1051 };
        assertTrue(template.doesObjectNotExist(new SQLException("boom", null, 1051)));
    }

    @Test
    void testDoesObjectNotExist_whenSqlStateMatches_returnsTrue() {
        JdbcSqlTemplate template = createTemplate();
        template.objectDoesNotExistStates = new String[] { "42S02" };
        assertTrue(template.doesObjectNotExist(new SQLException("boom", "42S02")));
    }

    @Test
    void testFindSQLException_withSQLException_returnsSameException() {
        JdbcSqlTemplate template = createTemplate();
        SQLException sqlEx = new SQLException("boom");
        assertSame(sqlEx, template.findSQLException(sqlEx));
    }

    @Test
    void testFindSQLException_withWrappedCause_returnsNestedSQLException() {
        JdbcSqlTemplate template = createTemplate();
        SQLException sqlEx = new SQLException("boom");
        RuntimeException wrapper = new RuntimeException("wrapped", sqlEx);
        assertSame(sqlEx, template.findSQLException(wrapper));
    }

    @Test
    void testFindSQLException_withNoCauseAndNotSQLException_returnsNull() {
        JdbcSqlTemplate template = createTemplate();
        assertNull(template.findSQLException(new RuntimeException("plain")));
    }

    @Test
    void testFindSQLException_withSelfReferencingCause_returnsNull() {
        JdbcSqlTemplate template = createTemplate();
        Throwable selfCaused = new RuntimeException("self") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertNull(template.findSQLException(selfCaused));
    }

    @Test
    void testGetObjectFromResultSet_withDateClass_usesGetTimestamp() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        Timestamp expected = new Timestamp(System.currentTimeMillis());
        when(rsMock.getTimestamp(1)).thenReturn(expected);
        assertEquals(expected, template.getObjectFromResultSet(rsMock, Date.class));
    }

    @Test
    void testGetObjectFromResultSet_withStringClass_usesGetString() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.getString(1)).thenReturn("value");
        assertEquals("value", template.getObjectFromResultSet(rsMock, String.class));
    }

    @Test
    void testGetObjectFromResultSet_withLongClass_usesGetLong() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.getLong(1)).thenReturn(42L);
        assertEquals(42L, template.getObjectFromResultSet(rsMock, Long.class));
    }

    @Test
    void testGetObjectFromResultSet_withIntegerClass_usesGetInt() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.getInt(1)).thenReturn(7);
        assertEquals(7, template.getObjectFromResultSet(rsMock, Integer.class));
    }

    @Test
    void testGetObjectFromResultSet_withFloatClass_usesGetFloat() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.getFloat(1)).thenReturn(1.5f);
        assertEquals(1.5f, template.getObjectFromResultSet(rsMock, Float.class));
    }

    @Test
    void testGetObjectFromResultSet_withDoubleClass_usesGetDouble() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.getDouble(1)).thenReturn(2.5);
        assertEquals(2.5, template.getObjectFromResultSet(rsMock, Double.class));
    }

    @Test
    void testGetObjectFromResultSet_withBigDecimalClass_usesGetBigDecimal() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        BigDecimal expected = new BigDecimal("3.14");
        when(rsMock.getBigDecimal(1)).thenReturn(expected);
        assertEquals(expected, template.getObjectFromResultSet(rsMock, BigDecimal.class));
    }

    @Test
    void testGetObjectFromResultSet_withOtherClass_usesGetObject() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        ResultSet rsMock = mock(ResultSet.class);
        Object expected = new Object();
        when(rsMock.getObject(1)).thenReturn(expected);
        assertSame(expected, template.getObjectFromResultSet(rsMock, Object.class));
    }

    @Test
    void testSetValues_withBlobTypeAndByteArrayArg_delegatesToLobHandler() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        byte[] bytes = { 1, 2, 3 };
        template.setValues(pstmtMock, new Object[] { bytes }, new int[] { Types.BLOB }, lobHandlerMock);
        verify(lobHandlerMock).setBlobAsBytes(pstmtMock, 1, bytes);
    }

    @Test
    void testSetValues_withBlobTypeAndEmptyByteArrayAndEmptyStringNulled_setsBlobFromCreatedBlob() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        template.isEmptyStringNulled = true;
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        Blob blobMock = mock(Blob.class);
        when(pstmtMock.getConnection()).thenReturn(connectionMock);
        when(connectionMock.createBlob()).thenReturn(blobMock);
        template.setValues(pstmtMock, new Object[] { new byte[0] }, new int[] { Types.BLOB }, lobHandlerMock);
        verify(pstmtMock).setBlob(1, blobMock);
        verify(lobHandlerMock, never()).setBlobAsBytes(any(PreparedStatement.class), anyInt(), any(byte[].class));
    }

    @Test
    void testSetValues_withBlobTypeAndStringArg_delegatesToLobHandlerWithBytes() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.setValues(pstmtMock, new Object[] { "value" }, new int[] { Types.BLOB }, lobHandlerMock);
        verify(lobHandlerMock).setBlobAsBytes(pstmtMock, 1, "value".getBytes(Charset.defaultCharset()));
    }

    @Test
    void testSetValues_withBlobTypeAndEmptyStringArgAndEmptyStringNulled_setsBlobFromCreatedBlob() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        template.isEmptyStringNulled = true;
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        Blob blobMock = mock(Blob.class);
        when(pstmtMock.getConnection()).thenReturn(connectionMock);
        when(connectionMock.createBlob()).thenReturn(blobMock);
        template.setValues(pstmtMock, new Object[] { "" }, new int[] { Types.BLOB }, lobHandlerMock);
        verify(pstmtMock).setBlob(1, blobMock);
        verify(lobHandlerMock, never()).setBlobAsBytes(any(PreparedStatement.class), anyInt(), any(byte[].class));
    }

    @Test
    void testSetValues_withClobType_delegatesToLobHandler() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.setValues(pstmtMock, new Object[] { "clob value" }, new int[] { Types.CLOB }, lobHandlerMock);
        verify(lobHandlerMock).setClobAsString(pstmtMock, 1, "clob value");
    }

    @Test
    void testSetValues_withClobTypeAndEmptyStringAndEmptyStringNulled_setsClobFromCreatedClob() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        template.isEmptyStringNulled = true;
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        Clob clobMock = mock(Clob.class);
        when(pstmtMock.getConnection()).thenReturn(connectionMock);
        when(connectionMock.createClob()).thenReturn(clobMock);
        template.setValues(pstmtMock, new Object[] { "" }, new int[] { Types.CLOB }, lobHandlerMock);
        verify(pstmtMock).setClob(1, clobMock);
        verify(lobHandlerMock, never()).setClobAsString(any(PreparedStatement.class), anyInt(), any(String.class));
    }

    @Test
    void testSetValues_withDecimalTypeAndNaNArg_setsParameterValueWithNull() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.setValues(pstmtMock, new Object[] { "NaN" }, new int[] { Types.DECIMAL }, lobHandler);
        verify(pstmtMock).setNull(1, Types.DECIMAL);
    }

    @Test
    void testSetValues_withDecimalTypeAndNonNaNArg_setsParameterValue() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        BigDecimal value = new BigDecimal("1.5");
        template.setValues(pstmtMock, new Object[] { value }, new int[] { Types.DECIMAL }, lobHandler);
        verify(pstmtMock).setBigDecimal(1, value);
    }

    @Test
    void testSetValues_withTinyIntType_setsParameterValue() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.setValues(pstmtMock, new Object[] { (byte) 1 }, new int[] { Types.TINYINT }, lobHandler);
        verify(pstmtMock).setObject(1, (byte) 1, Types.TINYINT);
    }

    @Test
    void testSetValues_withUnknownTypeArg_setsParameterValue() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.setValues(pstmtMock, new Object[] { "value" }, new int[] { Types.VARCHAR }, lobHandler);
        verify(pstmtMock).setString(1, "value");
    }

    @Test
    void testSetValues_fourArg_whenSQLExceptionThrown_wrapsWithParameterContextMessage() throws SQLException {
        SymmetricLobHandler lobHandlerMock = mock(SymmetricLobHandler.class);
        lobHandler = lobHandlerMock;
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        SQLException cause = new SQLException("boom");
        doThrow(cause).when(lobHandlerMock).setClobAsString(pstmtMock, 1, "value");
        SQLException thrown = assertThrows(SQLException.class, () -> template.setValues(pstmtMock,
                new Object[] { "value" }, new int[] { Types.CLOB }, lobHandlerMock));
        assertSame(cause, thrown.getCause());
        assertTrue(thrown.getMessage().contains("Parameter arg"));
    }

    @Test
    void testVerifyArgType_withOracleTimestampTz_returnsVarchar() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Types.VARCHAR, template.verifyArgType("value", ColumnTypes.ORACLE_TIMESTAMPTZ));
    }

    @Test
    void testVerifyArgType_withOracleTimestampLtz_returnsVarchar() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Types.VARCHAR, template.verifyArgType("value", ColumnTypes.ORACLE_TIMESTAMPLTZ));
    }

    @Test
    void testVerifyArgType_withTypesOther_returnsTypeUnknown() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(SqlTypeValue.TYPE_UNKNOWN, template.verifyArgType("value", Types.OTHER));
    }

    @Test
    void testVerifyArgType_withMssqlSqlVariant_returnsTypeUnknown() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(SqlTypeValue.TYPE_UNKNOWN, template.verifyArgType("value", ColumnTypes.MSSQL_SQL_VARIANT));
    }

    @Test
    void testVerifyArgType_withIntegerTypeAndBigIntegerArg_returnsDecimal() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Types.DECIMAL, template.verifyArgType(BigInteger.ONE, Types.INTEGER));
    }

    @Test
    void testVerifyArgType_withBigintTypeAndBigDecimalArg_returnsDecimal() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Types.DECIMAL, template.verifyArgType(BigDecimal.ONE, Types.BIGINT));
    }

    @Test
    void testVerifyArgType_withUnmatchedTypeAndArg_returnsSameType() {
        JdbcSqlTemplate template = createTemplate();
        assertEquals(Types.VARCHAR, template.verifyArgType("value", Types.VARCHAR));
    }

    @Test
    void testSetValues_twoArg_delegatesToDoSetValueForEachArgInOrder() throws SQLException {
        JdbcSqlTemplate template = spy(createTemplate());
        doNothing().when(template).doSetValue(any(PreparedStatement.class), anyInt(), any());
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.setValues(pstmtMock, new Object[] { "a", "b" });
        verify(template).doSetValue(pstmtMock, 1, "a");
        verify(template).doSetValue(pstmtMock, 2, "b");
    }

    @Test
    void testSetValues_twoArg_whenSQLException_logsAndRethrowsSameException() throws SQLException {
        JdbcSqlTemplate template = spy(createTemplate());
        SQLException cause = new SQLException("boom");
        doThrow(cause).when(template).doSetValue(any(PreparedStatement.class), eq(1), any());
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        SQLException thrown = assertThrows(SQLException.class,
                () -> template.setValues(pstmtMock, new Object[] { "a" }));
        assertSame(cause, thrown);
    }

    @Test
    void testDoSetValue_setsParameterValueViaStatementCreatorUtils() throws SQLException {
        JdbcSqlTemplate template = createTemplate();
        PreparedStatement pstmtMock = mock(PreparedStatement.class);
        template.doSetValue(pstmtMock, 1, 42);
        verify(pstmtMock).setObject(1, 42);
    }

    @Test
    void testGetUniqueKeyViolationIndexName_whenRegexNotConfigured_returnsNull() {
        JdbcSqlTemplate template = createTemplate();
        assertNull(template.getUniqueKeyViolationIndexName(new SQLException("Duplicate entry for key 'idx_name'")));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_whenRegexMatches_returnsCapturedGroup() {
        JdbcSqlTemplate template = createTemplate();
        template.uniqueKeyViolationNameRegex = new String[] { "for key '(.+)'" };
        String indexName = template.getUniqueKeyViolationIndexName(
                new SQLException("Duplicate entry '5' for key 'idx_name'"));
        assertEquals("idx_name", indexName);
    }

    @Test
    void testGetUniqueKeyViolationIndexName_whenFirstRegexFails_triesNextRegex() {
        JdbcSqlTemplate template = createTemplate();
        template.uniqueKeyViolationNameRegex = new String[] { "no-match-(\\d+)", "for key '(.+)'" };
        String indexName = template.getUniqueKeyViolationIndexName(
                new SQLException("Duplicate entry '5' for key 'idx_name'"));
        assertEquals("idx_name", indexName);
    }

    @Test
    void testClearThreadLocalConnectionHandler_removesHandlerFromExecute() {
        JdbcSqlTemplate template = createTemplate();
        IConnectionHandler handlerMock = mock(IConnectionHandler.class);
        template.setThreadLocalConnectionHandler(handlerMock);
        template.clearThreadLocalConnectionHandler();
        template.execute(con -> "value");
        verify(handlerMock, never()).before(any(Connection.class));
    }

    private JdbcSqlTemplate createTemplate() {
        return new JdbcSqlTemplate(dataSourceMock, settings, lobHandler, databaseInfo);
    }
}
