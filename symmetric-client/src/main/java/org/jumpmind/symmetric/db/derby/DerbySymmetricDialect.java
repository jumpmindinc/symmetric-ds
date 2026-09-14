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
package org.jumpmind.symmetric.db.derby;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;

public class DerbySymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SQL_DROP_FUNCTION = "DROP FUNCTION $(functionName)";
    static final String SQL_DROP_PROCEDURE = "DROP PROCEDURE $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from sys.sysaliases where alias = upper('$(functionName)')";

    public DerbySymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new DerbyTriggerTemplate(this);
        platform.getDatabaseInfo().setGeneratedColumnsSupported(!Version.isOlderThanVersion(getProductVersion(), "10.5"));
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema, String tableName,
            String triggerName) {
        return platform.getSqlTemplate().queryForInt(
                "select count(*) from sys.systriggers where triggername = ?",
                new Object[] { triggerName.toUpperCase() }) > 0;
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String escape = this.parameterService.getTablePrefix() + "_" + "escape";
        if (!installed(SQL_FUNCTION_INSTALLED, escape)) {
            String sql = "CREATE FUNCTION $(functionName)(STR VARCHAR(10000)) RETURNS                                                                                                                                            "
                    +
                    " VARCHAR(10000) PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA EXTERNAL NAME                                                                                                 "
                    +
                    " 'org.jumpmind.symmetric.db.derby.DerbyFunctions.escape'                                                                                                                ";
            install(sql, escape, ddl);
        }
        String clobToString = this.parameterService.getTablePrefix() + "_" + "clob_to_string";
        if (!installed(SQL_FUNCTION_INSTALLED, clobToString)) {
            String sql = "CREATE FUNCTION $(functionName)(columnName varchar(50),                                                                                                                                                "
                    +
                    " tableName varchar(50), whereClause varchar(8000)) RETURNS                                                                                                              "
                    +
                    " varchar(32672) PARAMETER STYLE JAVA READS SQL DATA LANGUAGE JAVA EXTERNAL NAME                                                                                         "
                    +
                    " 'org.jumpmind.symmetric.db.derby.DerbyFunctions.clobToString'                                                                                                          ";
            install(sql, clobToString, ddl);
        }
        String blobToString = this.parameterService.getTablePrefix() + "_" + "blob_to_string";
        if (!installed(SQL_FUNCTION_INSTALLED, blobToString)) {
            String sql = "CREATE FUNCTION $(functionName)(columnName varchar(50),                                                                                                                                                "
                    +
                    " tableName varchar(50), whereClause varchar(8000)) RETURNS                                                                                                              "
                    +
                    " varchar(32672) PARAMETER STYLE JAVA READS SQL DATA LANGUAGE JAVA EXTERNAL NAME                                                                                         "
                    +
                    " 'org.jumpmind.symmetric.db.derby.DerbyFunctions.blobToString'                                                                                                          ";
            install(sql, blobToString, ddl);
        }
        String transactionId = this.parameterService.getTablePrefix() + "_" + "transaction_id";
        if (!installed(SQL_FUNCTION_INSTALLED, transactionId)) {
            String sql = "CREATE FUNCTION $(functionName)() RETURNS                                                                                                                                                              "
                    +
                    " varchar(100) PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA EXTERNAL NAME                                                                                                   "
                    +
                    " 'org.jumpmind.symmetric.db.derby.DerbyFunctions.getTransactionId'                                                                                                      ";
            install(sql, transactionId, ddl);
        }
        String syncTriggersDisabled = this.parameterService.getTablePrefix() + "_" + "sync_triggers_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, syncTriggersDisabled)) {
            String sql = "CREATE FUNCTION $(functionName)() RETURNS                                                                                                                                                              "
                    +
                    " integer PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA EXTERNAL NAME                                                                                                        "
                    +
                    " 'org.jumpmind.symmetric.db.derby.DerbyFunctions.isSyncDisabled'                                                                                                        ";
            install(sql, syncTriggersDisabled, ddl);
        }
        String syncTriggersSetDisabled = this.parameterService.getTablePrefix() + "_" + "sync_triggers_set_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, syncTriggersSetDisabled)) {
            String sql = "CREATE FUNCTION $(functionName)(state integer) RETURNS                                                                                                                                                 "
                    +
                    "   integer PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA EXTERNAL NAME                                                                                                        "
                    +
                    "   'org.jumpmind.symmetric.db.derby.DerbyFunctions.setSyncDisabled'                                                                                                       ";
            install(sql, syncTriggersSetDisabled, ddl);
        }
        String syncNodeSetDisabled = this.parameterService.getTablePrefix() + "_" + "sync_node_set_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, syncNodeSetDisabled)) {
            String sql = "CREATE FUNCTION $(functionName)(nodeId varchar(50)) RETURNS                                                                                                                                            "
                    +
                    "   varchar(50) PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA EXTERNAL NAME                                                                                                    "
                    +
                    "   'org.jumpmind.symmetric.db.derby.DerbyFunctions.setSyncNodeDisabled'                                                                                                   ";
            install(sql, syncNodeSetDisabled, ddl);
        }
        String saveData = this.parameterService.getTablePrefix() + "_" + "save_data";
        if (!installed(SQL_FUNCTION_INSTALLED, saveData)) {
            String sql = "CREATE PROCEDURE $(functionName)(enabled integer, schemaName varchar(50), prefixName varchar(50),                                                                                                                       "
                    +
                    "  tableName varchar(50), channelName varchar(50), dmlType varchar(1), triggerHistId int,                                                                                 "
                    +
                    "  transactionId varchar(1000), externalData varchar(50), columnNames varchar(32672), pkColumnNames varchar(32672))                                       "
                    +
                    "  PARAMETER STYLE JAVA LANGUAGE JAVA MODIFIES SQL DATA EXTERNAL NAME                                                                                                     "
                    +
                    "  'org.jumpmind.symmetric.db.derby.DerbyFunctions.insertData'                                                                                                            ";
            install(sql, saveData, ddl);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String escape = this.parameterService.getTablePrefix() + "_" + "escape";
        if (installed(SQL_FUNCTION_INSTALLED, escape)) {
            uninstall(SQL_DROP_FUNCTION, escape);
        }
        String clobToString = this.parameterService.getTablePrefix() + "_" + "clob_to_string";
        if (installed(SQL_FUNCTION_INSTALLED, clobToString)) {
            uninstall(SQL_DROP_FUNCTION, clobToString);
        }
        String blobToString = this.parameterService.getTablePrefix() + "_" + "blob_to_string";
        if (installed(SQL_FUNCTION_INSTALLED, blobToString)) {
            uninstall(SQL_DROP_FUNCTION, blobToString);
        }
        String transactionId = this.parameterService.getTablePrefix() + "_" + "transaction_id";
        if (installed(SQL_FUNCTION_INSTALLED, transactionId)) {
            uninstall(SQL_DROP_FUNCTION, transactionId);
        }
        String syncTriggersDisabled = this.parameterService.getTablePrefix() + "_" + "sync_triggers_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, syncTriggersDisabled)) {
            uninstall(SQL_DROP_FUNCTION, syncTriggersDisabled);
        }
        String syncTriggersSetDisabled = this.parameterService.getTablePrefix() + "_" + "sync_triggers_set_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, syncTriggersSetDisabled)) {
            uninstall(SQL_DROP_FUNCTION, syncTriggersSetDisabled);
        }
        String syncNodeSetDisabled = this.parameterService.getTablePrefix() + "_" + "sync_node_set_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, syncNodeSetDisabled)) {
            uninstall(SQL_DROP_FUNCTION, syncNodeSetDisabled);
        }
        String saveData = this.parameterService.getTablePrefix() + "_" + "save_data";
        if (installed(SQL_FUNCTION_INSTALLED, saveData)) {
            uninstall(SQL_DROP_PROCEDURE, saveData);
        }
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
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
        return BinaryEncoding.BASE64;
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.queryForObject(
                String.format("values %s_sync_triggers_set_disabled(1)", parameterService.getTablePrefix()),
                Integer.class);
        if (nodeId != null) {
            transaction.queryForObject(
                    String.format("values %s_sync_node_set_disabled('%s')", parameterService.getTablePrefix(), nodeId),
                    String.class);
        }
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.queryForObject(
                String.format("values %s_sync_triggers_set_disabled(0)", parameterService.getTablePrefix()),
                Integer.class);
        transaction.queryForObject(
                String.format("values %s_sync_node_set_disabled(null)", parameterService.getTablePrefix()), String.class);
    }

    public String getSyncTriggersExpression() {
        return String.format("%s_sync_triggers_disabled() = 0", parameterService.getTablePrefix());
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema,
            Trigger trigger) {
        return String.format("%s_transaction_id()", parameterService.getTablePrefix());
    }

    public void cleanDatabase() {
    }

    @Override
    public void truncateTable(String tableName) {
        platform.getSqlTemplate().update("delete from " + tableName);
    }

    public boolean needsToSelectLobData() {
        return true;
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "values current_timestamp";
    }
}