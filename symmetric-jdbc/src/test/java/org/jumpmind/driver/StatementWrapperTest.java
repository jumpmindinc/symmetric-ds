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
package org.jumpmind.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatementWrapperTest {
    private static final String SELECT_SQL = "select 1";
    private static final String UPDATE_SQL = "update x set y=1";
    private Statement wrappedMock;
    private WrapperInterceptor interceptorMock;
    private StatementWrapper statementWrapper;

    @BeforeEach
    void setUp() {
        wrappedMock = mock(Statement.class);
        interceptorMock = mock(WrapperInterceptor.class);
        statementWrapper = new StatementWrapper(wrappedMock, interceptorMock);
    }

    @Test
    void testConstructor_singleArg_createsWorkingWrapper() throws SQLException {
        try (StatementWrapper singleArgWrapper = new StatementWrapper(wrappedMock)) {
            assertInstanceOf(StatementWrapper.class, singleArgWrapper);
        }
    }

    @Test
    void testConstructor_singleArg_delegatesIsClosed() throws SQLException {
        when(wrappedMock.isClosed()).thenReturn(true);
        try (StatementWrapper singleArgWrapper = new StatementWrapper(wrappedMock)) {
            assertTrue(singleArgWrapper.isClosed());
        }
    }

    @Test
    void testClose_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("close")).thenReturn(notIntercepted());
        statementWrapper.close();
        verify(wrappedMock).close();
        verify(interceptorMock).postExecute(eq("close"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClose_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("close")).thenReturn(intercepted(null));
        statementWrapper.close();
        verify(wrappedMock, never()).close();
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong());
    }

    @Test
    void testGetConnection_delegatesToWrapped() throws SQLException {
        Connection canned = mock(Connection.class);
        when(interceptorMock.preExecute("getConnection")).thenReturn(notIntercepted());
        when(wrappedMock.getConnection()).thenReturn(canned);
        when(interceptorMock.postExecute(eq("getConnection"), eq(canned), anyLong(), anyLong())).thenReturn(notIntercepted());
        Connection result = statementWrapper.getConnection();
        assertSame(canned, result);
        verify(wrappedMock).getConnection();
    }

    @Test
    void testGetConnection_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Connection interceptValue = mock(Connection.class);
        when(interceptorMock.preExecute("getConnection")).thenReturn(intercepted(interceptValue));
        Connection result = statementWrapper.getConnection();
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).getConnection();
    }

    @Test
    void testGetConnection_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Connection wrappedValue = mock(Connection.class);
        Connection postValue = mock(Connection.class);
        when(interceptorMock.preExecute("getConnection")).thenReturn(notIntercepted());
        when(wrappedMock.getConnection()).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("getConnection"), eq(wrappedValue), anyLong(), anyLong())).thenReturn(intercepted(postValue));
        Connection result = statementWrapper.getConnection();
        assertSame(postValue, result);
    }

    @Test
    void testExecuteStringInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("execute", SELECT_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(notIntercepted());
        boolean result = statementWrapper.execute(SELECT_SQL, Statement.RETURN_GENERATED_KEYS);
        assertTrue(result);
        verify(wrappedMock).execute(SELECT_SQL, Statement.RETURN_GENERATED_KEYS);
    }

    @Test
    void testExecuteStringInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SELECT_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL, Statement.RETURN_GENERATED_KEYS);
        assertFalse(result);
        verify(wrappedMock, never()).execute(anyString(), anyInt());
    }

    @Test
    void testExecuteStringInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SELECT_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL, Statement.RETURN_GENERATED_KEYS);
        assertFalse(result);
    }

    @Test
    void testExecuteString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("execute", SELECT_SQL)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL))).thenReturn(notIntercepted());
        boolean result = statementWrapper.execute(SELECT_SQL);
        assertTrue(result);
        verify(wrappedMock).execute(SELECT_SQL);
    }

    @Test
    void testExecuteString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SELECT_SQL)).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL);
        assertFalse(result);
        verify(wrappedMock, never()).execute(anyString());
    }

    @Test
    void testExecuteString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SELECT_SQL)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL))).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL);
        assertFalse(result);
    }

    @Test
    void testExecuteStringArray_delegatesToWrapped() throws SQLException {
        String[] columns = new String[] { "id" };
        when(interceptorMock.preExecute("execute", SELECT_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL, columns)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL), eq(columns))).thenReturn(notIntercepted());
        boolean result = statementWrapper.execute(SELECT_SQL, columns);
        assertTrue(result);
        verify(wrappedMock).execute(SELECT_SQL, columns);
    }

    @Test
    void testExecuteStringArray_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        String[] columns = new String[] { "id" };
        when(interceptorMock.preExecute("execute", SELECT_SQL, columns)).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL, columns);
        assertFalse(result);
        verify(wrappedMock, never()).execute(anyString(), any(String[].class));
    }

    @Test
    void testExecuteStringArray_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        String[] columns = new String[] { "id" };
        when(interceptorMock.preExecute("execute", SELECT_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL, columns)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL), eq(columns))).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL, columns);
        assertFalse(result);
    }

    @Test
    void testExecuteIntArray_delegatesToWrapped() throws SQLException {
        int[] columns = new int[] { 1 };
        when(interceptorMock.preExecute("execute", SELECT_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL, columns)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL), eq(columns))).thenReturn(notIntercepted());
        boolean result = statementWrapper.execute(SELECT_SQL, columns);
        assertTrue(result);
        verify(wrappedMock).execute(SELECT_SQL, columns);
    }

    @Test
    void testExecuteIntArray_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] columns = new int[] { 1 };
        when(interceptorMock.preExecute("execute", SELECT_SQL, columns)).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL, columns);
        assertFalse(result);
        verify(wrappedMock, never()).execute(anyString(), any(int[].class));
    }

    @Test
    void testExecuteIntArray_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] columns = new int[] { 1 };
        when(interceptorMock.preExecute("execute", SELECT_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SELECT_SQL, columns)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SELECT_SQL), eq(columns))).thenReturn(intercepted(false));
        boolean result = statementWrapper.execute(SELECT_SQL, columns);
        assertFalse(result);
    }

    @Test
    void testIsClosed_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(notIntercepted());
        when(wrappedMock.isClosed()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isClosed"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        boolean result = statementWrapper.isClosed();
        assertTrue(result);
        verify(wrappedMock).isClosed();
    }

    @Test
    void testIsClosed_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(intercepted(false));
        boolean result = statementWrapper.isClosed();
        assertFalse(result);
        verify(wrappedMock, never()).isClosed();
    }

    @Test
    void testIsClosed_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(notIntercepted());
        when(wrappedMock.isClosed()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isClosed"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        boolean result = statementWrapper.isClosed();
        assertFalse(result);
    }

    @Test
    void testGetWarnings_delegatesToWrapped() throws SQLException {
        SQLWarning canned = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(notIntercepted());
        when(wrappedMock.getWarnings()).thenReturn(canned);
        when(interceptorMock.postExecute(eq("getWarnings"), eq(canned), anyLong(), anyLong())).thenReturn(notIntercepted());
        SQLWarning result = statementWrapper.getWarnings();
        assertSame(canned, result);
        verify(wrappedMock).getWarnings();
    }

    @Test
    void testGetWarnings_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        SQLWarning interceptValue = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(intercepted(interceptValue));
        SQLWarning result = statementWrapper.getWarnings();
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).getWarnings();
    }

    @Test
    void testGetWarnings_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        SQLWarning wrappedValue = mock(SQLWarning.class);
        SQLWarning postValue = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(notIntercepted());
        when(wrappedMock.getWarnings()).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("getWarnings"), eq(wrappedValue), anyLong(), anyLong())).thenReturn(intercepted(postValue));
        SQLWarning result = statementWrapper.getWarnings();
        assertSame(postValue, result);
    }

    @Test
    void testClearWarnings_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("clearWarnings")).thenReturn(notIntercepted());
        statementWrapper.clearWarnings();
        verify(wrappedMock).clearWarnings();
        verify(interceptorMock).postExecute(eq("clearWarnings"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClearWarnings_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("clearWarnings")).thenReturn(intercepted(null));
        statementWrapper.clearWarnings();
        verify(wrappedMock, never()).clearWarnings();
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong());
    }

    @Test
    void testExecuteQuery_delegatesToWrapped() throws SQLException {
        ResultSet canned = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery", SELECT_SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeQuery(SELECT_SQL)).thenReturn(canned);
        when(interceptorMock.postExecute(eq("executeQuery"), eq(canned), anyLong(), anyLong(), eq(SELECT_SQL))).thenReturn(notIntercepted());
        ResultSet result = statementWrapper.executeQuery(SELECT_SQL);
        assertSame(canned, result);
        verify(wrappedMock).executeQuery(SELECT_SQL);
    }

    @Test
    void testExecuteQuery_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet interceptValue = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery", SELECT_SQL)).thenReturn(intercepted(interceptValue));
        ResultSet result = statementWrapper.executeQuery(SELECT_SQL);
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).executeQuery(anyString());
    }

    @Test
    void testExecuteQuery_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet wrappedValue = mock(ResultSet.class);
        ResultSet postValue = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery", SELECT_SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeQuery(SELECT_SQL)).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("executeQuery"), eq(wrappedValue), anyLong(), anyLong(), eq(SELECT_SQL))).thenReturn(intercepted(postValue));
        ResultSet result = statementWrapper.executeQuery(SELECT_SQL);
        assertSame(postValue, result);
    }

    @Test
    void testExecuteUpdateStringInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(notIntercepted());
        int result = statementWrapper.executeUpdate(UPDATE_SQL, Statement.RETURN_GENERATED_KEYS);
        assertEquals(5, result);
        verify(wrappedMock).executeUpdate(UPDATE_SQL, Statement.RETURN_GENERATED_KEYS);
    }

    @Test
    void testExecuteUpdateStringInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(intercepted(99));
        int result = statementWrapper.executeUpdate(UPDATE_SQL, Statement.RETURN_GENERATED_KEYS);
        assertEquals(99, result);
        verify(wrappedMock, never()).executeUpdate(anyString(), anyInt());
    }

    @Test
    void testExecuteUpdateStringInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL, Statement.RETURN_GENERATED_KEYS)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(intercepted(42));
        int result = statementWrapper.executeUpdate(UPDATE_SQL, Statement.RETURN_GENERATED_KEYS);
        assertEquals(42, result);
    }

    @Test
    void testExecuteUpdateIntArray_delegatesToWrapped() throws SQLException {
        int[] columns = new int[] { 1 };
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL, columns)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL), eq(columns))).thenReturn(notIntercepted());
        int result = statementWrapper.executeUpdate(UPDATE_SQL, columns);
        assertEquals(5, result);
        verify(wrappedMock).executeUpdate(UPDATE_SQL, columns);
    }

    @Test
    void testExecuteUpdateIntArray_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] columns = new int[] { 1 };
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, columns)).thenReturn(intercepted(99));
        int result = statementWrapper.executeUpdate(UPDATE_SQL, columns);
        assertEquals(99, result);
        verify(wrappedMock, never()).executeUpdate(anyString(), any(int[].class));
    }

    @Test
    void testExecuteUpdateIntArray_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] columns = new int[] { 1 };
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL, columns)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL), eq(columns))).thenReturn(intercepted(42));
        int result = statementWrapper.executeUpdate(UPDATE_SQL, columns);
        assertEquals(42, result);
    }

    @Test
    void testExecuteUpdateStringArray_delegatesToWrapped() throws SQLException {
        String[] columns = new String[] { "id" };
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL, columns)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL), eq(columns))).thenReturn(notIntercepted());
        int result = statementWrapper.executeUpdate(UPDATE_SQL, columns);
        assertEquals(5, result);
        verify(wrappedMock).executeUpdate(UPDATE_SQL, columns);
    }

    @Test
    void testExecuteUpdateStringArray_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        String[] columns = new String[] { "id" };
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, columns)).thenReturn(intercepted(99));
        int result = statementWrapper.executeUpdate(UPDATE_SQL, columns);
        assertEquals(99, result);
        verify(wrappedMock, never()).executeUpdate(anyString(), any(String[].class));
    }

    @Test
    void testExecuteUpdateStringArray_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        String[] columns = new String[] { "id" };
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL, columns)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL, columns)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL), eq(columns))).thenReturn(intercepted(42));
        int result = statementWrapper.executeUpdate(UPDATE_SQL, columns);
        assertEquals(42, result);
    }

    @Test
    void testExecuteUpdateString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL))).thenReturn(notIntercepted());
        int result = statementWrapper.executeUpdate(UPDATE_SQL);
        assertEquals(5, result);
        verify(wrappedMock).executeUpdate(UPDATE_SQL);
    }

    @Test
    void testExecuteUpdateString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL)).thenReturn(intercepted(99));
        int result = statementWrapper.executeUpdate(UPDATE_SQL);
        assertEquals(99, result);
        verify(wrappedMock, never()).executeUpdate(anyString());
    }

    @Test
    void testExecuteUpdateString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", UPDATE_SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(UPDATE_SQL)).thenReturn(5);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(5), anyLong(), anyLong(), eq(UPDATE_SQL))).thenReturn(intercepted(42));
        int result = statementWrapper.executeUpdate(UPDATE_SQL);
        assertEquals(42, result);
    }

    @Test
    void testGetMaxFieldSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMaxFieldSize")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxFieldSize()).thenReturn(64);
        when(interceptorMock.postExecute(eq("getMaxFieldSize"), eq(64), anyLong(), anyLong())).thenReturn(notIntercepted());
        int result = statementWrapper.getMaxFieldSize();
        assertEquals(64, result);
        verify(wrappedMock).getMaxFieldSize();
    }

    @Test
    void testGetMaxFieldSize_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxFieldSize")).thenReturn(intercepted(99));
        int result = statementWrapper.getMaxFieldSize();
        assertEquals(99, result);
        verify(wrappedMock, never()).getMaxFieldSize();
    }

    @Test
    void testGetMaxFieldSize_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxFieldSize")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxFieldSize()).thenReturn(64);
        when(interceptorMock.postExecute(eq("getMaxFieldSize"), eq(64), anyLong(), anyLong())).thenReturn(intercepted(42));
        int result = statementWrapper.getMaxFieldSize();
        assertEquals(42, result);
    }

    @Test
    void testSetMaxFieldSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setMaxFieldSize", 100)).thenReturn(notIntercepted());
        statementWrapper.setMaxFieldSize(100);
        verify(wrappedMock).setMaxFieldSize(100);
        verify(interceptorMock).postExecute(eq("setMaxFieldSize"), isNull(), anyLong(), anyLong(), eq(100));
    }

    @Test
    void testSetMaxFieldSize_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setMaxFieldSize", 100)).thenReturn(intercepted(null));
        statementWrapper.setMaxFieldSize(100);
        verify(wrappedMock, never()).setMaxFieldSize(anyInt());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetMaxRows_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMaxRows")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxRows()).thenReturn(100);
        when(interceptorMock.postExecute(eq("getMaxRows"), eq(100), anyLong(), anyLong())).thenReturn(notIntercepted());
        int result = statementWrapper.getMaxRows();
        assertEquals(100, result);
        verify(wrappedMock).getMaxRows();
    }

    @Test
    void testGetMaxRows_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxRows")).thenReturn(intercepted(99));
        int result = statementWrapper.getMaxRows();
        assertEquals(99, result);
        verify(wrappedMock, never()).getMaxRows();
    }

    @Test
    void testGetMaxRows_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxRows")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxRows()).thenReturn(100);
        when(interceptorMock.postExecute(eq("getMaxRows"), eq(100), anyLong(), anyLong())).thenReturn(intercepted(42));
        int result = statementWrapper.getMaxRows();
        assertEquals(42, result);
    }

    @Test
    void testSetMaxRows_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setMaxRows", 50)).thenReturn(notIntercepted());
        statementWrapper.setMaxRows(50);
        verify(wrappedMock).setMaxRows(50);
        verify(interceptorMock).postExecute(eq("setMaxRows"), isNull(), anyLong(), anyLong(), eq(50));
    }

    @Test
    void testSetMaxRows_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setMaxRows", 50)).thenReturn(intercepted(null));
        statementWrapper.setMaxRows(50);
        verify(wrappedMock, never()).setMaxRows(anyInt());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testSetEscapeProcessing_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setEscapeProcessing", true)).thenReturn(notIntercepted());
        statementWrapper.setEscapeProcessing(true);
        verify(wrappedMock).setEscapeProcessing(true);
        verify(interceptorMock).postExecute(eq("setEscapeProcessing"), isNull(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void testSetEscapeProcessing_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setEscapeProcessing", true)).thenReturn(intercepted(null));
        statementWrapper.setEscapeProcessing(true);
        verify(wrappedMock, never()).setEscapeProcessing(anyBoolean());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetQueryTimeout_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getQueryTimeout")).thenReturn(notIntercepted());
        when(wrappedMock.getQueryTimeout()).thenReturn(30);
        when(interceptorMock.postExecute(eq("getQueryTimeout"), eq(30), anyLong(), anyLong())).thenReturn(notIntercepted());
        int result = statementWrapper.getQueryTimeout();
        assertEquals(30, result);
        verify(wrappedMock).getQueryTimeout();
    }

    @Test
    void testGetQueryTimeout_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getQueryTimeout")).thenReturn(intercepted(99));
        int result = statementWrapper.getQueryTimeout();
        assertEquals(99, result);
        verify(wrappedMock, never()).getQueryTimeout();
    }

    @Test
    void testGetQueryTimeout_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getQueryTimeout")).thenReturn(notIntercepted());
        when(wrappedMock.getQueryTimeout()).thenReturn(30);
        when(interceptorMock.postExecute(eq("getQueryTimeout"), eq(30), anyLong(), anyLong())).thenReturn(intercepted(42));
        int result = statementWrapper.getQueryTimeout();
        assertEquals(42, result);
    }

    @Test
    void testSetQueryTimeout_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setQueryTimeout", 30)).thenReturn(notIntercepted());
        statementWrapper.setQueryTimeout(30);
        verify(wrappedMock).setQueryTimeout(30);
        verify(interceptorMock).postExecute(eq("setQueryTimeout"), isNull(), anyLong(), anyLong(), eq(30));
    }

    @Test
    void testSetQueryTimeout_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setQueryTimeout", 30)).thenReturn(intercepted(null));
        statementWrapper.setQueryTimeout(30);
        verify(wrappedMock, never()).setQueryTimeout(anyInt());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testCancel_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("cancel")).thenReturn(notIntercepted());
        statementWrapper.cancel();
        verify(wrappedMock).cancel();
        verify(interceptorMock).postExecute(eq("cancel"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testCancel_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("cancel")).thenReturn(intercepted(null));
        statementWrapper.cancel();
        verify(wrappedMock, never()).cancel();
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong());
    }

    @Test
    void testSetCursorName_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setCursorName", "cursor1")).thenReturn(notIntercepted());
        statementWrapper.setCursorName("cursor1");
        verify(wrappedMock).setCursorName("cursor1");
        verify(interceptorMock).postExecute(eq("setCursorName"), isNull(), anyLong(), anyLong(), eq("cursor1"));
    }

    @Test
    void testSetCursorName_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setCursorName", "cursor1")).thenReturn(intercepted(null));
        statementWrapper.setCursorName("cursor1");
        verify(wrappedMock, never()).setCursorName(anyString());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetResultSet_delegatesToWrapped() throws SQLException {
        ResultSet canned = mock(ResultSet.class);
        when(interceptorMock.preExecute("getResultSet")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSet()).thenReturn(canned);
        when(interceptorMock.postExecute(eq("getResultSet"), eq(canned), anyLong(), anyLong())).thenReturn(notIntercepted());
        ResultSet result = statementWrapper.getResultSet();
        assertSame(canned, result);
        verify(wrappedMock).getResultSet();
    }

    @Test
    void testGetResultSet_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet interceptValue = mock(ResultSet.class);
        when(interceptorMock.preExecute("getResultSet")).thenReturn(intercepted(interceptValue));
        ResultSet result = statementWrapper.getResultSet();
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).getResultSet();
    }

    @Test
    void testGetResultSet_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet wrappedValue = mock(ResultSet.class);
        ResultSet postValue = mock(ResultSet.class);
        when(interceptorMock.preExecute("getResultSet")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSet()).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("getResultSet"), eq(wrappedValue), anyLong(), anyLong())).thenReturn(intercepted(postValue));
        ResultSet result = statementWrapper.getResultSet();
        assertSame(postValue, result);
    }

    @Test
    void testGetUpdateCount_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getUpdateCount")).thenReturn(notIntercepted());
        when(wrappedMock.getUpdateCount()).thenReturn(5);
        when(interceptorMock.postExecute(eq("getUpdateCount"), eq(5), anyLong(), anyLong())).thenReturn(notIntercepted());
        int result = statementWrapper.getUpdateCount();
        assertEquals(5, result);
        verify(wrappedMock).getUpdateCount();
    }

    @Test
    void testGetUpdateCount_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getUpdateCount")).thenReturn(intercepted(99));
        int result = statementWrapper.getUpdateCount();
        assertEquals(99, result);
        verify(wrappedMock, never()).getUpdateCount();
    }

    @Test
    void testGetUpdateCount_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getUpdateCount")).thenReturn(notIntercepted());
        when(wrappedMock.getUpdateCount()).thenReturn(5);
        when(interceptorMock.postExecute(eq("getUpdateCount"), eq(5), anyLong(), anyLong())).thenReturn(intercepted(42));
        int result = statementWrapper.getUpdateCount();
        assertEquals(42, result);
    }

    @Test
    void testGetMoreResultsWithInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults", Statement.KEEP_CURRENT_RESULT)).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults(Statement.KEEP_CURRENT_RESULT)).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong(), eq(Statement.KEEP_CURRENT_RESULT)))
                .thenReturn(notIntercepted());
        boolean result = statementWrapper.getMoreResults(Statement.KEEP_CURRENT_RESULT);
        assertTrue(result);
        verify(wrappedMock).getMoreResults(Statement.KEEP_CURRENT_RESULT);
    }

    @Test
    void testGetMoreResultsWithInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults", Statement.KEEP_CURRENT_RESULT)).thenReturn(intercepted(false));
        boolean result = statementWrapper.getMoreResults(Statement.KEEP_CURRENT_RESULT);
        assertFalse(result);
        verify(wrappedMock, never()).getMoreResults(anyInt());
    }

    @Test
    void testGetMoreResultsWithInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults", Statement.KEEP_CURRENT_RESULT)).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults(Statement.KEEP_CURRENT_RESULT)).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong(), eq(Statement.KEEP_CURRENT_RESULT)))
                .thenReturn(intercepted(false));
        boolean result = statementWrapper.getMoreResults(Statement.KEEP_CURRENT_RESULT);
        assertFalse(result);
    }

    @Test
    void testGetMoreResults_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults")).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults()).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        boolean result = statementWrapper.getMoreResults();
        assertTrue(result);
        verify(wrappedMock).getMoreResults();
    }

    @Test
    void testGetMoreResults_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults")).thenReturn(intercepted(false));
        boolean result = statementWrapper.getMoreResults();
        assertFalse(result);
        verify(wrappedMock, never()).getMoreResults();
    }

    @Test
    void testGetMoreResults_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults")).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults()).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        boolean result = statementWrapper.getMoreResults();
        assertFalse(result);
    }

    @Test
    void testSetFetchDirection_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setFetchDirection", ResultSet.FETCH_FORWARD)).thenReturn(notIntercepted());
        statementWrapper.setFetchDirection(ResultSet.FETCH_FORWARD);
        verify(wrappedMock).setFetchDirection(ResultSet.FETCH_FORWARD);
        verify(interceptorMock).postExecute(eq("setFetchDirection"), isNull(), anyLong(), anyLong(), eq(ResultSet.FETCH_FORWARD));
    }

    @Test
    void testSetFetchDirection_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setFetchDirection", ResultSet.FETCH_FORWARD)).thenReturn(intercepted(null));
        statementWrapper.setFetchDirection(ResultSet.FETCH_FORWARD);
        verify(wrappedMock, never()).setFetchDirection(anyInt());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetFetchDirection_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getFetchDirection")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchDirection()).thenReturn(ResultSet.FETCH_FORWARD);
        when(interceptorMock.postExecute(eq("getFetchDirection"), eq(ResultSet.FETCH_FORWARD), anyLong(), anyLong())).thenReturn(notIntercepted());
        int result = statementWrapper.getFetchDirection();
        assertEquals(ResultSet.FETCH_FORWARD, result);
        verify(wrappedMock).getFetchDirection();
    }

    @Test
    void testGetFetchDirection_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchDirection")).thenReturn(intercepted(99));
        int result = statementWrapper.getFetchDirection();
        assertEquals(99, result);
        verify(wrappedMock, never()).getFetchDirection();
    }

    @Test
    void testGetFetchDirection_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchDirection")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchDirection()).thenReturn(ResultSet.FETCH_FORWARD);
        when(interceptorMock.postExecute(eq("getFetchDirection"), eq(ResultSet.FETCH_FORWARD), anyLong(), anyLong())).thenReturn(intercepted(42));
        int result = statementWrapper.getFetchDirection();
        assertEquals(42, result);
    }

    @Test
    void testSetFetchSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setFetchSize", 10)).thenReturn(notIntercepted());
        statementWrapper.setFetchSize(10);
        verify(wrappedMock).setFetchSize(10);
        verify(interceptorMock).postExecute(eq("setFetchSize"), isNull(), anyLong(), anyLong(), eq(10));
    }

    @Test
    void testSetFetchSize_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setFetchSize", 10)).thenReturn(intercepted(null));
        statementWrapper.setFetchSize(10);
        verify(wrappedMock, never()).setFetchSize(anyInt());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetFetchSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getFetchSize")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchSize()).thenReturn(50);
        when(interceptorMock.postExecute(eq("getFetchSize"), eq(50), anyLong(), anyLong())).thenReturn(notIntercepted());
        int result = statementWrapper.getFetchSize();
        assertEquals(50, result);
        verify(wrappedMock).getFetchSize();
    }

    @Test
    void testGetFetchSize_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchSize")).thenReturn(intercepted(99));
        int result = statementWrapper.getFetchSize();
        assertEquals(99, result);
        verify(wrappedMock, never()).getFetchSize();
    }

    @Test
    void testGetFetchSize_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchSize")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchSize()).thenReturn(50);
        when(interceptorMock.postExecute(eq("getFetchSize"), eq(50), anyLong(), anyLong())).thenReturn(intercepted(42));
        int result = statementWrapper.getFetchSize();
        assertEquals(42, result);
    }

    @Test
    void testGetResultSetConcurrency_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getResultSetConcurrency")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetConcurrency()).thenReturn(ResultSet.CONCUR_UPDATABLE);
        when(interceptorMock.postExecute(eq("getResultSetConcurrency"), eq(ResultSet.CONCUR_UPDATABLE), anyLong(), anyLong()))
                .thenReturn(notIntercepted());
        int result = statementWrapper.getResultSetConcurrency();
        assertEquals(ResultSet.CONCUR_UPDATABLE, result);
        verify(wrappedMock).getResultSetConcurrency();
    }

    @Test
    void testGetResultSetConcurrency_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetConcurrency")).thenReturn(intercepted(99));
        int result = statementWrapper.getResultSetConcurrency();
        assertEquals(99, result);
        verify(wrappedMock, never()).getResultSetConcurrency();
    }

    @Test
    void testGetResultSetConcurrency_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetConcurrency")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetConcurrency()).thenReturn(ResultSet.CONCUR_UPDATABLE);
        when(interceptorMock.postExecute(eq("getResultSetConcurrency"), eq(ResultSet.CONCUR_UPDATABLE), anyLong(), anyLong()))
                .thenReturn(intercepted(42));
        int result = statementWrapper.getResultSetConcurrency();
        assertEquals(42, result);
    }

    @Test
    void testGetResultSetType_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getResultSetType")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetType()).thenReturn(ResultSet.TYPE_SCROLL_INSENSITIVE);
        when(interceptorMock.postExecute(eq("getResultSetType"), eq(ResultSet.TYPE_SCROLL_INSENSITIVE), anyLong(), anyLong()))
                .thenReturn(notIntercepted());
        int result = statementWrapper.getResultSetType();
        assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, result);
        verify(wrappedMock).getResultSetType();
    }

    @Test
    void testGetResultSetType_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetType")).thenReturn(intercepted(99));
        int result = statementWrapper.getResultSetType();
        assertEquals(99, result);
        verify(wrappedMock, never()).getResultSetType();
    }

    @Test
    void testGetResultSetType_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetType")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetType()).thenReturn(ResultSet.TYPE_SCROLL_INSENSITIVE);
        when(interceptorMock.postExecute(eq("getResultSetType"), eq(ResultSet.TYPE_SCROLL_INSENSITIVE), anyLong(), anyLong()))
                .thenReturn(intercepted(42));
        int result = statementWrapper.getResultSetType();
        assertEquals(42, result);
    }

    @Test
    void testAddBatch_delegatesToWrapped() throws SQLException {
        String sql = "insert into x values (1)";
        when(interceptorMock.preExecute("addBatch", sql)).thenReturn(notIntercepted());
        statementWrapper.addBatch(sql);
        verify(wrappedMock).addBatch(sql);
        verify(interceptorMock).postExecute(eq("addBatch"), isNull(), anyLong(), anyLong(), eq(sql));
    }

    @Test
    void testAddBatch_shortCircuitsWhenPreIntercepted() throws SQLException {
        String sql = "insert into x values (1)";
        when(interceptorMock.preExecute("addBatch", sql)).thenReturn(intercepted(null));
        statementWrapper.addBatch(sql);
        verify(wrappedMock, never()).addBatch(anyString());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testClearBatch_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("clearBatch")).thenReturn(notIntercepted());
        statementWrapper.clearBatch();
        verify(wrappedMock).clearBatch();
        verify(interceptorMock).postExecute(eq("clearBatch"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClearBatch_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("clearBatch")).thenReturn(intercepted(null));
        statementWrapper.clearBatch();
        verify(wrappedMock, never()).clearBatch();
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong());
    }

    @Test
    void testExecuteBatch_delegatesToWrapped() throws SQLException {
        int[] canned = new int[] { 1, 2 };
        when(interceptorMock.preExecute("executeBatch")).thenReturn(notIntercepted());
        when(wrappedMock.executeBatch()).thenReturn(canned);
        when(interceptorMock.postExecute(eq("executeBatch"), eq(canned), anyLong(), anyLong())).thenReturn(notIntercepted());
        int[] result = statementWrapper.executeBatch();
        assertSame(canned, result);
        verify(wrappedMock).executeBatch();
    }

    @Test
    void testExecuteBatch_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] interceptValue = new int[] { 9 };
        when(interceptorMock.preExecute("executeBatch")).thenReturn(intercepted(interceptValue));
        int[] result = statementWrapper.executeBatch();
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).executeBatch();
    }

    @Test
    void testExecuteBatch_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] wrappedValue = new int[] { 1, 2 };
        int[] postValue = new int[] { 3, 4, 5 };
        when(interceptorMock.preExecute("executeBatch")).thenReturn(notIntercepted());
        when(wrappedMock.executeBatch()).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("executeBatch"), eq(wrappedValue), anyLong(), anyLong())).thenReturn(intercepted(postValue));
        int[] result = statementWrapper.executeBatch();
        assertSame(postValue, result);
    }

    @Test
    void testGetGeneratedKeys_delegatesToWrapped() throws SQLException {
        ResultSet canned = mock(ResultSet.class);
        when(interceptorMock.preExecute("getGeneratedKeys")).thenReturn(notIntercepted());
        when(wrappedMock.getGeneratedKeys()).thenReturn(canned);
        when(interceptorMock.postExecute(eq("getGeneratedKeys"), eq(canned), anyLong(), anyLong())).thenReturn(notIntercepted());
        ResultSet result = statementWrapper.getGeneratedKeys();
        assertSame(canned, result);
        verify(wrappedMock).getGeneratedKeys();
    }

    @Test
    void testGetGeneratedKeys_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet interceptValue = mock(ResultSet.class);
        when(interceptorMock.preExecute("getGeneratedKeys")).thenReturn(intercepted(interceptValue));
        ResultSet result = statementWrapper.getGeneratedKeys();
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).getGeneratedKeys();
    }

    @Test
    void testGetGeneratedKeys_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet wrappedValue = mock(ResultSet.class);
        ResultSet postValue = mock(ResultSet.class);
        when(interceptorMock.preExecute("getGeneratedKeys")).thenReturn(notIntercepted());
        when(wrappedMock.getGeneratedKeys()).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("getGeneratedKeys"), eq(wrappedValue), anyLong(), anyLong())).thenReturn(intercepted(postValue));
        ResultSet result = statementWrapper.getGeneratedKeys();
        assertSame(postValue, result);
    }

    @Test
    void testGetResultSetHoldability_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getResultSetHoldability")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetHoldability()).thenReturn(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        when(interceptorMock.postExecute(eq("getResultSetHoldability"), eq(ResultSet.HOLD_CURSORS_OVER_COMMIT), anyLong(), anyLong()))
                .thenReturn(notIntercepted());
        int result = statementWrapper.getResultSetHoldability();
        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, result);
        verify(wrappedMock).getResultSetHoldability();
    }

    @Test
    void testGetResultSetHoldability_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetHoldability")).thenReturn(intercepted(99));
        int result = statementWrapper.getResultSetHoldability();
        assertEquals(99, result);
        verify(wrappedMock, never()).getResultSetHoldability();
    }

    @Test
    void testGetResultSetHoldability_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetHoldability")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetHoldability()).thenReturn(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        when(interceptorMock.postExecute(eq("getResultSetHoldability"), eq(ResultSet.HOLD_CURSORS_OVER_COMMIT), anyLong(), anyLong()))
                .thenReturn(intercepted(42));
        int result = statementWrapper.getResultSetHoldability();
        assertEquals(42, result);
    }

    @Test
    void testSetPoolable_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setPoolable", true)).thenReturn(notIntercepted());
        statementWrapper.setPoolable(true);
        verify(wrappedMock).setPoolable(true);
        verify(interceptorMock).postExecute(eq("setPoolable"), isNull(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void testSetPoolable_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setPoolable", true)).thenReturn(intercepted(null));
        statementWrapper.setPoolable(true);
        verify(wrappedMock, never()).setPoolable(anyBoolean());
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testIsPoolable_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isPoolable")).thenReturn(notIntercepted());
        when(wrappedMock.isPoolable()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isPoolable"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        boolean result = statementWrapper.isPoolable();
        assertTrue(result);
        verify(wrappedMock).isPoolable();
    }

    @Test
    void testIsPoolable_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isPoolable")).thenReturn(intercepted(false));
        boolean result = statementWrapper.isPoolable();
        assertFalse(result);
        verify(wrappedMock, never()).isPoolable();
    }

    @Test
    void testIsPoolable_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isPoolable")).thenReturn(notIntercepted());
        when(wrappedMock.isPoolable()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isPoolable"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        boolean result = statementWrapper.isPoolable();
        assertFalse(result);
    }

    @Test
    void testCloseOnCompletion_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("closeOnCompletion")).thenReturn(notIntercepted());
        statementWrapper.closeOnCompletion();
        verify(wrappedMock).closeOnCompletion();
        verify(interceptorMock).postExecute(eq("closeOnCompletion"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testCloseOnCompletion_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("closeOnCompletion")).thenReturn(intercepted(null));
        statementWrapper.closeOnCompletion();
        verify(wrappedMock, never()).closeOnCompletion();
        verify(interceptorMock, never()).postExecute(any(), any(), anyLong(), anyLong());
    }

    @Test
    void testIsCloseOnCompletion_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isCloseOnCompletion")).thenReturn(notIntercepted());
        when(wrappedMock.isCloseOnCompletion()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isCloseOnCompletion"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        boolean result = statementWrapper.isCloseOnCompletion();
        assertTrue(result);
        verify(wrappedMock).isCloseOnCompletion();
    }

    @Test
    void testIsCloseOnCompletion_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isCloseOnCompletion")).thenReturn(intercepted(false));
        boolean result = statementWrapper.isCloseOnCompletion();
        assertFalse(result);
        verify(wrappedMock, never()).isCloseOnCompletion();
    }

    @Test
    void testIsCloseOnCompletion_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isCloseOnCompletion")).thenReturn(notIntercepted());
        when(wrappedMock.isCloseOnCompletion()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isCloseOnCompletion"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        boolean result = statementWrapper.isCloseOnCompletion();
        assertFalse(result);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testUnwrap_delegatesToWrapped() throws SQLException {
        Class targetType = String.class;
        Object canned = "wrappedValue";
        when(interceptorMock.preExecute("unwrap", targetType)).thenReturn(notIntercepted());
        when(wrappedMock.unwrap(targetType)).thenReturn(canned);
        when(interceptorMock.postExecute(eq("unwrap"), eq(canned), anyLong(), anyLong(), eq(targetType))).thenReturn(notIntercepted());
        Object result = statementWrapper.unwrap(targetType);
        assertSame(canned, result);
        verify(wrappedMock).unwrap(targetType);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testUnwrap_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Class targetType = String.class;
        Object interceptValue = "interceptValue";
        when(interceptorMock.preExecute("unwrap", targetType)).thenReturn(intercepted(interceptValue));
        Object result = statementWrapper.unwrap(targetType);
        assertSame(interceptValue, result);
        verify(wrappedMock, never()).unwrap(any(Class.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void testUnwrap_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Class targetType = String.class;
        Object wrappedValue = "wrappedValue";
        Object postValue = "postValue";
        when(interceptorMock.preExecute("unwrap", targetType)).thenReturn(notIntercepted());
        when(wrappedMock.unwrap(targetType)).thenReturn(wrappedValue);
        when(interceptorMock.postExecute(eq("unwrap"), eq(wrappedValue), anyLong(), anyLong(), eq(targetType))).thenReturn(intercepted(postValue));
        Object result = statementWrapper.unwrap(targetType);
        assertSame(postValue, result);
    }

    @Test
    void testIsWrapperFor_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(notIntercepted());
        when(wrappedMock.isWrapperFor(String.class)).thenReturn(true);
        when(interceptorMock.postExecute(eq("isWrapperFor"), eq(true), anyLong(), anyLong(), eq(String.class))).thenReturn(notIntercepted());
        boolean result = statementWrapper.isWrapperFor(String.class);
        assertTrue(result);
        verify(wrappedMock).isWrapperFor(String.class);
    }

    @Test
    void testIsWrapperFor_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(intercepted(false));
        boolean result = statementWrapper.isWrapperFor(String.class);
        assertFalse(result);
        verify(wrappedMock, never()).isWrapperFor(any(Class.class));
    }

    @Test
    void testIsWrapperFor_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(notIntercepted());
        when(wrappedMock.isWrapperFor(String.class)).thenReturn(true);
        when(interceptorMock.postExecute(eq("isWrapperFor"), eq(true), anyLong(), anyLong(), eq(String.class))).thenReturn(intercepted(false));
        boolean result = statementWrapper.isWrapperFor(String.class);
        assertFalse(result);
    }

    private InterceptResult notIntercepted() {
        return new InterceptResult();
    }

    private InterceptResult intercepted(Object value) {
        InterceptResult result = new InterceptResult();
        result.setIntercepted(true);
        result.setInterceptResult(value);
        return result;
    }
}
