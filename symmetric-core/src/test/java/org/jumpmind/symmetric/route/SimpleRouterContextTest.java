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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.route.SimpleRouterContext.RouterTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleRouterContextTest {
    private NodeChannel channel;
    private SimpleRouterContext context;

    @BeforeEach
    void setUp() {
        channel = new NodeChannel("default");
        context = new SimpleRouterContext("store-1", channel);
    }

    @Test
    void testGetSourceNodeId() {
        assertEquals("store-1", context.getSourceNodeId());
    }

    @Test
    void testGetChannel() {
        assertSame(channel, context.getChannel());
    }

    @Test
    void testGetBinaryEncoding_isNull() {
        assertNull(context.getBinaryEncoding());
    }

    @Test
    void testGetBatchId_isUnassigned() {
        assertEquals(-1, context.getBatchId());
    }

    @Test
    void testDefaultConstructor_leavesNodeIdUnset() {
        assertNull(new SimpleRouterContext().getSourceNodeId());
    }

    @Test
    void testIsEncountedTransactionBoundary_isFalseByDefault() {
        assertFalse(context.isEncountedTransactionBoundary());
    }

    @Test
    void testSetEncountedTransactionBoundary() {
        context.setEncountedTransactionBoundary(true);
        assertTrue(context.isEncountedTransactionBoundary());
    }

    @Test
    void testGetBatchSizeNotToExceed_isZeroByDefault() {
        assertEquals(0, context.getBatchSizeNotToExceed());
    }

    @Test
    void testSetBatchSizeNotToExceed() {
        context.setBatchSizeNotToExceed(500);
        assertEquals(500, context.getBatchSizeNotToExceed());
    }

    @Test
    void testGetStat_withUnrecordedNameIsZero() {
        assertEquals(0, context.getStat("missing"));
    }

    @Test
    void testIncrementStat_accumulates() {
        context.incrementStat(5, "read.ms");
        context.incrementStat(7, "read.ms");
        assertEquals(12, context.getStat("read.ms"));
    }

    @Test
    void testIncrementStat_keepsNamesIndependent() {
        context.incrementStat(5, "read.ms");
        context.incrementStat(9, "write.ms");
        assertEquals(5, context.getStat("read.ms"));
        assertEquals(9, context.getStat("write.ms"));
    }

    @Test
    void testAddQueryTime_createsAndAccumulatesTimer() {
        context.addQueryTime("router-1", 10);
        RouterTimer timer = context.addQueryTime("router-1", 5);
        assertEquals(15, timer.getQueryTime());
        assertEquals(15, timer.getTotalQueryTime());
    }

    @Test
    void testAddQueryTime_keepsRoutersIndependent() {
        context.addQueryTime("router-1", 10);
        assertEquals(3, context.addQueryTime("router-2", 3).getQueryTime());
    }

    @Test
    void testRouterTimer_resetQueryTimeKeepsTotal() {
        RouterTimer timer = context.addQueryTime("router-1", 10);
        timer.resetQueryTime();
        assertEquals(0, timer.getQueryTime());
        assertEquals(10, timer.getTotalQueryTime());
    }

    @Test
    void testGetContextCache_storesValues() {
        context.put("key", "value");
        assertEquals("value", context.getContextCache().get("key"));
    }

    // Defect pinned, not endorsed: transferStats reads each value from its own stats map rather than
    // the source context's, so the source's numbers are never carried over and its own are doubled.
    @Test
    void testTransferStats_doesNotCarryOverSourceValues() {
        SimpleRouterContext source = new SimpleRouterContext("store-2", channel);
        source.incrementStat(7, "read.ms");
        context.incrementStat(3, "read.ms");
        context.transferStats(source);
        assertEquals(6, context.getStat("read.ms"));
    }
}
