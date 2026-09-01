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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.symmetric.service.INodeCommunicationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.http.IHttpResumeCache;
import org.jumpmind.symmetric.transport.http.ResumeCacheEntry;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataLoaderServiceTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private ITransportManager transportManager;
    private IHttpResumeCache resumeCache;
    private DataLoaderService dataLoaderService;
    private Node remote;
    private Node local;
    private RemoteNodeStatus status;
    private IIncomingTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        engine = mock(ISymmetricEngine.class);
        when(engine.getTablePrefix()).thenReturn("sym");
        parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getRegistrationUrl()).thenReturn("http://registration");
        when(engine.getParameterService()).thenReturn(parameterService);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        ISqlTemplate sqlTemplateDirty = mock(ISqlTemplate.class);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        IIncomingBatchService incomingBatchService = mock(IIncomingBatchService.class);
        when(engine.getIncomingBatchService()).thenReturn(incomingBatchService);
        configurationService = mock(IConfigurationService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        transportManager = mock(ITransportManager.class);
        when(engine.getTransportManager()).thenReturn(transportManager);
        resumeCache = mock(IHttpResumeCache.class);
        when(transportManager.getResumeCache()).thenReturn(resumeCache);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(statisticManager.newProcessInfo(any())).thenReturn(new ProcessInfo(
                new ProcessInfoKey("remote1", Constants.QUEUE_DEFAULT, "me", ProcessType.PULL_JOB_TRANSFER)));
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        ITransformService transformService = mock(ITransformService.class);
        when(engine.getTransformService()).thenReturn(transformService);
        ILoadFilterService loadFilterService = mock(ILoadFilterService.class);
        when(engine.getLoadFilterService()).thenReturn(loadFilterService);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        INodeCommunicationService nodeCommunicationService = mock(INodeCommunicationService.class);
        when(engine.getNodeCommunicationService()).thenReturn(nodeCommunicationService);
        ICacheManager cacheManager = mock(ICacheManager.class);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        dataLoaderService = spy(new DataLoaderService(engine));
        doReturn(Collections.emptyList()).when(dataLoaderService).loadDataFromTransport(any(), any(), any(), any(), any(), any());
        remote = new Node();
        remote.setNodeId("remote1");
        local = new Node();
        local.setNodeId("me");
        when(nodeService.findIdentity()).thenReturn(local);
        NodeSecurity localSecurity = new NodeSecurity();
        when(nodeService.findNodeSecurity("me", true)).thenReturn(localSecurity);
        when(configurationService.getSuspendIgnoreChannelLists("remote1")).thenReturn(new NodeChannels());
        status = new RemoteNodeStatus("remote1", Constants.CHANNEL_DEFAULT, new HashMap<>());
        transport = mock(IIncomingTransport.class);
        when(transportManager.getPullTransport(any(), any(), any(), anyMap(), any(), any())).thenReturn(transport);
    }

    @Test
    void loadDataFromPull_noPendingResume_pullsNormallyWithNullResumeBatchId() throws Exception {
        when(resumeCache.getPendingForNode("remote1", Constants.QUEUE_DEFAULT)).thenReturn(null);
        when(transport.getHeaders()).thenReturn(new HashMap<>());
        dataLoaderService.loadDataFromPull(remote, status);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transportManager).getPullTransport(eq(remote), eq(local), any(), propsCaptor.capture(), any(), isNull());
        assertNull(propsCaptor.getValue().get(WebConstants.HEADER_IF_ETAG));
        assertNull(propsCaptor.getValue().get(WebConstants.HEADER_RANGE));
        verify(dataLoaderService).loadDataFromTransport(any(), eq(remote), eq(transport), isNull(), eq(status), isNull());
        verify(resumeCache, never()).remove(any(), any(Long.class));
    }

    @Test
    void loadDataFromPull_pendingResume_addsIfETagAndRangeHeadersAndRequestsResumeBatchId() throws Exception {
        StagedResourceETag etag = new StagedResourceETag(111L, 500L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId("remote1")
                .batchId(77L)
                .etag(etag)
                .receivedCount(200L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(123L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        when(resumeCache.getPendingForNode("remote1", Constants.QUEUE_DEFAULT)).thenReturn(pendingResume);
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put(WebConstants.HEADER_CONTENT_RANGE, "200-499/500");
        when(transport.getHeaders()).thenReturn(responseHeaders);
        dataLoaderService.loadDataFromPull(remote, status);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transportManager).getPullTransport(eq(remote), eq(local), any(), propsCaptor.capture(), any(), eq(77L));
        assertEquals(etag.toJson(), propsCaptor.getValue().get(WebConstants.HEADER_IF_ETAG));
        assertEquals("chars=200-", propsCaptor.getValue().get(WebConstants.HEADER_RANGE));
        verify(dataLoaderService).loadDataFromTransport(any(), eq(remote), eq(transport), isNull(), eq(status), eq(pendingResume));
        verify(resumeCache, never()).remove(any(), any(Long.class));
    }

    @Test
    void loadDataFromPull_serverDeclinesResume_clearsCacheAndFallsBackToNormalLoad() throws Exception {
        StagedResourceETag etag = new StagedResourceETag(111L, 500L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId("remote1")
                .batchId(77L)
                .etag(etag)
                .receivedCount(200L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(123L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        when(resumeCache.getPendingForNode("remote1", Constants.QUEUE_DEFAULT)).thenReturn(pendingResume);
        when(transport.getHeaders()).thenReturn(new HashMap<>());
        dataLoaderService.loadDataFromPull(remote, status);
        verify(resumeCache).remove("remote1", 77L);
        verify(dataLoaderService).loadDataFromTransport(any(), eq(remote), eq(transport), isNull(), eq(status), isNull());
    }

    @Test
    void loadDataFromPull_noResumeCacheAvailable_pullsNormallyWithoutNpe() throws Exception {
        when(transportManager.getResumeCache()).thenReturn(null);
        when(transport.getHeaders()).thenReturn(new HashMap<>());
        dataLoaderService.loadDataFromPull(remote, status);
        verify(transportManager).getPullTransport(eq(remote), eq(local), any(), anyMap(), any(), isNull());
        verify(dataLoaderService).loadDataFromTransport(any(), eq(remote), eq(transport), isNull(), eq(status), isNull());
    }

    @Test
    void loadDataFromPull_pendingResumeBelongsToFileSync_ignoresItAndPullsNormally() throws Exception {
        StagedResourceETag etag = new StagedResourceETag(111L, 500L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId("remote1")
                .batchId(77L)
                .etag(etag)
                .receivedCount(200L)
                .cachedAtTime(123L)
                .queue(Constants.QUEUE_DEFAULT)
                .fileSync(true)
                .build();
        when(resumeCache.getPendingForNode("remote1", Constants.QUEUE_DEFAULT)).thenReturn(pendingResume);
        when(transport.getHeaders()).thenReturn(new HashMap<>());
        dataLoaderService.loadDataFromPull(remote, status);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transportManager).getPullTransport(eq(remote), eq(local), any(), propsCaptor.capture(), any(), isNull());
        assertNull(propsCaptor.getValue().get(WebConstants.HEADER_IF_ETAG));
        assertNull(propsCaptor.getValue().get(WebConstants.HEADER_RANGE));
        verify(dataLoaderService).loadDataFromTransport(any(), eq(remote), eq(transport), isNull(), eq(status), isNull());
        verify(resumeCache, never()).remove(any(), any(Long.class));
    }

    @Test
    void loadDataFromPull_connectionFailsDuringResumeCheck_preservesCacheEntryAndPropagatesException() throws Exception {
        StagedResourceETag etag = new StagedResourceETag(111L, 500L);
        ResumeCacheEntry pendingResume = ResumeCacheEntry.builder()
                .nodeId("remote1")
                .batchId(77L)
                .etag(etag)
                .receivedCount(200L)
                .channelId("channel1")
                .binaryEncoding("NONE")
                .cachedAtTime(123L)
                .queue(Constants.QUEUE_DEFAULT)
                .build();
        when(resumeCache.getPendingForNode("remote1", Constants.QUEUE_DEFAULT)).thenReturn(pendingResume);
        when(transport.getHeaders()).thenThrow(new IOException("Connection refused"));
        assertThrows(IOException.class, () -> dataLoaderService.loadDataFromPull(remote, status));
        verify(resumeCache, never()).remove(any(), any(Long.class));
    }
}
