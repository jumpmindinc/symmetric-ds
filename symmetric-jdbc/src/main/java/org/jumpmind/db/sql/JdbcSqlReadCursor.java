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
package org.jumpmind.db.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcSqlReadCursor<T> implements ISqlReadCursor<T> {
    protected Connection c;
    protected ResultSet rs;
    protected Statement st;
    protected boolean autoCommitFlag;
    protected ISqlRowMapper<T> mapper;
    protected JdbcSqlTemplate sqlTemplate;
    protected int rowNumber;
    protected int originalIsolationLevel;
    protected ResultSetMetaData rsMetaData = null;
    protected int rsColumnCount;
    protected IConnectionHandler connectionHandler;
    protected boolean returnLobObjects;

    public JdbcSqlReadCursor() {
    }

    public JdbcSqlReadCursor(JdbcSqlTemplate sqlTemplate, ISqlRowMapper<T> mapper, String sql,
            Object[] values, int[] types) {
        this(sqlTemplate, mapper, sql, values, types, null, false);
    }

    public JdbcSqlReadCursor(JdbcSqlTemplate sqlTemplate, ISqlRowMapper<T> mapper, String sql,
            Object[] values, int[] types, IConnectionHandler connectionHandler, boolean returnLobObjects) {
        this.sqlTemplate = sqlTemplate;
        this.mapper = mapper;
        this.connectionHandler = connectionHandler;
        this.returnLobObjects = returnLobObjects;
        try {
            c = sqlTemplate.getDataSource().getConnection();
            if (this.connectionHandler != null) {
                this.connectionHandler.before(c);
            }
            originalIsolationLevel = c.getTransactionIsolation();
            autoCommitFlag = c.getAutoCommit();
            if (c.getTransactionIsolation() != sqlTemplate.getIsolationLevel()) {
                c.setTransactionIsolation(sqlTemplate.getIsolationLevel());
            }
            if (sqlTemplate.isRequiresAutoCommitFalseToSetFetchSize()) {
                c.setAutoCommit(false);
            }
            try {
                if (values != null) {
                    PreparedStatement pstmt = c.prepareStatement(sql,
                            sqlTemplate.getSettings().getResultSetType(),
                            ResultSet.CONCUR_READ_ONLY);
                    sqlTemplate.setValues(pstmt, values, types, sqlTemplate.getLobHandler());
                    st = pstmt;
                    st.setQueryTimeout(sqlTemplate.getSettings().getQueryTimeout());
                    st.setFetchSize(sqlTemplate.getSettings().getFetchSize());
                    rs = pstmt.executeQuery();
                } else {
                    st = c.createStatement(sqlTemplate.getSettings().getResultSetType(),
                            ResultSet.CONCUR_READ_ONLY);
                    st.setQueryTimeout(sqlTemplate.getSettings().getQueryTimeout());
                    st.setFetchSize(sqlTemplate.getSettings().getFetchSize());
                    rs = st.executeQuery(sql);
                }
            } catch (SQLException e) {
                /*
                 * The Xerial SQLite JDBC driver throws an exception if a query returns an empty set This gets around that
                 */
                if (e.getMessage() == null
                        || !e.getMessage().equalsIgnoreCase("query does not return results")) {
                    throw e;
                }
            }
            SqlUtils.addSqlReadCursor(this);
        } catch (SQLException ex) {
            close();
            throw sqlTemplate.translate("Failed to execute sql: " + sql, ex);
        } catch (Throwable ex) {
            close();
            throw sqlTemplate.translate("Failed to execute sql: " + sql, ex);
        }
    }

    public T next() {
        try {
            while (rs != null && rs.next()) {
                if (rsMetaData == null) {
                    rsMetaData = rs.getMetaData();
                    rsColumnCount = rsMetaData.getColumnCount();
                }
                Row row = getMapForRow(rs, rsMetaData, rsColumnCount, sqlTemplate.getSettings().isReadStringsAsBytes(), returnLobObjects);
                T value = mapper.mapRow(row);
                if (value != null) {
                    return value;
                }
            }
            JdbcSqlTemplate.close(rs);
            rs = null;
            return null;
        } catch (SQLException ex) {
            throw sqlTemplate.translate(ex);
        }
    }

    protected static Row getMapForRow(ResultSet rs, ResultSetMetaData argResultSetMetaData,
            int columnCount, boolean readStringsAsBytes, boolean returnLobObjects) throws SQLException {
        Row mapOfColValues = new Row(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String key = JdbcSqlTemplate.lookupColumnName(argResultSetMetaData, i);
            Object obj = JdbcSqlTemplate.getResultSetValue(rs, argResultSetMetaData, i, readStringsAsBytes, returnLobObjects);
            mapOfColValues.put(key, obj);
        }
        return mapOfColValues;
    }

    public final void close() {
        if (this.connectionHandler != null) {
            this.connectionHandler.after(c);
        }
        JdbcSqlTemplate.close(rs);
        JdbcSqlTemplate.close(st);
        JdbcSqlTemplate.close(autoCommitFlag, originalIsolationLevel, c);
        SqlUtils.removeSqlReadCursor(this);
    }
}
