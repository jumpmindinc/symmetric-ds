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

import static org.apache.commons.lang3.StringUtils.isNumeric;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a node who has registered for sync updates.
 */
public class Node implements Serializable, Comparable<Node> {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(Node.class);
    private int MAX_VERSION_SIZE = 50;
    private String nodeId;
    private String nodeGroupId;
    private String externalId;
    private String syncUrl;
    /**
     * Record the version of the schema. This is recorded and managed by the sync software.
     */
    private String schemaVersion;
    private String configVersion;
    /**
     * Record the type of database the node hosts.
     */
    private String databaseType;
    private String databaseName;
    private String symmetricVersion = Version.version();
    /**
     * Get the version of the database the node hosts.
     */
    private String databaseVersion;
    private boolean syncEnabled = true;
    private String createdAtNodeId;
    private int batchToSendCount;
    private int batchInErrorCount;
    private String deploymentType;
    private String deploymentSubType;
    private int[] symmetricVersionParts;
    private Date lastSuccessfulSyncDate;
    private String mostRecentActiveTableSynced;
    private int dataRowsToSendCount;
    private int dataRowsLoadedCount;
    private Date oldestLoadTime;
    private long purgeOutgoingLastMs;
    private Date purgeOutgoingLastRun;
    private long purgeOutgoingAverageMs;
    private long routingAverageMs;
    private Date routingLastRun;
    private long routingLastMs;
    private long symDataSize;

    public Node() {
    }

    public Node(String nodeId, String nodeGroupId) {
        this.nodeId = nodeId;
        this.externalId = nodeId;
        this.nodeGroupId = nodeGroupId;
    }

    public Node(Properties properties) {
        setNodeGroupId(properties.getProperty(ParameterConstants.NODE_GROUP_ID));
        setExternalId(properties.getProperty(ParameterConstants.EXTERNAL_ID));
        setSyncUrl(properties.getProperty(ParameterConstants.SYNC_URL));
        setSchemaVersion(properties.getProperty(ParameterConstants.SCHEMA_VERSION));
        this.deploymentSubType = SymmetricUtils.getDeploymentSubType(properties);
    }

    public Node(IParameterService parameterService, ISymmetricDialect symmetricDialect, String databaseName) {
        setNodeGroupId(parameterService.getNodeGroupId());
        setExternalId(parameterService.getExternalId());
        setDatabaseType(symmetricDialect.getName());
        setDatabaseVersion(symmetricDialect.getVersion());
        setDatabaseName(databaseName);
        setSyncUrl(parameterService.getSyncUrl());
        setSchemaVersion(parameterService.getString(ParameterConstants.SCHEMA_VERSION));
        setConfigVersion(Version.version());
    }

    public Node(String nodeId, String syncURL, String version) {
        this.nodeId = nodeId;
        this.syncUrl = syncURL;
        this.schemaVersion = version;
    }

    public boolean equals(Object n) {
        return n != null && n instanceof Node && nodeId != null && nodeId.equals(((Node) n).getNodeId());
    }

    @Override
    public int hashCode() {
        return nodeId != null ? nodeId.hashCode() : super.hashCode();
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getSyncUrl() {
        return syncUrl;
    }

    public void setSyncUrl(String syncURL) {
        this.syncUrl = syncURL;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String version) {
        // abbreviate because we do not control the version
        this.schemaVersion = StringUtils.abbreviate(version, MAX_VERSION_SIZE);
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getDatabaseVersion() {
        return databaseVersion;
    }

    public void setDatabaseVersion(String databaseVersion) {
        this.databaseVersion = databaseVersion;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String domainId) {
        this.externalId = domainId;
    }

    public String getNodeGroupId() {
        return nodeGroupId;
    }

    public void setNodeGroupId(String domainName) {
        this.nodeGroupId = domainName;
    }

    public String getSymmetricVersion() {
        return symmetricVersion;
    }

    public int[] getSymmetricVersionParts() {
        if (symmetricVersionParts == null) {
            if (StringUtils.isEmpty(symmetricVersion) || symmetricVersion.equals("development")) {
                symmetricVersionParts = null;
            } else {
                symmetricVersionParts = Version.parseVersion(symmetricVersion);
            }
        }
        return symmetricVersionParts;
    }

    public void setSymmetricVersion(String symmetricVersion) {
        this.symmetricVersion = symmetricVersion;
        this.symmetricVersionParts = null;
    }

    public String toString() {
        return nodeGroupId + ":" + externalId + ":" + (nodeId == null ? "?" : nodeId);
    }

    public String getCreatedAtNodeId() {
        return createdAtNodeId;
    }

    public void setCreatedAtNodeId(String createdByNodeId) {
        this.createdAtNodeId = createdByNodeId;
    }

    public void setBatchInErrorCount(int batchesInErrorCount) {
        this.batchInErrorCount = batchesInErrorCount;
    }

    public int getBatchInErrorCount() {
        return batchInErrorCount;
    }

    public void setBatchToSendCount(int batchesToSendCount) {
        this.batchToSendCount = batchesToSendCount;
    }

    public int getBatchToSendCount() {
        return batchToSendCount;
    }

    public void setDeploymentType(String deploymentType) {
        this.deploymentType = deploymentType;
    }

    public String getDeploymentType() {
        return deploymentType;
    }

    public String getDeploymentSubType() {
        return deploymentSubType;
    }

    public void setDeploymentSubType(String deploymentSubType) {
        this.deploymentSubType = deploymentSubType;
    }

    public Date getLastSuccessfulSyncDate() {
        return lastSuccessfulSyncDate;
    }

    public void setLastSuccessfulSyncDate(Date lastSuccessfulSyncDate) {
        this.lastSuccessfulSyncDate = lastSuccessfulSyncDate;
    }

    public int getDataRowsToSendCount() {
        return dataRowsToSendCount;
    }

    public void setDataRowsToSendCount(int dataRowsToSendCount) {
        this.dataRowsToSendCount = dataRowsToSendCount;
    }

    public int getDataRowsLoadedCount() {
        return dataRowsLoadedCount;
    }

    public void setDataRowsLoadedCount(int dataRowsLoadedCount) {
        this.dataRowsLoadedCount = dataRowsLoadedCount;
    }

    public Date getOldestLoadTime() {
        return oldestLoadTime;
    }

    public void setOldestLoadTime(Date oldestLoadTime) {
        this.oldestLoadTime = oldestLoadTime;
    }

    public long getPurgeOutgoingLastMs() {
        return purgeOutgoingLastMs;
    }

    public void setPurgeOutgoingLastMs(long purgeOutgoingLastMs) {
        this.purgeOutgoingLastMs = purgeOutgoingLastMs;
    }

    public Date getPurgeOutgoingLastRun() {
        return purgeOutgoingLastRun;
    }

    public void setPurgeOutgoingLastRun(Date purgeOutgoingLastRun) {
        this.purgeOutgoingLastRun = purgeOutgoingLastRun;
    }

    public long getRoutingAverageMs() {
        return routingAverageMs;
    }

    public void setRoutingAverageMs(long routingAverageMs) {
        this.routingAverageMs = routingAverageMs;
    }

    public Date getRoutingLastRun() {
        return routingLastRun;
    }

    public void setRoutingLastRun(Date routingLastRun) {
        this.routingLastRun = routingLastRun;
    }

    public long getSymDataSize() {
        return symDataSize;
    }

    public void setSymDataSize(long symDataSize) {
        this.symDataSize = symDataSize;
    }

    public long getPurgeOutgoingAverageMs() {
        return purgeOutgoingAverageMs;
    }

    public void setPurgeOutgoingAverageMs(long purgeOutgoingAverageMs) {
        this.purgeOutgoingAverageMs = purgeOutgoingAverageMs;
    }

    public long getRoutingLastMs() {
        return routingLastMs;
    }

    public void setRoutingLastMs(long routingLastMs) {
        this.routingLastMs = routingLastMs;
    }

    public boolean requires13Compatiblity() {
        if (symmetricVersion != null) {
            if (symmetricVersion.equals("development")) {
                return false;
            }
            try {
                int[] currentVersion = getSymmetricVersionParts();
                return currentVersion != null && currentVersion.length > 0 && currentVersion[0] <= 1;
            } catch (Exception ex) {
                log.warn(
                        "Could not parse the version {} for node {}.  Setting backwards compatibility mode to true",
                        symmetricVersion, nodeId);
                return true;
            }
        }
        return false;
    }

    public boolean allowCaptureTimeInProtocol() {
        return isVersionGreaterThanOrEqualTo(3, 12);
    }

    public boolean isVersionGreaterThanOrEqualTo(int... targetVersion) {
        if (symmetricVersion != null) {
            if (symmetricVersion.equals("development")) {
                return true;
            }
            int[] currentVersion = getSymmetricVersionParts();
            if (currentVersion == null) {
                return false;
            }
            for (int i = 0; i < currentVersion.length; i++) {
                int j = currentVersion[i];
                if (targetVersion.length > i) {
                    if (j > targetVersion[i]) {
                        return true;
                    } else if (j < targetVersion[i]) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int compareTo(Node other) {
        String otherNodeId = other.getNodeId();
        if (nodeId != null && otherNodeId != null) {
            if (isNumeric(otherNodeId) && isNumeric(nodeId)) {
                return new BigDecimal(nodeId).compareTo(new BigDecimal(otherNodeId));
            } else {
                return nodeId.compareTo(otherNodeId);
            }
        } else {
            return 0;
        }
    }

    public String getMostRecentActiveTableSynced() {
        return mostRecentActiveTableSynced == null ? "" : mostRecentActiveTableSynced;
    }

    public void setMostRecentActiveTableSynced(String mostRecentActiveTableSynced) {
        this.mostRecentActiveTableSynced = mostRecentActiveTableSynced;
    }
}