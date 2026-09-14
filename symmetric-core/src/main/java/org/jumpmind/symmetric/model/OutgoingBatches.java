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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.AbstractBatch.Status;

/**
 * A container for {@link OutgoingBatch}s.
 */
public class OutgoingBatches implements Serializable {
    private static final long serialVersionUID = 1L;
    List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
    Set<NodeChannel> activeChannels = new HashSet<NodeChannel>();
    Set<String> activeChannelIds = new HashSet<String>();

    public OutgoingBatches(List<OutgoingBatch> batches) {
        this.batches = batches;
    }

    public OutgoingBatches() {
    }

    public boolean containsBatches() {
        return batches != null && batches.size() > 0;
    }

    public Set<NodeChannel> getActiveChannels() {
        return activeChannels;
    }

    public void addActiveChannel(NodeChannel nodeChannel) {
        activeChannels.add(nodeChannel);
        activeChannelIds.add(nodeChannel.getChannelId());
    }

    public void setActiveChannels(Set<NodeChannel> activeChannels) {
        this.activeChannels = activeChannels;
        activeChannelIds = new HashSet<String>();
        for (NodeChannel nodeChannel : activeChannels) {
            activeChannelIds.add(nodeChannel.getChannelId());
        }
    }

    public List<OutgoingBatch> getBatches() {
        return batches;
    }

    public void setBatches(List<OutgoingBatch> batches) {
        this.batches = batches;
    }

    /**
     * Removes all batches associated with the provided channel from this object.
     * 
     * @param channel
     *            - channel for which corresponding batches are removed
     * @return A list of the batches removed
     */
    public List<OutgoingBatch> filterBatchesForChannel(Channel channel) {
        List<OutgoingBatch> filtered = getBatchesForChannel(channel);
        batches.removeAll(filtered);
        return filtered;
    }

    public int countBatches(boolean includeOnlyErrors) {
        int count = 0;
        if (batches != null) {
            for (OutgoingBatch batch : batches) {
                if (includeOnlyErrors && batch.isErrorFlag()) {
                    count++;
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    public List<OutgoingBatch> filterBatchesForChannel(String channelId) {
        List<OutgoingBatch> filtered = getBatchesForChannel(channelId);
        batches.removeAll(filtered);
        return filtered;
    }

    public List<OutgoingBatch> filterBatchesForChannels(Set<String> channels) {
        List<OutgoingBatch> filtered = getBatchesForChannels(channels);
        batches.removeAll(filtered);
        return filtered;
    }

    public List<OutgoingBatch> filterSuspendedBatches(NodeChannels nodeChannels) {
        List<OutgoingBatch> filtered = getFilteredBatches(nodeChannels::isBatchSuspended);
        batches.removeAll(filtered);
        return filtered;
    }

    public List<OutgoingBatch> filterIgnoredBatches(NodeChannels nodeChannels) {
        List<OutgoingBatch> filtered = getFilteredBatches(nodeChannels::isBatchIgnored);
        batches.removeAll(filtered);
        return filtered;
    }

    private List<OutgoingBatch> getFilteredBatches(Function<OutgoingBatch, Boolean> filterFunction) {
        List<OutgoingBatch> filtered = new ArrayList<OutgoingBatch>();
        for (OutgoingBatch batch : batches) {
            if (filterFunction.apply(batch)) {
                filtered.add(batch);
            }
        }
        return filtered;
    }

    public void removeNonLoadBatches() {
        HashSet<String> channelsWithRequestedLoad = new HashSet<String>();
        for (Iterator<OutgoingBatch> iterator = batches.iterator(); iterator.hasNext();) {
            OutgoingBatch b = iterator.next();
            if (!b.isLoadFlag() && !b.getChannelId().contentEquals(Constants.CHANNEL_CONFIG) &&
                    !b.getChannelId().contentEquals(Constants.CHANNEL_SYSTEM) &&
                    !b.getChannelId().contentEquals(Constants.CHANNEL_HEARTBEAT) && !b.getChannelId().contentEquals(Constants.CHANNEL_MONITOR)) {
                iterator.remove();
            } else {
                if (Status.RQ.equals(b.getStatus())) {
                    channelsWithRequestedLoad.add(b.getChannelId());
                }
                if (channelsWithRequestedLoad.contains(b.getChannelId())) {
                    iterator.remove();
                }
            }
        }
    }

    public void removeThreadChannelBatches(String channelId) {
        for (Iterator<OutgoingBatch> iterator = batches.iterator(); iterator.hasNext();) {
            OutgoingBatch b = iterator.next();
            if (!b.getChannelId().equals(channelId)) {
                iterator.remove();
            }
        }
    }

    public boolean containsLoadBatches() {
        for (OutgoingBatch b : batches) {
            if (b.isLoadFlag()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsBatchesInError() {
        for (OutgoingBatch b : batches) {
            if (b.isErrorFlag()) {
                return true;
            }
        }
        return false;
    }

    public List<OutgoingBatch> getBatchesForChannel(Channel channel) {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        if (channel != null) {
            batchList = getBatchesForChannel(channel.getChannelId());
        }
        return batchList;
    }

    public List<OutgoingBatch> getBatchesForChannel(String channelId) {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        if (channelId != null) {
            for (OutgoingBatch batch : batches) {
                if (channelId.equals(batch.getChannelId())) {
                    batchList.add(batch);
                }
            }
        }
        return batchList;
    }

    public List<OutgoingBatch> getBatchesForChannels(Set<String> channelIds) {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        if (channelIds != null) {
            for (OutgoingBatch batch : batches) {
                if (channelIds.contains(batch.getChannelId())) {
                    batchList.add(batch);
                }
            }
        }
        return batchList;
    }

    /**
     * Removes all batches that are not associated with an 'activeChannel'.
     * 
     * @return List of batches that were filtered
     */
    public List<OutgoingBatch> filterBatchesForInactiveChannels() {
        List<OutgoingBatch> filtered = new ArrayList<OutgoingBatch>();
        for (OutgoingBatch batch : batches) {
            if (!activeChannelIds.contains(batch.getChannelId())) {
                filtered.add(batch);
            }
        }
        batches.removeAll(filtered);
        return filtered;
    }

    public void sortChannels(List<NodeChannel> channels) {
        final HashMap<String, Date> errorChannels = new HashMap<String, Date>();
        for (OutgoingBatch batch : batches) {
            if (batch.isErrorFlag()) {
                Date date = errorChannels.get(batch.getChannelId());
                if (date == null || batch.getLastUpdatedTime().compareTo(date) > 0) {
                    errorChannels.put(batch.getChannelId(), batch.getLastUpdatedTime());
                }
            }
        }
        Collections.sort(channels, new Comparator<NodeChannel>() {
            public int compare(NodeChannel b1, NodeChannel b2) {
                boolean isError1 = errorChannels.containsKey(b1.getChannelId());
                boolean isError2 = errorChannels.containsKey(b2.getChannelId());
                if (!isError1 && !isError2) {
                    return b1.getProcessingOrder() < b2.getProcessingOrder() ? -1 : 1;
                } else if (isError1 && isError2) {
                    return errorChannels.get(b1.getChannelId()).compareTo(
                            errorChannels.get(b2.getChannelId()));
                } else if (!isError1 && isError2) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
        for (NodeChannel nodeChannel : channels) {
            long extractPeriodMillis = nodeChannel.getExtractPeriodMillis();
            Date lastExtractedTime = nodeChannel.getLastExtractTime(nodeChannel.getNodeId());
            if ((extractPeriodMillis < 1)
                    || (lastExtractedTime == null)
                    || (Calendar.getInstance().getTimeInMillis() - lastExtractedTime.getTime() >= extractPeriodMillis)) {
                addActiveChannel(nodeChannel);
            }
        }
        filterBatchesForInactiveChannels();
    }
}