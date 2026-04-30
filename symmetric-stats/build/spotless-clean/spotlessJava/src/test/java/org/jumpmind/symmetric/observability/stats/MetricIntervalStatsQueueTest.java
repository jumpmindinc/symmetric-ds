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
package org.jumpmind.symmetric.observability.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricIntervalStatsQueueTest {
    // Jan 1 2020 00:00:00 UTC — aligned to 5-minute boundary
    private static final long T = 1_577_836_800_000L;
    private static final long D = 300_000L;
    private MetricIntervalStatsQueue queue;

    @BeforeEach
    void setUp() {
        queue = new MetricIntervalStatsQueue();
    }

    private static MetricIntervalStats stats(long start) {
        return new MetricIntervalStats(start, start + D, 1.0, 0.0, 2.0, 0.0, 1, 1.0, false);
    }
    // ── basic queue operations ────────────────────────────────────────────────

    @Test
    void isEmpty_newQueue_returnsTrue() {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void offer_singleItem_sizeIsOne() {
        queue.offer(stats(T));
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    void add_delegatesToOffer_incrementsSize() {
        queue.add(stats(T));
        assertEquals(1, queue.size());
    }

    @Test
    void poll_emptyQueue_returnsNull() {
        assertNull(queue.poll());
    }

    @Test
    void poll_removesItem_decrementsSize() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        ISymIntervalStats polled = queue.poll();
        assertEquals(s, polled);
        assertEquals(0, queue.size());
    }

    @Test
    void peek_emptyQueue_returnsNull() {
        assertNull(queue.peek());
    }

    @Test
    void peek_doesNotRemoveItem_sizeUnchanged() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        assertEquals(s, queue.peek());
        assertEquals(1, queue.size());
    }

    @Test
    void element_emptyQueue_throwsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> queue.element());
    }

    @Test
    void element_nonEmptyQueue_returnsHead() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        queue.offer(stats(T + D));
        assertEquals(s, queue.element());
        assertEquals(2, queue.size());
    }

    @Test
    void remove_emptyQueue_throwsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> queue.remove());
    }

    @Test
    void remove_nonEmptyQueue_removesAndDecrementsSize() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        queue.offer(stats(T + D));
        ISymIntervalStats removed = queue.remove();
        assertEquals(s, removed);
        assertEquals(1, queue.size());
    }

    @Test
    void clear_resetsSize() {
        queue.offer(stats(T));
        queue.offer(stats(T + D));
        queue.clear();
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }
    // ── contains / containsAll ────────────────────────────────────────────────

    @Test
    void contains_presentItem_returnsTrue() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        assertTrue(queue.contains(s));
    }

    @Test
    void contains_absentItem_returnsFalse() {
        queue.offer(stats(T));
        assertFalse(queue.contains(stats(T + D)));
    }

    @Test
    void containsAll_allPresent_returnsTrue() {
        ISymIntervalStats a = stats(T);
        ISymIntervalStats b = stats(T + D);
        queue.offer(a);
        queue.offer(b);
        assertTrue(queue.containsAll(List.of(a, b)));
    }

    @Test
    void containsAll_someAbsent_returnsFalse() {
        ISymIntervalStats a = stats(T);
        queue.offer(a);
        assertFalse(queue.containsAll(List.of(a, stats(T + D))));
    }
    // ── addAll / removeAll / retainAll ────────────────────────────────────────

    @Test
    void addAll_addsAllItems() {
        List<MetricIntervalStats> items = List.of(stats(T), stats(T + D), stats(T + 2 * D));
        queue.addAll(items);
        assertEquals(3, queue.size());
    }

    @Test
    void addAll_emptyCollection_returnsFalse() {
        assertFalse(queue.addAll(List.of()));
        assertEquals(0, queue.size());
    }

    @Test
    void removeObject_presentItem_returnsTrueAndDecrements() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        assertTrue(queue.remove(s));
        assertEquals(0, queue.size());
    }

    @Test
    void removeObject_absentItem_returnsFalse_sizeUnchanged() {
        queue.offer(stats(T));
        assertFalse(queue.remove(stats(T + 99999)));
        assertEquals(1, queue.size());
    }

    @Test
    void removeAll_removesOnlySpecified() {
        ISymIntervalStats a = stats(T);
        ISymIntervalStats b = stats(T + D);
        ISymIntervalStats c = stats(T + 2 * D);
        queue.offer(a);
        queue.offer(b);
        queue.offer(c);
        assertTrue(queue.removeAll(List.of(a, c)));
        assertEquals(1, queue.size());
        assertTrue(queue.contains(b));
    }

    @Test
    void retainAll_keepsOnlySpecified() {
        ISymIntervalStats a = stats(T);
        ISymIntervalStats b = stats(T + D);
        queue.offer(a);
        queue.offer(b);
        assertTrue(queue.retainAll(List.of(a)));
        assertEquals(1, queue.size());
        assertTrue(queue.contains(a));
        assertFalse(queue.contains(b));
    }

    @Test
    void retainAll_allRetained_returnsFalse() {
        ISymIntervalStats a = stats(T);
        ISymIntervalStats b = stats(T + D);
        queue.offer(a);
        queue.offer(b);
        assertFalse(queue.retainAll(List.of(a, b)));
        assertEquals(2, queue.size());
    }
    // ── iterator ─────────────────────────────────────────────────────────────

    @Test
    void iterator_remove_decrementsSize() {
        queue.offer(stats(T));
        queue.offer(stats(T + D));
        Iterator<ISymIntervalStats> it = queue.iterator();
        it.next();
        it.remove();
        assertEquals(1, queue.size());
    }
    // ── toArray ───────────────────────────────────────────────────────────────

    @Test
    void toArray_returnsAllItems() {
        queue.offer(stats(T));
        queue.offer(stats(T + D));
        assertEquals(2, queue.toArray().length);
    }

    @Test
    void toArray_typedArray_returnsAllItems() {
        ISymIntervalStats s = stats(T);
        queue.offer(s);
        ISymIntervalStats[] arr = queue.toArray(new ISymIntervalStats[0]);
        assertEquals(1, arr.length);
        assertEquals(s, arr[0]);
    }
    // ── exportAll ─────────────────────────────────────────────────────────────

    @Test
    void exportAll_drains_returnsAll() {
        ISymIntervalStats a = stats(T);
        ISymIntervalStats b = stats(T + D);
        queue.offer(a);
        queue.offer(b);
        List<ISymIntervalStats> exported = queue.exportAll();
        assertEquals(2, exported.size());
        assertTrue(exported.contains(a));
        assertTrue(exported.contains(b));
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void exportAll_emptyQueue_returnsEmptyList() {
        assertTrue(queue.exportAll().isEmpty());
    }

    @Test
    void exportAll_subsequentCall_returnsEmpty() {
        queue.offer(stats(T));
        queue.exportAll();
        assertTrue(queue.exportAll().isEmpty());
    }

    @Test
    void exportAll_itemsAddedAfterExport_areRetained() {
        queue.offer(stats(T));
        queue.exportAll();
        queue.offer(stats(T + D));
        assertEquals(1, queue.size());
    }
    // ── peekBetween ───────────────────────────────────────────────────────────

    @Test
    void peekBetween_returnsMatchingItems_withoutRemoving() {
        ISymIntervalStats s1 = stats(T);
        ISymIntervalStats s2 = stats(T + D);
        ISymIntervalStats s3 = stats(T + 2 * D);
        queue.offer(s1);
        queue.offer(s2);
        queue.offer(s3);
        ISymIntervalStats[] result = queue.peekBetween(T, T + D);
        assertEquals(2, result.length);
        assertEquals(3, queue.size()); // unchanged
    }

    @Test
    void peekBetween_inclusiveBoundaries_includesExactMatches() {
        queue.offer(stats(T));
        queue.offer(stats(T + D));
        ISymIntervalStats[] result = queue.peekBetween(T, T + D);
        assertEquals(2, result.length);
    }

    @Test
    void peekBetween_noMatches_returnsEmptyArray() {
        queue.offer(stats(T));
        ISymIntervalStats[] result = queue.peekBetween(T + D * 2, T + D * 3);
        assertEquals(0, result.length);
        assertEquals(1, queue.size());
    }
    // ── removeAllBetween ──────────────────────────────────────────────────────

    @Test
    void removeAllBetween_removesAndReturnsMatchingItems() {
        queue.offer(stats(T));
        queue.offer(stats(T + D));
        queue.offer(stats(T + 2 * D));
        ISymIntervalStats[] removed = queue.removeAllBetween(T, T + D);
        assertEquals(2, removed.length);
        assertEquals(1, queue.size());
        assertFalse(queue.contains(stats(T)));
    }

    @Test
    void removeAllBetween_noMatches_returnsEmptyArray_sizeUnchanged() {
        queue.offer(stats(T));
        ISymIntervalStats[] removed = queue.removeAllBetween(T + D * 5, T + D * 10);
        assertEquals(0, removed.length);
        assertEquals(1, queue.size());
    }

    @Test
    void removeAllBetween_allItems_queueBecomesEmpty() {
        queue.offer(stats(T));
        queue.offer(stats(T + D));
        ISymIntervalStats[] removed = queue.removeAllBetween(T, T + D);
        assertEquals(2, removed.length);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void removeAllBetween_onlyItemsWithinRangeAreRemoved() {
        queue.offer(stats(T)); // start=T, in range [T, T+D]
        queue.offer(stats(T + D)); // start=T+D, in range
        queue.offer(stats(T + 2 * D)); // start=T+2D, outside range
        ISymIntervalStats[] removed = queue.removeAllBetween(T, T + D);
        assertEquals(2, removed.length);
        assertEquals(1, queue.size());
        assertNotNull(queue.peek());
        assertEquals(T + 2 * D, queue.peek().getStartEpoch());
    }
    // ── offer eviction loop ───────────────────────────────────────────────────

    @Test
    void offer_whenApproximateSizeAtMaxCapacity_evictsRealItemAndAddsNew() {
        // Seed one real item then trick approximateSize into MAX_QUEUE_SIZE.
        // This exercises the actual MetricIntervalStatsQueue.offer() eviction loop
        // (the poll-returns-non-null path: approximateSize.decrementAndGet() fires).
        ISymIntervalStats existing = stats(T);
        queue.offer(existing);
        queue.approximateSize.set(MetricIntervalStatsQueue.MAX_QUEUE_SIZE);
        ISymIntervalStats newest = stats(T + D);
        boolean added = queue.offer(newest);
        assertTrue(added);
        assertFalse(queue.contains(existing)); // oldest evicted by poll() inside while loop
        assertTrue(queue.contains(newest));
    }

    @Test
    void offer_whenApproximateSizeAtMaxButQueueActuallyEmpty_breaksAndAddsItem() {
        // approximateSize reports MAX but the underlying ConcurrentLinkedQueue is empty
        // → q().poll() returns null → the while loop hits the break branch.
        queue.approximateSize.set(MetricIntervalStatsQueue.MAX_QUEUE_SIZE);
        ISymIntervalStats item = stats(T);
        boolean added = queue.offer(item);
        assertTrue(added);
        assertTrue(queue.contains(item));
    }
}
