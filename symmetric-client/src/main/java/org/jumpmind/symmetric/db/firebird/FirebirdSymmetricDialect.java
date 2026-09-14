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
package org.jumpmind.symmetric.db.firebird;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.SequenceIdentifier;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;
import org.springframework.jdbc.UncategorizedSQLException;

/*
 * Database dialect for <a href="http://www.firebirdsql.org/">Firebird</a>.
 */
public class FirebirdSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SQL_DROP_EXTERNAL_FUNCTION = "DROP EXTERNAL FUNCTION $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from rdb$functions where rdb$function_name = upper('$(functionName)')";
    static final String SYNC_TRIGGERS_DISABLED_USER_VARIABLE = "sync_triggers_disabled";
    static final String SYNC_TRIGGERS_DISABLED_NODE_VARIABLE = "sync_node_disabled";

    public FirebirdSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new FirebirdTriggerTemplate(this);
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String escape = this.parameterService.getTablePrefix() + "_" + "escape";
        if (!installed(SQL_FUNCTION_INSTALLED, escape)) {
            String sql = "declare external function $(functionName) cstring(32660)                                                                                                                                               "
                    +
                    "  returns cstring(32660) free_it entry_point 'sym_escape' module_name 'sym_udf'                                                                                          ";
            install(sql, escape, ddl);
        }
        String hex = this.parameterService.getTablePrefix() + "_" + "hex";
        if (!installed(SQL_FUNCTION_INSTALLED, hex)) {
            String sql = "declare external function $(functionName) blob                                                                                                                                                         "
                    +
                    "  returns cstring(32660) free_it entry_point 'sym_hex' module_name 'sym_udf'                                                                                             ";
            install(sql, hex, ddl);
        }
        try {
            platform.getSqlTemplate().queryForInt("select char_length(" + escape + "('')) from rdb$database");
        } catch (UncategorizedSQLException e) {
            if (e.getSQLException().getErrorCode() == -804) {
                log.error("Please install the sym_udf.so/dll to your {firebird_home}/UDF folder");
            }
            throw new RuntimeException("Function " + escape + " is not installed", e);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String escape = this.parameterService.getTablePrefix() + "_" + "escape";
        if (installed(SQL_FUNCTION_INSTALLED, escape)) {
            uninstall(SQL_DROP_EXTERNAL_FUNCTION, escape);
        }
        String hex = this.parameterService.getTablePrefix() + "_" + "hex";
        if (installed(SQL_FUNCTION_INSTALLED, hex)) {
            uninstall(SQL_DROP_EXTERNAL_FUNCTION, hex);
        }
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalogName, String schema, String tableName, String triggerName) {
        return platform.getSqlTemplate().queryForInt("select count(*) from rdb$triggers where rdb$trigger_name = ?",
                new Object[] { triggerName.toUpperCase() }) > 0;
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
    }

    public String getSyncTriggersExpression() {
        return "1=1";
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger) {
        return "current_transaction||''";
    }

    @Override
    public boolean isBlobSyncSupported() {
        return true;
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    public void cleanDatabase() {
    }

    @Override
    public String getName() {
        return StringUtils.left(super.getName(), 50);
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() {
        return false;
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    @Override
    public void truncateTable(String tableName) {
        platform.getSqlTemplate().update("delete from " + tableName);
    }

    @Override
    public void cleanupTriggers() {
        List<String> names = platform.getSqlTemplate().query("select rdb$trigger_name from rdb$triggers where rdb$trigger_name like '" + parameterService
                .getTablePrefix().toUpperCase() + "_%'", new StringMapper());
        int count = 0;
        for (String name : names) {
            count += platform.getSqlTemplate().update("drop trigger " + name);
        }
        if (count > 0) {
            log.info("Remove {} triggers", count);
        }
    }

    @Override
    public String massageDataExtractionSql(String sql, boolean isContainsBigLob) {
        if (!isContainsBigLob) {
            String[] sizes = parameterService.getString(ParameterConstants.FIREBIRD_EXTRACT_VARCHAR_ROW_OLD_PK_DATA, "20000,20000,1000").split(",");
            sql = Strings.CS.replace(sql, "d.row_data", "cast(d.row_data as varchar(" + sizes[0] + "))");
            sql = Strings.CS.replace(sql, "d.old_data", "cast(d.old_data as varchar(" + sizes[1] + "))");
            sql = Strings.CS.replace(sql, "d.pk_data", "cast(d.pk_data as varchar(" + sizes[2] + "))");
        }
        return sql;
    }

    @Override
    public long getCurrentSequenceValue(SequenceIdentifier identifier) {
        return platform.getSqlTemplate().queryForLong("select gen_id(GEN_" + getSequenceName(identifier) + ", 0) from rdb$database");
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "select current_timestamp from rdb$database";
    }
}
