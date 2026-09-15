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
package org.jumpmind.symmetric.wrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapperLogHandlerTest {
    private static final long MAX_BYTES = 40L;
    @TempDir
    private Path tempDir;
    private WrapperLogHandler handler;
    private Path logFile;

    @AfterEach
    void tearDown() {
        if (handler != null) {
            handler.close();
        }
    }

    @Test
    void testConstructor_createsLogFile() throws Exception {
        newHandler(MAX_BYTES, 3);
        assertTrue(Files.exists(logFile));
        assertEquals(0, Files.size(logFile));
    }

    @Test
    void testConstructor_withUnwritablePath() {
        assertThrows(IOException.class, () -> new WrapperLogHandler(tempDir.resolve("missing").resolve("wrapper.log").toString(), MAX_BYTES, 3));
    }

    @Test
    void testConstructor_appendsToExistingFileBelowMaxSize() throws Exception {
        logFile = tempDir.resolve("wrapper.log");
        Files.write(logFile, "old\n".getBytes(StandardCharsets.UTF_8));
        newHandler(MAX_BYTES, 3);
        publish("new");
        handler.flush();
        assertEquals("old\nnew", readLog());
    }

    @Test
    void testConstructor_rotatesExistingFileAtMaxSize() throws Exception {
        logFile = tempDir.resolve("wrapper.log");
        Files.write(logFile, "0123456789".getBytes(StandardCharsets.UTF_8));
        newHandler(10L, 3);
        assertEquals("0123456789", new String(Files.readAllBytes(tempDir.resolve("wrapper.log.1")), StandardCharsets.UTF_8));
        assertEquals(0, Files.size(logFile));
    }

    @Test
    void testPublish_writesFormattedMessage() throws Exception {
        newHandler(MAX_BYTES, 3);
        publish("hello");
        handler.flush();
        assertEquals("hello", readLog());
    }

    @Test
    void testPublish_rotatesWhenMaxSizeReached() throws Exception {
        newHandler(10L, 3);
        publish("0123456789");
        publish("after");
        handler.flush();
        assertEquals("0123456789", new String(Files.readAllBytes(tempDir.resolve("wrapper.log.1")), StandardCharsets.UTF_8));
        assertEquals("after", readLog());
    }

    @Test
    void testPublish_shiftsOlderLogsOnRotation() throws Exception {
        Files.write(tempDir.resolve("wrapper.log.1"), "first".getBytes(StandardCharsets.UTF_8));
        newHandler(5L, 3);
        publish("second");
        handler.flush();
        assertEquals("second", new String(Files.readAllBytes(tempDir.resolve("wrapper.log.1")), StandardCharsets.UTF_8));
        assertEquals("first", new String(Files.readAllBytes(tempDir.resolve("wrapper.log.2")), StandardCharsets.UTF_8));
    }

    @Test
    void testPublish_deletesLogBeyondMaxCount() throws Exception {
        Files.write(tempDir.resolve("wrapper.log.2"), "oldest".getBytes(StandardCharsets.UTF_8));
        newHandler(5L, 2);
        publish("newest");
        handler.flush();
        assertFalse(Files.exists(tempDir.resolve("wrapper.log.2")));
        assertEquals("newest", new String(Files.readAllBytes(tempDir.resolve("wrapper.log.1")), StandardCharsets.UTF_8));
    }

    @Test
    void testPublish_whenFormatterThrows_reportsFormatFailure() throws Exception {
        newHandler(MAX_BYTES, 3);
        ErrorManager errorManagerMock = mock(ErrorManager.class);
        handler.setErrorManager(errorManagerMock);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                throw new IllegalStateException("bad pattern");
            }
        });
        publish("boom");
        verify(errorManagerMock).error(isNull(), any(IllegalStateException.class), eq(ErrorManager.FORMAT_FAILURE));
    }

    @Test
    void testPublish_afterClose_isIgnored() throws Exception {
        newHandler(MAX_BYTES, 3);
        handler.close();
        publish("ignored");
        assertEquals("", readLog());
    }

    @Test
    void testFlush_afterClose_isIgnored() throws Exception {
        newHandler(MAX_BYTES, 3);
        handler.close();
        handler.flush();
        assertEquals("", readLog());
    }

    @Test
    void testClose_isIdempotent() throws Exception {
        newHandler(MAX_BYTES, 3);
        handler.close();
        handler.close();
        assertTrue(Files.exists(logFile));
    }

    @Test
    void testClose_flushesBufferedOutput() throws Exception {
        newHandler(MAX_BYTES, 3);
        publish("buffered");
        handler.close();
        assertEquals("buffered", readLog());
    }

    private void newHandler(long maxByteCount, int maxLogCount) throws IOException {
        logFile = tempDir.resolve("wrapper.log");
        handler = new WrapperLogHandler(logFile.toString(), maxByteCount, maxLogCount);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return logRecord.getMessage();
            }
        });
    }

    private void publish(String message) {
        handler.publish(new LogRecord(Level.INFO, message));
    }

    private String readLog() throws IOException {
        return new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
    }
}
