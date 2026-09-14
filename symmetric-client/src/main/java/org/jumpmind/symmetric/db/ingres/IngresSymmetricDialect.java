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
package org.jumpmind.symmetric.db.ingres;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;

public class IngresSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SYNC_TRIGGERS_DISABLED_VARIABLE = "synctriggersdisabled";
    static final String SYNC_NODE_DISABLED_VARIABLE = "sourcenode";

    public IngresSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new IngresSqlTriggerTemplate(this);
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    @Override
    public void cleanDatabase() {
    }

    @Override
    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        deleteSyncTriggersDisabled(transaction);
        insertSyncTriggersDisabled(transaction);
        deleteSyncNodeDisabled(transaction);
        insertSyncNodeDisabled(transaction, nodeId);
    }

    @Override
    public void enableSyncTriggers(ISqlTransaction transaction) {
        deleteSyncTriggersDisabled(transaction);
        deleteSyncNodeDisabled(transaction);
    }

    private void deleteSyncTriggersDisabled(ISqlTransaction transaction) {
        deleteSymContextRecord(transaction, SYNC_TRIGGERS_DISABLED_VARIABLE);
    }

    private void insertSyncTriggersDisabled(ISqlTransaction transaction) {
        insertSymContextRecord(transaction, SYNC_TRIGGERS_DISABLED_VARIABLE, "1");
    }

    private void deleteSyncNodeDisabled(ISqlTransaction transaction) {
        deleteSymContextRecord(transaction, SYNC_NODE_DISABLED_VARIABLE);
    }

    private void insertSyncNodeDisabled(ISqlTransaction transaction, String nodeId) {
        insertSymContextRecord(transaction, SYNC_NODE_DISABLED_VARIABLE, nodeId);
    }

    private void deleteSymContextRecord(ISqlTransaction transaction, String variableName) {
        transaction.prepareAndExecute("delete from " + parameterService.getTablePrefix() + "_" + TableConstants.SYM_CONTEXT +
                " where name = DBMSINFO('session_id') || ':" + variableName + "'");
    }

    private void insertSymContextRecord(ISqlTransaction transaction, String variableName, String contextValue) {
        transaction.prepareAndExecute("insert into " + parameterService.getTablePrefix() + "_" + TableConstants.SYM_CONTEXT +
                " (name, context_value, create_time, last_update_time) " +
                " values(DBMSINFO('session_id') || ':" + variableName + "', '" + contextValue + "', current_timestamp, current_timestamp)");
    }

    @Override
    public String getSyncTriggersExpression() {
        return "((var_sync_triggers_disabled is null) OR (var_sync_triggers_disabled = '0'))";
    }

    @Override
    public void dropRequiredDatabaseObjects() {
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalogName, String schema,
            String tableName, String triggerName) {
        return platform.getSqlTemplate().queryForInt(
                "select count(*) from iirule where rule_name = ? ",
                new Object[] { triggerName.toLowerCase() }) > 0;
    }

    @Override
    public boolean requiresAutoCommitFalseToSetFetchSize() {
        return true;
    }

    @Override
    public boolean needsToSelectLobData() {
        return true;
    }

    @Override
    public void truncateTable(String tableName) {
        platform.getSqlTemplate().update("modify " + tableName + " to truncated");
    }

    @Override
    public boolean isTransactionIdOverrideSupported() {
        return false;
    }
}
