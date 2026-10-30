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
package org.jumpmind.db.platform.duckdb;

import java.sql.Connection;
import java.sql.Time;
import java.sql.Types;
import java.util.Date;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class DuckDbDatabasePlatform extends AbstractJdbcDatabasePlatform {
    public static final String JDBC_DRIVER = "org.duckdb.DuckDBDriver";
    public static final String JDBC_SUBPROTOCOL = "duckdb";

    public DuckDbDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.DUCKDB;
    }

    @Override
    public String getClassName() {
        return DuckDbDatabasePlatform.class.getName();
    }

    @Override
    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("SELECT current_schema()", String.class);
        }
        return defaultSchema;
    }

    @Override
    public String getDefaultCatalog() {
        if (StringUtils.isBlank(defaultCatalog)) {
            defaultCatalog = (String) getSqlTemplate().queryForObject("SELECT current_catalog()", String.class);
        }
        return defaultCatalog;
    }

    @Override
    protected DuckDbDdlBuilder createDdlBuilder() {
        return new DuckDbDdlBuilder();
    }

    @Override
    protected IDdlReader createDdlReader() {
        return new DuckDbDdlReader(this);
    }

    @Override
    protected ISqlTemplate createSqlTemplate() {
        return new DuckDbJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    protected ISqlTemplate createSqlTemplateDirty() {
        DuckDbJdbcSqlTemplate sqlTemplateDirty = new DuckDbJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
        sqlTemplateDirty.setIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED);
        return sqlTemplateDirty;
    }

    @Override
    public boolean supportsLimitOffset() {
        return true;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql + " limit " + limit + " offset " + offset;
    }

    @Override
    public Date parseDate(int type, String value, boolean useVariableDates) {
        if (StringUtils.isNotBlank(value)) {
            if (type == Types.TIME) {
                return Time.valueOf(formatTimeValue(value));
            } else {
                return super.parseDate(type, value, useVariableDates);
            }
        } else {
            return null;
        }
    }

    protected String formatTimeValue(String t) {
        if (t == null) {
            return null;
        }
        if (t.indexOf(" ") > 0) {
            t = t.substring(t.indexOf(" ") + 1, t.length());
        }
        return t.substring(0, t.indexOf(".") > 0 ? t.indexOf(".") : t.length());
    }
}
