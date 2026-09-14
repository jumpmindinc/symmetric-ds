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

public class TableReloadRequestKey {
    protected String targetNodeId;
    protected String sourceNodeId;
    protected String triggerId;
    protected String routerId;
    protected String receivedFromNodeId;
    protected Date createTime;

    public TableReloadRequestKey(String targetNodeId, String sourceNodeId, String triggerId,
            String routerId, String receivedFromNodeId) {
        this.targetNodeId = targetNodeId;
        this.sourceNodeId = sourceNodeId;
        this.triggerId = triggerId;
        this.routerId = routerId;
        this.receivedFromNodeId = receivedFromNodeId;
    }

    public TableReloadRequestKey(String targetNodeId, String sourceNodeId, String triggerId,
            String routerId, Date createTime) {
        this.targetNodeId = targetNodeId;
        this.sourceNodeId = sourceNodeId;
        this.triggerId = triggerId;
        this.routerId = routerId;
        this.createTime = createTime;
    }

    public String getRouterId() {
        return routerId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setReceivedFromNodeId(String receivedFromNodeId) {
        this.receivedFromNodeId = receivedFromNodeId;
    }

    public String getReceivedFromNodeId() {
        return receivedFromNodeId;
    }

    public Date getCreateTime() {
        return createTime;
    }
}
