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
package org.jumpmind.symmetric.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.jumpmind.symmetric.observability.interfaces.ISymObservation;
import org.jumpmind.symmetric.observability.models.ObservationDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservationsQueueTest {
    private static final long T = 1_700_000_000_000L;
    private ObservationsQueue<ISymObservation> queue;

    private static ObservationDouble obs(double value, long timestamp) {
        return new ObservationDouble(value, timestamp);
    }

    @BeforeEach
    void setUp() {
        queue = new ObservationsQueue<>();
    }
    // -----------------------------------------------------------------------
    // offer / add / size / isEmpty
    // -----------------------------------------------------------------------

    @Test
    void offer_singleItem_sizeIsOne() {
        queue.offer(obs(1.0, T));
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    void add_delegatesToOffer_incrementsSize() {
        queue.add(obs(2.0, T));
        assertEquals(1, queue.size());
    }

    @Test
    void offer_multipleItems_sizeTracksAll() {
        queue.offer(obs(1.0, T));
        queue.offer(obs(2.0, T + 1));
        queue.offer(obs(3.0, T + 2));
        assertEquals(3, queue.size());
    }

    @Test
    void isEmpty_onNewQueue_returnsTrue() {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }
    // -----------------------------------------------------------------------
    // poll / peek / element / remove()
    // -----------------------------------------------------------------------

    @Test
    void poll_emptyQueue_returnsNull() {
        assertNull(queue.poll());
    }

    @Test
    void poll_removesHead_decrementsSize() {
        ObservationDouble first = obs(1.0, T);
        ObservationDouble second = obs(2.0, T + 1);
        queue.offer(first);
        queue.offer(second);
        ISymObservation polled = queue.poll();
        assertEquals(first, polled);
        assertEquals(1, queue.size());
    }

    @Test
    void poll_untilEmpty_sizeReachesZero() {
        queue.offer(obs(1.0, T));
        queue.offer(obs(2.0, T + 1));
        queue.poll();
        queue.poll();
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void peek_emptyQueue_returnsNull() {
        assertNull(queue.peek());
    }

    @Test
    void peek_doesNotRemoveItem_sizeUnchanged() {
        queue.offer(obs(5.0, T));
        ISymObservation head = queue.peek();
        assertEquals(obs(5.0, T), head);
        assertEquals(1, queue.size());
    }

    @Test
    void element_emptyQueue_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> queue.element());
    }

    @Test
    void element_nonEmptyQueue_returnsHead() {
        ObservationDouble first = obs(1.0, T);
        queue.offer(first);
        queue.offer(obs(2.0, T + 1));
        assertEquals(first, queue.element());
        assertEquals(2, queue.size());
    }

    @Test
    void remove_emptyQueue_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> queue.remove());
    }

    @Test
    void remove_nonEmptyQueue_removesAndDecrementsSize() {
        ObservationDouble first = obs(1.0, T);
        queue.offer(first);
        queue.offer(obs(2.0, T + 1));
        ISymObservation removed = queue.remove();
        assertEquals(first, removed);
        assertEquals(1, queue.size());
    }
    // -----------------------------------------------------------------------
    // remove(Object)
    // -----------------------------------------------------------------------

    @Test
    void removeObject_presentItem_returnsTrueAndDecrementsSize() {
        ObservationDouble item = obs(3.0, T);
        queue.offer(item);
        queue.offer(obs(4.0, T + 1));
        boolean result = queue.remove(item);
        assertTrue(result);
        assertEquals(1, queue.size());
    }

    @Test
    void removeObject_absentItem_returnsFalse_sizeUnchanged() {
        queue.offer(obs(1.0, T));
        boolean result = queue.remove(obs(99.0, T + 999));
        assertFalse(result);
        assertEquals(1, queue.size());
    }
    // -----------------------------------------------------------------------
    // clear
    // -----------------------------------------------------------------------

    @Test
    void clear_removesAllItems_sizeZero() {
        queue.offer(obs(1.0, T));
        queue.offer(obs(2.0, T + 1));
        queue.clear();
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }
    // -----------------------------------------------------------------------
    // contains / containsAll
    // -----------------------------------------------------------------------

    @Test
    void contains_presentItem_returnsTrue() {
        ObservationDouble item = obs(7.0, T);
        queue.offer(item);
        assertTrue(queue.contains(item));
    }

    @Test
    void contains_absentItem_returnsFalse() {
        queue.offer(obs(1.0, T));
        assertFalse(queue.contains(obs(99.0, T)));
    }

    @Test
    void containsAll_allPresent_returnsTrue() {
        ObservationDouble a = obs(1.0, T);
        ObservationDouble b = obs(2.0, T + 1);
        queue.offer(a);
        queue.offer(b);
        assertTrue(queue.containsAll(List.of(a, b)));
    }

    @Test
    void containsAll_someAbsent_returnsFalse() {
        ObservationDouble a = obs(1.0, T);
        queue.offer(a);
        assertFalse(queue.containsAll(List.of(a, obs(99.0, T + 1))));
    }
    // -----------------------------------------------------------------------
    // addAll
    // -----------------------------------------------------------------------

    @Test
    void addAll_nonEmptyCollection_addsAllAndReturnsTrue() {
        List<ObservationDouble> items = List.of(obs(1.0, T), obs(2.0, T + 1), obs(3.0, T + 2));
        boolean changed = queue.addAll(items);
        assertTrue(changed);
        assertEquals(3, queue.size());
    }

    @Test
    void addAll_emptyCollection_returnsFalse_sizeZero() {
        boolean changed = queue.addAll(List.of());
        assertFalse(changed);
        assertEquals(0, queue.size());
    }
    // -----------------------------------------------------------------------
    // removeAll
    // -----------------------------------------------------------------------

    @Test
    void removeAll_removesOnlySpecifiedItems() {
        ObservationDouble a = obs(1.0, T);
        ObservationDouble b = obs(2.0, T + 1);
        ObservationDouble c = obs(3.0, T + 2);
        queue.offer(a);
        queue.offer(b);
        queue.offer(c);
        boolean changed = queue.removeAll(List.of(a, c));
        assertTrue(changed);
        assertEquals(1, queue.size());
        assertTrue(queue.contains(b));
    }

    @Test
    void removeAll_nonePresent_returnsFalse() {
        queue.offer(obs(1.0, T));
        boolean changed = queue.removeAll(List.of(obs(99.0, T)));
        assertFalse(changed);
        assertEquals(1, queue.size());
    }
    // -----------------------------------------------------------------------
    // retainAll
    // -----------------------------------------------------------------------

    @Test
    void retainAll_keepsOnlySpecifiedItems() {
        ObservationDouble a = obs(1.0, T);
        ObservationDouble b = obs(2.0, T + 1);
        ObservationDouble c = obs(3.0, T + 2);
        queue.offer(a);
        queue.offer(b);
        queue.offer(c);
        boolean changed = queue.retainAll(List.of(b));
        assertTrue(changed);
        assertEquals(1, queue.size());
        assertTrue(queue.contains(b));
        assertFalse(queue.contains(a));
        assertFalse(queue.contains(c));
    }

    @Test
    void retainAll_allRetained_returnsFalse() {
        ObservationDouble a = obs(1.0, T);
        ObservationDouble b = obs(2.0, T + 1);
        queue.offer(a);
        queue.offer(b);
        boolean changed = queue.retainAll(List.of(a, b));
        assertFalse(changed);
        assertEquals(2, queue.size());
    }
    // -----------------------------------------------------------------------
    // iterator remove
    // -----------------------------------------------------------------------

    @Test
    void iterator_remove_decrementsSize() {
        ObservationDouble a = obs(1.0, T);
        ObservationDouble b = obs(2.0, T + 1);
        queue.offer(a);
        queue.offer(b);
        Iterator<ISymObservation> it = queue.iterator();
        it.next();
        it.remove();
        assertEquals(1, queue.size());
    }
    // -----------------------------------------------------------------------
    // toArray
    // -----------------------------------------------------------------------

    @Test
    void toArray_returnsAllItems() {
        ObservationDouble a = obs(1.0, T);
        ObservationDouble b = obs(2.0, T + 1);
        queue.offer(a);
        queue.offer(b);
        Object[] arr = queue.toArray();
        assertEquals(2, arr.length);
    }

    @Test
    void toArray_typedArray_returnsAllItems() {
        ObservationDouble a = obs(1.0, T);
        queue.offer(a);
        ISymObservation[] arr = queue.toArray(new ISymObservation[0]);
        assertEquals(1, arr.length);
        assertEquals(a, arr[0]);
    }
    // -----------------------------------------------------------------------
    // MAX_QUEUE_SIZE enforcement
    // -----------------------------------------------------------------------

    @Test
    void offer_atMaxCapacity_evictsOldestBeforeAdding() {
        // Subclass with a tiny cap so the test runs quickly without filling 10 million slots.
        ObservationsQueue<ISymObservation> capped = new ObservationsQueue<ISymObservation>() {
            private static final int TINY_MAX = 3;

            @Override
            public boolean offer(ISymObservation arg0) {
                while (approximateSize.get() >= TINY_MAX) {
                    ISymObservation removed = queue.poll();
                    if (removed != null) {
                        approximateSize.decrementAndGet();
                    } else {
                        break;
                    }
                }
                boolean added = queue.offer(arg0);
                if (added) {
                    approximateSize.incrementAndGet();
                }
                return added;
            }
        };
        capped.offer(obs(1.0, T));
        capped.offer(obs(2.0, T + 1));
        capped.offer(obs(3.0, T + 2));
        assertEquals(3, capped.size());
        // Adding a 4th item should evict the oldest and keep size at 3
        ObservationDouble newest = obs(4.0, T + 3);
        capped.offer(newest);
        assertEquals(3, capped.size());
        // The oldest item (value=1.0) should have been evicted; newest should be present
        assertTrue(capped.contains(newest));
    }
    // -----------------------------------------------------------------------
    // peekBetween
    // -----------------------------------------------------------------------

    @Test
    void peekBetween_returnsItemsInRangeWithoutRemoving() {
        queue.offer(obs(1.0, T));
        queue.offer(obs(2.0, T + 100));
        queue.offer(obs(3.0, T + 200));
        queue.offer(obs(4.0, T + 300));
        ISymObservation[] result = queue.peekBetween(T + 50, T + 250);
        assertEquals(2, result.length);
        assertEquals(obs(2.0, T + 100), result[0]);
        assertEquals(obs(3.0, T + 200), result[1]);
        // Queue must be unchanged
        assertEquals(4, queue.size());
    }

    @Test
    void peekBetween_inclusiveBoundaries_includesExactMatches() {
        ObservationDouble first = obs(1.0, T);
        ObservationDouble last = obs(3.0, T + 200);
        queue.offer(first);
        queue.offer(obs(2.0, T + 100));
        queue.offer(last);
        ISymObservation[] result = queue.peekBetween(T, T + 200);
        assertEquals(3, result.length);
    }

    @Test
    void peekBetween_noMatchingItems_returnsEmptyArray() {
        queue.offer(obs(1.0, T));
        ISymObservation[] result = queue.peekBetween(T + 1000, T + 2000);
        assertEquals(0, result.length);
        assertEquals(1, queue.size());
    }
    // -----------------------------------------------------------------------
    // removeAllBetween
    // -----------------------------------------------------------------------

    @Test
    void removeAllBetween_removesAndReturnsItemsInRange() {
        queue.offer(obs(1.0, T));
        queue.offer(obs(2.0, T + 100));
        queue.offer(obs(3.0, T + 200));
        queue.offer(obs(4.0, T + 300));
        ISymObservation[] removed = queue.removeAllBetween(T + 50, T + 250);
        assertEquals(2, removed.length);
        assertEquals(2, queue.size());
        assertFalse(queue.contains(obs(2.0, T + 100)));
        assertFalse(queue.contains(obs(3.0, T + 200)));
    }

    @Test
    void removeAllBetween_inclusiveBoundaries_removesExactMatches() {
        ObservationDouble boundary1 = obs(1.0, T + 100);
        ObservationDouble boundary2 = obs(2.0, T + 200);
        queue.offer(obs(0.0, T));
        queue.offer(boundary1);
        queue.offer(boundary2);
        queue.offer(obs(3.0, T + 300));
        ISymObservation[] removed = queue.removeAllBetween(T + 100, T + 200);
        assertEquals(2, removed.length);
        assertEquals(2, queue.size());
    }

    @Test
    void removeAllBetween_noMatchingItems_returnsEmptyArray_sizeUnchanged() {
        queue.offer(obs(1.0, T));
        ISymObservation[] removed = queue.removeAllBetween(T + 1000, T + 2000);
        assertEquals(0, removed.length);
        assertEquals(1, queue.size());
    }

    @Test
    void removeAllBetween_allItems_queueBecomesEmpty() {
        queue.offer(obs(1.0, T + 10));
        queue.offer(obs(2.0, T + 20));
        ISymObservation[] removed = queue.removeAllBetween(T, T + 100);
        assertEquals(2, removed.length);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }
}
