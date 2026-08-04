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
package org.jumpmind.symmetric.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.junit.jupiter.api.Test;

class FileSyncBatchEnvelopeTest {
    @Test
    void testWriteThenReadHeaderRoundTrips() throws IOException {
        StagedResourceETag etag = new StagedResourceETag(123456789L, 42L);
        byte[] body = "some zip bytes, with a comma".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileSyncBatchEnvelope.writeHeader(out, 99L, body.length, etag);
        out.write(body);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        FileSyncBatchEnvelope header = FileSyncBatchEnvelope.readHeader(in);
        assertEquals(99L, header.getBatchId());
        assertEquals(body.length, header.getLength());
        assertEquals(etag, header.getEtag());
        byte[] readBody = in.readAllBytes();
        assertArrayEquals(body, readBody);
    }

    @Test
    void testReadHeaderAtCleanEofReturnsNull() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        assertNull(FileSyncBatchEnvelope.readHeader(in));
    }

    @Test
    void testReadHeaderParsesEtagJsonContainingCommas() throws IOException {
        StagedResourceETag etag = new StagedResourceETag(1L, 2L);
        String etagJson = etag.toJson();
        assertEquals(true, etagJson.contains(","));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileSyncBatchEnvelope.writeHeader(out, 1L, 0L, etag);
        FileSyncBatchEnvelope header = FileSyncBatchEnvelope.readHeader(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(etag, header.getEtag());
    }

    @Test
    void testReadHeaderWithMalformedLineThrowsIOException() {
        byte[] malformed = "notavalidheader\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(malformed);
        assertThrows(IOException.class, () -> FileSyncBatchEnvelope.readHeader(in));
    }

    @Test
    void testMultipleHeadersInSequenceReadInOrder() throws IOException {
        StagedResourceETag etag1 = new StagedResourceETag(1L, 3L);
        StagedResourceETag etag2 = new StagedResourceETag(2L, 4L);
        byte[] body1 = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "wxyz".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileSyncBatchEnvelope.writeHeader(out, 1L, body1.length, etag1);
        out.write(body1);
        FileSyncBatchEnvelope.writeHeader(out, 2L, body2.length, etag2);
        out.write(body2);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        FileSyncBatchEnvelope first = FileSyncBatchEnvelope.readHeader(in);
        assertEquals(1L, first.getBatchId());
        byte[] readBody1 = in.readNBytes(body1.length);
        assertArrayEquals(body1, readBody1);
        FileSyncBatchEnvelope second = FileSyncBatchEnvelope.readHeader(in);
        assertEquals(2L, second.getBatchId());
        byte[] readBody2 = in.readNBytes(body2.length);
        assertArrayEquals(body2, readBody2);
        assertNull(FileSyncBatchEnvelope.readHeader(in));
    }
}
