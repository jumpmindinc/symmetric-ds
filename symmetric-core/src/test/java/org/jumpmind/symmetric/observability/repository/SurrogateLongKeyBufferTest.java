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

    // -----------------------------------------------------------------------
    // roundDownToBufferStart — rounds value down to the nearest SPAN boundary
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "roundDown({0}) == {1}")
    @CsvSource({
        "0,   0",   // already on a boundary
        "1,   0",   // first slot inside span 0
        "9,   0",   // last slot inside span 0
        "10,  10",  // exactly on next boundary
        "15,  10",  // mid-span
        "19,  10",  // last slot inside span 10
        "20,  20",  // exactly on span 20 boundary
        "100, 100"  // larger multiple
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

    // -----------------------------------------------------------------------
    // roundUpToNextBufferStart — returns distance from value to the next
    //   SPAN boundary (result is in [1, SPAN_SIZE])
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "roundUp({0}) == {1}")
    @CsvSource({
        "0,  10",   // on boundary → full span away
        "1,   9",
        "5,   5",   // mid-span
        "9,   1",   // one step from next boundary
        "10, 10",   // on boundary again → full span away
        "11,  9",
        "20, 10"    // another boundary
    })
    void roundUp_parametrized(long input, long expected) {
        assertEquals(expected, SurrogateLongKeyBuffer.roundUpToNextBufferStart(input));
    }

    @Test
    void roundUp_boundaryValuesReturnFullSpan() {
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(0));
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(SURROGATE_KEY_BUFFER_SIZE));
        assertEquals(SURROGATE_KEY_BUFFER_SIZE,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(2 * SURROGATE_KEY_BUFFER_SIZE));
    }

    @Test
    void roundUp_oneBelowBoundaryReturnsOne() {
        assertEquals(1L,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(SURROGATE_KEY_BUFFER_SIZE - 1));
    }

    @Test
    void roundUp_oneAboveBoundaryReturnsSpanMinusOne() {
        assertEquals(SURROGATE_KEY_BUFFER_SIZE - 1,
                SurrogateLongKeyBuffer.roundUpToNextBufferStart(SURROGATE_KEY_BUFFER_SIZE + 1));
    }

    // -----------------------------------------------------------------------
    // Combined: roundDown(value + roundUp(value)) == next buffer boundary
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "roundDown(value={0} + roundUp(value)) == {1}")
    @CsvSource({
        "0,  10",   // on boundary → next boundary is SPAN away
        "1,  10",
        "5,  10",   // mid-span
        "9,  10",   // one step from boundary
        "10, 20",   // on next boundary → jumps to the one after
        "15, 20",
        "19, 20",
        "20, 30"
    })
    void roundDown_of_valuePlusRoundUp_equalsNextBoundary(long value, long expectedNextBoundary) {
        long offset = SurrogateLongKeyBuffer.roundUpToNextBufferStart(value);
        long nextBoundary = SurrogateLongKeyBuffer.roundDownToBufferStart(value + offset);
        assertEquals(expectedNextBoundary, nextBoundary);
    }

    @Test
    void roundDown_of_valuePlusRoundUp_isAlwaysMultipleOfSpan() {
        for (long v = 0; v < 3 * SURROGATE_KEY_BUFFER_SIZE; v++) {
            long offset = SurrogateLongKeyBuffer.roundUpToNextBufferStart(v);
            long nextBoundary = SurrogateLongKeyBuffer.roundDownToBufferStart(v + offset);
            assertEquals(0, nextBoundary % SURROGATE_KEY_BUFFER_SIZE,
                    "Expected multiple of SPAN for value=" + v);
        }
    }
}
