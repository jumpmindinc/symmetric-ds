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

import java.util.Date;

public class NodeGroupLink implements IModelObject, Comparable<NodeGroupLink> {
    private static final long serialVersionUID = 1L;
    private String sourceNodeGroupId;
    private String targetNodeGroupId;
    private NodeGroupLinkAction dataEventAction = NodeGroupLinkAction.W;
    private boolean syncConfigEnabled = true;
    private boolean syncSqlEnabled = true;
    private boolean isReversible;
    private Date createTime;
    private Date lastUpdateTime;
    private String lastUpdateBy;

    public NodeGroupLink() {
    }

    public NodeGroupLink(String sourceNodeGroupId, String targetNodeGroupId) {
        this.sourceNodeGroupId = sourceNodeGroupId;
        this.targetNodeGroupId = targetNodeGroupId;
    }

    public NodeGroupLink(String sourceNodeGroupId, String targetNodeGroupId, NodeGroupLinkAction action) {
        this(sourceNodeGroupId, targetNodeGroupId);
        this.dataEventAction = action;
    }

    public NodeGroupLinkAction getDataEventAction() {
        return dataEventAction;
    }

    public void setDataEventAction(NodeGroupLinkAction dataEventAction) {
        this.dataEventAction = dataEventAction;
    }

    public void setSyncConfigEnabled(boolean syncConfigEnabled) {
        this.syncConfigEnabled = syncConfigEnabled;
    }

    public boolean isSyncConfigEnabled() {
        return syncConfigEnabled;
    }

    public void setSyncSqlEnabled(boolean syncSqlEnabled) {
        this.syncSqlEnabled = syncSqlEnabled;
    }

    public boolean isSyncSqlEnabled() {
        return syncSqlEnabled;
    }

    public String getSourceNodeGroupId() {
        return sourceNodeGroupId;
    }

    public void setSourceNodeGroupId(String sourceNodeGroupId) {
        this.sourceNodeGroupId = sourceNodeGroupId;
    }

    public String getTargetNodeGroupId() {
        return targetNodeGroupId;
    }

    public void setTargetNodeGroupId(String targetNodeGroupId) {
        this.targetNodeGroupId = targetNodeGroupId;
    }

    public boolean isReversible() {
        return isReversible;
    }

    public void setReversible(boolean isReversible) {
        this.isReversible = isReversible;
    }

    @Override
    public Date getCreateTime() {
        return createTime;
    }

    @Override
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    @Override
    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    @Override
    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @Override
    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public String toString() {
        return sourceNodeGroupId + " " + dataEventAction + " " + targetNodeGroupId;
    }

    @Override
    public int compareTo(NodeGroupLink o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sourceNodeGroupId == null) ? 0 : sourceNodeGroupId.hashCode());
        result = prime * result + ((targetNodeGroupId == null) ? 0 : targetNodeGroupId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        NodeGroupLink other = (NodeGroupLink) obj;
        if (sourceNodeGroupId == null) {
            if (other.sourceNodeGroupId != null) {
                return false;
            }
        } else if (!sourceNodeGroupId.equals(other.sourceNodeGroupId)) {
            return false;
        }
        if (targetNodeGroupId == null) {
            if (other.targetNodeGroupId != null) {
                return false;
            }
        } else if (!targetNodeGroupId.equals(other.targetNodeGroupId)) {
            return false;
        }
        return true;
    }
}