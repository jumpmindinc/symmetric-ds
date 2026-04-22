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
package org.jumpmind.symmetric.observability.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class MetricIntervalStatsTest {
    // Sep 9 2001 01:46:40 UTC — well after Y2K
    private static final long T_2001 = 1_000_000_000_000L;
    // Nov 14 2023 22:13:20 UTC
    private static final long T_2023 = 1_700_000_000_000L;
    private static final long D = 300_000L; // 5-minute interval

    private static MetricIntervalStats interval(long start, long end) {
        return new MetricIntervalStats(start, end, 0, 0, 0, 0, 1, 0, false);
    }

    // -----------------------------------------------------------------------
    // compareTo — end-time ordering (post-2000 timestamps)
    // -----------------------------------------------------------------------

    @Test
    void compareTo_earlierEndTime_comesFirst() {
        MetricIntervalStats a = interval(T_2001, T_2001 + D);
        MetricIntervalStats b = interval(T_2023, T_2023 + D);
        assertTrue(a.compareTo(b) < 0, "interval ending in 2001 should sort before one ending in 2023");
        assertTrue(b.compareTo(a) > 0, "interval ending in 2023 should sort after one ending in 2001");
    }

    @Test
    void compareTo_adjacentIntervals_olderFirst() {
        // Back-to-back 5-minute windows
        MetricIntervalStats first  = interval(T_2023,       T_2023 + D);
        MetricIntervalStats second = interval(T_2023 + D,   T_2023 + 2 * D);
        MetricIntervalStats third  = interval(T_2023 + 2*D, T_2023 + 3 * D);
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(third) < 0);
        assertTrue(first.compareTo(third) < 0);
    }

    // -----------------------------------------------------------------------
    // compareTo — same end time: longer interval (earlier start) sorts first
    // -----------------------------------------------------------------------

    @Test
    void compareTo_sameEnd_longerIntervalComesFirst() {
        // longer: starts 10 minutes before the shared end
        MetricIntervalStats longer  = interval(T_2023 - 2 * D, T_2023);
        // shorter: starts 5 minutes before the shared end
        MetricIntervalStats shorter = interval(T_2023 - D,     T_2023);
        assertTrue(longer.compareTo(shorter) < 0, "longer interval (earlier start) should sort before shorter with same end");
        assertTrue(shorter.compareTo(longer) > 0);
    }

    @Test
    void compareTo_sameEnd_sameStart_returnsZero() {
        MetricIntervalStats a = interval(T_2001, T_2001 + D);
        MetricIntervalStats b = interval(T_2001, T_2001 + D);
        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));
    }

    // -----------------------------------------------------------------------
    // Stream.sorted() — verifies natural ordering end-to-end
    // -----------------------------------------------------------------------

    @Test
    void sorted_mixedIntervals_orderedByEndThenStart() {
        MetricIntervalStats early       = interval(T_2001,           T_2001 + D);       // ends earliest
        MetricIntervalStats longerSameEnd = interval(T_2023 - 2 * D, T_2023 + D);      // same end as late, longer
        MetricIntervalStats shorterSameEnd = interval(T_2023 - D,    T_2023 + D);      // same end as longerSameEnd, shorter
        MetricIntervalStats late        = interval(T_2023,           T_2023 + 2 * D);  // ends latest

        List<MetricIntervalStats> sorted = Stream.of(late, shorterSameEnd, early, longerSameEnd)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(early,          sorted.get(0), "earliest end should be first");
        assertEquals(longerSameEnd,  sorted.get(1), "longer interval should precede shorter when end times match");
        assertEquals(shorterSameEnd, sorted.get(2));
        assertEquals(late,           sorted.get(3), "latest end should be last");
    }
}
