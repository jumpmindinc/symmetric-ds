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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.Collections;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;

class PullUriHandlerTest {
    private IParameterService parameterService;
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private IDataExtractorService dataExtractorService;
    private IRegistrationService registrationService;
    private IStatisticManager statisticManager;
    private IOutgoingBatchService outgoingBatchService;
    private PullUriHandler handler;
    private HttpServletResponse res;
    private IOutgoingTransport outgoingTransport;
    private ProcessInfo processInfo;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        nodeService = mock(INodeService.class);
        configurationService = mock(IConfigurationService.class);
        dataExtractorService = mock(IDataExtractorService.class);
        registrationService = mock(IRegistrationService.class);
        statisticManager = mock(IStatisticManager.class);
        outgoingBatchService = mock(IOutgoingBatchService.class);
        handler = new PullUriHandler(parameterService, nodeService, configurationService, dataExtractorService,
                registrationService, statisticManager, outgoingBatchService);
        res = mock(HttpServletResponse.class);
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        outgoingTransport = mock(IOutgoingTransport.class);
        when(outgoingTransport.getWriter()).thenReturn(writer);
        processInfo = new ProcessInfo(new ProcessInfoKey("me", "node1", ProcessType.PULL_HANDLER_EXTRACT));
    }

    @Test
    void handleResume_blankBatchId_returnsFalseWithoutLookup() {
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "", null, null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
        verify(outgoingBatchService, never()).findOutgoingBatch(anyLong(), anyString());
    }

    @Test
    void handleResume_nonNumericBatchId_returnsFalse() {
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "not-a-number", null, null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
    }

    @Test
    void handleResume_missingBatch_returnsFalse() {
        when(outgoingBatchService.findOutgoingBatch(1L, "node1")).thenReturn(null);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", null, null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
    }

    @Test
    void handleResume_missingStagedResource_returnsFalse() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        when(outgoingBatchService.findOutgoingBatch(1L, "node1")).thenReturn(batch);
        when(dataExtractorService.getStagedResourceForResume(batch)).thenReturn(null);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", null, null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
    }

    @Test
    void handleResume_resourceNotDone_returnsFalse() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.CREATE);
        when(outgoingBatchService.findOutgoingBatch(1L, "node1")).thenReturn(batch);
        when(dataExtractorService.getStagedResourceForResume(batch)).thenReturn(resource);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", null, null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
    }

    @Test
    void handleResume_resourceNotFileBacked_returnsFalse() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.DONE);
        when(resource.isFileResource()).thenReturn(false);
        when(outgoingBatchService.findOutgoingBatch(1L, "node1")).thenReturn(batch);
        when(dataExtractorService.getStagedResourceForResume(batch)).thenReturn(resource);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", null, null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
    }

    @Test
    void handleResume_channelOnDifferentQueue_returnsFalse() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        Channel channel = new Channel();
        channel.setQueue(Constants.QUEUE_SYSTEM);
        when(configurationService.getChannel("channel1")).thenReturn(channel);
        StagedResourceETag etag = new StagedResourceETag(1000L, 500L);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", etag.toJson(), "bytes=200-", Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
        verify(dataExtractorService, never()).extractSingleBatchForResume(any(), any(), any(), anyLong(), any());
    }

    @Test
    void handleResume_channelNotFound_returnsFalse() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        when(configurationService.getChannel("channel1")).thenReturn(null);
        StagedResourceETag etag = new StagedResourceETag(1000L, 500L);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", etag.toJson(), "bytes=200-", Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertFalse(result);
    }

    private IStagedResource setUpEligibleResource(OutgoingBatch batch) {
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.DONE);
        when(resource.isFileResource()).thenReturn(true);
        when(resource.getGenerationTime()).thenReturn(1000L);
        when(resource.getSize()).thenReturn(500L);
        when(outgoingBatchService.findOutgoingBatch(1L, "node1")).thenReturn(batch);
        when(dataExtractorService.getStagedResourceForResume(batch)).thenReturn(resource);
        Channel channel = new Channel();
        channel.setQueue(Constants.QUEUE_DEFAULT);
        when(configurationService.getChannel(batch.getChannelId())).thenReturn(channel);
        return resource;
    }

    @Test
    void handleResume_matchingEtagAndValidRange_returnsPartialContent() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        StagedResourceETag etag = new StagedResourceETag(1000L, 500L);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", etag.toJson(), "bytes=200-", Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertTrue(result);
        verify(res).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(res).setHeader(WebConstants.HEADER_CONTENT_RANGE, "200-499/500");
        verify(res).setHeader(eq(WebConstants.HEADER_ETAG), anyString());
        verify(res).setHeader(WebConstants.HEADER_ACCEPT_RANGES, "bytes");
        verify(dataExtractorService).extractSingleBatchForResume(eq(batch), any(), any(), eq(200L), eq(processInfo));
    }

    @Test
    void handleResume_staleEtag_fullResendWithNoPartialStatus() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        StagedResourceETag staleEtag = new StagedResourceETag(999L, 500L);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", staleEtag.toJson(), "bytes=200-", Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertTrue(result);
        verify(res, never()).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(res, never()).setHeader(eq(WebConstants.HEADER_CONTENT_RANGE), anyString());
        verify(dataExtractorService).extractSingleBatchForResume(eq(batch), any(), any(), eq(0L), eq(processInfo));
    }

    @Test
    void handleResume_missingIfETagHeader_fullResend() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", null, "bytes=200-", Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertTrue(result);
        verify(res, never()).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(dataExtractorService).extractSingleBatchForResume(eq(batch), any(), any(), eq(0L), eq(processInfo));
    }

    @Test
    void handleResume_missingRangeHeader_fullResendEvenWithMatchingEtag() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        StagedResourceETag etag = new StagedResourceETag(1000L, 500L);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", etag.toJson(), null, Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertTrue(result);
        verify(res, never()).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(dataExtractorService).extractSingleBatchForResume(eq(batch), any(), any(), eq(0L), eq(processInfo));
    }

    @Test
    void handleResume_rangeStartAtOrPastTotalSize_fullResend() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(1);
        batch.setChannelId("channel1");
        setUpEligibleResource(batch);
        StagedResourceETag etag = new StagedResourceETag(1000L, 500L);
        boolean result = handler.handleResume(new PullUriHandler.ResumeRequest("node1", "1", etag.toJson(), "bytes=500-", Constants.QUEUE_DEFAULT),
                outgoingTransport, res, processInfo);
        assertTrue(result);
        verify(res, never()).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(dataExtractorService).extractSingleBatchForResume(eq(batch), any(), any(), eq(0L), eq(processInfo));
    }

    @Test
    void parseRangeSkipCount_blankHeader_returnsNull() {
        assertNull(handler.parseRangeSkipCount(null));
        assertNull(handler.parseRangeSkipCount(""));
        assertNull(handler.parseRangeSkipCount("   "));
    }

    @Test
    void parseRangeSkipCount_malformedHeader_returnsNull() {
        assertNull(handler.parseRangeSkipCount("bytes=abc-"));
        assertNull(handler.parseRangeSkipCount("not-a-range-header"));
        assertNull(handler.parseRangeSkipCount("bytes=100-200"));
    }

    @Test
    void parseRangeSkipCount_validHeader_returnsSkipCount() {
        assertEquals(1234L, handler.parseRangeSkipCount("bytes=1234-"));
    }

    @Test
    void parseRangeSkipCount_headerWithWhitespace_isTrimmed() {
        assertEquals(42L, handler.parseRangeSkipCount("  bytes=42-  "));
    }

    @Test
    void handlePull_resumeEligibleAndEnabled_skipsNormalExtract() throws Exception {
        PullUriHandler spyHandler = spy(handler);
        NodeSecurity nodeSecurity = new NodeSecurity();
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(nodeSecurity);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        when(nodeService.findNode("node1", true)).thenReturn(new Node());
        when(statisticManager.newProcessInfo(any())).thenReturn(processInfo);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        doReturn(true).when(spyHandler).handleResume(any(), any(), any(), any());
        spyHandler.handlePull(new PullUriHandler.ResumeRequest("node1", "1", null, null, Constants.QUEUE_DEFAULT), "host", "1.2.3.4",
                new ByteArrayOutputStream(), null, res, new NodeChannels());
        verify(dataExtractorService, never()).extract(any(), any(), any(), any());
    }

    @Test
    void handlePull_noBatchId_neverInvokesResume() throws Exception {
        PullUriHandler spyHandler = spy(handler);
        NodeSecurity nodeSecurity = new NodeSecurity();
        when(nodeService.findNodeSecurity("node1", true)).thenReturn(nodeSecurity);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        when(nodeService.findNode("node1", true)).thenReturn(new Node());
        when(statisticManager.newProcessInfo(any())).thenReturn(processInfo);
        when(dataExtractorService.extract(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        spyHandler.handlePull(new PullUriHandler.ResumeRequest("node1", null, null, null, Constants.QUEUE_DEFAULT), "host", "1.2.3.4",
                new ByteArrayOutputStream(), null, res, new NodeChannels());
        verify(spyHandler, never()).handleResume(any(), any(), any(), any());
        verify(dataExtractorService).extract(any(), any(), any(), any());
    }
}
