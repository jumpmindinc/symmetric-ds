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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class RegistrationRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum RegistrationStatus {
        OK, RQ, RJ, RR, ER
    };

    public static final List<RegistrationStatus> incompleteStatuses = Arrays.asList(
            RegistrationStatus.RQ, RegistrationStatus.ER, RegistrationStatus.RJ);

    private String nodeGroupId;
    private String externalId;
    private RegistrationStatus status;
    private String hostName;
    private String ipAddress;
    private long attemptCount = 1;
    private String registeredNodeId;
    private String errorMessage;
    private Date createTime = new Date();
    private String lastUpdateBy = "engine";
    private Date lastUpdateTime = new Date();

    public RegistrationRequest() {
    }

    public RegistrationRequest(Node node, RegistrationStatus status,
            String hostName, String ipAddress) {
        this.nodeGroupId = node.getNodeGroupId();
        this.externalId = node.getExternalId();
        this.registeredNodeId = node.getNodeId();
        this.status = status;
        this.hostName = hostName == null ? "unknown" : hostName;
        setIpAddress(ipAddress);
    }

    public String getNodeGroupId() {
        return nodeGroupId;
    }

    public void setNodeGroupId(String nodeGroupId) {
        this.nodeGroupId = nodeGroupId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = StringUtils.left(ipAddress == null ? "unknown" : ipAddress,
                NodeHost.MAX_IP_ADDRESS_SIZE);
    }

    public long getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(long attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getRegisteredNodeId() {
        return registeredNodeId;
    }

    public void setRegisteredNodeId(String registeredNodeId) {
        this.registeredNodeId = registeredNodeId;
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

    public void setErrorMessage(String message) {
        this.errorMessage = message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((createTime == null) ? 0 : createTime.hashCode());
        result = prime * result + ((externalId == null) ? 0 : externalId.hashCode());
        result = prime * result + ((nodeGroupId == null) ? 0 : nodeGroupId.hashCode());
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
        RegistrationRequest other = (RegistrationRequest) obj;
        if (createTime == null) {
            if (other.createTime != null) {
                return false;
            }
        } else if (!createTime.equals(other.createTime)) {
            return false;
        }
        if (externalId == null) {
            if (other.externalId != null) {
                return false;
            }
        } else if (!externalId.equals(other.externalId)) {
            return false;
        }
        if (nodeGroupId == null) {
            if (other.nodeGroupId != null) {
                return false;
            }
        } else if (!nodeGroupId.equals(other.nodeGroupId)) {
            return false;
        }
        return true;
    }

    public void incrementAttemptsAndSetLatestMessage(RegistrationRequest priorRequest) {
        this.attemptCount = 1 + priorRequest.attemptCount;
        this.errorMessage = StringUtils.defaultIfEmpty(this.errorMessage, priorRequest.errorMessage);
    }

    public boolean isIncomplete() {
        return incompleteStatuses.contains(this.status);
    }
}
