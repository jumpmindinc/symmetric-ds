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
package org.jumpmind.symmetric.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A composite parent for {@link Channel} and {@link NodeChannelControl}
 */
public class NodeChannel implements IModelObject {
    private static final long serialVersionUID = 1L;
    private Channel channel;
    private String nodeId;
    private Map<String, NodeChannelControl> nodeChannelControlMap = new HashMap<String, NodeChannelControl>();

    public NodeChannel() {
        this(new Channel());
    }

    public NodeChannel(String channelId) {
        this(new Channel(channelId, 0));
    }

    public NodeChannel(Channel channel) {
        this.channel = channel;
    }

    public String getChannelId() {
        return channel.getChannelId();
    }

    public void setDataEventActionShortName(String dataEventAction) {
        setDataEventAction(NodeGroupLinkAction.fromShortName(dataEventAction));
    }

    public String getDataEventActionShortName() {
        return channel.getDataEventAction() == null ? "" : channel.getDataEventAction().getShortName();
    }

    public void setDataEventAction(NodeGroupLinkAction dataEventAction) {
        channel.setDataEventAction(dataEventAction);
    }

    public NodeGroupLinkAction getDataEventAction() {
        return channel.getDataEventAction();
    }

    public int getMaxBatchSize() {
        return channel.getMaxBatchSize();
    }

    public void setMaxBatchSize(int maxBatchSize) {
        channel.setMaxBatchSize(maxBatchSize);
    }

    public int getMaxBatchToSend() {
        return channel.getMaxBatchToSend();
    }

    public void setMaxBatchToSend(int maxBatchToSend) {
        channel.setMaxBatchToSend(maxBatchToSend);
    }

    public void setMaxDataToRoute(int maxDataToRoute) {
        channel.setMaxDataToRoute(maxDataToRoute);
    }

    public int getMaxDataToRoute() {
        return channel.getMaxDataToRoute();
    }

    public void setUseOldDataToRoute(boolean useOldDataToRoute) {
        channel.setUseOldDataToRoute(useOldDataToRoute);
    }

    public boolean isUseOldDataToRoute() {
        return channel.isUseOldDataToRoute();
    }

    public void setUseRowDataToRoute(boolean useRowDataToRoute) {
        channel.setUseRowDataToRoute(useRowDataToRoute);
    }

    public boolean isUseRowDataToRoute() {
        return channel.isUseRowDataToRoute();
    }

    public void setUsePkDataToRoute(boolean usePkDataToRoute) {
        channel.setUsePkDataToRoute(usePkDataToRoute);
    }

    public boolean isUsePkDataToRoute() {
        return channel.isUsePkDataToRoute();
    }

    public int getProcessingOrder() {
        return channel.getProcessingOrder();
    }

    public String getBatchAlgorithm() {
        return channel.getBatchAlgorithm();
    }

    public void setBatchAlgorithm(String batchAlgorithm) {
        channel.setBatchAlgorithm(batchAlgorithm);
    }

    public void setEnabled(boolean enabled) {
        channel.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return channel.isEnabled();
    }

    public boolean isSuspendEnabled(String targetNodeId) {
        if (nodeChannelControlMap.containsKey(targetNodeId)) {
            return nodeChannelControlMap.get(targetNodeId).isSuspendEnabled();
        }
        return false;
    }

    public boolean isIgnoreEnabled(String targetNodeId) {
        if (nodeChannelControlMap.containsKey(targetNodeId)) {
            return nodeChannelControlMap.get(targetNodeId).isIgnoreEnabled();
        }
        return false;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
        for (NodeChannelControl nodeChannelControl : nodeChannelControlMap.values()) {
            nodeChannelControl.setNodeId(nodeId);
        }
    }

    public void setLastExtractTime(String targetNodeId, Date lastExtractedTime) {
        getNodeChannelControl(targetNodeId).setLastExtractTime(lastExtractedTime);
    }

    public Date getLastExtractTime(String targetNodeId) {
        if (nodeChannelControlMap.containsKey(targetNodeId)) {
            return nodeChannelControlMap.get(targetNodeId).getLastExtractTime();
        }
        return null;
    }

    public void setIgnoreEnabled(String targetNodeId, boolean ignored) {
        getNodeChannelControl(targetNodeId).setIgnoreEnabled(ignored);
    }

    public void setProcessingOrder(int priority) {
        channel.setProcessingOrder(priority);
    }

    public void setChannelId(String id) {
        channel.setChannelId(id);
        for (NodeChannelControl nodeChannelControl : nodeChannelControlMap.values()) {
            nodeChannelControl.setChannelId(id);
        }
    }

    public void setLastUpdateTime(Date date) {
        channel.setLastUpdateTime(date);
    }

    public void setCreateTime(Date date) {
        channel.setCreateTime(date);
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        channel.setLastUpdateBy(lastUpdateBy);
    }

    public void setSuspendEnabled(String targetNodeId, boolean suspended) {
        getNodeChannelControl(targetNodeId).setSuspendEnabled(suspended);
    }

    public Channel getChannel() {
        return channel;
    }

    public NodeChannelControl getNodeChannelControl(String targetNodeId) {
        if (nodeChannelControlMap.containsKey(targetNodeId)) {
            return nodeChannelControlMap.get(targetNodeId);
        }
        NodeChannelControl nodeChannelControl = new NodeChannelControl();
        nodeChannelControl.setNodeId(getNodeId());
        nodeChannelControl.setTargetNodeId(targetNodeId);
        nodeChannelControl.setChannelId(getChannelId());
        nodeChannelControlMap.put(targetNodeId, nodeChannelControl);
        return nodeChannelControl;
    }

    public Map<String, NodeChannelControl> getNodeChannelControlMap() {
        return nodeChannelControlMap;
    }

    public long getExtractPeriodMillis() {
        return channel.getExtractPeriodMillis();
    }

    public void setExtractPeriodMillis(long extractPeriodMillis) {
        channel.setExtractPeriodMillis(extractPeriodMillis);
    }

    public void setContainsBigLob(boolean containsBigLobs) {
        this.channel.setContainsBigLob(containsBigLobs);
    }

    public boolean isContainsBigLob() {
        return this.channel.isContainsBigLob();
    }

    public void setDataLoaderType(String type) {
        channel.setDataLoaderType(type);
    }

    public String getDataLoaderType() {
        return channel.getDataLoaderType();
    }

    public void setReloadFlag(boolean value) {
        this.channel.setReloadFlag(value);
    }

    public boolean isReloadFlag() {
        return this.channel.isReloadFlag();
    }

    public void setFileSyncFlag(boolean value) {
        this.channel.setFileSyncFlag(value);
    }

    public boolean isFileSyncFlag() {
        return this.channel.isFileSyncFlag();
    }

    public Date getCreateTime() {
        return this.channel.getCreateTime();
    }

    public String getLastUpdateBy() {
        return this.channel.getLastUpdateBy();
    }

    public Date getLastUpdateTime() {
        return this.channel.getLastUpdateTime();
    }

    public void setQueue(String queue) {
        this.channel.setQueue(queue);
    }

    public String getQueue() {
        return this.channel.getQueue();
    }

    public BigDecimal getMaxKBytesPerSecond() {
        return this.channel.getMaxKBytesPerSecond();
    }

    public void setMaxKBytesPerSecond(BigDecimal maxKBytesPerSecond) {
        this.channel.setMaxKBytesPerSecond(maxKBytesPerSecond);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((channel == null) ? 0 : channel.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NodeChannel other = (NodeChannel) obj;
        if (channel == null) {
            if (other.channel != null)
                return false;
        } else if (!channel.equals(other.channel))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Channel: '" + getChannelId() + "' Node: '" + getNodeId() + "' " + super.toString();
    }
}