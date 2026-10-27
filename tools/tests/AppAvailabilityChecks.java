package io.freemytv;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the production cache without Android or lifecycle callbacks. */
public final class AppAvailabilityChecks {
    private static int passed;
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    private static void freshness(boolean initiallyInstalled) {
        AtomicLong clock = new AtomicLong(100);
        AtomicInteger probes = new AtomicInteger();
        boolean[] installed = {initiallyInstalled};
        AppAvailability cache = new AppAvailability(pkg -> { probes.incrementAndGet(); return installed[0]; }, clock::get, 30);
        check(cache.available("app") == initiallyInstalled, "Initial state");
        installed[0] = !initiallyInstalled;
        clock.set(129);
        check(cache.available("app") == initiallyInstalled && probes.get() == 1, "Reuse before expiry");
        clock.set(130);
        check(cache.available("app") != initiallyInstalled && probes.get() == 2, "Refresh at exact expiry without HomeView callback");
        passed++;
    }
    private static void invalidation(boolean initiallyInstalled) {
        boolean[] installed = {initiallyInstalled};
        AppAvailability cache = new AppAvailability(pkg -> installed[0]);
        check(cache.available("app") == initiallyInstalled, "Initial state");
        installed[0] = !initiallyInstalled;
        cache.invalidate();
        check(cache.available("app") != initiallyInstalled, "Explicit package/session invalidation");
        passed++;
    }
    private static void oldWorker(boolean invalidate) throws Exception {
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger probes = new AtomicInteger();
        AppAvailability cache = new AppAvailability(pkg -> {
            probes.incrementAndGet();
            if (Thread.currentThread().getName().equals("old-probe")) {
                started.countDown();
                try { check(finish.await(5, TimeUnit.SECONDS), "Old probe release timeout"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return false;
            }
            return true;
        }, () -> 100L, 30);
        Thread old = new Thread(() -> {
            try { cache.available("app"); } catch (Throwable e) { failure.set(e); }
        }, "old-probe");
        old.start();
        try {
            check(started.await(5, TimeUnit.SECONDS), "Old probe start timeout");
            if (invalidate) cache.invalidate();
            check(cache.available("app"), "Newer probe must see installation");
        } finally { finish.countDown(); old.join(5000); }
        check(!old.isAlive(), "Old worker stopped");
        check(failure.get() == null, "Old worker failed: " + failure.get());
        check(cache.available("app"), "Old worker must not overwrite newer state");
        check(probes.get() == 2, "Last result must be cached");
        passed++;
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger probes = new AtomicInteger();
        AppAvailability cache = new AppAvailability(pkg -> { probes.incrementAndGet(); return true; }, () -> 100L, 30);
        for (int i = 0; i < 100; i++) check(cache.available("app"), "Installed app missing");
        check(probes.get() == 1, "Artwork callbacks must reuse package lookup");
        passed++;
        freshness(true);
        freshness(false);
        invalidation(true);
        invalidation(false);
        AtomicLong clock = new AtomicLong(100);
        boolean[] installed = {true};
        AppAvailability rollback = new AppAvailability(pkg -> installed[0], clock::get, 30);
        check(rollback.available("app"), "Initial rollback state");
        installed[0] = false;
        clock.set(99);
        check(!rollback.available("app"), "Clock rollback must force refresh");
        passed++;
        oldWorker(true);
        oldWorker(false);
        System.out.println("PASS: AppAvailabilityChecks " + passed + " scenarios");
    }
}
