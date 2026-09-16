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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.writer.IProtocolDataWriterListener;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SimpleStagingDataWriterTest {
    private static final String SOURCE_NODE_ID = "store-1";
    private static final String TARGET_NODE_ID = "corp";
    private static final String CATEGORY = "incoming";
    @TempDir
    private File tempDir;
    private StagingManager stagingManager;
    private ISymmetricEngine engine;
    private ProcessInfo processInfo;
    private DataContext context;

    @BeforeEach
    void setUp() {
        stagingManager = new StagingManager(tempDir.getAbsolutePath(), false);
        engine = mock(ISymmetricEngine.class);
        when(engine.getStagingManager()).thenReturn(stagingManager);
        processInfo = mock(ProcessInfo.class);
        context = new DataContext();
    }

    @Test
    void testProcess_stagesBatchAsDoneResource() throws IOException {
        process(batchProtocol(1));
        IStagedResource resource = stagingManager.find(CATEGORY, SOURCE_NODE_ID, 1);
        assertNotNull(resource);
        assertEquals(State.DONE, resource.getState());
    }

    @Test
    void testProcess_writesBatchHeaderAndRows() throws IOException {
        process(batchProtocol(1));
        String staged = readStaged(1);
        assertTrue(staged.contains("nodeid," + SOURCE_NODE_ID));
        assertTrue(staged.contains("binary,BASE64"));
        assertTrue(staged.contains("channel,default"));
        assertTrue(staged.contains("batch,1"));
        assertTrue(staged.contains("insert,1,widget"));
        assertTrue(staged.contains("commit,1"));
    }

    @Test
    void testProcess_notifiesListenerOfBatchStart() throws IOException {
        IProtocolDataWriterListener listener = mock(IProtocolDataWriterListener.class);
        process(batchProtocol(1), listener);
        ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(listener).start(eq(context), batchCaptor.capture());
        assertEquals(1L, batchCaptor.getValue().getBatchId());
    }

    @Test
    void testProcess_notifiesListenerOfBatchEnd() throws IOException {
        IProtocolDataWriterListener listener = mock(IProtocolDataWriterListener.class);
        process(batchProtocol(1), listener);
        verify(listener).end(eq(context), any(Batch.class), any(IStagedResource.class));
    }

    @Test
    void testProcess_stagesEachBatchSeparately() throws IOException {
        process(batchProtocol(1) + batchProtocol(2));
        assertNotNull(stagingManager.find(CATEGORY, SOURCE_NODE_ID, 1));
        assertNotNull(stagingManager.find(CATEGORY, SOURCE_NODE_ID, 2));
    }

    @Test
    void testProcess_rejectsLinesOutsideOfABatch() {
        String protocol = "insert,1,orphan\n" + batchProtocol(1);
        assertThrows(ProtocolException.class, () -> process(protocol));
    }

    @Test
    void testProcess_withEmptyInputStagesNothing() throws IOException {
        process("");
        assertTrue(stagingManager.getResourceReferences().isEmpty());
    }

    @Test
    void testGetException_isNullAfterCleanRun() throws IOException {
        SimpleStagingDataWriter writer = newWriter(batchProtocol(1));
        writer.process();
        assertNull(writer.getException());
    }

    @Test
    void testProcess_recordsBatchOnProcessInfo() throws IOException {
        process(batchProtocol(1));
        verify(processInfo).setCurrentBatchId(1L);
        verify(processInfo).incrementBatchCount();
    }

    private String batchProtocol(long batchId) {
        return "nodeid," + SOURCE_NODE_ID + "\n"
                + "binary,BASE64\n"
                + "channel,default\n"
                + "batch," + batchId + "\n"
                + "table,item\n"
                + "keys,id\n"
                + "columns,id,name\n"
                + "insert,1,widget\n"
                + "commit," + batchId + "\n";
    }

    private void process(String protocol, IProtocolDataWriterListener... listeners) throws IOException {
        newWriter(protocol, listeners).process();
    }

    private SimpleStagingDataWriter newWriter(String protocol, IProtocolDataWriterListener... listeners) {
        BufferedReader reader = new BufferedReader(new StringReader(protocol));
        return new SimpleStagingDataWriter(processInfo, reader, engine, CATEGORY, 0, BatchType.LOAD,
                SOURCE_NODE_ID, TARGET_NODE_ID, context, listeners);
    }

    private String readStaged(long batchId) throws IOException {
        IStagedResource resource = stagingManager.find(CATEGORY, SOURCE_NODE_ID, batchId);
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = resource.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
        }
        return text.toString();
    }
}
