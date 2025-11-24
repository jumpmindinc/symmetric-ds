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
package org.jumpmind.db.platform.oracle;

import java.util.ArrayList;
import java.util.List;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.sql.DataSource;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * Provides support for the Oracle platform.
 */
public class OracleDatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The standard Oracle jdbc driver. */
    public static final String JDBC_DRIVER = "oracle.jdbc.driver.OracleDriver";
    /* The old Oracle jdbc driver. */
    public static final String JDBC_DRIVER_OLD = "oracle.jdbc.dnlddriver.OracleDriver";
    /* The thin subprotocol used by the standard Oracle driver. */
    public static final String JDBC_SUBPROTOCOL_THIN = "oracle:thin";
    /* The thin subprotocol used by the standard Oracle driver. */
    public static final String JDBC_SUBPROTOCOL_OCI8 = "oracle:oci8";
    /* The old thin subprotocol used by the standard Oracle driver. */
    public static final String JDBC_SUBPROTOCOL_THIN_OLD = "oracle:dnldthin";

    /*
     * Creates a new platform instance.
     */
    public OracleDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected OracleDdlBuilder createDdlBuilder() {
        return new OracleDdlBuilder();
    }

    @Override
    protected OracleDdlReader createDdlReader() {
        return new OracleDdlReader(this);
    }

    @Override
    protected OracleJdbcSqlTemplate createSqlTemplate() {
        return new OracleJdbcSqlTemplate(dataSource, settings, new OracleLobHandler(settings.getJdbcLobHandling()), getDatabaseInfo());
    }

    @Override
    protected ISqlTemplate createSqlTemplateDirty() {
        return sqlTemplate;
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.ORACLE;
    }

    @Override
    public String getDefaultCatalog() {
        return null;
    }

    @Override
    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = getSqlTemplate()
                    .queryForObject("SELECT sys_context('USERENV', 'CURRENT_SCHEMA') FROM dual", String.class);
        }
        return defaultSchema;
    }

    @Override
    public boolean canColumnBeUsedInWhereClause(Column column) {
        return !(isLob(column) || isGeometry(column)) && super.canColumnBeUsedInWhereClause(column);
    }

    private boolean isGeometry(Column column) {
        String name = column.getJdbcTypeName();
        if (name != null && (name.toUpperCase().contains(TypeMap.GEOMETRY) ||
                name.toUpperCase().contains(TypeMap.GEOGRAPHY))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE OR REPLACE TRIGGER TEST_TRIGGER AFTER UPDATE ON " + delimiter
                + PERMISSION_TEST_TABLE_NAME + delimiter + " BEGIN END";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, triggerSql);
        try {
            getSqlTemplate().update(triggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE TRIGGER permission or TRIGGER permission");
        }
        return result;
    }

    @Override
    public PermissionResult getExecuteSymPermission() {
        String executeSql = "SELECT DBMS_LOB.GETLENGTH('TEST'), UTL_RAW.CAST_TO_RAW('TEST') FROM DUAL";
        PermissionResult result = new PermissionResult(PermissionType.EXECUTE, executeSql);
        try {
            getSqlTemplate().update(executeSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant EXECUTE on DBMS_LOB and UTL_RAW");
        }
        return result;
    }

    @Override
    public PermissionResult getLogMinePermission() {
        final PermissionResult result = new PermissionResult(PermissionType.LOG_MINE, "Use LogMiner");
        try {
            StringBuilder missingGrants = new StringBuilder();
            for (String name : new String[] { "EXECUTE_CATALOG_ROLE" }) {
                if (!hasPrivilege(name)) {
                    if (missingGrants.length() > 0) {
                        missingGrants.append(", ");
                    }
                    missingGrants.append(name);
                }
            }
            String[] systemPrivs = new String[] { "SELECT ANY DICTIONARY", "ALTER SESSION", "SELECT ANY TRANSACTION" };
            if (sqlTemplate.getDatabaseMajorVersion() >= 12) {
                systemPrivs = ArrayUtils.add(systemPrivs, "LOGMINING");
            }
            for (String name : systemPrivs) {
                if (!hasSystemPrivilege(name)) {
                    if (missingGrants.length() > 0) {
                        missingGrants.append(", ");
                    }
                    missingGrants.append(name);
                }
            }
            String logMode = getSqlTemplate().queryForString("SELECT LOG_MODE FROM V$DATABASE");
            if (!logMode.equals("ARCHIVELOG")) {
                if (missingGrants.length() > 0) {
                    missingGrants.append(", ");
                }
                missingGrants.append("DATABASE MUST BE IN ARCHIVE LOG MODE");
            }
            if (missingGrants.length() > 0) {
                log.error("Missing privileges: {}", missingGrants.toString());
                result.setSolution("Grant " + missingGrants.toString());
                result.setStatus(Status.FAIL);
            } else {
                result.setStatus(Status.PASS);
            }
            String supplementalLogging = sqlTemplate.queryForString("select SUPPLEMENTAL_LOG_DATA_ALL from v$database");
            if ("NO".equals(supplementalLogging)) {
                log.error("Missing Oracle log miner alter for supplemental log data.");
                result.setSolution("alter database add supplemental log data (all) columns");
                result.setStatus(Status.FAIL);
                return result;
            }
        } catch (Exception e) {
            log.error("Error checking privileges", e);
            result.setException(e);
            result.setStatus(Status.FAIL);
            return result;
        }
        return result;
    }

    private boolean hasSystemPrivilege(String name) {
        return getSqlTemplate().queryForInt("select count(*) from user_sys_privs where privilege = ?", name) > 0;
    }
    /*
     * private boolean hasDbaSystemPrivilege(String name) { return getSqlTemplate().queryForInt("select count(*) from dba_sys_privs where privilege = ?", name)
     * > 0; }
     */

    private boolean hasPrivilege(String name) {
        return getSqlTemplate().queryForInt("select count(*) from user_role_privs where granted_role = ?", name) > 0;
    }

    @Override
    public long getEstimatedRowCount(Table table) {
        return getSqlTemplateDirty().queryForLong(
                "select nvl(num_rows, -1) from all_tables where table_name = ? and owner = ?", table.getName(),
                table.getSchema());
    }

    @Override
    public List<Transaction> getTransactions() {
        ISqlTemplate template = getSqlTemplate();
        List<Transaction> transactions = new ArrayList<Transaction>();
        if (template.getDatabaseMajorVersion() >= 10) {
            String sql = "select" +
                    "  blocked.sid," +
                    "  blocked.username," +
                    "  blocked.status," +
                    "  blocked.blocking_session," +
                    "  sql.last_load_time," +
                    "  sql.sql_text " +
                    "from v$session blocked " +
                    "left join v$sqlarea sql" +
                    "  on blocked.sql_id = sql.sql_id";
            for (Row row : template.query(sql)) {
                Transaction transaction = new Transaction(row.getString("sid"), row.getString("username"),
                        row.getString("blocking_session"), row.getDateTime("last_load_time"), row.getString("sql_text"));
                transaction.setStatus(row.getString("status"));
                transactions.add(transaction);
            }
        }
        return transactions;
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
        int orderIndex = Strings.CI.lastIndexOf(sql, "order by");
        String order = sql.substring(orderIndex);
        String innerSql = sql.substring(0, orderIndex - 1);
        innerSql = Strings.CI.replace(innerSql, " from", ", ROW_NUMBER() over (" + order + ") as row_num from");
        return "select * from (" + innerSql + ") " +
                "where row_num between " + (offset + 1) + " and " + (offset + limit);
    }

    @Override
    public String massageForObjectAlreadyExists(String sql) {
        if (sql.toUpperCase().contains("CREATE TABLE")) {
            return sql;
        }
        return Strings.CI.replaceOnce(sql, "create", "create or replace");
    }

    @Override
    public boolean supportsSliceTables() {
        return true;
    }

    @Override
    public String getSliceTableSql(String columnName, int sliceNum, int totalSlices) {
        return "mod(ora_hash(rowid), " + totalSlices + ") = " + sliceNum;
    }

    @Override
    public String getCharSetName() {
        return getSqlTemplate().queryForObject("select value from nls_database_parameters where\r\n"
                + "parameter='NLS_CHARACTERSET'", String.class);
    }
}
