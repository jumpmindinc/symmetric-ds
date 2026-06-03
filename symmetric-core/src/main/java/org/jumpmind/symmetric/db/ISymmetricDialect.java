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
package org.jumpmind.symmetric.db;

import java.sql.Connection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;

/*
 * A dialect is the interface that insulates SymmetricDS from database implementation specifics.
 */
public interface ISymmetricDialect {
    public void createTrigger(StringBuilder sqlBuffer, DataEventType dml,
            Trigger trigger, TriggerHistory hist, Channel channel,
            String tablePrefix, Table table, ISqlTransaction transaction);

    public void createTrigger(StringBuilder sqlBuffer, DataEventType dml,
            Trigger trigger, TriggerHistory hist, Channel channel,
            String tablePrefix, Table table);

    public void createDdlTrigger(String tablePrefix, StringBuilder sqlBuffer, String triggerName, String runtimeCatalog, String runtimeSchema);

    /*
     * Get the name of this symmetric instance. This can be set in symmetric.properties using the engine.name property.
     */
    public String getEngineName();

    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName,
            String tableName, ISqlTransaction transaction);

    public void removeTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName,
            String tableName);

    public void removeDdlTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName);

    public boolean doesTriggerExist(StringBuilder sqlBuffer, String catalogName, String schema, String tableName, String triggerName);

    public boolean doesDdlTriggerExist(String catalogName, String schema, String triggerName);

    public void verifyDatabaseIsCompatible();

    public void initTablesAndDatabaseObjects();

    public void dropTablesAndDatabaseObjects();

    public boolean createOrAlterTablesIfNecessary(String... tables);

    public void dropRequiredDatabaseObjects();

    public void createRequiredDatabaseObjects();

    public String getCreateRequiredDatabaseObjectsDDL();

    public IDatabasePlatform getPlatform();

    public IDatabasePlatform getTargetPlatform();

    public IDatabasePlatform getTargetPlatform(String tableName);

    public String getName();

    public String getVersion();

    public int getMajorVersion();

    public int getMinorVersion();

    public String getProductVersion();

    public BinaryEncoding getBinaryEncoding();

    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema, Trigger trigger);

    public String getTransactionId(ISqlTransaction transaction);

    public String createInitialLoadSqlFor(Node node, TriggerRouter trigger, Relation relation, TriggerHistory triggerHistory, Channel channel,
            String overrideSelectSql);

    public boolean[] getColumnPositionUsingTemplate(Relation originalRelation, TriggerHistory triggerHistory);

    public String createPurgeSqlFor(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory);

    public String createPurgeSqlFor(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory, List<TransformTableNodeGroupLink> transforms);

    public String createPurgeSqlFor(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory, List<TransformTableNodeGroupLink> transforms,
            String deleteSql);

    public List<String> createPurgeSqlForMultipleTables(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            List<TransformTableNodeGroupLink> transforms, String deleteSql);

    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause, Table table);

    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause);

    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause, Table table);

    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause);

    /*
     * Get the maximum size the name of a trigger can be for the database platform. If the generated symmetric trigger name is greater than the max trigger
     * name, symmetric will truncate the name, then log a warning suggesting that you might want to provide your own name.
     */
    public int getMaxTriggerNameLength();

    public boolean supportsTransactionId();

    /*
     * Use this call to check to see if the implemented database dialect supports a way to check on pending database transactions.
     */
    public boolean supportsTransactionViews();

    /*
     * Indicates if this dialect supports subselects in delete statements.
     */
    public boolean supportsSubselectsInDelete();

    /*
     * Indicates if this dialect supports subselects in update statements.
     */
    public boolean supportsSubselectsInUpdate();

    public boolean supportsDdlTriggers();

    /*
     * Implement this if the database has some type of cleanup functionality that needs to be run when dropping database objects. An example is Oracle's 'purge
     * recyclebin'
     */
    public void cleanDatabase();

    @Deprecated
    public void disableSyncTriggers(ISqlTransaction transaction);

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId);

    public void enableSyncTriggers(ISqlTransaction transaction);

    public void disableSyncTriggers(Connection connection, String nodeId);

    public void enableSyncTriggers(Connection connection);

    public String getSyncTriggersExpression();

    public String getSyncTriggersOnIncomingExpression();

    public String getSourceNodeExpression();

    public String getCreateSymmetricDDL();

    public boolean isBlobSyncSupported();

    public boolean isClobSyncSupported();

    /*
     * An indicator as to whether the ability to override the default transaction id provided by the dialect can be overridden in the trigger configuration.
     */
    public boolean isTransactionIdOverrideSupported();

    public long insertWithGeneratedKey(final String sql, final SequenceIdentifier sequenceId);

    public long insertWithGeneratedKey(final String sql, final SequenceIdentifier identifier, Object... args);

    @Deprecated
    public Column[] orderColumns(String[] columnNames, Table table);

    public boolean supportsOpenCursorsAcrossCommit();

    /*
     * Retrieves a list of keywords for the database.
     */
    public Set<String> getSqlKeywords();

    public String getInitialLoadTableAlias();

    public String preProcessTriggerSqlClause(String sqlClause);

    public void truncateTable(String tableName);

    public String getDatabaseTimeSQL();

    public long getDatabaseTime();

    public boolean areDatabaseTransactionsPendingSince(long time);

    public Date getEarliestTransactionStartTime();

    /*
     * Returns true if the trigger select lob data back from the original table.
     */
    public boolean needsToSelectLobData();

    /*
     * This is a SQL clause that compares the old data to the new data in a trigger.
     */
    public String getDataHasChangedCondition(Trigger trigger);

    /*
     * Indicates whether captured data can contain gaps.
     */
    public boolean canGapsOccurInCapturedDataIds();

    public String massageDataExtractionSql(String sql, boolean isContainsBigLob);

    public String massageForLob(String sql, boolean isContainsBigLob);

    public boolean isInitialLoadTwoPassLob(Relation relation);

    public String getInitialLoadTwoPassLobSql(String sql, Relation relation, boolean isFirstPass);

    /*
     * Indicates that the dialect relies on SQL that is to be inserted into the database for use by embedded Java triggers. H2 is an example dialect that needs
     * this feature.
     * 
     * @return
     */
    public boolean escapesTemplatesForDatabaseInserts();

    public String getMasterCollation();

    public boolean supportsBatchUpdates();

    public void cleanupTriggers();

    public String getDriverName();

    public String getDriverVersion();

    public String getSequenceName(SequenceIdentifier identifier);

    public String getSequenceKeyName(SequenceIdentifier identifier);

    public long getCurrentSequenceValue(SequenceIdentifier identifier);

    public String getTablePrefix();

    public Database readSymmetricSchemaFromXml();

    public String getSymmetricDdlChanges();

    public String getTemplateNumberPrecisionSpec();

    public Map<String, String> getSqlReplacementTokens();

    public int getSqlTypeForIds();

    public AbstractTriggerTemplate getTriggerTemplate();

    public IParameterService getParameterService();

    public void setExtensionService(IExtensionService extensionService);

    public PermissionType[] getSymTablePermissions();

    public ISymmetricDialect getTargetDialect();

    public ISymmetricDialect getTargetDialect(String tableName);

    public void setTargetDialect(ISymmetricDialect targetDialect);
}
