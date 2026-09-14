/**
] * Licensed to JumpMind Inc under one or more contributor
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;

/**
 * Definition of a channel and it's priority. A channel is a group of tables that get synchronized together.
 */
public class Channel implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    private String channelId;
    private int processingOrder;
    private int maxBatchSize = 10000;
    private int maxBatchToSend = 10;
    private int maxDataToRoute = 500000;
    private BigDecimal maxKBytesPerSecond = BigDecimal.ZERO;
    private boolean enabled = true;
    private boolean useOldDataToRoute = true;
    private boolean useRowDataToRoute = true;
    private boolean usePkDataToRoute = true;
    private boolean containsBigLob = false;
    private String batchAlgorithm = "default";
    private long extractPeriodMillis = 0;
    private String dataLoaderType = "default";
    private Date createTime;
    private Date lastUpdateTime;
    private String lastUpdateBy;
    private boolean reloadFlag = false;
    private boolean fileSyncFlag = false;
    private String queue = "default";
    private NodeGroupLinkAction dataEventAction = null;
    private String description;

    public Channel() {
    }

    public Channel(String id, int processingOrder) {
        this.channelId = id;
        this.processingOrder = processingOrder;
    }

    public Channel(String id, int processingOrder, int maxBatchSize, int maxBatchToSend, boolean enabled,
            long extractPeriodMillis, boolean containsBigLobs, String batchAlgorithm, boolean reloadFlag, boolean filesyncFlag) {
        this(id, processingOrder, maxBatchSize, maxBatchToSend, enabled, extractPeriodMillis, containsBigLobs, reloadFlag, filesyncFlag);
        this.batchAlgorithm = batchAlgorithm;
    }

    public Channel(String id, int processingOrder, int maxBatchSize, int maxBatchToSend, boolean enabled,
            long extractPeriodMillis, boolean containsBigLobs, boolean reloadFlag, boolean filesyncFlag) {
        this(id, processingOrder, maxBatchSize, maxBatchToSend, enabled, extractPeriodMillis, containsBigLobs);
        this.reloadFlag = reloadFlag;
        this.fileSyncFlag = filesyncFlag;
    }

    public Channel(String id, int processingOrder, int maxBatchSize, int maxBatchToSend, boolean enabled,
            long extractPeriodMillis, boolean containsBigLobs) {
        this(id, processingOrder);
        this.maxBatchSize = maxBatchSize;
        this.maxBatchToSend = maxBatchToSend;
        this.enabled = enabled;
        this.extractPeriodMillis = extractPeriodMillis;
        this.containsBigLob = containsBigLobs;
        this.queue = "default";
    }

    public Channel(String id, int processingOrder, int maxBatchSize, int maxBatchToSend, boolean enabled,
            long extractPeriodMillis, boolean containsBigLobs, String queue) {
        this(id, processingOrder, maxBatchSize, maxBatchToSend, enabled, extractPeriodMillis, containsBigLobs);
        this.queue = queue;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String id) {
        this.channelId = id;
    }

    public int getProcessingOrder() {
        return processingOrder;
    }

    public void setProcessingOrder(int priority) {
        this.processingOrder = priority;
    }

    public void setMaxDataToRoute(int maxDataToRoute) {
        this.maxDataToRoute = maxDataToRoute;
    }

    public int getMaxDataToRoute() {
        return maxDataToRoute;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxNumberOfEvents) {
        this.maxBatchSize = maxNumberOfEvents;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxBatchToSend() {
        return maxBatchToSend;
    }

    public void setMaxBatchToSend(int maxBatchToSend) {
        this.maxBatchToSend = maxBatchToSend;
    }

    /**
     * Check to see if this channel id matches one of the channels in the collection
     * 
     * @return true if a match is found
     */
    public boolean isInList(Collection<? extends NodeChannel> channels) {
        return findInList(channels) != null;
    }

    public Channel findInList(Collection<? extends NodeChannel> channels) {
        if (channels != null) {
            for (NodeChannel channel : channels) {
                if (channel.getChannelId().equals(channelId)) {
                    return channel.getChannel();
                }
            }
        }
        return null;
    }

    public void setBatchAlgorithm(String batchAlgorithm) {
        this.batchAlgorithm = batchAlgorithm;
    }

    public String getBatchAlgorithm() {
        return batchAlgorithm;
    }

    public long getExtractPeriodMillis() {
        return extractPeriodMillis;
    }

    public void setExtractPeriodMillis(long extractPeriodMillis) {
        this.extractPeriodMillis = extractPeriodMillis;
    }

    public void setUseOldDataToRoute(boolean useOldDataToRoute) {
        this.useOldDataToRoute = useOldDataToRoute;
    }

    public boolean isUseOldDataToRoute() {
        return useOldDataToRoute;
    }

    public void setUseRowDataToRoute(boolean useRowDataToRoute) {
        this.useRowDataToRoute = useRowDataToRoute;
    }

    public boolean isUseRowDataToRoute() {
        return useRowDataToRoute;
    }

    public void setUsePkDataToRoute(boolean usePkDataToRoute) {
        this.usePkDataToRoute = usePkDataToRoute;
    }

    public boolean isUsePkDataToRoute() {
        return usePkDataToRoute;
    }

    public void setContainsBigLob(boolean containsBigLobs) {
        this.containsBigLob = containsBigLobs;
    }

    public boolean isContainsBigLob() {
        return containsBigLob;
    }

    public void setDataLoaderType(String dataloader) {
        this.dataLoaderType = dataloader;
    }

    public String getDataLoaderType() {
        return dataLoaderType;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setFileSyncFlag(boolean filesyncFlag) {
        this.fileSyncFlag = filesyncFlag;
    }

    public boolean isFileSyncFlag() {
        return fileSyncFlag;
    }

    public void setReloadFlag(boolean reloadFlag) {
        this.reloadFlag = reloadFlag;
    }

    public boolean isReloadFlag() {
        return reloadFlag;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public BigDecimal getMaxKBytesPerSecond() {
        return maxKBytesPerSecond;
    }

    public void setMaxKBytesPerSecond(BigDecimal maxKBytesPerSecond) {
        this.maxKBytesPerSecond = maxKBytesPerSecond;
    }

    public NodeGroupLinkAction getDataEventAction() {
        return dataEventAction;
    }

    public void setDataEventAction(NodeGroupLinkAction dataEventAction) {
        this.dataEventAction = dataEventAction;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int hashCode() {
        if (channelId != null) {
            return channelId.hashCode();
        } else {
            return super.hashCode();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (channelId != null) {
            if (obj instanceof Channel) {
                return channelId.equals(((Channel) obj).channelId);
            } else {
                return false;
            }
        } else {
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        if (channelId != null) {
            return channelId;
        } else {
            return super.toString();
        }
    }

    public Channel copy() {
        Channel channel = null;
        try {
            channel = (Channel) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        return channel;
    }
}