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
package org.jumpmind.symmetric.model;

import java.util.Date;

import org.jumpmind.symmetric.io.data.Batch;

/**
 * Used for tracking the sending a collection of data to a node in the system. A new outgoing_batch is created and given a status of 'NE'. After sending the
 * outgoing_batch to its target node, the status becomes 'SE'. The node responds with either a success status of 'OK' or an error status of 'ER'. An error while
 * sending to the node also results in an error status of 'ER' regardless of whether the node sends that acknowledgement.
 */
public class OutgoingBatch extends AbstractBatch {
    private static final long serialVersionUID = 1L;
    private boolean extractJobFlag;
    private Date extractStartTime;
    private Date transferStartTime;
    private Date loadStartTime;
    private Integer threadId;
    private Date dataMinCreateTime;
    private Date dataMaxCreateTime;

    public OutgoingBatch() {
        setStatus(Status.RT);
    }

    public OutgoingBatch(String nodeId, String channelId, Status status) {
        setNodeId(nodeId);
        setChannelId(channelId);
        setStatus(status);
        setCreateTime(new Date());
        setBulkLoaderFlag(isBulkLoaderFlag());
    }

    @Override
    public Date getLastUpdatedTime() {
        if (super.getLastUpdatedTime() == null) {
            return new Date();
        } else {
            return super.getLastUpdatedTime();
        }
    }

    @Override
    public String getStagedLocation() {
        return Batch.getStagedLocation(isCommonFlag(), getNodeId(), getBatchId());
    }

    @Override
    public String toString() {
        return getNodeBatchId();
    }

    public void setExtractJobFlag(boolean extractJobFlag) {
        this.extractJobFlag = extractJobFlag;
    }

    public boolean isExtractJobFlag() {
        return extractJobFlag;
    }

    public Date getExtractStartTime() {
        return extractStartTime;
    }

    public void setExtractStartTime(Date extractStartTime) {
        this.extractStartTime = extractStartTime;
    }

    public Date getTransferStartTime() {
        return transferStartTime;
    }

    public void setTransferStartTime(Date transferStartTime) {
        this.transferStartTime = transferStartTime;
    }

    public Date getLoadStartTime() {
        return loadStartTime;
    }

    public void setLoadStartTime(Date loadStartTime) {
        this.loadStartTime = loadStartTime;
    }

    public Integer getThreadId() {
        return threadId;
    }

    public void setThreadId(Integer threadId) {
        this.threadId = threadId;
    }

    public Date getDataMinCreateTime() {
        return dataMinCreateTime;
    }

    public void setDataMinCreateTime(Date dataMinCreateTime) {
        this.dataMinCreateTime = dataMinCreateTime;
    }

    public Date getDataMaxCreateTime() {
        return dataMaxCreateTime;
    }

    public void setDataMaxCreateTime(Date dataMaxCreateTime) {
        this.dataMaxCreateTime = dataMaxCreateTime;
    }
}