package io.freemytv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/** Reuse package queries within a Home visit; package callbacks and the next visit invalidate the snapshot. */
final class AppAvailability {
    private static final class Entry {
        final long generation;
        final boolean available;
        Entry(long generation, boolean available) { this.generation = generation; this.available = available; }
    }
    private final Predicate<String> probe;
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    AppAvailability(Predicate<String> probe) { this.probe = probe; }

    boolean available(String pkg) {
        long stamp = generation.get();
        Entry cached = entries.get(pkg);
        if (cached != null && cached.generation == stamp) return cached.available;
        boolean value = probe.test(pkg);
        if (stamp == generation.get()) entries.put(pkg, new Entry(stamp, value));
        return value;
    }

    void invalidate() { generation.incrementAndGet(); entries.clear(); }
}
