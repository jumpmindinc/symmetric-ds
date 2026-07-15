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
package org.jumpmind.symmetric;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.extension.IProcessInfoListener;
import org.jumpmind.extension.IProgressListener;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.job.IJobManager;
import org.jumpmind.symmetric.model.NodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IBandwidthService;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOfflinePullService;
import org.jumpmind.symmetric.service.IOfflinePushService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IPullService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.jumpmind.symmetric.service.IPushService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.service.IStatisticService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.IUpdateService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.slf4j.Logger;

public interface ISymmetricEngine {
    public void stop();

    public void destroy();

    public void uninstall();

    public void uninstall(IProcessInfoListener listener);

    /**
     * @return the URL that represents this engine
     */
    public String getSyncUrl();

    /**
     * Get a list of configured properties for Symmetric. Read-only.
     */
    public Properties getProperties();

    /**
     * @return The lower case representation of the engine name as setup in the symmetric.properties file. We always use a lower case representation because
     *         there are times the engine name is used in triggers at which point you can lose the original case representation.
     */
    public String getEngineName();

    /**
     * Will setup the SymmetricDS tables, if not already setup and if the engine is configured to do so.
     */
    public void setup();

    /**
     * Must be called to start SymmetricDS.
     * 
     * @return true if successfully started
     */
    public boolean start();

    public boolean start(boolean startJobs);

    /**
     * Queue up an initial load or a reload to a node.
     * 
     * @param createBy
     *            TODO
     */
    public String reloadNode(String nodeId, String createBy);

    public String sendSQL(String nodeId, String catalogName, String schemaName, String tableName, String sql);

    /**
     * Will perform a push the same way the {@link PushJob} would have.
     * 
     * @see IPushService#pushData(boolean)
     * @return {@link RemoteNodeStatuses}
     */
    public RemoteNodeStatuses push();

    /**
     * Call this to resync triggers
     * 
     * @see ITriggerRouterService#syncTriggers()
     */
    public boolean syncTriggers();

    /**
     * Call this to force all triggers to be rebuilt
     * 
     * @see ITriggerRouterService#forceTriggerRebuild()
     */
    public boolean forceTriggerRebuild();

    /**
     * Get the current status of this node.
     * 
     * @return {@link NodeStatus}
     */
    public NodeStatus getNodeStatus();

    /**
     * Will perform a pull the same way the {@link PullJob} would have.
     * 
     * @see IPullService#pullData(boolean)
     * @return {@link RemoteNodeStatuses}
     */
    public RemoteNodeStatuses pull();

    /**
     * Route captured data the same way the {@link RouterJob} would have.
     */
    public void route();

    /**
     * This can be called to do a purge. It may be called only if the {@link OutgoingPurgeJob} has not been enabled.
     * 
     * @see IPurgeService#purgeOutgoing(boolean)
     */
    public void purge();

    /**
     * Will check to see if this instance of SymmetricDS is configured with the required properties for a node to operate.
     */
    public boolean isConfigured();

    /**
     * Push a copy of the node onto the push queue so the SymmetricDS node 'checks' in with it's root node.
     * 
     * @param force
     *            When force is true the heart beat will always be inserted. If it is false, the heart beat will only be inserted if the period between heart
     *            beats has expired.
     */
    public void heartbeat(boolean force);

    /**
     * Open up registration for node to attach.
     * 
     * @see IRegistrationService#openRegistration(String, String)
     */
    public void openRegistration(String groupId, String externalId);

    public void reOpenRegistration(String nodeId);

    /**
     * Check to see if this node has been registered.
     * 
     * @return true if the node is registered
     */
    public boolean isRegistered();

    /**
     * Check to see if this node has been started.
     * 
     * @return true if the node is started
     */
    public boolean isStarted();

    public boolean isInitialized();

    /**
     * Check to see if this node is starting.
     * 
     * @return true if the node is starting
     */
    public boolean isStarting();

    public void clearCaches();

    /**
     * Attempt to configure the database objects that support SymmetricDS. If they are out of date this method will attempt to alter the tables to bring them up
     * to date.
     * 
     * @param force
     *            forces this action to be run regardless of the parameter settings
     */
    public void setupDatabase(boolean force);

    public void removeAndCleanupNode(String nodeId);

    public IConfigurationService getConfigurationService();

    public IParameterService getParameterService();

    public INodeService getNodeService();

    public IRegistrationService getRegistrationService();

    public IClusterService getClusterService();

    public IPurgeService getPurgeService();

    public IDataService getDataService();

    public ISymmetricDialect getSymmetricDialect();

    public ISymmetricDialect getTargetDialect();

    public IJobManager getJobManager();

    public IOutgoingBatchService getOutgoingBatchService();

    public IAcknowledgeService getAcknowledgeService();

    public IBandwidthService getBandwidthService();

    public IDataExtractorService getDataExtractorService();

    public IDataExtractorService getFileSyncExtractorService();

    public IDataLoaderService getDataLoaderService();

    public IIncomingBatchService getIncomingBatchService();

    public IPullService getPullService();

    public IPushService getPushService();

    public IOfflinePullService getOfflinePullService();

    public IOfflinePushService getOfflinePushService();

    public IRouterService getRouterService();

    public ISecurityService getSecurityService();

    public IStatisticService getStatisticService();

    public ITriggerRouterService getTriggerRouterService();

    public IGroupletService getGroupletService();

    public IStatisticManager getStatisticManager();

    public String getDeploymentType();

    public String getDeploymentSubType();

    public IConcurrentConnectionManager getConcurrentConnectionManager();

    public IEngineMetricsService getMetricsService();

    public ITransformService getTransformService();

    public ILoadFilterService getLoadFilterService();

    public IInitialLoadService getInitialLoadService();

    public ITransportManager getTransportManager();

    public INodeCommunicationService getNodeCommunicationService();

    public ISequenceService getSequenceService();

    public String getTablePrefix();

    public Logger getLog();

    public IExtensionService getExtensionService();

    public IStagingManager getStagingManager();

    public ISqlTemplate getSqlTemplate();

    public IFileSyncService getFileSyncService();

    public IContextService getContextService();

    public IUpdateService getUpdateService();

    public Date getLastRestartTime();

    public <T> T getDataSource();

    public IDatabasePlatform getDatabasePlatform();

    public File snapshot(IProgressListener listener);

    public List<File> listSnapshots();

    public String getNodeId();

    public Throwable getLastException();

    public String getLastExceptionMessage();

    public String getEngineDescription(String mesage);

    public ICacheManager getCacheManager();
}
