package io.freemytv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Reuse package queries during active visits, with bounded freshness in every client/process. */
final class AppAvailability {
    private static final long MAX_AGE_MS = 30_000;
    private static final class Entry {
        final long generation, observedAt, request;
        final boolean available;
        Entry(long generation, long observedAt, long request, boolean available) {
            this.generation = generation; this.observedAt = observedAt;
            this.request = request; this.available = available;
        }
    }
    private final Predicate<String> probe;
    private final LongSupplier clock;
    private final long maxAgeMs;
    private final AtomicLong generation = new AtomicLong(), requests = new AtomicLong();
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    AppAvailability(Predicate<String> probe) { this(probe, () -> System.nanoTime() / 1_000_000, MAX_AGE_MS); }
    AppAvailability(Predicate<String> probe, LongSupplier clock, long maxAgeMs) {
        if (maxAgeMs <= 0) throw new IllegalArgumentException("Cache lifetime must be positive");
        this.probe = probe; this.clock = clock; this.maxAgeMs = maxAgeMs;
    }

    boolean available(String pkg) {
        long stamp = generation.get(), now = clock.getAsLong();
        Entry cached = entries.get(pkg);
        if (cached != null && cached.generation == stamp && now >= cached.observedAt &&
                now - cached.observedAt < maxAgeMs) return cached.available;
        long request = requests.incrementAndGet();
        boolean value = probe.test(pkg);
        Entry loaded = new Entry(stamp, now, request, value);
        entries.compute(pkg, (key, previous) -> {
            if (stamp != generation.get()) return previous;
            return previous == null || previous.generation != stamp || previous.request < request ? loaded : previous;
        });
        return value;
    }

    void invalidate() { generation.incrementAndGet(); entries.clear(); }
}
