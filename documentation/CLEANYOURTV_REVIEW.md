# CleanYourTV review for Vibe TV

Reviewed 27 October 2026. CleanYourTV was downloaded for source inspection only; none of its install,
cleanup, recovery or benchmark scripts was executed. Inspected commit:
[`59b6ac7686a501ec137202d7a8230f9b6550b38b`](https://github.com/Gathin23/cleanyourtv/tree/59b6ac7686a501ec137202d7a8230f9b6550b38b).
Vibe TV implementation baseline: `a493c6677c47a8d0ad002a1c2d9afdfe8cea4fdd`, the local main
revision at the time this audit branch was prepared. The separate dirty `ui/yours` checkout was
also inspected for its existing HomeMode policy; none of its edits was copied or modified.

## Result

The strongest additions are an evidence-based inventory, supported privacy-consent controls, and
repeatable performance measurement. There is no justified universal “disable everything else”
list for TCL. Our current cleanup already targets Samba (`tv.samba.ssm.ui`), TCL log collection,
TCL content/message/demo apps and the Alexa client, while protecting firmware-dependent input,
pairing, settings and system components. That is source coverage, not a claim about current TV state.
See [`HomeMode`](../app/src/io/freemytv/HomeMode.java).

This branch incorporates a dependency-free, read-only support audit and offline tests. It does
**not** change the launcher, Settings, Continue Watching, package-disable policy, security
services, or network configuration. No APK was installed and nothing was launched on the TV.
A follow-up read-only connection on the same day succeeded and confirmed G10 / Android 12.
It also exposed an inventory-parser bug (the special `android` package), now fixed with a regression
test. See the [device findings](TCL_G10_AUDIT.md) and [sanitized snapshot](audits/tcl-g10-2026-10-27.json).
Package states are now verified for that observation; traffic reduction and speed improvements
remain unmeasured. No privacy/consent settings were changed.

## Package comparison

The device runbook calls the user's set “TCL Smart TV Pro, Android 12.” CleanYourTV has a closer
G08 Smart TV Pro Android 12 report, but the follow-up device query identifies the user's unit as
**G10, Android 12 / SDK 31**. Model names alone do not identify boards. Use the G10 Android 12
report for candidate discovery; do not transfer Android 14 or G08 behavior as proof of safety.

| Package or group | Vibe TV today | CleanYourTV evidence / decision |
|---|---|---|
| `tv.samba.ssm.ui` | Already in DEBLOAT | Dedicated Samba ACR candidate. Check installed/disabled status before claiming further improvement. [G10 report](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/examples/tcl-g10-google-tv/debloat.txt) |
| `com.tcl.logkit`, `com.tcl.smartalexa` | Already in DEBLOAT | Existing logging/assistant coverage. Alexa removal also removes a user feature. [G08 report](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/examples/tcl-g08-eu-android12/debloat.txt) |
| `com.google.android.feedback`, `com.android.tv.feedbackconsent` | Not in DEBLOAT | Candidates for an explicit optional-diagnostics policy. Feedback submission/consent is not proof of continuous advertising telemetry. Inspect actual firmware and user choice first. [Privacy catalogue](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/tools/privacy.py) |
| `com.google.android.tv.remotecontrol.logging` | Not in DEBLOAT | Candidate distinct from the functional `com.google.android.tv.remote.service`; the source notes no INTERNET permission of its own. Presence or Bluetooth permission does not prove uploads. Do not disable the real remote service. [Catalogue](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/tools/privacy.py) |
| `com.google.android.apps.tv.netoscope` | Not in DEBLOAT | Google TV network-diagnostics candidate, reported on Chromecast. Do not assume this TCL has it or that removing it speeds up navigation. [Catalogue](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/tools/privacy.py) |
| `com.tcl.tv.plus`, `com.tcl.smartlink.core` | Not in DEBLOAT | TV+ content and bundled installer candidates on a G10 unit. Inspect exact components and use before any optional disable. [G10 report](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/examples/tcl-g10-google-tv/debloat.txt) |
| `com.tcl.ttvs`, `com.tcl.interactive`, `com.tcl.suspension` | Protected | Keep. G08 reports broken source overlays, a dead Source key and repeated binder errors when these dependencies are removed. Another model calling ttvs analytics does not override this. [G08 report](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/examples/tcl-g08-eu-android12/debloat.txt) |
| `com.tcl.guard` | Protected | Keep. Disabling the cleaner caused a missing-service retry every five seconds on one G08 unit; disabling more can increase background work. [G08 report](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/examples/tcl-g08-eu-android12/debloat.txt) |
| TCL WebView, dashboard, gamebar, hearing aid, peripheral updates, casting | Protected in relevant cases | Some comparison lists disable these by firmware or owner choice. Do not copy: these can remove accessibility, app web rendering, gaming, updates or casting. [G08 AU list](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/examples/tcl-g08-au-android12/debloat.txt) |
| `com.google.android.partnersetup` | Not in DEBLOAT | Their privacy catalogue lists attribution. Setup/partner roles and dependencies require deeper examination; excluded from this tool's small actionable candidate set. [Catalogue](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/tools/privacy.py) |

## Privacy beyond package cleanup

1. **Delete advertising ID using the supported Google TV privacy screen**, where available. Reset
   merely replaces the identifier. Do not collect the ID or fake success by writing an undocumented
   settings key. This does not stop every form of tracking or remove streaming-app ads.
2. **Review optional usage/diagnostic consent and the actual Samba service consent.** Samba's
   service-off control stops its collection; limiting ad personalization has narrower scope. Menu
   labels vary. Do not change TCL picture-mode ACR features merely because their name says ACR.
3. **Treat DNS filtering as a separate opt-in feature.** It can filter selected tracking domains,
   but encrypted Private DNS alone is not filtering. Record and restore the exact previous mode
   and hostname, including unset values, rather than blindly restoring “Automatic.” Check router
   filtering/VPN use first: external Private DNS can bypass existing local domain rules. Never
   advertise it as YouTube/Netflix ad removal.
4. **Keep security scanning and updates.** Play Protect, Play services, OTA updaters, permission
   controllers, package installers, WebView and functional remote services are not ad blockers.
   Keep ADB authentication; do not turn debugging off blindly while our recovery/switching relies
   on local ADB. A report of `ro.adb.secure=0` merits an architecture/security discussion, not an
   automatic change to connectivity.

The Google, Android, TCL and Samba primary sources and limits are in
[ANDROID_PRIVACY_SOURCES.md](ANDROID_PRIVACY_SOURCES.md). DNS workflow evidence is in
[CleanYourTV's prompt](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/PROMPT.md).
Its [ad-blocking add-on](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/addons/ad-blocking/README.md)
is a controller for an existing Pi-hole/AdGuard Home server, not a self-contained on-TV blocker.
Copying it would not give an install-only solution. A VPN blocker is possible future work, but
adds a background service and conflicts with another active VPN; not proposed as an invisible change.

## Performance findings that preserve the design

| Technique | Source comparison | Recommended action |
|---|---|---|
| Stop redraw/polling when Home is hidden | CleanYourTV stops animations and timers in `MainActivity.onPause`; ours removes frame callbacks, ticks and listeners in `HomeView.pause` | Already substantially present. Preserve the playback listener's separate Continue Watching lifetime. |
| Cache expensive graphics, avoid per-frame clipping/allocations | Their Android 9 TCL rendering report measures large clipping/gradient/shadow costs; ours already uses recorded layers, bitmap shaders, legacy fallbacks and cached artwork | Keep our renderer. Different board/Android version; their percentages are not transferable to this TCL. |
| Release artwork during real memory pressure | Their launcher detaches tile views and drops caches only when backgrounded under pressure. Our `HomeActivity.onTrimMemory` acts at COMPLETE and `Recs.trim` retains current picks | Worth a separate measured experiment: audit retained bitmap/layer references, and release/rebuild only offscreen data under pressure. Do not clear history or discard the whole cache every Home press. |
| Avoid redundant app-list rebuilds after returning Home | Their `TileRefresh` tracks stopped state/package changes. Ours suppresses rapid Home bursts but ordinarily reloads on resume | Candidate for profiling: dirty flags for package changes/settings and one rebuild on return. Must preserve HDMI/input discovery, launch intent changes, focus and settings updates. No blind port of their RecyclerView architecture. |
| Controlled frame/memory benchmarks | Their benchmark reads gfxinfo/memory, but also injects keys and cold-starts apps | Adopt the measurement discipline. Our audit only reads counters. Active benchmark/reboot/force-stop testing needs a separate approved device session. |

Performance source locations:
[MainActivity](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java),
[TileRefresh](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/launcher/app/src/main/java/com/gathin/tvhome/TileRefresh.java),
[TCL Android 9 measurements](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/docs/devices/tcl-beyondtv2-v660.md),
[benchmark implementation](https://github.com/Gathin23/cleanyourtv/blob/59b6ac7686a501ec137202d7a8230f9b6550b38b/tools/bench.py).
Our comparison locations: `HomeView.resume/pause/reload`, `HomeActivity.onTrimMemory`, `Recording`,
`Recs.loadImage/trim`, `HomeData`. No speed or RAM improvement was measured in this session.

Do not use global animation-scale changes, forced GPU settings, background-process limits, repeated
cache purges, or unconditional force-stops as substitutes for profiling. They can change the look,
cause more decoding/restarts, or interfere with playback capture. Preserve TCL's APP_AUTO_START
permission and Original/Google TV plus watchdog recovery.

## Incorporated on this branch

- [tools/tv_audit.py](../tools/tv_audit.py): fixed read-only ADB queries, exact TCL/Google candidate
  catalogue with pinned evidence, comparison with our existing policy, sanitized memory/frame
  counters, unknown-state handling, and offline snapshot mode. No third-party code or dependencies
  were copied. CleanYourTV is credited for the investigation approach and source evidence.
- [tools/test_tv_audit.py](../tools/test_tv_audit.py): offline checks of command boundaries,
  failure handling, protected dependencies, numeric parsing and omission of raw diagnostics.
- [TV_AUDIT.md](TV_AUDIT.md): usage, scope and acceptance criteria.

Validation: the original seven offline tests passed; the follow-up parser regression brings this to eight passing tests. `tools/check.sh` passed with JDK 17: Android APK build,
269 translation keys, LG and Samsung packaging, ES5 syntax/API checks and legacy CSS checks.
The isolated build used its newly generated example configuration, not the owner's private one;
it is a compile/package check only. No installation, active navigation benchmark, reboot, screenshot,
recovery or packet-capture test was performed. The follow-up read-only device results are recorded separately; they do not establish a successful cleanup or benchmark.

## Suggested next decisions

1. Read-only inventory is complete: [current findings](TCL_G10_AUDIT.md). Rank only the installed
   and enabled candidates; retain this sanitized baseline and keep raw device output private.
2. Agree whether to apply the supported advertising-ID/diagnostics consent controls and which
   optional components the owner wants off. Before a runtime patch, record exact original states
   and extend the existing reversible HomeMode path, with fail-closed protections unchanged.
3. Profile return-to-Home latency and background memory in controlled, repeatable runs. Implement
   only the app-reload/cache change that measurements justify, keeping the same rendered output.
4. Decide separately whether DNS filtering belongs in the product. For an install-only experience,
   system-setting handoff/reachability/rollback and VPN/router compatibility need a design first.

No direct main changes, pushes, merges, releases, network changes or device mutations were performed
by this audit. The review branch is intended for discussion before any runtime or consent changes.
