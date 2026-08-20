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
package org.jumpmind.db.platform.sybase;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.SqlTypeValue;

public class SybaseJdbcSqlTemplate extends JdbcSqlTemplate implements ISqlTemplate {
    private static final Logger log = LoggerFactory.getLogger(SybaseJdbcSqlTemplate.class);
    protected static final String NATIVE_PREPARED_STATEMENT_NAME = "com.sybase.jdbc4.jdbc.SybPreparedStatement";
    protected int jdbcMajorVersion;
    protected boolean isUsingJtds;

    public SybaseJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        primaryKeyViolationCodes = new int[] { 423, 511, 515, 530, 547, 2601, 2615, 2714 };
        uniqueKeyViolationNameRegex = new String[] { "unique index '(.*)'" };
        foreignKeyViolationCodes = new int[] { 546 };
        foreignKeyChildExistsViolationCodes = new int[] { 547 };
        Connection c = null;
        try {
            c = dataSource.getConnection();
            jdbcMajorVersion = c.getMetaData().getJDBCMajorVersion();
            if (dataSource.getConnection().getMetaData().getURL().contains("jtds")) {
                isUsingJtds = true;
            }
        } catch (SQLException ex) {
            jdbcMajorVersion = -1;
        } finally {
            close(c);
        }
    }

    @Override
    protected boolean allowsNullForIdentityColumn() {
        return false;
    }

    protected void setDecimalValue(PreparedStatement ps, int i, Object arg, int argType) throws SQLException {
        if ((argType == Types.DECIMAL || argType == Types.NUMERIC) && arg != null && arg.equals("NaN")) {
            setNanOrNull(ps, i, arg, argType);
        } else {
            PreparedStatement nativeStatement = getNativeStmt(ps);
            if (nativeStatement != null && NATIVE_PREPARED_STATEMENT_NAME.equals(nativeStatement.getClass().getName())) {
                Class<?> clazz = nativeStatement.getClass();
                Class<?>[] parameterTypes = new Class[] { int.class, BigDecimal.class, int.class, int.class };
                BigDecimal value = null;
                if (arg instanceof BigDecimal) {
                    value = (BigDecimal) arg;
                } else if (arg instanceof Boolean) {
                    // Leave the BigDecimal null for a Boolean
                } else if (arg != null) {
                    value = new BigDecimal(arg.toString());
                }
                int precision = 1;
                int scale = 0;
                if (value != null) {
                    scale = value.scale();
                    precision = value.precision();
                    if (precision < scale) {
                        precision = scale + 1;
                    }
                    if (precision > 127) {
                        precision = 127;
                        if (scale > 127) {
                            scale = 126;
                        }
                    }
                }
                Object[] params = new Object[] { Integer.valueOf(i), value, Integer.valueOf(precision), Integer.valueOf(scale) };
                try {
                    if (arg instanceof Long) {
                        params = new Object[] { Integer.valueOf(i), Long.valueOf(arg.toString()) };
                        parameterTypes = new Class[] { int.class, long.class };
                        Method method = clazz.getMethod("setLong", parameterTypes);
                        method.invoke(nativeStatement, params);
                    } else if (arg instanceof Integer) {
                        params = new Object[] { Integer.valueOf(i), Integer.valueOf(arg.toString()) };
                        parameterTypes = new Class[] { int.class, int.class };
                        Method method = clazz.getMethod("setInt", parameterTypes);
                        method.invoke(nativeStatement, params);
                    } else if (arg instanceof Boolean) {
                        Integer intValue = ((Boolean) arg) ? Integer.valueOf(1) : Integer.valueOf(0);
                        params = new Object[] { Integer.valueOf(i), intValue };
                        parameterTypes = new Class[] { int.class, int.class };
                        Method method = clazz.getMethod("setInt", parameterTypes);
                        method.invoke(nativeStatement, params);
                    } else {
                        Method method = clazz.getMethod("setBigDecimal", parameterTypes);
                        method.invoke(nativeStatement, params);
                    }
                } catch (Throwable e) {
                    if (e instanceof InvocationTargetException) {
                        e = ((InvocationTargetException) e).getTargetException();
                    }
                    log.warn(String.format("Error calling the Sybase stmt.setBigDecimal(%s) method", Arrays.toString(params)), e);
                    super.setDecimalValue(ps, i, arg, argType);
                }
            } else {
                super.setDecimalValue(ps, i, arg, argType);
            }
        }
    }

    @Override
    protected int getUpdateCount(Statement stmt) throws SQLException {
        int updateCount;
        do {
            updateCount = stmt.getUpdateCount();
        } while (stmt.getMoreResults());
        return updateCount;
    }

    private PreparedStatement getNativeStmt(PreparedStatement ps) {
        PreparedStatement stmt = ps;
        try {
            stmt = (PreparedStatement) ps.unwrap(Class.forName(NATIVE_PREPARED_STATEMENT_NAME));
        } catch (ClassNotFoundException ex) {
            log.debug("Cannot get native statement because missing class {}", NATIVE_PREPARED_STATEMENT_NAME);
        } catch (SQLException ex) {
            log.debug("Could not find a native preparedstatement using {}", ps.getClass().getName(), ex);
        } catch (AbstractMethodError e) {
            log.debug("Cannot get native statement because not implemented");
        }
        return stmt;
    }

    @Override
    public void setValues(PreparedStatement ps, Object[] args)
            throws SQLException {
        super.setValues(ps, args);
        if (args != null && args.length > 0) {
            int[] argTypes = new int[args.length];
            for (int i = 0; i < argTypes.length; i++) {
                argTypes[i] = SqlTypeValue.TYPE_UNKNOWN;
            }
            setValues(ps, args, argTypes, getLobHandler());
        }
    }

    public boolean supportsGetGeneratedKeys() {
        // needs to return true for jtds
        if (jdbcMajorVersion >= 4 || isUsingJtds) {
            return true;
        } else {
            return false;
        }
        // return jdbcMajorVersion >= 4;
    }

    protected String getSelectLastInsertIdSql(String sequenceName) {
        return "select @@identity";
    }
}
