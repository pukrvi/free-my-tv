# Two quick performance fixes — 27 October 2026

The user authorized only these two changes, device testing and a short comparison, with the working build preserved
for rollback and no merge work. Previous working source: `597c9ef`, tag `feedback-stremio-tv-20261027`.
Its signed private APK was verified against the saved SHA-256 before changing the source.

## Changes

1. Reuse the visible-card list and protected artwork sets. Rebuild them only when visible rows, horizontal offsets,
   dimensions, card identities or metadata change. Scrolling frames with the same card membership do no collection
   allocation or metadata scan here. Metadata warming is coalesced onto a separate main-loop callback; image
   protection updates immediately when the viewport changes. Pause cancels the pending callback.
2. Deliver artwork-ready notifications separately from catalog/metadata changes. Vibe redraws only visible rows
   using that image and prepares the active background if needed. Artwork no longer triggers ranking, row layout or
   all-twelve-row invalidation. Rows refresh once when entering the viewport, including artwork that finished while
   they were hidden. Actual background changes still wake the fade animation. Other themes retain the prior listener
   behavior through a default callback.

Visible-image cache protection and thumbnail sizes are unchanged from the working flicker fix. This is still a
feedback build, not the final production checkpoint.

## Validation

- APK build passed. The existing suite passed 36 catalog checks, 52 runtime cases / 251 assertions and the artwork
  stress test (1,350 late loads). The cache test also exercises reuse of a caller-owned protection set.
- Installed on the saved TCL target. Checked horizontal movement past the initial viewport, returning left,
  seven shelves down and seven back up, then repeat navigation on the final build. Opened full-resolution captures.
  Visible pictures remained loaded; idle samples recorded zero artwork decodes.
- No new app crash appeared in the device crash buffer after installation. Historical four-element-array crashes
  precede this build and remain separately documented.

## Quick comparison

The same `tools/tv_profile.py --quick --explicit-home` protocol was used as the preceding feedback-build comparison:
three Settings → Home returns and one fixed navigation run. The first new pass was slower, so it was repeated once.
Both results are retained; no reboot or background-system changes were made.

| Metric | Fresh original TV1 baseline | Previous flicker-fixed build | Two quick fixes, pass 1 | Pass 2 |
|---|---:|---:|---:|---:|
| Median warm return | 72 ms | 74 ms | 99 ms | 79 ms |
| Navigation median frame | 21 ms | 21 ms | 21 ms | 21 ms |
| Navigation 90th percentile | 34 ms | 34 ms | 36 ms | 36 ms |
| Janky/deadline-missed frames | 2.51% | 2.21% | 2.60% (19/732) | 2.35% (17/723) |
| Home process PSS | 66.9 MiB | 83.9 MiB | 113.5 MiB | 90.7 MiB |

The original baseline used seven returns and three navigation runs; all later columns are short samples.
The new passes return 99/100/87 ms and 79/81/66 ms respectively. The reduction in redundant work is implemented,
but these measurements do **not** demonstrate a speed or memory improvement. Median frame time is unchanged,
and warm-return/tail-frame/memory snapshots are somewhat higher. The new test also warmed additional horizontal
artwork. System load, transient allocations and cache warmth were not controlled sufficiently to attribute these
differences. Neither version meets the 16 ms median-frame target. Do not label this a performance win or merge gate pass.

Both APKs and all raw profiles/screenshots are saved in separate private checkpoint folders. The updated build is left
installed for feedback, with the previous working build available for rollback. No main merge, push or production
checkpoint change was performed. The next useful step is controlled profiling of frame work and retained objects,
not additional speculative changes under the remaining usage limit.
