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
package org.jumpmind.db.platform.mysql;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;

public class MySqlJdbcSqlTemplate extends JdbcSqlTemplate {
    public MySqlJdbcSqlTemplate(DataSource dataSource, SqlTemplateSettings settings,
            SymmetricLobHandler lobHandler, DatabaseInfo databaseInfo) {
        super(dataSource, settings, lobHandler, databaseInfo);
        primaryKeyViolationCodes = new int[] { 1062 };
        uniqueKeyViolationNameRegex = new String[] { "Duplicate entry .* for key '(.*)'" };
        foreignKeyViolationCodes = new int[] { 1452, 1216 };
        foreignKeyChildExistsViolationCodes = new int[] { 1451 };
        deadlockCodes = new int[] { 1213 };
    }

    @Override
    public String getSelectLastInsertIdSql(String sequenceName) {
        return "select last_insert_id()";
    }

    @Override
    public void setValues(PreparedStatement ps, Object[] args, int[] argTypes, SymmetricLobHandler lobHandler) throws SQLException {
        for (int i = 1; i <= args.length; i++) {
            Object arg = args[i - 1];
            int argType = argTypes != null && argTypes.length >= i ? argTypes[i - 1] : SqlTypeValue.TYPE_UNKNOWN;
            setValue(ps, i, arg, argType, lobHandler);
        }
    }

    private void setValue(PreparedStatement ps, int i, Object arg, int argType, SymmetricLobHandler lobHandler) throws SQLException {
        try {
            if (argType == Types.BLOB && lobHandler != null && arg instanceof byte[]) {
                lobHandler.setBlobAsBytes(ps, i, (byte[]) arg);
            } else if (argType == Types.BLOB && lobHandler != null && arg instanceof String) {
                lobHandler.setBlobAsBytes(ps, i, arg.toString().getBytes(Charset.defaultCharset()));
            } else if (argType == Types.CLOB && lobHandler != null) {
                lobHandler.setClobAsString(ps, i, (String) arg);
            } else if ((argType == Types.DECIMAL || argType == Types.NUMERIC) && arg != null) {
                setDecimalValue(ps, i, arg, argType);
            } else if (argType == Types.TINYINT) {
                setTinyIntValue(ps, i, arg, argType);
            } else if (argType == Types.BIGINT && arg instanceof BigInteger
                    && ((BigInteger) arg).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                ps.setString(i, arg.toString());
            } else {
                StatementCreatorUtils.setParameterValue(ps, i, verifyArgType(arg, argType), arg);
            }
        } catch (SQLException ex) {
            String msg = String.format("Parameter arg '%s' type: %s caused exception: %s", arg,
                    TypeMap.getJdbcTypeName(argType), ex.getMessage());
            throw new SQLException(msg, ex);
        }
    }
}
