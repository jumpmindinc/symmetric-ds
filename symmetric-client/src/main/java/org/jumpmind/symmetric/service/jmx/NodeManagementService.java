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
package org.jumpmind.symmetric.service.jmx;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.util.IPooledDataSource;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.NodeConnectionStatistics;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "The management interface for a node")
public class NodeManagementService implements IBuiltInExtensionPoint, ISymmetricEngineAware {
    final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine;

    public NodeManagementService() {
    }

    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @ManagedAttribute(description = "Checks if SymmetricDS has been started.")
    public boolean isStarted() {
        if (engine != null) {
            return engine.isStarted();
        } else {
            return false;
        }
    }

    @ManagedOperation(description = "Start the SymmetricDS engine")
    public boolean start() {
        try {
            if (engine != null) {
                engine.getParameterService().saveParameter(engine.getParameterService().getExternalId(), engine.getParameterService().getNodeGroupId(),
                        ParameterConstants.AUTO_START_ENGINE, "true", Constants.SYSTEM_USER);
                return engine.start();
            } else {
                return false;
            }
        } catch (Exception ex) {
            log.error("", ex);
            return false;
        }
    }

    @ManagedOperation(description = "Stop the SymmetricDS engine")
    public void stop() {
        try {
            if (engine != null) {
                log.info("JMX is stopping the following engine: {}", engine.getEngineName());
                engine.stop();
                engine.getParameterService().saveParameter(engine.getParameterService().getExternalId(), engine.getParameterService().getNodeGroupId(),
                        ParameterConstants.AUTO_START_ENGINE, "false", Constants.SYSTEM_USER);
            }
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    @ManagedOperation(description = "Run the outgoing purge process")
    public void purge() {
        log.info("JMX is running the outgoing purge process from the following engine: {} ", engine.getEngineName());
        engine.getPurgeService().purgeOutgoing(true);
    }

    @ManagedOperation(description = "Create a snapshot of the current state of the system")
    public String snapshot() {
        File file = engine.snapshot(null);
        if (file != null) {
            return file.getAbsolutePath();
        } else {
            return null;
        }
    }

    @ManagedOperation(description = "Force the cached objects to be reread from the database the next time they are accessed")
    public void clearCaches() {
        engine.clearCaches();
    }

    @ManagedOperation(description = "Synchronize the triggers")
    public void syncTriggers() {
        log.info("JMX is running syncTriggers from the following engine: {} ", engine.getEngineName());
        engine.getTriggerRouterService().syncTriggers();
    }

    @ManagedAttribute(
            description = "Get the number of current connections allowed to this "
                    + "instance of the node via HTTP.  If this value is 20, then 20 concurrent push"
                    + " clients and 20 concurrent pull clients will be allowed")
    public int getConcurrentWorkersMax() {
        return engine.getParameterService().getInt(ParameterConstants.CONCURRENT_WORKERS);
    }

    @ManagedAttribute(description = "Get the number of active connections to this node via HTTP")
    public int getConcurrentWorkersActive() {
        if (engine != null) {
            int available = engine.getNodeCommunicationService().getAvailableThreads(CommunicationType.PUSH);
            available += engine.getNodeCommunicationService().getAvailableThreads(CommunicationType.PULL);
            return engine.getParameterService().getInt(ParameterConstants.CONCURRENT_WORKERS) - available;
        }
        return 0;
    }

    @ManagedOperation(description = "Get connection statistics about indivdual nodes")
    public String showNodeConcurrencyStatisticsAsText() {
        String lineFeed = "\n";
        if (engine.getParameterService().getString(ParameterConstants.JMX_LINE_FEED).equals("html")) {
            lineFeed = "</br>";
        }
        Map<String, Map<String, NodeConnectionStatistics>> stats = engine
                .getConcurrentConnectionManager().getNodeConnectionStatisticsByPoolByNodeId();
        StringBuilder out = new StringBuilder();
        for (String pool : stats.keySet()) {
            out.append("-------------------------------------------------------------------------------------------------------------------------------");
            out.append(lineFeed);
            out.append("  CONNECTION TYPE: ");
            out.append(pool);
            out.append(lineFeed);
            out.append("-------------------------------------------------------------------------------------------------------------------------------");
            out.append(lineFeed);
            out.append("             NODE ID             LAST CONNECT TIME      NUMBER OF CONNECTIONS     NUMBER OF REJECTIONS       AVG CONNECTED TIME");
            out.append(lineFeed);
            out.append("-------------------------------------------------------------------------------------------------------------------------------");
            out.append(lineFeed);
            Map<String, NodeConnectionStatistics> nodeStats = stats.get(pool);
            for (String nodeId : nodeStats.keySet()) {
                NodeConnectionStatistics nodeStat = nodeStats.get(nodeId);
                out.append(StringUtils.leftPad(nodeId, 20));
                out.append(StringUtils.leftPad(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                                .format(new Date(nodeStat.getLastConnectionTimeMs())), 30));
                out.append(StringUtils.leftPad(Long.toString(nodeStat.getTotalConnectionCount()),
                        27));
                out.append(StringUtils.leftPad(Integer.toString(nodeStat.getNumOfRejections()), 25));
                out.append(StringUtils.leftPad(
                        NumberFormat.getIntegerInstance().format(
                                nodeStat.getTotalConnectionTimeMs()
                                        / nodeStat.getTotalConnectionCount()), 25));
            }
            out.append(lineFeed);
        }
        return out.toString();
    }

    @ManagedOperation(description = "Clean up both incoming and outgoing resources that are older than the passed in number of milliseconds")
    @ManagedOperationParameters({ @ManagedOperationParameter(
            name = "timeToLiveInMS",
            description = "The number of milliseconds old a resource should be before it is cleaned up") })
    public long cleanStaging(long timeToLiveInMS) {
        return engine.getStagingManager().clean(timeToLiveInMS);
    }

    @ManagedAttribute(
            description = "Get a list of nodes that have been added to the white list, a list of node ids that always get through the concurrency manager.")
    public String getNodesInWhiteList() {
        StringBuilder ret = new StringBuilder();
        String[] list = engine.getConcurrentConnectionManager().getWhiteList();
        for (String string : list) {
            ret.append(string);
            ret.append(",");
        }
        return ret.length() > 0 ? ret.substring(0, ret.length() - 1) : "";
    }

    @ManagedOperation(description = "Add a node id to the list of nodes that will always get through the concurrency manager")
    @ManagedOperationParameters({ @ManagedOperationParameter(name = "nodeId", description = "The node id to add to the white list") })
    public void addNodeToWhiteList(String nodeId) {
        log.info("JMX is adding node: {} to the white list from the following engine: {} ", nodeId, engine.getEngineName());
        engine.getConcurrentConnectionManager().addToWhitelist(nodeId);
    }

    @ManagedOperation(description = "Remove a node id to the list of nodes that will always get through the concurrency manager")
    @ManagedOperationParameters({ @ManagedOperationParameter(name = "nodeId", description = "The node id to remove from the white list") })
    public void removeNodeFromWhiteList(String nodeId) {
        log.info("JMX is removing node: {} from the white list from the following engine: {} ", nodeId, engine.getEngineName());
        engine.getConcurrentConnectionManager().removeFromWhiteList(nodeId);
    }

    @ManagedAttribute(
            description = "Configure the number of connections allowed to this node."
                    + "  If the value is set to zero you are effectively disabling your transport"
                    + " (wihch can be useful for maintainance")
    public void setConcurrentWorkersMax(int value) {
        log.info("JMX is setting the http.concurrent.workers.max to {} parameter from the following engine: {} ", value, engine.getEngineName());
        engine.getParameterService().saveParameter(ParameterConstants.CONCURRENT_WORKERS, value, "jmx");
    }

    @ManagedAttribute(description = "The group this node belongs to")
    public String getNodeGroupId() {
        return engine.getParameterService().getNodeGroupId();
    }

    @ManagedAttribute(description = "An external name given to this SymmetricDS node")
    public String getExternalId() {
        return engine.getParameterService().getExternalId();
    }

    @ManagedAttribute(description = "The node id given to this SymmetricDS node")
    public String getNodeId() {
        Node node = engine.getNodeService().findIdentity();
        if (node != null) {
            return node.getNodeId();
        } else {
            return "?";
        }
    }

    @ManagedAttribute(description = "Whether the basic DataSource is being used as the default datasource.")
    public boolean isBasicDataSource() {
        DataSource dataSource = engine.getDataSource();
        return dataSource instanceof BasicDataSource;
    }

    @ManagedAttribute(description = "Show the number of active connections in the pool")
    public int getDatabaseConnectionsActive() {
        IPooledDataSource pooled = IPooledDataSource.of(engine.getDataSource());
        return pooled != null ? pooled.getNumActive() : -1;
    }

    @ManagedAttribute(description = "Show the max number of total connections in the pool")
    public int getDatabaseConnectionsMax() {
        IPooledDataSource pooled = IPooledDataSource.of(engine.getDataSource());
        return pooled != null ? pooled.getMaxTotal() : -1;
    }

    @ManagedOperation(description = "Check to see if the external id is registered")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeGroupId", description = "The node group id for a node"),
            @ManagedOperationParameter(name = "externalId", description = "The external id for a node") })
    public boolean isExternalIdRegistered(String nodeGroupdId, String externalId) {
        return engine.getNodeService().isExternalIdRegistered(nodeGroupdId, externalId);
    }

    @ManagedOperation(description = "Emergency remove all locks (if left abandoned on a cluster)")
    public void clearAllLocks() {
        engine.getClusterService().clearAllLocks();
    }

    @ManagedOperation(
            description = "Check to see if the initial load for a node id is complete.  This method will throw an exception if the load error'd out or was never started.")
    @ManagedOperationParameters({ @ManagedOperationParameter(name = "nodeId", description = "The node id") })
    public boolean areAllLoadBatchesComplete(String nodeId) {
        return engine.getOutgoingBatchService().areAllLoadBatchesComplete(nodeId);
    }

    @ManagedOperation(description = "Enable or disable synchronization completely for a node")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeId", description = "The node to enable or disable"),
            @ManagedOperationParameter(name = "syncEnabled", description = "true is enabled, false is disabled") })
    public boolean setSyncEnabledForNode(String nodeId, boolean syncEnabled) {
        Node node = engine.getNodeService().findNode(nodeId);
        if (node != null) {
            log.info("JMX is setting the SyncEnabled flag to {} from the following engine: {} ", syncEnabled, engine.getEngineName());
            node.setSyncEnabled(syncEnabled);
            engine.getNodeService().save(node);
            return true;
        } else {
            return false;
        }
    }

    @ManagedOperation(description = "Extract multiple batches to a file for a time range")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "fileName", description = "The file to write the batch output to"),
            @ManagedOperationParameter(name = "nodeId", description = "The target node id whose batches need extracted"),
            @ManagedOperationParameter(name = "startTime", description = "The start time range to extract.  The format is yyyy-MM-dd hh:mm"),
            @ManagedOperationParameter(name = "endTime", description = "The start time range to extract.  The format is yyyy-MM-dd hh:mm"),
            @ManagedOperationParameter(name = "channelIdList", description = "A comma separated list of channels to extract") })
    public boolean extractBatcheRange(String fileName, String nodeId, String startTime,
            String endTime, String channelIdList) {
        File file = new File(fileName);
        file.getParentFile().mkdirs();
        Date startBatchTime = FormatUtils.parseDate(startTime, FormatUtils.TIMESTAMP_PATTERNS);
        Date endBatchTime = FormatUtils.parseDate(endTime, FormatUtils.TIMESTAMP_PATTERNS);
        String[] channelIds = channelIdList.split(",");
        IDataExtractorService dataExtractorService = engine.getDataExtractorService();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            dataExtractorService.extractBatchRange(writer, nodeId, startBatchTime, endBatchTime,
                    channelIds);
            return true;
        } catch (Exception ex) {
            log.error("Failed to write batch range to file", ex);
            return false;
        }
    }

    @ManagedOperation(description = "Enable or disable a channel for a specific external id")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "ignore", description = "Set to true to enable and false to disable"),
            @ManagedOperationParameter(name = "channelId", description = "The channel id to enable or disable"),
            @ManagedOperationParameter(name = "nodeGroupId", description = "The node group id for the source node"),
            @ManagedOperationParameter(name = "externalId", description = "The external id for the source node"),
            @ManagedOperationParameter(name = "nodeGroupId", description = "The node group id for the target node"),
            @ManagedOperationParameter(name = "externalId", description = "The external id for the target node") })
    public void ignoreNodeChannelForExternalId(boolean ignore, String channelId,
            String nodeGroupId, String externalId, String targetNodeGroupId, String targetExternalId) {
        if (ignore) {
            log.info("JMX is disabling the {} channel for the source externalId of {} and target externalId of {} from the following engine: {} ",
                    channelId, externalId, targetExternalId, engine.getEngineName());
        } else {
            log.info("JMX is enabling the {} channel for the source externalId of {} and target externalId of {} from the following engine: {} ",
                    channelId, externalId, targetExternalId, engine.getEngineName());
        }
        engine.getNodeService().ignoreNodeChannelForExternalId(ignore, channelId, nodeGroupId,
                externalId, targetNodeGroupId, targetExternalId);
    }

    @ManagedOperation(description = "Open the registration for a node with the specified external id")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeGroup", description = "The node group id this node will belong to"),
            @ManagedOperationParameter(name = "externalId", description = "The external id for the node") })
    public void openRegistration(String nodeGroupId, String externalId) {
        engine.getRegistrationService().openRegistration(nodeGroupId, externalId);
    }

    @ManagedOperation(description = "Re-open the registration for a node with the specified external id")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeId", description = "The node id to reopen registration for") })
    public void reopenRegistration(String nodeId) {
        engine.getRegistrationService().reOpenRegistration(nodeId);
    }

    @ManagedOperation(description = "Send an initial load of data to a node.")
    @ManagedOperationParameters({ @ManagedOperationParameter(name = "nodeId", description = "The node id to reload.") })
    public String reloadNode(String nodeId) {
        return engine.getDataService().reloadNode(nodeId, false, "jmx");
    }

    @ManagedOperation(description = "Send a SQL event to a node.")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeId", description = "The node id to sent the event to."),
            @ManagedOperationParameter(name = "catalogName", description = "The catalog name to reload. Can be null."),
            @ManagedOperationParameter(name = "schemaName", description = "The schema name to reload. Can be null."),
            @ManagedOperationParameter(name = "tableName", description = "The table name the SQL is for."),
            @ManagedOperationParameter(name = "sql", description = "The SQL statement to send.") })
    public String sendSQL(String nodeId, String catalogName, String schemaName, String tableName,
            String sql) {
        return engine.getDataService().sendSQL(nodeId, catalogName, schemaName, tableName, sql);
    }

    @ManagedOperation(description = "Send a delete and reload of a table to a node.")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeId", description = "The node id to reload."),
            @ManagedOperationParameter(name = "catalogName", description = "The catalog name to reload. Can be null."),
            @ManagedOperationParameter(name = "schemaName", description = "The schema name to reload. Can be null."),
            @ManagedOperationParameter(name = "tableName", description = "The table name to reload.") })
    public String reloadTable(String nodeId, String catalogName, String schemaName, String tableName) {
        return engine.getDataService().reloadTable(nodeId, catalogName, schemaName, tableName);
    }

    @ManagedOperation(description = "Send a delete and reload of a table to a node.")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeId", description = "The node id to reload."),
            @ManagedOperationParameter(name = "catalogName", description = "The catalog name to reload. Can be null."),
            @ManagedOperationParameter(name = "schemaName", description = "The schema name to reload. Can be null."),
            @ManagedOperationParameter(name = "tableName", description = "The table name to reload."),
            @ManagedOperationParameter(name = "overrideInitialLoadSelect", description = "Override initial load select where-clause.") })
    public String reloadTable(String nodeId, String catalogName, String schemaName,
            String tableName, String overrideInitialLoadSelect) {
        return engine.getDataService().reloadTable(nodeId, catalogName, schemaName, tableName,
                overrideInitialLoadSelect);
    }

    @ManagedOperation(description = "Write a range of batches to a file in SymmetricDS Data Format.")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeId", description = "The node id for the batches which will be written"),
            @ManagedOperationParameter(name = "startBatchId", description = "Starting batch ID of range"),
            @ManagedOperationParameter(name = "endBatchId", description = "Ending batch ID of range"),
            @ManagedOperationParameter(name = "fileName", description = "File name to write batches") })
    public void writeBatchRangeToFile(String nodeId, String startBatchId, String endBatchId,
            String fileName) throws Exception {
        Writer writer = new FileWriter(new File(fileName));
        engine.getDataExtractorService().extractBatchRange(writer, nodeId,
                Long.valueOf(startBatchId), Long.valueOf(endBatchId));
        try {
            writer.close();
        } catch (IOException e) {
        }
    }

    @ManagedOperation(description = "Encrypts plain text for use with db.user and db.password properties")
    @ManagedOperationParameters({ @ManagedOperationParameter(name = "plainText", description = "Plain text to encrypt") })
    public String encryptText(String plainText) throws Exception {
        try {
            return SecurityConstants.PREFIX_ENC + engine.getSecurityService().encrypt(plainText);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @ManagedAttribute(description = "Number of batches in error")
    public int getBatchesInError() {
        if (engine != null) {
            return engine.getOutgoingBatchService().countOutgoingBatchesInError();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Number of batches unsent")
    public int getBatchesUnsent() {
        if (engine != null) {
            return engine.getOutgoingBatchService().countOutgoingBatchesUnsent();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Number of data unrouted")
    public long getDataUnrouted() {
        if (engine != null) {
            return engine.getRouterService().getUnroutedDataCount();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Last restart time")
    public Date getStartedTime() {
        if (engine != null) {
            return engine.getLastRestartTime();
        } else {
            return null;
        }
    }

    @ManagedAttribute(description = "Version")
    public String getVersion() {
        return Version.version();
    }
}
