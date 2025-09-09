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
package org.jumpmind.symmetric.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.cache.TriggerRouterRoutersCache;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.config.ITableResolver;
import org.jumpmind.symmetric.config.ITriggerCreationListener;
import org.jumpmind.symmetric.config.TriggerFailureListener;
import org.jumpmind.symmetric.config.TriggerSelector;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerReBuildReason;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.ConfigurationChangedDataRouter;
import org.jumpmind.symmetric.route.FileSyncDataRouter;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.util.ExceptionUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.MDC;

/**
 * @see ITriggerRouterService
 */
public class TriggerRouterService extends AbstractService implements ITriggerRouterService {
    private IClusterService clusterService;
    private IConfigurationService configurationService;
    private ISequenceService sequenceService;
    private IExtensionService extensionService;
    private IParameterService parameterService;
    private TriggerFailureListener failureListener = new TriggerFailureListener();
    private IStatisticManager statisticManager;
    private IGroupletService groupletService;
    private INodeService nodeService;
    private Date lastUpdateTime;
    private ICacheManager cacheManager;
    /**
     * Cache the history for performance. History never changes and does not grow big so this should be OK.
     */
    private Map<Integer, TriggerHistory> historyMap = Collections.synchronizedMap(new HashMap<Integer, TriggerHistory>());

    public TriggerRouterService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.cacheManager = engine.getCacheManager();
        this.clusterService = engine.getClusterService();
        this.configurationService = engine.getConfigurationService();
        this.statisticManager = engine.getStatisticManager();
        this.groupletService = engine.getGroupletService();
        this.nodeService = engine.getNodeService();
        this.sequenceService = engine.getSequenceService();
        this.extensionService = engine.getExtensionService();
        this.parameterService = engine.getParameterService();
        engine.getExtensionService().addExtensionPoint(failureListener);
        setSqlMap(new TriggerRouterServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    public boolean refreshFromDatabase() {
        Date date1 = sqlTemplate.queryForObject(getSql("selectMaxTriggerLastUpdateTime"), Date.class);
        Date date2 = sqlTemplate.queryForObject(getSql("selectMaxRouterLastUpdateTime"), Date.class);
        Date date3 = sqlTemplate.queryForObject(getSql("selectMaxTriggerRouterLastUpdateTime"), Date.class);
        Date date = maxDate(date1, date2, date3);
        if (date != null) {
            if (lastUpdateTime == null || lastUpdateTime.before(date)) {
                if (lastUpdateTime != null) {
                    log.info("Newer trigger router settings were detected");
                }
                lastUpdateTime = date;
                clearCache();
                return true;
            }
        }
        return false;
    }

    public List<Trigger> getTriggers() {
        return getTriggers(true);
    }

    public List<Trigger> getTriggers(boolean replaceTokens) {
        List<Trigger> triggers = sqlTemplate.query(getSql("selectTriggersSql"), new TriggerMapper());
        if (replaceTokens) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Map<String, String> replacements = (Map) parameterService.getAllParameters();
            for (Trigger trigger : triggers) {
                trigger.setSourceCatalogName(FormatUtils.replaceTokens(trigger.getSourceCatalogName(), replacements, true));
                trigger.setSourceSchemaName(FormatUtils.replaceTokens(trigger.getSourceSchemaName(), replacements, true));
                trigger.setSourceTableName(FormatUtils.replaceTokens(trigger.getSourceTableName(), replacements, true));
            }
        }
        return triggers;
    }

    public boolean isTriggerBeingUsed(String triggerId) {
        return sqlTemplate.queryForInt(getSql("countTriggerRoutersByTriggerIdSql"), triggerId) > 0;
    }

    public boolean doesTriggerExist(String triggerId) {
        return sqlTemplate.queryForInt(getSql("countTriggerByTriggerIdSql"), triggerId) > 0;
    }

    public boolean doesTriggerExistForTable(String tableName) {
        if (tableName.toLowerCase().startsWith(symmetricDialect.getTablePrefix().toLowerCase())) {
            return doesTriggerExistForTable(tableName, true);
        } else {
            return doesTriggerExistForTable(tableName, false);
        }
    }

    public boolean doesTriggerExistForTable(String tableName, boolean useTriggerHist) {
        if (useTriggerHist) {
            return sqlTemplate.queryForInt(getSql("countTriggerByTableNameFromTriggerHistSql"), tableName, tableName.toLowerCase(), tableName
                    .toUpperCase()) > 0;
        } else {
            return sqlTemplate.queryForInt(getSql("countTriggerByTableNameSql"), tableName, tableName.toLowerCase(), tableName.toUpperCase()) > 0;
        }
    }

    public void deleteTrigger(Trigger trigger) {
        deleteTrigger(trigger.getTriggerId());
    }

    private void deleteTrigger(String id) {
        sqlTemplate.update(getSql("deleteTriggerSql"), (Object) id);
    }

    @Override
    public void deleteTriggers(Collection<Trigger> triggers) {
        List<TriggerRouter> triggerRouters = getTriggerRouters(true);
        List<TriggerRouter> triggerRoutersToDelete = new ArrayList<TriggerRouter>();
        for (TriggerRouter triggerRouter : triggerRouters) {
            if (triggers.contains(triggerRouter.getTrigger())) {
                triggerRoutersToDelete.add(triggerRouter);
            }
        }
        ISqlTransaction transaction = null;
        try {
            int maxRowsToFlush = parameterService.getInt(ParameterConstants.DATA_FLUSH_JDBC_BATCH_SIZE);
            transaction = sqlTemplate.startSqlTransaction();
            deleteTriggerRouters(transaction, triggerRoutersToDelete);
            transaction.prepare(getSql("deleteTriggerSql"));
            int[] types = new int[] { Types.VARCHAR };
            int rowCount = 0;
            for (Trigger trigger : triggers) {
                transaction.addRow(null, new Object[] { trigger.getTriggerId() }, types);
                if (++rowCount > maxRowsToFlush) {
                    transaction.flush();
                }
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw e;
        } finally {
            close(transaction);
        }
        clearCache();
    }

    @Override
    public void deleteAllTriggers() {
        sqlTemplate.update(getSql("deleteAllTriggersSql"));
        clearCache();
    }

    public void dropTriggers() {
        dropTriggers(false);
    }

    public void dropTriggers(boolean includeSymTriggers) {
        TriggerRouterContext triggerRouterContext = new TriggerRouterContext();
        long ts = System.currentTimeMillis();
        List<TriggerHistory> activeHistories = getActiveTriggerHistories();
        triggerRouterContext.incrementActiveTriggerHistoriesTime(System.currentTimeMillis() - ts);
        Set<String> symTables = TableConstants.getTables(symmetricDialect.getTablePrefix());
        for (TriggerHistory history : activeHistories) {
            if (includeSymTriggers || !symTables.contains(history.getSourceTableName())) {
                dropTriggers(history, (StringBuilder) null, triggerRouterContext);
            }
        }
        logTriggerRouterContextTimings(triggerRouterContext);
    }

    public void dropTriggers(Set<String> tables) {
        TriggerRouterContext triggerRouterContext = new TriggerRouterContext();
        List<TriggerHistory> activeHistories = null;
        for (String table : tables) {
            if (doesTriggerExistForTable(table)) {
                long ts = System.currentTimeMillis();
                activeHistories = this.getActiveTriggerHistories(table);
                triggerRouterContext.incrementActiveTriggerHistoriesTime(System.currentTimeMillis() - ts);
                for (TriggerHistory history : activeHistories) {
                    dropTriggers(history, (StringBuilder) null, triggerRouterContext);
                }
            }
        }
        logTriggerRouterContextTimings(triggerRouterContext);
    }

    protected void deleteTriggerHistory(TriggerHistory history) {
        sqlTemplate.update(getSql("deleteTriggerHistorySql"), history.getTriggerHistoryId());
    }

    public void createTriggersOnChannelForTables(String channelId, String catalogName,
            String schemaName, List<String> tables, String lastUpdateBy) {
        List<Trigger> createdTriggers = new ArrayList<Trigger>();
        List<Trigger> existingTriggers = getTriggers();
        for (String table : tables) {
            Trigger trigger = new Trigger();
            trigger.setChannelId(channelId);
            trigger.setSourceCatalogName(catalogName);
            trigger.setSourceSchemaName(schemaName);
            trigger.setSourceTableName(table);
            String triggerId = table;
            if (table.length() > 50) {
                triggerId = table.substring(0, 13) + "_" + UUID.randomUUID().toString();
            }
            boolean uniqueNameCreated = false;
            int suffix = 0;
            while (!uniqueNameCreated) {
                String triggerIdPriorToCheck = triggerId;
                for (Trigger existingTrigger : existingTriggers) {
                    if (triggerId.equals(existingTrigger.getTriggerId())) {
                        String suffixString = "_" + suffix;
                        if (suffix == 0) {
                            triggerId = triggerId + suffixString;
                        } else {
                            triggerId = triggerId.substring(0, triggerId.length()
                                    - ("_" + (suffix - 1)).length())
                                    + suffixString;
                        }
                        suffix++;
                    }
                }
                if (triggerId.equals(triggerIdPriorToCheck)) {
                    uniqueNameCreated = true;
                }
            }
            trigger.setTriggerId(triggerId);
            trigger.setLastUpdateBy(lastUpdateBy);
            trigger.setLastUpdateTime(new Date());
            trigger.setCreateTime(new Date());
            saveTrigger(trigger);
            createdTriggers.add(trigger);
        }
    }

    public Collection<Trigger> findMatchingTriggers(List<Trigger> triggers, String catalog, String schema,
            String table) {
        Set<Trigger> matches = new HashSet<Trigger>();
        IDatabasePlatform targetPlatform = getTargetPlatform(table);
        boolean isTargetCatalog = StringUtils.isNotBlank(catalog) && catalog.equals(targetPlatform.getDefaultCatalog());
        boolean isTargetSchema = StringUtils.isNotBlank(schema) && schema.equals(targetPlatform.getDefaultSchema());
        for (Trigger trigger : triggers) {
            boolean catalogMatches = trigger.isSourceCatalogNameWildCarded()
                    || (catalog == null && trigger.getSourceCatalogName() == null)
                    || (StringUtils.isBlank(trigger.getSourceCatalogName()) && isTargetCatalog)
                    || (StringUtils.isNotBlank(catalog) && catalog.equals(trigger
                            .getSourceCatalogName()));
            boolean schemaMatches = trigger.isSourceSchemaNameWildCarded()
                    || (schema == null && trigger.getSourceSchemaName() == null)
                    || (StringUtils.isBlank(trigger.getSourceSchemaName()) && isTargetSchema)
                    || (StringUtils.isNotBlank(schema) && schema.equals(trigger
                            .getSourceSchemaName()));
            boolean tableMatches = trigger.isSourceTableNameWildCarded()
                    || table.equalsIgnoreCase(trigger.getSourceTableName());
            if (catalogMatches && schemaMatches && tableMatches) {
                matches.add(trigger);
            }
        }
        return matches;
    }

    protected void invalidateTriggerHistory(TriggerHistory history, Exception ex) {
        if (history != null && history.getInactiveTime() == null) {
            log.info("Cleaning up trigger hist row of {} after failing to create trigger for {}", history.getTriggerHistoryId(),
                    history.getFullyQualifiedSourceTableName());
            history.setErrorMessage(ex.getMessage());
            inactivateTriggerHistory(history);
        }
    }

    public void inactivateTriggerHistory(TriggerHistory history) {
        history.setInactiveTime(new Date());
        sqlTemplate.update(getSql("inactivateTriggerHistorySql"),
                new Object[] { history.getInactiveTime(), history.getErrorMessage(), history.getTriggerHistoryId() },
                new int[] { Types.TIMESTAMP, Types.VARCHAR, Types.INTEGER });
    }

    public Map<Long, TriggerHistory> getHistoryRecords() {
        final Map<Long, TriggerHistory> retMap = new HashMap<Long, TriggerHistory>();
        sqlTemplate.query(getSql("allTriggerHistSql"), new TriggerHistoryMapper(retMap));
        return retMap;
    }

    protected boolean isTriggerNameInUse(List<TriggerHistory> activeTriggerHistories, Trigger trigger, String triggerName,
            TriggerHistory oldhist, List<String> triggerNamesGeneratedThisSession) {
        synchronized (activeTriggerHistories) {
            for (TriggerHistory triggerHistory : activeTriggerHistories) {
                if ((!triggerHistory.getTriggerId().equals(trigger.getTriggerId()) ||
                        ((trigger.isSourceTableNameWildCarded() || trigger.isSourceCatalogNameWildCarded() || trigger.isSourceSchemaNameWildCarded() || trigger
                                .isSourceTableNameExpanded()) &&
                                (oldhist == null || triggerHistory.getTriggerHistoryId() != oldhist.getTriggerHistoryId()))) &&
                        ((triggerHistory.getNameForDeleteTrigger() != null && triggerHistory.getNameForDeleteTrigger().equals(triggerName)) ||
                                (triggerHistory.getNameForInsertTrigger() != null && triggerHistory.getNameForInsertTrigger().equals(triggerName)) ||
                                (triggerHistory.getNameForUpdateTrigger() != null && triggerHistory.getNameForUpdateTrigger().equals(triggerName)))) {
                    return true;
                }
            }
        }
        if (triggerNamesGeneratedThisSession.contains(triggerName)) {
            return true;
        }
        return false;
    }

    public TriggerHistory findTriggerHistory(String catalogName, String schemaName, String tableName) {
        List<TriggerHistory> list = findTriggerHistories(catalogName, schemaName, tableName);
        return list.size() > 0 ? list.get(0) : null;
    }

    public List<TriggerHistory> findTriggerHistories(String catalogName, String schemaName,
            String tableName) {
        List<TriggerHistory> listToReturn = new ArrayList<TriggerHistory>();
        List<TriggerHistory> triggerHistories = getActiveTriggerHistories();
        if (triggerHistories != null && triggerHistories.size() > 0) {
            for (TriggerHistory triggerHistory : triggerHistories) {
                boolean matches = true;
                if (StringUtils.isNotBlank(catalogName)) {
                    matches = catalogName.equals(StringUtils.isNotBlank(triggerHistory.getSourceCatalogName()) ? triggerHistory.getSourceCatalogName()
                            : platform.getDefaultCatalog());
                }
                if (matches && StringUtils.isNotBlank(schemaName)) {
                    matches = schemaName.equals(StringUtils.isNotBlank(triggerHistory.getSourceSchemaName()) ? triggerHistory.getSourceSchemaName()
                            : platform.getDefaultSchema());
                }
                if (matches && StringUtils.isNotBlank(tableName)) {
                    boolean ignoreCase = parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE) &&
                            !FormatUtils.isMixedCase(tableName);
                    matches = ignoreCase ? triggerHistory.getSourceTableName().equalsIgnoreCase(tableName)
                            : triggerHistory.getSourceTableName().equals(tableName);
                }
                if (matches) {
                    listToReturn.add(triggerHistory);
                }
            }
        }
        return listToReturn;
    }

    public TriggerHistory getTriggerHistory(int histId) {
        TriggerHistory history = historyMap.get(histId);
        if (history == null && histId >= 0) {
            history = sqlTemplate.queryForObject(getSql("triggerHistSql"),
                    new TriggerHistoryMapper(), histId);
            if (history != null) {
                historyMap.put(histId, history);
            }
        }
        return history;
    }

    public List<TriggerHistory> getActiveTriggerHistories(Trigger trigger) {
        List<TriggerHistory> active = sqlTemplate.query(getSql("allTriggerHistSql", "activeTriggerHistSqlByTriggerId"),
                new TriggerHistoryMapper(), trigger.getTriggerId());
        for (TriggerHistory triggerHistory : active) {
            historyMap.put(triggerHistory.getTriggerHistoryId(), triggerHistory);
        }
        return active;
    }

    public TriggerHistory getNewestTriggerHistoryForTrigger(List<TriggerHistory> activeTriggerHistories, String triggerId, String catalogName,
            String schemaName, String tableName) {
        for (TriggerHistory triggerHistory : activeTriggerHistories) {
            if ((StringUtils.isBlank(catalogName) && StringUtils.isBlank(triggerHistory
                    .getSourceCatalogName()))
                    || (StringUtils.isNotBlank(catalogName) && catalogName.equals(triggerHistory
                            .getSourceCatalogName()))) {
                if ((StringUtils.isBlank(schemaName) && StringUtils.isBlank(triggerHistory
                        .getSourceSchemaName()))
                        || (StringUtils.isNotBlank(schemaName) && schemaName.equals(triggerHistory
                                .getSourceSchemaName()))) {
                    if ((StringUtils.isBlank(tableName) && StringUtils.isBlank(triggerHistory.getSourceTableName()))
                            || (StringUtils.isNotBlank(tableName) && tableName.equals(triggerHistory
                                    .getSourceTableName()))) {
                        if (StringUtils.isNotBlank(triggerId) && triggerId.equals(triggerHistory.getTriggerId())) {
                            return triggerHistory;
                        }
                    }
                }
            }
        }
        return null;
    }

    public TriggerHistory getNewestTriggerHistoryForTrigger(String triggerId, String catalogName,
            String schemaName, String tableName) {
        List<TriggerHistory> triggerHistories = sqlTemplate.query(getSql("latestTriggerHistSql"),
                new TriggerHistoryMapper(), triggerId, tableName);
        return getNewestTriggerHistoryForTrigger(triggerHistories, triggerId, catalogName, schemaName, tableName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<TriggerHistory> getActiveTriggerHistoriesFromCache() {
        return new ArrayList<TriggerHistory>(historyMap != null ? historyMap.values() : Collections.EMPTY_LIST);
    }

    /**
     * Get a list of trigger histories that are currently active
     */
    public List<TriggerHistory> getActiveTriggerHistories() {
        String sqlKey = "allTriggerHistSql";
        if (!parameterService.hasDatabaseBeenSetup()) {
            sqlKey = "allTriggerHistBackwardsCompatibleSql";
        }
        List<TriggerHistory> histories = sqlTemplate.query(getSql(sqlKey, "activeTriggerHistSql"),
                new TriggerHistoryMapper());
        for (TriggerHistory triggerHistory : histories) {
            historyMap.put(triggerHistory.getTriggerHistoryId(), triggerHistory);
        }
        return histories;
    }

    public Map<String, TriggerHistory> getLastErrorTriggerHistories() {
        Map<String, TriggerHistory> map = new HashMap<String, TriggerHistory>();
        sqlTemplate.query(getSql("allTriggerHistSql", "errorTriggerHistSql"), new LastTriggerHistoryMapper(map));
        return map;
    }

    public List<TriggerHistory> getActiveTriggerHistories(String tableName) {
        if (tableName != null) {
            String sqlKey = "allTriggerHistSql";
            if (!parameterService.hasDatabaseBeenSetup()) {
                sqlKey = "allTriggerHistBackwardsCompatibleSql";
            }
            return sqlTemplate.query(getSql(sqlKey, "triggerHistBySourceTableWhereSql"),
                    new TriggerHistoryMapper(), tableName, tableName.toLowerCase(), tableName.toUpperCase());
        } else {
            return new ArrayList<TriggerHistory>();
        }
    }

    public List<Trigger> buildTriggersForSymmetricTables(String version,
            String... tablesToExclude) {
        List<Trigger> triggers = new ArrayList<Trigger>();
        List<String> tables = new ArrayList<String>(TableConstants.getConfigTables(symmetricDialect
                .getTablePrefix()));
        List<Trigger> definedTriggers = getTriggers();
        for (Trigger trigger : definedTriggers) {
            if (tables.remove(trigger.getSourceTableName())) {
                logOnce(String
                        .format("Not generating virtual triggers for %s because there is a user defined trigger already defined",
                                trigger.getSourceTableName()));
            }
        }
        if (tablesToExclude != null) {
            for (String tableToExclude : tablesToExclude) {
                String tablename = TableConstants.getTableName(tablePrefix, tableToExclude);
                if (!tables.remove(tablename)) {
                    if (!tables.remove(tablename.toUpperCase())) {
                        tables.remove(tablename.toLowerCase());
                    }
                }
            }
        }
        Set<String> configTablesWithoutCapture = TableConstants.getConfigTablesWithoutCapture(symmetricDialect.getTablePrefix());
        for (String tableName : tables) {
            Trigger trigger = buildTriggerForSymmetricTable(tableName, configTablesWithoutCapture);
            triggers.add(trigger);
        }
        /*
         * if (parameterService.is(ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK)) { updateOrCreateDatabaseTriggers(triggers, new StringBuilder(),
         * true, true, getActiveTriggerHistories(), true); }
         */
        return triggers;
    }

    protected Trigger buildTriggerForSymmetricTable(String tableName, Set<String> configTablesWithoutCapture) {
        boolean syncChanges = !configTablesWithoutCapture.contains(tableName)
                && (parameterService.is(ParameterConstants.AUTO_SYNC_CONFIGURATION)
                        || TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST).equals(tableName)
                        || TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE).equals(tableName)
                        || TableConstants.getTableName(tablePrefix, TableConstants.SYM_MONITOR_EVENT).equals(tableName));
        // boolean syncOnIncoming = !configurationService.isMasterToMaster() && (parameterService.is(
        // ParameterConstants.AUTO_SYNC_CONFIGURATION_ON_INCOMING, true)
        // || tableName.equals(TableConstants.getTableName(tablePrefix,
        // TableConstants.SYM_TABLE_RELOAD_REQUEST)));
        // sync on incoming for symmetric tables are blocked from being synchronized to other nodes when
        // master to master is set up. We need to allow sync on incoming at the registration server so that
        // tables like sym_node and sym_node_security are delivered to other nodes in the master to master
        // and the other nodes will then be able to synchronize with non-registration nodes.
        boolean syncOnIncoming = (!configurationService.isMasterToMaster() || nodeService.isRegistrationServer())
                && (parameterService.is(ParameterConstants.AUTO_SYNC_CONFIGURATION_ON_INCOMING, true)
                        || tableName.equals(TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_REQUEST)));
        Trigger trigger = new Trigger();
        trigger.setUseHandleKeyUpdates(false);
        trigger.setTriggerId(tableName);
        trigger.setSyncOnDelete(syncChanges);
        trigger.setSyncOnInsert(syncChanges);
        trigger.setSyncOnUpdate(syncChanges);
        trigger.setSyncOnIncomingBatch(syncOnIncoming);
        trigger.setSourceTableName(tableName);
        trigger.setUseCaptureOldData(false);
        if (TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST).equals(tableName)) {
            trigger.setChannelId(Constants.CHANNEL_HEARTBEAT);
        } else if (TableConstants.getTableName(tablePrefix, TableConstants.SYM_MONITOR_EVENT).equals(tableName) ||
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_REQUEST).equals(tableName) ||
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_STATUS).equals(tableName) ||
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_EXTRACT_REQUEST).equals(tableName) ||
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_COMPARE_REQUEST).equals(tableName) ||
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_COMPARE_STATUS).equals(tableName) ||
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_COMPARE_TABLE_STATUS).equals(tableName)) {
            trigger.setChannelId(Constants.CHANNEL_MONITOR);
            trigger.setUseCaptureOldData(true);
        } else if (TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT)
                .equals(tableName)) {
            trigger.setChannelId(Constants.CHANNEL_DYNAMIC);
            trigger.setChannelExpression("$(curTriggerValue).$(curColumnPrefix)" + platform.alterCaseToMatchDatabaseDefaultCase("channel_id"));
            trigger.setReloadChannelId(Constants.CHANNEL_FILESYNC_RELOAD);
            trigger.setUseCaptureOldData(true);
            trigger.setSyncOnIncomingBatch(false);
            boolean syncEnabled = parameterService.is(ParameterConstants.FILE_SYNC_ENABLE);
            trigger.setSyncOnInsert(syncEnabled);
            trigger.setSyncOnUpdate(syncEnabled); // Changed to false because of issues with the traffic file
            trigger.setSyncOnDelete(false);
        } else {
            trigger.setChannelId(Constants.CHANNEL_CONFIG);
        }
        if (TableConstants.getTableName(tablePrefix, TableConstants.SYM_MONITOR_EVENT).equals(tableName)
                && !parameterService.is(ParameterConstants.MONITOR_EVENTS_CAPTURE_ENABLED)) {
            trigger.setSyncOnInsert(false);
            trigger.setSyncOnUpdate(false);
            trigger.setSyncOnDelete(false);
        }
        if (!TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST)
                .equals(tableName) &&
                !TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE)
                        .equals(tableName) &&
                !TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_SECURITY)
                        .equals(tableName) &&
                !TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_REQUEST)
                        .equals(tableName) &&
                !TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT)
                        .equals(tableName)) {
            trigger.setUseCaptureLobs(true);
        }
        // little trick to force the rebuild of SymmetricDS triggers every time
        // there is a new version of SymmetricDS
        trigger.setLastUpdateTime(new Date(Version.version().hashCode()));
        return trigger;
    }

    public List<TriggerRouter> buildTriggerRoutersForSymmetricTables(String version,
            NodeGroupLink nodeGroupLink, String... tablesToExclude) {
        int initialLoadOrder = 1;
        List<Trigger> triggers = buildTriggersForSymmetricTables(version, tablesToExclude);
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>(triggers.size());
        for (int j = 0; j < triggers.size(); j++) {
            Trigger trigger = triggers.get(j);
            TriggerRouter triggerRouter = buildTriggerRoutersForSymmetricTables(version, trigger,
                    nodeGroupLink);
            triggerRouter.setInitialLoadOrder(initialLoadOrder++);
            triggerRouters.add(triggerRouter);
        }
        return triggerRouters;
    }

    public String buildSymmetricTableRouterId(String triggerId, String sourceNodeGroupId, String targetNodeGroupId) {
        return String.format("%s_%s_2_%s", triggerId, sourceNodeGroupId, targetNodeGroupId);
    }

    protected TriggerRouter buildTriggerRoutersForSymmetricTables(String version, Trigger trigger,
            NodeGroupLink nodeGroupLink) {
        TriggerRouter triggerRouter = new TriggerRouter();
        triggerRouter.setTrigger(trigger);
        Router router = triggerRouter.getRouter();
        router.setRouterId(buildSymmetricTableRouterId(trigger.getTriggerId(), nodeGroupLink.getSourceNodeGroupId(), nodeGroupLink.getTargetNodeGroupId()));
        if (TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT).equals(
                trigger.getSourceTableName())) {
            router.setRouterType(FileSyncDataRouter.ROUTER_TYPE);
        } else {
            router.setRouterType(ConfigurationChangedDataRouter.ROUTER_TYPE);
        }
        router.setNodeGroupLink(nodeGroupLink);
        router.setLastUpdateTime(trigger.getLastUpdateTime());
        triggerRouter.setLastUpdateTime(trigger.getLastUpdateTime());
        return triggerRouter;
    }

    public Set<TriggerRouter> getTriggerRouterForTableForCurrentNode(String catalogName,
            String schemaName, String tableName, boolean refreshCache) {
        return getTriggerRouterForTableForCurrentNode(null, catalogName, schemaName, tableName,
                refreshCache);
    }

    public Set<TriggerRouter> getTriggerRouterForTableForCurrentNode(NodeGroupLink link,
            String catalogName, String schemaName, String tableName, boolean refreshCache) {
        TriggerRouterRoutersCache cache = getTriggerRoutersCacheForCurrentNode(refreshCache);
        Collection<List<TriggerRouter>> triggerRouters = cache.triggerRoutersByTriggerId.values();
        HashSet<TriggerRouter> returnList = new HashSet<TriggerRouter>();
        for (List<TriggerRouter> list : triggerRouters) {
            for (TriggerRouter triggerRouter : list) {
                if (isMatch(link, triggerRouter)
                        && isMatch(catalogName, schemaName, tableName, triggerRouter.getTrigger())) {
                    returnList.add(triggerRouter);
                }
            }
        }
        return returnList;
    }

    protected boolean isMatch(NodeGroupLink link, TriggerRouter router) {
        if (link != null && router != null && router.getRouter() != null) {
            return link.getSourceNodeGroupId().equals(
                    router.getRouter().getNodeGroupLink().getSourceNodeGroupId())
                    && link.getTargetNodeGroupId().equals(
                            router.getRouter().getNodeGroupLink().getTargetNodeGroupId());
        } else {
            return true;
        }
    }

    protected boolean isMatch(String catalogName, String schemaName, String tableName,
            Trigger trigger) {
        if (!StringUtils.isBlank(tableName) && !tableName.equals(trigger.getSourceTableName())) {
            return false;
        } else if (StringUtils.isBlank(tableName)
                && !StringUtils.isBlank(trigger.getSourceTableName())) {
            return false;
        } else if (!StringUtils.isBlank(catalogName)
                && !catalogName.equals(trigger.getSourceCatalogName())) {
            return false;
        } else if (StringUtils.isBlank(catalogName)
                && !StringUtils.isBlank(trigger.getSourceCatalogName())) {
            return false;
        } else if (!StringUtils.isBlank(schemaName)
                && !schemaName.equals(trigger.getSourceSchemaName())) {
            return false;
        } else if (StringUtils.isBlank(schemaName)
                && !StringUtils.isBlank(trigger.getSourceSchemaName())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Create a list of {@link TriggerRouter} for the SymmetricDS tables that should have triggers created for them on the current node.
     */
    protected List<TriggerRouter> getConfigurationTablesTriggerRoutersForCurrentNode(
            String sourceNodeGroupId) {
        List<TriggerRouter> triggerRouters = new ArrayList<TriggerRouter>();
        List<NodeGroupLink> links = configurationService.getNodeGroupLinksFor(sourceNodeGroupId, false);
        for (NodeGroupLink nodeGroupLink : links) {
            triggerRouters.addAll(buildTriggerRoutersForSymmetricTables(Version.version(),
                    nodeGroupLink));
        }
        if (triggerRouters.size() == 0 && parameterService.is(ParameterConstants.SYNC_TRIGGERS_REG_SVR_INSTALL_WITHOUT_CONFIG, true) &&
                parameterService.isRegistrationServer()) {
            NodeGroupLink link = new NodeGroupLink(sourceNodeGroupId, Constants.NO_GROUP);
            triggerRouters.addAll(buildTriggerRoutersForSymmetricTables(Version.version(), link));
        }
        return triggerRouters;
    }

    protected void mergeInConfigurationTablesTriggerRoutersForCurrentNode(String sourceNodeGroupId,
            List<TriggerRouter> configuredInDatabase) {
        List<TriggerRouter> virtualConfigTriggers = getConfigurationTablesTriggerRoutersForCurrentNode(sourceNodeGroupId);
        for (TriggerRouter trigger : virtualConfigTriggers) {
            if (trigger.getRouter().getNodeGroupLink().getSourceNodeGroupId()
                    .equalsIgnoreCase(sourceNodeGroupId)
                    && !doesTriggerRouterExistInList(configuredInDatabase, trigger)) {
                configuredInDatabase.add(trigger);
            }
        }
    }

    protected boolean doesTriggerRouterExistInList(List<TriggerRouter> triggerRouters,
            TriggerRouter triggerRouter) {
        for (TriggerRouter checkMe : triggerRouters) {
            if (checkMe.isSame(triggerRouter)) {
                return true;
            }
        }
        return false;
    }

    public TriggerRouter getTriggerRouterForCurrentNode(String triggerId, String routerId, boolean refreshCache) {
        TriggerRouter triggerRouter = null;
        List<TriggerRouter> triggerRouters = getTriggerRoutersForCurrentNode(refreshCache).get(triggerId);
        if (triggerRouters != null) {
            for (TriggerRouter testTriggerRouter : triggerRouters) {
                if (ConfigurationChangedDataRouter.ROUTER_TYPE.equals(testTriggerRouter.getRouter().getRouterType()) ||
                        testTriggerRouter.getRouter().getRouterId().equals(routerId)
                        || routerId.equals(Constants.UNKNOWN_ROUTER_ID)) {
                    triggerRouter = testTriggerRouter;
                    break;
                }
            }
        }
        if (triggerRouter == null) {
            log.warn("Could not find trigger router [{}:{}] in list {}", new Object[] { triggerId, routerId, triggerRouters == null ? 0
                    : triggerRouters.toString() });
        }
        return triggerRouter;
    }

    public Map<String, List<TriggerRouter>> getTriggerRoutersForCurrentNode(boolean refreshCache) {
        return getTriggerRoutersCacheForCurrentNode(refreshCache).triggerRoutersByTriggerId;
    }

    public List<Trigger> getTriggersForCurrentNode(boolean refreshCache) {
        Map<String, List<TriggerRouter>> triggerRouters = getTriggerRoutersForCurrentNode(refreshCache);
        List<Trigger> triggers = new ArrayList<Trigger>(triggerRouters.size());
        for (List<TriggerRouter> list : triggerRouters.values()) {
            if (list.size() > 0) {
                triggers.add(list.get(0).getTrigger());
            }
        }
        return triggers;
    }

    public TriggerRouter getTriggerRouterByTriggerHist(String targetNodeGroupId, int triggerHistId, boolean refreshCache) {
        TriggerRouter triggerRouter = null;
        TriggerHistory hist = getTriggerHistory(triggerHistId);
        if (hist != null) {
            Map<String, List<TriggerRouter>> triggerRouters = getTriggerRoutersForCurrentNode(refreshCache);
            for (List<TriggerRouter> list : triggerRouters.values()) {
                for (TriggerRouter curTriggerRouter : list) {
                    if (curTriggerRouter.getTriggerId().equals(hist.getTriggerId()) &&
                            curTriggerRouter.getRouter().getNodeGroupLink().getTargetNodeGroupId().equals(targetNodeGroupId)) {
                        triggerRouter = curTriggerRouter;
                        break;
                    }
                }
            }
        }
        return triggerRouter;
    }

    public Map<Integer, TriggerRouter> getTriggerRoutersByTriggerHist(String targetNodeGroupId, boolean refreshCache) {
        return cacheManager.getTriggerRoutersByTriggerHist(refreshCache).get(targetNodeGroupId);
    }

    public Map<String, Map<Integer, TriggerRouter>> getTriggerRoutersByTriggerHistFromDatabase() {
        Map<String, Map<Integer, TriggerRouter>> cache = new HashMap<String, Map<Integer, TriggerRouter>>();
        Map<String, List<TriggerRouter>> triggerRouters = getTriggerRoutersForCurrentNode(true);
        Map<String, TriggerHistory> triggerHistoryByTrigger = new HashMap<String, TriggerHistory>();
        for (TriggerHistory hist : getActiveTriggerHistories()) {
            triggerHistoryByTrigger.put(hist.getTriggerId(), hist);
        }
        for (List<TriggerRouter> list : triggerRouters.values()) {
            for (TriggerRouter triggerRouter : list) {
                String groupId = triggerRouter.getRouter().getNodeGroupLink().getTargetNodeGroupId();
                Map<Integer, TriggerRouter> map = cache.get(groupId);
                if (map == null) {
                    map = new HashMap<Integer, TriggerRouter>();
                    cache.put(groupId, map);
                }
                TriggerHistory hist = triggerHistoryByTrigger.get(triggerRouter.getTriggerId());
                if (hist != null) {
                    map.put(hist.getTriggerHistoryId(), triggerRouter);
                }
            }
        }
        return cache;
    }

    protected TriggerRouterRoutersCache getTriggerRoutersCacheForCurrentNode(boolean refreshCache) {
        String myNodeGroupId = parameterService.getNodeGroupId();
        return cacheManager.getTriggerRoutersByNodeGroupId(refreshCache).get(myNodeGroupId);
    }

    public Map<String, TriggerRouterRoutersCache> getTriggerRoutersCacheByNodeGroupIdFromDatabase() {
        String myNodeGroupId = parameterService.getNodeGroupId();
        Map<String, TriggerRouterRoutersCache> newTriggerRouterCacheByNodeGroupId = new HashMap<String, TriggerRouterRoutersCache>();
        List<TriggerRouter> triggerRouters = getAllTriggerRoutersForCurrentNode(myNodeGroupId);
        Map<String, List<TriggerRouter>> triggerRoutersByTriggerId = new HashMap<String, List<TriggerRouter>>(
                triggerRouters.size());
        Map<String, Router> routers = new HashMap<String, Router>(triggerRouters.size());
        for (TriggerRouter triggerRouter : triggerRouters) {
            if (triggerRouter.isEnabled()) {
                boolean sourceEnabled = groupletService.isSourceEnabled(triggerRouter);
                if (sourceEnabled) {
                    String triggerId = triggerRouter.getTrigger().getTriggerId();
                    List<TriggerRouter> list = triggerRoutersByTriggerId.get(triggerId);
                    if (list == null) {
                        list = new ArrayList<TriggerRouter>();
                        triggerRoutersByTriggerId.put(triggerId, list);
                    }
                    list.add(triggerRouter);
                    routers.put(triggerRouter.getRouter().getRouterId(),
                            triggerRouter.getRouter());
                }
            }
        }
        newTriggerRouterCacheByNodeGroupId.put(myNodeGroupId, new TriggerRouterRoutersCache(
                triggerRoutersByTriggerId, routers));
        return newTriggerRouterCacheByNodeGroupId;
    }

    /**
     * @see ITriggerRouterService#getActiveRouterByIdForCurrentNode(String, boolean)
     */
    public Router getActiveRouterByIdForCurrentNode(String routerId, boolean refreshCache) {
        return getTriggerRoutersCacheForCurrentNode(refreshCache).routersByRouterId.get(routerId);
    }

    /**
     * @see ITriggerRouterService#getRoutersByGroupLink(NodeGroupLink)
     */
    public List<Router> getRoutersByGroupLink(NodeGroupLink link) {
        return sqlTemplate.query(
                getSql("select", "selectRoutersColumnList", "selectRouterByNodeGroupLinkWhereSql"),
                new RouterMapper(configurationService.getNodeGroupLinks(false)), link.getSourceNodeGroupId(), link.getTargetNodeGroupId());
    }

    public Trigger getTriggerForCurrentNodeById(String triggerId) {
        List<Trigger> triggers = getTriggersForCurrentNode();
        for (Trigger trigger : triggers) {
            if (trigger.getTriggerId().equals(triggerId)) {
                return trigger;
            }
        }
        return null;
    }

    public Trigger getTriggerById(String triggerId) {
        return getTriggerById(triggerId, true);
    }

    public Trigger getTriggerById(String triggerId, boolean refreshCache) {
        Trigger trigger = cacheManager.getTriggers(refreshCache).get(triggerId);
        if (trigger == null && !refreshCache) {
            trigger = getTriggerById(triggerId, true);
        }
        return trigger;
    }

    public Router getRouterById(String routerId) {
        return getRouterById(routerId, true);
    }

    public Router getRouterById(String routerId, boolean refreshCache) {
        Map<String, Router> cache = cacheManager.getRouters(refreshCache);
        return (cache != null ? cache.get(routerId) : null);
    }

    public List<Router> getRouters() {
        return getRouters(true);
    }

    public List<Router> getRouters(boolean replaceVariables) {
        List<Router> routers = sqlTemplate.query(getSql("select ", "selectRoutersColumnList", "selectRoutersSql"),
                new RouterMapper(configurationService.getNodeGroupLinks(false)));
        if (replaceVariables) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Map<String, String> replacements = (Map) parameterService.getAllParameters();
            for (Router router : routers) {
                router.setTargetCatalogName(FormatUtils.replaceTokens(router.getTargetCatalogName(), replacements, true));
                router.setTargetSchemaName(FormatUtils.replaceTokens(router.getTargetSchemaName(), replacements, true));
                router.setTargetTableName(FormatUtils.replaceTokens(router.getTargetTableName(), replacements, true));
            }
        }
        return routers;
    }

    private String getTriggerRouterSql(String sql) {
        return getSql("select ", "selectTriggerRoutersColumnList", "selectTriggerRoutersSql", sql);
    }

    public List<TriggerRouter> getTriggerRouters(boolean refreshCache) {
        return cacheManager.getTriggerRouters(refreshCache);
    }

    public List<TriggerRouter> getTriggerRoutersFromDatabase() {
        return enhanceTriggerRouters(sqlTemplate.query(
                getTriggerRouterSql(null), new TriggerRouterMapper()));
    }

    public List<TriggerRouter> getAllTriggerRoutersForCurrentNode(String sourceNodeGroupId) {
        List<TriggerRouter> triggerRouters = enhanceTriggerRouters(sqlTemplate.query(
                getTriggerRouterSql("activeTriggersForSourceNodeGroupSql"),
                new TriggerRouterMapper(), sourceNodeGroupId));
        mergeInConfigurationTablesTriggerRoutersForCurrentNode(sourceNodeGroupId, triggerRouters);
        return triggerRouters;
    }

    public List<TriggerRouter> getTriggerRoutersForTargetNode(String targetNodeGroupId) {
        List<TriggerRouter> triggerRouters = enhanceTriggerRouters(sqlTemplate.query(getTriggerRouterSql("activeTriggersForTargetNodeGroupSql"),
                new TriggerRouterMapper(), targetNodeGroupId));
        return triggerRouters;
    }

    public List<TriggerRouter> getTriggerRoutersForSourceAndTargetNodes(String sourceNodeGroupId, String targetNodeGroupId) {
        List<TriggerRouter> triggerRouters = enhanceTriggerRouters(
                sqlTemplate.query(getTriggerRouterSql("activeTriggersForSourceAndTargetNodeGroupsSql"),
                        new TriggerRouterMapper(), sourceNodeGroupId, targetNodeGroupId));
        return triggerRouters;
    }

    public List<TriggerRouter> getAllTriggerRoutersForReloadForCurrentNode(
            String sourceNodeGroupId, String targetNodeGroupId) {
        return enhanceTriggerRouters(sqlTemplate.query(
                getTriggerRouterSql("activeTriggersForReloadSql"), new TriggerRouterMapper(),
                sourceNodeGroupId, targetNodeGroupId, Constants.CHANNEL_CONFIG));
    }

    public TriggerRouter findTriggerRouterById(String triggerId, String routerId) {
        return findTriggerRouterById(triggerId, routerId, true);
    }

    public TriggerRouter findTriggerRouterById(String triggerId, String routerId, boolean refreshCache) {
        return cacheManager.getTriggerRoutersById(refreshCache).get(triggerId + routerId);
    }

    public List<TriggerRouter> findTriggerRoutersByTriggerId(String triggerId, boolean refreshCache) {
        List<TriggerRouter> configs = (List<TriggerRouter>) sqlTemplate.query(
                getTriggerRouterSql("selectTriggerRoutersByTriggerIdSql"), new TriggerRouterMapper(), triggerId);
        for (TriggerRouter triggerRouter : configs) {
            triggerRouter.setRouter(getRouterById(triggerRouter.getRouter().getRouterId(), refreshCache));
            triggerRouter.setTrigger(getTriggerById(triggerRouter.getTrigger().getTriggerId(), refreshCache));
        }
        return configs;
    }

    public List<TriggerRouter> findTriggerRoutersByRouterId(String routerId, boolean refreshCache) {
        List<TriggerRouter> configs = (List<TriggerRouter>) sqlTemplate.query(
                getTriggerRouterSql("selectTriggerRoutersByRouterIdSql"), new TriggerRouterMapper(), routerId);
        for (TriggerRouter triggerRouter : configs) {
            triggerRouter.setRouter(getRouterById(triggerRouter.getRouter().getRouterId(), refreshCache));
            triggerRouter.setTrigger(getTriggerById(triggerRouter.getTrigger().getTriggerId(), refreshCache));
        }
        return configs;
    }

    private List<TriggerRouter> enhanceTriggerRouters(List<TriggerRouter> triggerRouters) {
        HashMap<String, Router> routersById = new HashMap<String, Router>();
        for (Router router : getRouters()) {
            routersById.put(router.getRouterId().trim().toUpperCase(), router);
        }
        HashMap<String, Trigger> triggersById = new HashMap<String, Trigger>();
        for (Trigger trigger : getTriggers()) {
            triggersById.put(trigger.getTriggerId().trim().toUpperCase(), trigger);
        }
        for (TriggerRouter triggerRouter : triggerRouters) {
            triggerRouter.setTrigger(triggersById.get(triggerRouter.getTrigger().getTriggerId().trim().toUpperCase()));
            triggerRouter.setRouter(routersById.get(triggerRouter.getRouter().getRouterId().trim().toUpperCase()));
        }
        return triggerRouters;
    }

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannel(String nodeGroupId) {
        return getTriggerRoutersByChannel(nodeGroupId, false);
    }

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannel(String nodeGroupId, boolean refreshCache) {
        return cacheManager.getTriggerRoutersByChannel(nodeGroupId, refreshCache);
    }

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannelFromDatabase(String nodeGroupId) {
        final Map<String, List<TriggerRouter>> newValue = new HashMap<String, List<TriggerRouter>>();
        List<TriggerRouter> triggerRouters = enhanceTriggerRouters(sqlTemplate.query(
                getTriggerRouterSql("selectGroupTriggersSql"), new TriggerRouterMapper(), nodeGroupId, nodeGroupId));
        for (TriggerRouter triggerRouter : triggerRouters) {
            List<TriggerRouter> list = newValue.get(triggerRouter.getTrigger().getChannelId());
            if (list == null) {
                list = new ArrayList<TriggerRouter>();
                newValue.put(triggerRouter.getTrigger().getChannelId(), list);
            }
            list.add(triggerRouter);
        }
        return newValue;
    }

    protected void handleTableNotFound(Trigger trigger, List<TriggerHistory> activeTriggerHistories, TriggerRouterContext context) {
        log.error("Table not found: {}", trigger.qualifiedSourceTableName());
        TriggerHistory hist = getNewestTriggerHistoryForTrigger(activeTriggerHistories, trigger.getTriggerId(), trigger.getSourceCatalogName(),
                trigger.getSourceSchemaName(), trigger.getSourceTableName());
        TriggerHistory errorHist = getTableNotFoundTriggerHistory(trigger);
        if (hist == null) {
            insert(errorHist);
        } else if (!errorHist.getErrorMessage().equals(hist.getErrorMessage())) {
            long ts = System.currentTimeMillis();
            inactivateTriggerHistory(hist);
            context.incrementInactivateTriggerHistTime(System.currentTimeMillis() - ts);
            insert(errorHist);
        }
        context.addTablesNotFound(trigger.qualifiedSourceTableName());
        context.incrementTriggersSyncedCount(1);
        for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
            long ts = System.currentTimeMillis();
            l.tableDoesNotExist(context.getTriggersToSyncCount(), context.getTriggersSyncedCount(), trigger);
            context.incrementTableDoesNotExistTime(System.currentTimeMillis() - ts);
        }
    }

    protected TriggerHistory getTableNotFoundTriggerHistory(Trigger trigger) {
        final String UNKNOWN = "?";
        TriggerHistory hist = new TriggerHistory(trigger);
        hist.setErrorMessage("Table not found");
        hist.setLastTriggerBuildReason(TriggerReBuildReason.TABLE_SCHEMA_CHANGED);
        hist.setColumnNames(UNKNOWN);
        hist.setPkColumnNames(UNKNOWN);
        hist.setCreateTime(new Date());
        hist.setInactiveTime(hist.getCreateTime());
        return hist;
    }

    public void insert(TriggerHistory newHistRecord) {
        if (newHistRecord.getTriggerHistoryId() <= 0) {
            newHistRecord.setTriggerHistoryId((int) sequenceService.nextVal(Constants.SEQUENCE_TRIGGER_HIST));
        }
        historyMap.put(newHistRecord.getTriggerHistoryId(), newHistRecord);
        sqlTemplate.update(
                getSql("insertTriggerHistorySql"),
                new Object[] { newHistRecord.getTriggerHistoryId(), newHistRecord.getTriggerId(),
                        newHistRecord.getSourceTableName(), newHistRecord.getTableHash(), newHistRecord.getCreateTime(),
                        newHistRecord.getColumnNames(), newHistRecord.getPkColumnNames(),
                        newHistRecord.isMissingPk() ? 1 : 0, newHistRecord.getLastTriggerBuildReason().getCode(),
                        newHistRecord.getNameForDeleteTrigger(), newHistRecord.getNameForInsertTrigger(),
                        newHistRecord.getNameForUpdateTrigger(), newHistRecord.getSourceSchemaName(),
                        newHistRecord.getSourceCatalogName(), newHistRecord.getTriggerRowHash(),
                        newHistRecord.getTriggerTemplateHash(), newHistRecord.getErrorMessage(), newHistRecord.getInactiveTime() },
                new int[] { Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.TIMESTAMP, Types.VARCHAR,
                        Types.VARCHAR, Types.SMALLINT, Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                        Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.TIMESTAMP });
    }

    @Override
    public void deleteTriggerRouter(String triggerId, String routerId) {
        sqlTemplate.update(getSql("deleteTriggerRouterSql"), triggerId, routerId);
        clearCache();
    }

    private void deleteTriggerRoutersByTriggerId(String triggerId) {
        sqlTemplate.update(getSql("deleteTriggerRoutersByTriggerIdSql"), triggerId);
        clearCache();
    }

    private void deleteTriggerRoutersByRouterId(String routerId) {
        sqlTemplate.update(getSql("deleteTriggerRoutersByRouterIdSql"), routerId);
        clearCache();
    }

    public void deleteTriggerRouter(TriggerRouter triggerRouter) {
        sqlTemplate.update(getSql("deleteTriggerRouterSql"), (Object) triggerRouter.getTrigger()
                .getTriggerId(), triggerRouter.getRouter().getRouterId());
        clearCache();
    }

    @Override
    public void deleteTriggerRouters(Collection<TriggerRouter> triggerRouters) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            deleteTriggerRouters(transaction, triggerRouters);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw e;
        } finally {
            close(transaction);
        }
        clearCache();
    }

    protected void deleteTriggerRouters(ISqlTransaction transaction, Collection<TriggerRouter> triggerRouters) {
        int maxRowsToFlush = parameterService.getInt(ParameterConstants.DATA_FLUSH_JDBC_BATCH_SIZE);
        transaction.prepare(getSql("deleteTriggerRouterSql"));
        int[] types = new int[] { Types.VARCHAR };
        int rowCount = 0;
        for (TriggerRouter triggerRouter : triggerRouters) {
            transaction.addRow(null, new Object[] { triggerRouter.getTrigger().getTriggerId(), triggerRouter.getRouter().getRouterId() }, types);
            if (++rowCount > maxRowsToFlush) {
                transaction.flush();
            }
        }
        transaction.flush();
    }

    public void deleteAllTriggerRouters() {
        sqlTemplate.update(getSql("deleteAllTriggerRoutersSql"));
        clearCache();
    }

    public void saveTriggerRouter(TriggerRouter triggerRouter) {
        saveTriggerRouter(triggerRouter, false);
    }

    @Override
    public void saveTriggerRouter(TriggerRouter triggerRouter, boolean updateTriggerRouterTableOnly) {
        if (!updateTriggerRouterTableOnly) {
            saveTrigger(triggerRouter.getTrigger());
            saveRouter(triggerRouter.getRouter());
        }
        triggerRouter.setLastUpdateTime(new Date());
        if (0 >= sqlTemplate.update(getSql("updateTriggerRouterSql"), getTriggerRouterSqlValues(triggerRouter),
                getTriggerRouterSqlTypes())) {
            triggerRouter.setCreateTime(triggerRouter.getLastUpdateTime());
            sqlTemplate.update(getSql("insertTriggerRouterSql"), getTriggerRouterSqlValues(triggerRouter),
                    getTriggerRouterSqlTypes());
        }
        clearCache();
    }

    public void renameTriggerRouter(String oldTriggerId, String oldRouterId, TriggerRouter triggerRouter) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            saveTriggerRouter(triggerRouter, true);
            sqlTemplate.update(getSql("updateTriggerRouterIdSql0"), triggerRouter.getTriggerId(), oldTriggerId);
            sqlTemplate.update(getSql("updateTriggerRouterIdSql1"), triggerRouter.getRouterId(), oldRouterId);
            deleteTriggerRouter(oldTriggerId, oldRouterId);
            transaction.commit();
        } catch (Exception ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    private void renameTriggerRouters(String newTriggerId, String newRouterId, List<TriggerRouter> triggerRouters) {
        if (triggerRouters != null && !triggerRouters.isEmpty()) {
            if (newTriggerId != null) {
                String oldTriggerId = triggerRouters.get(0).getTriggerId();
                for (TriggerRouter triggerRouter : triggerRouters) {
                    triggerRouter.setTriggerId(newTriggerId);
                    saveTriggerRouter(triggerRouter);
                }
                sqlTemplate.update(getSql("updateTriggerRouterIdSql0"), newTriggerId, oldTriggerId);
                deleteTriggerRoutersByTriggerId(oldTriggerId);
            } else if (newRouterId != null) {
                String oldRouterId = triggerRouters.get(0).getRouterId();
                for (TriggerRouter triggerRouter : triggerRouters) {
                    triggerRouter.setRouterId(newRouterId);
                    saveTriggerRouter(triggerRouter);
                }
                sqlTemplate.update(getSql("updateTriggerRouterIdSql1"), newRouterId, oldRouterId);
                deleteTriggerRoutersByRouterId(oldRouterId);
            }
        }
    }

    @Override
    public void insertTriggerRouters(Collection<TriggerRouter> triggerRouters) {
        insertUpdateTriggerRouters(triggerRouters, true, null);
    }

    @Override
    public void insertTriggersAndTriggerRouters(Collection<Trigger> triggers, Collection<TriggerRouter> triggerRouters) {
        insertUpdateTriggerRouters(triggerRouters, true, triggers);
    }

    @Override
    public void updateTriggerRouters(Collection<TriggerRouter> triggerRouters) {
        insertUpdateTriggerRouters(triggerRouters, false, null);
    }

    protected void insertUpdateTriggerRouters(Collection<TriggerRouter> triggerRouters, boolean isInsert, Collection<Trigger> triggers) {
        ISqlTransaction transaction = null;
        try {
            int maxRowsToFlush = parameterService.getInt(ParameterConstants.DATA_FLUSH_JDBC_BATCH_SIZE);
            transaction = sqlTemplate.startSqlTransaction();
            if (triggers != null) {
                insertUpdateTriggers(transaction, triggers, isInsert);
            }
            transaction.prepare(isInsert ? getSql("insertTriggerRouterSql") : getSql("updateTriggerRouterSql"));
            int[] types = getTriggerRouterSqlTypes();
            int rowCount = 0;
            for (TriggerRouter triggerRouter : triggerRouters) {
                triggerRouter.setLastUpdateTime(new Date());
                if (triggerRouter.getCreateTime() == null) {
                    triggerRouter.setCreateTime(triggerRouter.getLastUpdateTime());
                }
                transaction.addRow(null, getTriggerRouterSqlValues(triggerRouter), types);
                if (++rowCount > maxRowsToFlush) {
                    transaction.flush();
                }
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw e;
        } finally {
            close(transaction);
        }
        clearCache();
    }

    protected int[] getTriggerRouterSqlTypes() {
        return new int[] { Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.TIMESTAMP, Types.VARCHAR,
                Types.TIMESTAMP, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
    }

    protected Object[] getTriggerRouterSqlValues(TriggerRouter triggerRouter) {
        return new Object[] { triggerRouter.getInitialLoadOrder(), triggerRouter.getInitialLoadSelect(),
                triggerRouter.getInitialLoadDeleteStmt(), triggerRouter.isPingBackEnabled() ? 1 : 0,
                triggerRouter.getCreateTime(), triggerRouter.getLastUpdateBy(), triggerRouter.getLastUpdateTime(),
                triggerRouter.isEnabled() ? 1 : 0, triggerRouter.getDataRefreshType(), triggerRouter.getTrigger().getTriggerId(),
                triggerRouter.getRouter().getRouterId() };
    }

    protected void resetTriggerRouterCacheByNodeGroupId() {
        cacheManager.flushTriggerRoutersByNodeGroupId();
    }

    public void saveRouter(Router router) {
        router.setLastUpdateTime(new Date());
        router.nullOutBlankFields();
        if (0 >= sqlTemplate.update(
                getSql("updateRouterSql"),
                new Object[] { router.getTargetCatalogName(), router.getTargetSchemaName(),
                        router.getTargetTableName(),
                        router.getNodeGroupLink().getSourceNodeGroupId(),
                        router.getNodeGroupLink().getTargetNodeGroupId(), router.getRouterType(),
                        router.getRouterExpression(), router.isSyncOnUpdate() ? 1 : 0,
                        router.isSyncOnInsert() ? 1 : 0, router.isSyncOnDelete() ? 1 : 0,
                        router.isUseSourceCatalogSchema() ? 1 : 0,
                        router.getLastUpdateBy(), router.getLastUpdateTime(),
                        router.getRouterId() }, new int[] {
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                                Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.SMALLINT,
                                Types.SMALLINT, Types.SMALLINT, Types.VARCHAR, Types.TIMESTAMP,
                                Types.VARCHAR })) {
            router.setCreateTime(router.getLastUpdateTime());
            sqlTemplate.update(
                    getSql("insertRouterSql"),
                    new Object[] { router.getTargetCatalogName(), router.getTargetSchemaName(),
                            router.getTargetTableName(),
                            router.getNodeGroupLink().getSourceNodeGroupId(),
                            router.getNodeGroupLink().getTargetNodeGroupId(),
                            router.getRouterType(), router.getRouterExpression(),
                            router.isSyncOnUpdate() ? 1 : 0, router.isSyncOnInsert() ? 1 : 0,
                            router.isSyncOnDelete() ? 1 : 0, router.isUseSourceCatalogSchema() ? 1 : 0,
                            router.getCreateTime(),
                            router.getLastUpdateBy(), router.getLastUpdateTime(), router.getRouterId() },
                    new int[] {
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT,
                            Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.TIMESTAMP, Types.VARCHAR,
                            Types.TIMESTAMP, Types.VARCHAR });
        }
        clearCache();
    }

    public Router saveRouterAsCopy(Router router) {
        String newId = router.getRouterId();
        List<Router> routers = sqlTemplate.query(
                getSql("select ", "selectRoutersColumnList", "selectRoutersWhereRouterIdLikeSql"),
                new RouterMapper(configurationService.getNodeGroupLinks(false)), newId + "%");
        List<String> ids = routers.stream().map(Router::getRouterId).collect(Collectors.toList());
        String suffix = "";
        for (int i = 2; ids.contains(newId + suffix); i++) {
            suffix = "_" + i;
        }
        router.setRouterId(newId + suffix);
        saveRouter(router);
        return router;
    }

    public void renameRouter(String oldId, Router router) {
        saveRouter(router);
        renameTriggerRouters(null, router.getRouterId(), findTriggerRoutersByRouterId(oldId, true));
        sqlTemplate.update(getSql("updateFileTriggerRouterSql"), router.getRouterId(), oldId);
        deleteRouter(oldId);
    }

    public boolean isRouterBeingUsed(String routerId) {
        return sqlTemplate.queryForInt(getSql("countTriggerRoutersByRouterIdSql"), routerId) > 0;
    }

    public void deleteRouter(Router router) {
        if (router != null) {
            sqlTemplate.update(getSql("deleteTriggerRoutersByRouterSql"), router.getRouterId());
            groupletService.deleteTriggerRouterGroupletsFor(router);
            sqlTemplate.update(getSql("deleteRouterSql"), router.getRouterId());
        }
    }

    private void deleteRouter(String id) {
        sqlTemplate.update(getSql("deleteRouterSql"), (Object) id);
    }

    public void deleteAllRouters() {
        sqlTemplate.update(getSql("deleteAllRoutersSql"));
    }

    @Override
    public void saveTrigger(Trigger trigger) {
        trigger.setLastUpdateTime(new Date());
        trigger.nullOutBlankFields();
        if (0 >= sqlTemplate.update(getSql("updateTriggerSql"), getTriggerSqlValues(trigger), getTriggerSqlTypes())) {
            trigger.setCreateTime(trigger.getLastUpdateTime());
            sqlTemplate.update(getSql("insertTriggerSql"), getTriggerSqlValues(trigger), getTriggerSqlTypes());
        }
        clearCache();
    }

    public void saveTriggerAsCopy(String originalId, Trigger trigger) {
        String newId = trigger.getTriggerId();
        List<Trigger> triggers = sqlTemplate.query(getSql("selectTriggersWhereTriggerIdLikeSql"), new TriggerMapper(), newId + "%");
        List<String> ids = triggers.stream().map(Trigger::getTriggerId).collect(Collectors.toList());
        String suffix = "";
        for (int i = 2; ids.contains(newId + suffix); i++) {
            suffix = "_" + i;
        }
        trigger.setTriggerId(newId + suffix);
        saveTrigger(trigger);
        for (TriggerRouter triggerRouter : findTriggerRoutersByTriggerId(originalId, true)) {
            triggerRouter.setTriggerId(newId + suffix);
            saveTriggerRouter(triggerRouter, true);
        }
    }

    public void renameTrigger(String oldId, Trigger trigger) {
        saveTrigger(trigger);
        renameTriggerRouters(trigger.getTriggerId(), null, findTriggerRoutersByTriggerId(oldId, true));
        deleteTrigger(oldId);
    }

    @Override
    public void insertTriggers(Collection<Trigger> triggers) {
        insertUpdateTriggers(triggers, true);
    }

    @Override
    public void updateTriggers(Collection<Trigger> triggers) {
        insertUpdateTriggers(triggers, false);
    }

    protected void insertUpdateTriggers(Collection<Trigger> triggers, boolean isInsert) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            insertUpdateTriggers(transaction, triggers, isInsert);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw e;
        } finally {
            close(transaction);
        }
        clearCache();
    }

    protected void insertUpdateTriggers(ISqlTransaction transaction, Collection<Trigger> triggers, boolean isInsert) {
        int maxRowsToFlush = parameterService.getInt(ParameterConstants.DATA_FLUSH_JDBC_BATCH_SIZE);
        transaction.prepare(isInsert ? getSql("insertTriggerSql") : getSql("updateTriggerSql"));
        int[] types = getTriggerSqlTypes();
        int rowCount = 0;
        for (Trigger trigger : triggers) {
            trigger.setLastUpdateTime(new Date());
            trigger.nullOutBlankFields();
            if (trigger.getCreateTime() == null) {
                trigger.setCreateTime(trigger.getLastUpdateTime());
            }
            transaction.addRow(null, getTriggerSqlValues(trigger), types);
            if (++rowCount > maxRowsToFlush) {
                transaction.flush();
            }
        }
        transaction.flush();
    }

    protected int[] getTriggerSqlTypes() {
        return new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT,
                Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.SMALLINT,
                Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR,
                Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR };
    }

    protected Object[] getTriggerSqlValues(Trigger trigger) {
        return new Object[] { trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                trigger.getSourceTableName(), trigger.getChannelId(), trigger.getReloadChannelId(),
                trigger.isSyncOnUpdate() ? 1 : 0, trigger.isSyncOnInsert() ? 1 : 0, trigger.isSyncOnDelete() ? 1 : 0,
                trigger.isSyncOnIncomingBatch() ? 1 : 0, trigger.isUseStreamLobs() ? 1 : 0,
                trigger.isUseCaptureLobs() ? 1 : 0, trigger.isUseCaptureOldData() ? 1 : 0,
                trigger.isUseHandleKeyUpdates() ? 1 : 0, trigger.getNameForUpdateTrigger(),
                trigger.getNameForInsertTrigger(), trigger.getNameForDeleteTrigger(),
                trigger.getSyncOnUpdateCondition(), trigger.getSyncOnInsertCondition(),
                trigger.getSyncOnDeleteCondition(), trigger.getCustomBeforeUpdateText(),
                trigger.getCustomBeforeInsertText(), trigger.getCustomBeforeDeleteText(),
                trigger.getCustomOnUpdateText(), trigger.getCustomOnInsertText(), trigger.getCustomOnDeleteText(),
                trigger.getTxIdExpression(), trigger.getExcludedColumnNames(), trigger.getIncludedColumnNames(),
                trigger.getSyncKeyNames(), trigger.getCreateTime(), trigger.getLastUpdateBy(), trigger.getLastUpdateTime(),
                trigger.getExternalSelect(), trigger.getChannelExpression(), trigger.isStreamRow(), trigger.getTimeBasedCaptureColumn(),
                trigger.getTriggerId() };
    }

    public boolean syncTriggers() {
        return syncTriggers(false);
    }

    public boolean syncTriggers(boolean force) {
        return syncTriggers((StringBuilder) null, force);
    }

    public boolean syncTriggers(StringBuilder sqlBuffer, boolean force) {
        if ((parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) || isCalledFromSymmetricAdminTool())) {
            synchronized (this) {
                if (clusterService.lock(ClusterConstants.SYNC_TRIGGERS)) {
                    TriggerRouterContext context = new TriggerRouterContext();
                    long startTime = System.currentTimeMillis();
                    try {
                        String additionalMessage = "";
                        if (isCalledFromSymmetricAdminTool()
                                && !parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)) {
                            additionalMessage = " "
                                    + ParameterConstants.AUTO_SYNC_TRIGGERS
                                    + " is set to false, but the sync triggers process will run so that needed changes can be written to a file so they can be applied manually.  Once all of the triggers have been successfully applied this process should not show triggers being created";
                        }
                        log.info("Synchronizing triggers{}", additionalMessage);
                        long ts = System.currentTimeMillis();
                        fixMultipleActiveTriggerHistories(context);
                        context.incrementFixMultipleActiveTriggerHistoriesTime(System.currentTimeMillis() - ts);
                        // make sure all tables are freshly read in
                        platform.resetCachedTableModel();
                        clearCache();
                        // make sure channels are read from the database
                        configurationService.clearCache();
                        ts = System.currentTimeMillis();
                        List<Trigger> triggersForCurrentNode = getTriggersForCurrentNode();
                        context.incrementTriggersForCurrentNodeTime(System.currentTimeMillis() - ts);
                        context.incrementTriggersToSyncCount(triggersForCurrentNode.size());
                        ts = System.currentTimeMillis();
                        for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                            l.syncTriggersStarted();
                        }
                        context.incrementSyncTriggersStartedTime(System.currentTimeMillis() - ts);
                        boolean createTriggersForTables = false;
                        String nodeId = nodeService.findIdentityNodeId();
                        if (StringUtils.isNotBlank(nodeId)) {
                            NodeSecurity nodeSecurity = null;
                            if (!force) {
                                nodeSecurity = nodeService.findNodeSecurity(nodeId);
                            }
                            if (nodeSecurity != null && (nodeSecurity.isInitialLoadEnabled() || nodeSecurity.getInitialLoadEndTime() == null)) {
                                createTriggersForTables = parameterService.is(ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD);
                                if (!createTriggersForTables) {
                                    log.info("Trigger creation has been disabled by "
                                            + ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD
                                            + " because an initial load is in progress or has not yet been requested");
                                }
                            } else {
                                createTriggersForTables = true;
                            }
                        }
                        if (!createTriggersForTables) {
                            triggersForCurrentNode.clear();
                        }
                        ts = System.currentTimeMillis();
                        List<TriggerHistory> activeTriggerHistories = getActiveTriggerHistories();
                        context.incrementActiveTriggerHistoriesTime(System.currentTimeMillis() - ts);
                        inactivateTriggers(triggersForCurrentNode, sqlBuffer, activeTriggerHistories, context);
                        updateOrCreateDatabaseTriggers(triggersForCurrentNode, sqlBuffer, force,
                                true, activeTriggerHistories, true, context);
                        resetTriggerRouterCacheByNodeGroupId();
                        if (createTriggersForTables && symmetricDialect.supportsDdlTriggers()) {
                            ts = System.currentTimeMillis();
                            updateOrCreateDdlTriggers(sqlBuffer);
                            context.incrementUpdateOrCreateDdlTriggersTime(System.currentTimeMillis() - ts);
                        }
                        statisticManager.addJobStats(ClusterConstants.SYNC_TRIGGERS, startTime, System.currentTimeMillis(), context.getTriggersSyncedCount(),
                                context.getErrorMessage());
                    } catch (RuntimeException e) {
                        statisticManager.addJobStats(ClusterConstants.SYNC_TRIGGERS, startTime, System.currentTimeMillis(), context.getTriggersSyncedCount(),
                                e);
                        throw e;
                    } finally {
                        long ts = System.currentTimeMillis();
                        for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                            l.syncTriggersEnded();
                        }
                        context.incrementSyncTriggersEndedTime(System.currentTimeMillis() - ts);
                        logTriggerRouterContextTimings(context);
                        logTriggerRouterContextAnomalies(context);
                        clusterService.unlock(ClusterConstants.SYNC_TRIGGERS);
                        log.info("Done synchronizing triggers");
                    }
                } else {
                    Lock lock = clusterService.findLocks().get(ClusterConstants.SYNC_TRIGGERS);
                    if (lock != null) {
                        log.info("Sync triggers was locked by the cluster service.  The locking server id was: {}.  The lock time was: {}", lock
                                .getLockingServerId(), lock.getLockTime());
                    } else {
                        log.info(
                                "Sync triggers was locked by the cluster service but lock details were not found. Perhaps the lock was released in the meantime.");
                    }
                    return false;
                }
            }
        } else {
            log.info("Not synchronizing triggers.  {} is set to false", ParameterConstants.AUTO_SYNC_TRIGGERS);
        }
        return true;
    }

    public void clearCache() {
        cacheManager.flushTriggerRoutersByNodeGroupId();
        cacheManager.flushTriggerRoutersByChannel();
        cacheManager.flushTriggerRouters();
        cacheManager.flushTriggerRoutersByTriggerHist();
        cacheManager.flushTriggerRoutersById();
        cacheManager.flushTriggers();
        cacheManager.flushRouters();
    }

    protected Set<String> getTriggerIdsFrom(List<Trigger> triggersThatShouldBeActive) {
        Set<String> triggerIds = new HashSet<String>(triggersThatShouldBeActive.size());
        for (Trigger trigger : triggersThatShouldBeActive) {
            triggerIds.add(trigger.getTriggerId());
        }
        return triggerIds;
    }

    protected Trigger getTriggerFromList(String triggerId, List<Trigger> triggersThatShouldBeActive) {
        for (Trigger trigger : triggersThatShouldBeActive) {
            if (trigger.getTriggerId().equals(triggerId)) {
                return trigger;
            }
        }
        return null;
    }

    private int getNumberOfThreadsToUseForSyncTriggers() {
        int numThreads = parameterService.getInt(ParameterConstants.SYNC_TRIGGERS_THREAD_COUNT_PER_SERVER);
        if (parameterService.is(ParameterConstants.SYNCHRONIZE_ALL_JOBS, false) || !platform.isUseMultiThreadSyncTriggers()) {
            numThreads = 1;
        }
        return numThreads;
    }

    protected void inactivateTriggers(final List<Trigger> triggersThatShouldBeActive,
            final StringBuilder sqlBuffer, List<TriggerHistory> activeTriggerHistories,
            TriggerRouterContext triggerRouterContext) {
        final boolean ignoreCase = this.parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE);
        final Map<String, Set<Table>> tablesByTriggerId = new HashMap<String, Set<Table>>();
        int numThreads = getNumberOfThreadsToUseForSyncTriggers();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads, new SyncTriggersThreadFactory());
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (final TriggerHistory history : activeTriggerHistories) {
            Runnable runnable = new Runnable() {
                public void run() {
                    MDC.put("engineName", parameterService.getEngineName());
                    Set<Table> tables;
                    synchronized (tablesByTriggerId) {
                        tables = tablesByTriggerId.get(history.getTriggerId());
                    }
                    Trigger trigger = getTriggerFromList(history.getTriggerId(), triggersThatShouldBeActive);
                    if (tables == null && trigger != null) {
                        tables = getTablesForTrigger(trigger, triggersThatShouldBeActive, true, triggerRouterContext);
                        synchronized (tablesByTriggerId) {
                            tablesByTriggerId.put(trigger.getTriggerId(), tables);
                        }
                    }
                    boolean foundTable = false;
                    if (tables != null && trigger != null) {
                        for (Table table : tables) {
                            boolean matchesCatalog = isEqual(
                                    trigger.isSourceCatalogNameWildCarded() ? table.getCatalog()
                                            : trigger.getSourceCatalogNameUnescaped(),
                                    history.getSourceCatalogName(), ignoreCase);
                            boolean matchesSchema = isEqual(
                                    trigger.isSourceSchemaNameWildCarded() ? table.getSchema()
                                            : trigger.getSourceSchemaNameUnescaped(), history.getSourceSchemaName(),
                                    ignoreCase);
                            boolean matchesTable = isEqual(
                                    (trigger.isSourceTableNameWildCarded() || trigger.isSourceTableNameExpanded()) ? table.getName()
                                            : trigger.getSourceTableNameUnescaped(), history.getSourceTableName(),
                                    ignoreCase);
                            foundTable |= matchesCatalog && matchesSchema && matchesTable;
                        }
                    }
                    if (trigger == null) {
                        log.info("About to remove triggers for inactivated table: {}", history.getFullyQualifiedSourceTableName());
                        dropTriggersIfNeeded(history, sqlBuffer, triggerRouterContext, true);
                    } else if (!foundTable && history.getErrorMessage() == null) {
                        log.info("About to inactivate trigger history {} because table is missing: {}", history.getTriggerHistoryId(),
                                trigger.qualifiedSourceTableName());
                        dropTriggersIfNeeded(history, sqlBuffer, triggerRouterContext, foundTable);
                    }
                }
            };
            futures.add(executor.submit(runnable));
        }
        awaitTermination(executor, futures);
    }

    protected boolean isEqual(String one, String two, boolean ignoreCase) {
        if (ignoreCase) {
            return StringUtils.equalsIgnoreCase(one, two);
        } else {
            return StringUtils.equals(one, two);
        }
    }

    protected void dropTriggersIfNeeded(TriggerHistory history, StringBuilder sqlBuffer, TriggerRouterContext triggerRouterContext, boolean foundTable) {
        if (foundTable || platform.getDatabaseInfo().canTriggerExistWithoutTable() || platform != getTargetPlatform(history.getSourceTableName())) {
            dropTriggers(history, sqlBuffer, triggerRouterContext);
        } else {
            long ts = System.currentTimeMillis();
            inactivateTriggerHistory(history);
            triggerRouterContext.incrementInactivateTriggerHistTime(System.currentTimeMillis() - ts);
        }
    }

    public void dropTriggers(TriggerHistory history) {
        TriggerRouterContext triggerRouterContext = new TriggerRouterContext();
        dropTriggers(history, null, triggerRouterContext);
        log.info("DropTriggers: it took {} ms to drop triggers for trigger ID {}", triggerRouterContext.getDropTriggerTime(),
                history.getTriggerId());
    }

    protected void dropTriggers(TriggerHistory history, StringBuilder sqlBuffer) {
        TriggerRouterContext triggerRouterContext = new TriggerRouterContext();
        dropTriggers(history, sqlBuffer, triggerRouterContext);
        if (sqlBuffer == null) {
            log.info("DropTriggers: it took {} ms to drop triggers for trigger ID {}", triggerRouterContext.getDropTriggerTime(),
                    history.getTriggerId());
        }
    }

    protected void dropTriggers(TriggerHistory history, StringBuilder sqlBuffer, TriggerRouterContext context) {
        try {
            long ts = System.currentTimeMillis();
            dropTrigger(sqlBuffer, history.getSourceCatalogName(), history.getSourceSchemaName(),
                    history.getNameForInsertTrigger(), history.getSourceTableName());
            dropTrigger(sqlBuffer, history.getSourceCatalogName(), history.getSourceSchemaName(),
                    history.getNameForDeleteTrigger(), history.getSourceTableName());
            dropTrigger(sqlBuffer, history.getSourceCatalogName(), history.getSourceSchemaName(),
                    history.getNameForUpdateTrigger(), history.getSourceTableName());
            context.incrementDropTriggerTime(System.currentTimeMillis() - ts);
            if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)) {
                context.incrementTriggersSyncedCount(1);
                for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                    ts = System.currentTimeMillis();
                    l.triggerInactivated(context.getTriggersToSyncCount(), context.getTriggersSyncedCount(), null, history);
                    context.incrementTriggerInactivatedTime(System.currentTimeMillis() - ts);
                }
            }
            ts = System.currentTimeMillis();
            boolean triggerExists = symmetricDialect.doesTriggerExist(sqlBuffer, history.getSourceCatalogName(),
                    history.getSourceSchemaName(), history.getSourceTableName(), history.getNameForInsertTrigger());
            triggerExists |= symmetricDialect.doesTriggerExist(sqlBuffer, history.getSourceCatalogName(),
                    history.getSourceSchemaName(), history.getSourceTableName(), history.getNameForUpdateTrigger());
            triggerExists |= symmetricDialect.doesTriggerExist(sqlBuffer, history.getSourceCatalogName(),
                    history.getSourceSchemaName(), history.getSourceTableName(), history.getNameForDeleteTrigger());
            context.incrementDoesTriggerExistTime(System.currentTimeMillis() - ts);
            if (triggerExists) {
                log.warn(
                        "There are triggers that have been marked as inactive.  Please remove triggers represented by trigger_id={} and trigger_hist_id={}",
                        history.getTriggerId(), history.getTriggerHistoryId());
            } else if (history.getInactiveTime() == null) {
                ts = System.currentTimeMillis();
                inactivateTriggerHistory(history);
                context.incrementInactivateTriggerHistTime(System.currentTimeMillis() - ts);
            }
        } catch (Throwable ex) {
            log.error("Error while dropping triggers for table {}", history.getSourceTableName(), ex);
        }
    }

    protected void dropTrigger(StringBuilder sqlBuffer, String catalog, String schema, String triggerName, String tableName) {
        if (StringUtils.isNotBlank(triggerName)) {
            try {
                symmetricDialect.removeTrigger(sqlBuffer, catalog, schema, triggerName, tableName);
            } catch (SqlException e) {
                log.error("Error while dropping trigger {} for table {}: {}", triggerName, tableName, e.getMessage());
            } catch (Throwable e) {
                log.error("Error while dropping trigger {} for table {}", triggerName, tableName, e);
            }
        }
    }

    protected List<TriggerRouter> toList(Collection<List<TriggerRouter>> source) {
        ArrayList<TriggerRouter> list = new ArrayList<TriggerRouter>();
        for (List<TriggerRouter> triggerRouters : source) {
            for (TriggerRouter triggerRouter : triggerRouters) {
                list.add(triggerRouter);
            }
        }
        return list;
    }

    protected List<Trigger> getTriggersForCurrentNode() {
        return new TriggerSelector(toList(getTriggerRoutersForCurrentNode(false).values()))
                .select();
    }

    protected Set<Table> getTablesForTrigger(Trigger trigger, List<Trigger> triggers, boolean useTableCache,
            TriggerRouterContext triggerRouterContext) {
        try {
            return getTablesForTriggerWithException(trigger, triggers, useTableCache, triggerRouterContext);
        } catch (RuntimeException e) {
            if (platform.getSqlTemplate().isDeadlock(e)) {
                log.warn("Deadlock occurred, so retrying");
                return getTablesForTriggerWithException(trigger, triggers, useTableCache, triggerRouterContext);
            }
            throw e;
        }
    }

    protected Set<Table> getTablesForTriggerWithException(Trigger trigger, List<Trigger> triggers, boolean useTableCache,
            TriggerRouterContext triggerRouterContext) {
        long ts = System.currentTimeMillis();
        Set<Table> tables = new HashSet<Table>();
        IDatabasePlatform sourcePlatform = getTargetPlatform(trigger.getSourceTableName());
        try {
            boolean ignoreCase = this.parameterService
                    .is(ParameterConstants.DB_METADATA_IGNORE_CASE);
            List<String> catalogNames = new ArrayList<String>();
            if (trigger.isSourceCatalogNameWildCarded()) {
                List<String> all = sourcePlatform.getDdlReader().getCatalogNames();
                for (String catalogName : all) {
                    if (trigger.matchesCatalogName(catalogName, ignoreCase)) {
                        catalogNames.add(catalogName);
                    }
                }
                if (catalogNames.size() == 0) {
                    catalogNames.add(null);
                }
            } else {
                if (isBlank(trigger.getSourceCatalogName())) {
                    catalogNames.add(sourcePlatform.getDefaultCatalog());
                } else {
                    catalogNames.add(trigger.getSourceCatalogNameUnescaped());
                }
            }
            for (String catalogName : catalogNames) {
                List<String> schemaNames = new ArrayList<String>();
                if (trigger.isSourceSchemaNameWildCarded()) {
                    List<String> all = sourcePlatform.getDdlReader().getSchemaNames(catalogName);
                    for (String schemaName : all) {
                        if (trigger.matchesSchemaName(schemaName, ignoreCase)) {
                            schemaNames.add(schemaName);
                        }
                    }
                    if (schemaNames.size() == 0) {
                        schemaNames.add(null);
                    }
                } else {
                    if (isBlank(trigger.getSourceSchemaName())) {
                        schemaNames.add(sourcePlatform.getDefaultSchema());
                    } else {
                        schemaNames.add(trigger.getSourceSchemaNameUnescaped());
                    }
                }
                for (String schemaName : schemaNames) {
                    if (trigger.isSourceTableNameWildCarded()) {
                        Database database = sourcePlatform.readDatabase(
                                catalogName, schemaName,
                                new String[] { "TABLE" });
                        Table[] tableArray = database.getTables();
                        for (Table table : tableArray) {
                            if (trigger.matches(table, catalogName,
                                    schemaName, ignoreCase)
                                    && !containsExactMatchForSourceTableName(table, triggers,
                                            ignoreCase)
                                    && !table.getName().toLowerCase().startsWith(tablePrefix)) {
                                tables.add(table);
                            }
                        }
                    } else if (!trigger.getSourceTableName().startsWith(parameterService.getTablePrefix() + "_")
                            && CollectionUtils.isNotEmpty(extensionService.getExtensionPointList(ITableResolver.class))) {
                        for (ITableResolver resolver : extensionService.getExtensionPointList(ITableResolver.class)) {
                            resolver.resolve(catalogName, schemaName, tables, sourcePlatform, nodeService, trigger, useTableCache,
                                    triggerRouterContext);
                        }
                    } else {
                        Table table = sourcePlatform.getTableFromCache(
                                catalogName, schemaName,
                                trigger.getSourceTableNameUnescaped(), !useTableCache);
                        if (table != null) {
                            tables.add(table);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Failed to retrieve tables for trigger with id of %s", trigger.getTriggerId()), ex);
        }
        triggerRouterContext.incrementTablesForTriggerTime(System.currentTimeMillis() - ts);
        return tables;
    }

    private boolean containsExactMatchForSourceTableName(Table table, List<Trigger> triggers,
            boolean ignoreCase) {
        for (Trigger trigger : triggers) {
            if (!trigger.isSourceWildCarded()) {
                String sourceCatalogName = trigger.getSourceCatalogName() != null ? trigger.getSourceCatalogName() : platform.getDefaultCatalog();
                String sourceSchemaName = trigger.getSourceSchemaName() != null ? trigger.getSourceSchemaName() : platform.getDefaultSchema();
                if (trigger.getSourceTableName().equals(table.getName())
                        && (sourceCatalogName == null || sourceCatalogName.equals(table.getCatalog())) &&
                        (sourceSchemaName == null || sourceSchemaName.equals(table.getSchema()))) {
                    return true;
                } else if (ignoreCase && trigger.getSourceTableName().equalsIgnoreCase(table.getName())
                        && (sourceCatalogName == null || sourceCatalogName.equalsIgnoreCase(table.getCatalog()))
                        && (sourceSchemaName == null || sourceSchemaName.equalsIgnoreCase(table.getSchema()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean syncTriggers(String targetExternalId, boolean force) {
        if (cacheManager.isUsingTargetExternalId(false)) {
            List<Trigger> triggers = getTriggersForCurrentNode();
            List<Table> tables = new ArrayList<Table>();
            for (Trigger trigger : triggers) {
                if (trigger.getSourceTableName().contains("targetExternalId")) {
                    Table table = platform.readTableFromDatabase(trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                            FormatUtils.replace("targetExternalId", targetExternalId, trigger.getSourceTableName()));
                    if (table != null) {
                        tables.add(table);
                    }
                }
            }
            if (tables.size() > 0) {
                return syncTriggers(tables, force);
            }
        }
        return true;
    }

    public boolean syncTriggers(Table table, boolean force) {
        return syncTriggers(Arrays.asList(table), force);
    }

    public boolean syncTriggers(List<Table> tables, boolean force) {
        if (clusterService.lock(ClusterConstants.SYNC_TRIGGERS)) {
            TriggerRouterContext context = new TriggerRouterContext();
            long startTime = System.currentTimeMillis();
            try {
                fixMultipleActiveTriggerHistories(context);
                boolean ignoreCase = this.parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE);
                long ts = System.currentTimeMillis();
                for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                    l.syncTriggersStarted();
                }
                context.incrementSyncTriggersStartedTime(System.currentTimeMillis() - ts);
                ts = System.currentTimeMillis();
                List<Trigger> triggersForCurrentNode = getTriggersForCurrentNode();
                context.incrementTriggersForCurrentNodeTime(System.currentTimeMillis() - ts);
                ts = System.currentTimeMillis();
                List<TriggerHistory> activeTriggerHistories = getActiveTriggerHistories();
                context.incrementActiveTriggerHistoriesTime(System.currentTimeMillis() - ts);
                Map<String, List<TriggerTableSupportingInfo>> triggerToTableSupportingInfo = getTriggerToTableSupportingInfo(
                        triggersForCurrentNode, activeTriggerHistories, false, context);
                Map<Trigger, List<Table>> triggersToProcess = new HashMap<Trigger, List<Table>>();
                for (Table table : tables) {
                    IDatabasePlatform targetPlatform = symmetricDialect.getTargetPlatform(table.getName());
                    for (Trigger trigger : triggersForCurrentNode) {
                        if (trigger.matches(table, targetPlatform.getDefaultCatalog(), targetPlatform.getDefaultSchema(), ignoreCase) &&
                                (!trigger.isSourceTableNameWildCarded() || !trigger.isSourceTableNameExpanded()
                                        || !containsExactMatchForSourceTableName(table, triggersForCurrentNode, ignoreCase))) {
                            List<Table> l = triggersToProcess.get(trigger);
                            if (l == null) {
                                l = new ArrayList<Table>();
                                triggersToProcess.put(trigger, l);
                            }
                            l.add(table);
                        }
                    }
                }
                if (triggersToProcess.size() > 0) {
                    context.incrementTriggersToSyncCount(triggersToProcess.size());
                    for (Map.Entry<Trigger, List<Table>> entry : triggersToProcess.entrySet()) {
                        Trigger trigger = entry.getKey();
                        List<Table> l = entry.getValue();
                        List<TriggerTableSupportingInfo> triggerTableSupportingInfoList = triggerToTableSupportingInfo.get(trigger.getTriggerId());
                        for (Table table : l) {
                            TriggerTableSupportingInfo triggerTableSupportingInfo = null;
                            for (TriggerTableSupportingInfo t : triggerTableSupportingInfoList) {
                                if (getFullyQualifiedTableName(t.getTable()).equals(getFullyQualifiedTableName(table))) {
                                    triggerTableSupportingInfo = t;
                                    break;
                                }
                            }
                            if (triggerTableSupportingInfo != null) {
                                log.info("Synchronizing triggers for {}", table.getFullyQualifiedTableName());
                                ts = System.currentTimeMillis();
                                updateOrCreateDatabaseTriggers(trigger, triggerTableSupportingInfo.getTable(), null, force, true, activeTriggerHistories,
                                        triggerTableSupportingInfo, context);
                                context.incrementUpdateOrCreateDatabaseTriggersTime(System.currentTimeMillis() - ts);
                                log.info("Done synchronizing triggers for {}", table.getFullyQualifiedTableName());
                            } else {
                                handleTableNotFound(trigger, activeTriggerHistories, context);
                            }
                        }
                    }
                }
                logTriggerRouterContextTimings(context);
                statisticManager.addJobStats(ClusterConstants.SYNC_TRIGGERS, startTime, System.currentTimeMillis(), context.getTriggersSyncedCount(),
                        context.getErrorMessage());
                return true;
            } catch (RuntimeException e) {
                statisticManager.addJobStats(ClusterConstants.SYNC_TRIGGERS, startTime, System.currentTimeMillis(), context.getTriggersSyncedCount(), e);
                throw e;
            } finally {
                logTriggerRouterContextAnomalies(context);
                clusterService.unlock(ClusterConstants.SYNC_TRIGGERS);
                long ts = System.currentTimeMillis();
                for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                    l.syncTriggersEnded();
                }
                context.incrementSyncTriggersEndedTime(System.currentTimeMillis() - ts);
            }
        }
        return false;
    }

    protected String getFullyQualifiedTableName(Table table) {
        IDatabasePlatform targetPlatform = symmetricDialect.getTargetPlatform(table.getName());
        String catalog = StringUtils.isNotBlank(table.getCatalog()) ? table.getCatalog() : targetPlatform.getDefaultCatalog();
        String schema = StringUtils.isNotBlank(table.getSchema()) ? table.getSchema() : targetPlatform.getDefaultSchema();
        return Table.getFullyQualifiedTableName(catalog, schema, table.getName());
    }

    protected void updateOrCreateDdlTriggers(StringBuilder sqlBuffer) {
        String allDdlTriggerName = tablePrefix + "_on_all_ddl";
        String filteredDdlTriggerName = tablePrefix + "_on_filtered_ddl";
        boolean isCapture = parameterService.is(ParameterConstants.TRIGGER_CAPTURE_DDL_CHANGES, false);
        boolean isFiltered = parameterService.is(ParameterConstants.TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST, true);
        boolean allDdlTriggerExists = getTargetDialect().doesDdlTriggerExist(getTargetPlatform().getDefaultCatalog(),
                getTargetPlatform().getDefaultSchema(), allDdlTriggerName);
        boolean filteredDdlTriggerExists = getTargetDialect().doesDdlTriggerExist(getTargetPlatform().getDefaultCatalog(),
                getTargetPlatform().getDefaultSchema(), filteredDdlTriggerName);
        if (isCapture) {
            if (isFiltered) {
                if (allDdlTriggerExists) {
                    getTargetDialect().removeDdlTrigger(sqlBuffer, getTargetPlatform().getDefaultCatalog(), getTargetPlatform().getDefaultSchema(),
                            allDdlTriggerName);
                }
                if (!filteredDdlTriggerExists) {
                    getTargetDialect().createDdlTrigger(tablePrefix, sqlBuffer, filteredDdlTriggerName, platform.getDefaultCatalog(), platform
                            .getDefaultSchema());
                }
            } else {
                if (!allDdlTriggerExists) {
                    getTargetDialect().createDdlTrigger(tablePrefix, sqlBuffer, allDdlTriggerName, platform.getDefaultCatalog(), platform.getDefaultSchema());
                }
                if (filteredDdlTriggerExists) {
                    getTargetDialect().removeDdlTrigger(sqlBuffer, getTargetPlatform().getDefaultCatalog(), getTargetPlatform().getDefaultSchema(),
                            filteredDdlTriggerName);
                }
            }
        } else {
            if (allDdlTriggerExists) {
                getTargetDialect().removeDdlTrigger(sqlBuffer, platform.getDefaultCatalog(), platform.getDefaultSchema(), allDdlTriggerName);
            }
            if (filteredDdlTriggerExists) {
                getTargetDialect().removeDdlTrigger(sqlBuffer, platform.getDefaultCatalog(), platform.getDefaultSchema(), filteredDdlTriggerName);
            }
        }
    }

    protected void updateOrCreateDatabaseTriggers(final List<Trigger> triggers, final StringBuilder sqlBuffer,
            final boolean force, final boolean verifyInDatabase, final List<TriggerHistory> activeTriggerHistories,
            final boolean useTableCache, TriggerRouterContext triggerRouterContext) {
        Map<String, List<TriggerTableSupportingInfo>> triggerToTableSupportingInfo = getTriggerToTableSupportingInfo(triggers, activeTriggerHistories,
                useTableCache, triggerRouterContext);
        int numThreads = getNumberOfThreadsToUseForSyncTriggers();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads, new SyncTriggersThreadFactory());
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (final Trigger trigger : triggers) {
            Runnable task = new Runnable() {
                public void run() {
                    MDC.put("engineName", parameterService.getEngineName());
                    updateOrCreateDatabaseTrigger(trigger, triggers, sqlBuffer, force, verifyInDatabase, activeTriggerHistories, useTableCache,
                            triggerToTableSupportingInfo, triggerRouterContext);
                }
            };
            futures.add(executor.submit(task));
        }
        awaitTermination(executor, futures);
    }

    private Map<String, List<TriggerTableSupportingInfo>> getTriggerToTableSupportingInfo(List<Trigger> triggers, List<TriggerHistory> activeTriggerHistories,
            boolean useTableCache, TriggerRouterContext triggerRouterContext) {
        Map<String, List<TriggerTableSupportingInfo>> triggerToTableSupportingInfo = new HashMap<String, List<TriggerTableSupportingInfo>>();
        List<String> triggerNamesGeneratedThisSession = new ArrayList<String>();
        for (final Trigger trigger : triggers) {
            List<TriggerTableSupportingInfo> triggerTableSupportingInfoList = new ArrayList<TriggerTableSupportingInfo>();
            Set<Table> tables = getTablesForTrigger(trigger, triggers, useTableCache, triggerRouterContext);
            long ts = System.currentTimeMillis();
            for (Table table : tables) {
                Table modifiedTable = table;
                boolean foundPk = false;
                Column[] columns = trigger.filterExcludedAndIncludedColumns(modifiedTable.getColumns());
                for (Column column : columns) {
                    foundPk |= column.isPrimaryKey();
                    if (foundPk) {
                        break;
                    }
                }
                if (!foundPk) {
                    modifiedTable = platform.makeAllColumnsPrimaryKeys(modifiedTable);
                }
                TriggerHistory latestHistoryBeforeRebuild;
                synchronized (activeTriggerHistories) {
                    latestHistoryBeforeRebuild = getNewestTriggerHistoryForTrigger(
                            activeTriggerHistories,
                            trigger.getTriggerId(),
                            trigger.isSourceCatalogNameWildCarded() ? modifiedTable.getCatalog() : trigger.getSourceCatalogNameUnescaped(),
                            trigger.isSourceSchemaNameWildCarded() ? modifiedTable.getSchema() : trigger.getSourceSchemaNameUnescaped(),
                            (trigger.isSourceTableNameWildCarded() || trigger.isSourceTableNameExpanded()) ? modifiedTable.getName()
                                    : trigger.getSourceTableNameUnescaped());
                }
                int maxTriggerNameLength = symmetricDialect.getMaxTriggerNameLength();
                String insertTriggerName = null;
                String updateTriggerName = null;
                String deleteTriggerName = null;
                if (trigger.isSyncOnInsert()) {
                    insertTriggerName = getTriggerName(DataEventType.INSERT,
                            maxTriggerNameLength, trigger, modifiedTable, activeTriggerHistories, latestHistoryBeforeRebuild, triggerNamesGeneratedThisSession)
                            .toUpperCase();
                    triggerNamesGeneratedThisSession.add(insertTriggerName);
                }
                if (trigger.isSyncOnUpdate()) {
                    updateTriggerName = getTriggerName(DataEventType.UPDATE,
                            maxTriggerNameLength, trigger, modifiedTable, activeTriggerHistories, latestHistoryBeforeRebuild, triggerNamesGeneratedThisSession)
                            .toUpperCase();
                    triggerNamesGeneratedThisSession.add(updateTriggerName);
                }
                if (trigger.isSyncOnDelete()) {
                    deleteTriggerName = getTriggerName(DataEventType.DELETE,
                            maxTriggerNameLength, trigger, modifiedTable, activeTriggerHistories, latestHistoryBeforeRebuild, triggerNamesGeneratedThisSession)
                            .toUpperCase();
                    triggerNamesGeneratedThisSession.add(deleteTriggerName);
                }
                TriggerTableSupportingInfo triggerTableSupportingInfo = new TriggerTableSupportingInfo(trigger.getTriggerId(), insertTriggerName,
                        updateTriggerName, deleteTriggerName, latestHistoryBeforeRebuild, modifiedTable);
                triggerTableSupportingInfoList.add(triggerTableSupportingInfo);
            }
            triggerToTableSupportingInfo.put(trigger.getTriggerId(), triggerTableSupportingInfoList);
            triggerRouterContext.incrementTriggerToTableSupportingInfoTime(System.currentTimeMillis() - ts);
        }
        return triggerToTableSupportingInfo;
    }

    class TriggerTableSupportingInfo {
        private String triggerId;
        private String insertTriggerName;
        private String updateTriggerName;
        private String deleteTriggerName;
        private TriggerHistory latestHistoryBeforeRebuild;
        private Table table;

        public TriggerTableSupportingInfo(String triggerId, String insertTriggerName, String updaterTriggerName, String deleteTriggerName,
                TriggerHistory latestHistoryBeforeRebuild, Table table) {
            this.triggerId = triggerId;
            this.insertTriggerName = insertTriggerName;
            this.updateTriggerName = updaterTriggerName;
            this.deleteTriggerName = deleteTriggerName;
            this.latestHistoryBeforeRebuild = latestHistoryBeforeRebuild;
            this.table = table;
        }

        public String getInsertTriggerName() {
            return insertTriggerName;
        }

        public void setInsertTriggerName(String insertTriggerName) {
            this.insertTriggerName = insertTriggerName;
        }

        public String getUpdateTriggerName() {
            return updateTriggerName;
        }

        public void setUpdateTriggerName(String updateTriggerName) {
            this.updateTriggerName = updateTriggerName;
        }

        public String getDeleteTriggerName() {
            return deleteTriggerName;
        }

        public void setDeleteTriggerName(String deleteTriggerName) {
            this.deleteTriggerName = deleteTriggerName;
        }

        public TriggerHistory getLatestHistoryBeforeRebuild() {
            return latestHistoryBeforeRebuild;
        }

        public void setLatestHistoryBeforeRebuild(TriggerHistory latestHistoryBeforeRebuild) {
            this.latestHistoryBeforeRebuild = latestHistoryBeforeRebuild;
        }

        public Table getTable() {
            return table;
        }

        public void setTable(Table table) {
            this.table = table;
        }

        public String getTriggerId() {
            return triggerId;
        }

        public void setTriggerId(String triggerId) {
            this.triggerId = triggerId;
        }
    }

    protected void updateOrCreateDatabaseTrigger(Trigger trigger, List<Trigger> triggers, StringBuilder sqlBuffer, boolean force, boolean verifyInDatabase,
            List<TriggerHistory> activeTriggerHistories, boolean useTableCache, Map<String, List<TriggerTableSupportingInfo>> triggerToTableSupportingInfo,
            TriggerRouterContext context) {
        List<TriggerTableSupportingInfo> triggerTableSupportingInfoList = triggerToTableSupportingInfo.get(trigger.getTriggerId());
        if (triggerTableSupportingInfoList != null && triggerTableSupportingInfoList.size() > 0) {
            for (TriggerTableSupportingInfo triggerTableSupportingInfo : triggerTableSupportingInfoList) {
                long ts = System.currentTimeMillis();
                updateOrCreateDatabaseTriggers(trigger, triggerTableSupportingInfo.getTable(), sqlBuffer, force, verifyInDatabase, activeTriggerHistories,
                        triggerTableSupportingInfo, context);
                context.incrementUpdateOrCreateDatabaseTriggersTime(System.currentTimeMillis() - ts);
            }
        } else {
            handleTableNotFound(trigger, activeTriggerHistories, context);
        }
    }

    public void syncTrigger(Trigger trigger, ITriggerCreationListener listener, boolean force) {
        syncTrigger(trigger, listener, force, true);
    }

    public void syncTrigger(Trigger trigger, ITriggerCreationListener listener, boolean force, boolean verifyInDatabase) {
        syncTriggers(Collections.singletonList(trigger), listener, force, verifyInDatabase);
    }

    public boolean syncTriggers(List<Trigger> triggers, ITriggerCreationListener listener, boolean force, boolean verifyInDatabase) {
        if (clusterService.lock(ClusterConstants.SYNC_TRIGGERS)) {
            TriggerRouterContext context = new TriggerRouterContext();
            long startTime = System.currentTimeMillis();
            try {
                long ts;
                fixMultipleActiveTriggerHistories(context);
                clearCache();
                List<Trigger> triggersForCurrentNode = null;
                if (verifyInDatabase) {
                    ts = System.currentTimeMillis();
                    triggersForCurrentNode = getTriggersForCurrentNode();
                    context.incrementTriggersForCurrentNodeTime(System.currentTimeMillis() - ts);
                } else {
                    triggersForCurrentNode = new ArrayList<Trigger>();
                    triggersForCurrentNode.addAll(triggers);
                }
                try {
                    if (listener != null) {
                        extensionService.addExtensionPoint(listener);
                    }
                    log.info("Synchronizing {} triggers", triggers.size());
                    context.incrementTriggersToSyncCount(triggers.size());
                    ts = System.currentTimeMillis();
                    for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                        l.syncTriggersStarted();
                    }
                    context.incrementSyncTriggersStartedTime(System.currentTimeMillis() - ts);
                    ts = System.currentTimeMillis();
                    List<TriggerHistory> allHistories = getActiveTriggerHistories();
                    context.incrementActiveTriggerHistoriesTime(System.currentTimeMillis() - ts);
                    Map<String, List<TriggerHistory>> activeHistoryByTriggerId = new HashMap<String, List<TriggerHistory>>();
                    for (TriggerHistory hist : allHistories) {
                        List<TriggerHistory> list = activeHistoryByTriggerId.get(hist.getTriggerId());
                        if (list == null) {
                            list = new ArrayList<TriggerHistory>();
                            activeHistoryByTriggerId.put(hist.getTriggerId(), list);
                        }
                        list.add(hist);
                    }
                    for (Trigger trigger : triggers) {
                        if (triggersForCurrentNode.contains(trigger)) {
                            if (!trigger.isSourceTableNameWildCarded() && !trigger.isSourceTableNameExpanded()) {
                                List<TriggerHistory> activeHistories = activeHistoryByTriggerId.get(trigger.getTriggerId());
                                if (activeHistories != null) {
                                    for (TriggerHistory triggerHistory : activeHistories) {
                                        if (!triggerHistory.getFullyQualifiedSourceTableName().equals(trigger.getFullyQualifiedSourceTableName())) {
                                            dropTriggers(triggerHistory, null, context);
                                        }
                                    }
                                }
                            } else if (trigger.isSourceTableNameWildCarded()) {
                                Set<Table> tables = getTablesForTrigger(trigger, triggers, verifyInDatabase, context);
                                Set<String> fullyQualifiedTableNames = new HashSet<String>();
                                for (Table table : tables) {
                                    fullyQualifiedTableNames.add(table.getFullyQualifiedTableName());
                                }
                                List<TriggerHistory> activeHistories = activeHistoryByTriggerId.get(trigger.getTriggerId());
                                if (activeHistories != null) {
                                    for (TriggerHistory triggerHistory : activeHistories) {
                                        if (!fullyQualifiedTableNames.contains(triggerHistory.getFullyQualifiedSourceTableName())) {
                                            dropTriggers(triggerHistory, null, context);
                                        }
                                    }
                                }
                            }
                            Map<String, List<TriggerTableSupportingInfo>> triggerToTableSupportingInfo = getTriggerToTableSupportingInfo(
                                    Collections.singletonList(trigger), allHistories, true, context);
                            updateOrCreateDatabaseTrigger(trigger, triggersForCurrentNode, null, force,
                                    verifyInDatabase, allHistories, false, triggerToTableSupportingInfo, context);
                        } else {
                            List<TriggerHistory> activeHistories = activeHistoryByTriggerId.get(trigger.getTriggerId());
                            if (activeHistories != null) {
                                for (TriggerHistory triggerHistory : activeHistories) {
                                    dropTriggers(triggerHistory, null, context);
                                }
                            }
                        }
                    }
                    statisticManager.addJobStats(ClusterConstants.SYNC_TRIGGERS, startTime, System.currentTimeMillis(), context.getTriggersSyncedCount(),
                            context.getErrorMessage());
                } finally {
                    ts = System.currentTimeMillis();
                    for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                        l.syncTriggersEnded();
                    }
                    context.incrementSyncTriggersEndedTime(System.currentTimeMillis() - ts);
                    if (listener != null) {
                        extensionService.removeExtensionPoint(listener);
                    }
                    logTriggerRouterContextTimings(context);
                    logTriggerRouterContextAnomalies(context);
                    log.info("Done synchronizing {} triggers", triggers.size());
                }
                return true;
            } catch (RuntimeException e) {
                statisticManager.addJobStats(ClusterConstants.SYNC_TRIGGERS, startTime, System.currentTimeMillis(), context.getTriggersSyncedCount(), e);
                throw e;
            } finally {
                clusterService.unlock(ClusterConstants.SYNC_TRIGGERS);
            }
        }
        return false;
    }

    protected void updateOrCreateDatabaseTriggers(Trigger trigger, Table table,
            StringBuilder sqlBuffer, boolean force, boolean verifyInDatabase, List<TriggerHistory> activeTriggerHistories,
            TriggerTableSupportingInfo triggerTableSupportingInfo, TriggerRouterContext context) {
        TriggerHistory latestHistoryBeforeRebuild = triggerTableSupportingInfo.getLatestHistoryBeforeRebuild();
        TriggerHistory newestHistory = null;
        TriggerReBuildReason reason = TriggerReBuildReason.NEW_TRIGGERS;
        if (verifyInDatabase) {
            Channel channel = configurationService.getChannel(trigger.getChannelId());
            if (channel == null) {
                log.warn("Trigger '{}' has a channel of '{}' not found in sym_channel table", trigger.getTriggerId(), trigger.getChannelId());
            }
        }
        trigger.setSyncOnIncomingBatch(trigger.isSyncOnIncomingBatch() && !configurationService.isMasterToMasterOnly());
        try {
            boolean forceRebuildOfTriggers = false;
            if (latestHistoryBeforeRebuild == null) {
                reason = TriggerReBuildReason.NEW_TRIGGERS;
                forceRebuildOfTriggers = true;
            } else if (table.calculateTableHashcode() != latestHistoryBeforeRebuild.getTableHash()) {
                reason = TriggerReBuildReason.TABLE_SCHEMA_CHANGED;
                forceRebuildOfTriggers = true;
            } else if (trigger.hasChangedSinceLastTriggerBuild(latestHistoryBeforeRebuild
                    .getCreateTime())
                    || trigger.toHashedValue() != latestHistoryBeforeRebuild.getTriggerRowHash()) {
                reason = TriggerReBuildReason.TABLE_SYNC_CONFIGURATION_CHANGED;
                forceRebuildOfTriggers = true;
            } else if (symmetricDialect.getTriggerTemplate().toHashedValue() != latestHistoryBeforeRebuild.getTriggerTemplateHash()) {
                reason = TriggerReBuildReason.TRIGGER_TEMPLATE_CHANGED;
                forceRebuildOfTriggers = true;
            } else if (force) {
                reason = TriggerReBuildReason.FORCED;
                forceRebuildOfTriggers = true;
            }
            boolean supportsTriggers = symmetricDialect.getPlatform().getDatabaseInfo()
                    .isTriggersSupported();
            newestHistory = rebuildTriggerIfNecessary(sqlBuffer, forceRebuildOfTriggers, trigger,
                    DataEventType.INSERT, reason, latestHistoryBeforeRebuild, null,
                    trigger.isSyncOnInsert() && supportsTriggers, table, activeTriggerHistories, triggerTableSupportingInfo);
            newestHistory = rebuildTriggerIfNecessary(sqlBuffer, forceRebuildOfTriggers, trigger,
                    DataEventType.UPDATE, reason, latestHistoryBeforeRebuild, newestHistory,
                    trigger.isSyncOnUpdate() && supportsTriggers, table, activeTriggerHistories, triggerTableSupportingInfo);
            newestHistory = rebuildTriggerIfNecessary(sqlBuffer, forceRebuildOfTriggers, trigger,
                    DataEventType.DELETE, reason, latestHistoryBeforeRebuild, newestHistory,
                    trigger.isSyncOnDelete() && supportsTriggers, table, activeTriggerHistories, triggerTableSupportingInfo);
            if (latestHistoryBeforeRebuild != null && newestHistory != null) {
                inactivateTriggerHistory(latestHistoryBeforeRebuild);
            }
            context.incrementTriggersSyncedCount(1);
            if (newestHistory != null) {
                if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)) {
                    for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                        l.triggerCreated(context.getTriggersToSyncCount(), context.getTriggersSyncedCount(), trigger, newestHistory);
                    }
                }
                synchronized (activeTriggerHistories) {
                    activeTriggerHistories.add(newestHistory);
                }
            } else {
                for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                    l.triggerChecked(context.getTriggersToSyncCount(), context.getTriggersSyncedCount());
                }
            }
        } catch (Exception ex) {
            log.error(String.format("Failed to create triggers for %s", trigger.qualifiedSourceTableName()), ex);
            invalidateTriggerHistory(newestHistory, ex);
            boolean usingTargetDialect = (!getSymmetricDialect().equals(getTargetDialect()) && !trigger.getSourceTableName().startsWith(getSymmetricDialect()
                    .getTablePrefix()));
            if (newestHistory != null && !usingTargetDialect) {
                dropTriggers(newestHistory, sqlBuffer);
            }
            context.incrementTriggersSyncedCount(1);
            context.addTriggersFailed(trigger.qualifiedSourceTableName(), ex);
            for (ITriggerCreationListener l : extensionService.getExtensionPointList(ITriggerCreationListener.class)) {
                l.triggerFailed(context.getTriggersToSyncCount(), context.getTriggersSyncedCount(), trigger, ex);
            }
        }
    }

    protected TriggerHistory rebuildTriggerIfNecessary(StringBuilder sqlBuffer,
            boolean forceRebuild, Trigger trigger, DataEventType dmlType,
            TriggerReBuildReason reason, TriggerHistory oldhist, TriggerHistory hist,
            boolean triggerIsActive, Table table, List<TriggerHistory> activeTriggerHistories,
            TriggerTableSupportingInfo triggerTableSupportingInfo) {
        try {
            return rebuildTriggerIfNecessaryWithException(sqlBuffer, forceRebuild, trigger, dmlType, reason, oldhist, hist, triggerIsActive, table,
                    activeTriggerHistories, triggerTableSupportingInfo);
        } catch (Exception e) {
            SQLException se = ExceptionUtils.unwrapSqlException(e);
            IDatabasePlatform platform = symmetricDialect.getTargetPlatform(table.getName());
            if (se != null && se.getMessage() != null && platform.getName().startsWith(DatabaseNamesConstants.MSSQL) && se.getErrorCode() == 207) {
                log.error("Retrying trigger for {} after exception {} [{}]: {}", table.getName(), e.getClass().getName(), se.getErrorCode(), e.getMessage());
                table = platform.readTableFromDatabase(table.getCatalog(), table.getSchema(), table.getName());
                if (table != null) {
                    return rebuildTriggerIfNecessaryWithException(sqlBuffer, forceRebuild, trigger, dmlType, reason, oldhist, hist, triggerIsActive, table,
                            activeTriggerHistories, triggerTableSupportingInfo);
                }
            } else if (platform.getSqlTemplate().isDeadlock(e)) {
                log.error("Retrying trigger for {} after deadlock {} [{}]: {}", table.getName(), e.getClass().getName(), se.getErrorCode(), e.getMessage());
                return rebuildTriggerIfNecessaryWithException(sqlBuffer, forceRebuild, trigger, dmlType, reason, oldhist, hist, triggerIsActive, table,
                        activeTriggerHistories, triggerTableSupportingInfo);
            }
            throw e;
        }
    }

    protected TriggerHistory rebuildTriggerIfNecessaryWithException(StringBuilder sqlBuffer,
            boolean forceRebuild, Trigger trigger, DataEventType dmlType,
            TriggerReBuildReason reason, TriggerHistory oldhist, TriggerHistory hist,
            boolean triggerIsActive, Table table, List<TriggerHistory> activeTriggerHistories,
            TriggerTableSupportingInfo triggerTableSupportingInfo) {
        boolean triggerExists = false;
        boolean triggerRemoved = false;
        boolean usingTargetDialect = false;
        if (!getSymmetricDialect().equals(getTargetDialect()) &&
                !trigger.getSourceTableName().startsWith(getSymmetricDialect().getTablePrefix())) {
            usingTargetDialect = true;
        }
        TriggerHistory newTriggerHist = new TriggerHistory(table, trigger,
                usingTargetDialect ? getTargetDialect().getTriggerTemplate() : getSymmetricDialect().getTriggerTemplate(), reason);
        if (usingTargetDialect) {
            if (hist == null && (oldhist == null || forceRebuild)) {
                insert(newTriggerHist);
                synchronized (activeTriggerHistories) {
                    activeTriggerHistories.add(newTriggerHist);
                    historyMap.put(newTriggerHist.getTriggerHistoryId(), newTriggerHist);
                    hist = getNewestTriggerHistoryForTrigger(activeTriggerHistories, trigger.getTriggerId(),
                            trigger.isSourceCatalogNameWildCarded() ? table.getCatalog() : trigger.getSourceCatalogNameUnescaped(),
                            trigger.isSourceSchemaNameWildCarded() ? table.getSchema() : trigger.getSourceSchemaNameUnescaped(),
                            (trigger.isSourceTableNameWildCarded() || trigger.isSourceTableNameExpanded()) ? table.getName()
                                    : trigger.getSourceTableNameUnescaped());
                }
            }
            if (hist == null) {
                hist = historyMap.get(newTriggerHist.getTriggerHistoryId());
            }
            return hist;
        }
        if (trigger.isSyncOnInsert()) {
            newTriggerHist.setNameForInsertTrigger(triggerTableSupportingInfo.getInsertTriggerName());
        }
        if (trigger.isSyncOnUpdate()) {
            newTriggerHist.setNameForUpdateTrigger(triggerTableSupportingInfo.getUpdateTriggerName());
        }
        if (trigger.isSyncOnDelete()) {
            newTriggerHist.setNameForDeleteTrigger(triggerTableSupportingInfo.getDeleteTriggerName());
        }
        String oldTriggerName = null;
        String oldSourceSchema = null;
        String oldCatalogName = null;
        if (oldhist != null) {
            oldTriggerName = oldhist.getTriggerNameForDmlType(dmlType);
            if (oldTriggerName == null) {
                oldTriggerName = getTriggerName(dmlType, triggerTableSupportingInfo);
            }
            oldSourceSchema = oldhist.getSourceSchemaName();
            oldCatalogName = oldhist.getSourceCatalogName();
            triggerExists = symmetricDialect.doesTriggerExist(sqlBuffer, oldCatalogName,
                    oldSourceSchema, oldhist.getSourceTableName(), oldTriggerName);
        } else {
            // We had no trigger_hist row, lets validate that the trigger as
            // defined in the trigger row data does not exist as well.
            oldTriggerName = newTriggerHist.getTriggerNameForDmlType(dmlType);
            oldSourceSchema = table.getSchema();
            oldCatalogName = table.getCatalog();
            if (StringUtils.isNotBlank(oldTriggerName)) {
                triggerExists = symmetricDialect.doesTriggerExist(sqlBuffer, oldCatalogName,
                        oldSourceSchema, table.getName(), oldTriggerName);
            }
        }
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
            if ((forceRebuild || !triggerIsActive) && triggerExists) {
                if (!triggerIsActive || !platform.getDatabaseInfo().isTriggersCreateOrReplaceSupported() ||
                        !oldTriggerName.equalsIgnoreCase(newTriggerHist.getTriggerNameForDmlType(dmlType))) {
                    symmetricDialect.removeTrigger(sqlBuffer, oldCatalogName, oldSourceSchema,
                            oldTriggerName, trigger.isSourceTableNameWildCarded() || trigger.isSourceTableNameExpanded() ? table.getName()
                                    : trigger.getSourceTableNameUnescaped(), transaction);
                    triggerRemoved = true;
                }
                triggerExists = false;
            }
            boolean isDeadTrigger = !trigger.isSyncOnInsert() && !trigger.isSyncOnUpdate() && !trigger.isSyncOnDelete();
            if (hist == null && (oldhist == null || (!triggerExists && triggerIsActive) || (isDeadTrigger && forceRebuild))) {
                insert(newTriggerHist);
                synchronized (activeTriggerHistories) {
                    for (Iterator<TriggerHistory> it = activeTriggerHistories.iterator(); it.hasNext();) {
                        TriggerHistory triggerHistory = it.next();
                        if (StringUtils.equals(triggerHistory.getSourceCatalogName(), newTriggerHist.getSourceCatalogName())) {
                            if (StringUtils.equals(triggerHistory.getSourceSchemaName(), newTriggerHist.getSourceSchemaName())) {
                                if (StringUtils.equals(triggerHistory.getSourceTableName(), newTriggerHist.getSourceTableName())) {
                                    it.remove();
                                }
                            }
                        }
                    }
                    activeTriggerHistories.add(newTriggerHist);
                    historyMap.put(newTriggerHist.getTriggerHistoryId(), newTriggerHist);
                    hist = getNewestTriggerHistoryForTrigger(activeTriggerHistories, trigger.getTriggerId(),
                            trigger.isSourceCatalogNameWildCarded() ? table.getCatalog() : trigger.getSourceCatalogNameUnescaped(),
                            trigger.isSourceSchemaNameWildCarded() ? table.getSchema() : trigger.getSourceSchemaNameUnescaped(),
                            trigger.isSourceTableNameWildCarded() || trigger.isSourceTableNameExpanded() ? table.getName()
                                    : trigger.getSourceTableNameUnescaped());
                }
            }
            try {
                if (!triggerExists && triggerIsActive) {
                    symmetricDialect
                            .createTrigger(sqlBuffer, dmlType, trigger, hist,
                                    configurationService.getChannel(trigger.getChannelId()),
                                    tablePrefix, table, transaction);
                    if (triggerRemoved) {
                        statisticManager.incrementTriggersRebuiltCount(1);
                    } else {
                        statisticManager.incrementTriggersCreatedCount(1);
                    }
                } else if (triggerRemoved) {
                    statisticManager.incrementTriggersRemovedCount(1);
                }
                transaction.commit();
            } catch (RuntimeException ex) {
                invalidateTriggerHistory(hist, ex);
                throw ex;
            }
        } catch (Throwable ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
        return hist;
    }

    public String getTriggerName(DataEventType dml, int maxTriggerNameLength, Trigger trigger,
            Table table, List<TriggerHistory> activeTriggerHistories, TriggerHistory oldhist, List<String> triggerNamesGeneratedThisSession) {
        String triggerName = null;
        switch (dml) {
            case INSERT:
                if (!StringUtils.isBlank(trigger.getNameForInsertTrigger())) {
                    triggerName = trigger.getNameForInsertTrigger();
                }
                break;
            case UPDATE:
                if (!StringUtils.isBlank(trigger.getNameForUpdateTrigger())) {
                    triggerName = trigger.getNameForUpdateTrigger();
                }
                break;
            case DELETE:
                if (!StringUtils.isBlank(trigger.getNameForDeleteTrigger())) {
                    triggerName = trigger.getNameForDeleteTrigger();
                }
                break;
            default:
                break;
        }
        if (StringUtils.isBlank(triggerName)) {
            String triggerPrefix = parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TRIGGER_PREFIX, tablePrefix);
            String triggerPrefix1 = triggerPrefix + "_";
            String triggerSuffix1 = "on_" + dml.getCode().toLowerCase() + "_for_";
            String triggerSuffix2 = FormatUtils.replaceCharsToShortenName(trigger.getTriggerId());
            if (trigger.isSourceTableNameWildCarded()) {
                triggerSuffix2 = FormatUtils.replaceCharsToShortenName(table.getName());
            } else if (trigger.isSourceTableNameExpanded()) {
                triggerSuffix2 = FormatUtils.replaceCharsToShortenName(table.getName());
            }
            String triggerSuffix3 = FormatUtils.replaceCharsToShortenName("_"
                    + parameterService.getNodeGroupId());
            triggerName = triggerPrefix1 + triggerSuffix1 + triggerSuffix2 + triggerSuffix3;
            // use the node group id as part of the trigger if we can because it
            // helps uniquely identify the trigger in embedded databases. In
            // hsqldb we choose the
            // correct connection based on the presence of a table that is named
            // for the trigger.
            // If the trigger isn't unique across all databases, then we can
            // choose the wrong connection.
            if (triggerName.length() > maxTriggerNameLength && maxTriggerNameLength > 0) {
                triggerName = triggerPrefix1 + triggerSuffix1 + triggerSuffix2;
            }
        }
        triggerName = triggerName.toUpperCase();
        if (triggerName.length() > maxTriggerNameLength && maxTriggerNameLength > 0) {
            triggerName = triggerName.substring(0, maxTriggerNameLength - 1);
            log.debug(
                    "We just truncated the trigger name for the {} trigger id={}.  You might want to consider manually providing a name for the trigger that is less than {} characters long",
                    new Object[] { dml.name().toLowerCase(), trigger.getTriggerId(),
                            maxTriggerNameLength });
        }
        int duplicateCount = 0;
        while (isTriggerNameInUse(activeTriggerHistories, trigger, triggerName, oldhist, triggerNamesGeneratedThisSession)) {
            duplicateCount++;
            String duplicateSuffix = Integer.toString(duplicateCount);
            if (triggerName.length() + duplicateSuffix.length() > maxTriggerNameLength) {
                triggerName = triggerName.substring(0,
                        triggerName.length() - duplicateSuffix.length())
                        + duplicateSuffix;
            } else {
                triggerName = triggerName + duplicateSuffix;
            }
        }
        return triggerName;
    }

    public String getTriggerName(DataEventType dml, TriggerTableSupportingInfo triggerTableSupportingInfo) {
        String triggerName = null;
        switch (dml) {
            case INSERT:
                triggerName = triggerTableSupportingInfo.getInsertTriggerName();
                break;
            case UPDATE:
                triggerName = triggerTableSupportingInfo.getUpdateTriggerName();
                break;
            case DELETE:
                triggerName = triggerTableSupportingInfo.getDeleteTriggerName();
                break;
            default:
                break;
        }
        return triggerName;
    }

    static class TriggerHistoryMapper implements ISqlRowMapper<TriggerHistory> {
        IParameterService parameterService;
        Map<Long, TriggerHistory> retMap = null;

        TriggerHistoryMapper() {
        }

        TriggerHistoryMapper(Map<Long, TriggerHistory> map) {
            this.retMap = map;
        }

        public TriggerHistory mapRow(Row rs) {
            TriggerHistory hist = new TriggerHistory();
            if (rs.containsKey("trigger_hist_id")) {
                hist.setTriggerHistoryId(rs.getInt("trigger_hist_id"));
            }
            if (rs.containsKey("trigger_id")) {
                hist.setTriggerId(rs.getString("trigger_id"));
            }
            if (rs.containsKey("source_table_name")) {
                hist.setSourceTableName(rs.getString("source_table_name"));
            }
            if (rs.containsKey("table_hash")) {
                hist.setTableHash(rs.getInt("table_hash"));
            }
            if (rs.containsKey("create_time")) {
                hist.setCreateTime(rs.getDateTime("create_time"));
            }
            if (rs.containsKey("pk_column_names")) {
                hist.setPkColumnNames(rs.getString("pk_column_names"));
            }
            if (rs.containsKey("column_names")) {
                hist.setColumnNames(rs.getString("column_names"));
            }
            if (rs.containsKey("is_missing_pk")) {
                hist.setIsMissingPk(rs.getBoolean("is_missing_pk"));
            }
            if (rs.containsKey("last_trigger_build_reason")) {
                hist.setLastTriggerBuildReason(TriggerReBuildReason.fromCode(rs
                        .getString("last_trigger_build_reason")));
            }
            if (rs.containsKey("name_for_delete_trigger")) {
                hist.setNameForDeleteTrigger(rs.getString("name_for_delete_trigger"));
            }
            if (rs.containsKey("name_for_insert_trigger")) {
                hist.setNameForInsertTrigger(rs.getString("name_for_insert_trigger"));
            }
            if (rs.containsKey("name_for_update_trigger")) {
                hist.setNameForUpdateTrigger(rs.getString("name_for_update_trigger"));
            }
            if (rs.containsKey("source_schema_name")) {
                hist.setSourceSchemaName(rs.getString("source_schema_name"));
            }
            if (rs.containsKey("source_catalog_name")) {
                hist.setSourceCatalogName(rs.getString("source_catalog_name"));
            }
            if (rs.containsKey("trigger_row_hash")) {
                hist.setTriggerRowHash(rs.getLong("trigger_row_hash"));
            }
            if (rs.containsKey("trigger_template_hash")) {
                hist.setTriggerTemplateHash(rs.getLong("trigger_template_hash"));
            }
            if (rs.containsKey("error_message")) {
                hist.setErrorMessage(rs.getString("error_message"));
            }
            if (this.retMap != null) {
                this.retMap.put((long) hist.getTriggerHistoryId(), hist);
            }
            return hist;
        }
    }

    static class LastTriggerHistoryMapper extends TriggerHistoryMapper {
        Map<String, TriggerHistory> map = null;

        LastTriggerHistoryMapper(Map<String, TriggerHistory> map) {
            this.map = map;
        }

        public TriggerHistory mapRow(Row rs) {
            TriggerHistory hist = super.mapRow(rs);
            map.put(hist.getTriggerId(), hist);
            return null;
        }
    }

    static class RouterMapper implements ISqlRowMapper<Router> {
        List<NodeGroupLink> nodeGroupLinks;

        public RouterMapper(List<NodeGroupLink> nodeGroupLinks) {
            this.nodeGroupLinks = nodeGroupLinks;
        }

        private NodeGroupLink getNodeGroupLink(String sourceNodeGroupId, String targetNodeGroupId) {
            for (NodeGroupLink nodeGroupLink : nodeGroupLinks) {
                if (nodeGroupLink.getSourceNodeGroupId().equals(sourceNodeGroupId) &&
                        nodeGroupLink.getTargetNodeGroupId().equals(targetNodeGroupId)) {
                    return nodeGroupLink;
                }
            }
            return null;
        }

        public Router mapRow(Row rs) {
            Router router = new Router();
            router.setSyncOnInsert(rs.getBoolean("r_sync_on_insert"));
            router.setSyncOnUpdate(rs.getBoolean("r_sync_on_update"));
            router.setSyncOnDelete(rs.getBoolean("r_sync_on_delete"));
            router.setTargetCatalogName(StringUtils.trimToNull(rs.getString("target_catalog_name")));
            router.setNodeGroupLink(getNodeGroupLink(
                    rs.getString("source_node_group_id"), rs.getString("target_node_group_id")));
            router.setTargetSchemaName(StringUtils.trimToNull(rs.getString("target_schema_name")));
            router.setTargetTableName(StringUtils.trimToNull(rs.getString("target_table_name")));
            String condition = StringUtils.trimToNull(rs.getString("router_expression"));
            if (!StringUtils.isBlank(condition)) {
                router.setRouterExpression(condition);
            }
            router.setRouterType(rs.getString("router_type"));
            router.setRouterId(rs.getString("router_id"));
            router.setUseSourceCatalogSchema(rs.getBoolean("use_source_catalog_schema"));
            router.setCreateTime(rs.getDateTime("r_create_time"));
            router.setLastUpdateTime(rs.getDateTime("r_last_update_time"));
            router.setLastUpdateBy(rs.getString("r_last_update_by"));
            return router;
        }
    }

    static class TriggerMapper implements ISqlRowMapper<Trigger> {
        public Trigger mapRow(Row rs) {
            Trigger trigger = new Trigger();
            trigger.setTriggerId(rs.getString("trigger_id"));
            trigger.setChannelId(rs.getString("channel_id"));
            trigger.setReloadChannelId(rs.getString("reload_channel_id"));
            trigger.setSourceTableName(rs.getString("source_table_name"));
            trigger.setSyncOnInsert(rs.getBoolean("sync_on_insert"));
            trigger.setSyncOnUpdate(rs.getBoolean("sync_on_update"));
            trigger.setSyncOnDelete(rs.getBoolean("sync_on_delete"));
            trigger.setSyncOnIncomingBatch(rs.getBoolean("sync_on_incoming_batch"));
            trigger.setUseStreamLobs(rs.getBoolean("use_stream_lobs"));
            trigger.setUseCaptureLobs(rs.getBoolean("use_capture_lobs"));
            trigger.setUseCaptureOldData(rs.getBoolean("use_capture_old_data"));
            trigger.setUseHandleKeyUpdates(rs.getBoolean("use_handle_key_updates"));
            trigger.setNameForDeleteTrigger(rs.getString("name_for_delete_trigger"));
            trigger.setNameForInsertTrigger(rs.getString("name_for_insert_trigger"));
            trigger.setNameForUpdateTrigger(rs.getString("name_for_update_trigger"));
            trigger.setStreamRow(rs.getBoolean("stream_row"));
            String schema = rs.getString("source_schema_name");
            trigger.setSourceSchemaName(schema);
            String catalog = rs.getString("source_catalog_name");
            trigger.setSourceCatalogName(catalog);
            String condition = rs.getString("sync_on_insert_condition");
            if (!StringUtils.isBlank(condition)) {
                trigger.setSyncOnInsertCondition(condition);
            }
            condition = rs.getString("sync_on_update_condition");
            if (!StringUtils.isBlank(condition)) {
                trigger.setSyncOnUpdateCondition(condition);
            }
            condition = rs.getString("sync_on_delete_condition");
            if (!StringUtils.isBlank(condition)) {
                trigger.setSyncOnDeleteCondition(condition);
            }
            String text = rs.getString("custom_on_insert_text");
            if (!StringUtils.isBlank(text)) {
                trigger.setCustomOnInsertText(text);
            }
            text = rs.getString("custom_on_update_text");
            if (!StringUtils.isBlank(text)) {
                trigger.setCustomOnUpdateText(text);
            }
            text = rs.getString("custom_on_delete_text");
            if (!StringUtils.isBlank(text)) {
                trigger.setCustomOnDeleteText(text);
            }
            text = rs.getString("custom_before_insert_text");
            if (!StringUtils.isBlank(text)) {
                trigger.setCustomBeforeInsertText(text);
            }
            text = rs.getString("custom_before_update_text");
            if (!StringUtils.isBlank(text)) {
                trigger.setCustomBeforeUpdateText(text);
            }
            text = rs.getString("custom_before_delete_text");
            if (!StringUtils.isBlank(text)) {
                trigger.setCustomBeforeDeleteText(text);
            }
            condition = rs.getString("external_select");
            if (!StringUtils.isBlank(condition)) {
                trigger.setExternalSelect(condition);
            }
            trigger.setChannelExpression(rs.getString("channel_expression"));
            trigger.setTxIdExpression(rs.getString("tx_id_expression"));
            trigger.setCreateTime(rs.getDateTime("create_time"));
            trigger.setLastUpdateTime(rs.getDateTime("last_update_time"));
            trigger.setLastUpdateBy(rs.getString("last_update_by"));
            trigger.setExcludedColumnNames(rs.getString("excluded_column_names"));
            trigger.setIncludedColumnNames(rs.getString("included_column_names"));
            trigger.setSyncKeyNames(rs.getString("sync_key_names"));
            if (rs.containsKey("time_based_column_name")) {
                trigger.setTimeBasedCaptureColumn(rs.getString("time_based_column_name"));
            }
            return trigger;
        }
    }

    static class TriggerRouterMapper implements ISqlRowMapper<TriggerRouter> {
        public TriggerRouterMapper() {
        }

        public TriggerRouter mapRow(Row rs) {
            TriggerRouter triggerRouter = new TriggerRouter();
            Trigger trigger = new Trigger();
            trigger.setTriggerId(rs.getString("trigger_id"));
            triggerRouter.setTrigger(trigger);
            Router router = new Router();
            router.setRouterId(rs.getString("router_id"));
            triggerRouter.setRouter(router);
            triggerRouter.setCreateTime(rs.getDateTime("create_time"));
            triggerRouter.setLastUpdateTime(rs.getDateTime("last_update_time"));
            triggerRouter.setLastUpdateBy(rs.getString("last_update_by"));
            triggerRouter.setInitialLoadOrder(rs.getInt("initial_load_order"));
            triggerRouter.setInitialLoadSelect(StringUtils.trimToNull(rs.getString("initial_load_select")));
            triggerRouter.setEnabled(rs.getBoolean("enabled"));
            triggerRouter.setInitialLoadDeleteStmt(StringUtils.trimToNull(rs.getString("initial_load_delete_stmt")));
            triggerRouter.setPingBackEnabled(rs.getBoolean("ping_back_enabled"));
            if (rs.containsKey("data_refresh_type")) {
                triggerRouter.setDataRefreshType(rs.getString("data_refresh_type"));
            }
            return triggerRouter;
        }
    }

    public Map<Trigger, Exception> getFailedTriggers() {
        return this.failureListener.getFailures();
    }

    public TriggerHistory findTriggerHistoryForGenericSync() {
        String triggerTableName = TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE);
        try {
            Collection<TriggerHistory> histories = historyMap.values();
            for (TriggerHistory triggerHistory : histories) {
                if (triggerHistory.getSourceTableName().equalsIgnoreCase(triggerTableName) && triggerHistory.getInactiveTime() == null) {
                    return triggerHistory;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find trigger history for generic sync", e);
        }
        TriggerHistory history = findTriggerHistory(null, null, triggerTableName.toUpperCase());
        if (history == null) {
            history = findTriggerHistory(null, null, triggerTableName);
        }
        return history;
    }

    @Override
    public Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistIdAndSortHist(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories) {
        return fillTriggerRoutersByHistIdAndSortHist(sourceNodeGroupId, targetNodeGroupId, targetExternalId, triggerHistories,
                getAllTriggerRoutersForReloadForCurrentNode(sourceNodeGroupId, targetNodeGroupId));
    }

    @Override
    public Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistIdAndSortHist(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories,
            List<TriggerRouter> triggerRouters) {
        final Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId = fillTriggerRoutersByHistId(
                sourceNodeGroupId, targetNodeGroupId, targetExternalId, triggerHistories, triggerRouters);
        final List<Table> sortedTables = getSortedTablesFor(triggerHistories);
        Comparator<TriggerHistory> comparator = new Comparator<TriggerHistory>() {
            public int compare(TriggerHistory o1, TriggerHistory o2) {
                List<TriggerRouter> triggerRoutersForTriggerHist1 = triggerRoutersByHistoryId
                        .get(o1.getTriggerHistoryId());
                int intialLoadOrder1 = 0;
                for (TriggerRouter triggerRouter1 : triggerRoutersForTriggerHist1) {
                    if (triggerRouter1.getInitialLoadOrder() > intialLoadOrder1) {
                        intialLoadOrder1 = triggerRouter1.getInitialLoadOrder();
                    }
                }
                List<TriggerRouter> triggerRoutersForTriggerHist2 = triggerRoutersByHistoryId
                        .get(o2.getTriggerHistoryId());
                int intialLoadOrder2 = 0;
                for (TriggerRouter triggerRouter2 : triggerRoutersForTriggerHist2) {
                    if (triggerRouter2.getInitialLoadOrder() > intialLoadOrder2) {
                        intialLoadOrder2 = triggerRouter2.getInitialLoadOrder();
                    }
                }
                if (intialLoadOrder1 < intialLoadOrder2) {
                    return -1;
                } else if (intialLoadOrder1 > intialLoadOrder2) {
                    return 1;
                }
                Table table1 = null;
                if (!o1.getSourceTableName().startsWith(tablePrefix)) {
                    table1 = getTargetPlatform().getTableFromCache(o1.getSourceCatalogName(),
                            o1.getSourceSchemaName(), o1.getSourceTableName(), false);
                }
                if (table1 == null) {
                    platform.getTableFromCache(o1.getSourceCatalogName(),
                            o1.getSourceSchemaName(), o1.getSourceTableName(), false);
                }
                Table table2 = null;
                if (!o2.getSourceTableName().startsWith(tablePrefix)) {
                    table2 = getTargetPlatform().getTableFromCache(o2.getSourceCatalogName(),
                            o2.getSourceSchemaName(), o2.getSourceTableName(), false);
                }
                if (table2 == null) {
                    platform.getTableFromCache(o2.getSourceCatalogName(),
                            o2.getSourceSchemaName(), o2.getSourceTableName(), false);
                }
                return Integer.valueOf(sortedTables.indexOf(table1)).compareTo(Integer.valueOf(sortedTables
                        .indexOf(table2)));
            };
        };
        Collections.sort(triggerHistories, comparator);
        return triggerRoutersByHistoryId;
    }

    @Override
    public Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistId(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories) {
        return fillTriggerRoutersByHistId(sourceNodeGroupId, targetNodeGroupId, targetExternalId, triggerHistories, getAllTriggerRoutersForReloadForCurrentNode(
                sourceNodeGroupId, targetNodeGroupId));
    }

    protected Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistId(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories,
            List<TriggerRouter> triggerRouters) {
        triggerRouters = new ArrayList<TriggerRouter>(triggerRouters);
        Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId = new HashMap<Integer, List<TriggerRouter>>(
                triggerHistories.size());
        for (TriggerHistory triggerHistory : triggerHistories) {
            List<TriggerRouter> triggerRoutersForTriggerHistory = new ArrayList<TriggerRouter>();
            triggerRoutersByHistoryId.put(triggerHistory.getTriggerHistoryId(),
                    triggerRoutersForTriggerHistory);
            String triggerId = triggerHistory.getTriggerId();
            for (TriggerRouter triggerRouter : triggerRouters) {
                if (triggerRouter.getTrigger().getTriggerId().equals(triggerId)) {
                    if (!triggerRouter.getTrigger().getSourceTableName().contains("$(targetExternalId)")
                            || triggerRouter.getTrigger().getSourceTableName().replace("$(targetExternalId)", targetExternalId)
                                    .equalsIgnoreCase(triggerHistory.getSourceTableName())) {
                        triggerRoutersForTriggerHistory.add(triggerRouter);
                    }
                }
            }
        }
        return triggerRoutersByHistoryId;
    }

    public List<Table> getSortedTablesFor(List<TriggerHistory> histories) {
        return Database.sortByForeignKeys(getTablesFor(histories), null, null, null);
    }

    public List<Table> getTablesFor(List<TriggerHistory> histories) {
        List<Table> tables = new ArrayList<Table>(histories.size());
        for (TriggerHistory triggerHistory : histories) {
            Table table = null;
            if (!triggerHistory.getSourceTableName().startsWith(tablePrefix)) {
                table = getTargetPlatform().getTableFromCache(triggerHistory.getSourceCatalogName(),
                        triggerHistory.getSourceSchemaName(), triggerHistory.getSourceTableName(),
                        false);
            }
            if (table == null) {
                table = platform.getTableFromCache(triggerHistory.getSourceCatalogName(),
                        triggerHistory.getSourceSchemaName(), triggerHistory.getSourceTableName(),
                        false);
            }
            if (table != null) {
                tables.add(table);
            }
        }
        return tables;
    }

    protected void awaitTermination(ExecutorService executor, List<Future<?>> futures) {
        long timeout = parameterService.getLong(ParameterConstants.SYNC_TRIGGERS_TIMEOUT_IN_SECONDS, 3600);
        executor.shutdown();
        try {
            if (executor.awaitTermination(timeout, TimeUnit.SECONDS)) {
                for (Future<?> future : futures) {
                    if (future.isDone()) {
                        future.get();
                    }
                }
            } else {
                log.warn("Timeout of {} reached for {}", timeout, ParameterConstants.SYNC_TRIGGERS_TIMEOUT_IN_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else {
                    throw new RuntimeException(cause);
                }
            }
            throw new RuntimeException(e);
        }
    }

    protected void fixMultipleActiveTriggerHistories(TriggerRouterContext triggerRouterContext) {
        if (parameterService.is(ParameterConstants.SYNC_TRIGGERS_FIX_DUPLICATE_ACTIVE_TRIGGER_HISTORIES, true)) {
            // Get trigger_id, source_table_name, source_schema_name, and source_catalog_name of active ones that have more than one active
            List<TriggerHistory> multiples = getMultipleActiveTriggerHistories();
            if (multiples.size() > 0) {
                log.info("Fixing " + multiples.size() + " trigger histories with multiple active entries");
            }
            for (TriggerHistory triggerHistory : multiples) {
                // Get trigger_history_ids in descending order
                List<TriggerHistory> th = getTriggerHistoryIds(triggerHistory);
                boolean first = true;
                for (TriggerHistory thLocal : th) {
                    // Leave the latest trigger history ID alone
                    if (first) {
                        first = false;
                        continue;
                    }
                    triggerRouterContext.incrementMultipleActiveTriggerRouterCount(thLocal.getTriggerHistoryId());
                    log.info("Marking trigger history ID " + thLocal.getTriggerHistoryId() + " inactive.");
                    long ts = System.currentTimeMillis();
                    inactivateTriggerHistory(thLocal);
                    triggerRouterContext.incrementInactivateTriggerHistTime(System.currentTimeMillis() - ts);
                }
            }
        }
    }

    protected List<TriggerHistory> getMultipleActiveTriggerHistories() {
        if (DatabaseNamesConstants.ASE.equals(getTargetPlatform().getName())) {
            List<TriggerHistory> activeHistoryList = getActiveTriggerHistories();
            List<SimpleTriggerHistory> activeSimpleHistoryList = new ArrayList<SimpleTriggerHistory>();
            for (TriggerHistory history : activeHistoryList) {
                activeSimpleHistoryList.add(new SimpleTriggerHistory(history));
            }
            Set<SimpleTriggerHistory> activeSimpleHistorySet = new HashSet<SimpleTriggerHistory>();
            return activeSimpleHistoryList.stream()
                    .filter(history -> !activeSimpleHistorySet.add(history))
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            return sqlTemplate.query(getSql("multipleActiveTriggerHistSql"), new TriggerHistoryMapper());
        }
    }

    protected List<TriggerHistory> getTriggerHistoryIds(TriggerHistory triggerHistory) {
        List<Object> values = new ArrayList<Object>();
        StringBuilder sb = new StringBuilder(getSql("selectTriggerHistIdSql")).append(" where trigger_id=? and source_table_name=?");
        values.add(triggerHistory.getTriggerId());
        values.add(triggerHistory.getSourceTableName());
        sb.append(" and source_catalog_name");
        if (StringUtils.isBlank(triggerHistory.getSourceCatalogName())) {
            sb.append(" is null ");
        } else {
            sb.append("=? ");
            values.add(triggerHistory.getSourceCatalogName());
        }
        sb.append(" and source_schema_name");
        if (StringUtils.isBlank(triggerHistory.getSourceSchemaName())) {
            sb.append(" is null ");
        } else {
            sb.append("=? ");
            values.add(triggerHistory.getSourceSchemaName());
        }
        sb.append(" and inactive_time is null");
        sb.append(" order by trigger_hist_id desc ");
        return sqlTemplate.query(sb.toString(), new TriggerHistoryMapper(), values.toArray());
    }

    protected void logTriggerRouterContextAnomalies(TriggerRouterContext triggerRouterContext) {
        for (int triggerHistoryId : triggerRouterContext.getMultipleActiveTriggerRouterKeyset()) {
            log.info("Marked trigger history ID {} inactive.", triggerHistoryId);
        }
        for (String triggerId : triggerRouterContext.getTriggerReadTableFromDatabaseKeyset()) {
            long databaseReadCount = triggerRouterContext.getTriggerReadTableFromDatabaseCount(triggerId);
            if (databaseReadCount > 2) {
                long copyCount = triggerRouterContext.getTriggerCopyTableCount(triggerId);
                log.info("The trigger {} read table {} times from the database, " +
                        "and made {} copies of the table.", triggerId, databaseReadCount, copyCount);
            }
        }
    }

    protected void logTriggerRouterContextTimings(TriggerRouterContext triggerRouterContext) {
        log.info("SyncTriggers: fix multiple active trigger histories took {} ms",
                triggerRouterContext.getFixMultipleActiveTriggerHistoriesTime());
        log.info("SyncTriggers: get triggers for current node took {} ms",
                triggerRouterContext.getTriggersForCurrentNodeTime());
        log.info("SyncTriggers: sync triggers started took {} ms",
                triggerRouterContext.getSyncTriggersStartedTime());
        log.info("SyncTriggers: get active trigger histories took {} ms",
                triggerRouterContext.getActiveTriggerHistoriesTime());
        log.info("SyncTriggers: update or create ddl triggers took {} ms",
                triggerRouterContext.getUpdateOrCreateDdlTriggersTime());
        log.info("SyncTriggers: sync triggers ended took {} ms",
                triggerRouterContext.getSyncTriggersEndedTime());
        log.info("SyncTriggers: tables for trigger took {} ms",
                triggerRouterContext.getTablesForTriggerTime());
        log.info("SyncTriggers: drop trigger took {} ms",
                triggerRouterContext.getDropTriggerTime());
        log.info("SyncTriggers: trigger inactivated took {} ms",
                triggerRouterContext.getTriggerInactivatedTime());
        log.info("SyncTriggers: does trigger exist took {} ms",
                triggerRouterContext.getDoesTriggerExistTime());
        log.info("SyncTriggers: inactivate trigger hist took {} ms",
                triggerRouterContext.getInactivateTriggerHistTime());
        log.info("SyncTriggers: update or create database trigger took {} ms",
                triggerRouterContext.getUpdateOrCreateDatabaseTriggersTime());
        log.info("SyncTriggers: trigger to table supporting info took {} ms",
                triggerRouterContext.getTriggerToTableSupportingInfoTime());
        log.info("SyncTriggers: table does not exist took {} ms",
                triggerRouterContext.getTableDoesNotExistTime());
    }

    class SyncTriggersThreadFactory implements ThreadFactory {
        AtomicInteger threadNumber = new AtomicInteger(1);
        String namePrefix = parameterService.getEngineName().toLowerCase() + "-sync-triggers-";

        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(namePrefix + threadNumber.getAndIncrement());
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        }
    }

    class SimpleTriggerHistory extends TriggerHistory {
        private static final long serialVersionUID = 1L;

        public SimpleTriggerHistory(TriggerHistory history) {
            setTriggerId(history.getTriggerId());
            setSourceTableName(history.getSourceTableName());
            setSourceSchemaName(history.getSourceSchemaName());
            setSourceCatalogName(history.getSourceCatalogName());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((getSourceCatalogName() == null) ? 0 : getSourceCatalogName().hashCode());
            result = prime * result + ((getSourceSchemaName() == null) ? 0 : getSourceSchemaName().hashCode());
            result = prime * result + ((getSourceTableName() == null) ? 0 : getSourceTableName().hashCode());
            result = prime * result + ((getTriggerId() == null) ? 0 : getTriggerId().hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SimpleTriggerHistory other = (SimpleTriggerHistory) obj;
            if (getSourceCatalogName() == null) {
                if (other.getSourceCatalogName() != null) {
                    return false;
                }
            } else if (!getSourceCatalogName().equals(other.getSourceCatalogName())) {
                return false;
            }
            if (getSourceSchemaName() == null) {
                if (other.getSourceSchemaName() != null) {
                    return false;
                }
            } else if (!getSourceSchemaName().equals(other.getSourceSchemaName())) {
                return false;
            }
            if (getSourceTableName() == null) {
                if (other.getSourceTableName() != null) {
                    return false;
                }
            } else if (!getSourceTableName().equals(other.getSourceTableName())) {
                return false;
            }
            if (getTriggerId() == null) {
                if (other.getTriggerId() != null) {
                    return false;
                }
            } else if (!getTriggerId().equals(other.getTriggerId())) {
                return false;
            }
            return true;
        }
    }
}
