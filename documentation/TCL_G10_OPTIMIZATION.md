# TCL G10 optimization results — 27 October 2026

## Final TV readings after one restart

| About this TV reading | Earlier reading reported by owner | Refreshed after restart |
|---|---:|---:|
| Home button | 219 ms | **90 ms** |
| Available memory | 680 MiB | **782 MiB** |
| Home process memory | Not supplied | **71 MiB** |

These are snapshots from the existing About measurement, not a controlled comparison with Google TV.
Reboot and cache/heap state contribute to memory differences. The factory launcher's historical 150 ms
was not remeasured; no factory-mode switch was performed.

## Controlled measurements and limits

| Phase | Warm Home median | Cold process launch | Navigation median frame | Deadline misses |
|---|---:|---:|---:|---:|
| Original installed APK | 80 ms (7 returns) | 717 ms (3 launches) | 20 ms | 1.99% median of 3 runs |
| Optimized code before compilation/reboot | 63 ms (7 returns) | 860 ms (3 launches) | 21 ms | 1.95% median of 3 runs |
| Compiled build after restart | 64 ms (3 returns) | **Unvalidated** | 21 ms | 1.70% median of 3 runs |

Warm returns improved about 21% in the controlled before-restart code test. Cold-start improvement is
**not established**: the code-only samples were slower, and post-restart attempts raced Android's automatic
Home restart and returned zero durations. Those zero results were rejected, not reported as instant startup.
The 60 fps / 16 ms median-frame target remains unmet. No convincing animation/frame-rate gain is claimed.

The fresh idle snapshot, before further navigation, had 791 MiB available and Home PSS of 61.6 MiB.
Later About measurements include Settings/caches and report 782 / 71 MiB instead.
Pre-reboot full-test PSS rose from 88.0 to 97.3 MiB despite less retained image-buffer work; do not claim
that every process-memory observation improved. Live image-buffer savings and system free RAM differ.

## Kept changes

- Current Vibe theme omits unused portrait recommendation images.
- Only active/fading backgrounds prepare tinted glass copies; old copies are released after fades.
  Preparation stays off the UI thread and is prioritized on the existing image worker.
- Synthetic on-TV comparison proved landscape and glass pixel equality, including release/rebuild,
  with **1,503,488 fewer image-buffer bytes per inactive cached recommendation** (about 1.43 MiB).
  This is a buffer-size result, not a promise of the same immediate PSS reduction.
- Recommendation text truncation is cached; fully offscreen draw work is skipped with shadow margins,
  while ahead-of-navigation artwork preparation is preserved.
- Section offsets are reused during scrolling. App availability is reused within a visit, with package
  change/return invalidation and a tested stale-worker race. Known shelf apps skip an unused resolver call.
- Native ARM code compilation: `cmd package compile -m speed -f io.freemytv`, confirmed as `speed` after
  reboot. Android had reported `verify` after install. This uses rebuildable code cache, changes no
  preferences/history, and resets with `cmd package compile --reset io.freemytv`. Installing a new APK
  may invalidate compilation; repeat the compile step after updates when appropriate.

## Cleanup and recovery

**Disabled, reversibly through HomeMode:** Google feedback (`com.google.android.feedback`), Play Movies
(`com.google.android.videos`), printing (`com.android.printspooler`). Calendar sync stays enabled.
YouTube Music and the separate Samba/logkit/Alexa packages were already disabled and remain so.
These optional extras are support opt-ins, not new default removals for every user.

Selective BI/usage/tracking component disable attempts in `com.tcl.ttvs` were refused by the firmware.
All three component overrides were confirmed at their original default state. Whole TCL packages,
identifiers, updates, inputs, remote functions, security services and network settings were retained.
No claim of eliminating all TCL telemetry is made. Failed component attempts were pruned from the journal;
the final journal contains only the three approved app packages.

Exact default/enabled states are journaled before changes. A successful restore/reapply test restored
feedback/printing to default and Movies to its prior explicit enabled state. Original recovery and
watchdog paths restore the optional cleanup. The private checkpoint holds the original installed APK,
settings/history, ADB identity and Continue Watching artwork; no system app was uninstalled or cleared.

Private recovery directory: `/tmp/vibe-tv-performance-checkpoint-20261027/`, with `RESTORE.md`.
Archive transfer, SHA-256, ZIP integrity and safe paths were verified without overwriting user data.
Full private-data restoration was reserved for recovery, not exercised against live preferences/history.
Both settings and history remained **byte-identical**, including after reboot. No private archive is committed.

## Rejected experiments

Removing animated card hardware layers did not improve frames and increased observed PSS, so it was reverted.
Cached shadows lowered deadline misses but worsened typical frame times to 26 ms, so that was reverted too.
The original rendering/shadow appearance remains.

## Verification and scope

Target: G10 Smart TV Pro, Android 12 / SDK 31, Realtek `rtd6748`, 32-bit ARM, four cores, PowerVR BXE-4-32,
60 Hz display and 1920×1080 app UI. Baseline APK exactly matched the separate `fix/netflix-card-art`
checkout at `9ee35f5`; the audit branch was rebased onto that source to preserve the installed UI.
No main merge/push was performed.

Android build passed with JDK 17; 271 translation keys, webOS/Tizen packages and legacy web checks passed.
Eight audit unit tests, package-cache reuse/invalidation/race checks, on-TV artwork pixel/buffer checks,
and all 19 isolated history regression checks passed. Actual user playback/provider behavior was not
re-investigated, and no full Original-mode switch was performed. Settings and Continue Watching were
visually inspected and their saved data verified unchanged.

Full-resolution local screenshots, all inspected at 1920×1080:
`/tmp/free-my-tv-tv-check/performance-20261027/01-home.png`, `02-continue.png`,
`03-recommendations.png`, `04-settings.png`, `05-widgets.png`, `06-fresh-reboot.png`.
Weather/dock spacing, card focus and title truncation, restored history/progress, Settings rail and widget
values remained present. Recommendations/backgrounds naturally advance on Home visits; weather/time changes
are expected. Captures contain personal viewing history and are not committed.

The single reboot completed with Vibe as default and resumed Home, APP_AUTO_START allowed, `speed`
compilation retained, all three optional apps disabled, calendar enabled and protected TCL packages enabled.
The TV is left on Home. APK SHA-256: `7c00e520ab57f0a28cd4cbd643b9dd50db0c10978ec81ec36f27d50cfd496890`.

Raw numerical results are in [audits](audits/). Trace files and private checkpoints remain local.
