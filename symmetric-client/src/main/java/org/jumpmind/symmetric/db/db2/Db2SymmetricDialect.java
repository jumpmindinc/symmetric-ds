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
package org.jumpmind.symmetric.db.db2;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;

/*
 * A dialect that is specific to DB2 databases
 */
public class Db2SymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    // DB2 Variables
    public static final String VAR_SOURCE_NODE_ID = "_source_node_id";
    public static final String VAR_TRIGGER_DISABLED = "_trigger_disabled";
    public static final String FUNCTION_TRANSACTION_ID = "_transactionid";
    static final String SQL_DROP_FUNCTION = "DROP FUNCTION $(functionName)";

    public Db2SymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new Db2TriggerTemplate(this);
    }

    public boolean createOrAlterTablesIfNecessary(String... tables) {
        boolean tablesCreated = super.createOrAlterTablesIfNecessary(tables);
        if (tablesCreated) {
            log.info("Resetting auto increment columns for {}{}", parameterService.getTablePrefix(), "_data");
            long dataId = platform.getSqlTemplate().queryForLong("select max(data_id) from " + parameterService.getTablePrefix() + "_data")
                    + 1;
            platform.getSqlTemplate()
                    .update("alter table " + parameterService.getTablePrefix() + "_data alter column data_id restart with " + dataId);
        }
        return tablesCreated;
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema, String tableName, String triggerName) {
        if (schema == null) {
            schema = platform.getDefaultSchema();
        }
        if (schema != null) {
            schema = schema.toUpperCase();
        }
        triggerName = triggerName == null ? null : triggerName.toUpperCase();
        return platform.getSqlTemplate().queryForInt(
                "SELECT COUNT(*) FROM " + getSystemSchemaName() + ".SYSTRIGGERS WHERE NAME = ? AND SCHEMA = ?",
                new Object[] { triggerName, schema }) > 0;
    }

    @Override
    public String massageDataExtractionSql(String sql, boolean isContainsBigLob) {
        /*
         * Remove tranaction_id from the sql because DB2 doesn't support transactions. In fact, DB2 iSeries does return results because the query asks for every
         * column in the table PLUS the router_id. We max out the size of the table on iSeries so when you try to return the entire table + additional columns
         * we go past the max size for a row
         */
        if (!this.getParameterService().is(ParameterConstants.DB2_CAPTURE_TRANSACTION_ID, false)) {
            sql = sql.replace("d.transaction_id, ", "");
        }
        return super.massageDataExtractionSql(sql, isContainsBigLob);
    }

    protected String getSystemSchemaName() {
        return "SYSIBM";
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String sql = "select " + getSourceNodeExpression() + " from sysibm.sysdummy1";
        try {
            platform.getSqlTemplate().query(sql);
        } catch (Exception e) {
            log.debug("Failed checking for variable (usually means it doesn't exist yet) '" + sql + "'", e);
            sql = "create variable " + getSourceNodeExpression() + " varchar(50)";
            logSql(sql, ddl);
            if (ddl == null) {
                platform.getSqlTemplate().update(sql);
            }
        }
        sql = "select " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " from sysibm.sysdummy1";
        try {
            platform.getSqlTemplate().query(sql);
        } catch (Exception e) {
            log.debug("Failed checking for variable (usually means it doesn't exist yet) '" + sql + "'", e);
            sql = "create variable " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " varchar(50)";
            logSql(sql, ddl);
            if (ddl == null) {
                platform.getSqlTemplate().update(sql);
            }
        }
        if (this.getParameterService().is(ParameterConstants.DB2_CAPTURE_TRANSACTION_ID, false)) {
            String transactionIdFunction = this.parameterService.getTablePrefix() + FUNCTION_TRANSACTION_ID;
            sql = "CREATE OR REPLACE FUNCTION $(functionName)()                                     "
                    + "     RETURNS VARCHAR(100)                                                      "
                    + "     LANGUAGE SQL                                                              "
                    + "     READS SQL DATA                                                            "
                    + "     RETURN                                                                    "
                    + "          select c.application_id || '_' || u.uow_id                           "
                    + "          from sysibmadm.mon_connection_summary c ,sysibmadm.mon_current_uow u "
                    + "          where u.application_handle = c.application_handle and c.application_id = application_id()    ";
            try {
                install(sql, transactionIdFunction, ddl);
            } catch (Exception e) {
                log.warn("Unable to install function " + this.parameterService.getTablePrefix() + FUNCTION_TRANSACTION_ID);
            }
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        if (this.getParameterService().is(ParameterConstants.DB2_CAPTURE_TRANSACTION_ID, false)) {
            String transactionIdFunction = this.parameterService.getTablePrefix() + FUNCTION_TRANSACTION_ID;
            try {
                uninstall(SQL_DROP_FUNCTION, transactionIdFunction);
            } catch (Exception e) {
                log.warn("Unable to uninstall function " + this.parameterService.getTablePrefix() + FUNCTION_TRANSACTION_ID);
            }
        }
    }

    @Override
    public boolean isBlobSyncSupported() {
        return true;
    }

    @Override
    public boolean isClobSyncSupported() {
        return true;
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("set " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " = null");
        transaction.prepareAndExecute("set " + getSourceNodeExpression() + " = null");
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("set " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " = 1");
        if (nodeId != null) {
            transaction.prepareAndExecute("set " + getSourceNodeExpression() + " = '" + nodeId + "'");
        }
    }

    public String getSyncTriggersExpression() {
        return parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " is null";
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger) {
        if (this.getParameterService().is(ParameterConstants.DB2_CAPTURE_TRANSACTION_ID, false)) {
            return "sym_transactionid()";
        } else {
            return "null";
        }
    }

    @Override
    public boolean supportsTransactionId() {
        return this.getParameterService().is(ParameterConstants.DB2_CAPTURE_TRANSACTION_ID, false);
    }

    public void cleanDatabase() {
    }

    @Override
    public void truncateTable(String tableName) {
        platform.getSqlTemplate().update("delete from " + tableName);
    }

    @Override
    protected String getDbSpecificDataHasChangedCondition(Trigger trigger) {
        return "var_old_data is null or var_row_data != var_old_data";
    }

    @Override
    public String getSourceNodeExpression() {
        return parameterService.getTablePrefix() + VAR_SOURCE_NODE_ID;
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "select current_timestamp from sysibm.sysdummy1";
    }
}
