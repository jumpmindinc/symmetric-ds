package org.jumpmind.symmetric.observability.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.symmetric.observability.models.MetricIntervalStats;

public class MetricIntervalStatsQueue implements Queue<MetricIntervalStats> {
    public final static int MAX_QUEUE_SIZE = 10000000;
    protected static final long serialVersionUID = 1L;
    protected final AtomicInteger approximateSize = new AtomicInteger(0);
    protected volatile ConcurrentLinkedQueue<MetricIntervalStats> queue = new ConcurrentLinkedQueue<>();

    public MetricIntervalStatsQueue() {
        super();
    }

    @Override
    public boolean addAll(Collection<? extends MetricIntervalStats> c) {
        boolean changed = false;
        for (MetricIntervalStats item : c) {
            if (offer(item)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        queue.clear();
        approximateSize.set(0);
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        if (queue.isEmpty()) {
            approximateSize.set(0);
            return true;
        }
        return false;
    }

    @Override
    public Iterator<MetricIntervalStats> iterator() {
        Iterator<MetricIntervalStats> delegate = queue.iterator();
        return new Iterator<MetricIntervalStats>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public MetricIntervalStats next() {
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
    public boolean remove(Object o) {
        boolean removed = queue.remove(o);
        if (removed) {
            approximateSize.decrementAndGet();
        }
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object o : c) {
            if (remove(o)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Iterator<MetricIntervalStats> it = queue.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            MetricIntervalStats item = it.next();
            if (!c.contains(item)) {
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
        return queue.toArray();
    }

    @Override
    public <E> E[] toArray(E[] arg0) {
        return queue.toArray(arg0);
    }

    @Override
    public boolean add(MetricIntervalStats arg0) {
        return offer(arg0);
    }

    @Override
    public MetricIntervalStats element() {
        return queue.element();
    }

    @Override
    public boolean offer(MetricIntervalStats arg0) {
        while (approximateSize.get() >= MAX_QUEUE_SIZE) {
            MetricIntervalStats removed = queue.poll();
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

    @Override
    public MetricIntervalStats peek() {
        return queue.peek();
    }

    @Override
    public MetricIntervalStats poll() {
        MetricIntervalStats item = queue.poll();
        if (item != null) {
            approximateSize.decrementAndGet();
        }
        return item;
    }

    @Override
    public MetricIntervalStats remove() {
        MetricIntervalStats item = queue.remove();
        approximateSize.decrementAndGet();
        return item;
    }

    /**
     * Returns all intervals whose start epoch falls within [start, end] without removing them from the queue.
     */
    public MetricIntervalStats[] peekBetween(long start, long end) {
        List<MetricIntervalStats> result = new ArrayList<>();
        for (MetricIntervalStats item : queue) {
            long ts = item.getStartEpoch();
            if (ts >= start && ts <= end) {
                result.add(item);
            }
        }
        return result.toArray(new MetricIntervalStats[0]);
    }

    /**
     * Removes and returns all intervals whose start epoch falls within [start, end].
     */
    public MetricIntervalStats[] removeAllBetween(long start, long end) {
        List<MetricIntervalStats> result = new ArrayList<>();
        Iterator<MetricIntervalStats> it = iterator();
        while (it.hasNext()) {
            MetricIntervalStats item = it.next();
            long ts = item.getStartEpoch();
            if (ts >= start && ts <= end) {
                it.remove();
                result.add(item);
            }
        }
        return result.toArray(new MetricIntervalStats[0]);
    }

    /**
     * Atomically detaches the current queue and returns all of its contents. Swaps the live queue reference for a fresh empty one and resets the size counter
     * in a single {@code getAndSet}, so callers that concurrently call {@link #offer} are immediately directed to the new queue with zero contention. Draining
     * the detached snapshot requires no per-element atomic operations.
     */
    public List<MetricIntervalStats> exportAll() {
        ConcurrentLinkedQueue<MetricIntervalStats> snapshot = queue;
        queue = new ConcurrentLinkedQueue<>();
        int capacity = approximateSize.getAndSet(0);
        if (capacity == 0 && snapshot.isEmpty()) {
            return Collections.emptyList();
        }
        List<MetricIntervalStats> result = new ArrayList<>(Math.max(capacity, 16));
        MetricIntervalStats item;
        while ((item = snapshot.poll()) != null) {
            result.add(item);
        }
        return result;
    }
}
