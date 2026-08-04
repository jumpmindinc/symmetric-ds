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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.file.FileSyncPullResult;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class FileSyncPullUriHandlerTest {
    private ISymmetricEngine engine;
    private IFileSyncService fileSyncService;
    private INodeService nodeService;
    private FileSyncPullUriHandler handler;
    private HttpServletRequest req;
    private HttpServletResponse res;

    @BeforeEach
    void setUp() throws Exception {
        engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        fileSyncService = mock(IFileSyncService.class);
        when(engine.getFileSyncService()).thenReturn(fileSyncService);
        nodeService = mock(INodeService.class);
        when(nodeService.findIdentityNodeId()).thenReturn("local");
        when(engine.getNodeService()).thenReturn(nodeService);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        when(configurationService.getSuspendIgnoreChannelLists(anyString())).thenReturn(new NodeChannels());
        when(engine.getConfigurationService()).thenReturn(configurationService);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(statisticManager.newProcessInfo(any())).thenAnswer(
                inv -> new ProcessInfo(new ProcessInfoKey("local", "node1", ProcessType.FILE_SYNC_PULL_HANDLER)));
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        handler = new FileSyncPullUriHandler(engine);
        req = mock(HttpServletRequest.class);
        res = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(res.getOutputStream()).thenReturn(outputStream);
    }

    @Test
    void handle_missingNodeId_sendsBadRequestAndSkipsFileSyncService() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn(null);
        handler.handle(req, res);
        verify(res).sendError(WebConstants.SC_BAD_REQUEST, "Node must be specified");
        verify(fileSyncService, never()).prepareFilesForPull(any(), any(), any(), any(), any());
    }

    @Test
    void handle_passesBatchIdAndResumeHeadersThrough() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn("node1");
        when(req.getParameter(WebConstants.BATCH_ID)).thenReturn("42");
        when(req.getHeader(WebConstants.HEADER_IF_ETAG)).thenReturn("{\"etag\":true}");
        when(req.getHeader(WebConstants.HEADER_RANGE)).thenReturn("bytes=10-");
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        ArrayList<OutgoingBatch> batches = new ArrayList<>();
        batches.add(new OutgoingBatch());
        when(fileSyncService.prepareFilesForPull(any(), eq(targetNode), eq("42"), eq("{\"etag\":true}"), eq("bytes=10-")))
                .thenAnswer(inv -> {
                    ProcessInfo processInfo = (ProcessInfo) inv.getArgument(0);
                    processInfo.incrementBatchCount();
                    return FileSyncPullResult.builder().batches(batches).allRequestedBatches(batches).envelopeFormatUsed(false).build();
                });
        handler.handle(req, res);
        verify(fileSyncService).prepareFilesForPull(any(), eq(targetNode), eq("42"), eq("{\"etag\":true}"), eq("bytes=10-"));
    }

    @Test
    void handle_resumeEtagPresentAndPartialContent_setsPartialContentHeaders() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn("node1");
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        StagedResourceETag etag = new StagedResourceETag(100L, 500L);
        ArrayList<OutgoingBatch> batches = new ArrayList<>();
        batches.add(new OutgoingBatch());
        when(fileSyncService.prepareFilesForPull(any(), eq(targetNode), isNull(), isNull(), isNull()))
                .thenAnswer(inv -> {
                    ProcessInfo processInfo = (ProcessInfo) inv.getArgument(0);
                    processInfo.incrementBatchCount();
                    return FileSyncPullResult.builder().batches(batches).partialContent(true).resumeEtag(etag)
                            .totalSize(500L).skipCount(200L).build();
                });
        handler.handle(req, res);
        verify(res).setHeader(WebConstants.HEADER_ETAG, etag.toJson());
        verify(res).setHeader(WebConstants.HEADER_ACCEPT_RANGES, "bytes");
        verify(res).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(res).setHeader(WebConstants.HEADER_CONTENT_RANGE, "200-499/500");
    }

    @Test
    void handle_resumeEtagPresentButFullContent_setsEtagWithoutPartialStatus() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn("node1");
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        StagedResourceETag etag = new StagedResourceETag(100L, 500L);
        ArrayList<OutgoingBatch> batches = new ArrayList<>();
        batches.add(new OutgoingBatch());
        when(fileSyncService.prepareFilesForPull(any(), eq(targetNode), isNull(), isNull(), isNull()))
                .thenAnswer(inv -> {
                    ProcessInfo processInfo = (ProcessInfo) inv.getArgument(0);
                    processInfo.incrementBatchCount();
                    return FileSyncPullResult.builder().batches(batches).partialContent(false).resumeEtag(etag)
                            .totalSize(500L).skipCount(0L).build();
                });
        handler.handle(req, res);
        verify(res).setHeader(WebConstants.HEADER_ETAG, etag.toJson());
        verify(res).setHeader(WebConstants.HEADER_ACCEPT_RANGES, "bytes");
        verify(res, never()).setStatus(WebConstants.SC_PARTIAL_CONTENT);
        verify(res, never()).setHeader(eq(WebConstants.HEADER_CONTENT_RANGE), anyString());
    }

    @Test
    void handle_envelopeFormatUsed_setsFileSyncFormatHeader() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn("node1");
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        ArrayList<OutgoingBatch> batches = new ArrayList<>();
        batches.add(new OutgoingBatch());
        when(fileSyncService.prepareFilesForPull(any(), eq(targetNode), isNull(), isNull(), isNull()))
                .thenAnswer(inv -> {
                    ProcessInfo processInfo = (ProcessInfo) inv.getArgument(0);
                    processInfo.incrementBatchCount();
                    return FileSyncPullResult.builder().batches(batches).allRequestedBatches(batches).envelopeFormatUsed(true).build();
                });
        handler.handle(req, res);
        verify(res).setHeader(WebConstants.HEADER_FILESYNC_FORMAT, "1");
    }

    @Test
    void handle_legacyFormat_doesNotSetFileSyncFormatHeaderOrResumeHeaders() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn("node1");
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        ArrayList<OutgoingBatch> batches = new ArrayList<>();
        batches.add(new OutgoingBatch());
        when(fileSyncService.prepareFilesForPull(any(), eq(targetNode), isNull(), isNull(), isNull()))
                .thenAnswer(inv -> {
                    ProcessInfo processInfo = (ProcessInfo) inv.getArgument(0);
                    processInfo.incrementBatchCount();
                    return FileSyncPullResult.builder().batches(batches).allRequestedBatches(batches).envelopeFormatUsed(false).build();
                });
        handler.handle(req, res);
        verify(res, never()).setHeader(eq(WebConstants.HEADER_FILESYNC_FORMAT), anyString());
        verify(res, never()).setHeader(eq(WebConstants.HEADER_ETAG), anyString());
        verify(res).setContentType("application/zip");
    }

    @Test
    void handle_noBatchesForNewPeer_sendsNoContentInsteadOfZipContentType() throws Exception {
        when(req.getParameter(WebConstants.NODE_ID)).thenReturn("node1");
        Node targetNode = new Node();
        targetNode.setNodeId("node1");
        targetNode.setSymmetricVersion("3.18.0");
        when(nodeService.findNode("node1", true)).thenReturn(targetNode);
        when(fileSyncService.prepareFilesForPull(any(), eq(targetNode), isNull(), isNull(), isNull()))
                .thenReturn(FileSyncPullResult.builder().batches(Collections.emptyList())
                        .allRequestedBatches(Collections.emptyList()).envelopeFormatUsed(false).build());
        handler.handle(req, res);
        verify(res).sendError(HttpServletResponse.SC_NO_CONTENT, "No files to pull.");
        verify(res, never()).setContentType("application/zip");
        verify(fileSyncService, never()).writeFilesForPull(any(), any());
    }
}
