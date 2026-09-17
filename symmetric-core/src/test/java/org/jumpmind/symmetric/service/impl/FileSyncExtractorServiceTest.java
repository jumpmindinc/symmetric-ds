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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeCommunication.CommunicationType;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.DataExtractorService.ExtractMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileSyncExtractorServiceTest {
    private IParameterService parameterService;
    private IFileSyncService fileSyncService;
    private IStagingManager stagingManager;
    private IConfigurationService configurationService;
    private INodeCommunicationService nodeCommunicationService;
    private FileSyncExtractorService fileSyncExtractorService;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(symmetricDialect.getName()).thenReturn("H2");
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<String, String>());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        fileSyncService = mock(IFileSyncService.class);
        stagingManager = mock(IStagingManager.class);
        configurationService = mock(IConfigurationService.class);
        nodeCommunicationService = mock(INodeCommunicationService.class);
        when(engine.getFileSyncService()).thenReturn(fileSyncService);
        when(engine.getNodeService()).thenReturn(mock(INodeService.class));
        when(engine.getStagingManager()).thenReturn(stagingManager);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getNodeCommunicationService()).thenReturn(nodeCommunicationService);
        when(engine.getExtensionService()).thenReturn(mock(IExtensionService.class));
        fileSyncExtractorService = new FileSyncExtractorService(engine);
    }

    @Test
    void testIsApplicable_withFileSyncEnabledAndAFileExtractRequest() {
        enableFileSync();
        assertTrue(fileSyncExtractorService.isApplicable(newNodeCommunication(CommunicationType.FILE_XTRCT)));
    }

    @Test
    void testIsApplicable_withFileSyncDisabled() {
        assertFalse(fileSyncExtractorService.isApplicable(newNodeCommunication(CommunicationType.FILE_XTRCT)));
    }

    @Test
    void testIsApplicable_withAnotherCommunicationType() {
        enableFileSync();
        assertFalse(fileSyncExtractorService.isApplicable(newNodeCommunication(CommunicationType.EXTRACT)));
    }

    @Test
    void testCanProcessExtractRequest_forTheFileSnapshotTable() {
        assertTrue(fileSyncExtractorService.canProcessExtractRequest(newExtractRequest("SYM_FILE_SNAPSHOT"), CommunicationType.FILE_XTRCT));
        assertTrue(fileSyncExtractorService.canProcessExtractRequest(newExtractRequest("sym_file_snapshot"), CommunicationType.FILE_XTRCT));
    }

    @Test
    void testCanProcessExtractRequest_forAnyOtherTable() {
        assertFalse(fileSyncExtractorService.canProcessExtractRequest(newExtractRequest("sym_data"), CommunicationType.FILE_XTRCT));
    }

    @Test
    void testWrapWithTransformWriter_returnsTheWriterUnchanged() {
        IDataWriter dataWriter = mock(IDataWriter.class);
        assertSame(dataWriter, fileSyncExtractorService.wrapWithTransformWriter(new Node("corp-000", "corp"), new Node("store-001", "store"), null,
                dataWriter, true));
    }

    @Test
    void testGetStagedResource_looksTheBatchUpByItsFileSyncPath() {
        OutgoingBatch batch = new OutgoingBatch("store-001", "filesync", OutgoingBatch.Status.NE);
        Object[] path = new Object[] { "store-001", "filesync", "1" };
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(fileSyncService.getStagingPathComponents(batch)).thenReturn(path);
        when(stagingManager.find(path)).thenReturn(stagedResource);
        assertSame(stagedResource, fileSyncExtractorService.getStagedResource(batch));
    }

    @Test
    void testGetProcessType() {
        assertEquals(ProcessType.FILE_SYNC_INITIAL_LOAD_EXTRACT_JOB, fileSyncExtractorService.getProcessType());
    }

    @Test
    void testExtractOutgoingBatch_withFileSyncDisabledReturnsNothing() {
        assertNull(fileSyncExtractorService.extractOutgoingBatch(null, new Node("store-001", "store"), mock(IDataWriter.class),
                new OutgoingBatch("store-001", "filesync", OutgoingBatch.Status.NE), false, false, ExtractMode.FOR_SYM_CLIENT, null));
    }

    @Test
    void testExtractOutgoingBatch_skipsChannelsThatAreNotForFileSync() {
        enableFileSync();
        OutgoingBatch batch = new OutgoingBatch("store-001", "default", OutgoingBatch.Status.NE);
        Channel channel = new Channel("default", 1);
        channel.setFileSyncFlag(false);
        when(configurationService.getChannel("default")).thenReturn(channel);
        assertNull(fileSyncExtractorService.extractOutgoingBatch(null, new Node("store-001", "store"), mock(IDataWriter.class), batch, false, false,
                ExtractMode.FOR_SYM_CLIENT, null));
    }

    @Test
    void testQueue_withFileSyncDisabledDoesNothing() {
        fileSyncExtractorService.queue("store-001", "default", new RemoteNodeStatuses(null));
        verify(nodeCommunicationService, never()).execute(any(NodeCommunication.class), any(), eq(fileSyncExtractorService));
    }

    @Test
    void testQueue_withAnAvailableThreadExecutesTheLock() {
        enableFileSync();
        NodeCommunication lock = newNodeCommunication(CommunicationType.FILE_XTRCT);
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.FILE_XTRCT)).thenReturn(1);
        when(nodeCommunicationService.find("store-001", "default", CommunicationType.FILE_XTRCT)).thenReturn(lock);
        RemoteNodeStatuses statuses = new RemoteNodeStatuses(null);
        fileSyncExtractorService.queue("store-001", "default", statuses);
        verify(nodeCommunicationService).execute(lock, statuses, fileSyncExtractorService);
    }

    @Test
    void testQueue_withoutAnAvailableThreadDoesNothing() {
        enableFileSync();
        when(nodeCommunicationService.getAvailableThreads(CommunicationType.FILE_XTRCT)).thenReturn(0);
        fileSyncExtractorService.queue("store-001", "default", new RemoteNodeStatuses(null));
        verify(nodeCommunicationService, never()).execute(any(NodeCommunication.class), any(), eq(fileSyncExtractorService));
    }

    private void enableFileSync() {
        when(parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)).thenReturn(true);
    }

    private NodeCommunication newNodeCommunication(CommunicationType communicationType) {
        NodeCommunication nodeCommunication = new NodeCommunication();
        nodeCommunication.setNodeId("store-001");
        nodeCommunication.setQueue("default");
        nodeCommunication.setCommunicationType(communicationType);
        return nodeCommunication;
    }

    private ExtractRequest newExtractRequest(String tableName) {
        ExtractRequest request = new ExtractRequest();
        request.setTableName(tableName);
        return request;
    }
}
