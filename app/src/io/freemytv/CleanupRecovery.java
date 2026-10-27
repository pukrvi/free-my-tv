package io.freemytv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Checkpoint/retry rules for optional cleanup, independent of Android and the state-file format. */
final class CleanupRecovery {
    static final class Entry {
        final String target;
        final int original;
        final Object source;
        Entry(String target, int original) { this(target, original, null); }
        Entry(String target, int original, Object source) {
            this.target = target; this.original = original; this.source = source;
        }
    }
    interface Device {
        Integer state(String target) throws Exception; // null: no longer installed; retain its restore record
        boolean disable(String target) throws Exception;
        boolean restore(String target, int original) throws Exception;
    }
    interface Checkpoint {
        void save(List<Entry> journal, Set<String> disabled) throws Exception;
    }
    static final class Result {
        final List<Entry> pending;
        final Set<String> disabled;
        Result(List<Entry> pending, Set<String> disabled) { this.pending = pending; this.disabled = disabled; }
        boolean complete() { return pending.isEmpty(); }
    }

    static boolean disable(String target, List<Entry> journal, Set<String> disabled,
                           Device device, Checkpoint checkpoint) throws Exception {
        Entry saved = null;
        for (Entry entry : journal) {
            if (target.equals(entry.target)) { saved = entry; break; }
        }
        Integer current = device.state(target);
        if (current == null) return false;
        if (saved == null) {
            if (current != 0 && current != 1) return false; // preserve pre-existing owner choices
            saved = new Entry(target, current);
            journal.add(saved);
        }
        if (saved.original != 0 && saved.original != 1) throw new IllegalStateException("Invalid cleanup checkpoint");
        if (!target.contains("/")) disabled.add(target);
        // Also checkpoint retries: the first attempt may have stopped before its checkpoint reached disk.
        checkpoint.save(journal, disabled);
        if (current == 3) return true;
        if (!device.disable(target) || !Integer.valueOf(3).equals(device.state(target))) {
            throw new IllegalStateException("Cleanup refused for " + target);
        }
        return true;
    }

    static Result restore(List<Entry> journal, Set<String> disabled, Predicate<String> allowed, Device device) {
        List<Entry> pending = new ArrayList<>();
        Set<String> remaining = new HashSet<>(disabled);
        for (int i = journal.size() - 1; i >= 0; i--) {
            Entry entry = journal.get(i);
            try {
                if (entry.target == null || !allowed.test(entry.target) || (entry.original != 0 && entry.original != 1)) {
                    pending.add(entry);
                    continue;
                }
                Integer current = device.state(entry.target);
                if (current == null || (current != entry.original &&
                        (!device.restore(entry.target, entry.original) ||
                        !Integer.valueOf(entry.original).equals(device.state(entry.target))))) {
                    pending.add(entry);
                    continue;
                }
                if (!entry.target.contains("/")) remaining.remove(entry.target);
            } catch (Exception failure) {
                pending.add(entry); // an optional failure must never prevent trying the other records
            }
        }
        return new Result(pending, remaining);
    }
}
