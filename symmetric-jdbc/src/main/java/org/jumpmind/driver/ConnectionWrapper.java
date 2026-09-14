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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.jumpmind.properties.TypedProperties;

public class ConnectionWrapper implements Connection {
    private WrapperInterceptor interceptor;
    private Connection wrapped;
    private TypedProperties engineProperties;

    public ConnectionWrapper(Connection wrapped) {
        this.wrapped = wrapped;
        // add system props.
        TypedProperties systemPlusEngineProperties = new TypedProperties();
        systemPlusEngineProperties.putAll(System.getProperties());
        engineProperties = systemPlusEngineProperties;
        this.interceptor = WrapperInterceptor.createInterceptor(this, engineProperties);
    }

    public ConnectionWrapper(Connection wrapped, WrapperInterceptor interceptor) {
        this.wrapped = wrapped;
        this.interceptor = interceptor;
    }

    @Override
    public void setReadOnly(boolean arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setReadOnly", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setReadOnly(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setReadOnly", null, startTime, endTime, arg1);
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

    public boolean isReadOnly() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("isReadOnly");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.isReadOnly();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("isReadOnly", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public void abort(Executor arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("abort", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.abort(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("abort", null, startTime, endTime, arg1);
    }

    public Statement createStatement() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createStatement");
        if (preResult.isIntercepted()) {
            return (Statement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Statement value = wrapped.createStatement();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createStatement", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Statement) postResult.getInterceptResult();
        }
        return value;
    }

    public Statement createStatement(int arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createStatement", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (Statement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Statement value = wrapped.createStatement(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createStatement", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (Statement) postResult.getInterceptResult();
        }
        return value;
    }

    public Statement createStatement(int arg1, int arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createStatement", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return (Statement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Statement value = wrapped.createStatement(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createStatement", value, startTime, endTime, arg1, arg2, arg3);
        if (postResult.isIntercepted()) {
            return (Statement) postResult.getInterceptResult();
        }
        return value;
    }

    public PreparedStatement prepareStatement(String arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareStatement", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (PreparedStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        PreparedStatement value = wrapped.prepareStatement(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareStatement", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (PreparedStatement) postResult.getInterceptResult();
        }
        return new PreparedStatementWrapper(value, arg1, engineProperties);
    }

    public PreparedStatement prepareStatement(String arg1, int arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareStatement", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return (PreparedStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        PreparedStatement value = wrapped.prepareStatement(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareStatement", value, startTime, endTime, arg1, arg2, arg3);
        if (postResult.isIntercepted()) {
            return (PreparedStatement) postResult.getInterceptResult();
        }
        return new PreparedStatementWrapper(value, arg1, engineProperties);
    }

    public PreparedStatement prepareStatement(String arg1, int arg2, int arg3, int arg4) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareStatement", arg1, arg2, arg3, arg4);
        if (preResult.isIntercepted()) {
            return (PreparedStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        PreparedStatement value = wrapped.prepareStatement(arg1, arg2, arg3, arg4);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareStatement", value, startTime, endTime, arg1, arg2, arg3, arg4);
        if (postResult.isIntercepted()) {
            return (PreparedStatement) postResult.getInterceptResult();
        }
        return new PreparedStatementWrapper(value, arg1, engineProperties);
    }

    public PreparedStatement prepareStatement(String arg1, int[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareStatement", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (PreparedStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        PreparedStatement value = wrapped.prepareStatement(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareStatement", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (PreparedStatement) postResult.getInterceptResult();
        }
        return new PreparedStatementWrapper(value, arg1, engineProperties);
    }

    public PreparedStatement prepareStatement(String arg1, String[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareStatement", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (PreparedStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        PreparedStatement value = wrapped.prepareStatement(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareStatement", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (PreparedStatement) postResult.getInterceptResult();
        }
        return new PreparedStatementWrapper(value, arg1, engineProperties);
    }

    public PreparedStatement prepareStatement(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareStatement", arg1);
        if (preResult.isIntercepted()) {
            return (PreparedStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        PreparedStatement value = wrapped.prepareStatement(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareStatement", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (PreparedStatement) postResult.getInterceptResult();
        }
        return new PreparedStatementWrapper(value, arg1, engineProperties);
    }

    public CallableStatement prepareCall(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareCall", arg1);
        if (preResult.isIntercepted()) {
            return (CallableStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        CallableStatement value = wrapped.prepareCall(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareCall", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (CallableStatement) postResult.getInterceptResult();
        }
        return value;
    }

    public CallableStatement prepareCall(String arg1, int arg2, int arg3) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareCall", arg1, arg2, arg3);
        if (preResult.isIntercepted()) {
            return (CallableStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        CallableStatement value = wrapped.prepareCall(arg1, arg2, arg3);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareCall", value, startTime, endTime, arg1, arg2, arg3);
        if (postResult.isIntercepted()) {
            return (CallableStatement) postResult.getInterceptResult();
        }
        return value;
    }

    public CallableStatement prepareCall(String arg1, int arg2, int arg3, int arg4) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("prepareCall", arg1, arg2, arg3, arg4);
        if (preResult.isIntercepted()) {
            return (CallableStatement) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        CallableStatement value = wrapped.prepareCall(arg1, arg2, arg3, arg4);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("prepareCall", value, startTime, endTime, arg1, arg2, arg3, arg4);
        if (postResult.isIntercepted()) {
            return (CallableStatement) postResult.getInterceptResult();
        }
        return value;
    }

    public String nativeSQL(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("nativeSQL", arg1);
        if (preResult.isIntercepted()) {
            return (String) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        String value = wrapped.nativeSQL(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("nativeSQL", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (String) postResult.getInterceptResult();
        }
        return value;
    }

    public void setAutoCommit(boolean arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setAutoCommit", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setAutoCommit(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setAutoCommit", null, startTime, endTime, arg1);
    }

    public boolean getAutoCommit() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getAutoCommit");
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.getAutoCommit();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getAutoCommit", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public void commit() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("commit");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.commit();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("commit", null, startTime, endTime);
    }

    public void rollback(Savepoint arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("rollback", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.rollback(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("rollback", null, startTime, endTime, arg1);
    }

    public void rollback() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("rollback");
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.rollback();
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("rollback", null, startTime, endTime);
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

    public DatabaseMetaData getMetaData() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getMetaData");
        if (preResult.isIntercepted()) {
            return (DatabaseMetaData) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        DatabaseMetaData value = wrapped.getMetaData();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getMetaData", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (DatabaseMetaData) postResult.getInterceptResult();
        }
        return value;
    }

    public void setCatalog(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setCatalog", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setCatalog(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setCatalog", null, startTime, endTime, arg1);
    }

    public String getCatalog() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getCatalog");
        if (preResult.isIntercepted()) {
            return (String) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        String value = wrapped.getCatalog();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getCatalog", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (String) postResult.getInterceptResult();
        }
        return value;
    }

    public void setTransactionIsolation(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setTransactionIsolation", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setTransactionIsolation(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setTransactionIsolation", null, startTime, endTime, arg1);
    }

    public int getTransactionIsolation() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getTransactionIsolation");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getTransactionIsolation();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getTransactionIsolation", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map getTypeMap() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getTypeMap");
        if (preResult.isIntercepted()) {
            return (Map) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Map value = wrapped.getTypeMap();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getTypeMap", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Map) postResult.getInterceptResult();
        }
        return value;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setTypeMap(Map arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setTypeMap", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setTypeMap(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setTypeMap", null, startTime, endTime, arg1);
    }

    public void setHoldability(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setHoldability", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setHoldability(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setHoldability", null, startTime, endTime, arg1);
    }

    public int getHoldability() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getHoldability");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getHoldability();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getHoldability", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    public Savepoint setSavepoint() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setSavepoint");
        if (preResult.isIntercepted()) {
            return (Savepoint) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Savepoint value = wrapped.setSavepoint();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("setSavepoint", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Savepoint) postResult.getInterceptResult();
        }
        return value;
    }

    public Savepoint setSavepoint(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setSavepoint", arg1);
        if (preResult.isIntercepted()) {
            return (Savepoint) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Savepoint value = wrapped.setSavepoint(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("setSavepoint", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (Savepoint) postResult.getInterceptResult();
        }
        return value;
    }

    public void releaseSavepoint(Savepoint arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("releaseSavepoint", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.releaseSavepoint(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("releaseSavepoint", null, startTime, endTime, arg1);
    }

    public Clob createClob() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createClob");
        if (preResult.isIntercepted()) {
            return (Clob) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Clob value = wrapped.createClob();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createClob", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Clob) postResult.getInterceptResult();
        }
        return value;
    }

    public Blob createBlob() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createBlob");
        if (preResult.isIntercepted()) {
            return (Blob) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Blob value = wrapped.createBlob();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createBlob", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Blob) postResult.getInterceptResult();
        }
        return value;
    }

    public NClob createNClob() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createNClob");
        if (preResult.isIntercepted()) {
            return (NClob) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        NClob value = wrapped.createNClob();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createNClob", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (NClob) postResult.getInterceptResult();
        }
        return value;
    }

    public SQLXML createSQLXML() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createSQLXML");
        if (preResult.isIntercepted()) {
            return (SQLXML) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        SQLXML value = wrapped.createSQLXML();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createSQLXML", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (SQLXML) postResult.getInterceptResult();
        }
        return value;
    }

    public boolean isValid(int arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("isValid", arg1);
        if (preResult.isIntercepted()) {
            return (boolean) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        boolean value = wrapped.isValid(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("isValid", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (boolean) postResult.getInterceptResult();
        }
        return value;
    }

    public void setClientInfo(Properties arg1) throws SQLClientInfoException {
        InterceptResult preResult = interceptor.preExecute("setClientInfo", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setClientInfo(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setClientInfo", null, startTime, endTime, arg1);
    }

    public void setClientInfo(String arg1, String arg2) throws SQLClientInfoException {
        InterceptResult preResult = interceptor.preExecute("setClientInfo", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setClientInfo(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setClientInfo", null, startTime, endTime, arg1, arg2);
    }

    public Properties getClientInfo() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getClientInfo");
        if (preResult.isIntercepted()) {
            return (Properties) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Properties value = wrapped.getClientInfo();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getClientInfo", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (Properties) postResult.getInterceptResult();
        }
        return value;
    }

    public String getClientInfo(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getClientInfo", arg1);
        if (preResult.isIntercepted()) {
            return (String) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        String value = wrapped.getClientInfo(arg1);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getClientInfo", value, startTime, endTime, arg1);
        if (postResult.isIntercepted()) {
            return (String) postResult.getInterceptResult();
        }
        return value;
    }

    public Array createArrayOf(String arg1, Object[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createArrayOf", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (Array) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Array value = wrapped.createArrayOf(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createArrayOf", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (Array) postResult.getInterceptResult();
        }
        return value;
    }

    public Struct createStruct(String arg1, Object[] arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("createStruct", arg1, arg2);
        if (preResult.isIntercepted()) {
            return (Struct) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        Struct value = wrapped.createStruct(arg1, arg2);
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("createStruct", value, startTime, endTime, arg1, arg2);
        if (postResult.isIntercepted()) {
            return (Struct) postResult.getInterceptResult();
        }
        return value;
    }

    public void setSchema(String arg1) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setSchema", arg1);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setSchema(arg1);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setSchema", null, startTime, endTime, arg1);
    }

    public String getSchema() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getSchema");
        if (preResult.isIntercepted()) {
            return (String) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        String value = wrapped.getSchema();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getSchema", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (String) postResult.getInterceptResult();
        }
        return value;
    }

    public void setNetworkTimeout(Executor arg1, int arg2) throws SQLException {
        InterceptResult preResult = interceptor.preExecute("setNetworkTimeout", arg1, arg2);
        if (preResult.isIntercepted()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        wrapped.setNetworkTimeout(arg1, arg2);
        long endTime = System.currentTimeMillis();
        interceptor.postExecute("setNetworkTimeout", null, startTime, endTime, arg1, arg2);
    }

    public int getNetworkTimeout() throws SQLException {
        InterceptResult preResult = interceptor.preExecute("getNetworkTimeout");
        if (preResult.isIntercepted()) {
            return (int) preResult.getInterceptResult();
        }
        long startTime = System.currentTimeMillis();
        int value = wrapped.getNetworkTimeout();
        long endTime = System.currentTimeMillis();
        InterceptResult postResult = interceptor.postExecute("getNetworkTimeout", value, startTime, endTime);
        if (postResult.isIntercepted()) {
            return (int) postResult.getInterceptResult();
        }
        return value;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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

    public TypedProperties getEngineProperties() {
        return engineProperties;
    }

    public void setEngineProperties(TypedProperties engineProperties) {
        this.engineProperties = engineProperties;
    }
}
