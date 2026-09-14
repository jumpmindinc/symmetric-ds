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

public class Db2As400SymmetricDialect extends Db2SymmetricDialect implements ISymmetricDialect {
    boolean supportsGlobalVariables = false;

    public Db2As400SymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new Db2As400TriggerTemplate(this);
        supportsGlobalVariables = platform.getSqlTemplate().getDatabaseMajorVersion() >= 7;
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema,
            String tableName, String triggerName) {
        if (schema == null) {
            schema = platform.getDefaultSchema();
        }
        if (schema != null) {
            schema = schema.toUpperCase();
        }
        triggerName = triggerName == null ? null : triggerName.toUpperCase();
        return platform.getSqlTemplate().queryForInt(
                "SELECT COUNT(*) FROM " + getSystemSchemaName() + ".SYSTRIGGERS WHERE TRIGNAME = ? AND TRIGSCHEMA = ?",
                new Object[] { triggerName, schema }) > 0;
    }

    @Override
    public void enableSyncTriggers(ISqlTransaction transaction) {
        if (supportsGlobalVariables) {
            super.enableSyncTriggers(transaction);
        }
    }

    @Override
    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        if (supportsGlobalVariables) {
            super.disableSyncTriggers(transaction, nodeId);
        }
    }

    @Override
    public String getSyncTriggersExpression() {
        return supportsGlobalVariables ? super.getSyncTriggersExpression() : "1=1";
    }

    @Override
    public String getSourceNodeExpression() {
        return supportsGlobalVariables ? super.getSourceNodeExpression() : "null";
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        if (supportsGlobalVariables) {
            super.createRequiredDatabaseObjectsImpl(ddl);
        }
    }

    @Override
    protected String getSystemSchemaName() {
        return "QSYS2";
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "select current_timestamp from sysibm.sysdummy1";
    }
}
