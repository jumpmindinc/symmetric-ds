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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jumpmind.properties.TypedProperties;

class ConnectionWrapperTest {
    private static final String SQL = "select * from sym_node";
    private Connection wrappedMock;
    private WrapperInterceptor interceptorMock;
    private ConnectionWrapper wrapper;

    @BeforeEach
    void setUp() {
        wrappedMock = mock(Connection.class);
        interceptorMock = mock(WrapperInterceptor.class, invocation -> notIntercepted());
        wrapper = new ConnectionWrapper(wrappedMock, interceptorMock);
    }

    @Test
    void testConstructor_singleArgDoesNotThrow() {
        Connection anotherWrappedMock = mock(Connection.class);
        assertInstanceOf(ConnectionWrapper.class, new ConnectionWrapper(anotherWrappedMock));
    }

    @Test
    void testConstructor_singleArgPopulatesEngineProperties() throws SQLException {
        Connection anotherWrappedMock = mock(Connection.class);
        try (ConnectionWrapper singleArgWrapper = new ConnectionWrapper(anotherWrappedMock)) {
            TypedProperties engineProperties = singleArgWrapper.getEngineProperties();
            assertTrue(engineProperties.containsKey("java.version"));
        }
    }

    @Test
    void testConstructor_singleArgDelegatesTransparently() throws SQLException {
        Connection anotherWrappedMock = mock(Connection.class);
        when(anotherWrappedMock.isClosed()).thenReturn(true);
        try (ConnectionWrapper singleArgWrapper = new ConnectionWrapper(anotherWrappedMock)) {
            assertTrue(singleArgWrapper.isClosed());
        }
    }

    @Test
    void testSetReadOnly_delegatesToWrapped() throws SQLException {
        wrapper.setReadOnly(true);
        verify(wrappedMock).setReadOnly(true);
        verify(interceptorMock).postExecute(eq("setReadOnly"), isNull(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void testSetReadOnly_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setReadOnly", true)).thenReturn(intercepted(null));
        wrapper.setReadOnly(true);
        verify(wrappedMock, never()).setReadOnly(true);
        verify(interceptorMock, never()).postExecute(eq("setReadOnly"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testClose_delegatesToWrapped() throws SQLException {
        wrapper.close();
        verify(wrappedMock).close();
        verify(interceptorMock).postExecute(eq("close"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClose_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("close")).thenReturn(intercepted(null));
        wrapper.close();
        verify(wrappedMock, never()).close();
        verify(interceptorMock, never()).postExecute(eq("close"), any(), anyLong(), anyLong());
    }

    @Test
    void testIsReadOnly_delegatesToWrapped() throws SQLException {
        when(wrappedMock.isReadOnly()).thenReturn(true);
        assertTrue(wrapper.isReadOnly());
        verify(wrappedMock).isReadOnly();
    }

    @Test
    void testIsReadOnly_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isReadOnly")).thenReturn(intercepted(true));
        assertTrue(wrapper.isReadOnly());
        verify(wrappedMock, never()).isReadOnly();
    }

    @Test
    void testIsReadOnly_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.isReadOnly()).thenReturn(false);
        when(interceptorMock.postExecute(eq("isReadOnly"), eq(false), anyLong(), anyLong())).thenReturn(intercepted(true));
        assertTrue(wrapper.isReadOnly());
    }

    @Test
    void testAbort_delegatesToWrapped() throws SQLException {
        Executor executorMock = mock(Executor.class);
        wrapper.abort(executorMock);
        verify(wrappedMock).abort(executorMock);
        verify(interceptorMock).postExecute(eq("abort"), isNull(), anyLong(), anyLong(), eq(executorMock));
    }

    @Test
    void testAbort_shortCircuitsWhenPreIntercepted() throws SQLException {
        Executor executorMock = mock(Executor.class);
        when(interceptorMock.preExecute("abort", executorMock)).thenReturn(intercepted(null));
        wrapper.abort(executorMock);
        verify(wrappedMock, never()).abort(any());
        verify(interceptorMock, never()).postExecute(eq("abort"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testCreateStatement_delegatesToWrapped() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(wrappedMock.createStatement()).thenReturn(statementMock);
        assertSame(statementMock, wrapper.createStatement());
        verify(wrappedMock).createStatement();
    }

    @Test
    void testCreateStatement_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(interceptorMock.preExecute("createStatement")).thenReturn(intercepted(statementMock));
        assertSame(statementMock, wrapper.createStatement());
        verify(wrappedMock, never()).createStatement();
    }

    @Test
    void testCreateStatement_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        Statement interceptedStatementMock = mock(Statement.class);
        when(wrappedMock.createStatement()).thenReturn(statementMock);
        when(interceptorMock.postExecute(eq("createStatement"), eq(statementMock), anyLong(), anyLong()))
                .thenReturn(intercepted(interceptedStatementMock));
        assertSame(interceptedStatementMock, wrapper.createStatement());
    }

    @Test
    void testCreateStatementIntInt_delegatesToWrapped() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(wrappedMock.createStatement(1, 2)).thenReturn(statementMock);
        assertSame(statementMock, wrapper.createStatement(1, 2));
        verify(wrappedMock).createStatement(1, 2);
    }

    @Test
    void testCreateStatementIntInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(interceptorMock.preExecute("createStatement", 1, 2)).thenReturn(intercepted(statementMock));
        assertSame(statementMock, wrapper.createStatement(1, 2));
        verify(wrappedMock, never()).createStatement(anyInt(), anyInt());
    }

    @Test
    void testCreateStatementIntInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        Statement interceptedStatementMock = mock(Statement.class);
        when(wrappedMock.createStatement(1, 2)).thenReturn(statementMock);
        when(interceptorMock.postExecute(eq("createStatement"), eq(statementMock), anyLong(), anyLong(), eq(1), eq(2)))
                .thenReturn(intercepted(interceptedStatementMock));
        assertSame(interceptedStatementMock, wrapper.createStatement(1, 2));
    }

    @Test
    void testCreateStatementIntIntInt_delegatesToWrapped() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(wrappedMock.createStatement(1, 2, 3)).thenReturn(statementMock);
        assertSame(statementMock, wrapper.createStatement(1, 2, 3));
        verify(wrappedMock).createStatement(1, 2, 3);
    }

    @Test
    void testCreateStatementIntIntInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        when(interceptorMock.preExecute("createStatement", 1, 2, 3)).thenReturn(intercepted(statementMock));
        assertSame(statementMock, wrapper.createStatement(1, 2, 3));
        verify(wrappedMock, never()).createStatement(anyInt(), anyInt(), anyInt());
    }

    @Test
    void testCreateStatementIntIntInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Statement statementMock = mock(Statement.class);
        Statement interceptedStatementMock = mock(Statement.class);
        when(wrappedMock.createStatement(1, 2, 3)).thenReturn(statementMock);
        when(interceptorMock.postExecute(eq("createStatement"), eq(statementMock), anyLong(), anyLong(), eq(1), eq(2), eq(3)))
                .thenReturn(intercepted(interceptedStatementMock));
        assertSame(interceptedStatementMock, wrapper.createStatement(1, 2, 3));
    }

    @Test
    void testPrepareStatementStringInt_delegatesToWrapped() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, 1)).thenReturn(preparedStatementMock);
        PreparedStatement result = wrapper.prepareStatement(SQL, 1);
        assertInstanceOf(PreparedStatementWrapper.class, result);
        assertEquals(SQL, ((PreparedStatementWrapper) result).getStatement());
        verify(wrappedMock).prepareStatement(SQL, 1);
    }

    @Test
    void testPrepareStatementStringInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(interceptorMock.preExecute("prepareStatement", SQL, 1)).thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, 1));
        verify(wrappedMock, never()).prepareStatement(anyString(), anyInt());
    }

    @Test
    void testPrepareStatementStringInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, 1)).thenReturn(preparedStatementMock);
        when(interceptorMock.postExecute(eq("prepareStatement"), eq(preparedStatementMock), anyLong(), anyLong(), eq(SQL), eq(1)))
                .thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, 1));
    }

    @Test
    void testPrepareStatementStringIntInt_delegatesToWrapped() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, 1, 2)).thenReturn(preparedStatementMock);
        PreparedStatement result = wrapper.prepareStatement(SQL, 1, 2);
        assertInstanceOf(PreparedStatementWrapper.class, result);
        assertEquals(SQL, ((PreparedStatementWrapper) result).getStatement());
        verify(wrappedMock).prepareStatement(SQL, 1, 2);
    }

    @Test
    void testPrepareStatementStringIntInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(interceptorMock.preExecute("prepareStatement", SQL, 1, 2)).thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, 1, 2));
        verify(wrappedMock, never()).prepareStatement(anyString(), anyInt(), anyInt());
    }

    @Test
    void testPrepareStatementStringIntInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, 1, 2)).thenReturn(preparedStatementMock);
        when(interceptorMock.postExecute(eq("prepareStatement"), eq(preparedStatementMock), anyLong(), anyLong(), eq(SQL), eq(1), eq(2)))
                .thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, 1, 2));
    }

    @Test
    void testPrepareStatementStringIntIntInt_delegatesToWrapped() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, 1, 2, 3)).thenReturn(preparedStatementMock);
        PreparedStatement result = wrapper.prepareStatement(SQL, 1, 2, 3);
        assertInstanceOf(PreparedStatementWrapper.class, result);
        assertEquals(SQL, ((PreparedStatementWrapper) result).getStatement());
        verify(wrappedMock).prepareStatement(SQL, 1, 2, 3);
    }

    @Test
    void testPrepareStatementStringIntIntInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(interceptorMock.preExecute("prepareStatement", SQL, 1, 2, 3)).thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, 1, 2, 3));
        verify(wrappedMock, never()).prepareStatement(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testPrepareStatementStringIntIntInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, 1, 2, 3)).thenReturn(preparedStatementMock);
        when(interceptorMock.postExecute(eq("prepareStatement"), eq(preparedStatementMock), anyLong(), anyLong(), eq(SQL), eq(1), eq(2), eq(3)))
                .thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, 1, 2, 3));
    }

    @Test
    void testPrepareStatementStringIntArray_delegatesToWrapped() throws SQLException {
        int[] columnIndexes = new int[] { 1 };
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, columnIndexes)).thenReturn(preparedStatementMock);
        PreparedStatement result = wrapper.prepareStatement(SQL, columnIndexes);
        assertInstanceOf(PreparedStatementWrapper.class, result);
        assertEquals(SQL, ((PreparedStatementWrapper) result).getStatement());
        verify(wrappedMock).prepareStatement(SQL, columnIndexes);
    }

    @Test
    void testPrepareStatementStringIntArray_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] columnIndexes = new int[] { 1 };
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(interceptorMock.preExecute("prepareStatement", SQL, columnIndexes)).thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, columnIndexes));
        verify(wrappedMock, never()).prepareStatement(anyString(), any(int[].class));
    }

    @Test
    void testPrepareStatementStringIntArray_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] columnIndexes = new int[] { 1 };
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, columnIndexes)).thenReturn(preparedStatementMock);
        when(interceptorMock.postExecute(eq("prepareStatement"), eq(preparedStatementMock), anyLong(), anyLong(), eq(SQL), eq(columnIndexes)))
                .thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, columnIndexes));
    }

    @Test
    void testPrepareStatementStringStringArray_delegatesToWrapped() throws SQLException {
        String[] columnNames = new String[] { "id" };
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, columnNames)).thenReturn(preparedStatementMock);
        PreparedStatement result = wrapper.prepareStatement(SQL, columnNames);
        assertInstanceOf(PreparedStatementWrapper.class, result);
        assertEquals(SQL, ((PreparedStatementWrapper) result).getStatement());
        verify(wrappedMock).prepareStatement(SQL, columnNames);
    }

    @Test
    void testPrepareStatementStringStringArray_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        String[] columnNames = new String[] { "id" };
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(interceptorMock.preExecute("prepareStatement", SQL, columnNames)).thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, columnNames));
        verify(wrappedMock, never()).prepareStatement(anyString(), any(String[].class));
    }

    @Test
    void testPrepareStatementStringStringArray_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        String[] columnNames = new String[] { "id" };
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL, columnNames)).thenReturn(preparedStatementMock);
        when(interceptorMock.postExecute(eq("prepareStatement"), eq(preparedStatementMock), anyLong(), anyLong(), eq(SQL), eq(columnNames)))
                .thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL, columnNames));
    }

    @Test
    void testPrepareStatementString_delegatesToWrapped() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL)).thenReturn(preparedStatementMock);
        PreparedStatement result = wrapper.prepareStatement(SQL);
        assertInstanceOf(PreparedStatementWrapper.class, result);
        assertEquals(SQL, ((PreparedStatementWrapper) result).getStatement());
        verify(wrappedMock).prepareStatement(SQL);
    }

    @Test
    void testPrepareStatementString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(interceptorMock.preExecute("prepareStatement", SQL)).thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL));
        verify(wrappedMock, never()).prepareStatement(anyString());
    }

    @Test
    void testPrepareStatementString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        PreparedStatement preparedStatementMock = mock(PreparedStatement.class);
        PreparedStatement interceptedPreparedStatementMock = mock(PreparedStatement.class);
        when(wrappedMock.prepareStatement(SQL)).thenReturn(preparedStatementMock);
        when(interceptorMock.postExecute(eq("prepareStatement"), eq(preparedStatementMock), anyLong(), anyLong(), eq(SQL)))
                .thenReturn(intercepted(interceptedPreparedStatementMock));
        assertSame(interceptedPreparedStatementMock, wrapper.prepareStatement(SQL));
    }

    @Test
    void testPrepareCallString_delegatesToWrapped() throws SQLException {
        CallableStatement callableStatementMock = mock(CallableStatement.class);
        when(wrappedMock.prepareCall(SQL)).thenReturn(callableStatementMock);
        assertSame(callableStatementMock, wrapper.prepareCall(SQL));
        verify(wrappedMock).prepareCall(SQL);
    }

    @Test
    void testPrepareCallString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        CallableStatement interceptedCallableStatementMock = mock(CallableStatement.class);
        when(interceptorMock.preExecute("prepareCall", SQL)).thenReturn(intercepted(interceptedCallableStatementMock));
        assertSame(interceptedCallableStatementMock, wrapper.prepareCall(SQL));
        verify(wrappedMock, never()).prepareCall(anyString());
    }

    @Test
    void testPrepareCallString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        CallableStatement callableStatementMock = mock(CallableStatement.class);
        CallableStatement interceptedCallableStatementMock = mock(CallableStatement.class);
        when(wrappedMock.prepareCall(SQL)).thenReturn(callableStatementMock);
        when(interceptorMock.postExecute(eq("prepareCall"), eq(callableStatementMock), anyLong(), anyLong(), eq(SQL)))
                .thenReturn(intercepted(interceptedCallableStatementMock));
        assertSame(interceptedCallableStatementMock, wrapper.prepareCall(SQL));
    }

    @Test
    void testPrepareCallStringIntInt_delegatesToWrapped() throws SQLException {
        CallableStatement callableStatementMock = mock(CallableStatement.class);
        when(wrappedMock.prepareCall(SQL, 1, 2)).thenReturn(callableStatementMock);
        assertSame(callableStatementMock, wrapper.prepareCall(SQL, 1, 2));
        verify(wrappedMock).prepareCall(SQL, 1, 2);
    }

    @Test
    void testPrepareCallStringIntInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        CallableStatement interceptedCallableStatementMock = mock(CallableStatement.class);
        when(interceptorMock.preExecute("prepareCall", SQL, 1, 2)).thenReturn(intercepted(interceptedCallableStatementMock));
        assertSame(interceptedCallableStatementMock, wrapper.prepareCall(SQL, 1, 2));
        verify(wrappedMock, never()).prepareCall(anyString(), anyInt(), anyInt());
    }

    @Test
    void testPrepareCallStringIntInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        CallableStatement callableStatementMock = mock(CallableStatement.class);
        CallableStatement interceptedCallableStatementMock = mock(CallableStatement.class);
        when(wrappedMock.prepareCall(SQL, 1, 2)).thenReturn(callableStatementMock);
        when(interceptorMock.postExecute(eq("prepareCall"), eq(callableStatementMock), anyLong(), anyLong(), eq(SQL), eq(1), eq(2)))
                .thenReturn(intercepted(interceptedCallableStatementMock));
        assertSame(interceptedCallableStatementMock, wrapper.prepareCall(SQL, 1, 2));
    }

    @Test
    void testPrepareCallStringIntIntInt_delegatesToWrapped() throws SQLException {
        CallableStatement callableStatementMock = mock(CallableStatement.class);
        when(wrappedMock.prepareCall(SQL, 1, 2, 3)).thenReturn(callableStatementMock);
        assertSame(callableStatementMock, wrapper.prepareCall(SQL, 1, 2, 3));
        verify(wrappedMock).prepareCall(SQL, 1, 2, 3);
    }

    @Test
    void testPrepareCallStringIntIntInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        CallableStatement interceptedCallableStatementMock = mock(CallableStatement.class);
        when(interceptorMock.preExecute("prepareCall", SQL, 1, 2, 3)).thenReturn(intercepted(interceptedCallableStatementMock));
        assertSame(interceptedCallableStatementMock, wrapper.prepareCall(SQL, 1, 2, 3));
        verify(wrappedMock, never()).prepareCall(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testPrepareCallStringIntIntInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        CallableStatement callableStatementMock = mock(CallableStatement.class);
        CallableStatement interceptedCallableStatementMock = mock(CallableStatement.class);
        when(wrappedMock.prepareCall(SQL, 1, 2, 3)).thenReturn(callableStatementMock);
        when(interceptorMock.postExecute(eq("prepareCall"), eq(callableStatementMock), anyLong(), anyLong(), eq(SQL), eq(1), eq(2), eq(3)))
                .thenReturn(intercepted(interceptedCallableStatementMock));
        assertSame(interceptedCallableStatementMock, wrapper.prepareCall(SQL, 1, 2, 3));
    }

    @Test
    void testNativeSQL_delegatesToWrapped() throws SQLException {
        when(wrappedMock.nativeSQL(SQL)).thenReturn(SQL);
        assertEquals(SQL, wrapper.nativeSQL(SQL));
        verify(wrappedMock).nativeSQL(SQL);
    }

    @Test
    void testNativeSQL_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("nativeSQL", SQL)).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.nativeSQL(SQL));
        verify(wrappedMock, never()).nativeSQL(anyString());
    }

    @Test
    void testNativeSQL_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.nativeSQL(SQL)).thenReturn(SQL);
        when(interceptorMock.postExecute(eq("nativeSQL"), eq(SQL), anyLong(), anyLong(), eq(SQL))).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.nativeSQL(SQL));
    }

    @Test
    void testSetAutoCommit_delegatesToWrapped() throws SQLException {
        wrapper.setAutoCommit(true);
        verify(wrappedMock).setAutoCommit(true);
        verify(interceptorMock).postExecute(eq("setAutoCommit"), isNull(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void testSetAutoCommit_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setAutoCommit", true)).thenReturn(intercepted(null));
        wrapper.setAutoCommit(true);
        verify(wrappedMock, never()).setAutoCommit(true);
        verify(interceptorMock, never()).postExecute(eq("setAutoCommit"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetAutoCommit_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getAutoCommit()).thenReturn(true);
        assertTrue(wrapper.getAutoCommit());
        verify(wrappedMock).getAutoCommit();
    }

    @Test
    void testGetAutoCommit_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getAutoCommit")).thenReturn(intercepted(true));
        assertTrue(wrapper.getAutoCommit());
        verify(wrappedMock, never()).getAutoCommit();
    }

    @Test
    void testGetAutoCommit_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getAutoCommit()).thenReturn(false);
        when(interceptorMock.postExecute(eq("getAutoCommit"), eq(false), anyLong(), anyLong())).thenReturn(intercepted(true));
        assertTrue(wrapper.getAutoCommit());
    }

    @Test
    void testCommit_delegatesToWrapped() throws SQLException {
        wrapper.commit();
        verify(wrappedMock).commit();
        verify(interceptorMock).postExecute(eq("commit"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testCommit_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("commit")).thenReturn(intercepted(null));
        wrapper.commit();
        verify(wrappedMock, never()).commit();
        verify(interceptorMock, never()).postExecute(eq("commit"), any(), anyLong(), anyLong());
    }

    @Test
    void testRollbackSavepoint_delegatesToWrapped() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        wrapper.rollback(savepointMock);
        verify(wrappedMock).rollback(savepointMock);
        verify(interceptorMock).postExecute(eq("rollback"), isNull(), anyLong(), anyLong(), eq(savepointMock));
    }

    @Test
    void testRollbackSavepoint_shortCircuitsWhenPreIntercepted() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        when(interceptorMock.preExecute("rollback", savepointMock)).thenReturn(intercepted(null));
        wrapper.rollback(savepointMock);
        verify(wrappedMock, never()).rollback(any(Savepoint.class));
        verify(interceptorMock, never()).postExecute(eq("rollback"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testRollback_delegatesToWrapped() throws SQLException {
        wrapper.rollback();
        verify(wrappedMock).rollback();
        verify(interceptorMock).postExecute(eq("rollback"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testRollback_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("rollback")).thenReturn(intercepted(null));
        wrapper.rollback();
        verify(wrappedMock, never()).rollback();
        verify(interceptorMock, never()).postExecute(eq("rollback"), any(), anyLong(), anyLong());
    }

    @Test
    void testIsClosed_delegatesToWrapped() throws SQLException {
        when(wrappedMock.isClosed()).thenReturn(true);
        assertTrue(wrapper.isClosed());
        verify(wrappedMock).isClosed();
    }

    @Test
    void testIsClosed_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(intercepted(true));
        assertTrue(wrapper.isClosed());
        verify(wrappedMock, never()).isClosed();
    }

    @Test
    void testIsClosed_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.isClosed()).thenReturn(false);
        when(interceptorMock.postExecute(eq("isClosed"), eq(false), anyLong(), anyLong())).thenReturn(intercepted(true));
        assertTrue(wrapper.isClosed());
    }

    @Test
    void testGetMetaData_delegatesToWrapped() throws SQLException {
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(wrappedMock.getMetaData()).thenReturn(metaDataMock);
        assertSame(metaDataMock, wrapper.getMetaData());
        verify(wrappedMock).getMetaData();
    }

    @Test
    void testGetMetaData_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        DatabaseMetaData interceptedMetaDataMock = mock(DatabaseMetaData.class);
        when(interceptorMock.preExecute("getMetaData")).thenReturn(intercepted(interceptedMetaDataMock));
        assertSame(interceptedMetaDataMock, wrapper.getMetaData());
        verify(wrappedMock, never()).getMetaData();
    }

    @Test
    void testGetMetaData_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        DatabaseMetaData interceptedMetaDataMock = mock(DatabaseMetaData.class);
        when(wrappedMock.getMetaData()).thenReturn(metaDataMock);
        when(interceptorMock.postExecute(eq("getMetaData"), eq(metaDataMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedMetaDataMock));
        assertSame(interceptedMetaDataMock, wrapper.getMetaData());
    }

    @Test
    void testSetCatalog_delegatesToWrapped() throws SQLException {
        wrapper.setCatalog("catalog");
        verify(wrappedMock).setCatalog("catalog");
        verify(interceptorMock).postExecute(eq("setCatalog"), isNull(), anyLong(), anyLong(), eq("catalog"));
    }

    @Test
    void testSetCatalog_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setCatalog", "catalog")).thenReturn(intercepted(null));
        wrapper.setCatalog("catalog");
        verify(wrappedMock, never()).setCatalog(anyString());
        verify(interceptorMock, never()).postExecute(eq("setCatalog"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetCatalog_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getCatalog()).thenReturn("catalog");
        assertEquals("catalog", wrapper.getCatalog());
        verify(wrappedMock).getCatalog();
    }

    @Test
    void testGetCatalog_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getCatalog")).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.getCatalog());
        verify(wrappedMock, never()).getCatalog();
    }

    @Test
    void testGetCatalog_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getCatalog()).thenReturn("catalog");
        when(interceptorMock.postExecute(eq("getCatalog"), eq("catalog"), anyLong(), anyLong())).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.getCatalog());
    }

    @Test
    void testSetTransactionIsolation_delegatesToWrapped() throws SQLException {
        wrapper.setTransactionIsolation(1);
        verify(wrappedMock).setTransactionIsolation(1);
        verify(interceptorMock).postExecute(eq("setTransactionIsolation"), isNull(), anyLong(), anyLong(), eq(1));
    }

    @Test
    void testSetTransactionIsolation_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setTransactionIsolation", 1)).thenReturn(intercepted(null));
        wrapper.setTransactionIsolation(1);
        verify(wrappedMock, never()).setTransactionIsolation(anyInt());
        verify(interceptorMock, never()).postExecute(eq("setTransactionIsolation"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetTransactionIsolation_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getTransactionIsolation()).thenReturn(1);
        assertEquals(1, wrapper.getTransactionIsolation());
        verify(wrappedMock).getTransactionIsolation();
    }

    @Test
    void testGetTransactionIsolation_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getTransactionIsolation")).thenReturn(intercepted(2));
        assertEquals(2, wrapper.getTransactionIsolation());
        verify(wrappedMock, never()).getTransactionIsolation();
    }

    @Test
    void testGetTransactionIsolation_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getTransactionIsolation()).thenReturn(1);
        when(interceptorMock.postExecute(eq("getTransactionIsolation"), eq(1), anyLong(), anyLong())).thenReturn(intercepted(2));
        assertEquals(2, wrapper.getTransactionIsolation());
    }

    @Test
    void testGetWarnings_delegatesToWrapped() throws SQLException {
        SQLWarning warningMock = mock(SQLWarning.class);
        when(wrappedMock.getWarnings()).thenReturn(warningMock);
        assertSame(warningMock, wrapper.getWarnings());
        verify(wrappedMock).getWarnings();
    }

    @Test
    void testGetWarnings_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        SQLWarning interceptedWarningMock = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(intercepted(interceptedWarningMock));
        assertSame(interceptedWarningMock, wrapper.getWarnings());
        verify(wrappedMock, never()).getWarnings();
    }

    @Test
    void testGetWarnings_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        SQLWarning warningMock = mock(SQLWarning.class);
        SQLWarning interceptedWarningMock = mock(SQLWarning.class);
        when(wrappedMock.getWarnings()).thenReturn(warningMock);
        when(interceptorMock.postExecute(eq("getWarnings"), eq(warningMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedWarningMock));
        assertSame(interceptedWarningMock, wrapper.getWarnings());
    }

    @Test
    void testClearWarnings_delegatesToWrapped() throws SQLException {
        wrapper.clearWarnings();
        verify(wrappedMock).clearWarnings();
        verify(interceptorMock).postExecute(eq("clearWarnings"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClearWarnings_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("clearWarnings")).thenReturn(intercepted(null));
        wrapper.clearWarnings();
        verify(wrappedMock, never()).clearWarnings();
        verify(interceptorMock, never()).postExecute(eq("clearWarnings"), any(), anyLong(), anyLong());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void testGetTypeMap_delegatesToWrapped() throws SQLException {
        Map typeMap = Collections.emptyMap();
        when(wrappedMock.getTypeMap()).thenReturn(typeMap);
        assertSame(typeMap, wrapper.getTypeMap());
        verify(wrappedMock).getTypeMap();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testGetTypeMap_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Map interceptedTypeMap = Collections.singletonMap("key", "value");
        when(interceptorMock.preExecute("getTypeMap")).thenReturn(intercepted(interceptedTypeMap));
        assertSame(interceptedTypeMap, wrapper.getTypeMap());
        verify(wrappedMock, never()).getTypeMap();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void testGetTypeMap_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Map typeMap = Collections.emptyMap();
        Map interceptedTypeMap = Collections.singletonMap("key", "value");
        when(wrappedMock.getTypeMap()).thenReturn(typeMap);
        when(interceptorMock.postExecute(eq("getTypeMap"), eq(typeMap), anyLong(), anyLong())).thenReturn(intercepted(interceptedTypeMap));
        assertSame(interceptedTypeMap, wrapper.getTypeMap());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void testSetTypeMap_delegatesToWrapped() throws SQLException {
        Map typeMap = Collections.emptyMap();
        wrapper.setTypeMap(typeMap);
        verify(wrappedMock).setTypeMap(typeMap);
        verify(interceptorMock).postExecute(eq("setTypeMap"), isNull(), anyLong(), anyLong(), eq(typeMap));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void testSetTypeMap_shortCircuitsWhenPreIntercepted() throws SQLException {
        Map typeMap = Collections.emptyMap();
        when(interceptorMock.preExecute("setTypeMap", typeMap)).thenReturn(intercepted(null));
        wrapper.setTypeMap(typeMap);
        verify(wrappedMock, never()).setTypeMap(any(Map.class));
        verify(interceptorMock, never()).postExecute(eq("setTypeMap"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testSetHoldability_delegatesToWrapped() throws SQLException {
        wrapper.setHoldability(1);
        verify(wrappedMock).setHoldability(1);
        verify(interceptorMock).postExecute(eq("setHoldability"), isNull(), anyLong(), anyLong(), eq(1));
    }

    @Test
    void testSetHoldability_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setHoldability", 1)).thenReturn(intercepted(null));
        wrapper.setHoldability(1);
        verify(wrappedMock, never()).setHoldability(anyInt());
        verify(interceptorMock, never()).postExecute(eq("setHoldability"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetHoldability_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getHoldability()).thenReturn(1);
        assertEquals(1, wrapper.getHoldability());
        verify(wrappedMock).getHoldability();
    }

    @Test
    void testGetHoldability_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getHoldability")).thenReturn(intercepted(2));
        assertEquals(2, wrapper.getHoldability());
        verify(wrappedMock, never()).getHoldability();
    }

    @Test
    void testGetHoldability_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getHoldability()).thenReturn(1);
        when(interceptorMock.postExecute(eq("getHoldability"), eq(1), anyLong(), anyLong())).thenReturn(intercepted(2));
        assertEquals(2, wrapper.getHoldability());
    }

    @Test
    void testSetSavepoint_delegatesToWrapped() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        when(wrappedMock.setSavepoint()).thenReturn(savepointMock);
        assertSame(savepointMock, wrapper.setSavepoint());
        verify(wrappedMock).setSavepoint();
    }

    @Test
    void testSetSavepoint_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Savepoint interceptedSavepointMock = mock(Savepoint.class);
        when(interceptorMock.preExecute("setSavepoint")).thenReturn(intercepted(interceptedSavepointMock));
        assertSame(interceptedSavepointMock, wrapper.setSavepoint());
        verify(wrappedMock, never()).setSavepoint();
    }

    @Test
    void testSetSavepoint_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        Savepoint interceptedSavepointMock = mock(Savepoint.class);
        when(wrappedMock.setSavepoint()).thenReturn(savepointMock);
        when(interceptorMock.postExecute(eq("setSavepoint"), eq(savepointMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedSavepointMock));
        assertSame(interceptedSavepointMock, wrapper.setSavepoint());
    }

    @Test
    void testSetSavepointString_delegatesToWrapped() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        when(wrappedMock.setSavepoint("savepoint")).thenReturn(savepointMock);
        assertSame(savepointMock, wrapper.setSavepoint("savepoint"));
        verify(wrappedMock).setSavepoint("savepoint");
    }

    @Test
    void testSetSavepointString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Savepoint interceptedSavepointMock = mock(Savepoint.class);
        when(interceptorMock.preExecute("setSavepoint", "savepoint")).thenReturn(intercepted(interceptedSavepointMock));
        assertSame(interceptedSavepointMock, wrapper.setSavepoint("savepoint"));
        verify(wrappedMock, never()).setSavepoint(anyString());
    }

    @Test
    void testSetSavepointString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        Savepoint interceptedSavepointMock = mock(Savepoint.class);
        when(wrappedMock.setSavepoint("savepoint")).thenReturn(savepointMock);
        when(interceptorMock.postExecute(eq("setSavepoint"), eq(savepointMock), anyLong(), anyLong(), eq("savepoint")))
                .thenReturn(intercepted(interceptedSavepointMock));
        assertSame(interceptedSavepointMock, wrapper.setSavepoint("savepoint"));
    }

    @Test
    void testReleaseSavepoint_delegatesToWrapped() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        wrapper.releaseSavepoint(savepointMock);
        verify(wrappedMock).releaseSavepoint(savepointMock);
        verify(interceptorMock).postExecute(eq("releaseSavepoint"), isNull(), anyLong(), anyLong(), eq(savepointMock));
    }

    @Test
    void testReleaseSavepoint_shortCircuitsWhenPreIntercepted() throws SQLException {
        Savepoint savepointMock = mock(Savepoint.class);
        when(interceptorMock.preExecute("releaseSavepoint", savepointMock)).thenReturn(intercepted(null));
        wrapper.releaseSavepoint(savepointMock);
        verify(wrappedMock, never()).releaseSavepoint(any(Savepoint.class));
        verify(interceptorMock, never()).postExecute(eq("releaseSavepoint"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testCreateClob_delegatesToWrapped() throws SQLException {
        Clob clobMock = mock(Clob.class);
        when(wrappedMock.createClob()).thenReturn(clobMock);
        assertSame(clobMock, wrapper.createClob());
        verify(wrappedMock).createClob();
    }

    @Test
    void testCreateClob_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Clob interceptedClobMock = mock(Clob.class);
        when(interceptorMock.preExecute("createClob")).thenReturn(intercepted(interceptedClobMock));
        assertSame(interceptedClobMock, wrapper.createClob());
        verify(wrappedMock, never()).createClob();
    }

    @Test
    void testCreateClob_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Clob clobMock = mock(Clob.class);
        Clob interceptedClobMock = mock(Clob.class);
        when(wrappedMock.createClob()).thenReturn(clobMock);
        when(interceptorMock.postExecute(eq("createClob"), eq(clobMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedClobMock));
        assertSame(interceptedClobMock, wrapper.createClob());
    }

    @Test
    void testCreateBlob_delegatesToWrapped() throws SQLException {
        Blob blobMock = mock(Blob.class);
        when(wrappedMock.createBlob()).thenReturn(blobMock);
        assertSame(blobMock, wrapper.createBlob());
        verify(wrappedMock).createBlob();
    }

    @Test
    void testCreateBlob_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Blob interceptedBlobMock = mock(Blob.class);
        when(interceptorMock.preExecute("createBlob")).thenReturn(intercepted(interceptedBlobMock));
        assertSame(interceptedBlobMock, wrapper.createBlob());
        verify(wrappedMock, never()).createBlob();
    }

    @Test
    void testCreateBlob_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Blob blobMock = mock(Blob.class);
        Blob interceptedBlobMock = mock(Blob.class);
        when(wrappedMock.createBlob()).thenReturn(blobMock);
        when(interceptorMock.postExecute(eq("createBlob"), eq(blobMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedBlobMock));
        assertSame(interceptedBlobMock, wrapper.createBlob());
    }

    @Test
    void testCreateNClob_delegatesToWrapped() throws SQLException {
        NClob nClobMock = mock(NClob.class);
        when(wrappedMock.createNClob()).thenReturn(nClobMock);
        assertSame(nClobMock, wrapper.createNClob());
        verify(wrappedMock).createNClob();
    }

    @Test
    void testCreateNClob_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        NClob interceptedNClobMock = mock(NClob.class);
        when(interceptorMock.preExecute("createNClob")).thenReturn(intercepted(interceptedNClobMock));
        assertSame(interceptedNClobMock, wrapper.createNClob());
        verify(wrappedMock, never()).createNClob();
    }

    @Test
    void testCreateNClob_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        NClob nClobMock = mock(NClob.class);
        NClob interceptedNClobMock = mock(NClob.class);
        when(wrappedMock.createNClob()).thenReturn(nClobMock);
        when(interceptorMock.postExecute(eq("createNClob"), eq(nClobMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedNClobMock));
        assertSame(interceptedNClobMock, wrapper.createNClob());
    }

    @Test
    void testCreateSQLXML_delegatesToWrapped() throws SQLException {
        SQLXML sqlxmlMock = mock(SQLXML.class);
        when(wrappedMock.createSQLXML()).thenReturn(sqlxmlMock);
        assertSame(sqlxmlMock, wrapper.createSQLXML());
        verify(wrappedMock).createSQLXML();
    }

    @Test
    void testCreateSQLXML_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        SQLXML interceptedSqlxmlMock = mock(SQLXML.class);
        when(interceptorMock.preExecute("createSQLXML")).thenReturn(intercepted(interceptedSqlxmlMock));
        assertSame(interceptedSqlxmlMock, wrapper.createSQLXML());
        verify(wrappedMock, never()).createSQLXML();
    }

    @Test
    void testCreateSQLXML_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        SQLXML sqlxmlMock = mock(SQLXML.class);
        SQLXML interceptedSqlxmlMock = mock(SQLXML.class);
        when(wrappedMock.createSQLXML()).thenReturn(sqlxmlMock);
        when(interceptorMock.postExecute(eq("createSQLXML"), eq(sqlxmlMock), anyLong(), anyLong())).thenReturn(intercepted(interceptedSqlxmlMock));
        assertSame(interceptedSqlxmlMock, wrapper.createSQLXML());
    }

    @Test
    void testIsValid_delegatesToWrapped() throws SQLException {
        when(wrappedMock.isValid(5)).thenReturn(true);
        assertTrue(wrapper.isValid(5));
        verify(wrappedMock).isValid(5);
    }

    @Test
    void testIsValid_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isValid", 5)).thenReturn(intercepted(true));
        assertTrue(wrapper.isValid(5));
        verify(wrappedMock, never()).isValid(anyInt());
    }

    @Test
    void testIsValid_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.isValid(5)).thenReturn(false);
        when(interceptorMock.postExecute(eq("isValid"), eq(false), anyLong(), anyLong(), eq(5))).thenReturn(intercepted(true));
        assertTrue(wrapper.isValid(5));
    }

    @Test
    void testSetClientInfoProperties_delegatesToWrapped() throws Exception {
        Properties properties = new Properties();
        wrapper.setClientInfo(properties);
        verify(wrappedMock).setClientInfo(properties);
        verify(interceptorMock).postExecute(eq("setClientInfo"), isNull(), anyLong(), anyLong(), eq(properties));
    }

    @Test
    void testSetClientInfoProperties_shortCircuitsWhenPreIntercepted() throws Exception {
        Properties properties = new Properties();
        when(interceptorMock.preExecute("setClientInfo", properties)).thenReturn(intercepted(null));
        wrapper.setClientInfo(properties);
        verify(wrappedMock, never()).setClientInfo(any(Properties.class));
        verify(interceptorMock, never()).postExecute(eq("setClientInfo"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testSetClientInfoStringString_delegatesToWrapped() throws Exception {
        wrapper.setClientInfo("name", "value");
        verify(wrappedMock).setClientInfo("name", "value");
        verify(interceptorMock).postExecute(eq("setClientInfo"), isNull(), anyLong(), anyLong(), eq("name"), eq("value"));
    }

    @Test
    void testSetClientInfoStringString_shortCircuitsWhenPreIntercepted() throws Exception {
        when(interceptorMock.preExecute("setClientInfo", "name", "value")).thenReturn(intercepted(null));
        wrapper.setClientInfo("name", "value");
        verify(wrappedMock, never()).setClientInfo(anyString(), anyString());
        verify(interceptorMock, never()).postExecute(eq("setClientInfo"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testGetClientInfo_delegatesToWrapped() throws SQLException {
        Properties properties = new Properties();
        when(wrappedMock.getClientInfo()).thenReturn(properties);
        assertSame(properties, wrapper.getClientInfo());
        verify(wrappedMock).getClientInfo();
    }

    @Test
    void testGetClientInfo_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Properties interceptedProperties = new Properties();
        when(interceptorMock.preExecute("getClientInfo")).thenReturn(intercepted(interceptedProperties));
        assertSame(interceptedProperties, wrapper.getClientInfo());
        verify(wrappedMock, never()).getClientInfo();
    }

    @Test
    void testGetClientInfo_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Properties properties = new Properties();
        Properties interceptedProperties = new Properties();
        when(wrappedMock.getClientInfo()).thenReturn(properties);
        when(interceptorMock.postExecute(eq("getClientInfo"), eq(properties), anyLong(), anyLong())).thenReturn(intercepted(interceptedProperties));
        assertSame(interceptedProperties, wrapper.getClientInfo());
    }

    @Test
    void testGetClientInfoString_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getClientInfo("name")).thenReturn("value");
        assertEquals("value", wrapper.getClientInfo("name"));
        verify(wrappedMock).getClientInfo("name");
    }

    @Test
    void testGetClientInfoString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getClientInfo", "name")).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.getClientInfo("name"));
        verify(wrappedMock, never()).getClientInfo(anyString());
    }

    @Test
    void testGetClientInfoString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getClientInfo("name")).thenReturn("value");
        when(interceptorMock.postExecute(eq("getClientInfo"), eq("value"), anyLong(), anyLong(), eq("name"))).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.getClientInfo("name"));
    }

    @Test
    void testCreateArrayOf_delegatesToWrapped() throws SQLException {
        Object[] elements = new Object[] { "a" };
        Array arrayMock = mock(Array.class);
        when(wrappedMock.createArrayOf("VARCHAR", elements)).thenReturn(arrayMock);
        assertSame(arrayMock, wrapper.createArrayOf("VARCHAR", elements));
        verify(wrappedMock).createArrayOf("VARCHAR", elements);
    }

    @Test
    void testCreateArrayOf_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Object[] elements = new Object[] { "a" };
        Array interceptedArrayMock = mock(Array.class);
        when(interceptorMock.preExecute("createArrayOf", "VARCHAR", elements)).thenReturn(intercepted(interceptedArrayMock));
        assertSame(interceptedArrayMock, wrapper.createArrayOf("VARCHAR", elements));
        verify(wrappedMock, never()).createArrayOf(anyString(), any(Object[].class));
    }

    @Test
    void testCreateArrayOf_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Object[] elements = new Object[] { "a" };
        Array arrayMock = mock(Array.class);
        Array interceptedArrayMock = mock(Array.class);
        when(wrappedMock.createArrayOf("VARCHAR", elements)).thenReturn(arrayMock);
        when(interceptorMock.postExecute(eq("createArrayOf"), eq(arrayMock), anyLong(), anyLong(), eq("VARCHAR"), eq(elements)))
                .thenReturn(intercepted(interceptedArrayMock));
        assertSame(interceptedArrayMock, wrapper.createArrayOf("VARCHAR", elements));
    }

    @Test
    void testCreateStruct_delegatesToWrapped() throws SQLException {
        Object[] attributes = new Object[] { "a" };
        Struct structMock = mock(Struct.class);
        when(wrappedMock.createStruct("MY_TYPE", attributes)).thenReturn(structMock);
        assertSame(structMock, wrapper.createStruct("MY_TYPE", attributes));
        verify(wrappedMock).createStruct("MY_TYPE", attributes);
    }

    @Test
    void testCreateStruct_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Object[] attributes = new Object[] { "a" };
        Struct interceptedStructMock = mock(Struct.class);
        when(interceptorMock.preExecute("createStruct", "MY_TYPE", attributes)).thenReturn(intercepted(interceptedStructMock));
        assertSame(interceptedStructMock, wrapper.createStruct("MY_TYPE", attributes));
        verify(wrappedMock, never()).createStruct(anyString(), any(Object[].class));
    }

    @Test
    void testCreateStruct_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Object[] attributes = new Object[] { "a" };
        Struct structMock = mock(Struct.class);
        Struct interceptedStructMock = mock(Struct.class);
        when(wrappedMock.createStruct("MY_TYPE", attributes)).thenReturn(structMock);
        when(interceptorMock.postExecute(eq("createStruct"), eq(structMock), anyLong(), anyLong(), eq("MY_TYPE"), eq(attributes)))
                .thenReturn(intercepted(interceptedStructMock));
        assertSame(interceptedStructMock, wrapper.createStruct("MY_TYPE", attributes));
    }

    @Test
    void testSetSchema_delegatesToWrapped() throws SQLException {
        wrapper.setSchema("schema");
        verify(wrappedMock).setSchema("schema");
        verify(interceptorMock).postExecute(eq("setSchema"), isNull(), anyLong(), anyLong(), eq("schema"));
    }

    @Test
    void testSetSchema_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setSchema", "schema")).thenReturn(intercepted(null));
        wrapper.setSchema("schema");
        verify(wrappedMock, never()).setSchema(anyString());
        verify(interceptorMock, never()).postExecute(eq("setSchema"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetSchema_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getSchema()).thenReturn("schema");
        assertEquals("schema", wrapper.getSchema());
        verify(wrappedMock).getSchema();
    }

    @Test
    void testGetSchema_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getSchema")).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.getSchema());
        verify(wrappedMock, never()).getSchema();
    }

    @Test
    void testGetSchema_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getSchema()).thenReturn("schema");
        when(interceptorMock.postExecute(eq("getSchema"), eq("schema"), anyLong(), anyLong())).thenReturn(intercepted("intercepted"));
        assertEquals("intercepted", wrapper.getSchema());
    }

    @Test
    void testSetNetworkTimeout_delegatesToWrapped() throws SQLException {
        Executor executorMock = mock(Executor.class);
        wrapper.setNetworkTimeout(executorMock, 30);
        verify(wrappedMock).setNetworkTimeout(executorMock, 30);
        verify(interceptorMock).postExecute(eq("setNetworkTimeout"), isNull(), anyLong(), anyLong(), eq(executorMock), eq(30));
    }

    @Test
    void testSetNetworkTimeout_shortCircuitsWhenPreIntercepted() throws SQLException {
        Executor executorMock = mock(Executor.class);
        when(interceptorMock.preExecute("setNetworkTimeout", executorMock, 30)).thenReturn(intercepted(null));
        wrapper.setNetworkTimeout(executorMock, 30);
        verify(wrappedMock, never()).setNetworkTimeout(any(Executor.class), anyInt());
        verify(interceptorMock, never()).postExecute(eq("setNetworkTimeout"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testGetNetworkTimeout_delegatesToWrapped() throws SQLException {
        when(wrappedMock.getNetworkTimeout()).thenReturn(30);
        assertEquals(30, wrapper.getNetworkTimeout());
        verify(wrappedMock).getNetworkTimeout();
    }

    @Test
    void testGetNetworkTimeout_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getNetworkTimeout")).thenReturn(intercepted(60));
        assertEquals(60, wrapper.getNetworkTimeout());
        verify(wrappedMock, never()).getNetworkTimeout();
    }

    @Test
    void testGetNetworkTimeout_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.getNetworkTimeout()).thenReturn(30);
        when(interceptorMock.postExecute(eq("getNetworkTimeout"), eq(30), anyLong(), anyLong())).thenReturn(intercepted(60));
        assertEquals(60, wrapper.getNetworkTimeout());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void testUnwrap_delegatesToWrapped() throws SQLException {
        Object unwrappedMock = new Object();
        Class unwrapType = String.class;
        when(wrappedMock.unwrap(unwrapType)).thenReturn(unwrappedMock);
        assertSame(unwrappedMock, wrapper.unwrap(String.class));
        verify(wrappedMock).unwrap(String.class);
    }

    @Test
    void testUnwrap_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Object interceptedUnwrappedMock = new Object();
        when(interceptorMock.preExecute("unwrap", String.class)).thenReturn(intercepted(interceptedUnwrappedMock));
        assertSame(interceptedUnwrappedMock, wrapper.unwrap(String.class));
        verify(wrappedMock, never()).unwrap(any());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void testUnwrap_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Object unwrappedMock = new Object();
        Object interceptedUnwrappedMock = new Object();
        Class unwrapType = String.class;
        when(wrappedMock.unwrap(unwrapType)).thenReturn(unwrappedMock);
        when(interceptorMock.postExecute(eq("unwrap"), eq(unwrappedMock), anyLong(), anyLong(), eq(String.class)))
                .thenReturn(intercepted(interceptedUnwrappedMock));
        assertSame(interceptedUnwrappedMock, wrapper.unwrap(String.class));
    }

    @Test
    void testIsWrapperFor_delegatesToWrapped() throws SQLException {
        when(wrappedMock.isWrapperFor(String.class)).thenReturn(true);
        assertTrue(wrapper.isWrapperFor(String.class));
        verify(wrappedMock).isWrapperFor(String.class);
    }

    @Test
    void testIsWrapperFor_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(intercepted(true));
        assertTrue(wrapper.isWrapperFor(String.class));
        verify(wrappedMock, never()).isWrapperFor(any());
    }

    @Test
    void testIsWrapperFor_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(wrappedMock.isWrapperFor(String.class)).thenReturn(false);
        when(interceptorMock.postExecute(eq("isWrapperFor"), eq(false), anyLong(), anyLong(), eq(String.class))).thenReturn(intercepted(true));
        assertTrue(wrapper.isWrapperFor(String.class));
    }

    @Test
    void testGetEngineProperties_returnsSetValue() {
        TypedProperties engineProperties = new TypedProperties();
        wrapper.setEngineProperties(engineProperties);
        assertSame(engineProperties, wrapper.getEngineProperties());
        verifyNoInteractions(interceptorMock);
    }

    @Test
    void testSetEngineProperties_roundTrip() {
        TypedProperties engineProperties = new TypedProperties();
        engineProperties.setProperty("key", "value");
        wrapper.setEngineProperties(engineProperties);
        assertEquals("value", wrapper.getEngineProperties().getProperty("key"));
    }

    private static InterceptResult notIntercepted() {
        InterceptResult result = new InterceptResult();
        result.setIntercepted(false);
        return result;
    }

    private static InterceptResult intercepted(Object value) {
        InterceptResult result = new InterceptResult();
        result.setIntercepted(true);
        result.setInterceptResult(value);
        return result;
    }
}
