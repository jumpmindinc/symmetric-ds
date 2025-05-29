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
package org.jumpmind.symmetric.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.IReloadQueueThreadAssigner;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ReadyChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutgoingBatchCache {
    private final Logger log = LoggerFactory.getLogger(OutgoingBatchCache.class);
    private IParameterService parameterService;
    private IConfigurationService configurationService;
    private IOutgoingBatchService outgoingBatchService;
    private IReloadQueueThreadAssigner extension;
    private Semaphore cacheLock = new Semaphore(1);
    volatile private Map<String, Collection<String>> readyQueuesCache = new HashMap<>();
    volatile private long readyQueuesCacheTime;

    public OutgoingBatchCache(ISymmetricEngine engine) {
        parameterService = engine.getParameterService();
        configurationService = engine.getConfigurationService();
        outgoingBatchService = engine.getOutgoingBatchService();
        extension = engine.getExtensionService().getExtensionPoint(IReloadQueueThreadAssigner.class);
    }

    public Map<String, Collection<String>> getReadyQueues(boolean refreshCache) {
        checkReadyQueuesCache(refreshCache);
        return readyQueuesCache;
    }

    public void flushReadyQueuesCache() {
        if (cacheLock.tryAcquire()) {
            readyQueuesCacheTime = 0;
            cacheLock.release();
        }
    }

    protected void checkReadyQueuesCache(boolean refreshCache) {
        long readyQueuesCacheTimeoutInMs = parameterService.getLong(ParameterConstants.CACHE_TIMEOUT_READY_QUEUE_IN_MS, 5000);
        if (System.currentTimeMillis() - readyQueuesCacheTime >= readyQueuesCacheTimeoutInMs || readyQueuesCache == null || refreshCache) {
            if (cacheLock.tryAcquire()) {
                try {
                    populateReadyQueuesCache(readyQueuesCacheTimeoutInMs);
                    readyQueuesCacheTime = System.currentTimeMillis();
                } catch (Exception e) {
                    log.error("Failed to retrieve ready queues", e);
                } finally {
                    cacheLock.release();
                }
            }
        }
    }

    protected void populateReadyQueuesCache(long readyQueuesCacheTimeoutInMs) {
        Map<String, Channel> channelMap = configurationService.getChannels(false);
        long ts = System.currentTimeMillis();
        Map<String, ReadyChannels> readyChannelMap = outgoingBatchService.getReadyChannelsFromDb();
        Map<String, Collection<String>> readyQueueMap = new HashMap<>();
        for (Map.Entry<String, ReadyChannels> entry : readyChannelMap.entrySet()) {
            String nodeId = entry.getKey();
            ReadyChannels readyChannels = entry.getValue();
            for (String channelId : readyChannels.getChannelIds()) {
                Channel channel = channelMap.get(channelId);
                if (channel != null) {
                    Collection<String> readyQueues = readyQueueMap.get(nodeId);
                    if (readyQueues == null) {
                        readyQueues = new HashSet<>();
                        readyQueueMap.put(nodeId, readyQueues);
                    }
                    if (extension != null && Constants.QUEUE_RELOAD.equals(channel.getQueue()) && readyChannels.getThreadIdCount() > 0) {
                        for (Integer threadId : readyChannels.getThreadIds()) {
                            readyQueues.add(channel.getQueue() + Constants.DELIMITER_QUEUE_THREAD + threadId);
                        }
                    } else {
                        readyQueues.add(channel.getQueue());
                    }
                }
            }
        }
        long queryTime = System.currentTimeMillis() - ts;
        if (queryTime > readyQueuesCacheTimeoutInMs) {
            log.warn("Query time of {} ms exceeded cache time of {} ms for ready queues.  This means the query may run on the database constantly.",
                    queryTime, readyQueuesCacheTimeoutInMs);
        }
        readyQueuesCache = readyQueueMap;
    }
}
