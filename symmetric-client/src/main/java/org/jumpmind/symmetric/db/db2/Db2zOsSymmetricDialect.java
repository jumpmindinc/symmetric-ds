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
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;

public class Db2zOsSymmetricDialect extends Db2SymmetricDialect implements ISymmetricDialect {
    public Db2zOsSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new Db2zOsTriggerTemplate(this);
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        // TODO: Detect if DB2 for Z/OS is version 11, then use global variables
        // TODO: On DB2 for Z/OS before version 11, check CURRENT SQLID = '${db.user}' instead
        try {
            platform.getSqlTemplate().update("SET " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " = 1");
        } catch (Exception e) {
            String sql = "CREATE VARIABLE " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " SMALLINT DEFAULT 0";
            logSql(sql, ddl);
            if (ddl == null) {
                try {
                    platform.getSqlTemplate().update(sql);
                } catch (Exception ex) {
                    log.info(
                            "Unable to create a user defined global variable to provide loop back support trigger enabled/disabled.  Loopback support is only available in version 11 or higher.  "
                                    + "This could also be a privilege issue.  At least one of the following is required.  The CREATEIN privilege on the schema, "
                                    + "System DBADM authority,  SYSADM authority, SYSCTRL authority.", ex);
                }
            }
        }
        try {
            platform.getSqlTemplate().update("SET " + parameterService.getTablePrefix() + VAR_SOURCE_NODE_ID + " = 'null'");
        } catch (Exception e) {
            String sql = "CREATE VARIABLE " + parameterService.getTablePrefix() + VAR_SOURCE_NODE_ID + " VARCHAR(50)";
            logSql(sql, ddl);
            if (ddl == null) {
                try {
                    platform.getSqlTemplate().update(sql);
                } catch (Exception ex) {
                    log.info(
                            "Unable to create a user defined global variable to provide loop back support for source node id.  Loopback support is only available in version 11 or higher.  "
                                    + "This could also be a privilege issue.  At least one of the following is required.  The CREATEIN privilege on the schema, "
                                    + "System DBADM authority,  SYSADM authority, SYSCTRL authority.", ex);
                }
            }
        }
    }

    @Override
    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("set " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " = 1");
        if (nodeId != null) {
            transaction.prepareAndExecute("set " + getSourceNodeExpression() + " = '" + nodeId + "'");
        }
    }

    @Override
    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("set " + parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " = 0");
        transaction.prepareAndExecute("set " + getSourceNodeExpression() + " = 'null'");
    }

    @Override
    public String getSyncTriggersExpression() {
        return parameterService.getTablePrefix() + VAR_TRIGGER_DISABLED + " = 0";
    }

    @Override
    public String getSourceNodeExpression() {
        return parameterService.getTablePrefix() + VAR_SOURCE_NODE_ID;
    }
}
