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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class PushHeartbeatListenerTest {
    private static final String EXTERNAL_ID = "external-1";
    private static final String NODE_GROUP_ID = "test-group";
    private static final String SCHEMA_VERSION = "v1";
    private static final String DB_TYPE = "h2";
    private static final String DB_VERSION = "2.x";
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private INodeService nodeService;
    private IOutgoingBatchService outgoingBatchService;
    private IIncomingBatchService incomingBatchService;
    private IConfigurationService configurationService;
    private ISymmetricDialect symmetricDialect;
    private IDatabasePlatform databasePlatform;
    private IStatisticManager statisticManager;
    private IJobManager jobManager;
    private IDataService dataService;
    private PushHeartbeatListener listener;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        nodeService = mock(INodeService.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        incomingBatchService = mock(IIncomingBatchService.class);
        configurationService = mock(IConfigurationService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        databasePlatform = mock(IDatabasePlatform.class);
        statisticManager = mock(IStatisticManager.class);
        jobManager = mock(IJobManager.class);
        dataService = mock(IDataService.class);
        when(databasePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(databasePlatform.getName()).thenReturn(DB_TYPE);
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getIncomingBatchService()).thenReturn(incomingBatchService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getTargetDialect()).thenReturn(symmetricDialect);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getJobManager()).thenReturn(jobManager);
        when(engine.getDataService()).thenReturn(dataService);
        when(configurationService.getChannels(false)).thenReturn(Collections.emptyMap());
        listener = new PushHeartbeatListener(engine);
    }

    @Test
    void heartbeat_disabled() {
        when(parameterService.is(ParameterConstants.HEARTBEAT_ENABLED)).thenReturn(false);
        listener.heartbeat(unchangedNode());
        verify(outgoingBatchService, never()).cancelStaleHeartbeatBatches();
        verify(nodeService, never()).updateNodeHostForCurrentNode();
        verify(nodeService, never()).save(Mockito.any(Node.class));
    }

    @Test
    void heartbeat_cancelsStaleBatchesBeforeUpdatingNodeHost() {
        stubUnchangedHeartbeat();
        listener.heartbeat(unchangedNode());
        InOrder inOrder = Mockito.inOrder(outgoingBatchService, nodeService);
        inOrder.verify(outgoingBatchService).cancelStaleHeartbeatBatches();
        inOrder.verify(nodeService).updateNodeHostForCurrentNode();
    }

    @Test
    void heartbeat_unchangedNodeSkipsSaveAndCancelsAndUpdatesNodeHost() {
        stubUnchangedHeartbeat();
        listener.heartbeat(unchangedNode());
        verify(nodeService, never()).save(Mockito.any(Node.class));
        verify(outgoingBatchService).cancelStaleHeartbeatBatches();
        verify(nodeService).updateNodeHostForCurrentNode();
    }

    @Test
    void heartbeat_changedNodeSavesAndCancelsAndUpdatesNodeHost() {
        stubUnchangedHeartbeat();
        Node me = unchangedNode();
        me.setExternalId("different-external-id");
        listener.heartbeat(me);
        verify(nodeService).save(me);
        verify(outgoingBatchService).cancelStaleHeartbeatBatches();
        verify(nodeService).updateNodeHostForCurrentNode();
    }

    @Test
    void heartbeat_batchStatusEnabledAndSavesNode() {
        stubUnchangedHeartbeat();
        when(parameterService.is(ParameterConstants.HEARTBEAT_UPDATE_NODE_WITH_BATCH_STATUS, false)).thenReturn(true);
        stubGatherSources();
        listener.heartbeat(unchangedNode());
        verify(nodeService).save(any(Node.class));
    }

    @Test
    void heartbeat_nonRegistrationServerWithoutTriggerSupportInsertsHeartbeatEvent() {
        stubUnchangedHeartbeat();
        when(nodeService.isRegistrationServer()).thenReturn(false);
        DatabaseInfo info = new DatabaseInfo();
        info.setTriggersSupported(false);
        when(databasePlatform.getDatabaseInfo()).thenReturn(info);
        Node me = unchangedNode();
        me.setNodeId("me-id");
        Node child = new Node();
        child.setNodeId("child-1");
        when(nodeService.findNodesThatOriginatedFromNodeId("me-id")).thenReturn(Set.of(child));
        listener.heartbeat(me);
        verify(dataService).insertHeartbeatEvent(me, false);
        verify(dataService).insertHeartbeatEvent(child, false);
    }

    @Test
    void getTimeBetweenHeartbeatsInSeconds_returnsParameterValue() {
        when(parameterService.getLong(ParameterConstants.HEARTBEAT_SYNC_ON_PUSH_PERIOD_SEC)).thenReturn(120L);
        assertEquals(120L, listener.getTimeBetweenHeartbeatsInSeconds());
    }

    @Test
    void checkConfig_skipsCleanup() {
        when(parameterService.is(ParameterConstants.HEARTBEAT_ENABLED)).thenReturn(false);
        Channel heartbeatChannel = new Channel();
        heartbeatChannel.setChannelId(Constants.CHANNEL_HEARTBEAT);
        heartbeatChannel.setDescription(String.valueOf(System.currentTimeMillis() / 86400000L + 365));
        Map<String, Channel> channels = new HashMap<>();
        channels.put(Constants.CHANNEL_HEARTBEAT, heartbeatChannel);
        when(configurationService.getChannels(false)).thenReturn(channels);
        listener.heartbeat(unchangedNode());
        verify(engine, never()).getDataExtractorService();
        verify(parameterService, never()).deleteAllParameters();
    }

    private void stubUnchangedHeartbeat() {
        when(parameterService.is(ParameterConstants.HEARTBEAT_ENABLED)).thenReturn(true);
        when(parameterService.is(ParameterConstants.HEARTBEAT_UPDATE_NODE_WITH_BATCH_STATUS, false)).thenReturn(false);
        when(parameterService.getExternalId()).thenReturn(EXTERNAL_ID);
        when(parameterService.getNodeGroupId()).thenReturn(NODE_GROUP_ID);
        when(parameterService.getSyncUrl()).thenReturn(null);
        when(parameterService.getString(ParameterConstants.SCHEMA_VERSION, "")).thenReturn(SCHEMA_VERSION);
        when(parameterService.isRegistrationServer()).thenReturn(true);
        when(engine.getDeploymentType()).thenReturn(null);
        when(engine.getDeploymentSubType()).thenReturn(null);
        when(symmetricDialect.getName()).thenReturn(DB_TYPE);
        when(symmetricDialect.getVersion()).thenReturn(DB_VERSION);
        when(nodeService.isRegistrationServer()).thenReturn(true);
    }

    private void stubGatherSources() {
        when(outgoingBatchService.countOutgoingBatchesInError()).thenReturn(2);
        when(outgoingBatchService.countOutgoingNonSystemBatchesRowsUnsent()).thenReturn(new int[] { 5, 50 });
        when(outgoingBatchService.getOutgoingBatchesLatestUpdateSql()).thenReturn(new Date());
        when(incomingBatchService.countIncomingBatchesInError()).thenReturn(1);
        when(incomingBatchService.getIncomingBatchesLatestUpdateSql()).thenReturn(null);
        when(statisticManager.getMostRecentActiveTableSynced()).thenReturn("test_table");
        when(statisticManager.getTotalLoadedRows()).thenReturn(null);
        when(jobManager.isStarted()).thenReturn(false);
        when(dataService.countData()).thenReturn(1000);
    }

    private Node unchangedNode() {
        Node me = new Node();
        me.setExternalId(EXTERNAL_ID);
        me.setNodeGroupId(NODE_GROUP_ID);
        me.setSchemaVersion(SCHEMA_VERSION);
        me.setSymmetricVersion(Version.version());
        me.setConfigVersion(Version.version());
        me.setDatabaseType(DB_TYPE);
        me.setDatabaseVersion(DB_VERSION);
        return me;
    }
}
