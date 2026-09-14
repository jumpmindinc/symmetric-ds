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

public class ProcessInfoKey implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sourceNodeId;
    private String targetNodeId;
    private ProcessType processType;
    private String queue;

    public ProcessInfoKey(String sourceNodeId, String targetNodeId, ProcessType processType) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.processType = processType;
        this.queue = null;
    }

    public ProcessInfoKey(String sourceNodeId, String queue, String targetNodeId, ProcessType processType) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.processType = processType;
        this.queue = queue;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public ProcessType getProcessType() {
        return processType;
    }

    public String getQueue() {
        return queue;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((processType == null) ? 0 : processType.hashCode());
        result = prime * result + ((sourceNodeId == null) ? 0 : sourceNodeId.hashCode());
        result = prime * result + ((targetNodeId == null) ? 0 : targetNodeId.hashCode());
        result = prime * result + ((queue == null) ? 0 : queue.hashCode());
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
        ProcessInfoKey other = (ProcessInfoKey) obj;
        if (processType != other.processType)
            return false;
        if (sourceNodeId == null) {
            if (other.sourceNodeId != null)
                return false;
        } else if (!sourceNodeId.equals(other.sourceNodeId))
            return false;
        if (targetNodeId == null) {
            if (other.targetNodeId != null)
                return false;
        } else if (!targetNodeId.equals(other.targetNodeId))
            return false;
        if (queue == null) {
            if (other.queue != null)
                return false;
        } else if (!queue.equals(other.queue))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return String.format("processType=%s,sourceNodeId=%s,targetNodeId=%s,queue=%s", processType.toString(), sourceNodeId,
                targetNodeId, queue);
    }
}
