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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.BatchId;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchStagingManagerTest {
    @TempDir
    private File tempDir;
    private BatchStagingManager stagingManager;
    private StagingPurgeContext context;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.is(anyString())).thenReturn(false);
        when(parameterService.getLong(anyString(), anyLong())).thenReturn(0L);
        stagingManager = new BatchStagingManager(engine, tempDir.getAbsolutePath());
        context = new StagingPurgeContext();
        context.putContextValue("startTime", System.currentTimeMillis());
        context.putContextValue("minTtlInMs", 0L);
        context.putContextValue("purgeBasedOnTTL", Boolean.FALSE);
        context.putContextValue("outgoingBatches", new HashSet<Long>());
        context.putContextValue("incomingBatches", new HashSet<BatchId>());
        context.putContextValue("biggestIncomingByNode", new HashMap<String, Long>());
        context.putContextValue("recordIncomingBatchesEnabled", Boolean.TRUE);
    }

    @Test
    void testGetBiggestBatchIds_keepsHighestPerNode() {
        Set<BatchId> batches = new HashSet<BatchId>();
        batches.add(new BatchId(1L, "store-1"));
        batches.add(new BatchId(9L, "store-1"));
        batches.add(new BatchId(4L, "store-2"));
        Map<String, Long> biggest = stagingManager.getBiggestBatchIds(batches);
        assertEquals(9L, biggest.get("store-1"));
        assertEquals(4L, biggest.get("store-2"));
    }

    @Test
    void testGetBiggestBatchIds_withNoBatches() {
        assertTrue(stagingManager.getBiggestBatchIds(new HashSet<BatchId>()).isEmpty());
    }

    @Test
    void testShouldCleanOutgoingPath_whenBatchNoLongerOutstanding() {
        assertTrue(stagingManager.shouldCleanOutgoingPath(oldResource(), 60000, context,
                new String[] { "outgoing", "store-1", "0000000005" }, true, true));
    }

    @Test
    void testShouldCleanOutgoingPath_whenBatchStillOutstanding() {
        outgoingBatches().add(5L);
        assertFalse(stagingManager.shouldCleanOutgoingPath(oldResource(), 60000, context,
                new String[] { "outgoing", "store-1", "0000000005" }, true, true));
    }

    @Test
    void testShouldCleanOutgoingPath_whenTtlIsZero() {
        outgoingBatches().add(5L);
        assertTrue(stagingManager.shouldCleanOutgoingPath(oldResource(), 0, context,
                new String[] { "outgoing", "store-1", "0000000005" }, false, false));
    }

    @Test
    void testShouldCleanOutgoingPath_stripsFilesyncSuffix() {
        outgoingBatches().add(5L);
        assertFalse(stagingManager.shouldCleanOutgoingPath(oldResource(), 60000, context,
                new String[] { "outgoing", "store-1", "0000000005_filesync" }, true, true));
    }

    @Test
    void testShouldCleanOutgoingPath_withNonNumericBatchUsesAge() {
        assertTrue(stagingManager.shouldCleanOutgoingPath(oldResource(), 60000, context,
                new String[] { "outgoing", "store-1", "not-a-batch" }, true, true));
    }

    @Test
    void testShouldCleanOutgoingPath_withNonNumericBatchKeepsFreshResource() {
        assertFalse(stagingManager.shouldCleanOutgoingPath(oldResource(), 60000, context,
                new String[] { "outgoing", "store-1", "not-a-batch" }, false, true));
    }

    @Test
    void testShouldCleanBulkPath_stripsNonDigits() {
        outgoingBatches().add(5L);
        assertFalse(stagingManager.shouldCleanBulkPath(oldResource(), 60000, context,
                new String[] { "bulk_load", "store-1", "batch-5.csv" }, true, true));
    }

    @Test
    void testShouldCleanBulkPath_whenBatchNoLongerOutstanding() {
        assertTrue(stagingManager.shouldCleanBulkPath(oldResource(), 60000, context,
                new String[] { "bulk_load", "store-1", "batch-5.csv" }, true, true));
    }

    @Test
    void testShouldCleanIncomingPath_whenSupersededByNewerBatch() {
        biggestIncomingByNode().put("store-1", 9L);
        assertTrue(stagingManager.shouldCleanIncomingPath(oldResource(), 60000, context,
                new String[] { "incoming", "store-1", "0000000005" }, true, true));
    }

    @Test
    void testShouldCleanIncomingPath_whenBatchIsStillTheNewest() {
        biggestIncomingByNode().put("store-1", 5L);
        assertFalse(stagingManager.shouldCleanIncomingPath(oldResource(), 60000, context,
                new String[] { "incoming", "store-1", "0000000005" }, true, true));
    }

    @Test
    void testShouldCleanIncomingPath_whenBatchStillOutstanding() {
        biggestIncomingByNode().put("store-1", 9L);
        incomingBatches().add(new BatchId(5L, "store-1"));
        assertFalse(stagingManager.shouldCleanIncomingPath(oldResource(), 60000, context,
                new String[] { "incoming", "store-1", "0000000005" }, true, true));
    }

    @Test
    void testShouldCleanIncomingPath_fallsBackToAgeWhenRecordingDisabled() {
        context.putContextValue("recordIncomingBatchesEnabled", Boolean.FALSE);
        assertTrue(stagingManager.shouldCleanIncomingPath(oldResource(), 60000, context,
                new String[] { "incoming", "store-1", "0000000005" }, true, true));
    }

    @Test
    void testShouldCleanPath_forLogMinerPathIsNeverCleaned() {
        assertFalse(stagingManager.shouldCleanPath(resourceWithPath("lm/store-1/0000000005"), 0, context));
    }

    @Test
    void testShouldCleanPath_forUnrecognizedPath() {
        assertFalse(stagingManager.shouldCleanPath(resourceWithPath("mystery/store-1/0000000005"), 0, context));
    }

    @Test
    void testShouldCleanPath_routesOutgoingPathToOutgoingRule() {
        assertTrue(stagingManager.shouldCleanPath(resourceWithPath("outgoing/store-1/0000000005"), 0, context));
    }

    @SuppressWarnings("unchecked")
    private Set<Long> outgoingBatches() {
        return (Set<Long>) context.getContextValue("outgoingBatches");
    }

    @SuppressWarnings("unchecked")
    private Set<BatchId> incomingBatches() {
        return (Set<BatchId>) context.getContextValue("incomingBatches");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> biggestIncomingByNode() {
        return (Map<String, Long>) context.getContextValue("biggestIncomingByNode");
    }

    private IStagedResource oldResource() {
        return resourceWithPath("outgoing/store-1/0000000005");
    }

    private IStagedResource resourceWithPath(String path) {
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getPath()).thenReturn(path);
        when(resource.getLastUpdateTime()).thenReturn(System.currentTimeMillis() - 600000);
        return resource;
    }
}
