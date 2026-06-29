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
package org.jumpmind.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    private Statistics stats;

    @BeforeEach
    void setUp() {
        stats = new Statistics();
    }

    @Test
    void testGet_absentCategoryReturnsZero() {
        assertEquals(0, stats.get("missing"));
    }

    @Test
    void testIncrement_byOneAccumulates() {
        stats.increment("rows");
        stats.increment("rows");
        stats.increment("rows");
        assertEquals(3, stats.get("rows"));
    }

    @Test
    void testIncrement_byAmountAccumulates() {
        stats.increment("rows", 5);
        stats.increment("rows", 3);
        assertEquals(8, stats.get("rows"));
    }

    @Test
    void testSet_overwritesValue() {
        stats.set("rows", 100);
        assertEquals(100, stats.get("rows"));
        stats.set("rows", 7);
        assertEquals(7, stats.get("rows"));
    }

    @Test
    void testContains_reflectsPresence() {
        assertFalse(stats.contains("rows"));
        stats.increment("rows");
        assertTrue(stats.contains("rows"));
    }

    @Test
    void testGetTableStats_emptyInitially() {
        assertTrue(stats.getTableStats().isEmpty());
    }

    @Test
    void testIncrementTableStats_accumulatesPerTablePerDmlType() {
        stats.incrementTableStats("sym_data", "I", 5);
        stats.incrementTableStats("sym_data", "I", 3);
        stats.incrementTableStats("sym_data", "U", 2);
        Map<String, Long> dataStats = stats.getTableStats().get("sym_data");
        assertEquals(2, dataStats.size());
        assertEquals(8L, dataStats.get("I").longValue());
        assertEquals(2L, dataStats.get("U").longValue());
    }

    @Test
    void testIncrementTableStats_separatesDistinctTables() {
        stats.incrementTableStats("table_a", "I", 1);
        stats.incrementTableStats("table_b", "I", 2);
        assertEquals(2, stats.getTableStats().size());
        assertEquals(1L, stats.getTableStats().get("table_a").get("I").longValue());
        assertEquals(2L, stats.getTableStats().get("table_b").get("I").longValue());
    }

    @Test
    void testStopTimer_returnsNonNegativeElapsedAndRecordsStat() {
        stats.startTimer("load");
        long elapsed = stats.stopTimer("load");
        // Wall-clock dependent, so assert the invariant rather than an exact duration.
        assertTrue(elapsed >= 0);
        // stopTimer increments the stat of the same name by the elapsed time.
        assertEquals(elapsed, stats.get("load"));
        assertTrue(stats.contains("load"));
    }

    @Test
    void testStopTimer_withoutStartReturnsZeroAndRecordsNothing() {
        long elapsed = stats.stopTimer("load");
        assertEquals(0, elapsed);
        assertFalse(stats.contains("load"));
        assertEquals(0, stats.get("load"));
    }

    @Test
    void testStopTimer_consumesTimer() {
        stats.startTimer("load");
        stats.stopTimer("load");
        // Timer was removed, so a second stop has nothing to measure.
        assertEquals(0, stats.stopTimer("load"));
    }

    @Test
    void testToString_reflectsStats() {
        stats.set("loaded", 5);
        assertEquals("{loaded=5}", stats.toString());
    }
}
