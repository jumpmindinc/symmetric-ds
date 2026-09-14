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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompressionResponseStreamTest {
    private HttpServletResponse response;
    private ByteArrayOutputStream underlying;

    @BeforeEach
    void setUp() throws IOException {
        underlying = new ByteArrayOutputStream();
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                underlying.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }
        });
    }

    @Test
    void testConstructor_addsGzipContentEncodingHeader() throws IOException {
        newStream();
        verify(response).addHeader("Content-Encoding", "gzip");
    }

    @Test
    void testWrite_singleByteAndClose_producesGzipOutput() throws IOException {
        CompressionResponseStream stream = newStream();
        stream.write('a');
        stream.close();
        byte[] result = underlying.toByteArray();
        assertEquals((byte) 0x1f, result[0]);
        assertEquals((byte) 0x8b, result[1]);
    }

    @Test
    void testWriteSingleByte_delegatesToGzipStreamAsByteArray() throws IOException {
        CompressionResponseStream stream = newStream();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        stream.write('a');
        verify(mockGzip).write(new byte[] { 'a' }, 0, 1);
    }

    @Test
    void testWriteByteArray_delegatesFullRangeToGzipStream() throws IOException {
        CompressionResponseStream stream = newStream();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        byte[] data = "hello".getBytes();
        stream.write(data);
        verify(mockGzip).write(data, 0, data.length);
    }

    @Test
    void testWriteByteArrayWithOffsetAndLength_delegatesToGzipStream() throws IOException {
        CompressionResponseStream stream = newStream();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        byte[] data = "hello world".getBytes();
        stream.write(data, 6, 5);
        verify(mockGzip).write(data, 6, 5);
    }

    @Test
    void testWrite_zeroLength_doesNotTouchGzipStream() throws IOException {
        CompressionResponseStream stream = newStream();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        stream.write(new byte[0], 0, 0);
        verifyNoInteractions(mockGzip);
    }

    @Test
    void testWrite_afterClosed_doesNothing() throws IOException {
        CompressionResponseStream stream = newStream();
        stream.close();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        stream.write('a');
        verifyNoInteractions(mockGzip);
    }

    @Test
    void testClose_setsClosedState() throws IOException {
        CompressionResponseStream stream = newStream();
        assertFalse(stream.closed());
        stream.close();
        assertTrue(stream.closed());
    }

    @Test
    void testClose_isIdempotent() throws IOException {
        CompressionResponseStream stream = newStream();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        stream.close();
        stream.close();
        verify(mockGzip).close();
    }

    @Test
    void testFlush_delegatesToGzipStream() throws IOException {
        CompressionResponseStream stream = newStream();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        stream.flush();
        verify(mockGzip).flush();
    }

    @Test
    void testFlush_whenClosed_doesNothing() throws IOException {
        CompressionResponseStream stream = newStream();
        stream.close();
        OutputStream mockGzip = mock(OutputStream.class);
        stream.gzipstream = mockGzip;
        stream.flush();
        verify(mockGzip, never()).flush();
    }

    @Test
    void testIsReady_returnsTrue() throws IOException {
        assertTrue(newStream().isReady());
    }

    @Test
    void testSetWriteListener_doesNothing() throws IOException {
        CompressionResponseStream stream = newStream();
        assertDoesNotThrow(() -> stream.setWriteListener(mock(WriteListener.class)));
    }

    private CompressionResponseStream newStream() throws IOException {
        return new CompressionResponseStream(response, Deflater.DEFAULT_COMPRESSION, Deflater.DEFAULT_STRATEGY);
    }
}
