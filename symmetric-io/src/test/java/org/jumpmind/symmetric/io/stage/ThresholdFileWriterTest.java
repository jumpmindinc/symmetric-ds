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
package org.jumpmind.symmetric.io.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThresholdFileWriterTest {
    @TempDir
    private File tempDir;
    private File target;

    @BeforeEach
    void setUp() {
        target = new File(tempDir, "nested/staged.txt");
    }

    @Test
    void testWrite_staysInMemoryBelowThreshold() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(100, buffer, target)) {
            writer.write("hello");
        }
        assertEquals("hello", buffer.toString());
        assertFalse(target.exists());
    }

    @Test
    void testWrite_spillsToFileAtThreshold() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(4, buffer, target)) {
            writer.write("hello");
        }
        assertTrue(target.exists());
        assertEquals("hello", readTarget());
    }

    @Test
    void testWrite_flushesBufferedContentWhenSpilling() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(6, buffer, target)) {
            writer.write("abc");
            writer.write("defgh");
        }
        assertEquals("abcdefgh", readTarget());
    }

    @Test
    void testWrite_keepsWritingToFileOnceSpilled() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(2, buffer, target)) {
            writer.write("abc");
            writer.write("def");
        }
        assertEquals("abcdef", readTarget());
    }

    @Test
    void testWrite_withNullBufferGoesStraightToFile() throws IOException {
        try (ThresholdFileWriter writer = new ThresholdFileWriter(1000, null, target)) {
            writer.write("hello");
        }
        assertTrue(target.exists());
        assertEquals("hello", readTarget());
    }

    @Test
    void testGetReader_readsFromMemoryWhenNotSpilled() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(100, buffer, target)) {
            writer.write("hello");
            try (BufferedReader reader = writer.getReader()) {
                assertEquals("hello", reader.readLine());
            }
        }
    }

    @Test
    void testGetReader_readsFromFileWhenSpilled() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(2, buffer, target)) {
            writer.write("hello");
            try (BufferedReader reader = writer.getReader()) {
                assertEquals("hello", reader.readLine());
            }
        }
    }

    @Test
    void testGetFile() throws IOException {
        try (ThresholdFileWriter writer = new ThresholdFileWriter(100, new StringBuilder(), target)) {
            assertEquals(target, writer.getFile());
        }
    }

    @Test
    void testSetFile() throws IOException {
        File other = new File(tempDir, "other.txt");
        try (ThresholdFileWriter writer = new ThresholdFileWriter(100, new StringBuilder(), target)) {
            writer.setFile(other);
            assertEquals(other, writer.getFile());
        }
    }

    @Test
    void testDelete_removesSpilledFileAndClearsReference() throws IOException {
        StringBuilder buffer = new StringBuilder();
        ThresholdFileWriter writer = new ThresholdFileWriter(2, buffer, target);
        writer.write("hello");
        writer.close();
        writer.delete();
        assertFalse(target.exists());
        assertNull(writer.getFile());
        assertEquals(0, buffer.length());
    }

    @Test
    void testDelete_clearsBufferWhenNeverSpilled() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(100, buffer, target)) {
            writer.write("hello");
            writer.delete();
        }
        assertEquals(0, buffer.length());
    }

    @Test
    void testFlush_withoutFileWriterDoesNothing() throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (ThresholdFileWriter writer = new ThresholdFileWriter(100, buffer, target)) {
            writer.write("hello");
            writer.flush();
            assertEquals("hello", buffer.toString());
        }
    }

    @Test
    void testClose_isIdempotent() throws IOException {
        StringBuilder buffer = new StringBuilder();
        ThresholdFileWriter writer = new ThresholdFileWriter(2, buffer, target);
        writer.write("hello");
        writer.close();
        writer.close();
        assertEquals("hello", readTarget());
    }

    private String readTarget() throws IOException {
        return new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
    }
}
