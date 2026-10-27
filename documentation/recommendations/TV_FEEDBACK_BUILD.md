# Installed TV feedback build — 27 October 2026

Status: installed for user testing on Vibe TV 1; not the final production checkpoint and not merged to main.
The subsequent two requested quick fixes and both comparison passes are recorded in
[QUICK_PERFORMANCE_FIXES.md](QUICK_PERFORMANCE_FIXES.md); the original build below remains preserved for rollback.
The source starts at optimized TV1 plus Continue Watching checkpoint `197db6e`. The pre-change signed APK and
fresh baseline profile are preserved privately. The historical stable tag is unchanged.

## Artwork flicker

The user's five-second recording showed fixed titles with pictures repeatedly disappearing and reappearing.
A six-second device log sample recorded 29 artwork decodes for 26 distinct pictures: three repeated decodes.
The old insertion-ordered cache could evict visible artwork when more visible/overscan rows requested images.

The installed fix:

- Pins visible artwork and the active home background. Access ordering keeps recently viewed cards available.
  Unprotected entries are evicted above 24; a larger actual viewport stays protected until it leaves the screen.
- Cancels queued decode requests for rows that have left the viewport.
- Uses 480×270 Vibe landscape cards instead of 960×540, reducing each retained card bitmap by 75%.
  Other designs retain their previous card sizes. Vibe cards display at 420×236 before focus scaling.
- Moves catalog/metadata enrichment to a separate worker so cached artwork decoding does not wait behind it.
  Image jobs capture immutable artwork keys; metadata updates cannot change the key midway through a decode.

The cache regression test first failed with `Visible artwork evicted: visible-0`, then passed after the fix:
26 protected images remain stable across 1,350 late loads, old rows are released, and eviction is access ordered.
The recommendation suite passed 36 catalog checks and 52 runtime cases / 251 assertions; the APK built successfully.

Device verification: traversed from New Movies to Must-Watch TV and back, inspecting 1920×1080 screenshots.
Eight seconds at rest and six seconds after returning each produced zero image decodes. Each pair of screenshots
was pixel-identical. The user independently confirmed the issue no longer occurs. No new app crash appeared after
this install; earlier crash records belong to the already repaired four-element row-text array.
The device screen recorder produced an empty file, so no successful playback/video verification is claimed.

## Quick performance comparison

Same existing fixed D-pad sequence and Settings → Home return. Baseline: full run immediately before integration
(seven returns, three navigation runs). Current: quick run (three returns, one navigation run), no reboot.
The current profiler explicitly addresses Vibe Home with the same MAIN/HOME intent because recovery left Google TV
as the default launcher; this does not change the TV's launcher setting.

| Metric | Fresh pre-integration baseline | Fixed recommendation build |
|---|---:|---:|
| Median warm Home return | 72 ms | 74 ms |
| Median navigation frame | 21 ms | 21 ms |
| Navigation 90th percentile | 34 ms (median across runs) | 34 ms |
| Janky/deadline-missed frames | 2.51% (median) | 2.21% (16 / 725 frames) |
| Home process PSS | 66.9 MiB | 83.9 MiB |
| System available memory | 477.8 MiB | 573.3 MiB |

Current warm samples: 93, 74, 74 ms. Baseline navigation jank ranged 2.38–2.78%; current 2.21% is a single sample,
not proof of improvement. Memory snapshots reflect different warmed content and system conditions. The fixed build
is close to this fresh baseline for responsiveness, with roughly 17 MiB more process memory. The earlier controlled
post-reboot 64 ms / 21 ms / 1.7% result remains a separate historical measurement. Neither build meets the 16 ms
median-frame goal. A full equal-protocol run is still required before production acceptance.

## Small performance follow-ups, in order

1. Reuse the visible-card list and protected-key set until the viewport or artwork changes. `warmVisible()` currently
   runs during scroll animations and repeats collection allocation and metadata scans on every animation frame.
   Keep metadata warming outside that frame loop while updating image protection when row/card boundaries change.
2. Invalidate only affected visible recommendation layers on artwork/metadata callbacks. Keep offscreen layers
   dormant; avoid rebuilding all twelve rows or gradients when titles, geometry and row membership are unchanged.
3. Profile retained objects before changing limits further: compact duplicate catalog metadata and release old
   background preparation/neighbor artwork under memory pressure, preserving the visible-image protection.
   Do not regain memory by recreating the flicker bug.

Measure each independently with the same profiler, then run the full comparison. No further speculative runtime
changes were included after this working feedback build because the user prioritized keeping it usable.

## Remaining production work

Complete native/Stremio exact-title opening and refresh/focus acceptance, full checks/localization, integration of
the latest main documentation and final Settings rail change, independent review and equal-protocol performance
comparison. Public-addon requests are catalog metadata only, not an arbitrary plugin installer or stream resolver.
Keep the production merge gate open. To restore Vibe as the Home-button destination after watchdog recovery, the
user chooses Themes → Vibe TV 1 on the TV; do not change the launcher mode remotely.
