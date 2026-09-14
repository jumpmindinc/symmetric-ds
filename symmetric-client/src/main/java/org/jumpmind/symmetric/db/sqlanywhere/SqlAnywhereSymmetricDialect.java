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
package org.jumpmind.symmetric.db.sqlanywhere;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.IConnectionCallback;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlUtils;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.SymmetricUtils;

/*
 * Sybase dialect was tested with jconn4 JDBC driver.
 */
public class SqlAnywhereSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String SQL_DROP_FUNCTION = "drop function $(defaultSchema).$(functionName)";
    static final String SQL_FUNCTION_INSTALLED = "select count(object_name(object_id('$(functionName)')))";
    static final String SYNC_TRIGGERS_DISABLED = "sync_triggers_disabled";
    static final String SYNC_NODE_DISABLED = "sync_node_disabled";

    public SqlAnywhereSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new SqlAnywhereTriggerTemplate(this);
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String triggersDisabled = this.parameterService.getTablePrefix() + "_" + "triggers_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, triggersDisabled)) {
            String sql = "create function $(defaultSchema).$(functionName)(@unused smallint) returns smallint as                                                                                                                              "
                    +
                    "                                begin                                                                                                                                                                  "
                    +
                    "                                   declare @ret smallint" +
                    "                                   select @ret=0 " +
                    "                                   begin   " +
                    "                                       if varexists('" + SYNC_TRIGGERS_DISABLED + "') = 1     " +
                    "                                          select " + SYNC_TRIGGERS_DISABLED + " into @ret    " +
                    "                                   end   " +
                    "                                   return @ret                                                                                                                                "
                    +
                    "                                end                                                                                                                                                                    ";
            System.out.println("install triggers_disabled: " + sql);
            install(sql, triggersDisabled, ddl);
        }
        String nodeDisabled = this.parameterService.getTablePrefix() + "_" + "node_disabled";
        if (!installed(SQL_FUNCTION_INSTALLED, nodeDisabled)) {
            String sql = "create function $(defaultSchema).$(functionName)(@unused smallint) returns varchar(50) as                                                                                                                           "
                    +
                    "                                begin                                                                                                                                                                  "
                    +
                    "                                    declare @ret varchar(50)    " +
                    "                                    begin      " +
                    "                                        if varexists('" + SYNC_NODE_DISABLED + "') = 1       " +
                    "                                            select " + SYNC_NODE_DISABLED + " into @ret      " +
                    "                                    end   " +
                    "                                    return @ret                                                                                                                                                 "
                    +
                    "                                end                                                                                                                                                                    ";
            install(sql, nodeDisabled, ddl);
        }
        String txId = this.parameterService.getTablePrefix() + "_" + "txid";
        if (!installed(SQL_FUNCTION_INSTALLED, txId)) {
            String sql = "create function $(defaultSchema).$(functionName)(@unused smallint) returns varchar(50) as                                                                                                                           "
                    +
                    "                                begin                                                                                                                                                                  "
                    +
                    "                                    declare @txid varchar(50)                                                                                                                                          "
                    +
                    "                                    if (connection_property ('TransactionStartTime') is not null and " +
                    "                                      connection_property ('TransactionStartTime') <> '' ) begin                                                                                                                                         "
                    +
                    "                                        select @txid = connection_property ('TransactionStartTime') + ' ' + CONNECTION_PROPERTY( 'number' )                                                            "
                    +
                    "                                    end                                                                                                                                                                "
                    +
                    "                                    return @txid                                                                                                                                                       "
                    +
                    "                                end                                                                                                                                                                    ";
            install(sql, txId, ddl);
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String encode = this.parameterService.getTablePrefix() + "_" + "base64_encode";
        if (installed(SQL_FUNCTION_INSTALLED, encode)) {
            uninstall(SQL_DROP_FUNCTION, encode);
        }
        String triggersDisabled = this.parameterService.getTablePrefix() + "_" + "triggers_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, triggersDisabled)) {
            uninstall(SQL_DROP_FUNCTION, triggersDisabled);
        }
        String nodeDisabled = this.parameterService.getTablePrefix() + "_" + "node_disabled";
        if (installed(SQL_FUNCTION_INSTALLED, nodeDisabled)) {
            uninstall(SQL_DROP_FUNCTION, nodeDisabled);
        }
        String txId = this.parameterService.getTablePrefix() + "_" + "txid";
        if (installed(SQL_FUNCTION_INSTALLED, txId)) {
            uninstall(SQL_DROP_FUNCTION, txId);
        }
    }

    @Override
    public void removeTrigger(StringBuilder sqlBuffer, final String catalogName, String schemaName,
            final String triggerName, String tableName, ISqlTransaction transaction) {
        final String sql = "drop trigger " + (StringUtils.isBlank(schemaName) ? SqlUtils.sanitizeIdentifier(platform.getDefaultSchema())
                : SqlUtils.sanitizeIdentifier(schemaName)) + "." + SqlUtils.sanitizeIdentifier(tableName) + "." + SqlUtils.sanitizeIdentifier(triggerName);
        logSql(sql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            log.info("Dropping {} trigger for {}", triggerName, (StringUtils.isBlank(schemaName) ? platform.getDefaultSchema() : schemaName) + "." + tableName
                    + "." + triggerName);
            ((JdbcSqlTransaction) transaction)
                    .executeCallback(new IConnectionCallback<Boolean>() {
                        public Boolean execute(Connection con) throws SQLException {
                            String previousCatalog = con.getCatalog();
                            Statement stmt = null;
                            try {
                                if (catalogName != null) {
                                    con.setCatalog(catalogName);
                                }
                                stmt = con.createStatement();
                                stmt.execute(sql);
                            } catch (Exception e) {
                                log.warn("Error removing {}: {}", triggerName, e.getMessage());
                                throw e;
                            } finally {
                                if (catalogName != null) {
                                    con.setCatalog(previousCatalog);
                                }
                                try {
                                    stmt.close();
                                } catch (Exception e) {
                                }
                            }
                            return Boolean.FALSE;
                        }
                    });
        }
    }

    @Override
    protected String switchCatalogForTriggerInstall(String catalog, ISqlTransaction transaction) {
        if (catalog != null) {
            Connection c = ((JdbcSqlTransaction) transaction).getConnection();
            String previousCatalog;
            try {
                previousCatalog = c.getCatalog();
                c.setCatalog(catalog);
                return previousCatalog;
            } catch (SQLException e) {
                throw new SqlException(e);
            }
        } else {
            return null;
        }
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.BASE64;
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, final String catalogName, String schema, String tableName,
            final String triggerName) {
        return ((JdbcSqlTemplate) platform.getSqlTemplate())
                .execute(new IConnectionCallback<Boolean>() {
                    public Boolean execute(Connection con) throws SQLException {
                        String previousCatalog = con.getCatalog();
                        PreparedStatement stmt = con
                                .prepareStatement("select count(*) from dbo.sysobjects where type = 'TR' AND name = ?");
                        try {
                            if (catalogName != null) {
                                con.setCatalog(catalogName);
                            }
                            stmt.setString(1, triggerName);
                            ResultSet rs = stmt.executeQuery();
                            if (rs.next()) {
                                int count = rs.getInt(1);
                                return count > 0;
                            }
                        } finally {
                            if (catalogName != null) {
                                con.setCatalog(previousCatalog);
                            }
                            stmt.close();
                        }
                        return Boolean.FALSE;
                    }
                });
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute("IF VAREXISTS('" + SYNC_TRIGGERS_DISABLED + "')=1 " +
                "THEN drop variable " + SYNC_TRIGGERS_DISABLED + " " +
                "END IF;" +
                "create variable " + SYNC_TRIGGERS_DISABLED + " smallint;" +
                "set " + SYNC_TRIGGERS_DISABLED + "=1;");
        transaction.prepareAndExecute("IF VAREXISTS('" + SYNC_NODE_DISABLED + "')=1 " +
                "THEN drop variable " + SYNC_NODE_DISABLED + " " +
                "END IF;" +
                "create variable " + SYNC_NODE_DISABLED + " varchar(50);");
        if (nodeId != null) {
            transaction.prepareAndExecute("set " + SYNC_NODE_DISABLED + " = '" + nodeId + "'");
        }
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute("IF VAREXISTS('" + SYNC_TRIGGERS_DISABLED + "')=1 " +
                "THEN drop variable " + SYNC_TRIGGERS_DISABLED + " " +
                "END IF;" +
                "create variable " + SYNC_TRIGGERS_DISABLED + " smallint;" +
                "set " + SYNC_TRIGGERS_DISABLED + "= 0;");
        transaction.prepareAndExecute("IF VAREXISTS('" + SYNC_NODE_DISABLED + "')=1 " +
                "THEN drop variable " + SYNC_NODE_DISABLED + " " +
                "END IF;" +
                "create variable " + SYNC_NODE_DISABLED + " varchar(50);");
    }

    public String getSyncTriggersExpression() {
        return SymmetricUtils.quote(this, platform.getDefaultCatalog()) +
                ".$(defaultSchema)" +
                parameterService.getTablePrefix() + "_triggers_disabled(0) = 0";
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger) {
        return SymmetricUtils.quote(this, platform.getDefaultCatalog()) +
                ".$(defaultSchema)" +
                parameterService.getTablePrefix() + "_txid(0)";
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    @Override
    public boolean isTransactionIdOverrideSupported() {
        return false;
    }

    public void cleanDatabase() {
    }

    public boolean needsToSelectLobData() {
        return true;
    }
}
