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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InternalIncomingTransportTest {
    private static final String TEST_DATA = "test data";

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    void testConstructor_withInputStreamOnly() throws IOException {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        assertTrue(transport.isOpen());
        assertNotNull(transport.openReader());
        assertSame(is, transport.openStream());
        assertNull(transport.getHeaders());
    }

    @Test
    void testConstructor_withInputStreamAndHeaders() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        InternalIncomingTransport transport = new InternalIncomingTransport(is, headers);
        assertTrue(transport.isOpen());
        assertSame(headers, transport.getHeaders());
    }

    @Test
    void testConstructor_withInputStreamHeadersAndHeadersReady() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        CountDownLatch headersReady = new CountDownLatch(1);
        headersReady.countDown();
        InternalIncomingTransport transport = new InternalIncomingTransport(is, headers, headersReady);
        assertTrue(transport.isOpen());
        assertSame(headers, transport.getHeaders());
    }

    @Test
    void testConstructor_withBufferedReaderOnly() throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(TEST_DATA));
        InternalIncomingTransport transport = new InternalIncomingTransport(reader);
        assertTrue(transport.isOpen());
        assertSame(reader, transport.openReader());
        assertNull(transport.openStream());
        assertNull(transport.getHeaders());
    }

    @Test
    void testConstructor_withBufferedReaderAndHeaders() {
        BufferedReader reader = new BufferedReader(new StringReader(TEST_DATA));
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        InternalIncomingTransport transport = new InternalIncomingTransport(reader, headers);
        assertTrue(transport.isOpen());
        assertSame(headers, transport.getHeaders());
    }

    @Test
    void testConstructor_withBufferedReaderHeadersAndHeadersReady() {
        BufferedReader reader = new BufferedReader(new StringReader(TEST_DATA));
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        CountDownLatch headersReady = new CountDownLatch(1);
        headersReady.countDown();
        InternalIncomingTransport transport = new InternalIncomingTransport(reader, headers, headersReady);
        assertTrue(transport.isOpen());
        assertSame(headers, transport.getHeaders());
    }

    @Test
    void testClose_closesReaderAndStream() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        transport.close();
        assertFalse(transport.isOpen());
        assertNull(transport.reader);
        assertNull(transport.is);
    }

    @Test
    void testClose_whenReaderThrowsIOException_doesNotPropagate() throws IOException {
        BufferedReader reader = mock(BufferedReader.class);
        doThrow(new IOException("boom")).when(reader).close();
        InternalIncomingTransport transport = new InternalIncomingTransport(reader);
        transport.close();
        assertFalse(transport.isOpen());
        assertNull(transport.reader);
    }

    @Test
    void testClose_whenStreamThrowsIOException_doesNotPropagate() throws IOException {
        InputStream is = mock(InputStream.class);
        doThrow(new IOException("boom")).when(is).close();
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        transport.close();
        assertFalse(transport.isOpen());
        assertNull(transport.is);
    }

    @Test
    void testClose_withNoReaderOrStream_doesNotThrow() {
        InternalIncomingTransport transport = new InternalIncomingTransport((BufferedReader) null);
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsTrueWhenReaderAndStreamPresent() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        assertTrue(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsTrueWhenOnlyReaderPresent() {
        BufferedReader reader = new BufferedReader(new StringReader(TEST_DATA));
        InternalIncomingTransport transport = new InternalIncomingTransport(reader);
        assertTrue(transport.isOpen());
    }

    @Test
    void testIsOpen_returnsFalseAfterClose() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        transport.close();
        assertFalse(transport.isOpen());
    }

    @Test
    void testOpenReader_returnsReader() throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(TEST_DATA));
        InternalIncomingTransport transport = new InternalIncomingTransport(reader);
        assertSame(reader, transport.openReader());
    }

    @Test
    void testOpenStream_returnsInputStream() throws IOException {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        assertSame(is, transport.openStream());
    }

    @Test
    void testOpenStream_returnsNullWhenConstructedFromReader() throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(TEST_DATA));
        InternalIncomingTransport transport = new InternalIncomingTransport(reader);
        assertNull(transport.openStream());
    }

    @Test
    void testGetRedirectionUrl_returnsNull() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        assertNull(transport.getRedirectionUrl());
    }

    @Test
    void testGetUrl_returnsEmptyString() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        assertEquals("", transport.getUrl());
    }

    @Test
    void testGetHeaders_withNullHeadersAndNullHeadersReady_returnsNull() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        InternalIncomingTransport transport = new InternalIncomingTransport(is);
        assertNull(transport.getHeaders());
    }

    @Test
    void testGetHeaders_withNullHeadersReady_returnsHeadersImmediately() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        InternalIncomingTransport transport = new InternalIncomingTransport(is, headers);
        assertSame(headers, transport.getHeaders());
    }

    @Test
    void testGetHeaders_withHeadersReadyAlreadyCountedDown_returnsHeaders() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        CountDownLatch headersReady = new CountDownLatch(1);
        headersReady.countDown();
        InternalIncomingTransport transport = new InternalIncomingTransport(is, headers, headersReady);
        assertSame(headers, transport.getHeaders());
    }

    @Test
    void testGetHeaders_whenInterrupted_setsInterruptFlagAndReturnsHeaders() {
        InputStream is = new ByteArrayInputStream(TEST_DATA.getBytes());
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("key", "value");
        CountDownLatch headersReady = new CountDownLatch(1);
        InternalIncomingTransport transport = new InternalIncomingTransport(is, headers, headersReady);
        Thread.currentThread().interrupt();
        Map<String, String> result = transport.getHeaders();
        assertTrue(Thread.currentThread().isInterrupted());
        assertSame(headers, result);
    }
}
