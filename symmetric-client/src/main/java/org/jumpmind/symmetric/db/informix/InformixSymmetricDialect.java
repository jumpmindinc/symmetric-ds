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
package org.jumpmind.symmetric.db.informix;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;

public class InformixSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SQL_DROP_FUNCTION = "drop function $(defaultSchema).$(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from sysprocedures where procname = '$(functionName)' and owner = (select trim(user) from sysmaster:sysdual)";
    static final String SQL_PAGE_SIZE = "select pagesize from systables where tabname = 'systables'";
    private int pageSize = 2048;

    public InformixSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new InformixTriggerTemplate(this);
        try {
            pageSize = platform.getSqlTemplate().queryForInt(SQL_PAGE_SIZE);
            log.info("Page size is {}", pageSize);
        } catch (Exception e) {
            log.debug("Unable to query page size", e);
        }
    }

    @Override
    public Database readSymmetricSchemaFromXml() {
        Database database = super.readSymmetricSchemaFromXml();
        String prefix = parameterService.getTablePrefix() + "_";
        if (pageSize == 2048) {
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_SNAPSHOT, "relative_dir", "55");
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_SNAPSHOT, "file_name", "55");
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_INCOMING, "relative_dir", "55");
            reconfigureTableColumn(database, prefix, TableConstants.SYM_FILE_INCOMING, "file_name", "55");
            Table table = database.findTable(prefix + TableConstants.SYM_REGISTRATION_REQUEST);
            if (table != null) {
                table.removeIndex(0);
            }
        }
        return database;
    }

    protected void reconfigureTableColumn(Database database, String prefix, String tableName, String columnName, String size) {
        Table table = database.findTable(prefix + tableName);
        if (table != null) {
            Column column = table.findColumn(columnName);
            if (column != null) {
                column.setSize(size);
            }
        }
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema, String tableName,
            String triggerName) {
        return platform.getSqlTemplate().queryForInt(
                "select count(*) from systriggers where lower(trigname) = ?",
                new Object[] { triggerName.toLowerCase() }) > 0;
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("select " + parameterService.getTablePrefix() + "_triggers_set_disabled('t'), "
                + parameterService.getTablePrefix() + "_node_set_disabled(?) from sysmaster:sysdual",
                new Object[] { nodeId });
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("select " + parameterService.getTablePrefix() + "_triggers_set_disabled('f'), "
                + parameterService.getTablePrefix() + "_node_set_disabled(null) from sysmaster:sysdual");
    }

    public String getSyncTriggersExpression() {
        return "not $(defaultSchema)" + parameterService.getTablePrefix() + "_triggers_disabled()";
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String triggersDisabled = this.parameterService.getTablePrefix() + "_" + "triggers_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, triggersDisabled)) {
            String sql = "create function $(defaultSchema).$(functionName)() returning boolean;                                                                                                                                   "
                    +
                    "                                   define global symmetric_triggers_disabled boolean default 'f';                                                                                                      "
                    +
                    "                                   return symmetric_triggers_disabled;                                                                                                                                 "
                    +
                    "                                end function;                                                                                                                                                          ";
            install(sql, triggersDisabled, ddl);
        }
        String triggersSetDisabled = this.parameterService.getTablePrefix() + "_" + "triggers_set_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, triggersSetDisabled)) {
            String sql = "create function $(defaultSchema).$(functionName)(is_disabled boolean) returning boolean;                                                                                                                "
                    +
                    "                                   define global symmetric_triggers_disabled boolean default 'f';                                                                                                      "
                    +
                    "                                   let symmetric_triggers_disabled = is_disabled;                                                                                                                      "
                    +
                    "                                   return symmetric_triggers_disabled;                                                                                                                                 "
                    +
                    "                                end function;                                                                                                                                                          ";
            install(sql, triggersSetDisabled, ddl);
        }
        String nodeDisabled = this.parameterService.getTablePrefix() + "_" + "node_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, nodeDisabled)) {
            String sql = "create function $(defaultSchema).$(functionName)() returning varchar(50);                                                                                                                               "
                    +
                    "                                   define global symmetric_node_disabled varchar(50) default null;                                                                                                     "
                    +
                    "                                   return symmetric_node_disabled;                                                                                                                                     "
                    +
                    "                                end function;                                                                                                                                                          ";
            install(sql, nodeDisabled, ddl);
        }
        String nodeSetDisabled = this.parameterService.getTablePrefix() + "_" + "node_set_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, nodeSetDisabled)) {
            String sql = "create function $(defaultSchema).$(functionName)(node_id varchar(50)) returning integer;                                                                                                                "
                    +
                    "                                   define global symmetric_node_disabled varchar(50) default null;                                                                                                     "
                    +
                    "                                   let symmetric_node_disabled = node_id;                                                                                                                              "
                    +
                    "                                   return 1;                                                                                                                                                           "
                    +
                    "                                end function;                                                                                                                                                          ";
            install(sql, nodeSetDisabled, ddl);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String triggersDisabled = this.parameterService.getTablePrefix() + "_" + "triggers_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, triggersDisabled)) {
            uninstall(SQL_DROP_FUNCTION, triggersDisabled);
        }
        String triggersSetDisabled = this.parameterService.getTablePrefix() + "_" + "triggers_set_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, triggersSetDisabled)) {
            uninstall(SQL_DROP_FUNCTION, triggersSetDisabled);
        }
        String nodeDisabled = this.parameterService.getTablePrefix() + "_" + "node_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, nodeDisabled)) {
            uninstall(SQL_DROP_FUNCTION, nodeDisabled);
        }
        String nodeSetDisabled = this.parameterService.getTablePrefix() + "_" + "node_set_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, nodeSetDisabled)) {
            uninstall(SQL_DROP_FUNCTION, nodeSetDisabled);
        }
    }

    @Override
    public boolean supportsTransactionId() {
        // TODO: write a user-defined routine in C that calls
        // mi_get_transaction_id()
        return false;
    }

    @Override
    public boolean isTransactionIdOverrideSupported() {
        return false;
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema,
            Trigger trigger) {
        return "null";
    }

    @Override
    public boolean isBlobSyncSupported() {
        return false;
    }

    @Override
    public boolean isClobSyncSupported() {
        return false;
    }

    public void cleanDatabase() {
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.BASE64;
    }
}