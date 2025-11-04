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

import java.io.Serializable;
import java.util.Date;

/**
 * 
 */
public class NodeChannelControl implements Serializable {
    private static final long serialVersionUID = 1L;
    private String nodeId = null;
    private String targetNodeId = null;
    private String channelId = null;
    private boolean ignoreEnabled = false;
    private boolean suspendEnabled = false;
    private Date lastExtractTime = null;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public boolean isIgnoreEnabled() {
        return ignoreEnabled;
    }

    public void setIgnoreEnabled(boolean ignored) {
        this.ignoreEnabled = ignored;
    }

    public boolean isSuspendEnabled() {
        return suspendEnabled;
    }

    public void setSuspendEnabled(boolean suspended) {
        this.suspendEnabled = suspended;
    }

    public Date getLastExtractTime() {
        return lastExtractTime;
    }

    public void setLastExtractTime(Date lastExtractedTime) {
        this.lastExtractTime = lastExtractedTime;
    }
}