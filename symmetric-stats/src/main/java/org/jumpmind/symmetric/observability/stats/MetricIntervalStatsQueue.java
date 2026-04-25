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
    public static final int MAX_QUEUE_SIZE = 10000000;
    protected static final long serialVersionUID = 1L;
    protected final AtomicInteger approximateSize = new AtomicInteger(0);
    protected final AtomicReference<ConcurrentLinkedQueue<ISymIntervalStats>> queue = new AtomicReference<>(new ConcurrentLinkedQueue<>());

    public MetricIntervalStatsQueue() {
        super();
    }

    private ConcurrentLinkedQueue<ISymIntervalStats> q() {
        return queue.get();
    }

    @Override
    public boolean addAll(Collection<? extends ISymIntervalStats> c) {
        boolean changed = false;
        for (ISymIntervalStats item : c) {
            if (offer(item)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        q().clear();
        approximateSize.set(0);
    }

    @Override
    public boolean contains(Object o) {
        return q().contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return q().containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        if (q().isEmpty()) {
            approximateSize.set(0);
            return true;
        }
        return false;
    }

    @Override
    public Iterator<ISymIntervalStats> iterator() {
        Iterator<ISymIntervalStats> delegate = q().iterator();
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
    public boolean remove(Object o) {
        boolean removed = q().remove(o);
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
        Iterator<ISymIntervalStats> it = q().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            ISymIntervalStats item = it.next();
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
        return q().toArray();
    }

    @Override
    public <E> E[] toArray(E[] arg0) {
        return q().toArray(arg0);
    }

    @Override
    public boolean add(ISymIntervalStats arg0) {
        return offer(arg0);
    }

    @Override
    public ISymIntervalStats element() {
        return q().element();
    }

    @Override
    public boolean offer(ISymIntervalStats arg0) {
        while (approximateSize.get() >= MAX_QUEUE_SIZE) {
            ISymIntervalStats removed = q().poll();
            if (removed != null) {
                approximateSize.decrementAndGet();
            } else {
                break;
            }
        }
        boolean added = q().offer(arg0);
        if (added) {
            approximateSize.incrementAndGet();
        }
        return added;
    }

    @Override
    public ISymIntervalStats peek() {
        return q().peek();
    }

    @Override
    public ISymIntervalStats poll() {
        ISymIntervalStats item = q().poll();
        if (item != null) {
            approximateSize.decrementAndGet();
        }
        return item;
    }

    @Override
    public ISymIntervalStats remove() {
        ISymIntervalStats item = q().remove();
        approximateSize.decrementAndGet();
        return item;
    }

    /**
     * Returns all intervals whose start epoch falls within [start, end] without removing them from the queue.
     */
    public ISymIntervalStats[] peekBetween(long start, long end) {
        List<ISymIntervalStats> result = new ArrayList<>();
        for (ISymIntervalStats item : q()) {
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
