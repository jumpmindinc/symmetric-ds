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
package org.jumpmind.symmetric.db.sqlite;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlUtils;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ContextService;
import org.jumpmind.util.AppUtils;

public class SqliteSymmetricDialect extends AbstractSymmetricDialect {
    static final String SYNC_TRIGGERS_DISABLED_USER_VARIABLE = "sync_triggers_disabled";
    static final String SYNC_TRIGGERS_DISABLED_NODE_VARIABLE = "sync_node_disabled";
    IContextService contextService;
    String sqliteFunctionToOverride;

    public SqliteSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new SqliteTriggerTemplate(this);
        this.contextService = new ContextService(parameterService, this);
        sqliteFunctionToOverride = SqlUtils.sanitizeFunction(parameterService.getString(ParameterConstants.SQLITE_TRIGGER_FUNCTION_TO_USE));
        platform.getDatabaseInfo().setGeneratedColumnsSupported(!Version.isOlderThanVersion(getProductVersion(), "3.31.0"));
    }

    @Override
    public void dropRequiredDatabaseObjects() {
    }

    public void cleanDatabase() {
    }

    protected void setSqliteFunctionResult(ISqlTransaction transaction, final String name, final String result) {
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        if (isBlank(sqliteFunctionToOverride)) {
            contextService.save(transaction, SYNC_TRIGGERS_DISABLED_USER_VARIABLE, "1");
            if (nodeId != null) {
                contextService.save(transaction, SYNC_TRIGGERS_DISABLED_NODE_VARIABLE, nodeId);
            }
        } else {
            String node = nodeId != null ? ":" + nodeId : "";
            setSqliteFunctionResult(transaction, sqliteFunctionToOverride, "DISABLED" + node);
        }
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        if (isBlank(sqliteFunctionToOverride)) {
            contextService.delete(transaction, SYNC_TRIGGERS_DISABLED_USER_VARIABLE);
            contextService.delete(transaction, SYNC_TRIGGERS_DISABLED_NODE_VARIABLE);
        } else {
            setSqliteFunctionResult(transaction, sqliteFunctionToOverride, "ENABLED");
        }
    }

    public String getSyncTriggersExpression() {
        if (isBlank(sqliteFunctionToOverride)) {
            String contextTableName = parameterService.getTablePrefix() + "_" + TableConstants.SYM_CONTEXT;
            return "(not exists (select context_value from " + contextTableName + " where name = '" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE + "'))";
        } else {
            return "(" + sqliteFunctionToOverride + "() not like 'DISABLED%')";
        }
    }

    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalogName, String schema, String tableName, String triggerName) {
        return platform.getSqlTemplate().queryForInt(
                "select count(*) from sqlite_master where type='trigger' and name=? and tbl_name=? COLLATE NOCASE", triggerName,
                tableName) > 0;
    }

    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    public boolean isBlobSyncSupported() {
        return true;
    }

    public boolean isClobSyncSupported() {
        return true;
    }

    public boolean isTransactionIdOverrideSupported() {
        return false;
    }

    @Override
    protected String getDbSpecificDataHasChangedCondition(Trigger trigger) {
        /* gets filled/replaced by trigger template as it will compare by each column */
        return "$(anyColumnChanged)";
    }

    @Override
    public void truncateTable(String tableName) {
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? platform
                .getDatabaseInfo().getDelimiterToken() : "";
        boolean success = false;
        int tryCount = 5;
        while (!success && tryCount > 0) {
            try {
                Table table = (Table) platform.getRelationFromCache(tableName, false);
                if (table != null) {
                    platform.getSqlTemplate().update(
                            String.format("delete from %s%s%s", quote, table.getName(), quote));
                    success = true;
                } else {
                    throw new RuntimeException(String.format("Could not find %s to trunate",
                            tableName));
                }
            } catch (SqlException ex) {
                log.warn("Failed to truncate the " + tableName + " table", ex);
                AppUtils.sleep(5000);
                tryCount--;
            }
        }
    }

    @Override
    public boolean canGapsOccurInCapturedDataIds() {
        return false;
    }
}
