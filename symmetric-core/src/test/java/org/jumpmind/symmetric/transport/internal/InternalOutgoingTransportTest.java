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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.junit.jupiter.api.Test;

class InternalOutgoingTransportTest {
    @Test
    void testConstructor_withOutputStreamAndEncoding() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, "UTF-8");
        assertTrue(transport.isOpen());
        assertSame(os, transport.openStream());
        assertNotNull(transport.openWriter());
        assertNotNull(transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), Constants.QUEUE_DEFAULT, mock(Node.class)));
    }

    @Test
    void testConstructor_withOutputStreamAndNullEncoding_usesDefaultCharset() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, (String) null);
        assertTrue(transport.isOpen());
        assertNotNull(transport.openWriter());
    }

    @Test
    void testConstructor_withInvalidEncoding_throwsUnsupportedEncodingException() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        assertThrows(UnsupportedEncodingException.class, () -> new InternalOutgoingTransport(os, "invalid-encoding-xyz"));
    }

    @Test
    void testConstructor_withOutputStreamNodeChannelsAndEncoding() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannels("node-001", "channel1");
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, nodeChannels, "UTF-8");
        assertTrue(transport.isOpen());
        NodeChannels result = transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), Constants.QUEUE_DEFAULT, mock(Node.class));
        assertSame(nodeChannels, result);
    }

    @Test
    void testConstructor_withBufferedWriter() {
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        InternalOutgoingTransport transport = new InternalOutgoingTransport(writer);
        assertTrue(transport.isOpen());
        assertSame(writer, transport.getWriter());
        assertNull(transport.openStream());
        assertNotNull(transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), Constants.QUEUE_DEFAULT, mock(Node.class)));
    }

    @Test
    void testClose_setsOpenFalseAndClosesWriter() throws IOException {
        BufferedWriter writer = mock(BufferedWriter.class);
        InternalOutgoingTransport transport = new InternalOutgoingTransport(writer);
        transport.close();
        assertFalse(transport.isOpen());
        verify(writer).close();
    }

    @Test
    void testClose_whenWriterThrowsIOException_doesNotPropagate() throws IOException {
        BufferedWriter writer = mock(BufferedWriter.class);
        doThrow(new IOException("boom")).when(writer).close();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(writer);
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testClose_withNullWriter_doesNotThrow() {
        InternalOutgoingTransport transport = new InternalOutgoingTransport((BufferedWriter) null);
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsTrueAfterConstruction() {
        InternalOutgoingTransport transport = new InternalOutgoingTransport(new BufferedWriter(new StringWriter()));
        assertTrue(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsFalseAfterClose() {
        InternalOutgoingTransport transport = new InternalOutgoingTransport(new BufferedWriter(new StringWriter()));
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testOpenStream_returnsOutputStream() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, "UTF-8");
        assertSame(os, transport.openStream());
    }

    @Test
    void testOpenStream_returnsNullWhenConstructedFromWriter() {
        InternalOutgoingTransport transport = new InternalOutgoingTransport(new BufferedWriter(new StringWriter()));
        assertNull(transport.openStream());
    }

    @Test
    void testOpenWriter_returnsWriter() {
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        InternalOutgoingTransport transport = new InternalOutgoingTransport(writer);
        assertSame(writer, transport.openWriter());
    }

    @Test
    void testGetWriter_returnsSameAsOpenWriter() {
        BufferedWriter writer = new BufferedWriter(new StringWriter());
        InternalOutgoingTransport transport = new InternalOutgoingTransport(writer);
        assertSame(transport.openWriter(), transport.getWriter());
    }

    @Test
    void testGetSuspendIgnoreChannelLists_returnsDefaultNodeChannelsWhenNotProvided() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, "UTF-8");
        NodeChannels result = transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), Constants.QUEUE_DEFAULT, mock(Node.class));
        assertEquals("", result.getSuspendChannelsAsString("node-001"));
        assertEquals("", result.getIgnoreChannelsAsString("node-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_returnsProvidedNodeChannels() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannels("node-001", "channel1");
        nodeChannels.addIgnoreChannels("node-001", "channel2");
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, nodeChannels, "UTF-8");
        NodeChannels result = transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), Constants.QUEUE_DEFAULT, mock(Node.class));
        assertEquals("channel1", result.getSuspendChannelsAsString("node-001"));
        assertEquals("channel2", result.getIgnoreChannelsAsString("node-001"));
    }

    @Test
    void testGetSuspendIgnoreChannelLists_ignoresQueueAndTargetNodeArguments() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        NodeChannels nodeChannels = new NodeChannels();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(os, nodeChannels, "UTF-8");
        Node targetNode = mock(Node.class);
        NodeChannels resultOne = transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), "queue-a", targetNode);
        NodeChannels resultTwo = transport.getSuspendIgnoreChannelLists(mock(IConfigurationService.class), "queue-b", null);
        assertSame(nodeChannels, resultOne);
        assertSame(nodeChannels, resultTwo);
    }
}
