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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.config.INodeIdCreator;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.ext.INodeRegistrationAuthenticator;
import org.jumpmind.symmetric.ext.INodeRegistrationListener;
import org.jumpmind.symmetric.ext.IRegistrationRedirect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.model.RegistrationRequest.RegistrationStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus.Status;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.security.INodePasswordFilter;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IInitialLoadService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationRedirectException;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class RegistrationServiceTest {
    private static final String TEST_SERVER_NODE_ID = "server";
    private static final String TEST_SERVER_GROUP = "server-group";
    private static final String TEST_CLIENT_GROUP_NAME = "client";
    private static final String TEST_CLIENT_EXTERNAL_ID = "client-001";
    private static final String TEST_CLIENT_NODE_ID = "client-001-2";
    private static final String TEST_OTHER_CLIENT_NODE_ID = "client-001-3";
    private static final String TEST_REMOTE_HOST = "client-host";
    private static final String TEST_REMOTE_ADDRESS = "10.0.0.5";
    private static final long WAIT_SECONDS = 10;
    private ISymmetricEngine engine;
    private RegistrationService service;
    private ISqlTemplate sqlTemplate;
    private INodeService nodeService;
    private ITransportManager transportManager;
    private IExtensionService extensionService;
    private IConfigurationService configurationService;
    private IParameterService parameterService;
    private ICacheManager cacheManager;
    private ISqlTransaction sqlTransaction;
    private INodeIdCreator nodeIdCreator;
    private ITriggerRouterService triggerRouterService;
    private IDataExtractorService dataExtractorService;
    private IInitialLoadService initialLoadService;
    private IDataLoaderService dataLoaderService;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        nodeService = mock(INodeService.class);
        transportManager = mock(ITransportManager.class);
        extensionService = mock(IExtensionService.class);
        configurationService = mock(IConfigurationService.class);
        cacheManager = mock(ICacheManager.class);
        sqlTransaction = mock(ISqlTransaction.class);
        nodeIdCreator = mock(INodeIdCreator.class);
        triggerRouterService = mock(ITriggerRouterService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        initialLoadService = mock(IInitialLoadService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn("");
        when(parameterService.getExternalId()).thenReturn("test");
        when(parameterService.getInt(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES, 30)).thenReturn(30);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<>());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        when(engine.getDataService()).thenReturn(mock(IDataService.class));
        dataLoaderService = mock(IDataLoaderService.class);
        when(engine.getDataLoaderService()).thenReturn(dataLoaderService);
        when(engine.getTransportManager()).thenReturn(transportManager);
        when(engine.getStatisticManager()).thenReturn(mock(IStatisticManager.class));
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getOutgoingBatchService()).thenReturn(mock(IOutgoingBatchService.class));
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getInitialLoadService()).thenReturn(initialLoadService);
        when(sqlTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(cacheManager.isUsingTargetExternalId(anyBoolean())).thenReturn(false);
        when(extensionService.getExtensionPoint(INodeIdCreator.class)).thenReturn(nodeIdCreator);
        when(extensionService.getExtensionPoint(IRegistrationRedirect.class)).thenReturn(null);
        when(extensionService.getExtensionPoint(INodePasswordFilter.class)).thenReturn(null);
        when(extensionService.getExtensionPointList(INodeRegistrationAuthenticator.class)).thenReturn(Collections.emptyList());
        when(extensionService.getExtensionPointList(INodeRegistrationListener.class)).thenReturn(Collections.emptyList());
        when(nodeIdCreator.generateNodeId(any(), any(), any())).thenReturn(TEST_CLIENT_EXTERNAL_ID);
        when(nodeIdCreator.selectNodeId(any(), any(), any())).thenReturn(TEST_CLIENT_EXTERNAL_ID);
        when(nodeIdCreator.generatePassword(any())).thenReturn("password123");
        when(parameterService.getRegistrationUrl()).thenReturn("http://registration");
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(Collections.emptyList());
        service = new RegistrationService(engine);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void registerNodeSkipsSecondRegistrationWhileFirstOneIsInProgress() throws Exception {
        setupOpenRegistrationFor(TEST_CLIENT_NODE_ID);
        CountDownLatch extractStarted = new CountDownLatch(1);
        CountDownLatch releaseExtract = new CountDownLatch(1);
        blockConfigurationExtract(extractStarted, releaseExtract);
        Future<Boolean> firstRegistration = executor.submit(() -> registerClient(TEST_CLIENT_NODE_ID));
        assertTrue(extractStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
        boolean secondRegistration = registerClient(TEST_CLIENT_NODE_ID);
        releaseExtract.countDown();
        assertFalse(secondRegistration);
        assertTrue(firstRegistration.get(WAIT_SECONDS, TimeUnit.SECONDS));
        verify(nodeService, times(1)).save(any(Node.class));
        verify(dataExtractorService, times(1)).extractConfigurationStandalone(any(Node.class), any(Writer.class), any(String[].class));
    }

    @Test
    void registerNodeAllowsConcurrentRegistrationOfDifferentNodes() throws Exception {
        setupOpenRegistrationFor(TEST_CLIENT_NODE_ID);
        setupOpenRegistrationFor(TEST_OTHER_CLIENT_NODE_ID);
        CountDownLatch extractStarted = new CountDownLatch(1);
        CountDownLatch releaseExtract = new CountDownLatch(1);
        blockConfigurationExtract(extractStarted, releaseExtract);
        Future<Boolean> firstRegistration = executor.submit(() -> registerClient(TEST_CLIENT_NODE_ID));
        assertTrue(extractStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
        Future<Boolean> otherRegistration = executor.submit(() -> registerClient(TEST_OTHER_CLIENT_NODE_ID));
        releaseExtract.countDown();
        assertTrue(firstRegistration.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(otherRegistration.get(WAIT_SECONDS, TimeUnit.SECONDS));
        verify(nodeService, times(2)).save(any(Node.class));
    }

    @Test
    void registerNodeAllowsRegistrationAgainAfterPreviousOneCompletes() throws IOException {
        setupOpenRegistrationFor(TEST_CLIENT_NODE_ID);
        assertTrue(registerClient(TEST_CLIENT_NODE_ID));
        assertTrue(registerClient(TEST_CLIENT_NODE_ID));
        verify(nodeService, times(2)).save(any(Node.class));
    }

    @Test
    void registerNodeReleasesInProgressMarkerWhenRegistrationFails() throws IOException {
        setupOpenRegistrationFor(TEST_CLIENT_NODE_ID);
        doAnswer(invocation -> {
            throw new IOException("client went away");
        }).when(dataExtractorService).extractConfigurationStandalone(any(Node.class), any(Writer.class), any(String[].class));
        boolean failed = false;
        try {
            registerClient(TEST_CLIENT_NODE_ID);
        } catch (IOException ex) {
            failed = true;
        }
        assertTrue(failed);
        doAnswer(invocation -> null).when(dataExtractorService).extractConfigurationStandalone(any(Node.class), any(Writer.class), any(String[].class));
        assertTrue(registerClient(TEST_CLIENT_NODE_ID));
    }

    @Test
    void registerNodeClearsAbandonedAttemptOlderThanRetryInterval() throws Exception {
        setupOpenRegistrationFor(TEST_CLIENT_NODE_ID);
        when(parameterService.getInt(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES, 30)).thenReturn(0);
        CountDownLatch extractStarted = new CountDownLatch(1);
        CountDownLatch releaseExtract = new CountDownLatch(1);
        blockConfigurationExtract(extractStarted, releaseExtract);
        Future<Boolean> abandonedRegistration = executor.submit(() -> registerClient(TEST_CLIENT_NODE_ID));
        assertTrue(extractStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
        releaseExtract.countDown();
        assertTrue(registerClient(TEST_CLIENT_NODE_ID));
        assertTrue(abandonedRegistration.get(WAIT_SECONDS, TimeUnit.SECONDS));
        verify(nodeService, times(2)).save(any(Node.class));
    }

    @Test
    void buildRegistrationKeyUsesNodeIdWhenPresent() {
        assertEquals(TEST_CLIENT_NODE_ID, service.buildRegistrationKey(buildClientNodeWithId(TEST_CLIENT_NODE_ID)));
    }

    @Test
    void buildRegistrationKeyFallsBackToGroupAndExternalIdWhenNodeIdMissing() {
        assertEquals(TEST_CLIENT_GROUP_NAME + ":" + TEST_CLIENT_EXTERNAL_ID, service.buildRegistrationKey(buildClientNodeWithId(null)));
    }

    @Test
    void saveRegistrationRequestIgnoresDuplicateKeyFromConcurrentInsert() {
        when(sqlTemplate.update(argThat(this::isInsertStatement), any(Object[].class), any(int[].class)))
                .thenThrow(new UniqueKeyException("duplicate key (client, 11498, 2026-09-10 12:30:16.55)"));
        service.saveRegistrationRequest(buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK));
        verify(sqlTemplate, times(1)).update(argThat(this::isInsertStatement), any(Object[].class), any(int[].class));
    }

    @Test
    void reRegisterWithServerRunsOnceWhenCalledConcurrently() throws Exception {
        CountDownLatch pullStarted = new CountDownLatch(1);
        CountDownLatch releasePull = new CountDownLatch(1);
        blockRegistrationPull(pullStarted, releasePull, Status.DATA_PROCESSED);
        Future<Boolean> firstAttempt = executor.submit(() -> service.reRegisterWithServer("first"));
        assertTrue(pullStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(service.isRegistrationInProgress());
        assertFalse(service.reRegisterWithServer("second"));
        releasePull.countDown();
        assertTrue(firstAttempt.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(service.isRegistrationInProgress());
        verify(dataLoaderService, times(1)).loadDataFromPull(isNull(), (String) isNull());
    }

    @Test
    void reRegisterWithServerSkipsSecondAttemptWithinRetryInterval() throws IOException {
        stubRegistrationPull(Status.DATA_PROCESSED);
        assertTrue(service.reRegisterWithServer("first"));
        assertFalse(service.reRegisterWithServer("second"));
        verify(dataLoaderService, times(1)).loadDataFromPull(isNull(), (String) isNull());
    }

    @Test
    void reRegisterWithServerRunsAgainAfterRetryIntervalElapsed() throws IOException {
        when(parameterService.getInt(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES, 30)).thenReturn(0);
        stubRegistrationPull(Status.DATA_PROCESSED);
        assertTrue(service.reRegisterWithServer("first"));
        assertTrue(service.reRegisterWithServer("second"));
        verify(dataLoaderService, times(2)).loadDataFromPull(isNull(), (String) isNull());
    }

    @Test
    void reRegisterWithServerReturnsFalseAndReleasesLockWhenPullFails() throws IOException {
        when(parameterService.getInt(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES, 30)).thenReturn(0);
        doThrow(new IOException("server unreachable")).when(dataLoaderService).loadDataFromPull(isNull(), (String) isNull());
        assertFalse(service.reRegisterWithServer("first"));
        assertFalse(service.isRegistrationInProgress());
        stubRegistrationPull(Status.DATA_PROCESSED);
        assertTrue(service.reRegisterWithServer("second"));
    }

    @Test
    void reRegisterWithServerReturnsFalseWhenServerDidNotSendRegistration() throws IOException {
        stubRegistrationPull(Status.NO_DATA);
        assertFalse(service.reRegisterWithServer("first"));
    }

    @Test
    void removeIdentityForReRegistrationDeletesIdentityOnce() {
        when(nodeService.findIdentity(false)).thenReturn(buildClientNodeWithId(TEST_CLIENT_NODE_ID), (Node) null);
        Node remote = buildIdentityNode();
        assertTrue(service.removeIdentityForReRegistration(remote, "push"));
        assertFalse(service.removeIdentityForReRegistration(remote, "push"));
        verify(nodeService, times(1)).deleteIdentity();
        verify(nodeService, times(1)).deleteNodeSecurity(TEST_CLIENT_NODE_ID);
        verify(nodeService, times(1)).deleteNode(TEST_CLIENT_NODE_ID, TEST_SERVER_NODE_ID, false);
    }

    @Test
    void removeIdentityForReRegistrationIsSkippedWhileReRegistering() throws Exception {
        when(nodeService.findIdentity(false)).thenReturn(buildClientNodeWithId(TEST_CLIENT_NODE_ID));
        CountDownLatch pullStarted = new CountDownLatch(1);
        CountDownLatch releasePull = new CountDownLatch(1);
        blockRegistrationPull(pullStarted, releasePull, Status.DATA_PROCESSED);
        Future<Boolean> registration = executor.submit(() -> service.reRegisterWithServer("pull"));
        assertTrue(pullStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(service.removeIdentityForReRegistration(buildIdentityNode(), "push"));
        releasePull.countDown();
        assertTrue(registration.get(WAIT_SECONDS, TimeUnit.SECONDS));
        verify(nodeService, never()).deleteIdentity();
    }

    @Test
    void clientRegistrationDisallowedWhenFlagOff() throws IOException {
        service.setAllowClientRegistration(false);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void registerNodeDelegatesTo7ArgVersionWhenSyncDisabled() throws IOException {
        service.setAllowClientRegistration(false);
        Node node = new Node();
        node.setNodeGroupId(TEST_CLIENT_GROUP_NAME);
        boolean result = service.registerNode(node, new ByteArrayOutputStream(), false);
        assertFalse(result);
    }

    @Test
    void processRegistrationReturnsNotSyncedWhenIdentityNull() throws IOException {
        when(nodeService.findIdentity()).thenReturn(null);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void processRegistrationBlocksWhenInitialLoadNotComplete() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.findNodeSecurity(TEST_SERVER_NODE_ID)).thenReturn(new NodeSecurity());
        when(nodeService.isRegistrationServer()).thenReturn(false);
        when(parameterService.is(ParameterConstants.REGISTRATION_REQUIRE_INITIAL_LOAD, true)).thenReturn(true);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void processRegistrationBlocksWhenNoNodeGroupLinkExists() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.findNodeSecurity(TEST_SERVER_NODE_ID)).thenReturn(new NodeSecurity());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false)).thenReturn(null);
        when(parameterService.is(ParameterConstants.REGISTRATION_REQUIRE_NODE_GROUP_LINK, true)).thenReturn(true);
        when(parameterService.is(ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK)).thenReturn(false);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void processRegistrationRegistersNodeSuccessfully() throws IOException {
        setupHappyPath();
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertTrue(result.isSyncEnabled());
        verify(nodeService, atLeast(1)).flushNodeAuthorizedCache();
    }

    @Test
    void processRegistrationWithAutoReloadEnabledCallsInitialLoadService() throws IOException {
        setupHappyPath();
        when(parameterService.is(ParameterConstants.AUTO_RELOAD_ENABLED)).thenReturn(true);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertTrue(result.isSyncEnabled());
        verify(nodeService).setInitialLoadEnabled(TEST_CLIENT_EXTERNAL_ID, true, false, -1, "registration");
    }

    @Test
    void processRegistrationOpensAutoRegistrationWhenNodeNotFound() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.findNodeSecurity(TEST_SERVER_NODE_ID)).thenReturn(new NodeSecurity());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false))
                .thenReturn(new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME));
        when(parameterService.is(ParameterConstants.AUTO_REGISTER_ENABLED)).thenReturn(true);
        Node foundNode = buildClientNode();
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null, null, foundNode, null);
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null, buildClientSecurity());
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertTrue(result.isSyncEnabled());
    }

    @Test
    void processRegistrationAutoCreatesGroupLinkAndRouters() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.findNodeSecurity(TEST_SERVER_NODE_ID)).thenReturn(new NodeSecurity());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false)).thenReturn(null);
        when(parameterService.is(ParameterConstants.REGISTRATION_REQUIRE_NODE_GROUP_LINK, true)).thenReturn(true);
        when(parameterService.is(ParameterConstants.REGISTRATION_AUTO_CREATE_GROUP_LINK)).thenReturn(true);
        when(parameterService.is(ParameterConstants.AUTO_REGISTER_ENABLED)).thenReturn(true);
        Node foundNode = buildClientNode();
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null, null, foundNode, null);
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null, buildClientSecurity());
        when(configurationService.getNodeGroupLinkFor(TEST_CLIENT_GROUP_NAME, TEST_SERVER_GROUP, false)).thenReturn(null);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertTrue(result.isSyncEnabled());
        verify(triggerRouterService, times(2)).saveRouter(any(Router.class));
    }

    @Test
    void registerNodeIgnoresPullWhenPushLinkConfigured() throws IOException {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(parameterService.getNodeGroupId()).thenReturn(TEST_SERVER_GROUP);
        NodeGroupLink pushLink = new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME);
        pushLink.setDataEventAction(NodeGroupLinkAction.P);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false)).thenReturn(pushLink);
        NodeSecurity registeredSecurity = buildClientSecurity();
        registeredSecurity.setRegistrationTime(new Date());
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(registeredSecurity);
        Node clientNode = buildClientNode();
        clientNode.setSyncUrl("http://client/sync");
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(clientNode);
        boolean result = service.registerNode(clientNode, null, null, new ByteArrayOutputStream(), null, null, false);
        assertTrue(result);
        verify(nodeService, never()).findIdentity();
    }

    @Test
    void isRegistrationPushedToNodeIsFalseWhenPushConfigNotAllowed() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(false);
        assertFalse(service.isRegistrationPushedToNode(buildClientNode(), null, null));
        verify(configurationService, never()).getNodeGroupLinkFor(anyString(), anyString(), anyBoolean());
    }

    @Test
    void isRegistrationPushedToNodeIsFalseWhenGroupLinkIsPull() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(parameterService.getNodeGroupId()).thenReturn(TEST_SERVER_GROUP);
        NodeGroupLink pullLink = new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME);
        pullLink.setDataEventAction(NodeGroupLinkAction.W);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false)).thenReturn(pullLink);
        assertFalse(service.isRegistrationPushedToNode(buildClientNode(), null, null));
        verify(nodeService, never()).findNodeSecurity(anyString());
    }

    @Test
    void isRegistrationPushedToNodeIsFalseWhenRegistrationNotPending() {
        setupPushLinkForClient();
        NodeSecurity openSecurity = buildClientSecurity();
        openSecurity.setRegistrationTime(null);
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(openSecurity);
        assertFalse(service.isRegistrationPushedToNode(buildClientNode(), null, null));
    }

    @Test
    void isRegistrationPushedToNodeIsFalseWhenNodeHasNoSyncUrl() {
        setupPushLinkForClient();
        NodeSecurity pendingSecurity = buildClientSecurity();
        pendingSecurity.setRegistrationTime(new Date());
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(pendingSecurity);
        Node clientNode = buildClientNode();
        clientNode.setSyncUrl("");
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(clientNode);
        assertFalse(service.isRegistrationPushedToNode(clientNode, null, null));
    }

    @Test
    void isRegistrationPushedToNodeIsTrueWhenPushLinkAndPendingRegistrationWithSyncUrl() {
        setupPushLinkForClient();
        NodeSecurity pendingSecurity = buildClientSecurity();
        pendingSecurity.setRegistrationTime(new Date());
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(pendingSecurity);
        Node clientNode = buildClientNode();
        clientNode.setSyncUrl("http://client/sync");
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(clientNode);
        assertTrue(service.isRegistrationPushedToNode(clientNode, null, null));
    }

    @Test
    void registerNodeExtractsConfigurationWhenSyncEnabled() throws IOException {
        setupHappyPath();
        Node node = buildClientNode();
        boolean result = service.registerNode(node, null, null, new ByteArrayOutputStream(), null, null, true);
        assertTrue(result);
    }

    @Test
    void deleteRegistrationRequestCallsSqlUpdate() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        service.deleteRegistrationRequest(request);
        verify(sqlTemplate, atLeast(1)).update(any(String.class), any(), any(), any(), any());
    }

    @Test
    void saveRegistrationRequestInsertsWhenNoPriorEntry() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        service.saveRegistrationRequest(request);
        verify(sqlTemplate, atLeast(1)).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void saveRegistrationRequestUpdatesInPlaceWhenRejectingPendingRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        doReturn(new ArrayList<>(Collections.singletonList(prior))).when(sqlTemplate).query(anyString(),
                any(ISqlRowMapper.class), any(Object[].class), any(int[].class));
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        request.setStatus(RegistrationStatus.RJ);
        service.saveRegistrationRequest(request);
        verify(sqlTemplate, times(1)).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void updateRegistrationRequestCallsSqlUpdate() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        service.updateRegistrationRequest(request);
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void getRedirectionUrlForReturnsNullWhenNoResults() {
        String result = service.getRedirectionUrlFor(TEST_CLIENT_EXTERNAL_ID);
        assertNull(result);
    }

    @Test
    void getRedirectionUrlForReturnsResolvedUrlWhenEntryFound() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(Collections.singletonList("http://server/sync"));
        when(transportManager.resolveURL("http://server/sync", "http://registration")).thenReturn("http://server/sync");
        String result = service.getRedirectionUrlFor(TEST_CLIENT_EXTERNAL_ID);
        assertEquals("http://server/sync", result);
    }

    @Test
    void saveRegistrationRedirectInsertsWhenUpdateFindsNoExistingEntry() {
        when(sqlTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(0);
        service.saveRegistrationRedirect(TEST_CLIENT_EXTERNAL_ID, "node-001");
        verify(sqlTemplate, times(2)).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void getRegistrationRequestsFiltersNodesWithOpenRegistration() {
        RegistrationRequest req = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        doReturn(new ArrayList<>(Collections.singletonList(req))).when(sqlTemplate).query(anyString(), any(ISqlRowMapper.class));
        Node openNode = new Node();
        openNode.setNodeGroupId(TEST_CLIENT_GROUP_NAME);
        openNode.setExternalId(TEST_CLIENT_EXTERNAL_ID);
        when(nodeService.findNodesWithOpenRegistration()).thenReturn(Collections.singletonList(openNode));
        List<RegistrationRequest> result = service.getRegistrationRequests(false, false);
        assertTrue(result.isEmpty());
    }

    @Test
    void markNodeAsRegisteredUpdatesSecurityAndNotifiesListeners() {
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null);
        service.markNodeAsRegistered(TEST_CLIENT_EXTERNAL_ID);
        verify(nodeService).flushNodeAuthorizedCache();
    }

    @Test
    void nullPriorRequestReturnsZeroWithNoUpdate() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, null);
        assertEquals(0, result);
        verify(sqlTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void sameStatusCombinesAttemptCountsAndCallsUpdate() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        prior.setAttemptCount(3);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(4, request.getAttemptCount());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void rejectedPriorWithPendingCurrentSetsStatusToRejectedAndUpdates() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.RJ, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void errorPriorWithOkCurrentSetsStatusToErrorAndUpdates() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.ER);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.ER, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void pendingPriorWithOkCurrentMergesWithoutOverwritingStatus() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.OK, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void rejectedPriorWithOkCurrentMergesWithoutOverwritingStatus() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.OK, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void okPriorWithRejectedCurrentNoMerge() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(0, result);
        verify(sqlTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void pendingPriorWithRejectedCurrentUpdatesInPlace() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        prior.setAttemptCount(2);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.RJ, request.getStatus());
        assertEquals(3, request.getAttemptCount());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void errorPriorWithRejectedCurrentUpdatesInPlace() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.ER);
        prior.setAttemptCount(4);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.RJ, request.getStatus());
        assertEquals(5, request.getAttemptCount());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void processRegistrationCallsCustomRedirectExtensionWhenRegistered() {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        IRegistrationRedirect redirectExtension = mock(IRegistrationRedirect.class);
        when(extensionService.getExtensionPoint(IRegistrationRedirect.class)).thenReturn(redirectExtension);
        when(redirectExtension.getRedirectionUrlFor(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME)).thenReturn("http://redirect");
        assertThrows(RegistrationRedirectException.class,
                () -> service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb"));
        verify(redirectExtension).getRedirectionUrlFor(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME);
    }

    @Test
    void processRegistrationThrowsRedirectExceptionWhenBuiltInRedirectFound() {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(sqlTemplate.query(
                argThat(sql -> sql != null && sql.contains("registration_redirect")),
                any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(Collections.singletonList("http://redirect"));
        when(transportManager.resolveURL("http://redirect", "http://registration")).thenReturn("http://redirect");
        assertThrows(RegistrationRedirectException.class,
                () -> service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb"));
    }

    @Test
    void processRegistrationReturnsNotSyncedWhenNodeGroupIdBlank() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, "", "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void processRegistrationChecksAuthenticatorsWhenCredentialsProvided() throws IOException {
        setupHappyPath();
        INodeRegistrationAuthenticator authenticator = mock(INodeRegistrationAuthenticator.class);
        when(extensionService.getExtensionPointList(INodeRegistrationAuthenticator.class))
                .thenReturn(Collections.singletonList(authenticator));
        when(authenticator.authenticate(anyString(), anyString())).thenReturn(false);
        service.registerNode(buildClientNode(), null, null, new ByteArrayOutputStream(), "user", "pass", false);
        verify(authenticator).authenticate("user", "pass");
    }

    @Test
    void processRegistrationSavesRequestAndReturnsFalseWhenManualRegistrationRequired() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false))
                .thenReturn(new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME));
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null);
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(null);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void processRegistrationWithReverseReloadEnabledCallsReverseInitialLoad() throws IOException {
        setupHappyPath();
        when(parameterService.is(ParameterConstants.AUTO_RELOAD_REVERSE_ENABLED)).thenReturn(true);
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertTrue(result.isSyncEnabled());
        verify(nodeService).setReverseInitialLoadEnabled(TEST_CLIENT_EXTERNAL_ID, true, false, -1, "registration");
    }

    @Test
    void processRegistrationReturnsNotSyncedWhenRegistrationNotOpen() throws IOException {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(nodeIdCreator.selectNodeId(any(), any(), any()))
                .thenThrow(new RegistrationNotOpenException("registration not open"));
        Node result = service.registerPullOnlyNode(TEST_CLIENT_EXTERNAL_ID, TEST_CLIENT_GROUP_NAME, "H2", "1.0", "testdb");
        assertFalse(result.isSyncEnabled());
    }

    @Test
    void registerNodePushConfigCallsSelectNodeIdWhenNodeIdBlank() throws IOException {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(parameterService.getNodeGroupId()).thenReturn(TEST_SERVER_GROUP);
        NodeGroupLink pushLink = new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME);
        pushLink.setDataEventAction(NodeGroupLinkAction.P);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false)).thenReturn(pushLink);
        NodeSecurity registeredSecurity = buildClientSecurity();
        registeredSecurity.setRegistrationTime(new Date());
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(registeredSecurity);
        Node foundNode = buildClientNode();
        foundNode.setSyncUrl("http://client/sync");
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(foundNode);
        Node clientNode = new Node();
        clientNode.setNodeGroupId(TEST_CLIENT_GROUP_NAME);
        boolean result = service.registerNode(clientNode, null, null, new ByteArrayOutputStream(), null, null, false);
        assertTrue(result);
        verify(nodeIdCreator).selectNodeId(any(), any(), any());
    }

    @Test
    void getRegistrationRequestsIncludesRejectsInSqlWhenFlagTrue() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        doReturn(Collections.emptyList()).when(sqlTemplate).query(anyString(), any(ISqlRowMapper.class));
        service.getRegistrationRequests(true, true);
        verify(sqlTemplate).query(sqlCaptor.capture(), any(ISqlRowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("'RJ'"));
    }

    @Test
    void getLatestRegistrationRequestReturnsNewestRequest() {
        RegistrationRequest older = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        older.setCreateTime(new Date(1000L));
        RegistrationRequest newer = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        newer.setCreateTime(new Date(2000L));
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(Arrays.asList(older, newer));
        RegistrationRequest result = service.getLatestRegistrationRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        assertEquals(newer, result);
    }

    @Test
    void deleteRegistrationRedirectsByNodeIdCallsSqlUpdate() {
        service.deleteRegistrationRedirectsByNodeId("node-42");
        verify(sqlTemplate).update(anyString(), eq("node-42"));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsTrueWhenSameStatus() {
        for (RegistrationStatus status : RegistrationStatus.values()) {
            RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, status);
            RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, status);
            assertTrue(service.isPriorRequestSupersededByNew(prior, request));
        }
    }

    @Test
    void isPriorRequestSupersededByNewReturnsTrueWhenRejectedPriorAndPendingRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        assertTrue(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsTrueWhenErrorPriorAndOkRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.ER);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        assertTrue(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsFalseWhenPendingPriorAndOkRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        assertFalse(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsFalseWhenRejectedPriorAndOkRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        assertFalse(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsFalseWhenErrorPriorAndPendingRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.ER);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        assertFalse(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsFalseWhenOkPriorAndPendingRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        assertFalse(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsFalseWhenPendingPriorAndRejectedRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        assertFalse(service.isPriorRequestSupersededByNew(prior, request));
    }

    @Test
    void isPriorRequestSupersededByNewReturnsFalseWhenErrorPriorAndRejectedRequest() {
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.ER);
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        assertFalse(service.isPriorRequestSupersededByNew(prior, request));
    }

    private void setupPushLinkForClient() {
        when(parameterService.is(ParameterConstants.REGISTRATION_PUSH_CONFIG_ALLOWED)).thenReturn(true);
        when(parameterService.getNodeGroupId()).thenReturn(TEST_SERVER_GROUP);
        NodeGroupLink pushLink = new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME);
        pushLink.setDataEventAction(NodeGroupLinkAction.P);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false)).thenReturn(pushLink);
    }

    private void setupOpenRegistrationFor(String nodeId) {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.findIdentityNodeId()).thenReturn(TEST_SERVER_NODE_ID);
        when(nodeService.findNodeSecurity(TEST_SERVER_NODE_ID)).thenReturn(new NodeSecurity());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false))
                .thenReturn(new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME));
        NodeSecurity clientSecurity = buildClientSecurity();
        clientSecurity.setNodeId(nodeId);
        clientSecurity.setCreatedAtNodeId(TEST_SERVER_NODE_ID);
        when(nodeService.findNode(nodeId)).thenReturn(buildClientNodeWithId(nodeId));
        when(nodeService.findNodeSecurity(nodeId)).thenReturn(clientSecurity);
    }

    private boolean registerClient(String nodeId) throws IOException {
        return service.registerNode(buildClientNodeWithId(nodeId), TEST_REMOTE_HOST, TEST_REMOTE_ADDRESS, new ByteArrayOutputStream(), null, null, false);
    }

    private void blockConfigurationExtract(CountDownLatch extractStarted, CountDownLatch releaseExtract) {
        doAnswer(invocation -> {
            extractStarted.countDown();
            assertTrue(releaseExtract.await(WAIT_SECONDS, TimeUnit.SECONDS));
            return null;
        }).when(dataExtractorService).extractConfigurationStandalone(any(Node.class), any(Writer.class), any(String[].class));
    }

    private void stubRegistrationPull(Status status) throws IOException {
        doReturn(buildRemoteNodeStatus(status)).when(dataLoaderService).loadDataFromPull(isNull(), (String) isNull());
    }

    private void blockRegistrationPull(CountDownLatch pullStarted, CountDownLatch releasePull, Status status) throws IOException {
        doAnswer(invocation -> {
            pullStarted.countDown();
            assertTrue(releasePull.await(WAIT_SECONDS, TimeUnit.SECONDS));
            return buildRemoteNodeStatus(status);
        }).when(dataLoaderService).loadDataFromPull(isNull(), (String) isNull());
    }

    private RemoteNodeStatus buildRemoteNodeStatus(Status status) {
        RemoteNodeStatus remoteNodeStatus = new RemoteNodeStatus(null, null, Collections.emptyMap());
        remoteNodeStatus.setStatus(status);
        return remoteNodeStatus;
    }

    private Node buildClientNodeWithId(String nodeId) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setNodeGroupId(TEST_CLIENT_GROUP_NAME);
        node.setExternalId(TEST_CLIENT_EXTERNAL_ID);
        node.setSyncUrl("http://client/sync");
        return node;
    }

    private boolean isInsertStatement(String sql) {
        return sql != null && sql.trim().startsWith("insert");
    }

    private void setupHappyPath() {
        when(nodeService.findIdentity()).thenReturn(buildIdentityNode());
        when(nodeService.findNodeSecurity(TEST_SERVER_NODE_ID)).thenReturn(new NodeSecurity());
        when(nodeService.isRegistrationServer()).thenReturn(true);
        when(configurationService.getNodeGroupLinkFor(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME, false))
                .thenReturn(new NodeGroupLink(TEST_SERVER_GROUP, TEST_CLIENT_GROUP_NAME));
        Node foundNode = buildClientNode();
        when(nodeService.findNode(TEST_CLIENT_EXTERNAL_ID)).thenReturn(foundNode, (Node) null);
        when(nodeService.findNodeSecurity(TEST_CLIENT_EXTERNAL_ID)).thenReturn(buildClientSecurity());
    }

    private Node buildIdentityNode() {
        Node identity = new Node();
        identity.setNodeId(TEST_SERVER_NODE_ID);
        identity.setNodeGroupId(TEST_SERVER_GROUP);
        return identity;
    }

    private Node buildClientNode() {
        Node node = new Node();
        node.setNodeId(TEST_CLIENT_EXTERNAL_ID);
        node.setNodeGroupId(TEST_CLIENT_GROUP_NAME);
        node.setExternalId(TEST_CLIENT_EXTERNAL_ID);
        node.setSyncEnabled(true);
        return node;
    }

    private NodeSecurity buildClientSecurity() {
        NodeSecurity security = new NodeSecurity();
        security.setRegistrationEnabled(true);
        return security;
    }

    private RegistrationRequest buildRequest(String groupName, String externalId, RegistrationStatus status) {
        RegistrationRequest req = new RegistrationRequest();
        req.setNodeGroupId(groupName);
        req.setExternalId(externalId);
        req.setHostName("localhost");
        req.setStatus(status);
        return req;
    }
}
