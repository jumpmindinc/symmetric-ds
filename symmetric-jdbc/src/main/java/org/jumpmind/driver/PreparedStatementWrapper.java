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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
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

import org.jumpmind.properties.TypedProperties;

public class PreparedStatementWrapper implements PreparedStatement {
    private WrapperInterceptor interceptor;
    private PreparedStatement wrapped;
    private String statement;
    private TypedProperties engineProperties;

    public PreparedStatementWrapper(PreparedStatement wrapped, String statement, TypedProperties engineProperties) {
        this.wrapped = wrapped;
        this.interceptor = WrapperInterceptor.createInterceptor(this, engineProperties);
        this.statement = statement;
    }

    public void setBoolean(int arg1, boolean arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBoolean", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBoolean(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBoolean", null, startTime, endTime, arg1, arg2);
    }

    public void setByte(int arg1, byte arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setByte", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setByte(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setByte", null, startTime, endTime, arg1, arg2);
    }

    public void setShort(int arg1, short arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setShort", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setShort(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setShort", null, startTime, endTime, arg1, arg2);
    }

    public void setInt(int arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setInt", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setInt(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setInt", null, startTime, endTime, arg1, arg2);
    }

    public void setLong(int arg1, long arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setLong", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setLong(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setLong", null, startTime, endTime, arg1, arg2);
    }

    public void setFloat(int arg1, float arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setFloat", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setFloat(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setFloat", null, startTime, endTime, arg1, arg2);
    }

    public void setDouble(int arg1, double arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setDouble", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setDouble(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setDouble", null, startTime, endTime, arg1, arg2);
    }

    public void setTimestamp(int arg1, Timestamp arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setTimestamp", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setTimestamp(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setTimestamp", null, startTime, endTime, arg1, arg2);
    }

    public void setTimestamp(int arg1, Timestamp arg2, Calendar arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setTimestamp", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setTimestamp(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setTimestamp", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setURL(int arg1, URL arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setURL", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setURL(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setURL", null, startTime, endTime, arg1, arg2);
    }

    public void setTime(int arg1, Time arg2, Calendar arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setTime", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setTime(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setTime", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setTime(int arg1, Time arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setTime", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setTime(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setTime", null, startTime, endTime, arg1, arg2);
    }

    public boolean execute() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("execute");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.execute();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("execute", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getMetaData");
        if (preResult.isIntercepted()) {
            return (ResultSetMetaData) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        ResultSetMetaData value = wrapped.getMetaData();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getMetaData", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (ResultSetMetaData) postResult.getInterceptResult();
        }
        return value;
    }

    public ResultSet executeQuery() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeQuery");
        if (preResult.isIntercepted()) {
            return (ResultSet) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        ResultSet value = wrapped.executeQuery();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeQuery", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (ResultSet) postResult.getInterceptResult();
        }
        return value;
    }

    public int executeUpdate() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeUpdate");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.executeUpdate();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeUpdate", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void addBatch() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("addBatch");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.addBatch();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("addBatch", null, startTime, endTime);
    }

    public void setNull(int arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNull", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNull(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNull", null, startTime, endTime, arg1, arg2);
    }

    public void setNull(int arg1, int arg2, String arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNull", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNull(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNull", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setBigDecimal(int arg1, BigDecimal arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBigDecimal", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBigDecimal(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBigDecimal", null, startTime, endTime, arg1, arg2);
    }

    public void setString(int arg1, String arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setString", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setString(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setString", null, startTime, endTime, arg1, arg2);
    }

    public void setBytes(int arg1, byte[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBytes", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBytes(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBytes", null, startTime, endTime, arg1, arg2);
    }

    public void setDate(int arg1, Date arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setDate", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setDate(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setDate", null, startTime, endTime, arg1, arg2);
    }

    public void setDate(int arg1, Date arg2, Calendar arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setDate", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setDate(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setDate", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setAsciiStream(int arg1, InputStream arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setAsciiStream", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setAsciiStream(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setAsciiStream", null, startTime, endTime, arg1, arg2);
    }

    public void setAsciiStream(int arg1, InputStream arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setAsciiStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setAsciiStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setAsciiStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setAsciiStream(int arg1, InputStream arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setAsciiStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setAsciiStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setAsciiStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    @Deprecated
    public void setUnicodeStream(int arg1, InputStream arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setUnicodeStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setUnicodeStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setUnicodeStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setBinaryStream(int arg1, InputStream arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBinaryStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBinaryStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBinaryStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setBinaryStream(int arg1, InputStream arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBinaryStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBinaryStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBinaryStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setBinaryStream(int arg1, InputStream arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBinaryStream", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBinaryStream(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBinaryStream", null, startTime, endTime, arg1, arg2);
    }

    public void clearParameters() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("clearParameters");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.clearParameters();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("clearParameters", null, startTime, endTime);
    }

    public void setObject(int arg1, Object arg2, int arg3, int arg4) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setObject", arg1, arg2, arg3, arg4);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setObject(arg1, arg2, arg3, arg4);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setObject", null, startTime, endTime, arg1, arg2, arg3, arg4);
    }

    public void setObject(int arg1, Object arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setObject", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setObject(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setObject", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setObject(int arg1, Object arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setObject", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setObject(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setObject", null, startTime, endTime, arg1, arg2);
    }

    public void setCharacterStream(int arg1, Reader arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setCharacterStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setCharacterStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setCharacterStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setCharacterStream(int arg1, Reader arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setCharacterStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setCharacterStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setCharacterStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setCharacterStream(int arg1, Reader arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setCharacterStream", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setCharacterStream(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setCharacterStream", null, startTime, endTime, arg1, arg2);
    }

    public void setRef(int arg1, Ref arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setRef", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setRef(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setRef", null, startTime, endTime, arg1, arg2);
    }

    public void setBlob(int arg1, InputStream arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBlob", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBlob(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBlob", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setBlob(int arg1, InputStream arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBlob", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBlob(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBlob", null, startTime, endTime, arg1, arg2);
    }

    public void setBlob(int arg1, Blob arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setBlob", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setBlob(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setBlob", null, startTime, endTime, arg1, arg2);
    }

    public void setClob(int arg1, Reader arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setClob", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setClob(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setClob", null, startTime, endTime, arg1, arg2);
    }

    public void setClob(int arg1, Reader arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setClob", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setClob(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setClob", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setClob(int arg1, Clob arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setClob", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setClob(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setClob", null, startTime, endTime, arg1, arg2);
    }

    public void setArray(int arg1, Array arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setArray", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setArray(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setArray", null, startTime, endTime, arg1, arg2);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getParameterMetaData");
        if (preResult.isIntercepted()) {
            return (ParameterMetaData) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        ParameterMetaData value = wrapped.getParameterMetaData();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getParameterMetaData", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (ParameterMetaData) postResult.getInterceptResult();
        }
        return value;
    }

    public void setRowId(int arg1, RowId arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setRowId", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setRowId(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setRowId", null, startTime, endTime, arg1, arg2);
    }

    public void setNString(int arg1, String arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNString", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNString(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNString", null, startTime, endTime, arg1, arg2);
    }

    public void setNCharacterStream(int arg1, Reader arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNCharacterStream", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNCharacterStream(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNCharacterStream", null, startTime, endTime, arg1, arg2);
    }

    public void setNCharacterStream(int arg1, Reader arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNCharacterStream", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNCharacterStream(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNCharacterStream", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setNClob(int arg1, Reader arg2, long arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNClob", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNClob(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNClob", null, startTime, endTime, arg1, arg2, arg3);
    }

    public void setNClob(int arg1, NClob arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNClob", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNClob(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNClob", null, startTime, endTime, arg1, arg2);
    }

    public void setNClob(int arg1, Reader arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNClob", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNClob(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNClob", null, startTime, endTime, arg1, arg2);
    }

    public void setSQLXML(int arg1, SQLXML arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setSQLXML", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setSQLXML(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setSQLXML", null, startTime, endTime, arg1, arg2);
    }

    public void close() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("close");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.close();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("close", null, startTime, endTime);
    }

    public Connection getConnection() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getConnection");
        if (preResult.isIntercepted()) {
            return (Connection) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Connection value = wrapped.getConnection();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getConnection", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Connection) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean execute(String arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("execute", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.execute(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("execute", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean execute(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("execute", arg1);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.execute(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("execute", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean execute(String arg1, String[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("execute", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.execute(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("execute", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean execute(String arg1, int[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("execute", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.execute(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("execute", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean isClosed() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("isClosed");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.isClosed();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("isClosed", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public SQLWarning getWarnings() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getWarnings");
        if (preResult.isIntercepted()) {
            return (SQLWarning) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        SQLWarning value = wrapped.getWarnings();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getWarnings", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (SQLWarning) postResult.getInterceptResult();
        }
        return value;
    }

    public void clearWarnings() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("clearWarnings");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.clearWarnings();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("clearWarnings", null, startTime, endTime);
    }

    public ResultSet executeQuery(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeQuery", arg1);
        if (preResult.isIntercepted()) {
            return (ResultSet) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        ResultSet value = wrapped.executeQuery(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeQuery", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (ResultSet) postResult.getInterceptResult();
        }
        return value;
    }

    public int executeUpdate(String arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeUpdate", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.executeUpdate(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeUpdate", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public int executeUpdate(String arg1, int[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeUpdate", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.executeUpdate(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeUpdate", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public int executeUpdate(String arg1, String[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeUpdate", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.executeUpdate(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeUpdate", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public int executeUpdate(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeUpdate", arg1);
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.executeUpdate(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeUpdate", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public int getMaxFieldSize() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getMaxFieldSize");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getMaxFieldSize();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getMaxFieldSize", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void setMaxFieldSize(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setMaxFieldSize", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setMaxFieldSize(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setMaxFieldSize", null, startTime, endTime, arg1);
    }

    public int getMaxRows() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getMaxRows");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getMaxRows();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getMaxRows", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void setMaxRows(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setMaxRows", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setMaxRows(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setMaxRows", null, startTime, endTime, arg1);
    }

    public void setEscapeProcessing(boolean arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setEscapeProcessing", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setEscapeProcessing(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setEscapeProcessing", null, startTime, endTime, arg1);
    }

    public int getQueryTimeout() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getQueryTimeout");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getQueryTimeout();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getQueryTimeout", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void setQueryTimeout(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setQueryTimeout", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setQueryTimeout(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setQueryTimeout", null, startTime, endTime, arg1);
    }

    public void cancel() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("cancel");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.cancel();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("cancel", null, startTime, endTime);
    }

    public void setCursorName(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setCursorName", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setCursorName(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setCursorName", null, startTime, endTime, arg1);
    }

    public ResultSet getResultSet() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getResultSet");
        if (preResult.isIntercepted()) {
            return (ResultSet) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        ResultSet value = wrapped.getResultSet();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getResultSet", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (ResultSet) postResult.getInterceptResult();
        }
        return value;
    }

    public int getUpdateCount() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getUpdateCount");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getUpdateCount();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getUpdateCount", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean getMoreResults(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getMoreResults", arg1);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.getMoreResults(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getMoreResults", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean getMoreResults() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getMoreResults");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.getMoreResults();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getMoreResults", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public void setFetchDirection(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setFetchDirection", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setFetchDirection(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setFetchDirection", null, startTime, endTime, arg1);
    }

    public int getFetchDirection() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getFetchDirection");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getFetchDirection();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getFetchDirection", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void setFetchSize(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setFetchSize", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setFetchSize(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setFetchSize", null, startTime, endTime, arg1);
    }

    public int getFetchSize() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getFetchSize");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getFetchSize();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getFetchSize", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public int getResultSetConcurrency() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getResultSetConcurrency");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getResultSetConcurrency();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getResultSetConcurrency", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public int getResultSetType() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getResultSetType");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getResultSetType();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getResultSetType", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void addBatch(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("addBatch", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.addBatch(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("addBatch", null, startTime, endTime, arg1);
    }

    public void clearBatch() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("clearBatch");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.clearBatch();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("clearBatch", null, startTime, endTime);
    }

    public int[] executeBatch() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("executeBatch");
        if (preResult.isIntercepted()) {
            return (int[]) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int[] value = wrapped.executeBatch();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("executeBatch", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int[]) postResult.getInterceptResult();
        }
        return value;
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getGeneratedKeys");
        if (preResult.isIntercepted()) {
            return (ResultSet) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        ResultSet value = wrapped.getGeneratedKeys();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getGeneratedKeys", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (ResultSet) postResult.getInterceptResult();
        }
        return value;
    }

    public int getResultSetHoldability() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getResultSetHoldability");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getResultSetHoldability();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getResultSetHoldability", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public void setPoolable(boolean arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setPoolable", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setPoolable(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setPoolable", null, startTime, endTime, arg1);
    }

    public boolean isPoolable() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("isPoolable");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.isPoolable();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("isPoolable", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public void closeOnCompletion() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("closeOnCompletion");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.closeOnCompletion();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("closeOnCompletion", null, startTime, endTime);
    }

    public boolean isCloseOnCompletion() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("isCloseOnCompletion");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.isCloseOnCompletion();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("isCloseOnCompletion", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Object unwrap(Class arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("unwrap", arg1);
        if (preResult.isIntercepted()) {
            return (Object) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Object value = wrapped.unwrap(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("unwrap", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (Object) postResult.getInterceptResult();
        }
        return value;
    }

    @SuppressWarnings({ "rawtypes" })
    public boolean isWrapperFor(Class arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("isWrapperFor", arg1);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.isWrapperFor(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("isWrapperFor", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public String getStatement() {
        return statement;
    }

    public TypedProperties getEngineProperties() {
        return engineProperties;
    }

    public void setEngineProperties(TypedProperties engineProperties) {
        this.engineProperties = engineProperties;
    }
}
