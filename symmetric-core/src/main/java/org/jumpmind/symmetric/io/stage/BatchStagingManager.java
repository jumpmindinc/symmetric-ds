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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.ext.IBatchStagingExtension;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.IStreamCipherContext;
import org.jumpmind.symmetric.staging.api.IStreamCipherProvider;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.api.StagingOptions;
import org.jumpmind.symmetric.staging.api.StreamCipherRegistry;
import org.jumpmind.symmetric.staging.factory.DefaultStagingFactory;
import org.jumpmind.symmetric.staging.factory.StagingParameterNames;
import org.jumpmind.symmetric.staging.factory.StagingParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchStagingManager implements IStagingManager {
    private static final Logger log = LoggerFactory.getLogger(BatchStagingManager.class);
    private static final String LEGACY_DEFAULT_CIPHER = "aes";
    protected final ISymmetricEngine engine;
    protected final org.jumpmind.symmetric.staging.api.IStagingManager delegate;
    protected final IStreamCipherProvider cipher;
    protected final long lockTtlMs;

    public BatchStagingManager(ISymmetricEngine engine, String directory) {
        this.engine = engine;
        IParameterService params = engine.getParameterService();
        Map<String, String> paramMap = collectStagingParams(params, directory);
        StagingConfig config = new StagingParameterResolver(paramMap, System.getenv()).buildConfig();
        this.delegate = new DefaultStagingFactory().create(config);
        this.cipher = resolveCipher(params, engine);
        this.lockTtlMs = params.getLong(ParameterConstants.LOCK_TIMEOUT_MS, 300_000L);
        if (cipher != null) {
            log.info("Staging encryption enabled with cipher '{}'", cipher.getCipherId());
        }
    }

    @Override
    public IStagedResource find(Object... path) {
        return adapt(delegate.find(path));
    }

    @Override
    public IStagedResource find(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return adapt(delegate.find(StagingKey.ofPath(path)));
    }

    @Override
    public IStagedResource create(Object... path) {
        return adapt(delegate.create(StagingOptions.defaults(), path));
    }

    @Override
    public IStagedResource createScratchResource(Object... path) {
        return adapt(delegate.createScratchResource(StagingOptions.plain(), path));
    }

    @Override
    public IStagingLock acquireFileLock(String serverInfo, Object... path) {
        return delegate.acquireLock(serverInfo, lockTtlMs, path);
    }

    @Override
    public Set<String> getResourceReferences() {
        Set<String> result = new LinkedHashSet<>();
        for (StagingKey key : delegate.listResources()) {
            result.add(key.asPath());
        }
        return result;
    }

    @Override
    public File getStagingDirectory() {
        if (delegate instanceof org.jumpmind.symmetric.staging.fs.FileSystemStagingManager) {
            return ((org.jumpmind.symmetric.staging.fs.FileSystemStagingManager) delegate).getStagingDirectory();
        }
        return delegate.getScratchDirectory();
    }

    @Override
    public File getScratchDirectory() {
        return delegate.getScratchDirectory();
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
        } catch (Exception ex) {
            log.debug("Cluster lock unavailable during stage management: {}", ex.getMessage());
        }
        try {
            long startTime = System.currentTimeMillis();
            IParameterService params = engine.getParameterService();
            boolean purgeBasedOnTTL = params.is(ParameterConstants.STREAM_TO_FILE_PURGE_ON_TTL_ENABLED, false);
            boolean recordIncomingBatchesEnabled = engine.getIncomingBatchService().isRecordOkBatchesEnabled();
            long minTtlInMs = params.getLong(ParameterConstants.STREAM_TO_FILE_MIN_TIME_TO_LIVE_MS, 600_000L);
            Set<Long> outgoingBatches = ttlInMs == 0 ? new HashSet<>() : new HashSet<>(engine.getOutgoingBatchService().getAllBatches());
            Set<BatchId> incomingBatches = ttlInMs == 0 ? new HashSet<>() : new HashSet<>(engine.getIncomingBatchService().getAllBatches());
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
            long freedBytes = 0L;
            int purgedCount = 0;
            for (StagingKey key : delegate.listResources()) {
                org.jumpmind.symmetric.staging.api.IStagedResource delegateResource = delegate.find(key);
                if (delegateResource == null) {
                    continue;
                }
                LegacyStagedResourceAdapter resource = new LegacyStagedResourceAdapter(delegateResource, cipher);
                if (!shouldCleanPath(resource, ttlInMs, context)) {
                    continue;
                }
                long size = resource.getSize();
                if (resource.delete()) {
                    freedBytes += size;
                    purgedCount++;
                    if (resource.isMemoryResource()) {
                        context.incrementPurgedMemoryCount();
                        context.addPurgedMemoryBytes(size);
                    } else {
                        context.incrementPurgedFileCount();
                        context.addPurgedFileBytes(size);
                    }
                }
            }
            if (purgedCount > 0) {
                log.info("Purged {} staged resources, freed {} bytes", purgedCount, freedBytes);
            }
            return freedBytes;
        } finally {
            if (isLockAcquired) {
                try {
                    engine.getClusterService().unlock(ClusterConstants.STAGE_MANAGEMENT);
                } catch (Exception ex) {
                    log.debug("Failed to release cluster lock: {}", ex.getMessage());
                }
            }
        }
    }

    protected boolean shouldCleanPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context) {
        if (context.getBoolean("purgeBasedOnTTL")) {
            boolean resourceIsOld = (System.currentTimeMillis() - resource.getLastUpdateTime()) > ttlInMs;
            return resourceIsOld && resource.getState() == IStagedResource.State.DONE && !resource.isInUse();
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

    protected boolean shouldCleanOutgoingPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context,
            String[] path, boolean resourceIsOld, boolean resourceClearsMinTimeHurdle) {
        @SuppressWarnings("unchecked")
        Set<Long> outgoingBatches = (Set<Long>) context.getContextValue("outgoingBatches");
        try {
            Long batchId = Long.valueOf(path[path.length - 1].replace("_filesync", ""));
            if ((resourceClearsMinTimeHurdle && !outgoingBatches.contains(batchId)) || ttlInMs == 0) {
                return true;
            }
        } catch (NumberFormatException ex) {
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
        } catch (NumberFormatException ex) {
            if (resourceIsOld || ttlInMs == 0) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldCleanIncomingPath(IStagedResource resource, long ttlInMs, StagingPurgeContext context,
            String[] path, boolean resourceIsOld, boolean resourceClearsMinTimeHurdle) {
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
        } catch (NumberFormatException ex) {
            if (resourceIsOld || ttlInMs == 0) {
                return true;
            }
        }
        return false;
    }

    protected Map<String, Long> getBiggestBatchIds(Set<BatchId> batches) {
        Map<String, Long> biggest = new HashMap<>();
        for (BatchId batchId : batches) {
            Long batchNumber = biggest.get(batchId.getNodeId());
            if (batchNumber == null || batchNumber < batchId.getBatchId()) {
                biggest.put(batchId.getNodeId(), batchId.getBatchId());
            }
        }
        return biggest;
    }

    private IStagedResource adapt(org.jumpmind.symmetric.staging.api.IStagedResource delegateResource) {
        return delegateResource == null ? null : new LegacyStagedResourceAdapter(delegateResource, cipher);
    }

    private static Map<String, String> collectStagingParams(IParameterService params, String directory) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put(StagingParameterNames.STAGING_DIR, directory);
        copyParam(params, paramMap, StagingParameterNames.STAGING_SCRATCH_DIR);
        copyParam(params, paramMap, StagingParameterNames.STAGING_PROVIDER_TYPE);
        copyParam(params, paramMap, StagingParameterNames.STAGING_PROVIDER_URL);
        copyParam(params, paramMap, StagingParameterNames.STAGING_PROVIDER_BUCKET);
        copyParam(params, paramMap, StagingParameterNames.STAGING_ACCOUNT_TYPE);
        copyParam(params, paramMap, StagingParameterNames.STAGING_ACCOUNT_KEY);
        copyParam(params, paramMap, StagingParameterNames.STAGING_ACCOUNT_SECRET);
        long lowSpaceMb = params.getLong(ParameterConstants.STAGING_LOW_SPACE_THRESHOLD_MEGABYTES, 0L);
        paramMap.put(StagingParameterNames.STAGING_LOW_SPACE_THRESHOLD_MEGABYTES, String.valueOf(lowSpaceMb));
        return paramMap;
    }

    private static void copyParam(IParameterService params, Map<String, String> paramMap, String name) {
        String value = params.getString(name);
        if (value != null && !value.isBlank()) {
            paramMap.put(name, value);
        }
    }

    private static IStreamCipherProvider resolveCipher(IParameterService params, ISymmetricEngine engine) {
        String cipherId = params.getString("staging.encryption.cipher", null);
        if (cipherId == null || cipherId.isBlank()) {
            if (params.is(ServerConstants.STREAM_TO_FILE_ENCRYPT_ENABLED, false)) {
                cipherId = LEGACY_DEFAULT_CIPHER;
            } else {
                return null;
            }
        }
        IStreamCipherContext context = new IStreamCipherContext() {
            @Override
            public org.jumpmind.security.ISecurityService getSecurityService() {
                return engine.getSecurityService();
            }
        };
        return StreamCipherRegistry.lookup(cipherId, context);
    }
}
