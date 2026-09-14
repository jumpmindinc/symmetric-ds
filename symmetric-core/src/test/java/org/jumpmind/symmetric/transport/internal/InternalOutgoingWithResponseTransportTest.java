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
package org.jumpmind.symmetric.transport.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InternalOutgoingWithResponseTransportTest {
    private ByteArrayOutputStream outputStream;
    private ByteArrayInputStream responseInputStream;
    private IConfigurationService configurationService;
    private Node targetNode;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        responseInputStream = new ByteArrayInputStream("response".getBytes());
        configurationService = mock(IConfigurationService.class);
        targetNode = mock(Node.class);
        when(targetNode.getNodeId()).thenReturn("target-001");
    }

    @Test
    void testConstructorWithTwoArgs() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertNotNull(transport);
        assertTrue(transport.isOpen());
    }

    @Test
    void testConstructorWithThreeArgs() {
        NodeChannels nodeChannels = new NodeChannels();
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, nodeChannels);
        assertNotNull(transport);
        assertTrue(transport.isOpen());
    }

    @Test
    void testConstructorWithNullNodeChannels() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, null);
        assertNotNull(transport);
        assertTrue(transport.isOpen());
    }

    @Test
    void testGetSuspendIgnoreChannelLists_withNullNodeChannels_returnsLocalConfigOnly() {
        NodeChannels localChannels = new NodeChannels();
        localChannels.addSuspendChannels("target-001", "channel1");
        localChannels.addIgnoreChannels("target-001", "channel2");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertNotNull(result);
        assertEquals("channel1", result.getSuspendChannelsAsString("target-001"));
        assertEquals("channel2", result.getIgnoreChannelsAsString("target-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_withRemoteNodeChannels_combinesBothConfigs() {
        NodeChannels remoteChannels = new NodeChannels();
        remoteChannels.addSuspendChannels("target-001", "remoteChannel1");
        remoteChannels.addIgnoreChannels("target-001", "remoteChannel2");
        NodeChannels localChannels = new NodeChannels();
        localChannels.addSuspendChannels("target-001", "localChannel1");
        localChannels.addIgnoreChannels("target-001", "localChannel2");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertNotNull(result);
        String suspendChannels = result.getSuspendChannelsAsString("target-001");
        assertTrue(suspendChannels.contains("remoteChannel1"));
        assertTrue(suspendChannels.contains("localChannel1"));
        String ignoreChannels = result.getIgnoreChannelsAsString("target-001");
        assertTrue(ignoreChannels.contains("remoteChannel2"));
        assertTrue(ignoreChannels.contains("localChannel2"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_remoteOnlySuspend() {
        NodeChannels remoteChannels = new NodeChannels();
        remoteChannels.addSuspendChannels("target-001", "remoteSuspend");
        NodeChannels localChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertEquals("remoteSuspend", result.getSuspendChannelsAsString("target-001"));
        assertEquals("", result.getIgnoreChannelsAsString("target-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_localOnlySuspend() {
        NodeChannels remoteChannels = new NodeChannels();
        NodeChannels localChannels = new NodeChannels();
        localChannels.addSuspendChannels("target-001", "localSuspend");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertEquals("localSuspend", result.getSuspendChannelsAsString("target-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_remoteOnlyIgnore() {
        NodeChannels remoteChannels = new NodeChannels();
        remoteChannels.addIgnoreChannels("target-001", "remoteIgnore");
        NodeChannels localChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertEquals("", result.getSuspendChannelsAsString("target-001"));
        assertEquals("remoteIgnore", result.getIgnoreChannelsAsString("target-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_localOnlyIgnore() {
        NodeChannels remoteChannels = new NodeChannels();
        NodeChannels localChannels = new NodeChannels();
        localChannels.addIgnoreChannels("target-001", "localIgnore");
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertEquals("localIgnore", result.getIgnoreChannelsAsString("target-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_emptyBothConfigs() {
        NodeChannels remoteChannels = new NodeChannels();
        NodeChannels localChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertNotNull(result);
        assertEquals("", result.getSuspendChannelsAsString("target-001"));
        assertEquals("", result.getIgnoreChannelsAsString("target-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_multipleChannelsFromRemote() {
        NodeChannels remoteChannels = new NodeChannels();
        remoteChannels.addSuspendChannels("target-001", "channel1,channel2,channel3");
        NodeChannels localChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        String suspendChannels = result.getSuspendChannelsAsString("target-001");
        assertTrue(suspendChannels.contains("channel1"));
        assertTrue(suspendChannels.contains("channel2"));
        assertTrue(suspendChannels.contains("channel3"));
    }

    @Test
    void testOpenStream() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertEquals(outputStream, transport.openStream());
    }

    @Test
    void testOpenWriter() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertNotNull(transport.openWriter());
    }

    @Test
    void testGetWriter() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertNotNull(transport.getWriter());
        assertEquals(transport.openWriter(), transport.getWriter());
    }

    @Test
    void testReadResponse() throws IOException {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertNotNull(transport.readResponse());
    }

    @Test
    void testClose() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertTrue(transport.isOpen());
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_afterConstruction() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        assertTrue(transport.isOpen());
    }

    @Test
    void testIsOpen_afterClose() {
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream);
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testGetSuspendIgnoreChannelLists_differentTargetNodeId() {
        NodeChannels remoteChannels = new NodeChannels();
        remoteChannels.addSuspendChannels("other-node", "channel1");
        remoteChannels.addSuspendChannels("target-001", "channel2");
        NodeChannels localChannels = new NodeChannels();
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        InternalOutgoingWithResponseTransport transport = new InternalOutgoingWithResponseTransport(outputStream, responseInputStream, remoteChannels);
        NodeChannels result = transport.getSuspendIgnoreChannelLists(configurationService, "default", targetNode);
        assertEquals("channel2", result.getSuspendChannelsAsString("target-001"));
        assertEquals("channel1", result.getSuspendChannelsAsString("other-node"));
    }
}
