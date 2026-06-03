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
package org.jumpmind.symmetric.io.data.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataProcessor;
import org.jumpmind.symmetric.io.data.reader.ProtocolDataReader;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.io.stage.LegacyStagingManagerAdapter;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StorageKind;
import org.jumpmind.symmetric.staging.fs.FileSystemStagingManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StagingDataWriterTest {
    static final File DIR = new File("target/tmp");
    static final File SCRATCH_DIR = new File("target/tmp-scratch");
    List<String> batchesWritten = new ArrayList<String>();

    @BeforeAll
    public static void setup() throws Exception {
        FileUtils.deleteDirectory(DIR);
        FileUtils.deleteDirectory(SCRATCH_DIR);
        DIR.mkdirs();
        SCRATCH_DIR.mkdirs();
    }

    @BeforeEach
    public void clearBatches() {
        batchesWritten.clear();
    }

    @Test
    public void testReadThenWriteToFile() throws Exception {
        readThenWrite(0);
    }

    @Test
    public void testReadThenWriteToMemory() throws Exception {
        readThenWrite(10000000);
    }

    public void readThenWrite(long threshold) throws Exception {
        InputStreamReader is = new InputStreamReader(getClass().getResourceAsStream("FileCsvDataWriterTest.1.csv"));
        String origCsv = IOUtils.toString(is);
        is.close();
        IStagingManager stagingManager = newStagingManager();
        ProtocolDataReader reader = new ProtocolDataReader(BatchType.LOAD, "test", origCsv);
        StagingDataWriter writer = new StagingDataWriter(threshold, false, "aaa", "test", stagingManager, false, false, new BatchListener());
        DataProcessor processor = new DataProcessor(reader, writer, "test");
        processor.process(new DataContext());
        assertEquals(1, batchesWritten.size());
        assertEquals(convertEol(origCsv), convertEol(batchesWritten.get(0)));
        IStagedResource resource = stagingManager.find("test", "aaa", 1);
        assertNotNull(resource);
        if (threshold > origCsv.length()) {
            assertFalse(resource.getFile().exists());
        } else {
            assertTrue(resource.getFile().exists());
        }
        resource.delete();
        assertFalse(resource.getFile().exists());
    }

    private static IStagingManager newStagingManager() {
        StagingConfig config = StagingConfig.builder()
                .withStorageKind(StorageKind.FILESYSTEM)
                .withStagingDir(DIR.getAbsolutePath())
                .withScratchDir(SCRATCH_DIR.getAbsolutePath())
                .build();
        return new LegacyStagingManagerAdapter(new FileSystemStagingManager(config));
    }

    private String convertEol(String str) {
        return str.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
    }

    class BatchListener implements IProtocolDataWriterListener {
        public void start(DataContext ctx, Batch batch) {
        }

        public void end(DataContext ctx, Batch batch, IStagedResource resource) {
            try {
                BufferedReader reader = resource.getReader();
                batchesWritten.add(IOUtils.toString(reader));
                resource.close();
            } catch (IOException e) {
                throw new IoException(e);
            }
        }
    }
}
