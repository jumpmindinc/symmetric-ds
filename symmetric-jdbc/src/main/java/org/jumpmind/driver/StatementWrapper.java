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

import java.sql.Connection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

public class StatementWrapper implements Statement {
    private WrapperInterceptor interceptor;
    private Statement wrapped;

    public StatementWrapper(Statement wrapped) {
        this.wrapped = wrapped;
        this.interceptor = WrapperInterceptor.createInterceptor(this, null);
    }

    public StatementWrapper(Statement wrapped, WrapperInterceptor interceptor) {
        this.wrapped = wrapped;
        this.interceptor = interceptor;
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
}
