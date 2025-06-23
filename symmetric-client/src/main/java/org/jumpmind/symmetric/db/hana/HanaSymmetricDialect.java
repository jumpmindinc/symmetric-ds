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
package org.jumpmind.symmetric.db.hana;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;

public class HanaSymmetricDialect extends AbstractSymmetricDialect {
    static final String SYNC_TRIGGERS_DISABLED_USER_VARIABLE = "sync_triggers_disabled";
    static final String SYNC_TRIGGERS_DISABLED_NODE_VARIABLE = "sync_node_disabled";
    static final String SQL_DROP_FUNCTION = "DROP FUNCTION $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from functions where function_name = '$(functionName)';";

    public HanaSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new HanaTriggerTemplate(this);
        this.parameterService = parameterService;
    }

    @Override
    public void cleanDatabase() {
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    @Override
    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("set '" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "'='1'");
        if (nodeId != null) {
            transaction
                    .prepareAndExecute("set '" + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE + "'='" + nodeId + "'");
        }
    }

    @Override
    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("set '" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "'=''");
        transaction.prepareAndExecute("set '" + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE + "'=''");
    }

    @Override
    public String getSyncTriggersExpression() {
        return "SESSION_CONTEXT('" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "')  is null";
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger) {
        return parameterService.getTablePrefix() + "_" + "transaction_id()";
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String function = this.parameterService.getTablePrefix() + "_" + "transaction_id";
        if (installed(SQL_FUNCTION_INSTALLED, function)) {
            uninstall(SQL_DROP_FUNCTION, function);
        }
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String transactionId = this.parameterService.getTablePrefix() + "_" + "transaction_id";
        if (!installed(SQL_FUNCTION_INSTALLED, transactionId)) {
            String sql = "CREATE OR REPLACE function $(functionName)                                                                                                                                                             "
                    + "   returns output1 varchar(50) LANGUAGE SQLSCRIPT AS                                                                                                                                                 "
                    + "   begin                                                                                                                                                              "
                    + "      select transaction_id into output1 from M_TRANSACTIONS where connection_id = CURRENT_CONNECTION;                                                                                                         "
                    + "   end;    ";
            install(sql, transactionId, ddl);
        }
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalogName, String schema, String tableName, String triggerName) {
        return platform.getSqlTemplate().queryForInt("select count(*) from triggers where trigger_name like ? and subject_table_name like ?",
                new Object[] { triggerName, tableName.toUpperCase() }) > 0;
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName,
            String triggerName, String tableName, ISqlTransaction transaction) {
        schemaName = schemaName == null ? "" : (schemaName + ".");
        final String sql = "drop trigger " + schemaName + triggerName;
        logSql(sql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            log.info("Dropping {} trigger for {}", triggerName, Table.getFullyQualifiedTableName(catalogName, schemaName, tableName));
            transaction.execute(sql);
        }
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "select current_timestamp from dummy";
    }
}
