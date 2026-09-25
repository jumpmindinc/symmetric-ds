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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jumpmind.properties.TypedProperties;

class PreparedStatementWrapperTest {
    private static final String SQL = "select * from test";
    private PreparedStatement wrappedMock;
    private WrapperInterceptor interceptorMock;
    private PreparedStatementWrapper preparedStatementWrapper;

    @BeforeEach
    void setUp() {
        wrappedMock = mock(PreparedStatement.class);
        preparedStatementWrapper = new PreparedStatementWrapper(wrappedMock, SQL, null);
        interceptorMock = mock(WrapperInterceptor.class);
        injectMockInterceptor(preparedStatementWrapper, interceptorMock);
    }

    @Test
    void testConstructor_getStatementReturnsSql() {
        assertEquals(SQL, preparedStatementWrapper.getStatement());
    }

    @Test
    void testConstructor_engineSettersRoundTrip() {
        TypedProperties properties = new TypedProperties();
        preparedStatementWrapper.setEngineProperties(properties);
        assertSame(properties, preparedStatementWrapper.getEngineProperties());
    }

    @Test
    void testSetBoolean_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setBoolean", 1, true)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBoolean(1, true);
        verify(wrappedMock).setBoolean(1, true);
        verify(interceptorMock).postExecute(eq("setBoolean"), isNull(), anyLong(), anyLong(), eq(1), eq(true));
    }

    @Test
    void testSetBoolean_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setBoolean", 1, true)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBoolean(1, true);
        verify(wrappedMock, never()).setBoolean(1, true);
        verify(interceptorMock, never()).postExecute(eq("setBoolean"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetByte_delegatesToWrapped() throws SQLException {
        byte value = (byte) 2;
        when(interceptorMock.preExecute("setByte", 1, value)).thenReturn(notIntercepted());
        preparedStatementWrapper.setByte(1, value);
        verify(wrappedMock).setByte(1, value);
        verify(interceptorMock).postExecute(eq("setByte"), isNull(), anyLong(), anyLong(), eq(1), eq(value));
    }

    @Test
    void testSetByte_shortCircuitsWhenPreIntercepted() throws SQLException {
        byte value = (byte) 2;
        when(interceptorMock.preExecute("setByte", 1, value)).thenReturn(intercepted(null));
        preparedStatementWrapper.setByte(1, value);
        verify(wrappedMock, never()).setByte(1, value);
        verify(interceptorMock, never()).postExecute(eq("setByte"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetShort_delegatesToWrapped() throws SQLException {
        short value = (short) 3;
        when(interceptorMock.preExecute("setShort", 1, value)).thenReturn(notIntercepted());
        preparedStatementWrapper.setShort(1, value);
        verify(wrappedMock).setShort(1, value);
        verify(interceptorMock).postExecute(eq("setShort"), isNull(), anyLong(), anyLong(), eq(1), eq(value));
    }

    @Test
    void testSetShort_shortCircuitsWhenPreIntercepted() throws SQLException {
        short value = (short) 3;
        when(interceptorMock.preExecute("setShort", 1, value)).thenReturn(intercepted(null));
        preparedStatementWrapper.setShort(1, value);
        verify(wrappedMock, never()).setShort(1, value);
        verify(interceptorMock, never()).postExecute(eq("setShort"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setInt", 1, 5)).thenReturn(notIntercepted());
        preparedStatementWrapper.setInt(1, 5);
        verify(wrappedMock).setInt(1, 5);
        verify(interceptorMock).postExecute(eq("setInt"), isNull(), anyLong(), anyLong(), eq(1), eq(5));
    }

    @Test
    void testSetInt_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setInt", 1, 5)).thenReturn(intercepted(null));
        preparedStatementWrapper.setInt(1, 5);
        verify(wrappedMock, never()).setInt(1, 5);
        verify(interceptorMock, never()).postExecute(eq("setInt"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetLong_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setLong", 1, 100L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setLong(1, 100L);
        verify(wrappedMock).setLong(1, 100L);
        verify(interceptorMock).postExecute(eq("setLong"), isNull(), anyLong(), anyLong(), eq(1), eq(100L));
    }

    @Test
    void testSetLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setLong", 1, 100L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setLong(1, 100L);
        verify(wrappedMock, never()).setLong(1, 100L);
        verify(interceptorMock, never()).postExecute(eq("setLong"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetFloat_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setFloat", 1, 1.5f)).thenReturn(notIntercepted());
        preparedStatementWrapper.setFloat(1, 1.5f);
        verify(wrappedMock).setFloat(1, 1.5f);
        verify(interceptorMock).postExecute(eq("setFloat"), isNull(), anyLong(), anyLong(), eq(1), eq(1.5f));
    }

    @Test
    void testSetFloat_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setFloat", 1, 1.5f)).thenReturn(intercepted(null));
        preparedStatementWrapper.setFloat(1, 1.5f);
        verify(wrappedMock, never()).setFloat(1, 1.5f);
        verify(interceptorMock, never()).postExecute(eq("setFloat"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetDouble_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setDouble", 1, 2.5d)).thenReturn(notIntercepted());
        preparedStatementWrapper.setDouble(1, 2.5d);
        verify(wrappedMock).setDouble(1, 2.5d);
        verify(interceptorMock).postExecute(eq("setDouble"), isNull(), anyLong(), anyLong(), eq(1), eq(2.5d));
    }

    @Test
    void testSetDouble_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setDouble", 1, 2.5d)).thenReturn(intercepted(null));
        preparedStatementWrapper.setDouble(1, 2.5d);
        verify(wrappedMock, never()).setDouble(1, 2.5d);
        verify(interceptorMock, never()).postExecute(eq("setDouble"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetTimestamp_delegatesToWrapped() throws SQLException {
        Timestamp timestamp = new Timestamp(0);
        when(interceptorMock.preExecute("setTimestamp", 1, timestamp)).thenReturn(notIntercepted());
        preparedStatementWrapper.setTimestamp(1, timestamp);
        verify(wrappedMock).setTimestamp(1, timestamp);
        verify(interceptorMock).postExecute(eq("setTimestamp"), isNull(), anyLong(), anyLong(), eq(1), eq(timestamp));
    }

    @Test
    void testSetTimestamp_shortCircuitsWhenPreIntercepted() throws SQLException {
        Timestamp timestamp = new Timestamp(0);
        when(interceptorMock.preExecute("setTimestamp", 1, timestamp)).thenReturn(intercepted(null));
        preparedStatementWrapper.setTimestamp(1, timestamp);
        verify(wrappedMock, never()).setTimestamp(1, timestamp);
        verify(interceptorMock, never()).postExecute(eq("setTimestamp"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetTimestampWithCalendar_delegatesToWrapped() throws SQLException {
        Timestamp timestamp = new Timestamp(0);
        Calendar calendar = Calendar.getInstance();
        when(interceptorMock.preExecute("setTimestamp", 1, timestamp, calendar)).thenReturn(notIntercepted());
        preparedStatementWrapper.setTimestamp(1, timestamp, calendar);
        verify(wrappedMock).setTimestamp(1, timestamp, calendar);
        verify(interceptorMock).postExecute(eq("setTimestamp"), isNull(), anyLong(), anyLong(), eq(1), eq(timestamp), eq(calendar));
    }

    @Test
    void testSetTimestampWithCalendar_shortCircuitsWhenPreIntercepted() throws SQLException {
        Timestamp timestamp = new Timestamp(0);
        Calendar calendar = Calendar.getInstance();
        when(interceptorMock.preExecute("setTimestamp", 1, timestamp, calendar)).thenReturn(intercepted(null));
        preparedStatementWrapper.setTimestamp(1, timestamp, calendar);
        verify(wrappedMock, never()).setTimestamp(1, timestamp, calendar);
        verify(interceptorMock, never()).postExecute(eq("setTimestamp"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetURL_delegatesToWrapped() throws SQLException {
        URL url = sampleUrl();
        when(interceptorMock.preExecute("setURL", 1, url)).thenReturn(notIntercepted());
        preparedStatementWrapper.setURL(1, url);
        verify(wrappedMock).setURL(1, url);
        verify(interceptorMock).postExecute(eq("setURL"), isNull(), anyLong(), anyLong(), eq(1), eq(url));
    }

    @Test
    void testSetURL_shortCircuitsWhenPreIntercepted() throws SQLException {
        URL url = sampleUrl();
        when(interceptorMock.preExecute("setURL", 1, url)).thenReturn(intercepted(null));
        preparedStatementWrapper.setURL(1, url);
        verify(wrappedMock, never()).setURL(1, url);
        verify(interceptorMock, never()).postExecute(eq("setURL"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetTimeWithCalendar_delegatesToWrapped() throws SQLException {
        Time time = new Time(0);
        Calendar calendar = Calendar.getInstance();
        when(interceptorMock.preExecute("setTime", 1, time, calendar)).thenReturn(notIntercepted());
        preparedStatementWrapper.setTime(1, time, calendar);
        verify(wrappedMock).setTime(1, time, calendar);
        verify(interceptorMock).postExecute(eq("setTime"), isNull(), anyLong(), anyLong(), eq(1), eq(time), eq(calendar));
    }

    @Test
    void testSetTimeWithCalendar_shortCircuitsWhenPreIntercepted() throws SQLException {
        Time time = new Time(0);
        Calendar calendar = Calendar.getInstance();
        when(interceptorMock.preExecute("setTime", 1, time, calendar)).thenReturn(intercepted(null));
        preparedStatementWrapper.setTime(1, time, calendar);
        verify(wrappedMock, never()).setTime(1, time, calendar);
        verify(interceptorMock, never()).postExecute(eq("setTime"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetTime_delegatesToWrapped() throws SQLException {
        Time time = new Time(0);
        when(interceptorMock.preExecute("setTime", 1, time)).thenReturn(notIntercepted());
        preparedStatementWrapper.setTime(1, time);
        verify(wrappedMock).setTime(1, time);
        verify(interceptorMock).postExecute(eq("setTime"), isNull(), anyLong(), anyLong(), eq(1), eq(time));
    }

    @Test
    void testSetTime_shortCircuitsWhenPreIntercepted() throws SQLException {
        Time time = new Time(0);
        when(interceptorMock.preExecute("setTime", 1, time)).thenReturn(intercepted(null));
        preparedStatementWrapper.setTime(1, time);
        verify(wrappedMock, never()).setTime(1, time);
        verify(interceptorMock, never()).postExecute(eq("setTime"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testExecute_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("execute")).thenReturn(notIntercepted());
        when(wrappedMock.execute()).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.execute());
        verify(wrappedMock).execute();
    }

    @Test
    void testExecute_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute")).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.execute());
        verify(wrappedMock, never()).execute();
    }

    @Test
    void testExecute_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute")).thenReturn(notIntercepted());
        when(wrappedMock.execute()).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.execute());
    }

    @Test
    void testGetMetaData_delegatesToWrapped() throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(interceptorMock.preExecute("getMetaData")).thenReturn(notIntercepted());
        when(wrappedMock.getMetaData()).thenReturn(metaData);
        when(interceptorMock.postExecute(eq("getMetaData"), eq(metaData), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(metaData, preparedStatementWrapper.getMetaData());
        verify(wrappedMock).getMetaData();
    }

    @Test
    void testGetMetaData_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(interceptorMock.preExecute("getMetaData")).thenReturn(intercepted(metaData));
        assertSame(metaData, preparedStatementWrapper.getMetaData());
        verify(wrappedMock, never()).getMetaData();
    }

    @Test
    void testGetMetaData_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        ResultSetMetaData postMetaData = mock(ResultSetMetaData.class);
        when(interceptorMock.preExecute("getMetaData")).thenReturn(notIntercepted());
        when(wrappedMock.getMetaData()).thenReturn(metaData);
        when(interceptorMock.postExecute(eq("getMetaData"), eq(metaData), anyLong(), anyLong())).thenReturn(intercepted(postMetaData));
        assertSame(postMetaData, preparedStatementWrapper.getMetaData());
    }

    @Test
    void testExecuteQuery_delegatesToWrapped() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery")).thenReturn(notIntercepted());
        when(wrappedMock.executeQuery()).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("executeQuery"), eq(resultSet), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(resultSet, preparedStatementWrapper.executeQuery());
        verify(wrappedMock).executeQuery();
    }

    @Test
    void testExecuteQuery_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery")).thenReturn(intercepted(resultSet));
        assertSame(resultSet, preparedStatementWrapper.executeQuery());
        verify(wrappedMock, never()).executeQuery();
    }

    @Test
    void testExecuteQuery_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSet postResultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery")).thenReturn(notIntercepted());
        when(wrappedMock.executeQuery()).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("executeQuery"), eq(resultSet), anyLong(), anyLong())).thenReturn(intercepted(postResultSet));
        assertSame(postResultSet, preparedStatementWrapper.executeQuery());
    }

    @Test
    void testExecuteUpdate_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate")).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate()).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(7, preparedStatementWrapper.executeUpdate());
        verify(wrappedMock).executeUpdate();
    }

    @Test
    void testExecuteUpdate_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate")).thenReturn(intercepted(9));
        assertEquals(9, preparedStatementWrapper.executeUpdate());
        verify(wrappedMock, never()).executeUpdate();
    }

    @Test
    void testExecuteUpdate_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate")).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate()).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong())).thenReturn(intercepted(11));
        assertEquals(11, preparedStatementWrapper.executeUpdate());
    }

    @Test
    void testAddBatch_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("addBatch")).thenReturn(notIntercepted());
        preparedStatementWrapper.addBatch();
        verify(wrappedMock).addBatch();
        verify(interceptorMock).postExecute(eq("addBatch"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testAddBatch_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("addBatch")).thenReturn(intercepted(null));
        preparedStatementWrapper.addBatch();
        verify(wrappedMock, never()).addBatch();
        verify(interceptorMock, never()).postExecute(eq("addBatch"), any(), anyLong(), anyLong());
    }

    @Test
    void testSetNull_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setNull", 1, 4)).thenReturn(notIntercepted());
        preparedStatementWrapper.setNull(1, 4);
        verify(wrappedMock).setNull(1, 4);
        verify(interceptorMock).postExecute(eq("setNull"), isNull(), anyLong(), anyLong(), eq(1), eq(4));
    }

    @Test
    void testSetNull_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setNull", 1, 4)).thenReturn(intercepted(null));
        preparedStatementWrapper.setNull(1, 4);
        verify(wrappedMock, never()).setNull(1, 4);
        verify(interceptorMock, never()).postExecute(eq("setNull"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetNullWithTypeName_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setNull", 1, 4, "VARCHAR")).thenReturn(notIntercepted());
        preparedStatementWrapper.setNull(1, 4, "VARCHAR");
        verify(wrappedMock).setNull(1, 4, "VARCHAR");
        verify(interceptorMock).postExecute(eq("setNull"), isNull(), anyLong(), anyLong(), eq(1), eq(4), eq("VARCHAR"));
    }

    @Test
    void testSetNullWithTypeName_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setNull", 1, 4, "VARCHAR")).thenReturn(intercepted(null));
        preparedStatementWrapper.setNull(1, 4, "VARCHAR");
        verify(wrappedMock, never()).setNull(1, 4, "VARCHAR");
        verify(interceptorMock, never()).postExecute(eq("setNull"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetBigDecimal_delegatesToWrapped() throws SQLException {
        BigDecimal value = new BigDecimal("1.5");
        when(interceptorMock.preExecute("setBigDecimal", 1, value)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBigDecimal(1, value);
        verify(wrappedMock).setBigDecimal(1, value);
        verify(interceptorMock).postExecute(eq("setBigDecimal"), isNull(), anyLong(), anyLong(), eq(1), eq(value));
    }

    @Test
    void testSetBigDecimal_shortCircuitsWhenPreIntercepted() throws SQLException {
        BigDecimal value = new BigDecimal("1.5");
        when(interceptorMock.preExecute("setBigDecimal", 1, value)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBigDecimal(1, value);
        verify(wrappedMock, never()).setBigDecimal(1, value);
        verify(interceptorMock, never()).postExecute(eq("setBigDecimal"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setString", 1, "value")).thenReturn(notIntercepted());
        preparedStatementWrapper.setString(1, "value");
        verify(wrappedMock).setString(1, "value");
        verify(interceptorMock).postExecute(eq("setString"), isNull(), anyLong(), anyLong(), eq(1), eq("value"));
    }

    @Test
    void testSetString_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setString", 1, "value")).thenReturn(intercepted(null));
        preparedStatementWrapper.setString(1, "value");
        verify(wrappedMock, never()).setString(1, "value");
        verify(interceptorMock, never()).postExecute(eq("setString"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetBytes_delegatesToWrapped() throws SQLException {
        byte[] bytes = new byte[] { 1, 2 };
        when(interceptorMock.preExecute("setBytes", 1, bytes)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBytes(1, bytes);
        verify(wrappedMock).setBytes(1, bytes);
        verify(interceptorMock).postExecute(eq("setBytes"), isNull(), anyLong(), anyLong(), eq(1), eq(bytes));
    }

    @Test
    void testSetBytes_shortCircuitsWhenPreIntercepted() throws SQLException {
        byte[] bytes = new byte[] { 1, 2 };
        when(interceptorMock.preExecute("setBytes", 1, bytes)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBytes(1, bytes);
        verify(wrappedMock, never()).setBytes(1, bytes);
        verify(interceptorMock, never()).postExecute(eq("setBytes"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetDate_delegatesToWrapped() throws SQLException {
        Date date = new Date(0);
        when(interceptorMock.preExecute("setDate", 1, date)).thenReturn(notIntercepted());
        preparedStatementWrapper.setDate(1, date);
        verify(wrappedMock).setDate(1, date);
        verify(interceptorMock).postExecute(eq("setDate"), isNull(), anyLong(), anyLong(), eq(1), eq(date));
    }

    @Test
    void testSetDate_shortCircuitsWhenPreIntercepted() throws SQLException {
        Date date = new Date(0);
        when(interceptorMock.preExecute("setDate", 1, date)).thenReturn(intercepted(null));
        preparedStatementWrapper.setDate(1, date);
        verify(wrappedMock, never()).setDate(1, date);
        verify(interceptorMock, never()).postExecute(eq("setDate"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetDateWithCalendar_delegatesToWrapped() throws SQLException {
        Date date = new Date(0);
        Calendar calendar = Calendar.getInstance();
        when(interceptorMock.preExecute("setDate", 1, date, calendar)).thenReturn(notIntercepted());
        preparedStatementWrapper.setDate(1, date, calendar);
        verify(wrappedMock).setDate(1, date, calendar);
        verify(interceptorMock).postExecute(eq("setDate"), isNull(), anyLong(), anyLong(), eq(1), eq(date), eq(calendar));
    }

    @Test
    void testSetDateWithCalendar_shortCircuitsWhenPreIntercepted() throws SQLException {
        Date date = new Date(0);
        Calendar calendar = Calendar.getInstance();
        when(interceptorMock.preExecute("setDate", 1, date, calendar)).thenReturn(intercepted(null));
        preparedStatementWrapper.setDate(1, date, calendar);
        verify(wrappedMock, never()).setDate(1, date, calendar);
        verify(interceptorMock, never()).postExecute(eq("setDate"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetAsciiStream_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setAsciiStream", 1, in)).thenReturn(notIntercepted());
        preparedStatementWrapper.setAsciiStream(1, in);
        verify(wrappedMock).setAsciiStream(1, in);
        verify(interceptorMock).postExecute(eq("setAsciiStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in));
    }

    @Test
    void testSetAsciiStream_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setAsciiStream", 1, in)).thenReturn(intercepted(null));
        preparedStatementWrapper.setAsciiStream(1, in);
        verify(wrappedMock, never()).setAsciiStream(1, in);
        verify(interceptorMock, never()).postExecute(eq("setAsciiStream"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetAsciiStreamWithLong_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setAsciiStream", 1, in, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setAsciiStream(1, in, 10L);
        verify(wrappedMock).setAsciiStream(1, in, 10L);
        verify(interceptorMock).postExecute(eq("setAsciiStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in), eq(10L));
    }

    @Test
    void testSetAsciiStreamWithLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setAsciiStream", 1, in, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setAsciiStream(1, in, 10L);
        verify(wrappedMock, never()).setAsciiStream(1, in, 10L);
        verify(interceptorMock, never()).postExecute(eq("setAsciiStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetAsciiStreamWithInt_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setAsciiStream", 1, in, 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setAsciiStream(1, in, 10);
        verify(wrappedMock).setAsciiStream(1, in, 10);
        verify(interceptorMock).postExecute(eq("setAsciiStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in), eq(10));
    }

    @Test
    void testSetAsciiStreamWithInt_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setAsciiStream", 1, in, 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setAsciiStream(1, in, 10);
        verify(wrappedMock, never()).setAsciiStream(1, in, 10);
        verify(interceptorMock, never()).postExecute(eq("setAsciiStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("deprecation")
    void testSetUnicodeStream_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setUnicodeStream", 1, in, 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setUnicodeStream(1, in, 10);
        verify(wrappedMock).setUnicodeStream(1, in, 10);
        verify(interceptorMock).postExecute(eq("setUnicodeStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in), eq(10));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testSetUnicodeStream_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setUnicodeStream", 1, in, 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setUnicodeStream(1, in, 10);
        verify(wrappedMock, never()).setUnicodeStream(1, in, 10);
        verify(interceptorMock, never()).postExecute(eq("setUnicodeStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetBinaryStreamWithLong_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBinaryStream", 1, in, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBinaryStream(1, in, 10L);
        verify(wrappedMock).setBinaryStream(1, in, 10L);
        verify(interceptorMock).postExecute(eq("setBinaryStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in), eq(10L));
    }

    @Test
    void testSetBinaryStreamWithLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBinaryStream", 1, in, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBinaryStream(1, in, 10L);
        verify(wrappedMock, never()).setBinaryStream(1, in, 10L);
        verify(interceptorMock, never()).postExecute(eq("setBinaryStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetBinaryStreamWithInt_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBinaryStream", 1, in, 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBinaryStream(1, in, 10);
        verify(wrappedMock).setBinaryStream(1, in, 10);
        verify(interceptorMock).postExecute(eq("setBinaryStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in), eq(10));
    }

    @Test
    void testSetBinaryStreamWithInt_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBinaryStream", 1, in, 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBinaryStream(1, in, 10);
        verify(wrappedMock, never()).setBinaryStream(1, in, 10);
        verify(interceptorMock, never()).postExecute(eq("setBinaryStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetBinaryStream_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBinaryStream", 1, in)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBinaryStream(1, in);
        verify(wrappedMock).setBinaryStream(1, in);
        verify(interceptorMock).postExecute(eq("setBinaryStream"), isNull(), anyLong(), anyLong(), eq(1), eq(in));
    }

    @Test
    void testSetBinaryStream_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBinaryStream", 1, in)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBinaryStream(1, in);
        verify(wrappedMock, never()).setBinaryStream(1, in);
        verify(interceptorMock, never()).postExecute(eq("setBinaryStream"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testClearParameters_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("clearParameters")).thenReturn(notIntercepted());
        preparedStatementWrapper.clearParameters();
        verify(wrappedMock).clearParameters();
        verify(interceptorMock).postExecute(eq("clearParameters"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClearParameters_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("clearParameters")).thenReturn(intercepted(null));
        preparedStatementWrapper.clearParameters();
        verify(wrappedMock, never()).clearParameters();
        verify(interceptorMock, never()).postExecute(eq("clearParameters"), any(), anyLong(), anyLong());
    }

    @Test
    void testSetObjectWithTargetSqlTypeAndScale_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setObject", 1, "value", 4, 2)).thenReturn(notIntercepted());
        preparedStatementWrapper.setObject(1, "value", 4, 2);
        verify(wrappedMock).setObject(1, "value", 4, 2);
        verify(interceptorMock).postExecute(eq("setObject"), isNull(), anyLong(), anyLong(), eq(1), eq("value"), eq(4), eq(2));
    }

    @Test
    void testSetObjectWithTargetSqlTypeAndScale_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setObject", 1, "value", 4, 2)).thenReturn(intercepted(null));
        preparedStatementWrapper.setObject(1, "value", 4, 2);
        verify(wrappedMock, never()).setObject(1, "value", 4, 2);
        verify(interceptorMock, never()).postExecute(eq("setObject"), any(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void testSetObjectWithTargetSqlType_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setObject", 1, "value", 4)).thenReturn(notIntercepted());
        preparedStatementWrapper.setObject(1, "value", 4);
        verify(wrappedMock).setObject(1, "value", 4);
        verify(interceptorMock).postExecute(eq("setObject"), isNull(), anyLong(), anyLong(), eq(1), eq("value"), eq(4));
    }

    @Test
    void testSetObjectWithTargetSqlType_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setObject", 1, "value", 4)).thenReturn(intercepted(null));
        preparedStatementWrapper.setObject(1, "value", 4);
        verify(wrappedMock, never()).setObject(1, "value", 4);
        verify(interceptorMock, never()).postExecute(eq("setObject"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetObject_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setObject", 1, "value")).thenReturn(notIntercepted());
        preparedStatementWrapper.setObject(1, "value");
        verify(wrappedMock).setObject(1, "value");
        verify(interceptorMock).postExecute(eq("setObject"), isNull(), anyLong(), anyLong(), eq(1), eq("value"));
    }

    @Test
    void testSetObject_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setObject", 1, "value")).thenReturn(intercepted(null));
        preparedStatementWrapper.setObject(1, "value");
        verify(wrappedMock, never()).setObject(1, "value");
        verify(interceptorMock, never()).postExecute(eq("setObject"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetCharacterStreamWithLong_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setCharacterStream", 1, reader, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setCharacterStream(1, reader, 10L);
        verify(wrappedMock).setCharacterStream(1, reader, 10L);
        verify(interceptorMock).postExecute(eq("setCharacterStream"), isNull(), anyLong(), anyLong(), eq(1), eq(reader), eq(10L));
    }

    @Test
    void testSetCharacterStreamWithLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setCharacterStream", 1, reader, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setCharacterStream(1, reader, 10L);
        verify(wrappedMock, never()).setCharacterStream(1, reader, 10L);
        verify(interceptorMock, never()).postExecute(eq("setCharacterStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetCharacterStreamWithInt_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setCharacterStream", 1, reader, 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setCharacterStream(1, reader, 10);
        verify(wrappedMock).setCharacterStream(1, reader, 10);
        verify(interceptorMock).postExecute(eq("setCharacterStream"), isNull(), anyLong(), anyLong(), eq(1), eq(reader), eq(10));
    }

    @Test
    void testSetCharacterStreamWithInt_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setCharacterStream", 1, reader, 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setCharacterStream(1, reader, 10);
        verify(wrappedMock, never()).setCharacterStream(1, reader, 10);
        verify(interceptorMock, never()).postExecute(eq("setCharacterStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetCharacterStream_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setCharacterStream", 1, reader)).thenReturn(notIntercepted());
        preparedStatementWrapper.setCharacterStream(1, reader);
        verify(wrappedMock).setCharacterStream(1, reader);
        verify(interceptorMock).postExecute(eq("setCharacterStream"), isNull(), anyLong(), anyLong(), eq(1), eq(reader));
    }

    @Test
    void testSetCharacterStream_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setCharacterStream", 1, reader)).thenReturn(intercepted(null));
        preparedStatementWrapper.setCharacterStream(1, reader);
        verify(wrappedMock, never()).setCharacterStream(1, reader);
        verify(interceptorMock, never()).postExecute(eq("setCharacterStream"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetRef_delegatesToWrapped() throws SQLException {
        Ref ref = mock(Ref.class);
        when(interceptorMock.preExecute("setRef", 1, ref)).thenReturn(notIntercepted());
        preparedStatementWrapper.setRef(1, ref);
        verify(wrappedMock).setRef(1, ref);
        verify(interceptorMock).postExecute(eq("setRef"), isNull(), anyLong(), anyLong(), eq(1), eq(ref));
    }

    @Test
    void testSetRef_shortCircuitsWhenPreIntercepted() throws SQLException {
        Ref ref = mock(Ref.class);
        when(interceptorMock.preExecute("setRef", 1, ref)).thenReturn(intercepted(null));
        preparedStatementWrapper.setRef(1, ref);
        verify(wrappedMock, never()).setRef(1, ref);
        verify(interceptorMock, never()).postExecute(eq("setRef"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetBlobWithLong_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBlob", 1, in, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBlob(1, in, 10L);
        verify(wrappedMock).setBlob(1, in, 10L);
        verify(interceptorMock).postExecute(eq("setBlob"), isNull(), anyLong(), anyLong(), eq(1), eq(in), eq(10L));
    }

    @Test
    void testSetBlobWithLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBlob", 1, in, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBlob(1, in, 10L);
        verify(wrappedMock, never()).setBlob(1, in, 10L);
        verify(interceptorMock, never()).postExecute(eq("setBlob"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetBlobWithInputStream_delegatesToWrapped() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBlob", 1, in)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBlob(1, in);
        verify(wrappedMock).setBlob(1, in);
        verify(interceptorMock).postExecute(eq("setBlob"), isNull(), anyLong(), anyLong(), eq(1), eq(in));
    }

    @Test
    void testSetBlobWithInputStream_shortCircuitsWhenPreIntercepted() throws SQLException {
        InputStream in = mock(InputStream.class);
        when(interceptorMock.preExecute("setBlob", 1, in)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBlob(1, in);
        verify(wrappedMock, never()).setBlob(1, in);
        verify(interceptorMock, never()).postExecute(eq("setBlob"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetBlobWithBlob_delegatesToWrapped() throws SQLException {
        Blob blob = mock(Blob.class);
        when(interceptorMock.preExecute("setBlob", 1, blob)).thenReturn(notIntercepted());
        preparedStatementWrapper.setBlob(1, blob);
        verify(wrappedMock).setBlob(1, blob);
        verify(interceptorMock).postExecute(eq("setBlob"), isNull(), anyLong(), anyLong(), eq(1), eq(blob));
    }

    @Test
    void testSetBlobWithBlob_shortCircuitsWhenPreIntercepted() throws SQLException {
        Blob blob = mock(Blob.class);
        when(interceptorMock.preExecute("setBlob", 1, blob)).thenReturn(intercepted(null));
        preparedStatementWrapper.setBlob(1, blob);
        verify(wrappedMock, never()).setBlob(1, blob);
        verify(interceptorMock, never()).postExecute(eq("setBlob"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetClobWithReader_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setClob", 1, reader)).thenReturn(notIntercepted());
        preparedStatementWrapper.setClob(1, reader);
        verify(wrappedMock).setClob(1, reader);
        verify(interceptorMock).postExecute(eq("setClob"), isNull(), anyLong(), anyLong(), eq(1), eq(reader));
    }

    @Test
    void testSetClobWithReader_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setClob", 1, reader)).thenReturn(intercepted(null));
        preparedStatementWrapper.setClob(1, reader);
        verify(wrappedMock, never()).setClob(1, reader);
        verify(interceptorMock, never()).postExecute(eq("setClob"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetClobWithReaderAndLong_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setClob", 1, reader, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setClob(1, reader, 10L);
        verify(wrappedMock).setClob(1, reader, 10L);
        verify(interceptorMock).postExecute(eq("setClob"), isNull(), anyLong(), anyLong(), eq(1), eq(reader), eq(10L));
    }

    @Test
    void testSetClobWithReaderAndLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setClob", 1, reader, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setClob(1, reader, 10L);
        verify(wrappedMock, never()).setClob(1, reader, 10L);
        verify(interceptorMock, never()).postExecute(eq("setClob"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetClobWithClob_delegatesToWrapped() throws SQLException {
        Clob clob = mock(Clob.class);
        when(interceptorMock.preExecute("setClob", 1, clob)).thenReturn(notIntercepted());
        preparedStatementWrapper.setClob(1, clob);
        verify(wrappedMock).setClob(1, clob);
        verify(interceptorMock).postExecute(eq("setClob"), isNull(), anyLong(), anyLong(), eq(1), eq(clob));
    }

    @Test
    void testSetClobWithClob_shortCircuitsWhenPreIntercepted() throws SQLException {
        Clob clob = mock(Clob.class);
        when(interceptorMock.preExecute("setClob", 1, clob)).thenReturn(intercepted(null));
        preparedStatementWrapper.setClob(1, clob);
        verify(wrappedMock, never()).setClob(1, clob);
        verify(interceptorMock, never()).postExecute(eq("setClob"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetArray_delegatesToWrapped() throws SQLException {
        Array array = mock(Array.class);
        when(interceptorMock.preExecute("setArray", 1, array)).thenReturn(notIntercepted());
        preparedStatementWrapper.setArray(1, array);
        verify(wrappedMock).setArray(1, array);
        verify(interceptorMock).postExecute(eq("setArray"), isNull(), anyLong(), anyLong(), eq(1), eq(array));
    }

    @Test
    void testSetArray_shortCircuitsWhenPreIntercepted() throws SQLException {
        Array array = mock(Array.class);
        when(interceptorMock.preExecute("setArray", 1, array)).thenReturn(intercepted(null));
        preparedStatementWrapper.setArray(1, array);
        verify(wrappedMock, never()).setArray(1, array);
        verify(interceptorMock, never()).postExecute(eq("setArray"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testGetParameterMetaData_delegatesToWrapped() throws SQLException {
        ParameterMetaData metaData = mock(ParameterMetaData.class);
        when(interceptorMock.preExecute("getParameterMetaData")).thenReturn(notIntercepted());
        when(wrappedMock.getParameterMetaData()).thenReturn(metaData);
        when(interceptorMock.postExecute(eq("getParameterMetaData"), eq(metaData), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(metaData, preparedStatementWrapper.getParameterMetaData());
        verify(wrappedMock).getParameterMetaData();
    }

    @Test
    void testGetParameterMetaData_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ParameterMetaData metaData = mock(ParameterMetaData.class);
        when(interceptorMock.preExecute("getParameterMetaData")).thenReturn(intercepted(metaData));
        assertSame(metaData, preparedStatementWrapper.getParameterMetaData());
        verify(wrappedMock, never()).getParameterMetaData();
    }

    @Test
    void testGetParameterMetaData_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ParameterMetaData metaData = mock(ParameterMetaData.class);
        ParameterMetaData postMetaData = mock(ParameterMetaData.class);
        when(interceptorMock.preExecute("getParameterMetaData")).thenReturn(notIntercepted());
        when(wrappedMock.getParameterMetaData()).thenReturn(metaData);
        when(interceptorMock.postExecute(eq("getParameterMetaData"), eq(metaData), anyLong(), anyLong())).thenReturn(intercepted(postMetaData));
        assertSame(postMetaData, preparedStatementWrapper.getParameterMetaData());
    }

    @Test
    void testSetRowId_delegatesToWrapped() throws SQLException {
        RowId rowId = mock(RowId.class);
        when(interceptorMock.preExecute("setRowId", 1, rowId)).thenReturn(notIntercepted());
        preparedStatementWrapper.setRowId(1, rowId);
        verify(wrappedMock).setRowId(1, rowId);
        verify(interceptorMock).postExecute(eq("setRowId"), isNull(), anyLong(), anyLong(), eq(1), eq(rowId));
    }

    @Test
    void testSetRowId_shortCircuitsWhenPreIntercepted() throws SQLException {
        RowId rowId = mock(RowId.class);
        when(interceptorMock.preExecute("setRowId", 1, rowId)).thenReturn(intercepted(null));
        preparedStatementWrapper.setRowId(1, rowId);
        verify(wrappedMock, never()).setRowId(1, rowId);
        verify(interceptorMock, never()).postExecute(eq("setRowId"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetNString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setNString", 1, "value")).thenReturn(notIntercepted());
        preparedStatementWrapper.setNString(1, "value");
        verify(wrappedMock).setNString(1, "value");
        verify(interceptorMock).postExecute(eq("setNString"), isNull(), anyLong(), anyLong(), eq(1), eq("value"));
    }

    @Test
    void testSetNString_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setNString", 1, "value")).thenReturn(intercepted(null));
        preparedStatementWrapper.setNString(1, "value");
        verify(wrappedMock, never()).setNString(1, "value");
        verify(interceptorMock, never()).postExecute(eq("setNString"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetNCharacterStream_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNCharacterStream", 1, reader)).thenReturn(notIntercepted());
        preparedStatementWrapper.setNCharacterStream(1, reader);
        verify(wrappedMock).setNCharacterStream(1, reader);
        verify(interceptorMock).postExecute(eq("setNCharacterStream"), isNull(), anyLong(), anyLong(), eq(1), eq(reader));
    }

    @Test
    void testSetNCharacterStream_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNCharacterStream", 1, reader)).thenReturn(intercepted(null));
        preparedStatementWrapper.setNCharacterStream(1, reader);
        verify(wrappedMock, never()).setNCharacterStream(1, reader);
        verify(interceptorMock, never()).postExecute(eq("setNCharacterStream"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetNCharacterStreamWithLong_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNCharacterStream", 1, reader, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setNCharacterStream(1, reader, 10L);
        verify(wrappedMock).setNCharacterStream(1, reader, 10L);
        verify(interceptorMock).postExecute(eq("setNCharacterStream"), isNull(), anyLong(), anyLong(), eq(1), eq(reader), eq(10L));
    }

    @Test
    void testSetNCharacterStreamWithLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNCharacterStream", 1, reader, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setNCharacterStream(1, reader, 10L);
        verify(wrappedMock, never()).setNCharacterStream(1, reader, 10L);
        verify(interceptorMock, never()).postExecute(eq("setNCharacterStream"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetNClobWithReaderAndLong_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNClob", 1, reader, 10L)).thenReturn(notIntercepted());
        preparedStatementWrapper.setNClob(1, reader, 10L);
        verify(wrappedMock).setNClob(1, reader, 10L);
        verify(interceptorMock).postExecute(eq("setNClob"), isNull(), anyLong(), anyLong(), eq(1), eq(reader), eq(10L));
    }

    @Test
    void testSetNClobWithReaderAndLong_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNClob", 1, reader, 10L)).thenReturn(intercepted(null));
        preparedStatementWrapper.setNClob(1, reader, 10L);
        verify(wrappedMock, never()).setNClob(1, reader, 10L);
        verify(interceptorMock, never()).postExecute(eq("setNClob"), any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void testSetNClobWithNClob_delegatesToWrapped() throws SQLException {
        NClob nclob = mock(NClob.class);
        when(interceptorMock.preExecute("setNClob", 1, nclob)).thenReturn(notIntercepted());
        preparedStatementWrapper.setNClob(1, nclob);
        verify(wrappedMock).setNClob(1, nclob);
        verify(interceptorMock).postExecute(eq("setNClob"), isNull(), anyLong(), anyLong(), eq(1), eq(nclob));
    }

    @Test
    void testSetNClobWithNClob_shortCircuitsWhenPreIntercepted() throws SQLException {
        NClob nclob = mock(NClob.class);
        when(interceptorMock.preExecute("setNClob", 1, nclob)).thenReturn(intercepted(null));
        preparedStatementWrapper.setNClob(1, nclob);
        verify(wrappedMock, never()).setNClob(1, nclob);
        verify(interceptorMock, never()).postExecute(eq("setNClob"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetNClobWithReader_delegatesToWrapped() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNClob", 1, reader)).thenReturn(notIntercepted());
        preparedStatementWrapper.setNClob(1, reader);
        verify(wrappedMock).setNClob(1, reader);
        verify(interceptorMock).postExecute(eq("setNClob"), isNull(), anyLong(), anyLong(), eq(1), eq(reader));
    }

    @Test
    void testSetNClobWithReader_shortCircuitsWhenPreIntercepted() throws SQLException {
        Reader reader = mock(Reader.class);
        when(interceptorMock.preExecute("setNClob", 1, reader)).thenReturn(intercepted(null));
        preparedStatementWrapper.setNClob(1, reader);
        verify(wrappedMock, never()).setNClob(1, reader);
        verify(interceptorMock, never()).postExecute(eq("setNClob"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testSetSQLXML_delegatesToWrapped() throws SQLException {
        SQLXML sqlxml = mock(SQLXML.class);
        when(interceptorMock.preExecute("setSQLXML", 1, sqlxml)).thenReturn(notIntercepted());
        preparedStatementWrapper.setSQLXML(1, sqlxml);
        verify(wrappedMock).setSQLXML(1, sqlxml);
        verify(interceptorMock).postExecute(eq("setSQLXML"), isNull(), anyLong(), anyLong(), eq(1), eq(sqlxml));
    }

    @Test
    void testSetSQLXML_shortCircuitsWhenPreIntercepted() throws SQLException {
        SQLXML sqlxml = mock(SQLXML.class);
        when(interceptorMock.preExecute("setSQLXML", 1, sqlxml)).thenReturn(intercepted(null));
        preparedStatementWrapper.setSQLXML(1, sqlxml);
        verify(wrappedMock, never()).setSQLXML(1, sqlxml);
        verify(interceptorMock, never()).postExecute(eq("setSQLXML"), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void testClose_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("close")).thenReturn(notIntercepted());
        preparedStatementWrapper.close();
        verify(wrappedMock).close();
        verify(interceptorMock).postExecute(eq("close"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClose_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("close")).thenReturn(intercepted(null));
        preparedStatementWrapper.close();
        verify(wrappedMock, never()).close();
        verify(interceptorMock, never()).postExecute(eq("close"), any(), anyLong(), anyLong());
    }

    @Test
    void testGetConnection_delegatesToWrapped() throws SQLException {
        Connection connection = mock(Connection.class);
        when(interceptorMock.preExecute("getConnection")).thenReturn(notIntercepted());
        when(wrappedMock.getConnection()).thenReturn(connection);
        when(interceptorMock.postExecute(eq("getConnection"), eq(connection), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(connection, preparedStatementWrapper.getConnection());
        verify(wrappedMock).getConnection();
    }

    @Test
    void testGetConnection_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Connection connection = mock(Connection.class);
        when(interceptorMock.preExecute("getConnection")).thenReturn(intercepted(connection));
        assertSame(connection, preparedStatementWrapper.getConnection());
        verify(wrappedMock, never()).getConnection();
    }

    @Test
    void testGetConnection_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        Connection connection = mock(Connection.class);
        Connection postConnection = mock(Connection.class);
        when(interceptorMock.preExecute("getConnection")).thenReturn(notIntercepted());
        when(wrappedMock.getConnection()).thenReturn(connection);
        when(interceptorMock.postExecute(eq("getConnection"), eq(connection), anyLong(), anyLong())).thenReturn(intercepted(postConnection));
        assertSame(postConnection, preparedStatementWrapper.getConnection());
    }

    @Test
    void testExecuteWithStringAndInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("execute", SQL, 1)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL, 1)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL), eq(1))).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.execute(SQL, 1));
        verify(wrappedMock).execute(SQL, 1);
    }

    @Test
    void testExecuteWithStringAndInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SQL, 1)).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.execute(SQL, 1));
        verify(wrappedMock, never()).execute(SQL, 1);
    }

    @Test
    void testExecuteWithStringAndInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SQL, 1)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL, 1)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL), eq(1))).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.execute(SQL, 1));
    }

    @Test
    void testExecuteWithString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("execute", SQL)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL))).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.execute(SQL));
        verify(wrappedMock).execute(SQL);
    }

    @Test
    void testExecuteWithString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SQL)).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.execute(SQL));
        verify(wrappedMock, never()).execute(SQL);
    }

    @Test
    void testExecuteWithString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("execute", SQL)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL))).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.execute(SQL));
    }

    @Test
    void testExecuteWithStringAndColumnNames_delegatesToWrapped() throws SQLException {
        String[] columnNames = { "a", "b" };
        when(interceptorMock.preExecute("execute", SQL, columnNames)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL, columnNames)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL), eq(columnNames))).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.execute(SQL, columnNames));
        verify(wrappedMock).execute(SQL, columnNames);
    }

    @Test
    void testExecuteWithStringAndColumnNames_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        String[] columnNames = { "a", "b" };
        when(interceptorMock.preExecute("execute", SQL, columnNames)).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.execute(SQL, columnNames));
        verify(wrappedMock, never()).execute(SQL, columnNames);
    }

    @Test
    void testExecuteWithStringAndColumnNames_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        String[] columnNames = { "a", "b" };
        when(interceptorMock.preExecute("execute", SQL, columnNames)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL, columnNames)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL), eq(columnNames))).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.execute(SQL, columnNames));
    }

    @Test
    void testExecuteWithStringAndColumnIndexes_delegatesToWrapped() throws SQLException {
        int[] columnIndexes = { 1, 2 };
        when(interceptorMock.preExecute("execute", SQL, columnIndexes)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL, columnIndexes)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL), eq(columnIndexes))).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.execute(SQL, columnIndexes));
        verify(wrappedMock).execute(SQL, columnIndexes);
    }

    @Test
    void testExecuteWithStringAndColumnIndexes_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] columnIndexes = { 1, 2 };
        when(interceptorMock.preExecute("execute", SQL, columnIndexes)).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.execute(SQL, columnIndexes));
        verify(wrappedMock, never()).execute(SQL, columnIndexes);
    }

    @Test
    void testExecuteWithStringAndColumnIndexes_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] columnIndexes = { 1, 2 };
        when(interceptorMock.preExecute("execute", SQL, columnIndexes)).thenReturn(notIntercepted());
        when(wrappedMock.execute(SQL, columnIndexes)).thenReturn(true);
        when(interceptorMock.postExecute(eq("execute"), eq(true), anyLong(), anyLong(), eq(SQL), eq(columnIndexes))).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.execute(SQL, columnIndexes));
    }

    @Test
    void testIsClosed_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(notIntercepted());
        when(wrappedMock.isClosed()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isClosed"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.isClosed());
        verify(wrappedMock).isClosed();
    }

    @Test
    void testIsClosed_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.isClosed());
        verify(wrappedMock, never()).isClosed();
    }

    @Test
    void testIsClosed_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isClosed")).thenReturn(notIntercepted());
        when(wrappedMock.isClosed()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isClosed"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.isClosed());
    }

    @Test
    void testGetWarnings_delegatesToWrapped() throws SQLException {
        SQLWarning warning = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(notIntercepted());
        when(wrappedMock.getWarnings()).thenReturn(warning);
        when(interceptorMock.postExecute(eq("getWarnings"), eq(warning), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(warning, preparedStatementWrapper.getWarnings());
        verify(wrappedMock).getWarnings();
    }

    @Test
    void testGetWarnings_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        SQLWarning warning = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(intercepted(warning));
        assertSame(warning, preparedStatementWrapper.getWarnings());
        verify(wrappedMock, never()).getWarnings();
    }

    @Test
    void testGetWarnings_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        SQLWarning warning = mock(SQLWarning.class);
        SQLWarning postWarning = mock(SQLWarning.class);
        when(interceptorMock.preExecute("getWarnings")).thenReturn(notIntercepted());
        when(wrappedMock.getWarnings()).thenReturn(warning);
        when(interceptorMock.postExecute(eq("getWarnings"), eq(warning), anyLong(), anyLong())).thenReturn(intercepted(postWarning));
        assertSame(postWarning, preparedStatementWrapper.getWarnings());
    }

    @Test
    void testClearWarnings_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("clearWarnings")).thenReturn(notIntercepted());
        preparedStatementWrapper.clearWarnings();
        verify(wrappedMock).clearWarnings();
        verify(interceptorMock).postExecute(eq("clearWarnings"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClearWarnings_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("clearWarnings")).thenReturn(intercepted(null));
        preparedStatementWrapper.clearWarnings();
        verify(wrappedMock, never()).clearWarnings();
        verify(interceptorMock, never()).postExecute(eq("clearWarnings"), any(), anyLong(), anyLong());
    }

    @Test
    void testExecuteQueryWithString_delegatesToWrapped() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery", SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeQuery(SQL)).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("executeQuery"), eq(resultSet), anyLong(), anyLong(), eq(SQL))).thenReturn(notIntercepted());
        assertSame(resultSet, preparedStatementWrapper.executeQuery(SQL));
        verify(wrappedMock).executeQuery(SQL);
    }

    @Test
    void testExecuteQueryWithString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery", SQL)).thenReturn(intercepted(resultSet));
        assertSame(resultSet, preparedStatementWrapper.executeQuery(SQL));
        verify(wrappedMock, never()).executeQuery(SQL);
    }

    @Test
    void testExecuteQueryWithString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSet postResultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("executeQuery", SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeQuery(SQL)).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("executeQuery"), eq(resultSet), anyLong(), anyLong(), eq(SQL))).thenReturn(intercepted(postResultSet));
        assertSame(postResultSet, preparedStatementWrapper.executeQuery(SQL));
    }

    @Test
    void testExecuteUpdateWithStringAndInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", SQL, 1)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL, 1)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL), eq(1))).thenReturn(notIntercepted());
        assertEquals(7, preparedStatementWrapper.executeUpdate(SQL, 1));
        verify(wrappedMock).executeUpdate(SQL, 1);
    }

    @Test
    void testExecuteUpdateWithStringAndInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", SQL, 1)).thenReturn(intercepted(9));
        assertEquals(9, preparedStatementWrapper.executeUpdate(SQL, 1));
        verify(wrappedMock, never()).executeUpdate(SQL, 1);
    }

    @Test
    void testExecuteUpdateWithStringAndInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", SQL, 1)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL, 1)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL), eq(1))).thenReturn(intercepted(11));
        assertEquals(11, preparedStatementWrapper.executeUpdate(SQL, 1));
    }

    @Test
    void testExecuteUpdateWithStringAndColumnIndexes_delegatesToWrapped() throws SQLException {
        int[] columnIndexes = { 1, 2 };
        when(interceptorMock.preExecute("executeUpdate", SQL, columnIndexes)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL, columnIndexes)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL), eq(columnIndexes))).thenReturn(notIntercepted());
        assertEquals(7, preparedStatementWrapper.executeUpdate(SQL, columnIndexes));
        verify(wrappedMock).executeUpdate(SQL, columnIndexes);
    }

    @Test
    void testExecuteUpdateWithStringAndColumnIndexes_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] columnIndexes = { 1, 2 };
        when(interceptorMock.preExecute("executeUpdate", SQL, columnIndexes)).thenReturn(intercepted(9));
        assertEquals(9, preparedStatementWrapper.executeUpdate(SQL, columnIndexes));
        verify(wrappedMock, never()).executeUpdate(SQL, columnIndexes);
    }

    @Test
    void testExecuteUpdateWithStringAndColumnIndexes_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] columnIndexes = { 1, 2 };
        when(interceptorMock.preExecute("executeUpdate", SQL, columnIndexes)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL, columnIndexes)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL), eq(columnIndexes))).thenReturn(intercepted(11));
        assertEquals(11, preparedStatementWrapper.executeUpdate(SQL, columnIndexes));
    }

    @Test
    void testExecuteUpdateWithStringAndColumnNames_delegatesToWrapped() throws SQLException {
        String[] columnNames = { "a", "b" };
        when(interceptorMock.preExecute("executeUpdate", SQL, columnNames)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL, columnNames)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL), eq(columnNames))).thenReturn(notIntercepted());
        assertEquals(7, preparedStatementWrapper.executeUpdate(SQL, columnNames));
        verify(wrappedMock).executeUpdate(SQL, columnNames);
    }

    @Test
    void testExecuteUpdateWithStringAndColumnNames_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        String[] columnNames = { "a", "b" };
        when(interceptorMock.preExecute("executeUpdate", SQL, columnNames)).thenReturn(intercepted(9));
        assertEquals(9, preparedStatementWrapper.executeUpdate(SQL, columnNames));
        verify(wrappedMock, never()).executeUpdate(SQL, columnNames);
    }

    @Test
    void testExecuteUpdateWithStringAndColumnNames_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        String[] columnNames = { "a", "b" };
        when(interceptorMock.preExecute("executeUpdate", SQL, columnNames)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL, columnNames)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL), eq(columnNames))).thenReturn(intercepted(11));
        assertEquals(11, preparedStatementWrapper.executeUpdate(SQL, columnNames));
    }

    @Test
    void testExecuteUpdateWithString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL))).thenReturn(notIntercepted());
        assertEquals(7, preparedStatementWrapper.executeUpdate(SQL));
        verify(wrappedMock).executeUpdate(SQL);
    }

    @Test
    void testExecuteUpdateWithString_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", SQL)).thenReturn(intercepted(9));
        assertEquals(9, preparedStatementWrapper.executeUpdate(SQL));
        verify(wrappedMock, never()).executeUpdate(SQL);
    }

    @Test
    void testExecuteUpdateWithString_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("executeUpdate", SQL)).thenReturn(notIntercepted());
        when(wrappedMock.executeUpdate(SQL)).thenReturn(7);
        when(interceptorMock.postExecute(eq("executeUpdate"), eq(7), anyLong(), anyLong(), eq(SQL))).thenReturn(intercepted(11));
        assertEquals(11, preparedStatementWrapper.executeUpdate(SQL));
    }

    @Test
    void testGetMaxFieldSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMaxFieldSize")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxFieldSize()).thenReturn(255);
        when(interceptorMock.postExecute(eq("getMaxFieldSize"), eq(255), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(255, preparedStatementWrapper.getMaxFieldSize());
        verify(wrappedMock).getMaxFieldSize();
    }

    @Test
    void testGetMaxFieldSize_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxFieldSize")).thenReturn(intercepted(10));
        assertEquals(10, preparedStatementWrapper.getMaxFieldSize());
        verify(wrappedMock, never()).getMaxFieldSize();
    }

    @Test
    void testGetMaxFieldSize_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxFieldSize")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxFieldSize()).thenReturn(255);
        when(interceptorMock.postExecute(eq("getMaxFieldSize"), eq(255), anyLong(), anyLong())).thenReturn(intercepted(20));
        assertEquals(20, preparedStatementWrapper.getMaxFieldSize());
    }

    @Test
    void testSetMaxFieldSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setMaxFieldSize", 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setMaxFieldSize(10);
        verify(wrappedMock).setMaxFieldSize(10);
        verify(interceptorMock).postExecute(eq("setMaxFieldSize"), isNull(), anyLong(), anyLong(), eq(10));
    }

    @Test
    void testSetMaxFieldSize_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setMaxFieldSize", 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setMaxFieldSize(10);
        verify(wrappedMock, never()).setMaxFieldSize(10);
        verify(interceptorMock, never()).postExecute(eq("setMaxFieldSize"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetMaxRows_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMaxRows")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxRows()).thenReturn(500);
        when(interceptorMock.postExecute(eq("getMaxRows"), eq(500), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(500, preparedStatementWrapper.getMaxRows());
        verify(wrappedMock).getMaxRows();
    }

    @Test
    void testGetMaxRows_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxRows")).thenReturn(intercepted(10));
        assertEquals(10, preparedStatementWrapper.getMaxRows());
        verify(wrappedMock, never()).getMaxRows();
    }

    @Test
    void testGetMaxRows_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMaxRows")).thenReturn(notIntercepted());
        when(wrappedMock.getMaxRows()).thenReturn(500);
        when(interceptorMock.postExecute(eq("getMaxRows"), eq(500), anyLong(), anyLong())).thenReturn(intercepted(20));
        assertEquals(20, preparedStatementWrapper.getMaxRows());
    }

    @Test
    void testSetMaxRows_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setMaxRows", 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setMaxRows(10);
        verify(wrappedMock).setMaxRows(10);
        verify(interceptorMock).postExecute(eq("setMaxRows"), isNull(), anyLong(), anyLong(), eq(10));
    }

    @Test
    void testSetMaxRows_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setMaxRows", 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setMaxRows(10);
        verify(wrappedMock, never()).setMaxRows(10);
        verify(interceptorMock, never()).postExecute(eq("setMaxRows"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testSetEscapeProcessing_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setEscapeProcessing", true)).thenReturn(notIntercepted());
        preparedStatementWrapper.setEscapeProcessing(true);
        verify(wrappedMock).setEscapeProcessing(true);
        verify(interceptorMock).postExecute(eq("setEscapeProcessing"), isNull(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void testSetEscapeProcessing_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setEscapeProcessing", true)).thenReturn(intercepted(null));
        preparedStatementWrapper.setEscapeProcessing(true);
        verify(wrappedMock, never()).setEscapeProcessing(true);
        verify(interceptorMock, never()).postExecute(eq("setEscapeProcessing"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetQueryTimeout_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getQueryTimeout")).thenReturn(notIntercepted());
        when(wrappedMock.getQueryTimeout()).thenReturn(30);
        when(interceptorMock.postExecute(eq("getQueryTimeout"), eq(30), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(30, preparedStatementWrapper.getQueryTimeout());
        verify(wrappedMock).getQueryTimeout();
    }

    @Test
    void testGetQueryTimeout_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getQueryTimeout")).thenReturn(intercepted(10));
        assertEquals(10, preparedStatementWrapper.getQueryTimeout());
        verify(wrappedMock, never()).getQueryTimeout();
    }

    @Test
    void testGetQueryTimeout_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getQueryTimeout")).thenReturn(notIntercepted());
        when(wrappedMock.getQueryTimeout()).thenReturn(30);
        when(interceptorMock.postExecute(eq("getQueryTimeout"), eq(30), anyLong(), anyLong())).thenReturn(intercepted(20));
        assertEquals(20, preparedStatementWrapper.getQueryTimeout());
    }

    @Test
    void testSetQueryTimeout_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setQueryTimeout", 10)).thenReturn(notIntercepted());
        preparedStatementWrapper.setQueryTimeout(10);
        verify(wrappedMock).setQueryTimeout(10);
        verify(interceptorMock).postExecute(eq("setQueryTimeout"), isNull(), anyLong(), anyLong(), eq(10));
    }

    @Test
    void testSetQueryTimeout_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setQueryTimeout", 10)).thenReturn(intercepted(null));
        preparedStatementWrapper.setQueryTimeout(10);
        verify(wrappedMock, never()).setQueryTimeout(10);
        verify(interceptorMock, never()).postExecute(eq("setQueryTimeout"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testCancel_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("cancel")).thenReturn(notIntercepted());
        preparedStatementWrapper.cancel();
        verify(wrappedMock).cancel();
        verify(interceptorMock).postExecute(eq("cancel"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testCancel_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("cancel")).thenReturn(intercepted(null));
        preparedStatementWrapper.cancel();
        verify(wrappedMock, never()).cancel();
        verify(interceptorMock, never()).postExecute(eq("cancel"), any(), anyLong(), anyLong());
    }

    @Test
    void testSetCursorName_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setCursorName", "cursor1")).thenReturn(notIntercepted());
        preparedStatementWrapper.setCursorName("cursor1");
        verify(wrappedMock).setCursorName("cursor1");
        verify(interceptorMock).postExecute(eq("setCursorName"), isNull(), anyLong(), anyLong(), eq("cursor1"));
    }

    @Test
    void testSetCursorName_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setCursorName", "cursor1")).thenReturn(intercepted(null));
        preparedStatementWrapper.setCursorName("cursor1");
        verify(wrappedMock, never()).setCursorName("cursor1");
        verify(interceptorMock, never()).postExecute(eq("setCursorName"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetResultSet_delegatesToWrapped() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("getResultSet")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSet()).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("getResultSet"), eq(resultSet), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(resultSet, preparedStatementWrapper.getResultSet());
        verify(wrappedMock).getResultSet();
    }

    @Test
    void testGetResultSet_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("getResultSet")).thenReturn(intercepted(resultSet));
        assertSame(resultSet, preparedStatementWrapper.getResultSet());
        verify(wrappedMock, never()).getResultSet();
    }

    @Test
    void testGetResultSet_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSet postResultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("getResultSet")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSet()).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("getResultSet"), eq(resultSet), anyLong(), anyLong())).thenReturn(intercepted(postResultSet));
        assertSame(postResultSet, preparedStatementWrapper.getResultSet());
    }

    @Test
    void testGetUpdateCount_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getUpdateCount")).thenReturn(notIntercepted());
        when(wrappedMock.getUpdateCount()).thenReturn(3);
        when(interceptorMock.postExecute(eq("getUpdateCount"), eq(3), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(3, preparedStatementWrapper.getUpdateCount());
        verify(wrappedMock).getUpdateCount();
    }

    @Test
    void testGetUpdateCount_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getUpdateCount")).thenReturn(intercepted(9));
        assertEquals(9, preparedStatementWrapper.getUpdateCount());
        verify(wrappedMock, never()).getUpdateCount();
    }

    @Test
    void testGetUpdateCount_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getUpdateCount")).thenReturn(notIntercepted());
        when(wrappedMock.getUpdateCount()).thenReturn(3);
        when(interceptorMock.postExecute(eq("getUpdateCount"), eq(3), anyLong(), anyLong())).thenReturn(intercepted(11));
        assertEquals(11, preparedStatementWrapper.getUpdateCount());
    }

    @Test
    void testGetMoreResultsWithInt_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults", 1)).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults(1)).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong(), eq(1))).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.getMoreResults(1));
        verify(wrappedMock).getMoreResults(1);
    }

    @Test
    void testGetMoreResultsWithInt_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults", 1)).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.getMoreResults(1));
        verify(wrappedMock, never()).getMoreResults(1);
    }

    @Test
    void testGetMoreResultsWithInt_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults", 1)).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults(1)).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong(), eq(1))).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.getMoreResults(1));
    }

    @Test
    void testGetMoreResults_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults")).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults()).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.getMoreResults());
        verify(wrappedMock).getMoreResults();
    }

    @Test
    void testGetMoreResults_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults")).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.getMoreResults());
        verify(wrappedMock, never()).getMoreResults();
    }

    @Test
    void testGetMoreResults_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getMoreResults")).thenReturn(notIntercepted());
        when(wrappedMock.getMoreResults()).thenReturn(true);
        when(interceptorMock.postExecute(eq("getMoreResults"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.getMoreResults());
    }

    @Test
    void testSetFetchDirection_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setFetchDirection", 1)).thenReturn(notIntercepted());
        preparedStatementWrapper.setFetchDirection(1);
        verify(wrappedMock).setFetchDirection(1);
        verify(interceptorMock).postExecute(eq("setFetchDirection"), isNull(), anyLong(), anyLong(), eq(1));
    }

    @Test
    void testSetFetchDirection_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setFetchDirection", 1)).thenReturn(intercepted(null));
        preparedStatementWrapper.setFetchDirection(1);
        verify(wrappedMock, never()).setFetchDirection(1);
        verify(interceptorMock, never()).postExecute(eq("setFetchDirection"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetFetchDirection_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getFetchDirection")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchDirection()).thenReturn(1);
        when(interceptorMock.postExecute(eq("getFetchDirection"), eq(1), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(1, preparedStatementWrapper.getFetchDirection());
        verify(wrappedMock).getFetchDirection();
    }

    @Test
    void testGetFetchDirection_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchDirection")).thenReturn(intercepted(2));
        assertEquals(2, preparedStatementWrapper.getFetchDirection());
        verify(wrappedMock, never()).getFetchDirection();
    }

    @Test
    void testGetFetchDirection_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchDirection")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchDirection()).thenReturn(1);
        when(interceptorMock.postExecute(eq("getFetchDirection"), eq(1), anyLong(), anyLong())).thenReturn(intercepted(3));
        assertEquals(3, preparedStatementWrapper.getFetchDirection());
    }

    @Test
    void testSetFetchSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setFetchSize", 50)).thenReturn(notIntercepted());
        preparedStatementWrapper.setFetchSize(50);
        verify(wrappedMock).setFetchSize(50);
        verify(interceptorMock).postExecute(eq("setFetchSize"), isNull(), anyLong(), anyLong(), eq(50));
    }

    @Test
    void testSetFetchSize_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setFetchSize", 50)).thenReturn(intercepted(null));
        preparedStatementWrapper.setFetchSize(50);
        verify(wrappedMock, never()).setFetchSize(50);
        verify(interceptorMock, never()).postExecute(eq("setFetchSize"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testGetFetchSize_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getFetchSize")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchSize()).thenReturn(50);
        when(interceptorMock.postExecute(eq("getFetchSize"), eq(50), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(50, preparedStatementWrapper.getFetchSize());
        verify(wrappedMock).getFetchSize();
    }

    @Test
    void testGetFetchSize_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchSize")).thenReturn(intercepted(20));
        assertEquals(20, preparedStatementWrapper.getFetchSize());
        verify(wrappedMock, never()).getFetchSize();
    }

    @Test
    void testGetFetchSize_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getFetchSize")).thenReturn(notIntercepted());
        when(wrappedMock.getFetchSize()).thenReturn(50);
        when(interceptorMock.postExecute(eq("getFetchSize"), eq(50), anyLong(), anyLong())).thenReturn(intercepted(30));
        assertEquals(30, preparedStatementWrapper.getFetchSize());
    }

    @Test
    void testGetResultSetConcurrency_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getResultSetConcurrency")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetConcurrency()).thenReturn(1007);
        when(interceptorMock.postExecute(eq("getResultSetConcurrency"), eq(1007), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(1007, preparedStatementWrapper.getResultSetConcurrency());
        verify(wrappedMock).getResultSetConcurrency();
    }

    @Test
    void testGetResultSetConcurrency_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetConcurrency")).thenReturn(intercepted(1008));
        assertEquals(1008, preparedStatementWrapper.getResultSetConcurrency());
        verify(wrappedMock, never()).getResultSetConcurrency();
    }

    @Test
    void testGetResultSetConcurrency_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetConcurrency")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetConcurrency()).thenReturn(1007);
        when(interceptorMock.postExecute(eq("getResultSetConcurrency"), eq(1007), anyLong(), anyLong())).thenReturn(intercepted(1003));
        assertEquals(1003, preparedStatementWrapper.getResultSetConcurrency());
    }

    @Test
    void testGetResultSetType_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getResultSetType")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetType()).thenReturn(1003);
        when(interceptorMock.postExecute(eq("getResultSetType"), eq(1003), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(1003, preparedStatementWrapper.getResultSetType());
        verify(wrappedMock).getResultSetType();
    }

    @Test
    void testGetResultSetType_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetType")).thenReturn(intercepted(1004));
        assertEquals(1004, preparedStatementWrapper.getResultSetType());
        verify(wrappedMock, never()).getResultSetType();
    }

    @Test
    void testGetResultSetType_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetType")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetType()).thenReturn(1003);
        when(interceptorMock.postExecute(eq("getResultSetType"), eq(1003), anyLong(), anyLong())).thenReturn(intercepted(1005));
        assertEquals(1005, preparedStatementWrapper.getResultSetType());
    }

    @Test
    void testAddBatchWithString_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("addBatch", SQL)).thenReturn(notIntercepted());
        preparedStatementWrapper.addBatch(SQL);
        verify(wrappedMock).addBatch(SQL);
        verify(interceptorMock).postExecute(eq("addBatch"), isNull(), anyLong(), anyLong(), eq(SQL));
    }

    @Test
    void testAddBatchWithString_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("addBatch", SQL)).thenReturn(intercepted(null));
        preparedStatementWrapper.addBatch(SQL);
        verify(wrappedMock, never()).addBatch(SQL);
        verify(interceptorMock, never()).postExecute(eq("addBatch"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testClearBatch_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("clearBatch")).thenReturn(notIntercepted());
        preparedStatementWrapper.clearBatch();
        verify(wrappedMock).clearBatch();
        verify(interceptorMock).postExecute(eq("clearBatch"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testClearBatch_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("clearBatch")).thenReturn(intercepted(null));
        preparedStatementWrapper.clearBatch();
        verify(wrappedMock, never()).clearBatch();
        verify(interceptorMock, never()).postExecute(eq("clearBatch"), any(), anyLong(), anyLong());
    }

    @Test
    void testExecuteBatch_delegatesToWrapped() throws SQLException {
        int[] results = { 1, 1 };
        when(interceptorMock.preExecute("executeBatch")).thenReturn(notIntercepted());
        when(wrappedMock.executeBatch()).thenReturn(results);
        when(interceptorMock.postExecute(eq("executeBatch"), eq(results), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(results, preparedStatementWrapper.executeBatch());
        verify(wrappedMock).executeBatch();
    }

    @Test
    void testExecuteBatch_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        int[] interceptedValue = { 9 };
        when(interceptorMock.preExecute("executeBatch")).thenReturn(intercepted(interceptedValue));
        assertSame(interceptedValue, preparedStatementWrapper.executeBatch());
        verify(wrappedMock, never()).executeBatch();
    }

    @Test
    void testExecuteBatch_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        int[] results = { 1, 1 };
        int[] postResults = { 2, 2 };
        when(interceptorMock.preExecute("executeBatch")).thenReturn(notIntercepted());
        when(wrappedMock.executeBatch()).thenReturn(results);
        when(interceptorMock.postExecute(eq("executeBatch"), eq(results), anyLong(), anyLong())).thenReturn(intercepted(postResults));
        assertSame(postResults, preparedStatementWrapper.executeBatch());
    }

    @Test
    void testGetGeneratedKeys_delegatesToWrapped() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("getGeneratedKeys")).thenReturn(notIntercepted());
        when(wrappedMock.getGeneratedKeys()).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("getGeneratedKeys"), eq(resultSet), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertSame(resultSet, preparedStatementWrapper.getGeneratedKeys());
        verify(wrappedMock).getGeneratedKeys();
    }

    @Test
    void testGetGeneratedKeys_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("getGeneratedKeys")).thenReturn(intercepted(resultSet));
        assertSame(resultSet, preparedStatementWrapper.getGeneratedKeys());
        verify(wrappedMock, never()).getGeneratedKeys();
    }

    @Test
    void testGetGeneratedKeys_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSet postResultSet = mock(ResultSet.class);
        when(interceptorMock.preExecute("getGeneratedKeys")).thenReturn(notIntercepted());
        when(wrappedMock.getGeneratedKeys()).thenReturn(resultSet);
        when(interceptorMock.postExecute(eq("getGeneratedKeys"), eq(resultSet), anyLong(), anyLong())).thenReturn(intercepted(postResultSet));
        assertSame(postResultSet, preparedStatementWrapper.getGeneratedKeys());
    }

    @Test
    void testGetResultSetHoldability_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("getResultSetHoldability")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetHoldability()).thenReturn(1);
        when(interceptorMock.postExecute(eq("getResultSetHoldability"), eq(1), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertEquals(1, preparedStatementWrapper.getResultSetHoldability());
        verify(wrappedMock).getResultSetHoldability();
    }

    @Test
    void testGetResultSetHoldability_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetHoldability")).thenReturn(intercepted(2));
        assertEquals(2, preparedStatementWrapper.getResultSetHoldability());
        verify(wrappedMock, never()).getResultSetHoldability();
    }

    @Test
    void testGetResultSetHoldability_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("getResultSetHoldability")).thenReturn(notIntercepted());
        when(wrappedMock.getResultSetHoldability()).thenReturn(1);
        when(interceptorMock.postExecute(eq("getResultSetHoldability"), eq(1), anyLong(), anyLong())).thenReturn(intercepted(3));
        assertEquals(3, preparedStatementWrapper.getResultSetHoldability());
    }

    @Test
    void testSetPoolable_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("setPoolable", true)).thenReturn(notIntercepted());
        preparedStatementWrapper.setPoolable(true);
        verify(wrappedMock).setPoolable(true);
        verify(interceptorMock).postExecute(eq("setPoolable"), isNull(), anyLong(), anyLong(), eq(true));
    }

    @Test
    void testSetPoolable_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("setPoolable", true)).thenReturn(intercepted(null));
        preparedStatementWrapper.setPoolable(true);
        verify(wrappedMock, never()).setPoolable(true);
        verify(interceptorMock, never()).postExecute(eq("setPoolable"), any(), anyLong(), anyLong(), any());
    }

    @Test
    void testIsPoolable_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isPoolable")).thenReturn(notIntercepted());
        when(wrappedMock.isPoolable()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isPoolable"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.isPoolable());
        verify(wrappedMock).isPoolable();
    }

    @Test
    void testIsPoolable_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isPoolable")).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.isPoolable());
        verify(wrappedMock, never()).isPoolable();
    }

    @Test
    void testIsPoolable_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isPoolable")).thenReturn(notIntercepted());
        when(wrappedMock.isPoolable()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isPoolable"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.isPoolable());
    }

    @Test
    void testCloseOnCompletion_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("closeOnCompletion")).thenReturn(notIntercepted());
        preparedStatementWrapper.closeOnCompletion();
        verify(wrappedMock).closeOnCompletion();
        verify(interceptorMock).postExecute(eq("closeOnCompletion"), isNull(), anyLong(), anyLong());
    }

    @Test
    void testCloseOnCompletion_shortCircuitsWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("closeOnCompletion")).thenReturn(intercepted(null));
        preparedStatementWrapper.closeOnCompletion();
        verify(wrappedMock, never()).closeOnCompletion();
        verify(interceptorMock, never()).postExecute(eq("closeOnCompletion"), any(), anyLong(), anyLong());
    }

    @Test
    void testIsCloseOnCompletion_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isCloseOnCompletion")).thenReturn(notIntercepted());
        when(wrappedMock.isCloseOnCompletion()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isCloseOnCompletion"), eq(true), anyLong(), anyLong())).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.isCloseOnCompletion());
        verify(wrappedMock).isCloseOnCompletion();
    }

    @Test
    void testIsCloseOnCompletion_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isCloseOnCompletion")).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.isCloseOnCompletion());
        verify(wrappedMock, never()).isCloseOnCompletion();
    }

    @Test
    void testIsCloseOnCompletion_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isCloseOnCompletion")).thenReturn(notIntercepted());
        when(wrappedMock.isCloseOnCompletion()).thenReturn(true);
        when(interceptorMock.postExecute(eq("isCloseOnCompletion"), eq(true), anyLong(), anyLong())).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.isCloseOnCompletion());
    }

    @Test
    void testUnwrap_delegatesToWrapped() throws SQLException {
        String unwrapped = "unwrappedResult";
        when(interceptorMock.preExecute("unwrap", String.class)).thenReturn(notIntercepted());
        when(wrappedMock.unwrap(String.class)).thenReturn(unwrapped);
        when(interceptorMock.postExecute(eq("unwrap"), eq(unwrapped), anyLong(), anyLong(), eq(String.class))).thenReturn(notIntercepted());
        assertSame(unwrapped, preparedStatementWrapper.unwrap(String.class));
        verify(wrappedMock).unwrap(String.class);
    }

    @Test
    void testUnwrap_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        Object interceptedValue = "preInterceptedResult";
        when(interceptorMock.preExecute("unwrap", String.class)).thenReturn(intercepted(interceptedValue));
        assertSame(interceptedValue, preparedStatementWrapper.unwrap(String.class));
        verify(wrappedMock, never()).unwrap(String.class);
    }

    @Test
    void testUnwrap_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        String unwrapped = "unwrappedResult";
        Object postValue = "postInterceptedResult";
        when(interceptorMock.preExecute("unwrap", String.class)).thenReturn(notIntercepted());
        when(wrappedMock.unwrap(String.class)).thenReturn(unwrapped);
        when(interceptorMock.postExecute(eq("unwrap"), eq(unwrapped), anyLong(), anyLong(), eq(String.class))).thenReturn(intercepted(postValue));
        assertSame(postValue, preparedStatementWrapper.unwrap(String.class));
    }

    @Test
    void testIsWrapperFor_delegatesToWrapped() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(notIntercepted());
        when(wrappedMock.isWrapperFor(String.class)).thenReturn(true);
        when(interceptorMock.postExecute(eq("isWrapperFor"), eq(true), anyLong(), anyLong(), eq(String.class))).thenReturn(notIntercepted());
        assertTrue(preparedStatementWrapper.isWrapperFor(String.class));
        verify(wrappedMock).isWrapperFor(String.class);
    }

    @Test
    void testIsWrapperFor_returnsInterceptedValueWhenPreIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(intercepted(true));
        assertTrue(preparedStatementWrapper.isWrapperFor(String.class));
        verify(wrappedMock, never()).isWrapperFor(String.class);
    }

    @Test
    void testIsWrapperFor_returnsInterceptedValueWhenPostIntercepted() throws SQLException {
        when(interceptorMock.preExecute("isWrapperFor", String.class)).thenReturn(notIntercepted());
        when(wrappedMock.isWrapperFor(String.class)).thenReturn(true);
        when(interceptorMock.postExecute(eq("isWrapperFor"), eq(true), anyLong(), anyLong(), eq(String.class))).thenReturn(intercepted(false));
        assertFalse(preparedStatementWrapper.isWrapperFor(String.class));
    }

    private InterceptResult notIntercepted() {
        InterceptResult result = new InterceptResult();
        result.setIntercepted(false);
        return result;
    }

    private InterceptResult intercepted(Object value) {
        InterceptResult result = new InterceptResult();
        result.setIntercepted(true);
        result.setInterceptResult(value);
        return result;
    }

    private URL sampleUrl() {
        try {
            return URI.create("http://x").toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private void injectMockInterceptor(PreparedStatementWrapper wrapper, WrapperInterceptor mockInterceptor) {
        try {
            Field field = PreparedStatementWrapper.class.getDeclaredField("interceptor");
            field.setAccessible(true);
            field.set(wrapper, mockInterceptor);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
