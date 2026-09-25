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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StagingPurgeContextTest {
    private StagingPurgeContext context;

    @BeforeEach
    void setUp() {
        context = new StagingPurgeContext();
    }

    @Test
    void testIncrementPurgedFileCount() {
        context.incrementPurgedFileCount();
        context.incrementPurgedFileCount();
        assertEquals(2, context.getPurgedFileCount());
    }

    @Test
    void testAddPurgedFileBytes() {
        context.addPurgedFileBytes(100);
        context.addPurgedFileBytes(50);
        assertEquals(150, context.getPurgedFileSize());
    }

    @Test
    void testIncrementPurgedMemoryCount() {
        context.incrementPurgedMemoryCount();
        assertEquals(1, context.getPurgedMemCount());
    }

    // Defect pinned, not endorsed: addPurgedMemoryBytes adds to purgedMemCount instead of
    // purgedMemSize, so memory byte totals are reported as a count and getPurgedMemSize stays 0.
    @Test
    void testAddPurgedMemoryBytes_addsToCountInsteadOfSize() {
        context.addPurgedMemoryBytes(100);
        assertEquals(100, context.getPurgedMemCount());
        assertEquals(0, context.getPurgedMemSize());
    }

    @Test
    void testSetPurgedFileCount() {
        context.setPurgedFileCount(9);
        assertEquals(9, context.getPurgedFileCount());
    }

    @Test
    void testSetPurgedFileSize() {
        context.setPurgedFileSize(1024);
        assertEquals(1024, context.getPurgedFileSize());
    }

    @Test
    void testSetPurgedMemCount() {
        context.setPurgedMemCount(3);
        assertEquals(3, context.getPurgedMemCount());
    }

    @Test
    void testSetPurgedMemSize() {
        context.setPurgedMemSize(2048);
        assertEquals(2048, context.getPurgedMemSize());
    }

    @Test
    void testPutContextValue_returnsPreviousValue() {
        assertNull(context.putContextValue("key", "first"));
        assertEquals("first", context.putContextValue("key", "second"));
    }

    @Test
    void testGetContextValue_withUnknownKey() {
        assertNull(context.getContextValue("missing"));
    }

    @Test
    void testGetBoolean() {
        context.putContextValue("flag", Boolean.TRUE);
        assertTrue(context.getBoolean("flag"));
    }

    @Test
    void testGetLong() {
        context.putContextValue("ttl", 5000L);
        assertEquals(5000L, context.getLong("ttl"));
    }

    @Test
    void testShouldLogStatus_withRecentStartTime() {
        context.setStartTime(System.currentTimeMillis());
        context.setLastLogTime(System.currentTimeMillis());
        assertFalse(context.shouldLogStatus());
    }

    @Test
    void testShouldLogStatus_whenBothWindowsElapsed() {
        long twoMinutesAgo = System.currentTimeMillis() - 120000;
        context.setStartTime(twoMinutesAgo);
        context.setLastLogTime(twoMinutesAgo);
        assertTrue(context.shouldLogStatus());
    }

    @Test
    void testShouldLogStatus_whenRecentlyLogged() {
        context.setStartTime(System.currentTimeMillis() - 120000);
        context.setLastLogTime(System.currentTimeMillis());
        assertFalse(context.shouldLogStatus());
    }

    @Test
    void testGetStartTime() {
        context.setStartTime(123L);
        assertEquals(123L, context.getStartTime());
    }

    @Test
    void testGetLastLogTime() {
        context.setLastLogTime(456L);
        assertEquals(456L, context.getLastLogTime());
    }
}
