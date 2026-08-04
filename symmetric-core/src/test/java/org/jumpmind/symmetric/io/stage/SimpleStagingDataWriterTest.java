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
package org.jumpmind.symmetric.io.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.commons.io.IOUtils;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.http.IHttpResumeCache;
import org.jumpmind.symmetric.transport.http.ResumeCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SimpleStagingDataWriterTest {
    @TempDir
    File tempDir;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ITransportManager transportManager;
    private IHttpResumeCache resumeCache;
    private StagingManager realStagingManager;
    private ProcessInfo processInfo;
    private DataContext context;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        transportManager = mock(ITransportManager.class);
        when(engine.getTransportManager()).thenReturn(transportManager);
        resumeCache = mock(IHttpResumeCache.class);
        when(transportManager.getResumeCache()).thenReturn(resumeCache);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        realStagingManager = new StagingManager(tempDir.getAbsolutePath(), false);
        when(engine.getStagingManager()).thenReturn(realStagingManager);
        processInfo = new ProcessInfo(new ProcessInfoKey("node1", "me", ProcessType.PULL_HANDLER_EXTRACT));
        context = new DataContext();
        context.getContext().put(Constants.DATA_CONTEXT_SOURCE_NODE, "node1");
    }

    private SimpleStagingDataWriter newWriter(String content, ResumeCacheEntry resumeEntry) {
        BufferedReader reader = new BufferedReader(new StringReader(content));
        return SimpleStagingDataWriter.builder()
                .processInfo(processInfo)
                .reader(reader)
                .engine(engine)
                .category(Constants.STAGING_CATEGORY_INCOMING)
                .memoryThresholdInBytes(0L)
                .batchType(BatchType.LOAD)
                .sourceNodeId("node1")
                .targetNodeId("me")
                .context(context)
                .resumeEntry(resumeEntry)
                .build();
    }

    private String readContent(IStagedResource resource) throws IOException {
        String content = IOUtils.toString(resource.getReader());
        resource.closeReaders();
        return content;
    }

    @Test
    void process_happyPath_unaffectedByResumeChanges() throws IOException {
        String content = "nodeid,node1\nbinary,NONE\nchannel,channel1\nbatch,100\ninsert,1,foo\ncommit,100\n";
        SimpleStagingDataWriter writer = newWriter(content, null);
        writer.process();
        assertNull(writer.getException());
        IStagedResource resource = realStagingManager.find(Constants.STAGING_CATEGORY_INCOMING, "node1", 100L);
        assertNotNull(resource);
        assertEquals(State.DONE, resource.getState());
        String staged = readContent(resource);
        assertTrue(staged.contains("insert,1,foo"));
        assertTrue(staged.contains("commit,100"));
        verify(resumeCache, never()).put(any(), anyLong(), any());
        verify(resumeCache).remove("node1", 100L);
    }

    @Test
    void process_etagLineCaptured_butNotPersistedToStagedFile() throws IOException {
        String etagJson = new StagedResourceETag(123L, 456L).toJson();
        String content = "nodeid,node1\nbinary,NONE\nchannel,channel1\nbatch,101\netag," + etagJson + "\ninsert,1,foo\ncommit,101\n";
        SimpleStagingDataWriter writer = newWriter(content, null);
        writer.process();
        assertNull(writer.getException());
        IStagedResource resource = realStagingManager.find(Constants.STAGING_CATEGORY_INCOMING, "node1", 101L);
        String staged = readContent(resource);
        assertFalse(staged.contains("etag,"));
        assertTrue(staged.contains("insert,1,foo"));
        assertTrue(staged.contains("commit,101"));
    }

    @Test
    void beginResumedBatch_existingCreateStateResource_reopensInAppendModeAndReturnsResource() throws IOException {
        Batch preStageBatch = new Batch(BatchType.LOAD, 200L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        IStagedResource existing = realStagingManager.create(Constants.STAGING_CATEGORY_INCOMING, preStageBatch.getStagedLocation(), 200L);
        BufferedWriter preWriter = existing.getWriter(0L);
        preWriter.write("previously,received\n");
        existing.close();
        StagedResourceETag etag = new StagedResourceETag(111L, 222L);
        ResumeCacheEntry resumeEntry = ResumeCacheEntry.builder()
                .nodeId("node1")
                .batchId(200L)
                .etag(etag)
                .receivedCount(20L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(999L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        SimpleStagingDataWriter writer = newWriter("insert,2,bar\ncommit,200\n", resumeEntry);
        IStagedResource result = writer.beginResumedBatch();
        assertNotNull(result);
        assertEquals(200L, writer.batch.getBatchId());
        assertEquals(etag, writer.currentBatchEtag);
        assertNotNull(writer.writer);
        writer.writer.write("appended,content\n");
        writer.writer.close();
        String staged = readContent(existing);
        assertEquals("previously,received\nappended,content\n", staged);
    }

    @Test
    void beginResumedBatch_missingResource_returnsNullAndLeavesBatchUnset() {
        ResumeCacheEntry resumeEntry = ResumeCacheEntry.builder()
                .nodeId("node1")
                .batchId(201L)
                .etag(new StagedResourceETag(1L, 2L))
                .receivedCount(0L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(999L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        SimpleStagingDataWriter writer = newWriter("commit,201\n", resumeEntry);
        IStagedResource result = writer.beginResumedBatch();
        assertNull(result);
        assertNull(writer.batch);
        assertNull(writer.writer);
    }

    @Test
    void beginResumedBatch_resourceAlreadyFinalized_returnsNull() throws IOException {
        Batch preStageBatch = new Batch(BatchType.LOAD, 202L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        IStagedResource existing = realStagingManager.create(Constants.STAGING_CATEGORY_INCOMING, preStageBatch.getStagedLocation(), 202L);
        BufferedWriter preWriter = existing.getWriter(0L);
        preWriter.write("complete\n");
        existing.close();
        existing.setState(State.DONE);
        ResumeCacheEntry resumeEntry = ResumeCacheEntry.builder()
                .nodeId("node1")
                .batchId(202L)
                .etag(new StagedResourceETag(1L, 2L))
                .receivedCount(0L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(999L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        SimpleStagingDataWriter writer = newWriter("commit,202\n", resumeEntry);
        IStagedResource result = writer.beginResumedBatch();
        assertNull(result);
        assertNull(writer.batch);
    }

    @Test
    void process_resumeEntrySupplied_appendsAndCommitsSuccessfully() throws IOException {
        Batch preStageBatch = new Batch(BatchType.LOAD, 300L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        IStagedResource existing = realStagingManager.create(Constants.STAGING_CATEGORY_INCOMING, preStageBatch.getStagedLocation(), 300L);
        BufferedWriter preWriter = existing.getWriter(0L);
        preWriter.write("previously,received\n");
        existing.close();
        ResumeCacheEntry resumeEntry = ResumeCacheEntry.builder()
                .nodeId("node1")
                .batchId(300L)
                .etag(new StagedResourceETag(1L, 2L))
                .receivedCount(20L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(999L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        SimpleStagingDataWriter writer = newWriter("insert,2,bar\ncommit,300\n", resumeEntry);
        writer.process();
        assertNull(writer.getException());
        IStagedResource resource = realStagingManager.find(Constants.STAGING_CATEGORY_INCOMING, "node1", 300L);
        assertEquals(State.DONE, resource.getState());
        String staged = readContent(resource);
        assertEquals("previously,received\ninsert,2,bar\ncommit,300\n", staged);
        verify(resumeCache).remove("node1", 300L);
    }

    @Test
    void isResumableInterruption_allConditionsMet_returnsTrue() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 1L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        writer.currentBatchEtag = new StagedResourceETag(1L, 2L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        assertTrue(writer.isResumableInterruption(new IOException("dropped"), resource));
    }

    @Test
    void isResumableInterruption_nonIOException_returnsFalse() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 1L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        writer.currentBatchEtag = new StagedResourceETag(1L, 2L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        assertFalse(writer.isResumableInterruption(new RuntimeException("data error"), resource));
    }

    @Test
    void isResumableInterruption_batchNull_returnsFalse() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.currentBatchEtag = new StagedResourceETag(1L, 2L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        assertFalse(writer.isResumableInterruption(new IOException("dropped"), resource));
    }

    @Test
    void isResumableInterruption_noCurrentEtag_returnsFalse() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 1L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        assertFalse(writer.isResumableInterruption(new IOException("dropped"), resource));
    }

    @Test
    void isResumableInterruption_resourceNotFileBacked_returnsFalse() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 1L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        writer.currentBatchEtag = new StagedResourceETag(1L, 2L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(false);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        assertFalse(writer.isResumableInterruption(new IOException("dropped"), resource));
    }

    @Test
    void isResumableInterruption_resumeDisabled_returnsFalse() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 1L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        writer.currentBatchEtag = new StagedResourceETag(1L, 2L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(false);
        assertFalse(writer.isResumableInterruption(new IOException("dropped"), resource));
    }

    @Test
    void isResumableInterruption_noResumeCacheAvailable_returnsFalse() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 1L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        writer.currentBatchEtag = new StagedResourceETag(1L, 2L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        when(transportManager.getResumeCache()).thenReturn(null);
        assertFalse(writer.isResumableInterruption(new IOException("dropped"), resource));
    }

    @Test
    void registerForResume_closesResourceAndPutsEntryInCache() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.batch = new Batch(BatchType.LOAD, 55L, "channel1", BinaryEncoding.NONE, "node1", "me", false);
        writer.currentBatchEtag = new StagedResourceETag(111L, 222L);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getSize()).thenReturn(999L);
        writer.registerForResume(resource);
        verify(resource).close();
        ArgumentCaptor<ResumeCacheEntry> captor = ArgumentCaptor.forClass(ResumeCacheEntry.class);
        verify(resumeCache).put(eq("node1"), eq(55L), captor.capture());
        ResumeCacheEntry entry = captor.getValue();
        assertEquals("node1", entry.getNodeId());
        assertEquals(55L, entry.getBatchId());
        assertEquals(writer.currentBatchEtag, entry.getEtag());
        assertEquals(999L, entry.getReceivedCount());
        assertEquals("channel1", entry.getChannelId());
        assertEquals("NONE", entry.getBinaryEncoding());
    }

    @Test
    void clearResumeCacheEntry_delegatesToResumeCacheRemove() {
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.clearResumeCacheEntry(42L);
        verify(resumeCache).remove("node1", 42L);
    }

    @Test
    void clearResumeCacheEntry_noTransportManager_doesNotThrow() {
        when(engine.getTransportManager()).thenReturn(null);
        SimpleStagingDataWriter writer = newWriter("", null);
        writer.clearResumeCacheEntry(42L);
        verify(resumeCache, never()).remove(anyString(), anyLong());
    }

    /**
     * A {@link BufferedWriter} that throws {@link IOException} on a specific, counted {@code write(String)} call, simulating a connection drop partway through
     * writing a batch to staging.
     */
    private static class DropAfterNWritesWriter extends BufferedWriter {
        private int callCount = 0;
        private final int failOnCall;

        DropAfterNWritesWriter(Writer out, int failOnCall) {
            super(out);
            this.failOnCall = failOnCall;
        }

        @Override
        public void write(String str) throws IOException {
            callCount++;
            if (callCount == failOnCall) {
                throw new IOException("Simulated connection drop");
            }
            super.write(str);
        }
    }

    private SimpleStagingDataWriter newWriterWithPoisonedResource(IStagedResource stagedResource, int failOnWriteCall) {
        StagingManager mockStagingManager = mock(StagingManager.class);
        when(engine.getStagingManager()).thenReturn(mockStagingManager);
        when(mockStagingManager.create(Constants.STAGING_CATEGORY_INCOMING, "node1", 100L)).thenReturn(stagedResource);
        when(stagedResource.getWriter(0L)).thenReturn(new DropAfterNWritesWriter(new StringWriter(), failOnWriteCall));
        String etagJson = new StagedResourceETag(123L, 456L).toJson();
        String content = "nodeid,node1\nbinary,NONE\nchannel,channel1\nbatch,100\netag," + etagJson + "\ninsert,1,foo\ncommit,100\n";
        return newWriter(content, null);
    }

    @Test
    void process_ioExceptionWithResumeEligible_preservesResourceAndRegistersForResume() throws IOException {
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        SimpleStagingDataWriter writer = newWriterWithPoisonedResource(stagedResource, 9);
        writer.process();
        assertNotNull(writer.getException());
        assertTrue(writer.getException() instanceof IOException);
        verify(stagedResource, never()).delete();
        verify(stagedResource).close();
        verify(resumeCache).put(eq("node1"), eq(100L), any(ResumeCacheEntry.class));
    }

    @Test
    void process_ioExceptionWithResumeDisabled_deletesResourceAsBefore() throws IOException {
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.isFileResource()).thenReturn(true);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(false);
        SimpleStagingDataWriter writer = newWriterWithPoisonedResource(stagedResource, 9);
        writer.process();
        assertNotNull(writer.getException());
        verify(stagedResource).delete();
        verify(resumeCache, never()).put(any(), anyLong(), any());
    }

    @Test
    void process_ioExceptionButResourceNotFileBacked_deletesResourceAsBefore() throws IOException {
        IStagedResource stagedResource = mock(IStagedResource.class);
        when(stagedResource.isFileResource()).thenReturn(false);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        SimpleStagingDataWriter writer = newWriterWithPoisonedResource(stagedResource, 9);
        writer.process();
        assertNotNull(writer.getException());
        verify(stagedResource).delete();
        verify(resumeCache, never()).put(any(), anyLong(), any());
    }
}
