package io.freemytv;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppAvailabilityChecks {
    public static void main(String[] args) throws Exception {
        AtomicInteger probes = new AtomicInteger();
        boolean[] installed = {true};
        AppAvailability cache = new AppAvailability(pkg -> { probes.incrementAndGet(); return installed[0]; });
        for (int i = 0; i < 100; i++) if (!cache.available("streaming.app")) throw new AssertionError("Installed app missing");
        if (probes.get() != 1) throw new AssertionError("Repeated artwork callbacks repeated package IPC");
        installed[0] = false; cache.invalidate();
        if (cache.available("streaming.app")) throw new AssertionError("Removal must invalidate positive cache");
        if (cache.available("streaming.app") || probes.get() != 2) throw new AssertionError("Negative cache must be reused");
        installed[0] = true; cache.invalidate();
        if (!cache.available("streaming.app")) throw new AssertionError("Installation must invalidate negative cache");

        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        AppAvailability concurrent = new AppAvailability(pkg -> {
            if (Thread.currentThread().getName().equals("old-probe")) {
                started.countDown();
                try { finish.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                return false;
            }
            return true;
        });
        Thread old = new Thread(() -> concurrent.available("streaming.app"), "old-probe");
        old.start(); started.await(); concurrent.invalidate();
        if (!concurrent.available("streaming.app")) throw new AssertionError("New generation must see installation");
        finish.countDown(); old.join();
        if (!concurrent.available("streaming.app")) throw new AssertionError("Old worker must not overwrite new package state");
        System.out.println("PASS: package query reuse, positive/negative invalidation, stale worker race");
    }
}
