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
package org.jumpmind.symmetric.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;
import org.jumpmind.symmetric.transport.internal.InternalTransportManager;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HybridTransportManagerTest {
    private static final String REMOTE_SYNC_URL = "http://remote";
    private static final String INTERNAL_SYNC_URL = "internal://server";
    private HybridTransportManager manager;
    private ISymmetricEngine engine;
    private ISymmetricEngine targetEngine;
    private InternalTransportManager internalTransport;
    private HttpTransportManager httpTransport;
    private IParameterService parameterService;
    private Node remoteNode;
    private Node localNode;
    private Map<String, ISymmetricEngine> registeredEnginesByUrl;

    @BeforeEach
    void setUp() throws Exception {
        engine = mock(ISymmetricEngine.class);
        targetEngine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        internalTransport = mock(InternalTransportManager.class);
        httpTransport = mock(HttpTransportManager.class);
        remoteNode = mock(Node.class);
        localNode = mock(Node.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(remoteNode.getNodeId()).thenReturn("remote-001");
        when(remoteNode.getSyncUrl()).thenReturn(REMOTE_SYNC_URL);
        when(localNode.getNodeId()).thenReturn("local-001");
        manager = new HybridTransportManager(engine);
        setField(manager, "internalTransport", internalTransport);
        setField(manager, "httpTransport", httpTransport);
        registeredEnginesByUrl = getRegisteredEnginesByUrl();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() {
        registeredEnginesByUrl.remove(REMOTE_SYNC_URL);
        registeredEnginesByUrl.remove(INTERNAL_SYNC_URL);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ISymmetricEngine> getRegisteredEnginesByUrl() throws Exception {
        Field field = AbstractSymmetricEngine.class.getDeclaredField("registeredEnginesByUrl");
        field.setAccessible(true);
        return (Map<String, ISymmetricEngine>) field.get(null);
    }

    private void registerEngineForInternalTransport() {
        registeredEnginesByUrl.put(REMOTE_SYNC_URL, targetEngine);
    }

    @Test
    void testSendAcknowledgement_delegatesToHttpTransport() throws IOException {
        when(httpTransport.sendAcknowledgement(any(Node.class), any(), any(Node.class), anyString(), anyString()))
                .thenReturn(WebConstants.SC_OK);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(httpTransport).sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
    }

    @Test
    void testSendAcknowledgement_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        when(internalTransport.sendAcknowledgement(any(Node.class), any(), any(Node.class), anyString(), anyString()))
                .thenReturn(WebConstants.SC_OK);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(internalTransport).sendAcknowledgement(remoteNode, batches, localNode, "token", "http://reg");
    }

    @Test
    void testSendAcknowledgement_withRequestProperties_delegatesToHttpTransport() throws IOException {
        when(httpTransport.sendAcknowledgement(any(Node.class), any(), any(Node.class), anyString(), anyMap(),
                anyString())).thenReturn(WebConstants.SC_OK);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        Map<String, String> requestProps = new HashMap<String, String>();
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", requestProps, "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(httpTransport).sendAcknowledgement(remoteNode, batches, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testSendAcknowledgement_withRequestProperties_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        when(internalTransport.sendAcknowledgement(any(Node.class), any(), any(Node.class), anyString(), anyMap(),
                anyString())).thenReturn(WebConstants.SC_OK);
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        Map<String, String> requestProps = new HashMap<String, String>();
        int result = manager.sendAcknowledgement(remoteNode, batches, localNode, "token", requestProps, "http://reg");
        assertEquals(WebConstants.SC_OK, result);
        verify(internalTransport).sendAcknowledgement(remoteNode, batches, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testWriteAcknowledgement_delegatesToHttpTransport() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        manager.writeAcknowledgement(out, remoteNode, batches, localNode, "token");
        verify(httpTransport).writeAcknowledgement(out, remoteNode, batches, localNode, "token");
    }

    @Test
    void testWriteAcknowledgement_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<IncomingBatch> batches = new ArrayList<IncomingBatch>();
        manager.writeAcknowledgement(out, remoteNode, batches, localNode, "token");
        verify(internalTransport).writeAcknowledgement(out, remoteNode, batches, localNode, "token");
    }

    @Test
    void testReadAcknowledgement_delegatesToHttpTransport() throws IOException {
        List<BatchAck> expectedAcks = new ArrayList<BatchAck>();
        when(httpTransport.readAcknowledgement("param1", "param2")).thenReturn(expectedAcks);
        List<BatchAck> result = manager.readAcknowledgement("param1", "param2");
        assertSame(expectedAcks, result);
        verify(httpTransport).readAcknowledgement("param1", "param2");
    }

    @Test
    void testGetPullTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(httpTransport.getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetPullTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(internalTransport.getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getPullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetPushTransport_delegatesToHttpTransport() throws IOException {
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        when(httpTransport.getPushTransport(remoteNode, localNode, "token", "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getPushTransport(remoteNode, localNode, "token", "http://reg");
    }

    @Test
    void testGetPushTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        when(internalTransport.getPushTransport(remoteNode, localNode, "token", "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getPushTransport(remoteNode, localNode, "token", "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getPushTransport(remoteNode, localNode, "token", "http://reg");
    }

    @Test
    void testGetPushTransport_withRequestProperties_delegatesToHttpTransport() throws IOException {
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(httpTransport.getPushTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetPushTransport_withRequestProperties_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(internalTransport.getPushTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetPingTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(httpTransport.getPingTransport(remoteNode, localNode, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getPingTransport(remoteNode, localNode, "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getPingTransport(remoteNode, localNode, "http://reg");
    }

    @Test
    void testGetPingTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(internalTransport.getPingTransport(remoteNode, localNode, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getPingTransport(remoteNode, localNode, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getPingTransport(remoteNode, localNode, "http://reg");
    }

    @Test
    void testGetFilePullTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(httpTransport.getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetFilePullTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(internalTransport.getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getFilePullTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetFilePushTransport_delegatesToHttpTransport() throws IOException {
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        when(httpTransport.getFilePushTransport(remoteNode, localNode, "token", "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getFilePushTransport(remoteNode, localNode, "token", "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getFilePushTransport(remoteNode, localNode, "token", "http://reg");
    }

    @Test
    void testGetFilePushTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        when(internalTransport.getFilePushTransport(remoteNode, localNode, "token", "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getFilePushTransport(remoteNode, localNode, "token", "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getFilePushTransport(remoteNode, localNode, "token", "http://reg");
    }

    @Test
    void testGetRegisterTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(httpTransport.getRegisterTransport(localNode, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getRegisterTransport(localNode, "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getRegisterTransport(localNode, "http://reg");
    }

    @Test
    void testGetRegisterTransport_delegatesToInternalTransport() throws IOException {
        registeredEnginesByUrl.put("http://reg", targetEngine);
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(internalTransport.getRegisterTransport(localNode, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getRegisterTransport(localNode, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getRegisterTransport(localNode, "http://reg");
        registeredEnginesByUrl.remove("http://reg");
    }

    @Test
    void testGetRegisterTransport_withRequestProperties_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(httpTransport.getRegisterTransport(localNode, "http://reg", requestProps)).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getRegisterTransport(localNode, "http://reg", requestProps);
        assertSame(expectedTransport, result);
        verify(httpTransport).getRegisterTransport(localNode, "http://reg", requestProps);
    }

    @Test
    void testGetRegisterTransport_withRequestProperties_delegatesToInternalTransport() throws IOException {
        registeredEnginesByUrl.put("http://reg", targetEngine);
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(internalTransport.getRegisterTransport(localNode, "http://reg", requestProps)).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getRegisterTransport(localNode, "http://reg", requestProps);
        assertSame(expectedTransport, result);
        verify(internalTransport).getRegisterTransport(localNode, "http://reg", requestProps);
        registeredEnginesByUrl.remove("http://reg");
    }

    @Test
    void testGetRegisterPushTransport_delegatesToHttpTransport() throws IOException {
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        when(httpTransport.getRegisterPushTransport(remoteNode, localNode)).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getRegisterPushTransport(remoteNode, localNode);
        assertSame(expectedTransport, result);
        verify(httpTransport).getRegisterPushTransport(remoteNode, localNode);
    }

    @Test
    void testGetRegisterPushTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        when(internalTransport.getRegisterPushTransport(remoteNode, localNode)).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getRegisterPushTransport(remoteNode, localNode);
        assertSame(expectedTransport, result);
        verify(internalTransport).getRegisterPushTransport(remoteNode, localNode);
    }

    @Test
    void testGetConfigTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(httpTransport.getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg");
    }

    @Test
    void testGetConfigTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(internalTransport.getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg"))
                .thenReturn(expectedTransport);
        IIncomingTransport result = manager.getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getConfigTransport(remoteNode, localNode, "token", "3.14.0", "3.14.0", "http://reg");
    }

    @Test
    void testGetBandwidthPullTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(httpTransport.getBandwidthPullTransport(remoteNode, localNode, "token", requestProps, "http://reg", 1000))
                .thenReturn(expectedTransport);
        IIncomingTransport result = manager.getBandwidthPullTransport(remoteNode, localNode, "token", requestProps, "http://reg", 1000);
        assertSame(expectedTransport, result);
        verify(httpTransport).getBandwidthPullTransport(remoteNode, localNode, "token", requestProps, "http://reg", 1000);
    }

    @Test
    void testGetBandwidthPullTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(internalTransport.getBandwidthPullTransport(remoteNode, localNode, "token", requestProps, "http://reg",
                1000)).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getBandwidthPullTransport(remoteNode, localNode, "token", requestProps, "http://reg", 1000);
        assertSame(expectedTransport, result);
        verify(internalTransport).getBandwidthPullTransport(remoteNode, localNode, "token", requestProps, "http://reg", 1000);
    }

    @Test
    void testGetBandwidthPushTransport_delegatesToHttpTransport() throws IOException {
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(httpTransport.getBandwidthPushTransport(remoteNode, localNode, "token", requestProps, "http://reg")).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getBandwidthPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(httpTransport).getBandwidthPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetBandwidthPushTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        Map<String, String> requestProps = new HashMap<String, String>();
        when(internalTransport.getBandwidthPushTransport(remoteNode, localNode, "token", requestProps, "http://reg"))
                .thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getBandwidthPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getBandwidthPushTransport(remoteNode, localNode, "token", requestProps, "http://reg");
    }

    @Test
    void testGetComparePullTransport_delegatesToHttpTransport() throws IOException {
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestParams = new HashMap<String, String>();
        when(httpTransport.getComparePullTransport(remoteNode, localNode, "token", "http://reg", requestParams)).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getComparePullTransport(remoteNode, localNode, "token", "http://reg", requestParams);
        assertSame(expectedTransport, result);
        verify(httpTransport).getComparePullTransport(remoteNode, localNode, "token", "http://reg", requestParams);
    }

    @Test
    void testGetComparePullTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        Map<String, String> requestParams = new HashMap<String, String>();
        when(internalTransport.getComparePullTransport(remoteNode, localNode, "token", "http://reg", requestParams))
                .thenReturn(expectedTransport);
        IIncomingTransport result = manager.getComparePullTransport(remoteNode, localNode, "token", "http://reg", requestParams);
        assertSame(expectedTransport, result);
        verify(internalTransport).getComparePullTransport(remoteNode, localNode, "token", "http://reg", requestParams);
    }

    @Test
    void testGetComparePushTransport_delegatesToHttpTransport() throws IOException {
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        Map<String, String> requestParams = new HashMap<String, String>();
        when(httpTransport.getComparePushTransport(remoteNode, localNode, "token", "http://reg", requestParams)).thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getComparePushTransport(remoteNode, localNode, "token", "http://reg", requestParams);
        assertSame(expectedTransport, result);
        verify(httpTransport).getComparePushTransport(remoteNode, localNode, "token", "http://reg", requestParams);
    }

    @Test
    void testGetComparePushTransport_delegatesToInternalTransport() throws IOException {
        registerEngineForInternalTransport();
        IOutgoingWithResponseTransport expectedTransport = mock(IOutgoingWithResponseTransport.class);
        Map<String, String> requestParams = new HashMap<String, String>();
        when(internalTransport.getComparePushTransport(remoteNode, localNode, "token", "http://reg", requestParams))
                .thenReturn(expectedTransport);
        IOutgoingWithResponseTransport result = manager.getComparePushTransport(remoteNode, localNode, "token", "http://reg", requestParams);
        assertSame(expectedTransport, result);
        verify(internalTransport).getComparePushTransport(remoteNode, localNode, "token", "http://reg", requestParams);
    }

    @Test
    void testResolveURL_delegatesToHttpTransport() {
        when(httpTransport.resolveURL("http://sync", "http://reg")).thenReturn("http://resolved");
        String result = manager.resolveURL("http://sync", "http://reg");
        assertEquals("http://resolved", result);
        verify(httpTransport).resolveURL("http://sync", "http://reg");
    }

    @Test
    void testResolveURL_delegatesToInternalTransport() {
        registeredEnginesByUrl.put("http://sync", targetEngine);
        when(internalTransport.resolveURL("http://sync", "http://reg")).thenReturn("http://resolved");
        String result = manager.resolveURL("http://sync", "http://reg");
        assertEquals("http://resolved", result);
        verify(internalTransport).resolveURL("http://sync", "http://reg");
        registeredEnginesByUrl.remove("http://sync");
    }

    @Test
    void testSendCopyRequest_delegatesToHttpTransport() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("http://reg");
        when(httpTransport.sendCopyRequest(localNode)).thenReturn(WebConstants.SC_OK);
        int result = manager.sendCopyRequest(localNode);
        assertEquals(WebConstants.SC_OK, result);
        verify(httpTransport).sendCopyRequest(localNode);
    }

    @Test
    void testSendCopyRequest_delegatesToInternalTransport() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn(INTERNAL_SYNC_URL);
        registeredEnginesByUrl.put(INTERNAL_SYNC_URL, targetEngine);
        when(internalTransport.sendCopyRequest(localNode)).thenReturn(WebConstants.SC_OK);
        int result = manager.sendCopyRequest(localNode);
        assertEquals(WebConstants.SC_OK, result);
        verify(internalTransport).sendCopyRequest(localNode);
    }

    @Test
    void testSendStatusRequest_delegatesToHttpTransport() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn("http://reg");
        Map<String, String> statuses = new HashMap<String, String>();
        when(httpTransport.sendStatusRequest(localNode, statuses)).thenReturn(WebConstants.SC_OK);
        int result = manager.sendStatusRequest(localNode, statuses);
        assertEquals(WebConstants.SC_OK, result);
        verify(httpTransport).sendStatusRequest(localNode, statuses);
    }

    @Test
    void testSendStatusRequest_delegatesToInternalTransport() throws IOException {
        when(parameterService.getRegistrationUrl()).thenReturn(INTERNAL_SYNC_URL);
        registeredEnginesByUrl.put(INTERNAL_SYNC_URL, targetEngine);
        Map<String, String> statuses = new HashMap<String, String>();
        when(internalTransport.sendStatusRequest(localNode, statuses)).thenReturn(WebConstants.SC_OK);
        int result = manager.sendStatusRequest(localNode, statuses);
        assertEquals(WebConstants.SC_OK, result);
        verify(internalTransport).sendStatusRequest(localNode, statuses);
    }

    @Test
    void testWriteRequestProperties_delegatesToHttpTransport() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Map<String, String> requestProps = new HashMap<String, String>();
        manager.writeRequestProperties(requestProps, os);
        verify(httpTransport).writeRequestProperties(requestProps, os);
    }

    @Test
    void testReadRequestProperties_delegatesToHttpTransport() throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[0]);
        Map<String, String> expectedProps = new HashMap<String, String>();
        when(httpTransport.readRequestProperties(is)).thenReturn(expectedProps);
        Map<String, String> result = manager.readRequestProperties(is);
        assertSame(expectedProps, result);
        verify(httpTransport).readRequestProperties(is);
    }

    @Test
    void testDelegation_handlesNullRemoteNode() throws IOException {
        when(httpTransport.getPingTransport(null, localNode, "http://reg")).thenReturn(null);
        manager.getPingTransport(null, localNode, "http://reg");
        verify(httpTransport).getPingTransport(null, localNode, "http://reg");
    }

    @Test
    void testDelegation_usesRemoteNodeSyncUrl() throws IOException {
        when(remoteNode.getSyncUrl()).thenReturn(INTERNAL_SYNC_URL);
        registeredEnginesByUrl.put(INTERNAL_SYNC_URL, targetEngine);
        IIncomingTransport expectedTransport = mock(IIncomingTransport.class);
        when(internalTransport.getPingTransport(remoteNode, localNode, "http://reg")).thenReturn(expectedTransport);
        IIncomingTransport result = manager.getPingTransport(remoteNode, localNode, "http://reg");
        assertSame(expectedTransport, result);
        verify(internalTransport).getPingTransport(remoteNode, localNode, "http://reg");
    }
}
