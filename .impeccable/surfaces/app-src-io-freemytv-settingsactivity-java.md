---
version: 1
slug: "app-src-io-freemytv-settingsactivity-java"
primary_target: "app/src/io/freemytv/SettingsActivity.java"
related_targets: []
---

# TCL Settings

Mode: Operate

Target: app/src/io/freemytv/SettingsActivity.java

Build directly in code for this thread, as explicitly requested. Future showcase surfaces retain the user's image-first workflow preference.

Task: browse and change launcher settings with a TV remote from the sofa. Navigation must be predictable, current values obvious, and factory recovery always available through the final Google TV choice (the existing Original mode).

Direction: extend the existing home design into Settings. Neutral charcoal, existing fonts and symbols, restrained white focus. One surface with a fixed left navigation column and a vertical divider. The right side is a single continuous document of naturally sized sections, grouped with headings and spacing. Short sections reveal the next group rather than occupying an empty screen.

Primary interaction: left destinations scroll to section anchors. Enter the right side at its first row; Up/Down crosses section boundaries and the left selection follows. Left/Back returns to the matching navigation item. Back cancels pickers/reordering before leaving.

Home Screen is a thumbnail grid ordered **Vibe TV 1**, **Simba (Free-My-TV)**, **Lamplight**, **Google TV**. Vibe TV 1 is primary. Simba preserves the original upstream home at `90908dc5bce178feb52e9868947cc039c64730fc`; Lamplight has its own independent renderer and adapter. Only Vibe TV 1 exposes Home sections for order/visibility; omit that rail/document section for the other themes. Theme choice and light/dark appearance are separate preferences.

Playback truth: preserve the `9d4473b` baseline and [PLAYBACK_STATUS.md](../../PLAYBACK_STATUS.md). Netflix/Prime app-level cards must not be described as verified exact-title/artwork/automatic resume. Per-theme device evidence is required before claiming the same interaction support across designs.

Finish evidence: actual TCL screenshots at 1920×1080, especially the thumbnail grid, exact upstream Simba composition, independent Lamplight home, Vibe-only Home sections and its reorder cancellation, Screensaver with Home Settings below, focus crossing that boundary, a long Shelf list beyond nine items, and picker cancellation. Compilation alone does not complete visual verification.

Verified on the TCL: theme picker and all three custom renderers; conditional Home sections; hide/reorder/drop/cancel; continuous section crossing; long Shelf list; sleep-picker cancellation; About details and final HOME default. Evidence: /tmp/free-my-tv-tv-check/final/ and /tmp/free-my-tv-tv-check/themes/. The final build includes a shorter, accurate recommendation-status line. Google TV recovery was preserved in code but not activated in this pass.
