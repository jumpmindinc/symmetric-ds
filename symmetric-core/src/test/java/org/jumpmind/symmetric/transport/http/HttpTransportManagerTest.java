package org.jumpmind.symmetric.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class HttpTransportManagerTest {
    private HttpTransportManager manager;
    private ISymmetricEngine engine;
    private Node remoteNode;
    private Node localNode;
    private IncomingBatch batch;
    private IParameterService ps;

    @BeforeEach
    void setUp() throws Exception {
        engine = mock(ISymmetricEngine.class);
        remoteNode = mock(Node.class);
        localNode = mock(Node.class);
        batch = mock(IncomingBatch.class);
        ps = mock(IParameterService.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(extensionService.getExtensionPointList(any())).thenReturn(Collections.emptyList());
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(remoteNode.getNodeId()).thenReturn("remote-001");
        when(localNode.getNodeId()).thenReturn("local-001");
        when(remoteNode.getNodeGroupId()).thenReturn("group-remote");
        when(localNode.getNodeGroupId()).thenReturn("group-local");
        when(remoteNode.getSyncUrl()).thenReturn("http://remote.example/sync");
        when(engine.getParameterService()).thenReturn(ps);
        when(engine.getParameterService().getInt(ParameterConstants.TRANSPORT_MAX_FORM_KEYS)).thenReturn(1000);
        when(engine.getParameterService().getInt(ParameterConstants.TRANSPORT_MAX_BYTES_TO_SYNC)).thenReturn(100000);
        when(engine.getParameterService().is(anyString())).thenReturn(false);
        manager = spy(new HttpTransportManager(engine));
        doReturn(200).when(manager).sendMessage(
                anyString(), any(Node.class), any(Node.class),
                anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    void testConstructor_initializesDefaultResumeCache() {
        assertNotNull(manager.getResumeCache());
        assertInstanceOf(DefaultHttpResumeCache.class, manager.getResumeCache());
    }

    @Test
    void testSendAcknowledgement_basic() throws Exception {
        List<IncomingBatch> batches = List.of(batch);
        doReturn(200).when(manager).sendMessage(
                eq("ack"), any(Node.class), any(Node.class),
                eq("ackData"), anyString(), anyMap(), anyString());
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", new HashMap<>(), "http://url");
        assertEquals(200, result);
    }

    @Test
    void testSendAcknowledgement_handlesBadRequest() throws Exception {
        List<IncomingBatch> batches = List.of(batch);
        doReturn(400).when(manager).sendMessage(anyString(), any(), any(), anyString(), any(), any(), anyString());
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", null, "http://url");
        assertEquals(400, result);
        assertEquals(1, manager.backOffPostCount);
    }

    @Test
    void testSendAcknowledgement_emptyListReturnsOk() throws Exception {
        int result = manager.sendAcknowledgement(remoteNode, Collections.emptyList(), localNode, "token", "http://url");
        assertEquals(200, result);
        verify(manager, never()).sendMessage(any(), any(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void testSendAcknowledgement_nullListReturnsOk() throws Exception {
        int result = manager.sendAcknowledgement(remoteNode, null, localNode, "token", "http://url");
        assertEquals(200, result);
        verify(manager, never()).sendMessage(any(), any(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void testSendAcknowledgement_setsDefaultMaxFormKeys_whenBackOffAndZeroMaxFormKeys() throws Exception {
        manager.backOffPostCount = 1;
        when(ps.getInt(ParameterConstants.TRANSPORT_MAX_FORM_KEYS)).thenReturn(0);
        doReturn(200).when(manager).sendMessage(anyString(), any(Node.class), any(Node.class),
                anyString(), anyString(), anyMap(), anyString());
        List<IncomingBatch> batches = List.of(batch);
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", new HashMap<>(), "http://url");
        assertEquals(200, result);
    }

    @Test
    void testGetPullTransport_sixArgWithResumeBatchId_appendsBatchIdToUrl() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.18.0");
        HttpConnection conn = mock(HttpConnection.class);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        doReturn(conn).when(manager).createGetConnectionFor(urlCaptor.capture(), anyString(), any());
        manager.getPullTransport(remoteNode, localNode, "token", new HashMap<>(), "http://reg", 42L);
        assertTrue(urlCaptor.getValue().toString().contains("batchId=42"));
    }

    @Test
    void testGetPullTransport_sixArgWithNullResumeBatchId_omitsBatchIdFromUrl() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.18.0");
        HttpConnection conn = mock(HttpConnection.class);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        doReturn(conn).when(manager).createGetConnectionFor(urlCaptor.capture(), anyString(), any());
        manager.getPullTransport(remoteNode, localNode, "token", new HashMap<>(), "http://reg", null);
        assertFalse(urlCaptor.getValue().toString().contains("batchId="));
    }

    @Test
    void testGetPullTransport_fiveArg_delegatesWithoutResumeBatchId() throws Exception {
        when(remoteNode.getSymmetricVersion()).thenReturn("3.18.0");
        HttpConnection conn = mock(HttpConnection.class);
        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        doReturn(conn).when(manager).createGetConnectionFor(urlCaptor.capture(), anyString(), any());
        manager.getPullTransport(remoteNode, localNode, "token", new HashMap<>(), "http://reg");
        assertFalse(urlCaptor.getValue().toString().contains("batchId="));
    }
}
