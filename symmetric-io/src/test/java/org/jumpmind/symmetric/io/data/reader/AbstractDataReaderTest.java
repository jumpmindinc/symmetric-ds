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
package org.jumpmind.symmetric.io.data.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractDataReaderTest {
    @TempDir
    private File tempDir;
    private ByteCountingReader dataReader;

    @BeforeEach
    void setUp() {
        dataReader = new ByteCountingReader();
    }

    @Test
    void testLogDebugAndCountBytes_sumsTokenLengths() {
        assertEquals(8, dataReader.logDebugAndCountBytes(new String[] { "abc", "defgh" }));
    }

    @Test
    void testLogDebugAndCountBytes_skipsNullTokens() {
        assertEquals(3, dataReader.logDebugAndCountBytes(new String[] { "abc", null }));
    }

    @Test
    void testLogDebugAndCountBytes_withNullArray() {
        assertEquals(0, dataReader.logDebugAndCountBytes(null));
    }

    @Test
    void testLogDebugAndCountBytes_withEmptyArray() {
        assertEquals(0, dataReader.logDebugAndCountBytes(new String[0]));
    }

    @Test
    void testToBatch_buildsCommonLoadBatch() {
        Batch batch = AbstractDataReader.toBatch(BinaryEncoding.BASE64);
        assertEquals(BatchType.LOAD, batch.getBatchType());
        assertEquals(Batch.UNKNOWN_BATCH_ID, batch.getBatchId());
        assertEquals("default", batch.getChannelId());
        assertEquals(BinaryEncoding.BASE64, batch.getBinaryEncoding());
        assertNull(batch.getSourceNodeId());
    }

    @Test
    void testToReader_fromFile() throws IOException {
        File file = new File(tempDir, "data.csv");
        Files.write(file.toPath(), "id,name".getBytes(StandardCharsets.UTF_8));
        try (Reader reader = AbstractDataReader.toReader(file)) {
            assertEquals('i', reader.read());
        }
    }

    @Test
    void testToReader_fromMissingFileThrowsIoException() {
        File missing = new File(tempDir, "missing.csv");
        assertThrows(IoException.class, () -> AbstractDataReader.toReader(missing));
    }

    @Test
    void testToReader_fromInputStream() throws IOException {
        try (Reader reader = AbstractDataReader.toReader(new ByteArrayInputStream("id,name".getBytes(StandardCharsets.UTF_8)))) {
            assertEquals('i', reader.read());
        }
    }

    private static class ByteCountingReader extends AbstractDataReader {
    }
}
