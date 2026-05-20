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
package org.jumpmind.symmetric.io.stage;

import static org.jumpmind.symmetric.common.Constants.STAGING_CATEGORY_BULK_EXTRACT;
import static org.jumpmind.symmetric.common.Constants.STAGING_CATEGORY_BULK_LOAD;
import static org.jumpmind.symmetric.common.Constants.STAGING_CATEGORY_INCOMING;
import static org.jumpmind.symmetric.common.Constants.STAGING_CATEGORY_LOG_MINER;
import static org.jumpmind.symmetric.common.Constants.STAGING_CATEGORY_OUTGOING;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.IBatchStagingExtension;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchStagingManager extends FileSystemStagingManager {
    protected static final Logger log = LoggerFactory.getLogger(BatchStagingManager.class);
    ISymmetricEngine engine;

    public BatchStagingManager(ISymmetricEngine engine, String directory) {
        super(directory, engine.getParameterService().is(ParameterConstants.CLUSTER_LOCKING_ENABLED),
                engine.getParameterService().getLong(ParameterConstants.STAGING_LOW_SPACE_THRESHOLD_MEGABYTES, 0));
        this.engine = engine;
    }

    protected Map<String, Long> getBiggestBatchIds(Set<BatchId> batches) {
        Map<String, Long> biggest = new HashMap<String, Long>();
        for (BatchId batchId : batches) {
            Long batchNumber = biggest.get(batchId.getNodeId());
            if (batchNumber == null || batchNumber < batchId.getBatchId()) {
                biggest.put(batchId.getNodeId(), batchId.getBatchId());
            }
        }
        return biggest;
    }

    @Override
    public long clean(long ttlInMs) {
        boolean isLockAcquired = false;
        try {
            boolean clusterStagingEnabled = engine.getParameterService().is(ParameterConstants.CLUSTER_STAGING_ENABLED, false);
            if (clusterStagingEnabled) {
                if (engine.getClusterService().lock(ClusterConstants.STAGE_MANAGEMENT)) {
                    isLockAcquired = true;
                } else {
                    log.debug("Could not get a lock to run stage management");
                    return 0;
                }
            }
        } catch (Exception e) {
            // during setup or un-install, it's possible sym_lock table isn't available yet
        }
        try {
            long startTime = System.currentTimeMillis();
            boolean purgeBasedOnTTL = engine.getParameterService().is(ParameterConstants.STREAM_TO_FILE_PURGE_ON_TTL_ENABLED, false);
            boolean recordIncomingBatchesEnabled = engine.getIncomingBatchService().isRecordOkBatchesEnabled();
            long minTtlInMs = engine.getParameterService().getLong(ParameterConstants.STREAM_TO_FILE_MIN_TIME_TO_LIVE_MS, 600000);
            Set<Long> outgoingBatches = ttlInMs == 0 ? new HashSet<Long>() : new HashSet<Long>(engine.getOutgoingBatchService().getAllBatches());
            Set<BatchId> incomingBatches = ttlInMs == 0 ? new HashSet<BatchId>() : new HashSet<BatchId>(engine.getIncomingBatchService().getAllBatches());
            Map<String, Long> biggestIncomingByNode = getBiggestBatchIds(incomingBatches);
            StagingPurgeContext context = new StagingPurgeContext();
            context.putContextValue("startTime", startTime);
            context.putContextValue("purgeBasedOnTTL", purgeBasedOnTTL);
            context.putContextValue("recordIncomingBatchesEnabled", recordIncomingBatchesEnabled);
            context.putContextValue("minTtlInMs", minTtlInMs);
            context.putContextValue("outgoingBatches", outgoingBatches);
            context.putContextValue("incomingBatches", incomingBatches);
            context.putContextValue("biggestIncomingByNode", biggestIncomingByNode);
            IBatchStagingExtension ext = engine.getExtensionService().getExtensionPoint(IBatchStagingExtension.class);
            if (ext != null) {
                context.putContextValue("extension", ext);
                ext.beforeClean(context);
            }
            return super.clean(ttlInMs, context);
        } finally {
            if (isLockAcquired) {
                try {
                    engine.getClusterService().unlock(ClusterConstants.STAGE_MANAGEMENT);
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    protected boolean shouldCleanPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context) {
        if (context.getBoolean("purgeBasedOnTTL")) {
            return super.shouldCleanPath(resource, ttlInMs, context);
        }
        String[] path = resource.getPath().split("/");
        boolean resourceIsOld = (System.currentTimeMillis() - resource.getLastUpdateTime()) > ttlInMs;
        boolean resourceClearsMinTimeHurdle = resource.getLastUpdateTime() < context.getLong("startTime")
                && (System.currentTimeMillis() - resource.getLastUpdateTime()) > context.getLong("minTtlInMs");
        if (path[0].equals(STAGING_CATEGORY_OUTGOING)) {
            return shouldCleanOutgoingPath(resource, ttlInMs, context, path, resourceIsOld, resourceClearsMinTimeHurdle);
        } else if (path[0].equals(STAGING_CATEGORY_INCOMING)) {
            return shouldCleanIncomingPath(resource, ttlInMs, context, path, resourceIsOld, resourceClearsMinTimeHurdle);
        } else if (path[0].equals(STAGING_CATEGORY_LOG_MINER)) {
            return false;
        } else if (path[0].equals(STAGING_CATEGORY_BULK_LOAD) || path[0].equals(STAGING_CATEGORY_BULK_EXTRACT)) {
            return shouldCleanBulkPath(resource, ttlInMs, context, path, resourceIsOld, resourceClearsMinTimeHurdle);
        } else {
            IBatchStagingExtension ext = (IBatchStagingExtension) context.getContextValue("extension");
            if (ext != null && ext.isValidPath(path[0])) {
                return ext.shouldCleanPath(resource, ttlInMs, context, path, resourceIsOld, resourceClearsMinTimeHurdle);
            } else {
                log.warn("Unrecognized path: " + resource.getPath());
            }
        }
        return false;
    }

    protected boolean shouldCleanOutgoingPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context, String[] path,
            boolean resourceIsOld, boolean resourceClearsMinTimeHurdle) {
        @SuppressWarnings("unchecked")
        Set<Long> outgoingBatches = (Set<Long>) context.getContextValue("outgoingBatches");
        try {
            Long batchId = Long.valueOf(path[path.length - 1].replace("_filesync", ""));
            if ((resourceClearsMinTimeHurdle && !outgoingBatches.contains(batchId)) || ttlInMs == 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            if (resourceIsOld || ttlInMs == 0) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldCleanBulkPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context, String[] path,
            boolean resourceIsOld, boolean resourceClearsMinTimeHurdle) {
        @SuppressWarnings("unchecked")
        Set<Long> outgoingBatches = (Set<Long>) context.getContextValue("outgoingBatches");
        try {
            Long batchId = Long.valueOf(path[path.length - 1].replaceAll("[^0-9]", ""));
            if ((resourceClearsMinTimeHurdle && !outgoingBatches.contains(batchId)) || ttlInMs == 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            if (resourceIsOld || ttlInMs == 0) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldCleanIncomingPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context, String[] path, boolean resourceIsOld,
            boolean resourceClearsMinTimeHurdle) {
        @SuppressWarnings("unchecked")
        Set<BatchId> incomingBatches = (Set<BatchId>) context.getContextValue("incomingBatches");
        @SuppressWarnings("unchecked")
        Map<String, Long> biggestIncomingByNode = (Map<String, Long>) context.getContextValue("biggestIncomingByNode");
        boolean recordIncomingBatchesEnabled = context.getBoolean("recordIncomingBatchesEnabled");
        try {
            BatchId batchId = new BatchId(Long.valueOf(path[path.length - 1].replace("_filesync", "")), path[1]);
            Long biggestBatchId = biggestIncomingByNode.get(batchId.getNodeId());
            if ((recordIncomingBatchesEnabled && resourceClearsMinTimeHurdle && biggestBatchId != null
                    && biggestBatchId > batchId.getBatchId() && !incomingBatches.contains(batchId))
                    || (!recordIncomingBatchesEnabled && resourceIsOld) || ttlInMs == 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            if (resourceIsOld || ttlInMs == 0) {
                return true;
            }
        }
        return false;
    }
}
