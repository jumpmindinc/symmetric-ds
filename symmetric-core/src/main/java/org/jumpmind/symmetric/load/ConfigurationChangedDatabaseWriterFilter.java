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
package org.jumpmind.symmetric.load;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.RelationsList;
import org.jumpmind.db.model.Table;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ConfigurationChangedHelper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterFilterAdapter;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An out of the box filter that checks to see if the SymmetricDS configuration has changed. If it has, it will take the correct action to apply the
 * configuration change to the current node.
 */
public class ConfigurationChangedDatabaseWriterFilter extends DatabaseWriterFilterAdapter implements IBuiltInExtensionPoint, ILoadSyncLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationChangedDatabaseWriterFilter.class);
    private static final String SUFFIX = ConfigurationChangedDatabaseWriterFilter.class.getSimpleName();
    private static final String CTX_KEY_RESYNC_TABLE_NEEDED = "Resync.Table" + SUFFIX;
    private static final String CTX_KEY_CHANGED_NODE_SECURITY = "ChangedNodeSecurity." + SUFFIX;
    private static final String CTX_KEY_INITIAL_LOAD_COMPLETED = "InitialLoadCompleted." + SUFFIX;
    private static final String CTX_KEY_INITIAL_LOAD_LISTENER = "InitialLoadListener." + SUFFIX;
    private static final String CTX_KEY_MY_NODE_ID = "MyNodeId." + SUFFIX;
    private static final String CTX_KEY_MY_NODE_SECURITY = "MyNodeSecurity." + SUFFIX;
    private static final String CTX_KEY_CANCEL_LOAD = "CancelLoad." + SUFFIX;
    private static final String CTX_KEY_INITAL_LOAD_ID = "InitialLoadId." + SUFFIX;
    private static final String CREATE_OR_ALTER_TABLE_PATTERN = " *(alter|create) +table +.*";
    private ISymmetricEngine engine;
    private ConfigurationChangedHelper helper;
    private String tablePrefixLower;
    private boolean matchesTablePrefix;

    public ConfigurationChangedDatabaseWriterFilter(ISymmetricEngine engine) {
        this.engine = engine;
        helper = new ConfigurationChangedHelper(engine);
        tablePrefixLower = engine.getParameterService().getTablePrefix().toLowerCase();
    }

    @Override
    public boolean beforeWrite(DataContext context, Table table, CsvData data) {
        matchesTablePrefix = table.getNameLowerCase().startsWith(tablePrefixLower);
        if (!matchesTablePrefix) {
            return true;
        }
        if (context.getBatch().getBatchId() == Constants.VIRTUAL_BATCH_FOR_REGISTRATION) {
            helper.setSyncTriggersAllowed(context, true);
        }
        if (matchesTable(table, TableConstants.SYM_NODE_SECURITY) && (data.getDataEventType() == DataEventType.INSERT ||
                data.getDataEventType() == DataEventType.UPDATE)) {
            Map<String, String> newData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
            String initialLoadTime = newData.get("INITIAL_LOAD_TIME");
            String initialLoadEndTime = newData.get("INITIAL_LOAD_END_TIME");
            String nodeId = newData.get("NODE_ID");
            String identityId = (String) context.get(CTX_KEY_MY_NODE_ID);
            NodeSecurity nodeSecurity = (NodeSecurity) context.get(CTX_KEY_MY_NODE_SECURITY);
            if (nodeId.equals(identityId) || identityId == null) {
                if (nodeSecurity != null && (nodeSecurity.getInitialLoadTime() == null || nodeSecurity.getInitialLoadEndTime() != null) &&
                        StringUtils.isNotBlank(initialLoadTime) && StringUtils.isBlank(initialLoadEndTime)) {
                    log.info("Initial load started for me");
                    if (hasClientReloadListener(context)) {
                        List<IClientReloadListener> listeners = engine.getExtensionService().getExtensionPointList(IClientReloadListener.class);
                        for (IClientReloadListener listener : listeners) {
                            listener.reloadStarted();
                        }
                    }
                }
            }
        } else if (matchesTable(table, TableConstants.SYM_MONITOR) && (data.getDataEventType() == DataEventType.INSERT)) {
            Map<String, String> newData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
            String monitorID = newData.get("MONITOR_ID");
            if (monitorID != null && (monitorID.equals("SystemBatchErrorMonitor")
                    || monitorID.equals("SystemLogMonitor")
                    || monitorID.equals("SystemOfflineNodeMonitor"))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterWrite(DataContext context, Table table, CsvData data) {
        if (DataEventType.SQL.equals(data.getDataEventType())) {
            String[] parsedData = data.getParsedData(CsvData.ROW_DATA);
            if (parsedData != null && parsedData.length > 0) {
                List<String> sqlStatements = helper.getSqlStatements(parsedData[0]);
                Pattern createOrAlterPattern = Pattern.compile(CREATE_OR_ALTER_TABLE_PATTERN, Pattern.CASE_INSENSITIVE);
                Optional<String> matchingSql = sqlStatements.stream()
                        .filter(sql -> createOrAlterPattern.matcher(sql).matches())
                        .findFirst();
                if (matchingSql.isPresent() && helper.isSyncTriggersAllowed(context)) {
                    helper.setSyncTriggersNeeded(context);
                }
            }
        } else if (data.getDataEventType() == DataEventType.CREATE) {
            @SuppressWarnings("unchecked")
            Set<Table> tables = (Set<Table>) context.get(CTX_KEY_RESYNC_TABLE_NEEDED);
            if (tables == null) {
                tables = new HashSet<>();
                context.put(CTX_KEY_RESYNC_TABLE_NEEDED, tables);
            }
            tables.add(table);
        }
        if (!matchesTablePrefix) {
            return;
        }
        helper.handleChange(context, table, data);
        if (matchesTable(table, TableConstants.SYM_NODE_SECURITY) && data.getDataEventType() == DataEventType.UPDATE) {
            Map<String, String> newData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
            String initialLoadEnabled = newData.get("INITIAL_LOAD_ENABLED");
            String initialLoadTime = newData.get("INITIAL_LOAD_TIME");
            String initialLoadEndTime = newData.get("INITIAL_LOAD_END_TIME");
            String nodeId = newData.get("NODE_ID");
            NodeSecurity nodeSecurity = (NodeSecurity) context.get(CTX_KEY_MY_NODE_SECURITY);
            boolean isInitialLoadComplete = nodeId != null && nodeId.equals(context.getBatch().getTargetNodeId()) &&
                    nodeSecurity != null && nodeSecurity.getInitialLoadEndTime() == null &&
                    StringUtils.isNotBlank(initialLoadTime) && StringUtils.isNotBlank(initialLoadEndTime) && "0".equals(initialLoadEnabled);
            if (isInitialLoadComplete && !engine.getParameterService().is(ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD)) {
                log.info("Requesting syncTriggers because {} is false and sym_node_security changed to indicate that an initial load has completed",
                        ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD);
                helper.setSyncTriggersNeeded(context);
                engine.getRegistrationService().setAllowClientRegistration(false);
            }
            if (isInitialLoadComplete) {
                context.put(CTX_KEY_INITIAL_LOAD_COMPLETED, true);
                context.put(CTX_KEY_INITAL_LOAD_ID, Long.parseLong(newData.get("INITIAL_LOAD_ID")));
            }
        }
        if (matchesTable(table, TableConstants.SYM_NODE_SECURITY)) {
            context.put(CTX_KEY_CHANGED_NODE_SECURITY, true);
        }
        if (matchesTable(table, TableConstants.SYM_TABLE_RELOAD_STATUS) && data.getDataEventType() == DataEventType.UPDATE) {
            Map<String, String> oldData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.OLD_DATA);
            Map<String, String> newData = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
            boolean isCancelled = "1".equals(newData.get("cancelled")) && (oldData.get("cancelled") == null || "0".equals(oldData.get("cancelled"))) &&
                    context.get(CTX_KEY_MY_NODE_ID).equals(newData.get("source_node_id"));
            String loadId = newData.get("load_id");
            if (isCancelled && loadId != null) {
                @SuppressWarnings("unchecked")
                List<Long> loadIds = (List<Long>) context.get(CTX_KEY_CANCEL_LOAD);
                if (loadIds == null) {
                    loadIds = new ArrayList<>();
                    context.put(CTX_KEY_CANCEL_LOAD, loadIds);
                }
                loadIds.add(Long.parseLong(loadId));
            }
        }
    }

    private boolean hasClientReloadListener(DataContext context) {
        Boolean hasListener = (Boolean) context.get(CTX_KEY_INITIAL_LOAD_LISTENER);
        if (hasListener == null) {
            hasListener = engine.getExtensionService().getExtensionPointList(IClientReloadListener.class).size() > 0;
            context.put(CTX_KEY_INITIAL_LOAD_LISTENER, hasListener);
        }
        return hasListener;
    }

    private boolean matchesTable(Table table, String tableSuffix) {
        if (table != null && table.getName() != null) {
            return table.getName().equalsIgnoreCase(TableConstants.getTableName(engine.getParameterService().getTablePrefix(), tableSuffix));
        } else {
            return false;
        }
    }

    @Override
    public void syncStarted(DataContext context) {
        putNodeIdentityIntoContext(context);
        putNodeSecurityIntoContext(context);
        helper.setSyncTriggersAllowed(context, engine.getParameterService().is(ParameterConstants.AUTO_SYNC_TRIGGERS) &&
                engine.getParameterService().is(ParameterConstants.AUTO_SYNC_TRIGGERS_AFTER_CONFIG_LOADED));
    }

    @Override
    public void syncEnded(DataContext context, List<IncomingBatch> batchesProcessed, Throwable ex) {
        helper.contextComplete(context);
        IParameterService parameterService = engine.getParameterService();
        INodeService nodeService = engine.getNodeService();
        @SuppressWarnings("unchecked")
        Set<Table> tables = (Set<Table>) context.remove(CTX_KEY_RESYNC_TABLE_NEEDED);
        if (tables != null && parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS)
                && (parameterService.is(ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD)
                        || nodeService.findNodeSecurity(nodeService.findIdentityNodeId(), true).hasInitialLoaded())
                && engine.getSymmetricDialect().getPlatform().equals(engine.getTargetDialect().getPlatform())) {
            engine.getTriggerRouterService().syncTriggers(new RelationsList(tables), false);
        }
        if (context.remove(CTX_KEY_INITIAL_LOAD_COMPLETED) != null) {
            long loadId = (long) context.remove(CTX_KEY_INITAL_LOAD_ID);
            log.info("Initial load ended for me, load ID {}", loadId);
            if (hasClientReloadListener(context)) {
                List<IClientReloadListener> listeners = engine.getExtensionService().getExtensionPointList(IClientReloadListener.class);
                for (IClientReloadListener listener : listeners) {
                    listener.reloadCompleted();
                }
            }
            if (parameterService.is(ParameterConstants.TRIGGER_CREATE_BEFORE_INITIAL_LOAD)) {
                TableReloadRequest currLoad = engine.getDataService().getTableReloadRequest(loadId);
                if (currLoad != null && currLoad.isCreateTable()) {
                    engine.getTriggerRouterService().syncTriggers();
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<Long> loadIds = (List<Long>) context.get(CTX_KEY_CANCEL_LOAD);
        String identityId = (String) context.get(CTX_KEY_MY_NODE_ID);
        if (loadIds != null && identityId != null) {
            for (Long loadId : loadIds) {
                TableReloadStatus status = engine.getDataService().getTableReloadStatusByLoadIdAndSourceNodeId(loadId, identityId);
                if (status != null) {
                    engine.getInitialLoadService().cancelLoad(status);
                }
            }
        }
    }

    @Override
    public void batchCommitted(DataContext context) {
        helper.contextCommitted(context);
        if (context.remove(CTX_KEY_CHANGED_NODE_SECURITY) != null) {
            putNodeSecurityIntoContext(context);
        }
        if (context.getBatch().getBatchId() == Constants.VIRTUAL_BATCH_FOR_REGISTRATION) {
            INodeService nodeService = engine.getNodeService();
            String nodeId = nodeService.findIdentityNodeId();
            Node sourceNode = nodeService.findNode(context.getBatch().getSourceNodeId());
            if (nodeId != null && sourceNode != null && Version.isOlderThanVersion(sourceNode.getSymmetricVersion(), "3.12.0")) {
                NodeSecurity security = nodeService.findNodeSecurity(nodeId);
                if (security != null && (security.isRegistrationEnabled() || security.getRegistrationTime() == null)) {
                    engine.getRegistrationService().markNodeAsRegistered(nodeId);
                }
            }
        }
    }

    protected void putNodeIdentityIntoContext(DataContext context) {
        context.put(CTX_KEY_MY_NODE_ID, engine.getNodeService().findIdentityNodeId());
    }

    protected void putNodeSecurityIntoContext(DataContext context) {
        String myNodeId = engine.getNodeService().findIdentityNodeId();
        if (myNodeId != null) {
            context.put(CTX_KEY_MY_NODE_SECURITY, engine.getNodeService().findNodeSecurity(myNodeId, true));
        }
    }
}
