package io.freemytv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Failure injection against the same cleanup engine used by HomeMode. No Android stubs. */
public final class CleanupRecoveryChecks {
    private static int passed;
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    private static class Device implements CleanupRecovery.Device {
        final Map<String, Integer> states = new HashMap<>();
        final List<String> attempts = new ArrayList<>();
        boolean refuse, fail;
        Runnable beforeDisable = () -> {};
        public Integer state(String target) throws Exception {
            if (fail && "bad".equals(target)) throw new IOException("Package lookup failed");
            return states.get(target);
        }
        public boolean disable(String target) throws Exception {
            attempts.add("disable:" + target);
            beforeDisable.run();
            if (fail) throw new IOException("Disconnected");
            if (refuse) return false;
            states.put(target, 3);
            return true;
        }
        public boolean restore(String target, int original) throws Exception {
            attempts.add("restore:" + target + ":" + original);
            if ("bad".equals(target)) {
                if (fail) throw new IOException("Restore disconnected");
                if (refuse) return false;
            }
            states.put(target, original);
            return true;
        }
    }
    private static List<CleanupRecovery.Entry> entries(CleanupRecovery.Entry... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
    private static Set<String> disabled(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
    private static void optionalFailure(String mode) {
        Device device = new Device();
        device.states.put("good", 3);
        if (!"missing".equals(mode)) device.states.put("bad", 3);
        device.refuse = "refused".equals(mode);
        device.fail = "lookup-error".equals(mode);
        CleanupRecovery.Entry bad = new CleanupRecovery.Entry("bad", "malformed".equals(mode) ? -1 : 0);
        // Reverse-order restoration encounters the bad record first.
        CleanupRecovery.Result result = CleanupRecovery.restore(entries(new CleanupRecovery.Entry("good", 1), bad),
                disabled("good", "bad"), target -> true, device);
        check(device.states.get("good") == 1, "Optional failure must allow other restoration");
        check(result.pending.size() == 1 && result.pending.get(0) == bad, "Failed original record remains pending");
        check(result.disabled.contains("bad") && !result.disabled.contains("good"), "Only successful restore removed from disabled tracking");
        check(!result.complete(), "Must report incomplete recovery");
        passed++;
    }
    private static void restoreException() {
        Device device = new Device() {
            public boolean restore(String target, int original) throws Exception {
                if ("bad".equals(target)) throw new IOException("ADB refused restore");
                return super.restore(target, original);
            }
        };
        device.states.put("bad", 3); device.states.put("good", 3);
        CleanupRecovery.Result result = CleanupRecovery.restore(entries(new CleanupRecovery.Entry("good", 0), new CleanupRecovery.Entry("bad", 1)),
                disabled("good", "bad"), target -> true, device);
        check(device.states.get("good") == 0 && result.pending.size() == 1, "Restore exception must not abort later targets");
        passed++;
    }
    private static void retry(int original) throws Exception {
        Device device = new Device(); device.states.put("app", original);
        CleanupRecovery.Entry record = new CleanupRecovery.Entry("app", original);
        List<CleanupRecovery.Entry> journal = entries(record);
        Set<String> disabled = disabled("app");
        check(CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> {}), "Interrupted plan must retry");
        check(device.states.get("app") == 3 && device.attempts.size() == 1, "Journal does not prove disable happened");
        check(journal.size() == 1 && journal.get(0).original == original, "Retry must preserve exact original state");
        check(CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> {}), "Already disabled retry succeeds");
        check(device.attempts.size() == 1, "Already disabled target needs no second mutation");
        CleanupRecovery.Result result = CleanupRecovery.restore(journal, disabled, target -> true, device);
        check(result.complete() && device.states.get("app") == original, "Restore exact original 0/1 after retry");
        passed++;
    }
    private static void preDisabled(int state) throws Exception {
        Device device = new Device(); device.states.put("app", state);
        List<CleanupRecovery.Entry> journal = entries(); Set<String> disabled = disabled();
        check(!CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> { throw new AssertionError("No checkpoint for owner's disabled app"); }), "Skip owner's disabled choice");
        CleanupRecovery.Result result = CleanupRecovery.restore(journal, disabled, target -> true, device);
        check(result.complete() && device.states.get("app") == state && device.attempts.isEmpty(), "Do not enable owner-disabled app");
        passed++;
    }
    private static void checkpointBeforeMutation() throws Exception {
        Device device = new Device(); device.states.put("app", 0);
        List<CleanupRecovery.Entry> journal = entries(); Set<String> disabled = disabled();
        boolean[] saved = {false};
        device.beforeDisable = () -> check(saved[0], "Durable checkpoint must precede mutation");
        check(CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> {
            check(j.size() == 1 && j.get(0).original == 0 && d.contains("app"), "Checkpoint contains original and tracking"); saved[0] = true;
        }), "Disable should succeed");
        passed++;
    }
    private static void checkpointFailure() throws Exception {
        Device device = new Device(); device.states.put("app", 1);
        try {
            CleanupRecovery.disable("app", entries(), disabled(), device, (j, d) -> { throw new IOException("Disk full"); });
            throw new AssertionError("Checkpoint failure must surface");
        } catch (IOException expected) { check(device.states.get("app") == 1 && device.attempts.isEmpty(), "Failed checkpoint must never mutate device"); }
        passed++;
    }
    private static void refusedDisableRetry() throws Exception {
        Device device = new Device(); device.states.put("app", 1); device.refuse = true;
        List<CleanupRecovery.Entry> journal = entries(); Set<String> disabled = disabled();
        boolean refused;
        try { refused = !CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> {}); }
        catch (IllegalStateException expected) { refused = true; }
        check(refused && journal.size() == 1 && journal.get(0).original == 1, "Refused disable must keep plan for recovery/retry");
        device.refuse = false;
        check(CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> {}) && device.states.get("app") == 3, "Retry must actually apply refused disable");
        check(journal.size() == 1 && journal.get(0).original == 1, "No duplicate or changed baseline on retry");
        passed++;
    }
    private static void disallowedAndAbsent() throws Exception {
        Device device = new Device(); device.states.put("foreign", 3);
        CleanupRecovery.Result result = CleanupRecovery.restore(entries(new CleanupRecovery.Entry("foreign", 0)), disabled("foreign"), target -> false, device);
        check(!result.complete() && device.attempts.isEmpty() && result.disabled.contains("foreign"), "Disallowed journal target cannot run commands");
        check(!CleanupRecovery.disable("missing", entries(), disabled(), device, (j, d) -> { throw new AssertionError("Absent app checkpoint"); }), "Absent new target skipped");
        passed++;
    }
    private static void malformedTarget() {
        Object rawRecord = new Object();
        CleanupRecovery.Entry malformed = new CleanupRecovery.Entry(null, 0, rawRecord);
        Device device = new Device(); device.states.put("good", 3);
        CleanupRecovery.Result result = CleanupRecovery.restore(entries(new CleanupRecovery.Entry("good", 0), malformed),
                disabled("good"), target -> true, device);
        check(device.states.get("good") == 0 && result.pending.size() == 1, "Malformed record cannot stop other restores");
        check(result.pending.get(0).source == rawRecord, "Malformed checkpoint content must survive for recovery");
        passed++;
    }
    private static void verifyActualState() throws Exception {
        Device device = new Device() {
            public boolean disable(String target) { return true; }
            public boolean restore(String target, int original) { return true; }
        };
        device.states.put("app", 1);
        List<CleanupRecovery.Entry> journal = entries(); Set<String> disabled = disabled();
        try {
            CleanupRecovery.disable("app", journal, disabled, device, (j, d) -> {});
            throw new AssertionError("Success text without changed state must fail");
        } catch (IllegalStateException expected) { check(journal.size() == 1, "Keep refused plan"); }
        device.states.put("app", 3);
        CleanupRecovery.Result result = CleanupRecovery.restore(journal, disabled, target -> true, device);
        check(!result.complete() && result.disabled.contains("app"), "Verify restored state before removing checkpoint");
        passed++;
    }
    public static void main(String[] args) throws Exception {
        optionalFailure("missing"); optionalFailure("refused"); optionalFailure("malformed"); optionalFailure("lookup-error");
        restoreException(); retry(0); retry(1); preDisabled(2); preDisabled(3);
        checkpointBeforeMutation(); checkpointFailure(); refusedDisableRetry(); disallowedAndAbsent();
        malformedTarget(); verifyActualState();
        System.out.println("PASS: CleanupRecoveryChecks " + passed + " scenarios");
    }
}
