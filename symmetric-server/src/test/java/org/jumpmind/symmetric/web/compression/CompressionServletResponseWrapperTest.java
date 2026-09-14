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
package org.jumpmind.symmetric.web.compression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.Deflater;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompressionServletResponseWrapperTest {
    private HttpServletResponse origResponse;

    @BeforeEach
    void setUp() throws IOException {
        origResponse = mock(HttpServletResponse.class);
        when(origResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                // no-op: these tests only verify wrapper behavior, not the bytes written
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // no-op: tests write synchronously and never need async write notifications
            }
        });
    }

    @Test
    void testSetContentType_delegatesToOrigResponse() {
        CompressionServletResponseWrapper wrapper = newWrapper();
        wrapper.setContentType("text/plain");
        verify(origResponse).setContentType("text/plain");
    }

    @Test
    void testCreateOutputStream_returnsCompressionResponseStream() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        ServletOutputStream result = wrapper.createOutputStream();
        assertTrue(result instanceof CompressionResponseStream);
        verify(origResponse).addHeader("Content-Encoding", "gzip");
    }

    @Test
    void testGetOutputStream_returnsCachedStreamOnSecondCall() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        ServletOutputStream first = wrapper.getOutputStream();
        ServletOutputStream second = wrapper.getOutputStream();
        assertSame(first, second);
    }

    @Test
    void testGetOutputStream_throwsWhenWriterAlreadyCalled() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        when(origResponse.getCharacterEncoding()).thenReturn("UTF-8");
        wrapper.getWriter();
        assertThrows(IllegalStateException.class, wrapper::getOutputStream);
    }

    @Test
    void testGetWriter_throwsWhenStreamAlreadyCalled() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        wrapper.getOutputStream();
        assertThrows(IllegalStateException.class, wrapper::getWriter);
    }

    @Test
    void testGetWriter_returnsCachedWriterOnSecondCall() throws IOException {
        when(origResponse.getCharacterEncoding()).thenReturn("UTF-8");
        CompressionServletResponseWrapper wrapper = newWrapper();
        PrintWriter first = wrapper.getWriter();
        PrintWriter second = wrapper.getWriter();
        assertSame(first, second);
    }

    @Test
    void testGetWriter_usesCharacterEncodingWhenPresent() throws IOException {
        when(origResponse.getCharacterEncoding()).thenReturn("UTF-8");
        CompressionServletResponseWrapper wrapper = newWrapper();
        PrintWriter writer = wrapper.getWriter();
        assertNotNull(writer);
        verify(origResponse).getCharacterEncoding();
    }

    @Test
    void testGetWriter_fallsBackWhenCharacterEncodingMissing() throws IOException {
        when(origResponse.getCharacterEncoding()).thenReturn(null);
        CompressionServletResponseWrapper wrapper = newWrapper();
        assertNotNull(wrapper.getWriter());
    }

    @Test
    void testFlushBuffer_flushesUnderlyingStream() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        wrapper.getOutputStream();
        assertDoesNotThrow(wrapper::flushBuffer);
    }

    @Test
    void testFinishResponse_closesStreamWhenWriterNull() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        CompressionResponseStream stream = (CompressionResponseStream) wrapper.getOutputStream();
        wrapper.finishResponse();
        assertTrue(stream.closed());
    }

    @Test
    void testFinishResponse_closesWriterWhenPresent() {
        CompressionServletResponseWrapper wrapper = newWrapper();
        PrintWriter mockWriter = mock(PrintWriter.class);
        wrapper.writer = mockWriter;
        wrapper.finishResponse();
        verify(mockWriter).close();
    }

    @Test
    void testFinishResponse_swallowsIOExceptionFromStreamClose() throws IOException {
        CompressionServletResponseWrapper wrapper = newWrapper();
        ServletOutputStream mockStream = mock(ServletOutputStream.class);
        doThrow(new IOException("boom")).when(mockStream).close();
        wrapper.stream = mockStream;
        assertDoesNotThrow(wrapper::finishResponse);
    }

    @Test
    void testSetContentLength_doesNothing() {
        CompressionServletResponseWrapper wrapper = newWrapper();
        wrapper.setContentLength(100);
        verify(origResponse, never()).setContentLength(anyInt());
    }

    private CompressionServletResponseWrapper newWrapper() {
        return new CompressionServletResponseWrapper(origResponse, Deflater.DEFAULT_COMPRESSION, Deflater.DEFAULT_STRATEGY);
    }
}
