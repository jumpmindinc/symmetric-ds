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
package org.jumpmind.symmetric.db.hsqldb2;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;

public class HsqlDb2SymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SQL_DROP_FUNCTION = "drop function $(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(*) from INFORMATION_SCHEMA.ROUTINES where ROUTINE_NAME=UPPER('$(functionName)')";

    public HsqlDb2SymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new HsqlDb2TriggerTemplate(this);
        platform.getSqlTemplate().update("SET DATABASE DEFAULT TABLE TYPE CACHED");
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalogName, String schemaName,
            String tableName, String triggerName) {
        boolean exists = (platform.getSqlTemplate().queryForInt(
                "select count(*) from INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_NAME = ?",
                new Object[] { triggerName }) > 0);
        return exists;
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName,
            String triggerName, String tableName, ISqlTransaction transaction) {
        final String dropSql = String.format("DROP TRIGGER %s", triggerName);
        logSql(dropSql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            int count = transaction.execute(dropSql);
            if (count > 0) {
                log.info("Just dropped trigger {}", triggerName);
            }
        }
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String encode = this.parameterService.getTablePrefix() + "_base_64_encode";
        if (!installed(SQL_FUNCTION_INSTALLED, encode)) {
            String sql = "CREATE FUNCTION $(functionName)(binaryData BINARY)                                                                                                                                                     "
                    +
                    " RETURNS VARCHAR(1000000)                                                                                                                                    "
                    +
                    " NO SQL                                                                                                                                                      "
                    +
                    " LANGUAGE JAVA PARAMETER STYLE JAVA                                                                                                                          "
                    +
                    " EXTERNAL NAME                                                                                                                                               "
                    +
                    "  'CLASSPATH:org.jumpmind.symmetric.db.hsqldb.HsqlDbFunctions.encodeBase64'                                                                                  ";
            install(sql, encode, ddl);
        }
        String setSession = this.parameterService.getTablePrefix() + "_set_session";
        if (!installed(SQL_FUNCTION_INSTALLED, setSession)) {
            String sql = "CREATE PROCEDURE $(functionName)(key VARCHAR(50), data VARCHAR(50))                                                                                                                                    "
                    +
                    " NO SQL                                                                                                                                                      "
                    +
                    " LANGUAGE JAVA PARAMETER STYLE JAVA                                                                                                                          "
                    +
                    " EXTERNAL NAME                                                                                                                                               "
                    +
                    "  'CLASSPATH:org.jumpmind.symmetric.db.hsqldb.HsqlDbFunctions.setSession'                                                                                    ";
            install(sql, setSession, ddl);
        }
        String getSession = this.parameterService.getTablePrefix() + "_get_session";
        if (!installed(SQL_FUNCTION_INSTALLED, getSession)) {
            String sql = "CREATE FUNCTION $(functionName)(key VARCHAR(50))                                                                                                                                                       "
                    +
                    " RETURNS VARCHAR(50)                                                                                                                                         "
                    +
                    " NO SQL                                                                                                                                                      "
                    +
                    " LANGUAGE JAVA PARAMETER STYLE JAVA                                                                                                                          "
                    +
                    " EXTERNAL NAME                                                                                                                                               "
                    +
                    "  'CLASSPATH:org.jumpmind.symmetric.db.hsqldb.HsqlDbFunctions.getSession'                                                                                    ";
            install(sql, getSession, ddl);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String encode = this.parameterService.getTablePrefix() + "_base_64_encode";
        if (installed(SQL_FUNCTION_INSTALLED, encode)) {
            uninstall(SQL_DROP_FUNCTION, encode);
        }
        String setSession = this.parameterService.getTablePrefix() + "_set_session";
        if (installed(SQL_FUNCTION_INSTALLED, setSession)) {
            uninstall(SQL_DROP_FUNCTION, setSession);
        }
        String getSession = this.parameterService.getTablePrefix() + "_get_session";
        if (installed(SQL_FUNCTION_INSTALLED, getSession)) {
            uninstall(SQL_DROP_FUNCTION, getSession);
        }
    }

    @Override
    public boolean isBlobSyncSupported() {
        return true;
    }

    @Override
    public boolean isClobSyncSupported() {
        return true;
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("CALL " + parameterService.getTablePrefix()
                + "_set_session('sync_prevented','1')");
        transaction.prepareAndExecute("CALL " + parameterService.getTablePrefix()
                + "_set_session('node_value','" + nodeId + "')");
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("CALL " + parameterService.getTablePrefix()
                + "_set_session('sync_prevented',null)");
        transaction.prepareAndExecute("CALL " + parameterService.getTablePrefix()
                + "_set_session('node_value',null)");
    }

    public String getSyncTriggersExpression() {
        return " " + parameterService.getTablePrefix() + "_get_session('sync_prevented') is null ";
    }

    /*
     * An expression which the java trigger can string replace
     */
    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema,
            Trigger trigger) {
        // TODO A method is coming that will all access to the transaction id
        // ...
        return "null";
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.HEX;
    }

    @Override
    public boolean supportsTransactionId() {
        return false;
    }

    @Override
    public void truncateTable(String tableName) {
        platform.getSqlTemplate().update("delete from " + tableName);
    }

    public void cleanDatabase() {
    }

    @Override
    public boolean canGapsOccurInCapturedDataIds() {
        return false;
    }
}
