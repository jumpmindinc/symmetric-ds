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
package org.jumpmind.symmetric.common;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.ext.IConfigurationChangedListener;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.job.IJobManager;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationChangedHelper {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationChangedHelper.class);
    private static final String SUFFIX = ConfigurationChangedHelper.class.getSimpleName();
    private static final String CTX_KEY_FLUSH_CHANNELS_NEEDED = "FlushChannels." + SUFFIX;
    private static final String CTX_KEY_FLUSH_CONFLICTS_NEEDED = "FlushConflicts." + SUFFIX;
    private static final String CTX_KEY_FLUSH_EXTENSIONS_NEEDED = "FlushExtensions." + SUFFIX;
    private static final String CTX_KEY_FLUSH_GROUPLETS_NEEDED = "FlushGrouplets." + SUFFIX;
    private static final String CTX_KEY_FLUSH_LOADFILTERS_NEEDED = "FlushLoadFilters." + SUFFIX;
    private static final String CTX_KEY_FLUSH_NODES_NEEDED = "FlushNodes." + SUFFIX;
    private static final String CTX_KEY_FLUSH_NODE_GROUP_LINKS_NEEDED = "FlushNodeGroups." + SUFFIX;
    private static final String CTX_KEY_FLUSH_NODE_SECURITY_NEEDED = "FlushNodeSecurity." + SUFFIX;
    private static final String CTX_KEY_FLUSH_ROUTERS_NEEDED = "FlushRouters." + SUFFIX;
    private static final String CTX_KEY_FLUSH_PARAMETERS_NEEDED = "FlushParameters." + SUFFIX;
    private static final String CTX_KEY_FLUSH_TRANSFORMS_NEEDED = "FlushTransforms." + SUFFIX;
    private static final String CTX_KEY_RESYNC_NEEDED = "Resync." + SUFFIX;
    private static final String CTX_KEY_RESYNC_ALLOWED = "ResyncAllowed." + SUFFIX;
    private static final String CTX_KEY_CHANGED_TRIGGER_IDS = "ChangedTriggerIds." + SUFFIX;
    private static final String CTX_KEY_CHANGED_JOB_IDS = "ChangedJobIds." + SUFFIX;
    private static final String CTX_KEY_RESTART_JOB_MANAGER_NEEDED = "RestartJobManager." + SUFFIX;
    private static final String CTX_KEY_FILE_SYNC_NEEDED = "FileSyncEnable." + SUFFIX;
    private static final String CTX_KEY_CLUSTER_NEEDED = "ClusterEnable." + SUFFIX;
    private ISymmetricEngine engine;
    private String tablePrefix;
    private ConfigurationVersionHelper versionHelper;
    private IConfigurationChangedListener listener;

    public ConfigurationChangedHelper(ISymmetricEngine engine) {
        this.engine = engine;
        tablePrefix = engine.getTablePrefix();
        versionHelper = new ConfigurationVersionHelper(tablePrefix);
        listener = AppUtils.newInstance(IConfigurationChangedListener.class, null, new Object[] { engine }, new Class[] { ISymmetricEngine.class });
    }

    public void handleChange(Context context, Table table, CsvData data) {
        updateContext(TableConstants.SYM_NODE, table, context, CTX_KEY_FLUSH_NODES_NEEDED);
        updateContext(TableConstants.SYM_NODE_SECURITY, table, context, CTX_KEY_FLUSH_NODE_SECURITY_NEEDED);
        if (context.get(Constants.DATA_CONTEXT_TARGET_NODE) == null && StringUtils.isNotBlank(data.getAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID))) {
            return;
        }
        updateContext(TableConstants.SYM_CHANNEL, table, context, CTX_KEY_FLUSH_CHANNELS_NEEDED);
        updateContext(TableConstants.SYM_CONFLICT, table, context, CTX_KEY_FLUSH_CONFLICTS_NEEDED);
        updateContext(TableConstants.SYM_EXTENSION, table, context, CTX_KEY_FLUSH_EXTENSIONS_NEEDED);
        updateContext(TableConstants.SYM_GROUPLET, table, context, CTX_KEY_FLUSH_GROUPLETS_NEEDED, CTX_KEY_RESYNC_NEEDED);
        updateContext(TableConstants.SYM_GROUPLET_LINK, table, context, CTX_KEY_FLUSH_GROUPLETS_NEEDED, CTX_KEY_RESYNC_NEEDED);
        updateContext(TableConstants.SYM_JOB, table, context, CTX_KEY_RESTART_JOB_MANAGER_NEEDED);
        updateContext(TableConstants.SYM_LOAD_FILTER, table, context, CTX_KEY_FLUSH_LOADFILTERS_NEEDED);
        updateContext(TableConstants.SYM_NODE_GROUP_LINK, table, context, CTX_KEY_FLUSH_NODE_GROUP_LINKS_NEEDED, CTX_KEY_FLUSH_TRANSFORMS_NEEDED,
                CTX_KEY_RESYNC_NEEDED);
        updateContext(TableConstants.SYM_PARAMETER, table, context, CTX_KEY_FLUSH_PARAMETERS_NEEDED);
        updateContext(TableConstants.SYM_ROUTER, table, context, CTX_KEY_RESYNC_NEEDED, CTX_KEY_FLUSH_ROUTERS_NEEDED);
        updateContext(TableConstants.SYM_TRANSFORM_TABLE, table, context, CTX_KEY_FLUSH_TRANSFORMS_NEEDED);
        updateContext(TableConstants.SYM_TRANSFORM_COLUMN, table, context, CTX_KEY_FLUSH_TRANSFORMS_NEEDED);
        updateContext(TableConstants.SYM_TRIGGER_ROUTER_GROUPLET, table, context, CTX_KEY_FLUSH_GROUPLETS_NEEDED, CTX_KEY_RESYNC_NEEDED);
        if (matchesTable(table, TableConstants.SYM_PARAMETER) && matchesDmlEventType(data) && matchesExternalId(table, data, "external_id")
                && matchesNodeGroupId(table, data, "node_group_id")) {
            String jobName = JobDefinition.getJobNameFromData(data);
            if (jobName != null) {
                getHashSet(context, CTX_KEY_CHANGED_JOB_IDS).add(jobName);
            }
            String paramKey = getColumnValue(table, data, "param_key");
            if (ParameterConstants.FILE_SYNC_ENABLE.equals(paramKey)) {
                context.put(CTX_KEY_FILE_SYNC_NEEDED, true);
            } else if (ParameterConstants.CLUSTER_LOCKING_ENABLED.equals(paramKey)) {
                context.put(CTX_KEY_CLUSTER_NEEDED, true);
            }
            Map<String, ParameterMetaData> parameters = ParameterConstants.getParameterMetaData();
            ParameterMetaData pmd = parameters.get(paramKey);
            if (pmd != null && pmd.getTags().contains(ParameterMetaData.TAG_TRIGGER)) {
                context.put(CTX_KEY_RESYNC_NEEDED, true);
            }
        }
        if ((matchesTable(table, TableConstants.SYM_TRIGGER) || matchesTable(table, TableConstants.SYM_TRIGGER_ROUTER)) && isSyncTriggersAllowed(context) &&
                context.get(CTX_KEY_RESYNC_NEEDED) == null) {
            if (data.getDataEventType().equals(DataEventType.DELETE)) {
                context.put(CTX_KEY_RESYNC_NEEDED, true);
            } else {
                Set<String> triggers = getHashSet(context, CTX_KEY_CHANGED_TRIGGER_IDS);
                String triggerId = getColumnValue(table, data, "trigger_id");
                if (triggerId != null) {
                    triggers.add(triggerId);
                }
            }
        }
        if (listener != null) {
            listener.handleChange(context, table, data);
        }
    }

    private boolean matchesDmlEventType(CsvData data) {
        boolean ret = false;
        if (data.getDataEventType().equals(DataEventType.INSERT) || data.getDataEventType().equals(DataEventType.UPDATE) || data.getDataEventType().equals(
                DataEventType.DELETE)) {
            ret = true;
        }
        return ret;
    }

    public void contextCommitted(Context context) {
        if (context.remove(CTX_KEY_FLUSH_CHANNELS_NEEDED) != null) {
            log.info("Clearing cache for channels");
            engine.getConfigurationService().clearCache();
        }
        if (context.remove(CTX_KEY_FLUSH_CONFLICTS_NEEDED) != null) {
            log.info("Clearing cache for conflicts");
            engine.getDataLoaderService().clearCache();
        }
        if (context.remove(CTX_KEY_FLUSH_EXTENSIONS_NEEDED) != null) {
            log.info("Clearing cache for extensions");
            engine.getExtensionService().refresh();
        }
        if (context.remove(CTX_KEY_FLUSH_GROUPLETS_NEEDED) != null) {
            log.info("Clearing cache for grouplets");
            engine.getGroupletService().clearCache();
        }
        if (context.remove(CTX_KEY_FLUSH_LOADFILTERS_NEEDED) != null) {
            log.info("Clearing cache for load filters");
            engine.getLoadFilterService().clearCache();
        }
        if (context.remove(CTX_KEY_FLUSH_NODES_NEEDED) != null) {
            log.info("Clearing cache for nodes");
            engine.getNodeService().flushNodeCache();
            engine.getNodeService().flushNodeGroupCache();
        }
        if (context.remove(CTX_KEY_FLUSH_NODE_GROUP_LINKS_NEEDED) != null) {
            log.info("Clearing cache for node groups");
            engine.getConfigurationService().clearCache();
            engine.getNodeService().flushNodeGroupCache();
        }
        if (context.remove(CTX_KEY_FLUSH_NODE_SECURITY_NEEDED) != null) {
            log.info("Clearing cache for node security");
            engine.getNodeService().flushNodeAuthorizedCache();
        }
        if (context.remove(CTX_KEY_FLUSH_PARAMETERS_NEEDED) != null) {
            log.info("Clearing cache for parameters");
            engine.getParameterService().rereadParameters();
        }
        if (context.remove(CTX_KEY_FLUSH_ROUTERS_NEEDED) != null) {
            log.info("Clearing cache for routers");
            engine.getCacheManager().flushAllWithRouters();
            engine.getRouterService().flushCache();
        }
        if (context.remove(CTX_KEY_CLUSTER_NEEDED) != null) {
            engine.getClusterService().refreshLockEntries();
        }
        if (listener != null) {
            listener.contextCommitted(context);
        }
    }

    public void contextComplete(Context context) {
        if (context.remove(CTX_KEY_FLUSH_TRANSFORMS_NEEDED) != null) {
            flushTransforms();
        }
        if (context.remove(CTX_KEY_RESTART_JOB_MANAGER_NEEDED) != null && engine.isStarted()) {
            log.info("Clearing cache for jobs and restarting jobs");
            engine.getJobManager().init();
            engine.getJobManager().startJobs();
            context.remove(CTX_KEY_CHANGED_JOB_IDS);
        }
        if (context.get(CTX_KEY_CHANGED_JOB_IDS) != null && engine.isStarted()) {
            restartJobs(context);
        }
        if (context.remove(CTX_KEY_RESYNC_NEEDED) != null && isSyncTriggersAllowed(context)) {
            log.info("Syncing all triggers because of configuration change");
            engine.getTriggerRouterService().syncTriggers();
            engine.getRegistrationService().setAllowClientRegistration(true);
            context.remove(CTX_KEY_CHANGED_TRIGGER_IDS);
        }
        if (context.get(CTX_KEY_CHANGED_TRIGGER_IDS) != null && isSyncTriggersAllowed(context)) {
            syncTriggers(context);
        }
        if (context.remove(CTX_KEY_FILE_SYNC_NEEDED) != null) {
            enableDisableFileSync(context);
        }
        if (listener != null) {
            listener.contextComplete(context);
        }
    }

    public void contextCommittedAndComplete(Context context) {
        contextCommitted(context);
        contextComplete(context);
    }

    private void syncTriggers(Context context) {
        @SuppressWarnings("unchecked")
        Set<String> triggerIds = (Set<String>) context.remove(CTX_KEY_CHANGED_TRIGGER_IDS);
        log.info("Syncing {} triggers because of configuration change", triggerIds.size());
        Set<Trigger> triggers = new HashSet<Trigger>();
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        triggerRouterService.clearCache();
        for (String triggerId : triggerIds) {
            Trigger trigger = triggerRouterService.getTriggerById(triggerId, false);
            if (trigger != null) {
                triggers.add(trigger);
            }
        }
        engine.getTriggerRouterService().syncTriggers(new ArrayList<Trigger>(triggers), null, false, true);
    }

    private void flushTransforms() {
        log.info("Clearing cache for transforms");
        engine.getTransformService().clearCache();
        List<OutgoingBatch> batches = engine.getOutgoingBatchService().getBatchesInProgress();
        if (batches.size() > 0) {
            log.info("Clearing staging for {} batches", batches.size());
            for (OutgoingBatch batch : batches) {
                IStagedResource resource = engine.getStagingManager().find(Constants.STAGING_CATEGORY_OUTGOING, batch.getStagedLocation(), batch.getBatchId());
                if (resource != null) {
                    log.debug("Deleting {}", resource.getPath());
                    boolean success = resource.delete();
                    if (!success) {
                        log.warn("Failed to delete the '{}' staging resource", resource.getPath());
                    }
                }
            }
        }
    }

    private void restartJobs(Context context) {
        @SuppressWarnings("unchecked")
        Set<String> jobNames = (Set<String>) context.remove(CTX_KEY_CHANGED_JOB_IDS);
        IJobManager jobManager = engine.getJobManager();
        if (jobManager != null) {
            log.info("Restarting jobs to match schedule change");
            for (String jobName : jobNames) {
                jobManager.restartJob(jobName);
            }
        }
    }

    private void enableDisableFileSync(Context context) {
        log.info("Changing file sync to match parameter change");
        engine.getParameterService().rereadParameters();
        engine.getConfigurationService().initDefaultChannels();
        engine.getFileSyncService().clearCache();
        IJobManager jobManager = engine.getJobManager();
        if (jobManager != null && engine.isStarted()) {
            jobManager.restartJob(ClusterConstants.FILE_SYNC_TRACKER);
            jobManager.restartJob(ClusterConstants.FILE_SYNC_PULL);
            jobManager.restartJob(ClusterConstants.FILE_SYNC_PUSH);
        }
        if (engine.getParameterService().is(ParameterConstants.AUTO_SYNC_TRIGGERS)) {
            log.info("Syncing triggers for file snapshot");
            engine.getTriggerRouterService().clearCache();
            Table fileSnapshotTable = engine.getDatabasePlatform()
                    .getTableFromCache(TableConstants.getTableName(engine.getTablePrefix(), TableConstants.SYM_FILE_SNAPSHOT), false);
            engine.getTriggerRouterService().syncTriggers(fileSnapshotTable, false);
        }
    }

    private boolean matchesTable(Table table, String tableSuffix) {
        if (table != null && table.getName() != null) {
            return table.getName().equalsIgnoreCase(TableConstants.getTableName(tablePrefix, tableSuffix));
        } else {
            return false;
        }
    }

    private boolean matchesExternalId(Table table, CsvData data, String columnName) {
        String externalId = engine.getParameterService().getExternalId();
        String columnValue = getColumnValue(table, data, columnName);
        return columnValue == null || externalId.equals(columnValue) || columnValue.equals(ParameterConstants.ALL);
    }

    private boolean matchesNodeGroupId(Table table, CsvData data, String columnName) {
        String nodeGroupId = engine.getParameterService().getNodeGroupId();
        String columnValue = getColumnValue(table, data, columnName);
        return columnValue == null || nodeGroupId.equals(columnValue) || columnValue.equals(ParameterConstants.ALL);
    }

    private void updateContext(String tableSuffix, Table table, Context context, String... constants) {
        if (matchesTable(table, tableSuffix)) {
            for (String constant : constants) {
                context.put(constant, true);
            }
        }
    }

    private Set<String> getHashSet(Context context, String name) {
        @SuppressWarnings("unchecked")
        Set<String> set = (Set<String>) context.get(name);
        if (set == null) {
            set = new HashSet<String>();
            context.put(name, set);
        }
        return set;
    }

    public static String getColumnValue(Table table, CsvData data, String name) {
        String[] values = data.getParsedData(CsvData.ROW_DATA);
        if (values == null) {
            values = data.getParsedData(CsvData.PK_DATA);
        }
        int index = table.getColumnIndex(name);
        if (index >= 0 && values != null && index < values.length) {
            return values[index];
        }
        return null;
    }

    public List<String> getSqlStatements(String script) {
        List<String> sqlStatements = new ArrayList<String>();
        SqlScriptReader scriptReader = new SqlScriptReader(new StringReader(script));
        scriptReader.setStripOutComments(true);
        try {
            String sql = scriptReader.readSqlStatement();
            while (sql != null) {
                if (StringUtils.startsWithIgnoreCase(sql, "delimiter")) {
                    String delimiter = StringUtils.trimToNull(sql.substring("delimiter".length()));
                    if (delimiter != null) {
                        scriptReader.setDelimiter(delimiter);
                    }
                } else {
                    sqlStatements.add(sql);
                }
                sql = scriptReader.readSqlStatement();
            }
            return sqlStatements;
        } finally {
            try {
                if (scriptReader != null) {
                    scriptReader.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public boolean isNewContext(Context context) {
        return context.get(CTX_KEY_RESYNC_ALLOWED) == null;
    }

    public void setSyncTriggersAllowed(Context context, boolean syncTriggersAllowed) {
        context.put(CTX_KEY_RESYNC_ALLOWED, syncTriggersAllowed);
    }

    public boolean isSyncTriggersAllowed(Context context) {
        return context.get(CTX_KEY_RESYNC_ALLOWED) == Boolean.TRUE;
    }

    public void setSyncTriggersNeeded(Context context) {
        context.put(CTX_KEY_RESYNC_NEEDED, true);
    }

    public Set<Node> filterNodes(Set<Node> nodes, String tableName, Map<String, String> columnValues) {
        return versionHelper.filterNodes(nodes, tableName, columnValues);
    }
}
