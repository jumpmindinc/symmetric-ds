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
package org.jumpmind.symmetric.io.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.Date;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.util.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchTest {
    private static final long BATCH_ID = 42L;
    private static final String CHANNEL_ID = "reload";
    private static final String SOURCE_NODE_ID = "source-1";
    private static final String TARGET_NODE_ID = "target-1";
    private Batch batch;

    @BeforeEach
    void setUp() {
        batch = newBatch(BatchType.EXTRACT, CHANNEL_ID, false);
    }

    @Test
    void testConstructor() {
        assertEquals(BATCH_ID, batch.getBatchId());
        assertEquals(CHANNEL_ID, batch.getChannelId());
        assertEquals(SOURCE_NODE_ID, batch.getSourceNodeId());
        assertEquals(TARGET_NODE_ID, batch.getTargetNodeId());
        assertEquals(BatchType.EXTRACT, batch.getBatchType());
        assertEquals(BinaryEncoding.NONE, batch.getBinaryEncoding());
        assertFalse(batch.isCommon());
        assertNotNull(batch.getStartTime());
    }

    @Test
    void testConstructor_withNullChannelId() {
        assertEquals(Batch.DEFAULT_CHANNEL_ID, newBatch(BatchType.EXTRACT, null, false).getChannelId());
    }

    @Test
    void testConstructor_withNoArguments() {
        Batch empty = new Batch();
        assertEquals(Batch.UNKNOWN_BATCH_ID, empty.getBatchId());
        assertEquals(Batch.DEFAULT_CHANNEL_ID, empty.getChannelId());
        assertNotNull(empty.getStartTime());
    }

    @Test
    void testSetBatchId() {
        batch.setBatchId(7L);
        assertEquals(7L, batch.getBatchId());
    }

    @Test
    void testIncrementLineCount() {
        assertEquals(1L, batch.incrementLineCount());
        assertEquals(2L, batch.incrementLineCount());
        assertEquals(2L, batch.getLineCount());
    }

    @Test
    void testSetLineCount() {
        batch.setLineCount(9L);
        assertEquals(9L, batch.getLineCount());
    }

    @Test
    void testIncrementDataReadMillis() {
        batch.incrementDataReadMillis(100L);
        batch.incrementDataReadMillis(50L);
        assertEquals(150L, batch.getDataReadMillis());
    }

    @Test
    void testIncrementDataWriteMillis() {
        batch.incrementDataWriteMillis(100L);
        batch.incrementDataWriteMillis(50L);
        assertEquals(150L, batch.getDataWriteMillis());
    }

    @Test
    void testEndTimer_whenStarted() {
        batch.startTimer("extract");
        assertTrue(batch.endTimer("extract") >= 0L);
    }

    @Test
    void testEndTimer_whenNeverStarted() {
        assertEquals(0L, batch.endTimer("never-started"));
    }

    @Test
    void testEndTimer_whenCalledTwice() {
        batch.startTimer("extract");
        batch.endTimer("extract");
        assertEquals(0L, batch.endTimer("extract"));
    }

    @Test
    void testSetStartTime() {
        Date startTime = new Date(0L);
        batch.setStartTime(startTime);
        assertEquals(startTime, batch.getStartTime());
    }

    @Test
    void testIsInitialLoad() {
        assertFalse(batch.isInitialLoad());
    }

    @Test
    void testSetIgnored() {
        assertFalse(batch.isIgnored());
        batch.setIgnored(true);
        assertTrue(batch.isIgnored());
    }

    @Test
    void testSetCommon() {
        batch.setCommon(true);
        assertTrue(batch.isCommon());
    }

    @Test
    void testSetComplete() {
        assertFalse(batch.isComplete());
        batch.setComplete(true);
        assertTrue(batch.isComplete());
    }

    @Test
    void testSetInvalidRetry() {
        assertFalse(batch.isInvalidRetry());
        batch.setInvalidRetry(true);
        assertTrue(batch.isInvalidRetry());
    }

    @Test
    void testSetBulkLoaderFlag() {
        assertFalse(batch.isBulkLoaderFlag());
        batch.setBulkLoaderFlag(true);
        assertTrue(batch.isBulkLoaderFlag());
    }

    @Test
    void testSetSourceNodeId() {
        batch.setSourceNodeId("source-2");
        assertEquals("source-2", batch.getSourceNodeId());
    }

    @Test
    void testSetStatistics() {
        Statistics statistics = new Statistics();
        batch.setStatistics(statistics);
        assertSame(statistics, batch.getStatistics());
    }

    @Test
    void testGetNodeBatchId_forExtract() {
        assertEquals("target-1-42", batch.getNodeBatchId());
    }

    @Test
    void testGetNodeBatchId_forLoad() {
        assertEquals("source-1-42", newBatch(BatchType.LOAD, CHANNEL_ID, false).getNodeBatchId());
    }

    @Test
    void testGetStagedLocation_forExtract() {
        assertEquals(TARGET_NODE_ID, batch.getStagedLocation());
    }

    @Test
    void testGetStagedLocation_forLoad() {
        assertEquals(SOURCE_NODE_ID, newBatch(BatchType.LOAD, CHANNEL_ID, false).getStagedLocation());
    }

    @Test
    void testGetStagedLocation_whenCommon() {
        assertEquals("common/042", newBatch(BatchType.EXTRACT, CHANNEL_ID, true).getStagedLocation());
    }

    @Test
    void testGetStagedLocation_whenCommonWrapsAtOneThousand() {
        assertEquals("common/001", Batch.getStagedLocation(true, TARGET_NODE_ID, 1001L));
    }

    @Test
    void testEncodeBinary_withHex() {
        batch.setBinaryEncoding(BinaryEncoding.HEX);
        assertEquals("4142", batch.encodeBinary("AB"));
    }

    @Test
    void testEncodeBinary_withBase64() {
        batch.setBinaryEncoding(BinaryEncoding.BASE64);
        assertEquals("QUI=", batch.encodeBinary("AB"));
    }

    @Test
    void testEncodeBinary_withNone() {
        assertEquals("AB", batch.encodeBinary("AB"));
    }

    @Test
    void testEncodeBinary_withNull() {
        batch.setBinaryEncoding(BinaryEncoding.HEX);
        assertNull(batch.encodeBinary(null));
    }

    @Test
    void testDecodeBinary_withHex() {
        batch.setBinaryEncoding(BinaryEncoding.HEX);
        assertArrayEquals(bytesOf("AB"), batch.decodeBinary("4142"));
    }

    @Test
    void testDecodeBinary_withBase64() {
        batch.setBinaryEncoding(BinaryEncoding.BASE64);
        assertArrayEquals(bytesOf("AB"), batch.decodeBinary("QUI="));
    }

    @Test
    void testDecodeBinary_withNone() {
        assertArrayEquals(bytesOf("AB"), batch.decodeBinary("AB"));
    }

    @Test
    void testDecodeBinary_withNull() {
        batch.setBinaryEncoding(BinaryEncoding.HEX);
        assertNull(batch.decodeBinary(null));
    }

    @Test
    void testDecodeBinary_throwsWhenHexIsMalformed() {
        batch.setBinaryEncoding(BinaryEncoding.HEX);
        assertThrows(RuntimeException.class, () -> batch.decodeBinary("zz"));
    }

    private Batch newBatch(BatchType batchType, String channelId, boolean common) {
        return new Batch(batchType, BATCH_ID, channelId, BinaryEncoding.NONE, SOURCE_NODE_ID, TARGET_NODE_ID, common);
    }

    private byte[] bytesOf(String value) {
        return value.getBytes(Charset.defaultCharset());
    }
}
