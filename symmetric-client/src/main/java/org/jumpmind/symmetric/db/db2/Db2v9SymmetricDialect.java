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

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;

public class Db2v9SymmetricDialect extends Db2SymmetricDialect implements ISymmetricDialect {
    static final String SYNC_TRIGGERS_DISABLED_USER_VARIABLE = "sync_triggers_disabled";
    static final String SYNC_TRIGGERS_DISABLED_NODE_VARIABLE = "sync_node_disabled";
    String syncTriggersDisabledUserVariable;
    String syncTriggersDisabledNodeVariable;

    public Db2v9SymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        syncTriggersDisabledUserVariable = this.parameterService.getTablePrefix() + "_" + SYNC_TRIGGERS_DISABLED_USER_VARIABLE;
        syncTriggersDisabledNodeVariable = this.parameterService.getTablePrefix() + "_" + SYNC_TRIGGERS_DISABLED_NODE_VARIABLE;
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction();
            enableSyncTriggers(transaction);
            transaction.commit();
        } catch (Exception e) {
            logSql("CREATE VARIABLE " + syncTriggersDisabledNodeVariable + " VARCHAR(50)", ddl);
            logSql("GRANT READ on VARIABLE " + syncTriggersDisabledNodeVariable + " TO PUBLIC", ddl);
            logSql("GRANT WRITE on VARIABLE " + syncTriggersDisabledNodeVariable + " TO PUBLIC", ddl);
            logSql("CREATE VARIABLE " + syncTriggersDisabledUserVariable + " INTEGER", ddl);
            logSql("GRANT READ on VARIABLE " + syncTriggersDisabledUserVariable + " TO PUBLIC", ddl);
            logSql("GRANT WRITE on VARIABLE " + syncTriggersDisabledUserVariable + " TO PUBLIC", ddl);
            if (ddl == null) {
                try {
                    log.info("Creating environment variables {} and {}", syncTriggersDisabledUserVariable,
                            syncTriggersDisabledNodeVariable);
                    ISqlTemplate template = getPlatform().getSqlTemplate();
                    template.update("CREATE VARIABLE " + syncTriggersDisabledNodeVariable + " VARCHAR(50)");
                    template.update("GRANT READ on VARIABLE " + syncTriggersDisabledNodeVariable + " TO PUBLIC");
                    template.update("GRANT WRITE on VARIABLE " + syncTriggersDisabledNodeVariable + " TO PUBLIC");
                    template.update("CREATE VARIABLE " + syncTriggersDisabledUserVariable + " INTEGER");
                    template.update("GRANT READ on VARIABLE " + syncTriggersDisabledUserVariable + " TO PUBLIC");
                    template.update("GRANT WRITE on VARIABLE " + syncTriggersDisabledUserVariable + " TO PUBLIC");
                } catch (Exception ex) {
                    log.error("Error while initializing DB2 dialect", ex);
                }
            }
        } finally {
            close(transaction);
        }
        super.createRequiredDatabaseObjectsImpl(ddl);
    }

    @Override
    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.execute("set " + syncTriggersDisabledUserVariable + "=1");
        if (StringUtils.isNotBlank(nodeId)) {
            transaction.execute("set " + syncTriggersDisabledNodeVariable + "='" + nodeId + "'");
        }
    }

    @Override
    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.execute("set " + syncTriggersDisabledUserVariable + "=null");
        transaction.execute("set " + syncTriggersDisabledNodeVariable + "=null");
    }

    public String getSyncTriggersExpression() {
        return syncTriggersDisabledUserVariable + " is null";
    }

    @Override
    public String getSourceNodeExpression() {
        return syncTriggersDisabledNodeVariable;
    }
}
