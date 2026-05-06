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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;

public class MetricIntervalStatsQueue implements Queue<ISymIntervalStats> {
    protected static final int MAX_QUEUE_SIZE = 1000000;
    protected static final long serialVersionUID = 1L;
    protected final AtomicInteger approximateSize = new AtomicInteger(0);
    protected final AtomicReference<ConcurrentLinkedQueue<ISymIntervalStats>> queue = new AtomicReference<>(new ConcurrentLinkedQueue<>());

    public MetricIntervalStatsQueue() {
        super();
    }
 
    @Override
    public boolean addAll(Collection<? extends ISymIntervalStats> collection) {
        boolean changed = false;
        for (ISymIntervalStats item : collection) {
            if (offer(item)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        queue.get().clear();
        approximateSize.set(0);
    }

    @Override
    public boolean contains(Object entry) {
        return queue.get().contains(entry);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        return queue.get().containsAll(collection);
    }

    @Override
    public boolean isEmpty() {
        if (queue.get().isEmpty()) {
            approximateSize.set(0);
            return true;
        }
        return false;
    }

    @Override
    public Iterator<ISymIntervalStats> iterator() {
        Iterator<ISymIntervalStats> delegate = queue.get().iterator();
        return new Iterator<ISymIntervalStats>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public ISymIntervalStats next() {
                return delegate.next();
            }

            @Override
            public void remove() {
                delegate.remove();
                approximateSize.decrementAndGet();
            }
        };
    }

    @Override
    public boolean remove(Object entry) {
        boolean removed = queue.get().remove(entry);
        if (removed) {
            approximateSize.decrementAndGet();
        }
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        boolean changed = false;
        for (Object entry : collection) {
            if (remove(entry)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        Iterator<ISymIntervalStats> it = queue.get().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            ISymIntervalStats item = it.next();
            if (!collection.contains(item)) {
                it.remove();
                approximateSize.decrementAndGet();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public int size() {
        return approximateSize.get();
    }

    @Override
    public Object[] toArray() {
        return queue.get().toArray();
    }

    @Override
    public <E> E[] toArray(E[] arg0) {
        return queue.get().toArray(arg0);
    }

    @Override
    public boolean add(ISymIntervalStats arg0) {
        return offer(arg0);
    }

    @Override
    public ISymIntervalStats element() {
        return queue.get().element();
    }

    @Override
    public boolean offer(ISymIntervalStats arg0) {
        while (approximateSize.get() >= MAX_QUEUE_SIZE) {
            ISymIntervalStats removed = queue.get().poll();
            if (removed != null) {
                approximateSize.decrementAndGet();
            } else {
                break;
            }
        }
        boolean added = queue.get().offer(arg0);
        if (added) {
            approximateSize.incrementAndGet();
        }
        return added;
    }

    @Override
    public ISymIntervalStats peek() {
        return queue.get().peek();
    }

    @Override
    public ISymIntervalStats poll() {
        ISymIntervalStats item = queue.get().poll();
        if (item != null) {
            approximateSize.decrementAndGet();
        }
        return item;
    }

    @Override
    public ISymIntervalStats remove() {
        ISymIntervalStats item = queue.get().remove();
        approximateSize.decrementAndGet();
        return item;
    }

    /**
     * Returns all intervals whose start epoch falls within [start, end] without removing them from the queue.
     */
    public ISymIntervalStats[] peekBetween(long start, long end) {
        List<ISymIntervalStats> result = new ArrayList<>();
        for (ISymIntervalStats item : queue.get()) {
            long ts = item.getStartEpoch();
            if (ts >= start && ts <= end) {
                result.add(item);
            }
        }
        return result.toArray(new ISymIntervalStats[0]);
    }

    /**
     * Removes and returns all intervals whose start epoch falls within [start, end].
     */
    public ISymIntervalStats[] removeAllBetween(long start, long end) {
        List<ISymIntervalStats> result = new ArrayList<>();
        Iterator<ISymIntervalStats> it = iterator();
        while (it.hasNext()) {
            ISymIntervalStats item = it.next();
            long ts = item.getStartEpoch();
            if (ts >= start && ts <= end) {
                it.remove();
                result.add(item);
            }
        }
        return result.toArray(new ISymIntervalStats[0]);
    }

    /**
     * Atomically detaches the current queue and returns all of its contents. Swaps the live queue reference for a fresh empty one and resets the size counter
     * in a single {@code getAndSet}, so callers that concurrently call {@link #offer} are immediately directed to the new queue with zero contention. Draining
     * the detached snapshot requires no per-element atomic operations.
     */
    public List<ISymIntervalStats> exportAll() {
        ConcurrentLinkedQueue<ISymIntervalStats> snapshot = queue.getAndSet(new ConcurrentLinkedQueue<>());
        int estimatedCount = approximateSize.getAndSet(0);
        if (estimatedCount < 1 && snapshot.isEmpty()) {
            return Collections.emptyList();
        }
        List<ISymIntervalStats> result = new ArrayList<>(estimatedCount + 1);
        ISymIntervalStats item;
        while ((item = snapshot.poll()) != null) {
            result.add(item);
        }
        return result;
    }
}
