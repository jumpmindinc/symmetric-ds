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
package org.jumpmind.symmetric.service.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.config.INodeIdCreator;
import org.jumpmind.symmetric.ext.INodeRegistrationAuthenticator;
import org.jumpmind.symmetric.ext.INodeRegistrationListener;
import org.jumpmind.symmetric.ext.IRegistrationRedirect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.model.RegistrationRequest.RegistrationStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus.Status;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.security.INodePasswordFilter;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.RegistrationFailedException;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationRedirectException;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ConnectionDuplicateException;
import org.jumpmind.symmetric.transport.ConnectionRejectedException;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.TransportUtils;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.RandomTimeSlot;

/**
 * @see IRegistrationService
 */
public class RegistrationService extends AbstractService implements IRegistrationService {
    private INodeService nodeService;
    private IDataExtractorService dataExtractorService;
    private IDataService dataService;
    private IDataLoaderService dataLoaderService;
    private ITransportManager transportManager;
    private IOutgoingBatchService outgoingBatchService;
    private RandomTimeSlot randomTimeSlot;
    private IStatisticManager statisticManager;
    private IConfigurationService configurationService;
    private IExtensionService extensionService;
    private ISymmetricEngine engine;
    private boolean allowClientRegistration = true;

    public RegistrationService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        this.nodeService = engine.getNodeService();
        this.dataExtractorService = engine.getDataExtractorService();
        this.dataService = engine.getDataService();
        this.dataLoaderService = engine.getDataLoaderService();
        this.transportManager = engine.getTransportManager();
        this.statisticManager = engine.getStatisticManager();
        this.configurationService = engine.getConfigurationService();
        this.outgoingBatchService = engine.getOutgoingBatchService();
        this.extensionService = engine.getExtensionService();
        this.randomTimeSlot = new RandomTimeSlot(parameterService.getExternalId(),
                engine.getParameterService().getInt(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES, 30));
        setSqlMap(new RegistrationServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    public Node registerPullOnlyNode(String externalId, String nodeGroupId,
            String databaseType, String databaseVersion, String databaseName)
            throws IOException {
        Node node = new Node();
        node.setExternalId(externalId);
        node.setNodeGroupId(nodeGroupId);
        node.setDatabaseType(databaseType);
        node.setDatabaseVersion(databaseVersion);
        node.setDatabaseName(databaseName);
        node.setDeploymentType(Constants.DEPLOYMENT_TYPE_REST);
        node = processRegistration(node, null, null, null, null, true);
        if (node.isSyncEnabled()) {
            // set the node as registered as we have no
            // virtual batch for registration to be ok'd
            markNodeAsRegistered(node.getNodeId());
        }
        return node;
    }

    public boolean registerNode(Node preRegisteredNode, OutputStream out, boolean isRequestedRegistration)
            throws IOException {
        return registerNode(preRegisteredNode, null, null, out, null, null, isRequestedRegistration);
    }

    protected void extractConfiguration(OutputStream out, Node registeredNode) {
        dataExtractorService.extractConfigurationStandalone(registeredNode, TransportUtils.toWriter(out), TableConstants
                .getConfigTablesExcludedFromRegistration());
    }

    protected Node processRegistration(Node nodePriorToRegistration, String remoteHost,
            String remoteAddress, String userId, String password, boolean isRequestedRegistration)
            throws IOException {
        Node processedNode = new Node();
        processedNode.setSyncEnabled(false);
        if (!allowClientRegistration) {
            log.warn("Cannot register a client node until this node has synced triggers");
            return processedNode;
        }
        Node identity = nodeService.findIdentity();
        NodeSecurity mySecurity = identity == null ? null : nodeService.findNodeSecurity(identity.getNodeId());
        if (identity == null || (mySecurity != null && mySecurity.isRegistrationEnabled())) {
            RegistrationRequest req = new RegistrationRequest(nodePriorToRegistration,
                    RegistrationStatus.ER, remoteHost, remoteAddress);
            req.setErrorMessage("Cannot register a client node until this node is registered");
            saveRegistrationRequest(req);
            log.warn(req.getErrorMessage());
            return processedNode;
        }
        try {
            if (!nodeService.isRegistrationServer()) {
                /*
                 * registration is not allowed until this node has an identity and an initial load
                 */
                boolean requiresInitialLoad = parameterService.is(ParameterConstants.REGISTRATION_REQUIRE_INITIAL_LOAD, true);
                if (requiresInitialLoad && (mySecurity == null || mySecurity.getInitialLoadEndTime() == null)) {
                    RegistrationRequest req = new RegistrationRequest(nodePriorToRegistration,
                            RegistrationStatus.ER, remoteHost, remoteAddress);
                    req.setErrorMessage(
                            "Cannot register a client node until this node has an initial load (ie. node_security.initial_load_end_time is a non null value)");
                    saveRegistrationRequest(req);
                    log.warn(req.getErrorMessage());
                    return processedNode;
                }
            }
            String redirectUrl = null;
            IRegistrationRedirect registrationRedirect = extensionService.getExtensionPoint(IRegistrationRedirect.class);
            if (registrationRedirect != null) {
                redirectUrl = registrationRedirect.getRedirectionUrlFor(nodePriorToRegistration.getExternalId(),
                        nodePriorToRegistration.getNodeGroupId());
            } else {
                redirectUrl = getRedirectionUrlFor(nodePriorToRegistration.getExternalId());
            }
            if (redirectUrl != null) {
                log.info("Redirecting {} to {} for registration.",
                        nodePriorToRegistration.getExternalId(), redirectUrl);
                saveRegistrationRequest(new RegistrationRequest(nodePriorToRegistration,
                        RegistrationStatus.RR, remoteHost, remoteAddress));
                throw new RegistrationRedirectException(redirectUrl);
            }
            if (StringUtils.isBlank(nodePriorToRegistration.getNodeGroupId())) {
                RegistrationRequest req = new RegistrationRequest(nodePriorToRegistration, RegistrationStatus.ER, remoteHost, remoteAddress);
                req.setErrorMessage("Missing node group ID in request from remote address " + remoteAddress);
                saveRegistrationRequest(req);
                log.warn(req.getErrorMessage());
                return processedNode;
            }
            /*
             * Check to see if there is a link that exists to service the node that is requesting registration
             */
            NodeGroupLink link = configurationService.getNodeGroupLinkFor(
                    identity.getNodeGroupId(), nodePriorToRegistration.getNodeGroupId(), false);
            if (link == null
                    && parameterService.is(ParameterConstants.REGISTRATION_REQUIRE_NODE_GROUP_LINK,
                            true) && !parameterService.is(ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK)) {
                RegistrationRequest req = new RegistrationRequest(nodePriorToRegistration,
                        RegistrationStatus.ER, remoteHost, remoteAddress);
                req.setErrorMessage(String.format(
                        "Cannot register a client node unless a node group link exists so the registering node can receive configuration updates.  Please add a group link where the source group id is %s and the target group id is %s",
                        identity.getNodeGroupId(), nodePriorToRegistration.getNodeGroupId()));
                saveRegistrationRequest(req);
                log.warn(req.getErrorMessage());
                return processedNode;
            }
            String nodeId = StringUtils.isBlank(nodePriorToRegistration.getNodeId()) ? extensionService.getExtensionPoint(INodeIdCreator.class).selectNodeId(
                    nodePriorToRegistration, remoteHost,
                    remoteAddress) : nodePriorToRegistration.getNodeId();
            Node foundNode = nodeService.findNode(nodeId);
            NodeSecurity security = nodeService.findNodeSecurity(nodeId);
            boolean isRegistrationAuthenticated = false;
            if (userId != null || password != null) {
                List<INodeRegistrationAuthenticator> listeners = extensionService.getExtensionPointList(INodeRegistrationAuthenticator.class);
                for (INodeRegistrationAuthenticator listener : listeners) {
                    isRegistrationAuthenticated |= listener.authenticate(userId, password);
                }
            }
            if ((foundNode == null || security == null || !security.isRegistrationEnabled() || !security.isRegistrationAllowedNow())
                    && (parameterService.is(ParameterConstants.AUTO_REGISTER_ENABLED) || isRegistrationAuthenticated)) {
                openRegistration(nodePriorToRegistration, remoteHost, remoteAddress, null, null);
                nodeId = StringUtils.isBlank(nodePriorToRegistration.getNodeId()) ? extensionService.getExtensionPoint(INodeIdCreator.class).selectNodeId(
                        nodePriorToRegistration, remoteHost,
                        remoteAddress) : nodePriorToRegistration.getNodeId();
                security = nodeService.findNodeSecurity(nodeId);
                foundNode = nodeService.findNode(nodeId);
            } else if (foundNode == null || security == null || !security.isRegistrationEnabled() || !security.isRegistrationAllowedNow()) {
                saveRegistrationRequest(new RegistrationRequest(nodePriorToRegistration,
                        RegistrationStatus.RQ, remoteHost, remoteAddress));
                return processedNode;
            }
            if (link == null && parameterService.is(ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK)) {
                link = new NodeGroupLink(identity.getNodeGroupId(), nodePriorToRegistration.getNodeGroupId());
                configurationService.saveNodeGroupLink(link);
                ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
                Router router = new Router();
                router.setNodeGroupLink(link);
                router.setRouterId(router.createDefaultName());
                triggerRouterService.saveRouter(router);
                link = configurationService.getNodeGroupLinkFor(nodePriorToRegistration.getNodeGroupId(), identity.getNodeGroupId(), false);
                if (link == null) {
                    link = new NodeGroupLink(nodePriorToRegistration.getNodeGroupId(), identity.getNodeGroupId(), NodeGroupLinkAction.P);
                    configurationService.saveNodeGroupLink(link);
                    router = new Router();
                    router.setNodeGroupLink(link);
                    router.setRouterId(router.createDefaultName());
                    triggerRouterService.saveRouter(router);
                }
            }
            // TODO: since we send sym_node in registration batch, save this record with source_node_id = node_id
            foundNode.setSyncEnabled(true);
            foundNode.setSyncUrl(nodePriorToRegistration.getSyncUrl());
            foundNode.setDatabaseType(nodePriorToRegistration.getDatabaseType());
            foundNode.setDatabaseVersion(nodePriorToRegistration.getDatabaseVersion());
            foundNode.setSymmetricVersion(nodePriorToRegistration.getSymmetricVersion());
            foundNode.setDeploymentType(nodePriorToRegistration.getDeploymentType());
            foundNode.setDatabaseName(nodePriorToRegistration.getDatabaseName());
            foundNode.setConfigVersion(Version.version());
            nodeService.save(foundNode);
            log.info("Registered node " + foundNode + " in my database, but pending acknowledgement");
            /**
             * Only send automatic initial load once or if the client is really re-registering
             */
            if ((security != null && security.getInitialLoadTime() == null) || isRequestedRegistration) {
                if (parameterService.is(ParameterConstants.AUTO_RELOAD_ENABLED)) {
                    engine.getInitialLoadService().cancelAllLoadsForTarget(nodeId);
                    log.info("Auto reload for target node {}", nodeId);
                    nodeService.setInitialLoadEnabled(nodeId, true, false, -1, "registration");
                }
                if (parameterService.is(ParameterConstants.AUTO_RELOAD_REVERSE_ENABLED)) {
                    log.info("Auto reverse reload from source node {}", nodeId);
                    nodeService.setReverseInitialLoadEnabled(nodeId, true, false, -1, "registration");
                }
            }
            saveRegistrationRequest(new RegistrationRequest(foundNode, RegistrationStatus.OK,
                    remoteHost, remoteAddress));
            markNodeAsRegistrationPending(nodeId);
            statisticManager.incrementNodesRegistered(1);
            return foundNode;
        } catch (RegistrationNotOpenException ex) {
            if (StringUtils.isNotBlank(ex.getMessage())) {
                log.warn("Registration not allowed for {} because {}", nodePriorToRegistration, ex.getMessage());
            }
            return processedNode;
        }
    }

    /**
     * @see IRegistrationService#registerNode(Node, OutputStream, boolean)
     */
    // Called when node connects using pull registration URL
    public boolean registerNode(Node nodePriorToRegistration, String remoteHost,
            String remoteAddress, OutputStream out, String userId, String password, boolean isRequestedRegistration)
            throws IOException {
        if (parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)) {
            NodeGroupLink link = configurationService.getNodeGroupLinkFor(parameterService.getNodeGroupId(), nodePriorToRegistration.getNodeGroupId(), false);
            if (link != null && link.getDataEventAction() == NodeGroupLinkAction.P) {
                String nodeId = StringUtils.isBlank(nodePriorToRegistration.getNodeId()) ? extensionService.getExtensionPoint(INodeIdCreator.class)
                        .selectNodeId(
                                nodePriorToRegistration, remoteHost, remoteAddress) : nodePriorToRegistration.getNodeId();
                NodeSecurity nodeSecurity = nodeService.findNodeSecurity(nodeId);
                if (nodeSecurity != null && nodeSecurity.isRegistrationEnabled()) {
                    // Make sure sync URL is set before skipping this registration request
                    Node node = nodeService.findNode(nodeId);
                    if (node != null && node.getSyncUrl() != null && node.getSyncUrl().length() > 0) {
                        log.debug("Pull of registration from {} is being ignored because group link is push", nodePriorToRegistration);
                        return true;
                    }
                }
            }
        }
        Node processedNode = processRegistration(nodePriorToRegistration, remoteHost,
                remoteAddress, userId, password, isRequestedRegistration);
        if (processedNode.isSyncEnabled()) {
            log.info("Preparing to send registration to node {} by clearing its outgoing config batches", processedNode);
            outgoingBatchService.markAllConfigAsSentForNode(processedNode.getNodeId());
            log.info("Sending registration batch to node {}", processedNode);
            extractConfiguration(out, processedNode);
        }
        return processedNode.isSyncEnabled();
    }

    public List<RegistrationRequest> getRegistrationRequests(boolean includeNodesWithOpenRegistrations, boolean includeRejects) {
        String sql = getSql("selectRegistrationRequestSql", "whereStatusIsRequestOrErrorSql");
        if (includeRejects) {
            sql = sql.replace(")", ",'" + RegistrationRequest.RegistrationStatus.RJ.name() + "')");
        }
        List<RegistrationRequest> requests = sqlTemplate.query(sql, new RegistrationRequestMapper());
        if (!includeNodesWithOpenRegistrations) {
            Collection<Node> nodes = nodeService.findNodesWithOpenRegistration();
            Iterator<RegistrationRequest> i = requests.iterator();
            while (i.hasNext()) {
                RegistrationRequest registrationRequest = (RegistrationRequest) i.next();
                for (Node node : nodes) {
                    if (node.getNodeGroupId().equals(registrationRequest.getNodeGroupId())
                            && node.getExternalId().equals(registrationRequest.getExternalId())) {
                        i.remove();
                    }
                }
            }
        }
        return requests;
    }

    public RegistrationRequest getLatestRegistrationRequest(String nodeGroupId, String externalId) {
        RegistrationRequest latestRequest = null;
        String sql = getSql("selectRegistrationRequestSql", "whereNodeGroupIdAndExternalIdSql");
        List<RegistrationRequest> requests = sqlTemplate.query(sql, new RegistrationRequestMapper(),
                new Object[] { nodeGroupId, externalId }, new int[] { Types.VARCHAR, Types.VARCHAR });
        for (RegistrationRequest request : requests) {
            if (latestRequest == null || request.getCreateTime().after(latestRequest.getCreateTime())) {
                latestRequest = request;
            }
        }
        return latestRequest;
    }

    public boolean deleteRegistrationRequest(RegistrationRequest request) {
        String externalId = request.getExternalId() == null ? "" : request.getExternalId();
        String nodeGroupId = request.getNodeGroupId() == null ? "" : request.getNodeGroupId();
        return 0 < sqlTemplate.update(getSql("deleteRegistrationRequestSql"), new Object[] {
                nodeGroupId, externalId, request.getHostName(), request.getStatus().name() });
    }

    public void saveRegistrationRequest(RegistrationRequest request) {
        /**
         * Lookup existing registration requests to update the attempt count. We previously did this in SQL on the update, but as400 v5 didn't like that
         */
        String externalId = request.getExternalId() == null ? "" : request.getExternalId();
        String nodeGroupId = request.getNodeGroupId() == null ? "" : request.getNodeGroupId();
        boolean foundOne = false;
        List<RegistrationRequest> requests = getRegistrationRequests(true, true);
        for (RegistrationRequest registrationRequest : requests) {
            if (registrationRequest.getNodeGroupId().equals(nodeGroupId) && registrationRequest.getExternalId().equals(externalId)) {
                request.setAttemptCount(registrationRequest.getAttemptCount() + 1);
                if (registrationRequest.getStatus().equals(RegistrationStatus.RJ) && request.getStatus().equals(RegistrationStatus.RQ)) {
                    request.setStatus(RegistrationStatus.RJ);
                } else if (registrationRequest.getStatus().equals(RegistrationStatus.ER) && request.getStatus().equals(RegistrationStatus.OK)) {
                    request.setStatus(RegistrationStatus.ER);
                    request.setErrorMessage(registrationRequest.getErrorMessage());
                }
                foundOne = true;
                break;
            }
        }
        int count = 0;
        if (foundOne) {
            count = sqlTemplate.update(
                    getSql("updateRegistrationRequestSql", "whereNodeGroupIdExternalIdHostNameStatusSql"),
                    new Object[] { request.getAttemptCount(), request.getLastUpdateBy(),
                            request.getLastUpdateTime(), request.getRegisteredNodeId(),
                            request.getStatus().name(), request.getErrorMessage(), nodeGroupId,
                            externalId, request.getHostName() }, new int[] {
                                    Types.NUMERIC, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR,
                                    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR });
        }
        if (count == 0) {
            sqlTemplate.update(
                    getSql("insertRegistrationRequestSql"),
                    new Object[] { request.getLastUpdateBy(), request.getLastUpdateTime(),
                            request.getRegisteredNodeId(), request.getStatus().name(), nodeGroupId,
                            externalId, request.getIpAddress(), request.getHostName(),
                            request.getErrorMessage(), new Date() }, new int[] { Types.VARCHAR, Types.TIMESTAMP,
                                    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                                    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP });
        }
    }

    public void updateRegistrationRequest(RegistrationRequest request) {
        sqlTemplate.update(
                getSql("updateRegistrationRequestSql", "wherePkSql"),
                new Object[] { request.getAttemptCount(), request.getLastUpdateBy(),
                        request.getLastUpdateTime(), request.getRegisteredNodeId(),
                        request.getStatus().name(), request.getErrorMessage(), request.getNodeGroupId(),
                        request.getExternalId(), request.getCreateTime() }, new int[] {
                                Types.NUMERIC, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR,
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP });
    }

    public String getRedirectionUrlFor(String externalId) {
        List<String> list = sqlTemplate.query(getSql("getRegistrationRedirectUrlSql"),
                new StringMapper(), new Object[] { externalId }, new int[] { Types.VARCHAR });
        if (list.size() > 0) {
            return transportManager.resolveURL(list.get(0), parameterService.getRegistrationUrl());
        } else {
            return null;
        }
    }

    public void saveRegistrationRedirect(String externalIdToRedirect, String nodeIdToRedirectTo) {
        int count = sqlTemplate.update(getSql("updateRegistrationRedirectUrlSql"), new Object[] {
                nodeIdToRedirectTo, externalIdToRedirect }, new int[] { Types.VARCHAR,
                        Types.VARCHAR });
        if (count == 0) {
            sqlTemplate.update(getSql("insertRegistrationRedirectUrlSql"), new Object[] {
                    nodeIdToRedirectTo, externalIdToRedirect }, new int[] { Types.VARCHAR,
                            Types.VARCHAR });
        }
    }

    /**
     * @see IRegistrationService#markNodeAsRegistered(Node)
     */
    public void markNodeAsRegistered(String nodeId) {
        sqlTemplate.update(getSql("registerNodeSecuritySql"), new Object[] { new Date(), nodeId });
        nodeService.flushNodeAuthorizedCache();
        Node node = nodeService.findNode(nodeId);
        if (node != null) {
            RegistrationRequest request = getLatestRegistrationRequest(node.getNodeGroupId(), node.getExternalId());
            if (!RegistrationStatus.OK.equals(request.getStatus()) || request.getErrorMessage() != null) {
                request.setStatus(RegistrationStatus.OK);
                request.setErrorMessage(null);
                updateRegistrationRequest(request);
            }
        }
        List<INodeRegistrationListener> registrationListeners = extensionService.getExtensionPointList(INodeRegistrationListener.class);
        for (INodeRegistrationListener l : registrationListeners) {
            l.registrationSyncTriggers();
        }
        log.info("Completed registration of node {}", nodeId);
        if (engine.getCacheManager().isUsingTargetExternalId(false)) {
            if (node != null) {
                log.info("Syncing triggers for node {} using target external ID of {}", node.toString(), node.getExternalId());
                engine.getTriggerRouterService().syncTriggers(node.getExternalId(), false);
            } else {
                log.warn("Unable to sync triggers for target external ID because node {} was not found", nodeId);
            }
        }
    }

    protected void markNodeAsRegistrationPending(String nodeId) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            symmetricDialect.disableSyncTriggers(transaction, nodeId);
            transaction.prepareAndExecute(getSql("registrationPendingSql"), new Date(), nodeId);
            transaction.commit();
            nodeService.flushNodeAuthorizedCache();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            symmetricDialect.enableSyncTriggers(transaction);
            close(transaction);
        }
    }

    private void sleepBeforeRegistrationRetry() {
        long sleepTimeInMs = DateUtils.MILLIS_PER_SECOND
                * randomTimeSlot.getRandomValueSeededByExternalId();
        log.info("Could not register.  Sleeping for {}ms before attempting again.", sleepTimeInMs);
        List<INodeRegistrationListener> registrationListeners = extensionService.getExtensionPointList(INodeRegistrationListener.class);
        for (INodeRegistrationListener l : registrationListeners) {
            l.registrationNextAttemptUpdated((int) (sleepTimeInMs / 1000));
        }
        AppUtils.sleep(sleepTimeInMs);
    }

    public boolean isRegisteredWithServer() {
        return nodeService.findIdentity() != null;
    }

    /**
     * @see IRegistrationService#registerWithServer()
     */
    public boolean registerWithServer() {
        boolean wasRegistered = isRegisteredWithServer();
        boolean registered = wasRegistered;
        int maxNumberOfAttempts = parameterService
                .getInt(ParameterConstants.REGISTRATION_NUMBER_OF_ATTEMPTS);
        while (!registered && (maxNumberOfAttempts < 0 || maxNumberOfAttempts > 0) && engine.isStarted()) {
            maxNumberOfAttempts--;
            registered = attemptToRegisterWithServer(maxNumberOfAttempts);
            if (!registered && maxNumberOfAttempts != 0) {
                sleepBeforeRegistrationRetry();
            }
        }
        if (!registered) {
            throw new RegistrationFailedException(String.format(
                    "Failed after trying to register %s times.",
                    parameterService.getString(ParameterConstants.REGISTRATION_NUMBER_OF_ATTEMPTS)));
        }
        return registered != wasRegistered;
    }

    public synchronized boolean attemptToRegisterWithServer(int maxNumberOfAttempts) {
        List<INodeRegistrationListener> registrationListeners = extensionService.getExtensionPointList(INodeRegistrationListener.class);
        boolean registered = isRegisteredWithServer();
        if (!registered) {
            try {
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationStarting(Thread.currentThread());
                }
                log.info("This node is unregistered.  It will attempt to register using the registration.url");
                registered = dataLoaderService.loadDataFromPull(null, (String) null).getStatus() == Status.DATA_PROCESSED;
            } catch (ConnectException e) {
                log.warn(
                        "The request to register failed because the client failed to connect to the server.  The connection error message was: {}",
                        e.getMessage());
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed(
                            "The request to register failed because the client failed to connect to the server.  The connection error message was: "
                                    + e.getMessage());
                }
            } catch (UnknownHostException e) {
                log.warn("The request to register failed because the host was unknown.  The unknown host exception was: {}", e.getMessage());
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed("The request to register failed because the host was unknown.  The unknown host exception was: "
                            + e.getMessage());
                }
            } catch (ConnectionRejectedException e) {
                log.warn("The request to register was rejected because the server is busy.");
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed("The request to register was rejected because the server is busy.");
                }
            } catch (ConnectionDuplicateException e) {
                log.warn("The request to register was rejected because of a duplicate connection.");
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed("The request to register was rejected because of a duplicate connection.");
                }
            } catch (RegistrationNotOpenException e) {
                log.warn("Waiting for registration to be accepted by the server. Registration is not open.");
                boolean authWasAttempted = false;
                for (INodeRegistrationListener l : registrationListeners) {
                    Map<String, String> prop = l.getRequestProperties();
                    if (prop != null && prop.containsKey(WebConstants.REG_USER_ID)) {
                        authWasAttempted = true;
                    }
                }
                for (INodeRegistrationListener l : registrationListeners) {
                    if (authWasAttempted) {
                        l.registrationFailed("User is not authorized.  Registration is not open.");
                    } else {
                        l.registrationFailed("Waiting for registration to be accepted by the server. Registration is not open.");
                    }
                }
            } catch (ServiceUnavailableException e) {
                log.warn("Unable to register with server because the service is not available.  It may be starting up.");
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed("Unable to register with server because the service is not available.  It may be starting up.");
                }
            } catch (Exception e) {
                log.error("Unexpected error during registration: "
                        + (StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : e.getClass().getName()), e);
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed("Unexpected error during registration: "
                            + (StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : e.getClass().getName()));
                }
            }
            registered = checkRegistrationSuccessful(registered, maxNumberOfAttempts);
        }
        return registered;
    }

    protected boolean checkRegistrationSuccessful(boolean registered, int maxNumberOfAttempts) {
        if (!registered && (maxNumberOfAttempts < 0 || maxNumberOfAttempts > 0)) {
            registered = isRegisteredWithServer();
            if (registered) {
                log.info(
                        "We registered, but were not able to acknowledge our registration.  Sending a sql event to the node where we registered to indicate that we are alive and registered");
                Node identity = nodeService.findIdentity();
                Node parentNode = nodeService.findNode(identity.getCreatedAtNodeId());
                dataService
                        .insertSqlEvent(
                                parentNode,
                                "update "
                                        + tablePrefix
                                        + "_node_security set registration_enabled=1, registration_time=current_timestamp where node_id='"
                                        + identity.getNodeId() + "'", false, -1, null);
            }
        }
        if (registered) {
            List<INodeRegistrationListener> registrationListeners = extensionService.getExtensionPointList(INodeRegistrationListener.class);
            Node node = nodeService.findIdentity();
            if (node != null) {
                log.info("Successfully registered node [id={}]", node.getNodeId());
                extensionService.refresh();
                dataService.heartbeat(true);
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationSuccessful();
                }
            } else {
                log.error("Node identity is missing after registration.  The registration server may be misconfigured or have an error");
                for (INodeRegistrationListener l : registrationListeners) {
                    l.registrationFailed("Node identity is missing after registration.  The registration server may be misconfigured or have an error");
                }
                registered = false;
            }
        }
        return registered;
    }

    public List<OutgoingBatch> registerWithClient(Node remote, IOutgoingWithResponseTransport transport) {
        List<OutgoingBatch> extractedBatches = new ArrayList<OutgoingBatch>();
        Node identity = nodeService.findIdentity();
        if (identity != null) {
            log.info("Node {} is unregistered.  Requesting to push registration to {}", remote.getNodeId(), remote.getSyncUrl());
            IIncomingTransport reqTransport = null;
            Node node = null;
            try {
                Map<String, String> prop = new HashMap<String, String>();
                prop.put(WebConstants.PUSH_REGISTRATION, Boolean.TRUE.toString());
                reqTransport = transportManager.getRegisterTransport(identity, remote.getSyncUrl(), prop);
                Map<String, String> params = transportManager.readRequestProperties(reqTransport.openStream());
                node = TransportUtils.convertPropertiesToNode(params);
                node = processRegistration(node, params.get(WebConstants.HOST_NAME), params.get(WebConstants.IP_ADDRESS), null, null, true);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.error("Failed to request push registration", e);
                } else {
                    log.error("Failed to request push registration: {}: {}", e.getClass().getSimpleName(), StringUtils.trimToEmpty(e.getMessage()));
                }
            } finally {
                if (reqTransport != null) {
                    reqTransport.close();
                }
            }
            if (node != null && node.isSyncEnabled()) {
                OutgoingBatch batch = new OutgoingBatch(remote.getNodeId(), Constants.CHANNEL_CONFIG, OutgoingBatch.Status.LD);
                batch.setBatchId(Constants.VIRTUAL_BATCH_FOR_REGISTRATION);
                extractedBatches.add(batch);
                try {
                    log.info("Preparing to send registration to node {} by clearing its outgoing config batches", remote);
                    outgoingBatchService.markAllConfigAsSentForNode(remote.getNodeId());
                    log.info("Sending registration batch to node {}", remote);
                    extractConfiguration(transport.openStream(), remote);
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.error("Failed to push registration batch", e);
                    } else {
                        log.error("Failed to push registration batch: {}: {}", e.getClass().getSimpleName(), StringUtils.trimToEmpty(e.getMessage()));
                    }
                }
            }
        }
        return extractedBatches;
    }

    public boolean writeRegistrationProperties(OutputStream os) {
        try {
            Node local = new Node(parameterService, symmetricDialect, engine.getDatabasePlatform().getName());
            local.setDeploymentType(engine.getDeploymentType());
            Map<String, String> requestProperties = TransportUtils.convertNodeToProperties(local, null);
            transportManager.writeRequestProperties(requestProperties, os);
        } catch (IOException e) {
            log.error("Failed to write response for push registration request", e);
            return false;
        }
        return true;
    }

    public boolean loadRegistrationBatch(Node node, InputStream is, OutputStream os) {
        try {
            dataLoaderService.loadDataFromPush(node, Constants.CHANNEL_DEFAULT, is, os);
        } catch (IOException e) {
            log.error("Failed to load batch from push registration", e);
            return false;
        }
        return true;
    }

    /**
     * @see IRegistrationService#reOpenRegistration(String)
     */
    public synchronized void reOpenRegistration(String nodeId) {
        reOpenRegistration(nodeId, null, null, null, null, false);
    }

    public synchronized void reOpenRegistration(String nodeId, boolean forceNewPassword) {
        reOpenRegistration(nodeId, null, null, null, null, forceNewPassword);
    }

    protected synchronized void reOpenRegistration(String nodeId, String remoteHost, String remoteAddress, Date notBefore, Date notAfter,
            boolean forceNewPassword) {
        Node me = nodeService.findIdentity();
        if (me != null && parameterService.isRegistrationServer() && me.getNodeId().equals(nodeId)) {
            throw new IllegalStateException("Cannot open registration for a registration server");
        }
        Node node = nodeService.findNode(nodeId);
        NodeSecurity security = nodeService.findNodeSecurity(nodeId);
        String password = null;
        if (security != null && StringUtils.isNotBlank(security.getNodePassword())
                && parameterService.is(ParameterConstants.REGISTRATION_REOPEN_USE_SAME_PASSWORD, true) && !forceNewPassword) {
            password = security.getNodePassword();
        } else {
            password = extensionService.getExtensionPoint(INodeIdCreator.class).generatePassword(node);
        }
        password = filterPasswordOnSaveIfNeeded(password, nodeId);
        if (node != null) {
            int updateCount = sqlTemplate.update(getSql("reopenRegistrationSql"), new Object[] {
                    password, notBefore, notAfter, nodeId });
            if (updateCount == 0 && nodeService.findNodeSecurity(nodeId) == null) {
                // if the update count was 0, then we probably have a row in the
                // node table, but not in node security.
                // lets go ahead and try to insert into node security.
                sqlTemplate.update(getSql("openRegistrationNodeSecuritySql"), new Object[] {
                        nodeId, password, notBefore, notAfter, nodeService.findIdentityNodeId() });
                log.info("Registration was opened for {}", nodeId);
            } else if (updateCount == 0) {
                log.warn("Registration was already enabled for {}.  No need to reenable it", nodeId);
            } else {
                log.info("Registration was reopened for {}", nodeId);
            }
            if (isNotBlank(remoteHost)) {
                NodeHost nodeHost = new NodeHost(node.getNodeId(), null);
                nodeHost.setHeartbeatTime(new Date());
                nodeHost.setIpAddress(remoteAddress);
                nodeHost.setHostName(remoteHost);
                nodeService.updateNodeHost(nodeHost);
            }
            nodeService.flushNodeAuthorizedCache();
        } else {
            log.warn("There was no row with a node id of {} to 'reopen' registration for", nodeId);
        }
    }

    /**
     * @see IRegistrationService#openRegistration(String, String)
     * @return The nodeId of the registered node
     */
    public synchronized String openRegistration(String nodeGroup, String externalId) {
        Node node = new Node();
        node.setExternalId(externalId);
        node.setNodeGroupId(nodeGroup);
        return openRegistration(node);
    }

    public synchronized String openRegistration(String nodeGroup, String externalId, String syncUrl, Date notBefore, Date notAfter) {
        Node node = new Node();
        node.setExternalId(externalId);
        node.setNodeGroupId(nodeGroup);
        node.setSyncUrl(syncUrl);
        return openRegistration(node, null, null, notBefore, notAfter);
    }

    public synchronized String openRegistration(String nodeGroup, String externalId, String remoteHost, String remoteAddress) {
        Node node = new Node();
        node.setExternalId(externalId);
        node.setNodeGroupId(nodeGroup);
        return openRegistration(node, remoteHost, remoteAddress, null, null);
    }

    public synchronized String openRegistration(Node node) {
        return openRegistration(node, null, null, null, null);
    }

    protected String openRegistration(Node node, String remoteHost, String remoteAddress, Date notBefore, Date notAfter) {
        Node me = nodeService.findIdentity();
        if (me != null) {
            String nodeId = extensionService.getExtensionPoint(INodeIdCreator.class).generateNodeId(node, remoteHost, remoteAddress);
            if (parameterService.isRegistrationServer() && me.getNodeId().equals(nodeId)) {
                throw new IllegalStateException("Cannot open registration for a registration server");
            }
            if (StringUtils.isBlank(node.getNodeGroupId())) {
                throw new IllegalStateException("Missing node group ID in request from remote address " + remoteAddress);
            }
            Node existingNode = nodeService.findNode(nodeId);
            if (existingNode == null) {
                node.setNodeId(nodeId);
                node.setSyncEnabled(false);
                boolean masterToMasterOnly = configurationService.containsMasterToMaster();
                node.setCreatedAtNodeId(masterToMasterOnly ? null : me.getNodeId());
                nodeService.save(node);
                // make sure there isn't a node security row lying around w/out
                // a node row
                nodeService.deleteNodeSecurity(nodeId);
                String password = extensionService.getExtensionPoint(INodeIdCreator.class).generatePassword(node);
                password = filterPasswordOnSaveIfNeeded(password, nodeId);
                sqlTemplate.update(getSql("openRegistrationNodeSecuritySql"), new Object[] {
                        nodeId, password, notBefore, notAfter, me.getNodeId() });
                if (isNotBlank(remoteHost)) {
                    NodeHost nodeHost = new NodeHost(node.getNodeId(), null);
                    nodeHost.setHeartbeatTime(new Date());
                    nodeHost.setIpAddress(remoteAddress);
                    nodeHost.setHostName(remoteHost);
                    nodeService.updateNodeHost(nodeHost);
                }
                nodeService.flushNodeAuthorizedCache();
                nodeService.flushNodeCache();
                nodeService.insertNodeGroup(node.getNodeGroupId(), null);
                nodeService.flushNodeGroupCache();
                log.info(
                        "Just opened registration for external id of {} and a node group of {} and a node id of {}",
                        new Object[] { node.getExternalId(), node.getNodeGroupId(), nodeId });
            } else {
                reOpenRegistration(nodeId, remoteHost, remoteAddress, notBefore, notAfter, false);
            }
            return nodeId;
        } else {
            throw new IllegalStateException(
                    "This node has not been configured.  Could not find a row in the identity table");
        }
    }

    public boolean isAutoRegistration() {
        return parameterService.is(ParameterConstants.AUTO_REGISTER_ENABLED);
    }

    private String filterPasswordOnSaveIfNeeded(String password, String nodeId) {
        String s = password;
        INodePasswordFilter nodePasswordFilter = extensionService.getExtensionPoint(INodePasswordFilter.class);
        if (nodePasswordFilter != null) {
            s = nodePasswordFilter.onNodeSecuritySave(password, nodeId);
        }
        return s;
    }

    public boolean isRegistrationOpen(String nodeGroupId, String externalId) {
        Node node = nodeService.findNodeByExternalId(nodeGroupId, externalId);
        if (node != null) {
            NodeSecurity security = nodeService.findNodeSecurity(node.getNodeId());
            return security != null && security.isRegistrationEnabled() && security.isRegistrationAllowedNow();
        }
        return false;
    }

    public boolean isRegistrationOpen() {
        Node node = nodeService.findIdentity();
        NodeSecurity nodeSecurity = null;
        if (node != null) {
            nodeSecurity = nodeService.findNodeSecurity(node.getNodeId());
        }
        return nodeSecurity != null && nodeSecurity.isRegistrationEnabled() && nodeSecurity.isRegistrationAllowedNow();
    }

    public void requestNodeCopy() {
        Node copyFrom = nodeService.findIdentity();
        if (copyFrom == null) {
            throw new IllegalStateException("No identity found.  Can only copy if the node has an identity");
        }
        boolean copied = false;
        int maxNumberOfAttempts = parameterService
                .getInt(ParameterConstants.REGISTRATION_NUMBER_OF_ATTEMPTS);
        while (!copied && (maxNumberOfAttempts < 0 || maxNumberOfAttempts > 0)) {
            try {
                log.info("Detected that node '{}' should be copied to a new node id.  Attempting to contact server to accomplish this", copyFrom.getNodeId());
                copied = transportManager.sendCopyRequest(copyFrom) == WebConstants.SC_OK;
                if (copied) {
                    nodeService.deleteIdentity();
                }
            } catch (ConnectException e) {
                log.warn("The request to copy failed because the client failed to connect to the server");
            } catch (UnknownHostException e) {
                log.warn("The request to copy failed because the host was unknown");
            } catch (ConnectionRejectedException | ConnectionDuplicateException ex) {
                log.warn(
                        "The request to copy was rejected by the server.  Either the server node is not started, the server is not configured properly or the registration url is incorrect");
            } catch (Exception e) {
                log.error("", e);
            }
            maxNumberOfAttempts--;
            if (!copied) {
                long sleepTimeInMs = DateUtils.MILLIS_PER_SECOND
                        * randomTimeSlot.getRandomValueSeededByExternalId();
                log.warn("Copy failed.  Sleeping before attempting again.", sleepTimeInMs);
                log.info("Sleeping for {}ms", sleepTimeInMs);
                AppUtils.sleep(sleepTimeInMs);
            }
        }
        if (!copied) {
            throw new RegistrationFailedException(String.format(
                    "Failed after trying to copy %s times.",
                    parameterService.getString(ParameterConstants.REGISTRATION_NUMBER_OF_ATTEMPTS)));
        }
    }

    public void setAllowClientRegistration(boolean enabled) {
        this.allowClientRegistration = enabled;
    }

    static class RegistrationRequestMapper implements ISqlRowMapper<RegistrationRequest> {
        public RegistrationRequest mapRow(Row rs) {
            RegistrationRequest request = new RegistrationRequest();
            request.setNodeGroupId(rs.getString("node_group_id"));
            request.setExternalId(rs.getString("external_id"));
            request.setStatus(RegistrationStatus.valueOf(RegistrationStatus.class,
                    rs.getString("status")));
            request.setHostName(rs.getString("host_name"));
            request.setIpAddress(rs.getString("ip_address"));
            request.setAttemptCount(rs.getLong("attempt_count"));
            request.setRegisteredNodeId(rs.getString("registered_node_id"));
            request.setCreateTime(rs.getDateTime("create_time"));
            request.setLastUpdateBy(rs.getString("last_update_by"));
            request.setLastUpdateTime(rs.getDateTime("last_update_time"));
            request.setErrorMessage(rs.getString("error_message"));
            return request;
        }
    }
}
