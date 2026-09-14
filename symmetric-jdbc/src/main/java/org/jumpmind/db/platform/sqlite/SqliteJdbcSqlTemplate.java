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
package org.jumpmind.db.platform.sqlite;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Date;

import javax.sql.DataSource;

import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.jumpmind.util.FormatUtils;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;

public class SqliteJdbcSqlTemplate extends JdbcSqlTemplate {
    public SqliteJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings, SymmetricLobHandler lobHandler,
            DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
    }

    @Override
    public boolean isUniqueKeyViolation(Throwable ex) {
        SQLException sqlEx = findSQLException(ex);
        return (sqlEx != null && sqlEx.getMessage() != null && (sqlEx.getMessage().contains("[SQLITE_CONSTRAINT]") ||
                sqlEx.getMessage().contains("[SQLITE_CONSTRAINT_PRIMARYKEY]") ||
                sqlEx.getMessage().contains("[SQLITE_CONSTRAINT_UNIQUE]")));
    }

    @Override
    public boolean supportsGetGeneratedKeys() {
        return false;
    }

    @Override
    public String getSelectLastInsertIdSql(String sequenceName) {
        return "SELECT last_insert_rowid();";
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObjectFromResultSet(ResultSet rs, Class<T> clazz) throws SQLException {
        if (Date.class.isAssignableFrom(clazz) || Timestamp.class.isAssignableFrom(clazz)) {
            String s = rs.getString(1);
            Date d = null;
            if (s != null) {
                d = FormatUtils.parseDate(s, FormatUtils.TIMESTAMP_PATTERNS);
            }
            if (d != null && Timestamp.class.isAssignableFrom(clazz)) {
                return (T) new Timestamp(d.getTime());
            } else {
                return (T) d;
            }
        } else {
            return super.getObjectFromResultSet(rs, clazz);
        }
    }

    @Override
    public void setValues(PreparedStatement ps, Object[] args, int[] argTypes,
            SymmetricLobHandler lobHandler) throws SQLException {
        for (int i = 1; i <= args.length; i++) {
            Object arg = args[i - 1];
            int argType = argTypes != null && argTypes.length >= i ? argTypes[i - 1] : SqlTypeValue.TYPE_UNKNOWN;
            if (argType == Types.BLOB && lobHandler != null && arg instanceof byte[]) {
                lobHandler.setBlobAsBytes(ps, i, (byte[]) arg);
            } else if (argType == Types.BLOB && lobHandler != null && arg instanceof String) {
                lobHandler.setBlobAsBytes(ps, i, arg.toString().getBytes(Charset.defaultCharset()));
            } else if (argType == Types.CLOB && lobHandler != null) {
                lobHandler.setClobAsString(ps, i, (String) arg);
            } else if (arg != null && argType == Types.DATE && arg instanceof Date) {
                Date clone = (Date) (((Date) arg).clone());
                arg = FormatUtils.TIMESTAMP_FORMATTER.format(DateUtils.truncate(clone, Calendar.DATE));
                args[i - 1] = arg;
                StatementCreatorUtils.setParameterValue(ps, i, verifyArgType(arg, argType), arg);
            } else if (arg != null && (arg instanceof Date || arg instanceof Timestamp)) {
                arg = FormatUtils.TIMESTAMP_FORMATTER.format(arg);
                args[i - 1] = arg;
                StatementCreatorUtils.setParameterValue(ps, i, verifyArgType(arg, argType), arg);
            } else {
                if (arg instanceof BigDecimal) {
                    arg = ((BigDecimal) arg).doubleValue();
                    args[i - 1] = arg;
                }
                StatementCreatorUtils.setParameterValue(ps, i, verifyArgType(arg, argType), arg);
            }
        }
    }

    @Override
    public void setValues(PreparedStatement ps, Object[] args) throws SQLException {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg != null && (arg instanceof Date || arg instanceof Timestamp)) {
                    arg = FormatUtils.TIMESTAMP_FORMATTER.format(arg);
                    args[i] = arg;
                }
                doSetValue(ps, i + 1, arg);
            }
        }
    }
}
