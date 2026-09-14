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
package org.jumpmind.symmetric.job;

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_5_MINUTES;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.NetworkedNode;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.transport.TransportUtils;
import org.jumpmind.symmetric.web.WebConstants;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/*
 * Related to hybrid-pull, this job is responsible for sending a light-weight HTTP message 
 * to nodes that pull from this node, indicating how many batches are available to pull.
 */
public class ReportStatusJob extends AbstractJob {
    private static Map<String, Integer> lastBatchCountPerChannel = Collections.synchronizedMap(new HashMap<String, Integer>());

    public ReportStatusJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.REPORT_STATUS, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults()
                .schedule(EVERY_5_MINUTES)
                .enabled(false)
                .description("Related to hybrid-pull");
    }

    @Override
    public void doJob(boolean force) throws Exception {
        NetworkedNode remote = engine.getNodeService().getRootNetworkedNode();
        Node identity = engine.getNodeService().findIdentity();
        if (remote.getNode().getNodeId().equals(identity.getNodeId())) {
            log.debug("Skipping report status job because this node is the root node. identity={}, remote={}", identity, remote);
            return;
        }
        Map<String, Integer> batchCountPerChannel = engine.getOutgoingBatchService().countOutgoingBatchesPendingByChannel(remote.getNode().getNodeId());
        log.debug("identity={} batchCountPerChannel='{}', lastBatchCountPerChannel='{}'",
                identity, batchCountPerChannel, lastBatchCountPerChannel);
        if (force || shouldSendStatus(batchCountPerChannel)) {
            Map<String, String> requestParams = new HashMap<String, String>();
            requestParams.put(WebConstants.BATCH_TO_SEND_COUNT, TransportUtils.toCSV(batchCountPerChannel));
            engine.getTransportManager().sendStatusRequest(identity, requestParams);
            updateLastSentStatus(batchCountPerChannel);
        }
    }

    protected boolean shouldSendStatus(Map<String, Integer> batchCountPerChannel) {
        if (batchCountPerChannel == null || batchCountPerChannel.isEmpty()) {
            return false;
        }
        if (lastBatchCountPerChannel.equals(batchCountPerChannel)) {
            return false;
        }
        if (engine.getParameterService().is(ParameterConstants.HYBRID_PUSH_PULL_BUFFER_STATUS_UPDATES)) {
            for (String channelId : batchCountPerChannel.keySet()) {
                Integer lastCount = lastBatchCountPerChannel.get(channelId);
                if (lastCount == null) {
                    lastCount = Integer.valueOf(0);
                }
                Integer currentCount = batchCountPerChannel.get(channelId);
                if (lastCount.equals(Integer.valueOf(0)) && !lastCount.equals(currentCount)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    protected void updateLastSentStatus(Map<String, Integer> batchesToSendByChannel) {
        lastBatchCountPerChannel.clear();
        lastBatchCountPerChannel.putAll(batchesToSendByChannel);
    }
}
