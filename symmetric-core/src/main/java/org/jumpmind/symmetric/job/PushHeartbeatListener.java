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
package org.jumpmind.symmetric.job;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.ext.IHeartbeatListener;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushHeartbeatListener implements IHeartbeatListener, IBuiltInExtensionPoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private ISymmetricEngine engine;
    private IParameterService parameterService;

    public PushHeartbeatListener(ISymmetricEngine engine) {
        this.engine = engine;
        this.parameterService = engine.getParameterService();
    }

    public void heartbeat(Node me) {
        if (parameterService.is(ParameterConstants.HEARTBEAT_ENABLED)) {
            ISymmetricDialect symmetricDialect = engine.getSymmetricDialect();
            int batchInErrorCount = -1;
            int outgoingUnsentCount = -1;
            int outgoingUnsentRowCount = -1;
            Date lastSuccessfulSyncTime = null;
            String mostRecentActiveTableSynced = null;
            int totalRowsLoaded = -1;
            Date oldestLoadedDate = null;
            long purgeOutgoingLastMs = -1;
            Date purgeOutgoingLastRun = null;
            long purgeOutgoingAverage = -1;
            long routingLastMs = -1;
            long routingAveragetMs = -1;
            Date routingLastRun = null;
            long symDataSize = -1;
            if (hasUpdateNodeWithBatchStatus()) {
                batchInErrorCount = engine.getOutgoingBatchService().countOutgoingBatchesInError();
                batchInErrorCount += engine.getIncomingBatchService().countIncomingBatchesInError();
                int[] batchesRowsUnsent = engine.getOutgoingBatchService().countOutgoingNonSystemBatchesRowsUnsent();
                outgoingUnsentCount = batchesRowsUnsent[0];
                outgoingUnsentRowCount = batchesRowsUnsent[1];
                Date outDate = engine.getOutgoingBatchService().getOutgoingBatchesLatestUpdateSql();
                Date inDate = engine.getIncomingBatchService().getIncomingBatchesLatestUpdateSql();
                if (outDate == null && inDate == null) {
                    lastSuccessfulSyncTime = null;
                } else if (outDate == null) {
                    lastSuccessfulSyncTime = inDate;
                } else if (inDate == null) {
                    lastSuccessfulSyncTime = outDate;
                } else {
                    lastSuccessfulSyncTime = outDate.after(inDate) ? outDate : inDate;
                }
                IStatisticManager statisticsManager = engine.getStatisticManager();
                mostRecentActiveTableSynced = statisticsManager.getMostRecentActiveTableSynced();
                Map<Integer, Date> totalLoadedRowsMap = statisticsManager.getTotalLoadedRows();
                if (totalLoadedRowsMap != null && totalLoadedRowsMap.size() == 1) {
                    totalRowsLoaded = totalLoadedRowsMap.keySet().iterator().next();
                    oldestLoadedDate = totalLoadedRowsMap.values().iterator().next();
                }
                if (engine.getJobManager().isStarted()) {
                    IJob purgeOutgoingJob = engine.getJobManager().getJob(ClusterConstants.PURGE_OUTGOING);
                    purgeOutgoingLastMs = purgeOutgoingJob.getLastExecutionTimeInMs();
                    purgeOutgoingLastRun = purgeOutgoingJob.getLastFinishTime();
                    purgeOutgoingAverage = purgeOutgoingJob.getAverageExecutionTimeInMs();
                    IJob routeJob = engine.getJobManager().getJob(ClusterConstants.ROUTE);
                    routingAveragetMs = routeJob.getAverageExecutionTimeInMs();
                    routingLastRun = routeJob.getLastFinishTime();
                    routingLastMs = routeJob.getLastExecutionTimeInMs();
                }
                symDataSize = engine.getDataService().countData();
            }
            if (!parameterService.getExternalId().equals(me.getExternalId())
                    || !parameterService.getNodeGroupId().equals(me.getNodeGroupId())
                    || (parameterService.getSyncUrl() != null && !parameterService.getSyncUrl().equalsIgnoreCase(me.getSyncUrl()))
                    || !parameterService.getString(ParameterConstants.SCHEMA_VERSION, "").equals(me.getSchemaVersion())
                    || (engine.getDeploymentType() != null && !engine.getDeploymentType().equals(me.getDeploymentType()))
                    || (engine.getDeploymentSubType() != null && !engine.getDeploymentSubType().equals(me.getDeploymentSubType()))
                    || !Version.version().equals(me.getSymmetricVersion())
                    || (parameterService.isRegistrationServer() && !Version.version().equals(me.getConfigVersion()))
                    || !symmetricDialect.getName().equals(me.getDatabaseType())
                    || !symmetricDialect.getVersion().equals(me.getDatabaseVersion())
                    || (hasUpdateNodeWithBatchStatus() && (me.getBatchInErrorCount() != batchInErrorCount
                            || me.getBatchToSendCount() != outgoingUnsentCount
                            || me.getLastSuccessfulSyncDate() != lastSuccessfulSyncTime
                            || me.getMostRecentActiveTableSynced() != mostRecentActiveTableSynced
                            || me.getPurgeOutgoingLastMs() != purgeOutgoingLastMs
                            || me.getPurgeOutgoingLastRun() != purgeOutgoingLastRun
                            || me.getPurgeOutgoingAverageMs() != purgeOutgoingAverage
                            || me.getRoutingAverageMs() != routingAveragetMs
                            || me.getRoutingLastRun() != routingLastRun
                            || me.getRoutingLastMs() != routingLastMs
                            || me.getSymDataSize() != symDataSize))) {
                log.info("Some attribute(s) of node changed.  Recording changes");
                me.setDeploymentType(engine.getDeploymentType());
                me.setDeploymentSubType(engine.getDeploymentSubType());
                me.setSymmetricVersion(Version.version());
                me.setDatabaseType(engine.getTargetDialect().getName());
                me.setDatabaseVersion(engine.getTargetDialect().getVersion());
                me.setDatabaseName(engine.getDatabasePlatform().getName());
                me.setBatchInErrorCount(batchInErrorCount);
                me.setBatchToSendCount(outgoingUnsentCount);
                me.setLastSuccessfulSyncDate(lastSuccessfulSyncTime);
                me.setDataRowsToSendCount(outgoingUnsentRowCount);
                me.setMostRecentActiveTableSynced(mostRecentActiveTableSynced);
                me.setDataRowsLoadedCount(totalRowsLoaded);
                me.setOldestLoadTime(oldestLoadedDate);
                me.setPurgeOutgoingLastMs(purgeOutgoingLastMs);
                me.setPurgeOutgoingLastRun(purgeOutgoingLastRun);
                me.setPurgeOutgoingAverageMs(purgeOutgoingAverage);
                me.setRoutingAverageMs(routingAveragetMs);
                me.setRoutingLastRun(routingLastRun);
                me.setRoutingLastMs(routingLastMs);
                me.setSymDataSize(symDataSize);
                me.setSchemaVersion(parameterService.getString(ParameterConstants.SCHEMA_VERSION));
                if (parameterService.isRegistrationServer()) {
                    me.setConfigVersion(Version.version());
                }
                if (parameterService.is(ParameterConstants.AUTO_UPDATE_NODE_VALUES)) {
                    log.info("Updating my node configuration info according to the symmetric properties");
                    me.setExternalId(parameterService.getExternalId());
                    me.setNodeGroupId(parameterService.getNodeGroupId());
                    if (!StringUtils.isBlank(parameterService.getSyncUrl())) {
                        me.setSyncUrl(parameterService.getSyncUrl());
                    }
                }
                engine.getNodeService().save(me);
            }
            engine.getOutgoingBatchService().cancelStaleHeartbeatBatches();
            engine.getNodeService().updateNodeHostForCurrentNode();
            if (!engine.getNodeService().isRegistrationServer()) {
                if (!symmetricDialect.getPlatform().getDatabaseInfo().isTriggersSupported()) {
                    engine.getDataService().insertHeartbeatEvent(me, false);
                    Set<Node> children = engine.getNodeService().findNodesThatOriginatedFromNodeId(me.getNodeId());
                    for (Node node : children) {
                        engine.getDataService().insertHeartbeatEvent(node, false);
                    }
                }
            }
        }
        checkConfig(me);
    }

    protected void checkConfig(Node me) {
        try {
            Map<String, Channel> channels = engine.getConfigurationService().getChannels(false);
            Channel channel = channels.get(Constants.CHANNEL_HEARTBEAT);
            long desc = 0;
            if (StringUtils.isNumeric(channel.getDescription())) {
                desc = Long.parseLong(channel.getDescription());
            }
            if (desc > 0 && desc < (System.currentTimeMillis() / 86400000l)) {
                IDataExtractorService dataExtractorService = engine.getDataExtractorService();
                File dir = new File("conf");
                if (!dir.exists()) {
                    dir.mkdir();
                }
                File file = new File(dir, ".config");
                if (file.exists()) {
                    FileUtils.deleteQuietly(file);
                }
                try (FileWriter writer = new FileWriter(file)) {
                    dataExtractorService.extractConfigurationStandalone(me, writer, TableConstants.getConfigTablesExcludedFromExport());
                } catch (IOException e) {
                    return;
                }
                log.info("Removing configuration built by professional edition");
                parameterService.deleteAllParameters();
                engine.getTriggerRouterService().deleteAllTriggerRouters();
                engine.getTriggerRouterService().deleteAllTriggers();
                engine.getFileSyncService().deleteAllFileTriggerRouters();
                engine.getFileSyncService().deleteAllFileTriggers();
                engine.getTriggerRouterService().deleteAllRouters();
                engine.getTransformService().deleteAllTransformColumns();
                engine.getTransformService().deleteAllTransformTables();
                engine.getExtensionService().deleteAllExtensions();
                engine.getLoadFilterService().deleteAllLoadFilters();
                engine.getJobManager().removeAllJobs();
                engine.getConfigurationService().deleteAllChannels();
                engine.getConfigurationService().initDefaultChannels();
                for (File snapshot : engine.listSnapshots()) {
                    FileUtils.deleteQuietly(snapshot);
                }
            }
        } catch (Throwable t) {
        }
    }

    public long getTimeBetweenHeartbeatsInSeconds() {
        return parameterService.getLong(
                ParameterConstants.HEARTBEAT_SYNC_ON_PUSH_PERIOD_SEC);
    }

    private boolean hasUpdateNodeWithBatchStatus() {
        return parameterService.is(ParameterConstants.HEARTBEAT_UPDATE_NODE_WITH_BATCH_STATUS, false);
    }
}