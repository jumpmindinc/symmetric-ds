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
package org.jumpmind.symmetric.observability.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.jumpmind.symmetric.observability.repository.SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SurrogateLongKeyBufferTest {
    @ParameterizedTest(name = "roundDown({0}) == {1}")
    @CsvSource({
            "0,   0", // already on a boundary
            "1,   0", // first slot inside span 0
            "9,   0", // last slot inside span 0
            "10,  10", // exactly on next boundary
            "15,  10", // mid-span
            "19,  10", // last slot inside span 10
            "20,  20", // exactly on span 20 boundary
            "100, 100" // larger multiple
    })
    void roundDown_parametrized(long input, long expected) {
        assertEquals(expected, SurrogateLongKeyBuffer.roundDownToBufferStart(input));
    }

    @Test
    void roundDown_zeroIsAlreadyAligned() {
        assertEquals(0L, SurrogateLongKeyBuffer.roundDownToBufferStart(0));
    }

    @Test
    void roundDown_exactBoundaryIsUnchanged() {
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundDownToBufferStart(SURROGATE_KEY_BUFFER_SIZE));
    }

    @Test
    void roundDown_oneBelowNextBoundary() {
        assertEquals(0L, SurrogateLongKeyBuffer.roundDownToBufferStart(SURROGATE_KEY_BUFFER_SIZE - 1));
    }

    @Test
    void roundDown_oneAboveBoundary() {
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundDownToBufferStart(SURROGATE_KEY_BUFFER_SIZE + 1));
    }

    @ParameterizedTest(name = "roundUp({0}) == {1}")
    @CsvSource({
            "0,  10", // on boundary → next buffer starts at 10
            "1,  10",
            "5,  10", // mid-span
            "9,  10", // one step from next boundary
            "10, 20", // on boundary → next buffer starts at 20
            "11, 20",
            "20, 30", // another boundary
            "77, 80" // key from dup-key regression: must not shrink nextAvailableValue
    })
    void roundUp_parametrized(long input, long expected) {
        assertEquals(expected, SurrogateLongKeyBuffer.roundUpToNextBufferStart(input));
    }

    @Test
    void roundUp_boundaryValuesReturnNextBoundary() {
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(0));
        assertEquals(2 * SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(SURROGATE_KEY_BUFFER_SIZE));
        assertEquals(3 * SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(2 * SURROGATE_KEY_BUFFER_SIZE));
    }

    @Test
    void roundUp_oneBelowBoundaryReturnsNextBoundary() {
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(SURROGATE_KEY_BUFFER_SIZE - 1));
    }

    @Test
    void roundUp_oneAboveBoundaryReturnsNextNextBoundary() {
        assertEquals(2 * SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(SURROGATE_KEY_BUFFER_SIZE + 1));
    }

    @ParameterizedTest(name = "roundUp({0}) == {1}")
    @CsvSource({
            "0,  10",
            "1,  10",
            "5,  10",
            "9,  10",
            "10, 20",
            "15, 20",
            "19, 20",
            "20, 30"
    })
    void roundUp_returnsAbsoluteNextBoundary(long value, long expectedNextBoundary) {
        assertEquals(expectedNextBoundary, SurrogateLongKeyBuffer.roundUpToNextBufferStart(value));
    }

    @Test
    void roundUp_isAlwaysAlignedToSpan() {
        for (long v = 0; v < 3 * SURROGATE_KEY_BUFFER_SIZE; v++) {
            long nextBoundary = SurrogateLongKeyBuffer.roundUpToNextBufferStart(v);
            assertEquals(0, nextBoundary % SURROGATE_KEY_BUFFER_SIZE,
                    "Expected multiple of SPAN for value=" + v);
        }
    }

    @Test
    void roundUp_isAlwaysGreaterThanInput() {
        for (long v = 0; v < 3 * SURROGATE_KEY_BUFFER_SIZE; v++) {
            long nextBoundary = SurrogateLongKeyBuffer.roundUpToNextBufferStart(v);
            assertEquals(true, nextBoundary > v,
                    "Expected roundUp(" + v + ")=" + nextBoundary + " to be greater than input");
        }
    }
}
