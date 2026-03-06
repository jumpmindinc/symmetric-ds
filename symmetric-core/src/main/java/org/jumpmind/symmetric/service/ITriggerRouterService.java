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
package org.jumpmind.symmetric.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.cache.TriggerRouterRoutersCache;
import org.jumpmind.symmetric.config.ITriggerCreationListener;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * Provides an API to configure {@link TriggerRouter}s, {@link Trigger}s and {@link Router}s.
 */
public interface ITriggerRouterService {
    public boolean refreshFromDatabase();

    public List<TriggerHistory> getActiveTriggerHistoriesFromCache();

    public List<TriggerHistory> getActiveTriggerHistories();

    public List<TriggerHistory> getActiveTriggerHistories(Trigger trigger);

    public List<TriggerHistory> getActiveTriggerHistories(String tableName);

    public Map<String, TriggerHistory> getLastErrorTriggerHistories();

    public List<TriggerRouter> getTriggerRouters(boolean refreshCache);

    public List<TriggerRouter> getTriggerRoutersFromDatabase();

    /**
     * Return a list of triggers used when extraction configuration data during the registration process.
     * 
     * @param sourceGroupId
     *            group id of the node being registered with
     * @param targetGroupId
     *            group id of the node that is registering
     */
    public List<TriggerRouter> buildTriggerRoutersForSymmetricTables(String version, NodeGroupLink nodeGroupLink, String... tablesToExclude);

    public String buildSymmetricTableRouterId(String triggerId, String sourceNodeGroupId, String targetNodeGroupId);

    public Trigger getTriggerForCurrentNodeById(String triggerId);

    public TriggerRouter getTriggerRouterForCurrentNode(String triggerId, String routerId, boolean refreshCache);

    public List<TriggerRouter> getTriggerRoutersForTargetNode(String targetNodeGroupId);

    public List<TriggerRouter> getTriggerRoutersForSourceAndTargetNodes(String sourceNodeGroupId, String targetNodeGroupId);

    /**
     * Returns a list of triggers that should be active for the current node.
     * 
     * @param refreshCache
     *            Indicates that the cache should be refreshed
     */
    public List<Trigger> getTriggersForCurrentNode(boolean refreshCache);

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannel(String nodeGroupId);

    public Map<String, List<TriggerRouter>> getTriggerRoutersByChannelFromDatabase(String nodeGroupId);

    /**
     * Returns a map of trigger routers keyed by trigger id.
     * 
     * @param refreshCache
     *            Indicates that the cache should be refreshed
     */
    public Map<String, List<TriggerRouter>> getTriggerRoutersForCurrentNode(boolean refreshCache);

    public Map<String, TriggerRouterRoutersCache> getTriggerRoutersCacheByNodeGroupIdFromDatabase();

    public Map<Integer, TriggerRouter> getTriggerRoutersByTriggerHist(String targetNodeGroupId, boolean refreshCache);

    public Map<String, Map<Integer, TriggerRouter>> getTriggerRoutersByTriggerHistFromDatabase();

    public TriggerRouter getTriggerRouterByTriggerHist(String targetNodeGroupId, int triggerHistId, boolean refreshCache);

    /**
     * Get router that is currently in use by a trigger router at the node that is hosting this call.
     * 
     * @param routerId
     *            The router_id to retrieve
     * @param refreshCache
     *            Whether to force the router to be re-retrieved from the database
     */
    public Router getActiveRouterByIdForCurrentNode(String routerId, boolean refreshCache);

    public Router getRouterById(String routerId);

    public Router getRouterById(String routerId, boolean refreshCache);

    public List<Router> getRouters();

    public List<Router> getRouters(boolean replaceVariables);

    /**
     * Get a list of routers for a specific node group link.
     */
    public List<Router> getRoutersByGroupLink(NodeGroupLink link);

    public boolean isRouterBeingUsed(String routerId);

    public void deleteRouter(Router router);

    public void deleteAllRouters();

    public void saveRouter(Router router);

    public Router saveRouterAsCopy(Router router);

    public void renameRouter(String oldId, Router router);

    public List<TriggerRouter> getAllTriggerRoutersForCurrentNode(String sourceNodeGroupId);

    /**
     * Get a list of all the triggers that have been defined for the system.
     */
    public List<Trigger> getTriggers();

    public List<Trigger> getTriggers(boolean replaceTokens);

    public void saveTrigger(Trigger trigger);

    public void insertTriggers(Collection<Trigger> triggers);

    public void updateTriggers(Collection<Trigger> triggers);

    public void saveTriggerAsCopy(String originalId, Trigger trigger);

    public void renameTrigger(String oldId, Trigger trigger);

    public void deleteTrigger(Trigger trigger);

    public void deleteTriggers(Collection<Trigger> triggers);

    public void deleteAllTriggers();

    public void dropTriggers();

    public void dropTriggers(boolean includeSymTriggers);

    public void dropTriggers(Set<String> tables);

    public void createTriggersOnChannelForTables(String channelId, String catalogName, String schemaName, List<String> tables, String lastUpdateBy);

    public boolean isTriggerBeingUsed(String triggerId);

    public boolean doesTriggerExist(String triggerId);

    public boolean doesTriggerExistForTable(String tableName);

    public List<TriggerRouter> getAllTriggerRoutersForReloadForCurrentNode(String sourceNodeGroupId, String targetNodeGroupId);

    public Set<TriggerRouter> getTriggerRouterForTableForCurrentNode(NodeGroupLink link, String catalogName, String schemaName, String tableName,
            boolean refreshCache);

    public Set<TriggerRouter> getTriggerRouterForTableForCurrentNode(String catalog, String schema, String tableName, boolean refreshCache);

    public TriggerRouter findTriggerRouterById(String triggerId, String routerId);

    public TriggerRouter findTriggerRouterById(String triggerId, String routerId, boolean refreshCache);

    public List<TriggerRouter> findTriggerRoutersByTriggerId(String triggerId, boolean refreshCache);

    public List<TriggerRouter> findTriggerRoutersByRouterId(String routerId, boolean refreshCache);

    public void inactivateTriggerHistory(TriggerHistory history);

    public TriggerHistory getNewestTriggerHistoryForTrigger(List<TriggerHistory> activeTriggerHistories, String triggerId, String catalogName,
            String schemaName, String tableName);

    public TriggerHistory getNewestTriggerHistoryForTrigger(String triggerId, String catalogName,
            String schemaName, String tableName);

    public TriggerHistory getTriggerHistory(int historyId);

    public List<TriggerHistory> findTriggerHistories(String catalogName, String schemaName, String tableName);

    public TriggerHistory findTriggerHistory(String catalogName, String schemaName, String tableName);

    public Trigger getTriggerById(String triggerId);

    public Trigger getTriggerById(String triggerId, boolean refreshCache);

    public String getTriggerName(DataEventType dml, int maxTriggerNameLength, Trigger trigger, Table table,
            List<TriggerHistory> activeTriggerHistories, TriggerHistory oldhist, List<String> triggerNamesGeneratedThisSession);

    public void insert(TriggerHistory newAuditRecord);

    public Map<Long, TriggerHistory> getHistoryRecords();

    public void deleteTriggerRouter(TriggerRouter triggerRouter);

    public void deleteTriggerRouter(String triggerId, String routerId);

    public void deleteTriggerRouters(Collection<TriggerRouter> triggerRouters);

    public void deleteAllTriggerRouters();

    public void saveTriggerRouter(TriggerRouter triggerRouter, boolean updateTriggerRouterTableOnly);

    public void saveTriggerRouter(TriggerRouter triggerRouter);

    public void renameTriggerRouter(String oldTriggerId, String oldRouterId, TriggerRouter TriggerRouter);

    public void insertTriggerRouters(Collection<TriggerRouter> triggerRouters);

    public void insertTriggersAndTriggerRouters(Collection<Trigger> triggers, Collection<TriggerRouter> triggerRouters);

    public void updateTriggerRouters(Collection<TriggerRouter> triggerRouters);

    public void syncTrigger(Trigger trigger, ITriggerCreationListener listener, boolean force);

    public void syncTrigger(Trigger trigger, ITriggerCreationListener listener, boolean force, boolean verifyTrigger);

    public boolean syncTriggers(List<Trigger> triggers, ITriggerCreationListener listener, boolean force, boolean verifyInDatabase);

    public boolean syncTriggers(List<Trigger> triggers, ITriggerCreationListener listener, boolean force, boolean verifyInDatabase, boolean useTableCache);

    public boolean syncTriggers(Table table, boolean genAlways);

    public boolean syncTriggers(List<Table> tables, boolean genAlways);

    public boolean syncTriggers(String targetExternalId, boolean force);

    public void dropTriggers(TriggerHistory history);

    public boolean syncTriggers(boolean genAlways);

    public boolean syncTriggers();

    public boolean syncTriggers(StringBuilder sqlBuffer, boolean genAlways);

    public Map<Trigger, Exception> getFailedTriggers();

    public Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistIdAndSortHist(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories,
            List<TriggerRouter> triggerRouters, boolean sortByFk);

    public Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistIdAndSortHist(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories, boolean sortByFk);

    public Map<Integer, List<TriggerRouter>> fillTriggerRoutersByHistId(
            String sourceNodeGroupId, String targetNodeGroupId, String targetExternalId, List<TriggerHistory> triggerHistories);

    public TriggerHistory findTriggerHistoryForGenericSync();

    public void clearCache();

    public Collection<Trigger> findMatchingTriggers(List<Trigger> triggers, String catalog, String schema,
            String table);

    public List<Table> getTablesFor(List<TriggerHistory> histories);

    public List<Table> getSortedTablesFor(List<TriggerHistory> histories);

    public List<Trigger> buildTriggersForSymmetricTables(String version, String... tablesToExclude);
}
