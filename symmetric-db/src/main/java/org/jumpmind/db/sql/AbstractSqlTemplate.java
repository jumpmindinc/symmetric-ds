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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractSqlTemplate implements ISqlTemplate {
    protected final static Logger log = LoggerFactory.getLogger(AbstractSqlTemplate.class
            .getPackage().getName());
    protected boolean dateOverrideToTimestamp;
    protected String identifierQuoteString;
    protected LogSqlBuilder logSqlBuilder = new LogSqlBuilder();

    public <T> T queryForObject(String sql, ISqlRowMapper<T> mapper, Object... args) {
        List<T> list = query(sql, mapper, args);
        if (list != null && list.size() > 0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    public String queryForString(String sql, Object... args) {
        return queryForObject(sql, String.class, args);
    }

    public int queryForInt(String sql, Map<String, Object> params) {
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String newSql = NamedParameterUtils.substituteNamedParameters(parsedSql, params);
        Object[] args = NamedParameterUtils.buildValueArray(parsedSql, params);
        return queryForInt(newSql, args);
    }

    public int queryForInt(String sql, Object... args) {
        Integer number = queryForObject(sql, Integer.class, args);
        if (number != null) {
            return number.intValue();
        } else {
            return 0;
        }
    }

    public long queryForLong(String sql, Object... args) {
        Long number = queryForObject(sql, Long.class, args);
        if (number != null) {
            return number.longValue();
        } else {
            return 0l;
        }
    }

    public Map<String, Object> queryForMap(String sql, final String keyColumn,
            final String valueColumn, Object... args) {
        final Map<String, Object> map = new HashMap<String, Object>();
        query(sql, new ISqlRowMapper<Object>() {
            public Object mapRow(Row rs) {
                map.put(rs.getString(keyColumn), rs.getString(valueColumn));
                return null;
            }
        }, args);
        return map;
    }

    public <T> Map<String, T> queryForMap(final String sql, final ISqlRowMapper<T> mapper,
            final String keyColumn, Object... args) {
        final Map<String, T> result = new HashMap<String, T>();
        query(sql, new ISqlRowMapper<T>() {
            public T mapRow(Row row) {
                String keyName = row.getString(keyColumn);
                T object = mapper.mapRow(row);
                result.put(keyName, object);
                return object;
            }
        }, args);
        return result;
    }

    public <T> ISqlReadCursor<T> queryForCursor(String sql, ISqlRowMapper<T> mapper) {
        return this.queryForCursor(sql, mapper, null, null);
    }

    public <T> ISqlReadCursor<T> queryForCursor(String sql, ISqlRowMapper<T> mapper,
            IConnectionHandler connectionHandler, Object[] args,
            int[] types) {
        return queryForCursor(sql, mapper, args, types);
    }

    public <T> ISqlReadCursor<T> queryForCursor(String sql, ISqlRowMapper<T> mapper, boolean returnLobObjects) {
        return queryForCursor(sql, mapper);
    }

    public <T> ISqlReadCursor<T> queryForCursor(String sql, ISqlRowMapper<T> mapper, Map<String, Object> namedParams) {
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String newSql = NamedParameterUtils.substituteNamedParameters(parsedSql, namedParams);
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql, namedParams);
        return queryForCursor(newSql, mapper, null, params, null);
    }

    public List<Row> query(String sql) {
        return query(sql, (Object[]) null, (int[]) null);
    }

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Object... args) {
        return query(sql, mapper, args, null);
    }

    @Override
    public <T> List<T> queryWithHandler(String sql, ISqlRowMapper<T> mapper, IConnectionHandler conHandler, Object... params) {
        return query(sql, mapper, conHandler, params, null);
    }

    public Row queryForRow(String sql, Object... args) {
        return queryForObject(sql, new ISqlRowMapper<Row>() {
            public Row mapRow(Row row) {
                return row;
            }
        }, args);
    }

    @SuppressWarnings("unchecked")
    public <T, W> Map<T, W> query(String sql, String keyCol, String valueCol, Object[] args,
            int[] types) {
        List<Row> rows = query(sql, args, types);
        Map<T, W> map = new HashMap<T, W>(rows.size());
        for (Row row : rows) {
            map.put((T) row.get(keyCol), (W) row.get(valueCol));
        }
        return map;
    }

    public <T> List<T> query(String sql, int maxRowsToFetch, ISqlRowMapper<T> mapper,
            Object... params) {
        return query(sql, maxRowsToFetch, mapper, params, null);
    }

    public <T> List<T> query(String sql, int maxRowsToFetch, ISqlRowMapper<T> mapper,
            Map<String, Object> namedParams) {
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String newSql = NamedParameterUtils.substituteNamedParameters(parsedSql, namedParams);
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql, namedParams);
        return query(newSql, maxRowsToFetch, mapper, params, null);
    }

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Map<String, ?> namedParams) {
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
        String newSql = NamedParameterUtils.substituteNamedParameters(parsedSql, namedParams);
        Object[] params = NamedParameterUtils.buildValueArray(parsedSql, namedParams);
        return query(newSql, mapper, params, null);
    }

    public List<Row> query(String sql, Object[] args, int[] types) {
        return query(sql, new ISqlRowMapper<Row>() {
            public Row mapRow(Row row) {
                return row;
            }
        }, args, types);
    }

    public List<Row> query(String sql, Object[] args) {
        return query(sql, new ISqlRowMapper<Row>() {
            public Row mapRow(Row row) {
                return row;
            }
        }, args);
    }

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Object[] args, int[] types) {
        return query(sql, -1, mapper, args, types);
    }

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, IConnectionHandler handler, Object[] args, int[] types) {
        return query(sql, -1, mapper, handler, args, types);
    }

    public <T> List<T> query(String sql, int maxNumberOfRowsToFetch, ISqlRowMapper<T> mapper,
            Object[] args, int[] types) {
        IConnectionHandler handler = null;
        return query(sql, maxNumberOfRowsToFetch, mapper, handler, args, types);
    }

    public <T> List<T> query(String sql, int maxNumberOfRowsToFetch, ISqlRowMapper<T> mapper,
            IConnectionHandler handler, Object[] args, int[] types) {
        ISqlReadCursor<T> cursor = queryForCursor(sql, mapper, handler, args, types);
        try {
            T next = null;
            List<T> list = new ArrayList<T>();
            int rowCount = 0;
            do {
                next = cursor.next();
                if (next != null) {
                    list.add(next);
                    rowCount++;
                }
                if (maxNumberOfRowsToFetch > 0 && rowCount >= maxNumberOfRowsToFetch) {
                    break;
                }
            } while (next != null);
            return list;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int update(String sql, Object... values) {
        return update(sql, values, null);
    }

    protected String expandSql(String sql, Object[] args) {
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof SqlList) {
                    SqlList list = (SqlList) arg;
                    StringBuilder sqllist = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        sqllist.append("?");
                        if (i < list.size() - 1) {
                            sqllist.append(",");
                        }
                    }
                    sql = sql.replaceFirst(list.getReplacementToken(), sqllist.toString());
                } else if (arg instanceof SqlToken) {
                    SqlToken token = (SqlToken) arg;
                    sql = sql.replaceFirst(token.getReplacementToken(), "?");
                }
            }
        }
        return sql;
    }

    protected Object[] expandArgs(String sql, Object[] args) {
        if (args != null && args.length > 0) {
            List<Object> argsList = null;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof SqlList) {
                    if (argsList == null) {
                        argsList = new ArrayList<Object>();
                        for (int j = 0; j < i; j++) {
                            argsList.add(args[j]);
                        }
                    }
                    SqlList list = (SqlList) arg;
                    if (sql.contains(list.getReplacementToken())) {
                        for (Object listItem : list) {
                            argsList.add(listItem);
                        }
                    }
                } else if (arg instanceof SqlToken) {
                    if (argsList == null) {
                        argsList = new ArrayList<Object>();
                        for (int j = 0; j < i; j++) {
                            argsList.add(args[j]);
                        }
                    }
                    SqlToken token = (SqlToken) arg;
                    if (sql.contains(token.getReplacementToken())) {
                        argsList.add(token.getValue());
                    }
                } else if (argsList != null) {
                    argsList.add(arg);
                }
            }
            if (argsList != null) {
                args = argsList.toArray(new Object[argsList.size()]);
            }
        }
        return args;
    }

    public SqlException translate(Throwable ex) {
        return translate(ex.getMessage(), ex);
    }

    public SqlException translate(String message, Throwable ex) {
        if (isUniqueKeyViolation(ex) && !(ex instanceof UniqueKeyException)) {
            return new UniqueKeyException(ex);
        } else if (isDataTruncationViolation(ex)) {
            return new DataTruncationException(ex);
        } else if (ex instanceof SqlException) {
            return (SqlException) ex;
        } else {
            return new SqlException(message, ex);
        }
    }

    @Override
    public String getUniqueKeyViolationIndexName(Throwable ex) {
        return null;
    }

    @Override
    public boolean isForeignKeyChildExistsViolation(Throwable ex) {
        return false;
    }

    @Override
    public boolean isDeadlock(Throwable ex) {
        return false;
    }

    @Override
    public boolean doesObjectAlreadyExist(Throwable ex) {
        return false;
    }

    @Override
    public boolean doesObjectNotExist(Throwable ex) {
        return false;
    }

    @Override
    public void setThreadLocalConnectionHandler(IConnectionHandler handler) {
    }

    @Override
    public void clearThreadLocalConnectionHandler() {
    }
}
