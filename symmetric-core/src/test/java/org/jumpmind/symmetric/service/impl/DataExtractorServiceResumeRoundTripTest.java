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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.SimpleStagingDataWriter;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.io.stage.StagingManager;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.http.IHttpResumeCache;
import org.jumpmind.symmetric.transport.http.ResumeCacheEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of the table-sync resume path: a real {@link DataExtractorService} extracting from a real staged outgoing batch, and a real
 * {@link SimpleStagingDataWriter} staging it on the client side. Unlike the granular, per-side tests in {@code DataExtractorServiceTest} and
 * {@code SimpleStagingDataWriterTest} (each of which mocks the other side), this exercises both real objects against each other so a coordinate mismatch
 * between them can't hide behind a mock.
 * <p>
 * The client's own detection of an interruption and computation of {@code receivedCount} is already covered directly by {@code SimpleStagingDataWriterTest}'s
 * {@code registerForResume}/{@code stagedCharCount} tests. This test instead starts from the partial state such an interruption would leave behind (a real,
 * partially-written {@code State.CREATE} staged resource, using the same character-counting rule production uses) and exercises the real resume round trip from
 * there: the real server-side suppression of the stats/ETag preamble on a resumed extraction, and the real client-side {@code beginResumedBatch()}
 * append-and-finalize path, asserting the reassembled staged file is identical to an uninterrupted transfer.
 */
class DataExtractorServiceResumeRoundTripTest {
    private static final String MULTI_BYTE_VALUE = "héllo wörld 日本語";
    @TempDir
    File serverStagingDir;
    @TempDir
    File referenceClientStagingDir;
    @TempDir
    File resumeClientStagingDir;

    @Test
    void interruptedResumedTransfer_reassemblesByteIdenticalToUninterruptedTransfer() throws IOException {
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        StagingManager serverStagingManager = new StagingManager(serverStagingDir.getAbsolutePath(), false);
        DataExtractorService dataExtractorService = newServerDataExtractorService(outgoingBatchService, serverStagingManager);
        ProcessInfo serverProcessInfo = new ProcessInfo(new ProcessInfoKey("node1", "me", ProcessType.PULL_HANDLER_EXTRACT));
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(500);
        batch.setNodeId("node1");
        batch.setChannelId("channel1");
        String clientStagedPrefix = "nodeid,node1\nbinary,NONE\nchannel,channel1\nbatch,500\n"
                + "table,mytable\nkeys,id\ncolumns,id,name\n"
                + "insert,1,\"" + MULTI_BYTE_VALUE + "\"\n";
        String stagedContent = clientStagedPrefix + "commit,500\n";
        IStagedResource outResource1 = createOutgoingResource(serverStagingManager, batch, 500L, stagedContent);
        IStagedResource outResource2 = createOutgoingResource(serverStagingManager, batch, 600L, stagedContent);
        StringWriter fullOut = new StringWriter();
        dataExtractorService.extractSingleBatchForResume(batch, outResource1, fullOut, 0L, serverProcessInfo);
        String fullWireContent = fullOut.toString();
        assertEquals(1, batch.getSentCount());
        assertTrue(fullWireContent.contains(CsvConstants.STATS_COLUMNS), "a full (non-resumed) extraction must still include the stats preamble");
        StagingManager referenceClientStagingManager = new StagingManager(referenceClientStagingDir.getAbsolutePath(), false);
        SimpleStagingDataWriter referenceWriter = newClientWriter(referenceClientStagingManager, mock(IHttpResumeCache.class),
                new BufferedReader(new StringReader(fullWireContent)), null);
        referenceWriter.process();
        assertNull(referenceWriter.getException());
        String referenceStaged = readStaged(referenceClientStagingManager, 500L);
        assertEquals(stagedContent, referenceStaged);
        StagingManager resumeClientStagingManager = new StagingManager(resumeClientStagingDir.getAbsolutePath(), false);
        IStagedResource partialResource = resumeClientStagingManager.create(Constants.STAGING_CATEGORY_INCOMING, "node1", 500L);
        partialResource.getWriter(0L).write(clientStagedPrefix);
        partialResource.close();
        long skipCount = clientStagedPrefix.length();
        StagedResourceETag etag = new StagedResourceETag(outResource1.getGenerationTime(), outResource1.getSize());
        ResumeCacheEntry pendingEntry = ResumeCacheEntry.builder()
                .nodeId("node1")
                .batchId(500L)
                .etag(etag)
                .receivedCount(skipCount)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(System.currentTimeMillis())
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        StringWriter resumedOut = new StringWriter();
        dataExtractorService.extractSingleBatchForResume(batch, outResource2, resumedOut, skipCount, serverProcessInfo);
        String resumedWireContent = resumedOut.toString();
        assertEquals(2, batch.getSentCount());
        verify(outgoingBatchService, times(2)).updateOutgoingBatch(batch);
        assertEquals("commit,500\n", resumedWireContent, "resumed response must contain exactly the unsent remainder");
        SimpleStagingDataWriter resumedWriter = newClientWriter(resumeClientStagingManager, mock(IHttpResumeCache.class),
                new BufferedReader(new StringReader(resumedWireContent)), pendingEntry);
        resumedWriter.process();
        assertNull(resumedWriter.getException());
        String resumedStaged = readStaged(resumeClientStagingManager, 500L);
        assertEquals(stagedContent, resumedStaged);
        assertEquals(referenceStaged, resumedStaged);
    }

    private DataExtractorService newServerDataExtractorService(IOutgoingBatchService outgoingBatchService, StagingManager serverStagingManager) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getTablePrefix()).thenReturn("sym");
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        when(parameterService.getLong(ParameterConstants.OUTGOING_BATCH_UPDATE_STATUS_MILLIS)).thenReturn(Long.MAX_VALUE);
        when(engine.getParameterService()).thenReturn(parameterService);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getName()).thenReturn("H2");
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ISqlTemplate sqlTemplateDirty = mock(ISqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        TriggerRouterService triggerRouterService = mock(TriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        INodeService nodeService = mock(INodeService.class);
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        targetNode.setSymmetricVersion("3.18.0");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        when(engine.getNodeService()).thenReturn(nodeService);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        Channel channel = new Channel();
        channel.setChannelId("channel1");
        when(configurationService.getChannel("channel1")).thenReturn(channel);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getStagingManager()).thenReturn(serverStagingManager);
        return new DataExtractorService(engine);
    }

    private SimpleStagingDataWriter newClientWriter(StagingManager stagingManager, IHttpResumeCache resumeCache, BufferedReader reader,
            ResumeCacheEntry resumeEntry) {
        ISymmetricEngine clientEngine = mock(ISymmetricEngine.class);
        when(clientEngine.getStagingManager()).thenReturn(stagingManager);
        ITransportManager transportManager = mock(ITransportManager.class);
        when(transportManager.getResumeCache()).thenReturn(resumeCache);
        when(clientEngine.getTransportManager()).thenReturn(transportManager);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        when(clientEngine.getParameterService()).thenReturn(parameterService);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(clientEngine.getConfigurationService()).thenReturn(configurationService);
        DataContext context = new DataContext();
        context.getContext().put(Constants.DATA_CONTEXT_SOURCE_NODE, "node1");
        return SimpleStagingDataWriter.builder()
                .processInfo(new ProcessInfo(new ProcessInfoKey("node1", "me", ProcessType.PULL_HANDLER_EXTRACT)))
                .reader(reader)
                .engine(clientEngine)
                .category(Constants.STAGING_CATEGORY_INCOMING)
                .memoryThresholdInBytes(0L)
                .batchType(BatchType.LOAD)
                .sourceNodeId("node1")
                .targetNodeId("me")
                .context(context)
                .resumeEntry(resumeEntry)
                .build();
    }

    private IStagedResource createOutgoingResource(StagingManager stagingManager, OutgoingBatch batch, long stagingBatchId, String content)
            throws IOException {
        Batch outgoingBatchDescriptor = new Batch(BatchType.EXTRACT, stagingBatchId, batch.getChannelId(), null, batch.getNodeId(), "me", false);
        IStagedResource resource = stagingManager.create(Constants.STAGING_CATEGORY_OUTGOING, outgoingBatchDescriptor.getStagedLocation(), stagingBatchId);
        resource.getWriter(0L).write(content);
        resource.close();
        resource.setState(State.DONE);
        return resource;
    }

    private String readStaged(StagingManager stagingManager, long batchId) throws IOException {
        IStagedResource resource = stagingManager.find(Constants.STAGING_CATEGORY_INCOMING, "node1", batchId);
        assertNotNull(resource);
        String content = IOUtils.toString(resource.getReader());
        resource.closeReaders();
        return content;
    }
}
