package org.jumpmind.symmetric.observability.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ObservationsQueue<T extends ISymObservation> implements Queue<T>
{
    public final static int MAX_QUEUE_SIZE = 10000000;
    protected static final long serialVersionUID = 1L;
    protected final AtomicInteger approximateSize = new AtomicInteger(0);
    protected ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<T>();

    public ObservationsQueue() {
        super( );
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean changed = false;
        for (T item : c) {
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
    public Iterator<T> iterator() {
        Iterator<T> delegate = queue.iterator();
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
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
        Iterator<T> it = queue.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            T item = it.next();
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
    public boolean add(T arg0) {
        return offer(arg0);
    }

    @Override
    public T element() {
        return queue.element();
    }

    @Override
    public boolean offer(T arg0) {
        while (approximateSize.get() >= MAX_QUEUE_SIZE) {
            T removed = queue.poll();
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
    public T peek() {
        return queue.peek();
    }

    @Override
    public T poll() {
        T item = queue.poll();
        if (item != null) {
            approximateSize.decrementAndGet();
        }
        return item;
    }

    @Override
    public T remove() {
        T item = queue.remove();
        approximateSize.decrementAndGet();
        return item;
    }

    /**
     * Returns all observations whose timestamp falls within [start, end] without removing them from the queue.
     */
    @SuppressWarnings("unchecked")
    public T[] peekBetween(long start, long end) {
        List<T> result = new ArrayList<>();
        for (T item : queue) {
            long ts = item.getTimestamp();
            if (ts >= start && ts <= end) {
                result.add(item);
            }
        }
        return (T[]) result.toArray(new ISymObservation[0]);
    }

    /**
     * Removes and returns all observations whose timestamp falls within [start, end].
     */
    @SuppressWarnings("unchecked")
    public T[] removeAllBetween(long start, long end) {
        List<T> result = new ArrayList<>();
        Iterator<T> it = iterator();
        while (it.hasNext()) {
            T item = it.next();
            long ts = item.getTimestamp();
            if (ts >= start && ts <= end) {
                it.remove();
                result.add(item);
            }
        }
        return (T[]) result.toArray(new ISymObservation[0]);
    }
}
