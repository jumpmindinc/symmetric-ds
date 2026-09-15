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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PerfResultTest {
    @Test
    void testConstructor_withName() {
        PerfResult result = new PerfResult("write");
        assertEquals("write", result.getName());
        assertEquals(0, result.getCount());
        assertEquals(0, result.getMillis());
        assertEquals("", result.getOutcome());
    }

    @Test
    void testConstructor_withCountAndMillis() {
        PerfResult result = new PerfResult("write", 500, 1000, 4.5f);
        assertEquals("write", result.getName());
        assertEquals(500, result.getCount());
        assertEquals(1000, result.getMillis());
    }

    // Defect pinned, not endorsed: the four-argument constructor never assigns its rating argument,
    // so a result built with a rating still reports 0 until setRating is called.
    @Test
    void testConstructor_dropsRatingArgument() {
        assertEquals(0f, new PerfResult("write", 500, 1000, 4.5f).getRating());
    }

    @Test
    void testSetRating() {
        PerfResult result = new PerfResult("write");
        result.setRating(3.5f);
        assertEquals(3.5f, result.getRating());
    }

    @Test
    void testIncrementCount() {
        PerfResult result = new PerfResult("write");
        result.incrementCount(5);
        result.incrementCount(3);
        assertEquals(8, result.getCount());
    }

    @Test
    void testIncrementMillis() {
        PerfResult result = new PerfResult("write");
        result.incrementMillis(100);
        result.incrementMillis(50);
        assertEquals(150, result.getMillis());
    }

    @Test
    void testGetOperationsPerSecond() {
        PerfResult result = new PerfResult("write", 500, 1000, 0f);
        assertEquals(500, result.getOperationsPerSecond());
    }

    @Test
    void testGetOperationsPerSecond_withSubSecondDuration() {
        PerfResult result = new PerfResult("write", 500, 500, 0f);
        assertEquals(1000, result.getOperationsPerSecond());
    }

    @Test
    void testGetOperationsPerSecond_withZeroMillisReturnsCount() {
        PerfResult result = new PerfResult("write", 500, 0, 0f);
        assertEquals(500, result.getOperationsPerSecond());
    }

    @Test
    void testSetOutcome() {
        PerfResult result = new PerfResult("write");
        result.setOutcome(PerfResult.OUTCOME_SUCCESS);
        assertEquals("Success", result.getOutcome());
    }

    @Test
    void testEquals_withSameName() {
        assertEquals(new PerfResult("write"), new PerfResult("write", 1, 2, 3f));
    }

    @Test
    void testEquals_withDifferentName() {
        assertNotEquals(new PerfResult("write"), new PerfResult("read"));
    }

    @Test
    void testEquals_withSameInstance() {
        PerfResult result = new PerfResult("write");
        assertTrue(result.equals(result));
    }

    @Test
    void testEquals_withNull() {
        assertFalse(new PerfResult("write").equals(null));
    }

    @Test
    void testEquals_withOtherType() {
        assertFalse(new PerfResult("write").equals("write"));
    }

    @Test
    void testHashCode_matchesForIdenticalState() {
        assertEquals(new PerfResult("write").hashCode(), new PerfResult("write").hashCode());
    }

    @Test
    void testToString_includesNameCountAndOps() {
        String text = new PerfResult("write", 500, 1000, 0f).toString();
        assertTrue(text.contains("name=write"));
        assertTrue(text.contains("count=500"));
        assertTrue(text.contains("ops=500"));
    }
}
