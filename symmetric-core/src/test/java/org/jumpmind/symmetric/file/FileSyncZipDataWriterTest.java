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
package org.jumpmind.symmetric.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.INodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileSyncZipDataWriterTest {
    private static final String[] COLUMNS = { "TRIGGER_ID", "ROUTER_ID", "RELATIVE_DIR", "FILE_NAME", "LAST_EVENT_TYPE",
            "CRC32_CHECKSUM", "OLD_CRC32_CHECKSUM", "FILE_SIZE", "FILE_MODIFIED_TIME", "LAST_UPDATE_BY" };
    private FileSyncZipDataWriter writer;
    private IFileSyncService fileSyncService;
    private INodeService nodeService;
    private IConfigurationService configurationService;
    private Batch batch;
    private Table snapshotTable;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        fileSyncService = mock(IFileSyncService.class);
        nodeService = mock(INodeService.class);
        configurationService = mock(IConfigurationService.class);
        when(engine.getFileSyncService()).thenReturn(fileSyncService);
        when(engine.getNodeService()).thenReturn(nodeService);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        Channel channel = new Channel();
        channel.setReloadFlag(false);
        when(configurationService.getChannel(anyString())).thenReturn(channel);
        IStagedResource stagedResource = mock(IStagedResource.class);
        writer = new FileSyncZipDataWriter(1000000, 5, stagedResource, engine);
        batch = new Batch(BatchType.EXTRACT, 1, "filesync", BinaryEncoding.BASE64, "store-1", "corp", false);
        snapshotTable = Table.buildTable("SYM_FILE_SNAPSHOT", new String[] { "TRIGGER_ID", "ROUTER_ID", "RELATIVE_DIR", "FILE_NAME" }, COLUMNS);
        writer.open(new DataContext(batch));
        writer.start(batch);
        writer.start(snapshotTable);
    }

    @Test
    void testStart_registersStatisticsForBatch() {
        assertTrue(writer.getStatistics().containsKey(batch));
    }

    @Test
    void testStartTable_returnsTrue() {
        assertTrue(writer.start(snapshotTable));
    }

    @Test
    void testWrite_withInsertRecordsSnapshot() {
        writer.write(insert("trig", "rtr", "subdir", "item.txt", "C", "123", "", "42", "1700000000000", "shiv"));
        assertEquals(1, writer.snapshotEvents.size());
        FileSnapshot snapshot = writer.snapshotEvents.get(0);
        assertEquals("trig", snapshot.getTriggerId());
        assertEquals("rtr", snapshot.getRouterId());
        assertEquals("subdir", snapshot.getRelativeDir());
        assertEquals("item.txt", snapshot.getFileName());
        assertEquals(LastEventType.CREATE, snapshot.getLastEventType());
        assertEquals(123L, snapshot.getCrc32Checksum());
        assertEquals(42L, snapshot.getFileSize());
        assertEquals(1700000000000L, snapshot.getFileModifiedTime());
        assertEquals("shiv", snapshot.getLastUpdateBy());
    }

    @Test
    void testWrite_withInsertIncrementsInsertCount() {
        writer.write(insert("trig", "rtr", ".", "item.txt", "C", "123", "", "42", "1700000000000", "shiv"));
        assertEquals(1, writer.getStatistics().get(batch).get(DataWriterStatisticConstants.INSERTCOUNT));
    }

    @Test
    void testWrite_withUpdateIncrementsUpdateCount() {
        CsvData data = new CsvData(DataEventType.UPDATE,
                new String[] { "trig", "rtr", ".", "item.txt", "M", "123", "", "42", "1700000000000", "shiv" });
        writer.write(data);
        assertEquals(1, writer.getStatistics().get(batch).get(DataWriterStatisticConstants.UPDATECOUNT));
    }

    @Test
    void testWrite_withMissingChecksumDefaultsToZero() {
        writer.write(insert("trig", "rtr", ".", "item.txt", "C", null, "", "42", "1700000000000", "shiv"));
        assertEquals(0L, writer.snapshotEvents.get(0).getCrc32Checksum());
    }

    @Test
    void testWrite_withNonNumericChecksumLeavesDefault() {
        writer.write(insert("trig", "rtr", ".", "item.txt", "C", "not-a-number", "", "42", "1700000000000", "shiv"));
        assertEquals(0L, writer.snapshotEvents.get(0).getCrc32Checksum());
    }

    @Test
    void testWrite_withNonNumericFileSizeLeavesDefault() {
        writer.write(insert("trig", "rtr", ".", "item.txt", "C", "123", "", "huge", "1700000000000", "shiv"));
        assertEquals(0L, writer.snapshotEvents.get(0).getFileSize());
    }

    @Test
    void testWrite_withNonNumericModifiedTimeLeavesDefault() {
        writer.write(insert("trig", "rtr", ".", "item.txt", "C", "123", "", "42", "yesterday", "shiv"));
        assertEquals(0L, writer.snapshotEvents.get(0).getFileModifiedTime());
    }

    @Test
    void testWrite_withDeleteEventTypeIsIgnored() {
        CsvData data = new CsvData(DataEventType.DELETE, new String[] { "trig", "rtr", ".", "item.txt" }, null);
        writer.write(data);
        assertTrue(writer.snapshotEvents.isEmpty());
    }

    @Test
    void testWrite_withReloadPullsDirectorySnapshots() {
        Node targetNode = new Node("corp", "corp-group");
        when(nodeService.findNode("corp", true)).thenReturn(targetNode);
        when(fileSyncService.getFileTriggerRoutersForCurrentNode(anyBoolean())).thenReturn(new ArrayList<>());
        writer.write(new CsvData(DataEventType.RELOAD));
        assertTrue(writer.snapshotEvents.isEmpty());
    }

    @Test
    void testWrite_recordsMultipleSnapshotsInOrder() {
        writer.write(insert("trig", "rtr", ".", "first.txt", "C", "1", "", "1", "1", "shiv"));
        writer.write(insert("trig", "rtr", ".", "second.txt", "C", "2", "", "2", "2", "shiv"));
        List<String> fileNames = new ArrayList<>();
        writer.snapshotEvents.forEach(snapshot -> fileNames.add(snapshot.getFileName()));
        assertEquals(Arrays.asList("first.txt", "second.txt"), fileNames);
    }

    @Test
    void testStart_resetsSnapshotEventsBetweenBatches() {
        writer.write(insert("trig", "rtr", ".", "item.txt", "C", "123", "", "42", "1", "shiv"));
        writer.start(new Batch(BatchType.EXTRACT, 2, "filesync", BinaryEncoding.BASE64, "store-1", "corp", false));
        assertTrue(writer.snapshotEvents.isEmpty());
    }

    private CsvData insert(String... values) {
        return new CsvData(DataEventType.INSERT, values);
    }
}
