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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.file.FileSyncBatchEnvelope;
import org.jumpmind.symmetric.file.FileSyncPullResult;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeCommunication;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.http.IHttpResumeCache;
import org.jumpmind.symmetric.transport.http.ResumeCacheEntry;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FileSyncServiceTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IStagingManager stagingManager;
    private IOutgoingBatchService outgoingBatchService;
    private IHttpResumeCache resumeCache;
    private FileSyncService fileSyncService;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getTempDirectory()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(engine.getParameterService()).thenReturn(parameterService);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ISqlTemplate sqlTemplateDirty = mock(ISqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        ICacheManager cacheManager = mock(ICacheManager.class);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        stagingManager = mock(IStagingManager.class);
        when(engine.getStagingManager()).thenReturn(stagingManager);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(configurationService.getChannel(anyString())).thenReturn(new Channel());
        when(engine.getConfigurationService()).thenReturn(configurationService);
        DataExtractorService dataExtractorService = mock(DataExtractorService.class);
        when(engine.getDataExtractorService()).thenReturn(dataExtractorService);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        INodeService nodeService = mock(INodeService.class);
        when(nodeService.findIdentityNodeId()).thenReturn("localNode");
        when(engine.getNodeService()).thenReturn(nodeService);
        ITransportManager transportManager = mock(ITransportManager.class);
        resumeCache = mock(IHttpResumeCache.class);
        when(transportManager.getResumeCache()).thenReturn(resumeCache);
        when(engine.getTransportManager()).thenReturn(transportManager);
        fileSyncService = spy(new FileSyncService(engine));
    }

    @Test
    void getStagedResource_nullBatch_returnsNull() {
        assertNull(fileSyncService.getStagedResource(null));
    }

    private static byte[] emptyZipBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // no entries: a minimal valid empty zip archive
        }
        return baos.toByteArray();
    }

    private Node targetNode(String version) {
        Node node = new Node();
        node.setNodeId("node1");
        node.setSymmetricVersion(version);
        return node;
    }

    @Test
    void sendFilesForPull_noBatchesAvailable_returnsEmptyResult() {
        Node targetNode = targetNode("3.18.0");
        doReturn(new ArrayList<OutgoingBatch>()).when(fileSyncService).getBatchesToProcess(targetNode);
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(new ProcessInfo(), targetNode, null, null, null);
        assertTrue(result.getBatches().isEmpty());
        assertFalse(result.isEnvelopeFormatUsed());
        assertNull(result.getResumeEtag());
    }

    @Test
    void sendFilesForPull_resumeDisabled_batchIdParamIsIgnored() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(false);
        Node targetNode = targetNode("3.18.0");
        doReturn(new ArrayList<OutgoingBatch>()).when(fileSyncService).getBatchesToProcess(targetNode);
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(new ProcessInfo(), targetNode, "42", null, null);
        assertTrue(result.getBatches().isEmpty());
        assertNull(result.getResumeEtag());
        verify(outgoingBatchService, never()).findOutgoingBatch(anyLong(), anyString());
    }

    @Test
    void sendFilesForPull_resumeRequestedButBatchNotFound_fallsBackToNormalPull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        Node targetNode = targetNode("3.18.0");
        when(outgoingBatchService.findOutgoingBatch(42L, "node1")).thenReturn(null);
        doReturn(new ArrayList<OutgoingBatch>()).when(fileSyncService).getBatchesToProcess(targetNode);
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(new ProcessInfo(), targetNode, "42", null, null);
        assertTrue(result.getBatches().isEmpty());
        assertNull(result.getResumeEtag());
    }

    @Test
    void sendFilesForPull_resumeWithMatchingEtagAndRange_servesPartialContentFromSkipOffset() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        Node targetNode = targetNode("3.18.0");
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(42);
        batch.setNodeId("node1");
        when(outgoingBatchService.findOutgoingBatch(42L, "node1")).thenReturn(batch);
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.getState()).thenReturn(State.DONE);
        when(stagedResource.isFileResource()).thenReturn(true);
        when(stagedResource.getSize()).thenReturn((long) content.length);
        when(stagedResource.getGenerationTime()).thenReturn(555L);
        when(stagedResource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        doReturn(stagedResource).when(fileSyncService).getStagedResource(batch);
        StagedResourceETag matchingEtag = new StagedResourceETag(555L, content.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        when(transport.openStream()).thenReturn(out);
        ProcessInfo processInfo = new ProcessInfo();
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(processInfo, targetNode, "42", matchingEtag.toJson(), "bytes=4-");
        fileSyncService.writeFilesForPull(new ProcessInfo(), result, transport);
        assertTrue(result.isPartialContent());
        assertEquals(4L, result.getSkipCount());
        assertEquals(content.length, result.getTotalSize());
        assertEquals(matchingEtag, result.getResumeEtag());
        assertEquals(1, processInfo.getTotalBatchCount());
        assertArrayEquals("456789".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    void sendFilesForPull_resumeWithStaleEtag_servesFullContentNotPartial() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        Node targetNode = targetNode("3.18.0");
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(42);
        batch.setNodeId("node1");
        when(outgoingBatchService.findOutgoingBatch(42L, "node1")).thenReturn(batch);
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.getState()).thenReturn(State.DONE);
        when(stagedResource.isFileResource()).thenReturn(true);
        when(stagedResource.getSize()).thenReturn((long) content.length);
        when(stagedResource.getGenerationTime()).thenReturn(555L);
        when(stagedResource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        doReturn(stagedResource).when(fileSyncService).getStagedResource(batch);
        StagedResourceETag staleEtag = new StagedResourceETag(999L, content.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        when(transport.openStream()).thenReturn(out);
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(new ProcessInfo(), targetNode, "42", staleEtag.toJson(), "bytes=4-");
        fileSyncService.writeFilesForPull(new ProcessInfo(), result, transport);
        assertFalse(result.isPartialContent());
        assertEquals(0L, result.getSkipCount());
        assertArrayEquals(content, out.toByteArray());
    }

    @Test
    void sendFilesForPull_targetNodeBelowVersionGate_fallsBackToSingleBatchLegacyFormat() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        when(parameterService.getLong(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC)).thenReturn(Long.MAX_VALUE);
        Node targetNode = targetNode("3.17.0");
        OutgoingBatch batch1 = new OutgoingBatch();
        batch1.setBatchId(1);
        batch1.setNodeId("node1");
        batch1.setChannelId(Constants.CHANNEL_FILESYNC);
        OutgoingBatch batch2 = new OutgoingBatch();
        batch2.setBatchId(2);
        batch2.setNodeId("node1");
        batch2.setChannelId(Constants.CHANNEL_FILESYNC);
        doReturn(Arrays.asList(batch1, batch2)).when(fileSyncService).getBatchesToProcess(targetNode);
        byte[] zip1 = "ZIP-ONE".getBytes(StandardCharsets.UTF_8);
        IStagedResource resource1 = mock(IStagedResource.class);
        when(resource1.getSize()).thenReturn((long) zip1.length);
        when(resource1.getGenerationTime()).thenReturn(100L);
        when(resource1.getInputStream()).thenReturn(new ByteArrayInputStream(zip1));
        Object[] pathComponents1 = fileSyncService.getStagingPathComponents(batch1);
        when(stagingManager.create(pathComponents1[0], pathComponents1[1], pathComponents1[2])).thenReturn(resource1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        when(transport.openStream()).thenReturn(out);
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(new ProcessInfo(), targetNode, null, null, null);
        fileSyncService.writeFilesForPull(new ProcessInfo(), result, transport);
        assertFalse(result.isEnvelopeFormatUsed());
        assertEquals(1, result.getBatches().size());
        assertArrayEquals(zip1, out.toByteArray());
    }

    @Test
    void sendFilesForPull_targetNodeAtVersionGate_bundlesMultipleBatchesWithEnvelope() throws IOException {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        when(parameterService.getLong(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC)).thenReturn(Long.MAX_VALUE);
        Node targetNode = targetNode("3.18.0");
        OutgoingBatch batch1 = new OutgoingBatch();
        batch1.setBatchId(1);
        batch1.setNodeId("node1");
        batch1.setChannelId(Constants.CHANNEL_FILESYNC);
        OutgoingBatch batch2 = new OutgoingBatch();
        batch2.setBatchId(2);
        batch2.setNodeId("node1");
        batch2.setChannelId(Constants.CHANNEL_FILESYNC);
        doReturn(Arrays.asList(batch1, batch2)).when(fileSyncService).getBatchesToProcess(targetNode);
        byte[] zip1 = "ZIP-ONE".getBytes(StandardCharsets.UTF_8);
        IStagedResource resource1 = mock(IStagedResource.class);
        when(resource1.getSize()).thenReturn((long) zip1.length);
        when(resource1.getGenerationTime()).thenReturn(100L);
        when(resource1.getInputStream()).thenReturn(new ByteArrayInputStream(zip1));
        Object[] pathComponents1 = fileSyncService.getStagingPathComponents(batch1);
        when(stagingManager.create(pathComponents1[0], pathComponents1[1], pathComponents1[2])).thenReturn(resource1);
        byte[] zip2 = "ZIP-TWO-LONGER".getBytes(StandardCharsets.UTF_8);
        IStagedResource resource2 = mock(IStagedResource.class);
        when(resource2.getSize()).thenReturn((long) zip2.length);
        when(resource2.getGenerationTime()).thenReturn(200L);
        when(resource2.getInputStream()).thenReturn(new ByteArrayInputStream(zip2));
        Object[] pathComponents2 = fileSyncService.getStagingPathComponents(batch2);
        when(stagingManager.create(pathComponents2[0], pathComponents2[1], pathComponents2[2])).thenReturn(resource2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOutgoingTransport transport = mock(IOutgoingTransport.class);
        when(transport.openStream()).thenReturn(out);
        FileSyncPullResult result = fileSyncService.prepareFilesForPull(new ProcessInfo(), targetNode, null, null, null);
        fileSyncService.writeFilesForPull(new ProcessInfo(), result, transport);
        assertTrue(result.isEnvelopeFormatUsed());
        assertEquals(2, result.getBatches().size());
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        FileSyncBatchEnvelope header1 = FileSyncBatchEnvelope.readHeader(in);
        assertEquals(1L, header1.getBatchId());
        assertEquals(zip1.length, header1.getLength());
        assertArrayEquals(zip1, in.readNBytes(zip1.length));
        FileSyncBatchEnvelope header2 = FileSyncBatchEnvelope.readHeader(in);
        assertEquals(2L, header2.getBatchId());
        assertArrayEquals(zip2, in.readNBytes(zip2.length));
        assertNull(FileSyncBatchEnvelope.readHeader(in));
    }

    @Test
    void processEnvelopedZip_multipleBatches_stagesUnzipsAndClearsEachIndependently() throws IOException {
        String sourceNodeId = "remoteNode";
        byte[] zip1 = emptyZipBytes();
        byte[] zip2 = emptyZipBytes();
        StagedResourceETag etag1 = new StagedResourceETag(1L, zip1.length);
        StagedResourceETag etag2 = new StagedResourceETag(2L, zip2.length);
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        FileSyncBatchEnvelope.writeHeader(envelope, 1L, zip1.length, etag1);
        envelope.write(zip1);
        FileSyncBatchEnvelope.writeHeader(envelope, 2L, zip2.length, etag2);
        envelope.write(zip2);
        IStagedResource localResource1 = mock(IStagedResource.class);
        ByteArrayOutputStream captured1 = new ByteArrayOutputStream();
        when(localResource1.getOutputStream()).thenReturn(captured1);
        when(localResource1.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(captured1.toByteArray()));
        when(stagingManager.create(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, "1_filesync")).thenReturn(localResource1);
        IStagedResource localResource2 = mock(IStagedResource.class);
        ByteArrayOutputStream captured2 = new ByteArrayOutputStream();
        when(localResource2.getOutputStream()).thenReturn(captured2);
        when(localResource2.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(captured2.toByteArray()));
        when(stagingManager.create(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, "2_filesync")).thenReturn(localResource2);
        List<IncomingBatch> result = fileSyncService.processEnvelopedZip(new ByteArrayInputStream(envelope.toByteArray()),
                sourceNodeId, new ProcessInfo());
        assertTrue(result.isEmpty());
        verify(localResource1).setState(State.DONE);
        verify(localResource1).delete();
        verify(localResource2).setState(State.DONE);
        verify(localResource2).delete();
        verify(resumeCache).remove(sourceNodeId, 1L);
        verify(resumeCache).remove(sourceNodeId, 2L);
    }

    @Test
    void processEnvelopedZip_bodyReadFailureWithResumeEnabled_registersResumeCacheEntryAndKeepsPartial() throws IOException {
        String sourceNodeId = "remoteNode";
        byte[] zip1 = emptyZipBytes();
        StagedResourceETag etag1 = new StagedResourceETag(1L, zip1.length);
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        FileSyncBatchEnvelope.writeHeader(envelope, 1L, zip1.length, etag1);
        envelope.write(zip1);
        IStagedResource localResource1 = mock(IStagedResource.class);
        OutputStream throwingOut = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("simulated write failure");
            }
        };
        when(localResource1.getOutputStream()).thenReturn(throwingOut);
        when(localResource1.getSize()).thenReturn(0L);
        when(stagingManager.create(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, "1_filesync")).thenReturn(localResource1);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        InputStream envelopeStream = new ByteArrayInputStream(envelope.toByteArray());
        assertThrows(IOException.class, () -> fileSyncService.processEnvelopedZip(envelopeStream, sourceNodeId, new ProcessInfo()));
        verify(localResource1, never()).delete();
        verify(localResource1).close();
        ArgumentCaptor<ResumeCacheEntry> captor = ArgumentCaptor.forClass(ResumeCacheEntry.class);
        verify(resumeCache).put(anyString(), anyLong(), captor.capture());
        ResumeCacheEntry captured = captor.getValue();
        assertEquals(sourceNodeId, captured.getNodeId());
        assertEquals(1L, captured.getBatchId());
        assertEquals(etag1, captured.getEtag());
    }

    @Test
    void processEnvelopedZip_bodyReadFailureWithResumeDisabled_deletesPartialInstead() throws IOException {
        String sourceNodeId = "remoteNode";
        byte[] zip1 = emptyZipBytes();
        StagedResourceETag etag1 = new StagedResourceETag(1L, zip1.length);
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        FileSyncBatchEnvelope.writeHeader(envelope, 1L, zip1.length, etag1);
        envelope.write(zip1);
        IStagedResource localResource1 = mock(IStagedResource.class);
        OutputStream throwingOut = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("simulated write failure");
            }
        };
        when(localResource1.getOutputStream()).thenReturn(throwingOut);
        when(stagingManager.create(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, "1_filesync")).thenReturn(localResource1);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(false);
        InputStream envelopeStream = new ByteArrayInputStream(envelope.toByteArray());
        assertThrows(IOException.class, () -> fileSyncService.processEnvelopedZip(envelopeStream, sourceNodeId, new ProcessInfo()));
        verify(localResource1).delete();
        verify(resumeCache, never()).put(anyString(), anyLong(), any());
    }

    @Test
    void resumePartialBatch_localResourceInCreateState_appendsUnzipsAndClearsResumeCache() throws IOException {
        String sourceNodeId = "remoteNode";
        long batchId = 7L;
        StagedResourceETag etag = new StagedResourceETag(111L, 999L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId(sourceNodeId)
                .batchId(batchId)
                .etag(etag)
                .receivedCount(3L)
                .cachedAtTime(1000L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        byte[] fullZip = emptyZipBytes();
        int splitAt = fullZip.length / 2;
        byte[] existingBytes = Arrays.copyOfRange(fullZip, 0, splitAt);
        byte[] continuationBytes = Arrays.copyOfRange(fullZip, splitAt, fullZip.length);
        IStagedResource localResource = mock(IStagedResource.class);
        when(localResource.getState()).thenReturn(State.CREATE);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        captured.write(existingBytes);
        when(localResource.getOutputStream(true)).thenReturn(captured);
        when(localResource.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(captured.toByteArray()));
        when(stagingManager.find(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, batchId + "_filesync")).thenReturn(localResource);
        List<IncomingBatch> result = fileSyncService.resumePartialBatch(new ByteArrayInputStream(continuationBytes), sourceNodeId,
                new ProcessInfo(), pendingResume);
        assertTrue(result.isEmpty());
        assertArrayEquals(fullZip, captured.toByteArray());
        verify(localResource).setState(State.DONE);
        verify(localResource).delete();
        verify(resumeCache).remove(sourceNodeId, batchId);
    }

    @Test
    void resumePartialBatch_localResourceMissing_returnsEmptyListAndClearsResumeCache() throws IOException {
        String sourceNodeId = "remoteNode";
        long batchId = 7L;
        StagedResourceETag etag = new StagedResourceETag(111L, 999L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId(sourceNodeId)
                .batchId(batchId)
                .etag(etag)
                .receivedCount(3L)
                .cachedAtTime(1000L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        when(stagingManager.find(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, batchId + "_filesync")).thenReturn(null);
        List<IncomingBatch> result = fileSyncService.resumePartialBatch(new ByteArrayInputStream(new byte[0]), sourceNodeId,
                new ProcessInfo(), pendingResume);
        assertTrue(result.isEmpty());
        verify(resumeCache).remove(sourceNodeId, batchId);
    }

    @Test
    void resumePartialBatch_localResourceAlreadyFinalized_returnsEmptyListAndClearsResumeCache() throws IOException {
        String sourceNodeId = "remoteNode";
        long batchId = 7L;
        StagedResourceETag etag = new StagedResourceETag(111L, 999L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId(sourceNodeId)
                .batchId(batchId)
                .etag(etag)
                .receivedCount(3L)
                .cachedAtTime(1000L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        IStagedResource localResource = mock(IStagedResource.class);
        when(localResource.getState()).thenReturn(State.DONE);
        when(stagingManager.find(Constants.STAGING_CATEGORY_INCOMING, sourceNodeId, batchId + "_filesync")).thenReturn(localResource);
        List<IncomingBatch> result = fileSyncService.resumePartialBatch(new ByteArrayInputStream(new byte[0]), sourceNodeId,
                new ProcessInfo(), pendingResume);
        assertTrue(result.isEmpty());
        verify(resumeCache).remove(sourceNodeId, batchId);
    }

    @Test
    void pullFilesFromNode_noPendingFileSyncResume_pullsNormallyWithNullBatchId() throws IOException {
        String nodeId = "node1";
        NodeCommunication nodeCommunication = mock(NodeCommunication.class);
        Node remoteNode = new Node();
        remoteNode.setNodeId(nodeId);
        when(nodeCommunication.getNodeId()).thenReturn(nodeId);
        when(nodeCommunication.getNode()).thenReturn(remoteNode);
        RemoteNodeStatus status = new RemoteNodeStatus(nodeId, Constants.CHANNEL_FILESYNC, new HashMap<>());
        Node identity = new Node();
        identity.setNodeId("localNode");
        NodeSecurity security = new NodeSecurity();
        when(resumeCache.getPendingFileSyncEntryForNode(nodeId)).thenReturn(null);
        when(engine.getStatisticManager().newProcessInfo(any())).thenReturn(new ProcessInfo());
        IIncomingTransport transport = mock(IIncomingTransport.class);
        when(transport.getHeaders()).thenReturn(new HashMap<>());
        when(transport.openStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        ITransportManager transportManager = engine.getTransportManager();
        when(transportManager.getFilePullTransport(any(), any(), any(), any(), any(), any())).thenReturn(transport);
        doReturn(new ArrayList<IncomingBatch>()).when(fileSyncService).processZip(any(), any(), any());
        fileSyncService.pullFilesFromNode(nodeCommunication, status, identity, security);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transportManager).getFilePullTransport(eq(remoteNode), eq(identity), any(), propsCaptor.capture(), any(), isNull());
        assertTrue(propsCaptor.getValue().isEmpty());
        verify(resumeCache, never()).getPendingForNode(anyString(), anyString());
        verify(resumeCache, never()).remove(anyString(), anyLong());
    }

    @Test
    void pullFilesFromNode_pendingFileSyncResumeFromDifferentQueue_stillHonoredAndRequestsResumeBatchId() throws IOException {
        String nodeId = "node1";
        NodeCommunication nodeCommunication = mock(NodeCommunication.class);
        Node remoteNode = new Node();
        remoteNode.setNodeId(nodeId);
        when(nodeCommunication.getNodeId()).thenReturn(nodeId);
        when(nodeCommunication.getNode()).thenReturn(remoteNode);
        RemoteNodeStatus status = new RemoteNodeStatus(nodeId, Constants.CHANNEL_FILESYNC, new HashMap<>());
        Node identity = new Node();
        identity.setNodeId("localNode");
        NodeSecurity security = new NodeSecurity();
        StagedResourceETag etag = new StagedResourceETag(111L, 500L);
        ResumeCacheEntry fileSyncResume = ResumeCacheEntry.builder()
                .nodeId(nodeId)
                .batchId(12L)
                .etag(etag)
                .receivedCount(200L)
                .cachedAtTime(123L)
                .queue(Constants.QUEUE_RELOAD)
                .fileSync(true)
                .build();
        // Registered under a different queue than this attempt's own status.getQueue() - still honored, since file
        // sync batch selection is not partitioned by queue the way table-sync's is.
        when(resumeCache.getPendingFileSyncEntryForNode(nodeId)).thenReturn(fileSyncResume);
        when(engine.getStatisticManager().newProcessInfo(any())).thenReturn(new ProcessInfo());
        IIncomingTransport transport = mock(IIncomingTransport.class);
        when(transport.getHeaders()).thenReturn(new HashMap<>());
        when(transport.openStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        ITransportManager transportManager = engine.getTransportManager();
        when(transportManager.getFilePullTransport(any(), any(), any(), any(), any(), any())).thenReturn(transport);
        doReturn(new ArrayList<IncomingBatch>()).when(fileSyncService).processZip(any(), any(), any());
        fileSyncService.pullFilesFromNode(nodeCommunication, status, identity, security);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transportManager).getFilePullTransport(eq(remoteNode), eq(identity), any(), propsCaptor.capture(), any(), eq(12L));
        assertEquals(etag.toJson(), propsCaptor.getValue().get(WebConstants.HEADER_IF_ETAG));
        assertEquals("bytes=200-", propsCaptor.getValue().get(WebConstants.HEADER_RANGE));
    }

    @Test
    void sendFiles_failureDuringSendPhase_incrementsDataSentErrorsOnly() throws Exception {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        FileSyncService service = spy(buildFileSyncService(statisticManager, outgoingBatchService));
        OutgoingBatch batch = new OutgoingBatch("target1", "testchannel", Status.NE);
        batch.setBatchId(1);
        List<OutgoingBatch> batchesToProcess = new ArrayList<OutgoingBatch>();
        batchesToProcess.add(batch);
        doReturn(batchesToProcess).when(service).getBatchesToProcess(any(Node.class));
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.exists()).thenReturn(true);
        when(stagedResource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        doReturn(stagedResource).when(service).getStagedResource(any(OutgoingBatch.class));
        OutputStream failingOutputStream = mock(OutputStream.class);
        doThrow(new IOException("simulated network failure")).when(failingOutputStream).flush();
        IOutgoingTransport outgoingTransport = mock(IOutgoingTransport.class);
        when(outgoingTransport.openStream()).thenReturn(failingOutputStream);
        Node targetNode = new Node();
        targetNode.setNodeId("target1");
        ProcessInfo processInfo = new ProcessInfo(new ProcessInfoKey("source1", "target1", null));
        try {
            service.sendFiles(processInfo, targetNode, outgoingTransport);
        } catch (RuntimeException expected) {
            // sendFiles rethrows after recording statistics
        }
        verify(statisticManager).incrementDataSentErrors("testchannel", 1);
        verify(statisticManager, never()).incrementDataExtractedErrors(any(), anyLong());
    }

    @Test
    void sendFiles_failureDuringExtractPhase_incrementsDataExtractedErrorsOnly() {
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        FileSyncService service = spy(buildFileSyncService(statisticManager, outgoingBatchService));
        OutgoingBatch batch = new OutgoingBatch("target1", "testchannel", Status.NE);
        batch.setBatchId(1);
        List<OutgoingBatch> batchesToProcess = new ArrayList<OutgoingBatch>();
        batchesToProcess.add(batch);
        doReturn(batchesToProcess).when(service).getBatchesToProcess(any(Node.class));
        doThrow(new RuntimeException("simulated extract failure")).when(service).getStagedResource(any(OutgoingBatch.class));
        Node targetNode = new Node();
        targetNode.setNodeId("target1");
        ProcessInfo processInfo = new ProcessInfo(new ProcessInfoKey("source1", "target1", null));
        IOutgoingTransport outgoingTransport = mock(IOutgoingTransport.class);
        try {
            service.sendFiles(processInfo, targetNode, outgoingTransport);
        } catch (RuntimeException expected) {
            // sendFiles rethrows after recording statistics
        }
        verify(statisticManager).incrementDataExtractedErrors("testchannel", 1);
        verify(statisticManager, never()).incrementDataSentErrors(any(), anyLong());
    }

    private FileSyncService buildFileSyncService(IStatisticManager statisticManager, IOutgoingBatchService outgoingBatchService) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        ICacheManager cacheManager = mock(ICacheManager.class);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        return new FileSyncService(engine);
    }
}
