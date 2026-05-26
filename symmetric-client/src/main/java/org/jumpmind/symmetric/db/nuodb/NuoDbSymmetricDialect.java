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
package org.jumpmind.symmetric.db.nuodb;

import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;

public class NuoDbSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SQL_DROP_FUNCTION = "drop function $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from system.functions where functionname='$(functionName)' and schema in (select current_schema from system.dual)";
    static final String SYNC_TRIGGERS_DISABLED_USER_VARIABLE = "sync_triggers_disabled";
    static final String SYNC_TRIGGERS_DISABLED_NODE_VARIABLE = "sync_node_disabled";

    public NuoDbSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new NuoDbTriggerTemplate(this);
        this.parameterService = parameterService;
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema,
            Trigger trigger) {
        return "(select transid from system.connections where connid = getconnectionid())";
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String function = parameterService.getTablePrefix() + "_get_session_variable";
        if (!installed(SQL_FUNCTION_INSTALLED, function)) {
            String sql = "create function $(functionName)(akey string) returns string as\n" +
                    "VAR l_out string = NULL;\n" +
                    "try\n" +
                    "l_out = (SELECT context_value from " + parameterService.getTablePrefix() + "_session_cache where name = akey);\n" +
                    "catch(error)\n" +
                    "create temp table if not exists " + parameterService.getTablePrefix() + "_session_cache\n" +
                    "(name string primary key, context_value string) on commit preserve rows;\n" +
                    "end_try;\n" +
                    "return l_out;\n" +
                    "END_FUNCTION;";
            install(sql, function, ddl);
        }
        function = parameterService.getTablePrefix() + "_set_session_variable";
        if (!installed(SQL_FUNCTION_INSTALLED, function)) {
            String sql = "create function $(functionName)(akey string, avalue string) returns string as\n" +
                    "VAR l_new string = NULL;\n" +
                    "try\n" +
                    "INSERT INTO " + parameterService.getTablePrefix() + "_session_cache (name, context_value) \n" +
                    "values (akey, avalue) ON DUPLICATE KEY UPDATE context_value = avalue;\n" +
                    "catch(error)\n" +
                    "create temp table if not exists " + parameterService.getTablePrefix() + "_session_cache\n" +
                    "(name string primary key, context_value string) on commit preserve rows;\n" +
                    "INSERT INTO " + parameterService.getTablePrefix() + "_session_cache VALUES (akey, avalue);\n" +
                    "l_new = error;\n" +
                    "end_try;\n" +
                    "return l_new;\n" +
                    "END_FUNCTION;";
            install(sql, function, ddl);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String function = this.parameterService.getTablePrefix() + "_get_session_variable";
        if (installed(SQL_FUNCTION_INSTALLED, function)) {
            uninstall(SQL_DROP_FUNCTION, function);
        }
        function = this.parameterService.getTablePrefix() + "_set_session_variable";
        if (installed(SQL_FUNCTION_INSTALLED, function)) {
            uninstall(SQL_DROP_FUNCTION, function);
        }
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema, String tableName,
            String triggerName) {
        schema = schema == null ? (platform.getDefaultSchema() == null ? null
                : platform
                        .getDefaultSchema()) : schema;
        String checkSchemaSql = (schema != null && schema.length() > 0) ? " and schema='"
                + schema + "'"
                : "";
        return platform
                .getSqlTemplate()
                .queryForInt(
                        "select count(*) from system.triggers where triggername = ? and tablename = ?"
                                + checkSchemaSql, new Object[] { triggerName, tableName }) > 0;
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName,
            String triggerName, String tableName, ISqlTransaction transaction) {
        final String sql = "drop trigger " + triggerName;
        logSql(sql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            log.info("Dropping {} trigger for {}", triggerName, Table.getFullyQualifiedName(catalogName, schemaName, tableName));
            transaction.execute(sql);
        }
    }

    @Override
    public void cleanupTriggers() {
        List<String> names = platform.getSqlTemplate().query("select triggername from system.triggers where triggername like '" + parameterService
                .getTablePrefix().toUpperCase() + "_%'", new StringMapper());
        int count = 0;
        for (String name : names) {
            count += platform.getSqlTemplate().update("drop trigger " + name + " if exists");
        }
        if (count > 0) {
            log.info("Remove {} triggers", count);
        }
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("select " + this.parameterService.getTablePrefix() + "_set_session_variable('" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE
                + "', '1') from dual");
        if (nodeId != null) {
            transaction
                    .prepareAndExecute("select " + this.parameterService.getTablePrefix() + "_set_session_variable('" + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE
                            + "','" + nodeId + "') from dual");
        }
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("select " + this.parameterService.getTablePrefix() + "_set_session_variable('" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE
                + "', null) from dual");
        transaction.prepareAndExecute("select " + this.parameterService.getTablePrefix() + "_set_session_variable('" + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE
                + "', null) from dual");
    }

    public String getSyncTriggersExpression() {
        return "$(defaultSchema)" + parameterService.getTablePrefix() + "_get_session_variable('" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "') is null";
    }

    public void cleanDatabase() {
    }

    @Override
    public boolean isClobSyncSupported() {
        return false;
    }

    @Override
    public boolean isBlobSyncSupported() {
        return false;
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.NONE;
    }

    @Override
    public PermissionType[] getSymTablePermissions() {
        PermissionType[] permissions = { PermissionType.CREATE_TABLE, PermissionType.DROP_TABLE, PermissionType.CREATE_TRIGGER, PermissionType.DROP_TRIGGER };
        return permissions;
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "select current_timestamp from dual";
    }
}
