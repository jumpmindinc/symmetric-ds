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

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.mapper.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * TODO Support Oracle's non-standard way of batching
 */
public class JdbcSqlTransaction implements ISqlTransaction {
    private final static Logger log = LoggerFactory.getLogger(JdbcSqlTransaction.class);
    protected boolean inBatchMode = false;
    protected Connection connection;
    protected String psql;
    protected PreparedStatement pstmt;
    protected JdbcSqlTemplate jdbcSqlTemplate;
    protected boolean autoCommit = false;
    protected boolean oldAutoCommitValue;
    protected List<Object> markers = new ArrayList<Object>();
    protected LogSqlBuilder logSqlBuilder;
    protected List<ISqlTransactionListener> listeners = new ArrayList<ISqlTransactionListener>();
    protected int batchSize = 100;

    public JdbcSqlTransaction(JdbcSqlTemplate jdbcSqlTemplate) {
        this(jdbcSqlTemplate, false);
    }

    public JdbcSqlTransaction(JdbcSqlTemplate jdbcSqlTemplate, boolean autoCommit) {
        this.autoCommit = autoCommit;
        this.jdbcSqlTemplate = jdbcSqlTemplate;
        this.logSqlBuilder = jdbcSqlTemplate.logSqlBuilder;
        this.batchSize = jdbcSqlTemplate.getSettings().getBatchSize();
        this.init();
    }

    @Override
    public void addSqlTransactionListener(ISqlTransactionListener listener) {
        listeners.add(listener);
    }

    protected final void init() {
        if (this.connection != null) {
            close();
        }
        try {
            this.connection = jdbcSqlTemplate.getDataSource().getConnection();
            this.oldAutoCommitValue = this.connection.getAutoCommit();
            this.connection.setAutoCommit(autoCommit);
            SqlUtils.addSqlTransaction(this);
        } catch (SQLException ex) {
            close();
            throw jdbcSqlTemplate.translate(ex);
        }
    }

    public void setInBatchMode(boolean useBatching) {
        if (connection != null) {
            this.inBatchMode = useBatching;
        }
    }

    public boolean isInBatchMode() {
        return inBatchMode;
    }

    public void commit() {
        if (connection != null) {
            try {
                if (pstmt != null && inBatchMode) {
                    flush();
                }
                if (!autoCommit) {
                    connection.commit();
                    for (ISqlTransactionListener listener : listeners) {
                        listener.transactionCommitted();
                    }
                }
            } catch (SQLException ex) {
                throw jdbcSqlTemplate.translate(ex);
            }
        }
    }

    public void rollback() {
        rollback(true);
        init();
    }

    protected void rollback(boolean clearMarkers) {
        if (connection != null) {
            try {
                if (clearMarkers) {
                    markers.clear();
                }
                if (!autoCommit) {
                    connection.rollback();
                    for (ISqlTransactionListener listener : listeners) {
                        listener.transactionRolledBack();
                    }
                }
            } catch (SQLException ex) {
                // do nothing
            }
        }
    }

    public void close() {
        if (connection != null) {
            JdbcSqlTemplate.close(pstmt);
            try {
                connection.setAutoCommit(this.oldAutoCommitValue);
            } catch (SQLException ex) {
                // do nothing
            }
            JdbcSqlTemplate.close(connection);
            connection = null;
            SqlUtils.removeSqlTransaction(this);
        }
    }

    public int flush() {
        int rowsUpdated = 0;
        if (markers.size() > 0 && pstmt != null) {
            try {
                int[] updates = pstmt.executeBatch();
                for (int i : updates) {
                    rowsUpdated += normalizeUpdateCount(i);
                }
                markers.clear();
            } catch (BatchUpdateException ex) {
                removeMarkersThatWereSuccessful(ex);
                throw jdbcSqlTemplate.translate(ex);
            } catch (SQLException ex) {
                throw jdbcSqlTemplate.translate(ex);
            }
        }
        return rowsUpdated;
    }

    @Override
    public Row queryForRow(String sql, Object... args) {
        List<Row> rows = query(sql, new RowMapper(), args, null);
        if (rows.size() > 0) {
            return rows.get(0);
        } else {
            return null;
        }
    }

    public int queryForInt(String sql, Object... args) {
        Integer val = queryForObject(sql, Integer.class, args);
        if (val == null) {
            val = Integer.MIN_VALUE;
        }
        return val;
    }

    public long queryForLong(String sql, Object... args) {
        Long val = queryForObject(sql, Long.class, args);
        if (val == null) {
            val = Long.MIN_VALUE;
        }
        return val;
    }

    public <T> T queryForObject(final String sql, final Class<T> clazz, final Object... args) {
        return executeCallback(new IConnectionCallback<T>() {
            public T execute(Connection con) throws SQLException {
                T result = null;
                Statement stmt = null;
                ResultSet rs = null;
                try {
                    if (args != null && args.length > 0) {
                        PreparedStatement ps = con.prepareStatement(sql);
                        stmt = ps;
                        stmt.setQueryTimeout(jdbcSqlTemplate.getSettings().getQueryTimeout());
                        jdbcSqlTemplate.setValues(ps, args);
                        long startTime = System.currentTimeMillis();
                        rs = ps.executeQuery();
                        long endTime = System.currentTimeMillis();
                        logSqlBuilder.logSql(log, sql, args, null, (endTime - startTime));
                    } else {
                        stmt = con.createStatement();
                        stmt.setQueryTimeout(jdbcSqlTemplate.getSettings().getQueryTimeout());
                        long startTime = System.currentTimeMillis();
                        rs = stmt.executeQuery(sql);
                        long endTime = System.currentTimeMillis();
                        logSqlBuilder.logSql(log, sql, args, null, (endTime - startTime));
                    }
                    if (rs.next()) {
                        result = jdbcSqlTemplate.getObjectFromResultSet(rs, clazz);
                    }
                } catch (SQLException e) {
                    throw logSqlBuilder.logSqlAfterException(log, sql, args, e);
                } finally {
                    JdbcSqlTemplate.close(rs);
                    JdbcSqlTemplate.close(stmt);
                }
                return result;
            }
        });
    }

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Map<String, Object> namedParams) {
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String newSql = NamedParameterUtils.substituteNamedParameters(parsedSql, namedParams);
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql, namedParams);
        return query(newSql, mapper, params, null);
    }

    public <T> List<T> query(final String sql, final ISqlRowMapper<T> mapper, final Object[] args,
            final int[] types) {
        return executeCallback(new IConnectionCallback<List<T>>() {
            public List<T> execute(Connection c) throws SQLException {
                PreparedStatement st = null;
                ResultSet rs = null;
                try {
                    st = c.prepareStatement(sql);
                    st.setQueryTimeout(jdbcSqlTemplate.getSettings().getQueryTimeout());
                    if (args != null) {
                        jdbcSqlTemplate.setValues(st, args, types, jdbcSqlTemplate.getLobHandler());
                    }
                    st.setFetchSize(jdbcSqlTemplate.getSettings().getFetchSize());
                    long startTime = System.currentTimeMillis();
                    rs = st.executeQuery();
                    long endTime = System.currentTimeMillis();
                    logSqlBuilder.logSql(log, sql, args, types, (endTime - startTime));
                    List<T> list = new ArrayList<T>();
                    ResultSetMetaData rsMetaData = rs.getMetaData();
                    int columnCount = rsMetaData.getColumnCount();
                    while (rs.next()) {
                        Row row = JdbcSqlReadCursor.getMapForRow(rs, rsMetaData, columnCount,
                                jdbcSqlTemplate.getSettings().isReadStringsAsBytes(), false);
                        T value = mapper.mapRow(row);
                        list.add(value);
                    }
                    return list;
                } catch (SQLException e) {
                    throw logSqlBuilder.logSqlAfterException(log, sql, args, e);
                } finally {
                    JdbcSqlTemplate.close(rs);
                    JdbcSqlTemplate.close(st);
                }
            }
        });
    }

    public int execute(final String sql) {
        return executeCallback(new IConnectionCallback<Integer>() {
            public Integer execute(Connection con) throws SQLException {
                Statement stmt = null;
                ResultSet rs = null;
                try {
                    stmt = con.createStatement();
                    long startTime = System.currentTimeMillis();
                    boolean hasResults = stmt.execute(sql);
                    long endTime = System.currentTimeMillis();
                    logSqlBuilder.logSql(log, sql, null, null, (endTime - startTime));
                    if (hasResults) {
                        rs = stmt.getResultSet();
                        while (rs.next()) {
                        }
                    }
                    return stmt.getUpdateCount();
                } catch (SQLException e) {
                    throw logSqlBuilder.logSqlAfterException(log, sql, null, e);
                } finally {
                    JdbcSqlTemplate.close(rs);
                    JdbcSqlTemplate.close(stmt);
                }
            }
        });
    }

    public int prepareAndExecute(final String sql, final Object[] args, final int[] types) {
        return executeCallback(new IConnectionCallback<Integer>() {
            public Integer execute(Connection con) throws SQLException {
                PreparedStatement stmt = null;
                ResultSet rs = null;
                try {
                    stmt = con.prepareStatement(sql);
                    jdbcSqlTemplate.setValues(stmt, args, types, jdbcSqlTemplate.getLobHandler());
                    return executePreparedUpdate(stmt, sql, args, types);
                } catch (SQLException e) {
                    throw logSqlBuilder.logSqlAfterException(log, sql, args, e);
                } finally {
                    JdbcSqlTemplate.close(rs);
                    JdbcSqlTemplate.close(stmt);
                }
            }
        });
    }

    public int prepareAndExecute(final String sql, final Map<String, Object> args) {
        return executeCallback(new IConnectionCallback<Integer>() {
            public Integer execute(Connection con) {
                Integer rowsUpdated = null;
                NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(con, true));
                long startTime = System.currentTimeMillis();
                rowsUpdated = jdbcTemplate.update(sql, args);
                long endTime = System.currentTimeMillis();
                logSqlBuilder.logSql(log, sql, args.values().toArray(), null, (endTime - startTime));
                return rowsUpdated;
            }
        });
    }

    public int prepareAndExecute(final String sql, final Object... args) {
        return executeCallback(new IConnectionCallback<Integer>() {
            public Integer execute(Connection con) throws SQLException {
                PreparedStatement stmt = null;
                ResultSet rs = null;
                try {
                    stmt = con.prepareStatement(sql);
                    if (args != null && args.length > 0) {
                        jdbcSqlTemplate.setValues(stmt, args);
                    }
                    long startTime = System.currentTimeMillis();
                    boolean hasResults = stmt.execute();
                    long endTime = System.currentTimeMillis();
                    logSqlBuilder.logSql(log, sql, args, null, (endTime - startTime));
                    if (hasResults) {
                        rs = stmt.getResultSet();
                        while (rs.next()) {
                        }
                    }
                    return stmt.getUpdateCount();
                } catch (SQLException e) {
                    throw logSqlBuilder.logSqlAfterException(log, sql, args, e);
                } finally {
                    JdbcSqlTemplate.close(rs);
                    JdbcSqlTemplate.close(stmt);
                }
            }
        });
    }

    public <T> T executeCallback(IConnectionCallback<T> callback) {
        try {
            return callback.execute(this.connection);
        } catch (SQLException ex) {
            throw this.jdbcSqlTemplate.translate(ex);
        }
    }

    /**
     * According to the executeUpdate() javadoc -2 means that the result was successful, but that the number of rows affected is unknown. since we know that
     * only one row is suppose to be affected, we'll default to 1.
     * 
     * @param value
     */
    protected final int normalizeUpdateCount(int value) {
        if (value == Statement.SUCCESS_NO_INFO) {
            value = 1;
        }
        return value;
    }

    protected void removeMarkersThatWereSuccessful(BatchUpdateException ex) {
        int[] updateCounts = ex.getUpdateCounts();
        if (updateCounts != null) {
            Iterator<Object> it = markers.iterator();
            int index = 0;
            while (it.hasNext()) {
                it.next();
                if (updateCounts.length > index && normalizeUpdateCount(updateCounts[index]) > 0) {
                    it.remove();
                }
                index++;
            }
        }
    }

    public void prepare(String sql) {
        try {
            if (this.markers.size() > 0) {
                throw new IllegalStateException(
                        "Cannot prepare a new batch before the last batch has been flushed.");
            }
            JdbcSqlTemplate.close(pstmt);
            pstmt = connection.prepareStatement(sql);
            psql = sql;
        } catch (SQLException ex) {
            throw jdbcSqlTemplate.translate(new SqlException("Exception while preparing sql [" + sql + "]", ex));
        }
    }

    public int addRow(Object marker, Object[] args, int[] argTypes) {
        int rowsUpdated = 0;
        try {
            if (args != null) {
                jdbcSqlTemplate.setValues(pstmt, args, argTypes, jdbcSqlTemplate.getLobHandler());
            }
            if (inBatchMode) {
                if (marker == null) {
                    marker = Integer.valueOf(markers.size() + 1);
                }
                markers.add(marker);
                long start = System.currentTimeMillis();
                pstmt.addBatch();
                long end = System.currentTimeMillis();
                logSqlBuilder.logSql(log, "addBatch()", psql, args, argTypes, (end - start));
                if (markers.size() >= this.batchSize) {
                    rowsUpdated = flush();
                }
            } else {
                rowsUpdated = executePreparedUpdate(pstmt, psql, args, argTypes);
            }
        } catch (SQLException ex) {
            throw jdbcSqlTemplate.translate(ex);
        }
        return rowsUpdated;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchSize() {
        return this.batchSize;
    }

    protected int executePreparedUpdate(PreparedStatement preparedStatement, String sql, Object[] args, int[] argTypes) throws SQLException {
        int rowsUpdated = 0;
        long start = System.currentTimeMillis();
        if (jdbcSqlTemplate.getSettings().isAllowUpdatesWithResults()) {
            rowsUpdated = executeAllowingResults(preparedStatement);
        } else {
            rowsUpdated = preparedStatement.executeUpdate();
        }
        long end = System.currentTimeMillis();
        logSqlBuilder.logSql(log, sql, args, argTypes, (end - start));
        return rowsUpdated;
    }

    protected int executeAllowingResults(PreparedStatement preparedStatement) throws SQLException {
        int rowsUpdated = 0;
        boolean hasResultsFlag = preparedStatement.execute();
        int currentUpdateCount = preparedStatement.getUpdateCount();
        if (currentUpdateCount != -1) {
            rowsUpdated = currentUpdateCount;
        }
        while (hasResultsFlag || currentUpdateCount != -1) {
            hasResultsFlag = preparedStatement.getMoreResults();
            currentUpdateCount = preparedStatement.getUpdateCount();
            if (currentUpdateCount != -1) {
                rowsUpdated = currentUpdateCount;
            }
        }
        return rowsUpdated;
    }

    public List<Object> getUnflushedMarkers(boolean clear) {
        List<Object> ret = new ArrayList<Object>(markers);
        if (clear) {
            markers.clear();
        }
        return ret;
    }

    public Connection getConnection() {
        return connection;
    }

    public void allowInsertIntoAutoIncrementColumns(boolean value, Table table, String quote, String catalogSeparator, String schemaSepartor) {
    }

    public long insertWithGeneratedKey(String sql, String column, String sequenceName,
            Object[] args, int[] types) {
        try {
            return jdbcSqlTemplate.insertWithGeneratedKey(connection, sql, column, sequenceName,
                    args, types);
        } catch (SQLException ex) {
            throw jdbcSqlTemplate.translate(ex);
        }
    }

    public LogSqlBuilder getLogSqlBuilder() {
        return logSqlBuilder;
    }

    public void setLogSqlBuilder(LogSqlBuilder logSqlBuilder) {
        this.logSqlBuilder = logSqlBuilder;
    }

    public void clearBatch() {
        if (this.inBatchMode && pstmt != null) {
            try {
                pstmt.clearBatch();
            } catch (SQLException e) {
                log.warn("Unable to clear batch mode for transaction. ", e);
            }
        }
    }

    @Override
    public boolean isAllowInsertIntoAutoIncrement() {
        return false;
    }
}