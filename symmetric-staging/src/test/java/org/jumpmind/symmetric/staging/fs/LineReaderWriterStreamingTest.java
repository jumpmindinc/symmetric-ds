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
package org.jumpmind.symmetric.staging.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jumpmind.symmetric.staging.api.ILineReader;
import org.jumpmind.symmetric.staging.api.ILineWriter;
import org.jumpmind.symmetric.staging.api.IStagedResource;
import org.jumpmind.symmetric.staging.api.ResourceState;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StagingOptions;
import org.jumpmind.symmetric.staging.api.StorageKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineReaderWriterStreamingTest {
    private static final int LARGE_LINE_COUNT = 100_000;
    private static final int LINE_LENGTH = 256;

    @Test
    void largeFile_roundTripsLineByLine(@TempDir Path stagingDir, @TempDir Path scratchDir) throws Exception {
        Files.createDirectories(stagingDir);
        Files.createDirectories(scratchDir);
        StagingConfig config = StagingConfig.builder()
                .withStorageKind(StorageKind.FILESYSTEM)
                .withStagingDir(stagingDir.toString())
                .withScratchDir(scratchDir.toString())
                .withMemoryThresholdBytes(0L)
                .build();
        FileSystemStagingManager manager = new FileSystemStagingManager(config);
        StagingOptions options = StagingOptions.builder()
                .withMemoryThresholdBytes(0L)
                .build();
        IStagedResource resource = manager.create(options, "large", "stream-test");
        long bytesExpected = (long) LARGE_LINE_COUNT * (LINE_LENGTH + System.lineSeparator().length());
        try (ILineWriter writer = resource.openLineWriter(StandardCharsets.UTF_8, 0L)) {
            for (int i = 0; i < LARGE_LINE_COUNT; i++) {
                writer.writeLine(buildLine(i));
            }
        }
        resource.setState(ResourceState.DONE);
        assertTrue(resource.getSize() >= bytesExpected / 2,
                "expected at least " + bytesExpected / 2 + " bytes on disk, got " + resource.getSize());
        int seen = 0;
        try (ILineReader reader = resource.openLineReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                assertEquals(buildLine(seen), line, "line " + seen + " mismatch");
                seen++;
            }
        }
        assertEquals(LARGE_LINE_COUNT, seen);
        resource.delete();
    }

    private static String buildLine(int n) {
        StringBuilder builder = new StringBuilder(LINE_LENGTH);
        builder.append(String.format("%010d|", n));
        while (builder.length() < LINE_LENGTH) {
            builder.append('x');
        }
        builder.setLength(LINE_LENGTH);
        return builder.toString();
    }
}
