# Merge readiness review — 27 October 2026

**Verdict: not ready for main.** Review-only; no source fixes or TV installation were performed in this pass.
Compared `origin/main` at `9ee35f5` with implementation HEAD `2554711` (three audit/optimization commits).
The conversation supplies the spec: preserve Vibe UI, Settings, Continue Watching and reversible recovery;
leave calendar enabled; keep code off main pending one coordinated documentation/code merge.

## Standards

1. **P1 — Optional cleanup can block essential recovery.** `HomeMode.java:303–315` can throw when an
   optional package/component disappears or cannot be restored. `applyOriginal` invokes this at line 592
   before restoring the factory launcher; watchdog invokes it at line 720 before completing setup,
   screensaver and listener recovery. This violates CLAUDE's “Keep the way back working.” Optional failures
   must remain journaled/reported without aborting core recovery; missing targets need explicit handling.
2. **P2 — An interrupted cleanup can falsely report success on retry.** `HomeMode.java:260–264` skips any
   journaled target, but journaling occurs before disabling. An interruption/refusal can therefore leave
   a target enabled permanently while subsequent calls return `CLEANUP_OK`. Reconcile actual state and
   resume planned changes without overwriting the original restoration state.
3. **P2 — Shared app cache has incomplete invalidation.** `Recs.installed` now uses an indefinite cache,
   but only Vibe HomeView invalidates it. Retained Simba/Lamplight clients do not. Changing installed
   streaming apps can leave recommendation eligibility stale until the process restarts. Centralize
   freshness handling or restrict caching to the client that supplies correct lifecycle invalidation.

## Spec

1. **P1 — Recovery guarantee is incomplete.** The user required a restorable checkpoint and reversible
   changes. A failed optional restore must not stop Original or watchdog recovery (same trigger above).
2. **P2 — The current TCL screensaver also misses invalidation.** Android 12's ScreensaverAlone has its
   own main-process Recs singleton. No HomeView callback invalidates that process's cache. After first
   use, installing/disabling a streaming app can leave screensaver recommendations stale indefinitely.
   This affects the target TV, not just other themes. Invalidate at each client/session start or implement
   bounded/shared package-change freshness within Recs.

## Validation

- Clean working tree at review start; no divergence from the fetched main base.
- Full checks passed in an isolated copy of committed source: JDK 17, Android platform 34,
  locally available build-tools 35.0.0. Exact CI build-tools 34.0.0 were unavailable locally.
- Android build, 271 translation keys, LG/Samsung packaging and legacy web checks passed.
- Eight Python audit tests and JVM app-availability reuse/invalidation/concurrency checks passed.
- Existing target-TV optimization/reboot evidence is in TCL_G10_OPTIMIZATION.md; no unsafe failure
  scenario was induced on the owner's TV during review. Passing prior checks does not cover these bugs.
- The review skill's issue-tracker configuration is absent; `/setup-matt-pocock-skills` can add it.
  The supplied conversation was sufficient to review this change without a tracker lookup.

## Documentation handoff

The actual About screenshot is embedded in TCL_G10_OPTIMIZATION.md and stored under
`screenshots/tcl-g10-about-performance.png`. Keep its historical-baseline caveat in README/Wiki copy.
Wiki enablement is acknowledged, but no Wiki/README publication or cross-chat message has been sent.
Prepare final documentation after the three unique correctness issues are fixed and revalidated.
Code and repository documentation can then share one main PR/merge; GitHub Wiki lives in a separate
repository and its publication must be coordinated separately from that atomic code merge.

Standards: 3 findings, worst P1 recovery blocker. Spec: 2 findings, worst P1 recovery blocker.
