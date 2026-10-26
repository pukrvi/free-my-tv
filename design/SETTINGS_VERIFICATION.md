# TCL Settings verification — 27 October 2026

Branch: `settings-nav`. Device: TCL Smart TV Pro, Android 12. Reference: 1920×1080.

## Result

Java 17 / Android 34 build succeeded; signed 2.0 MB APK installed and launched over Wi-Fi ADB. Translation check passes all 269 keys across 26 catalogs; git diff --check passes. Playback modules Resume, NowPlaying, Recs, HomeMode, HomeApps and DreamView match the completed playback baseline 9d4473b; its HomeView fallback behavior and manifest process configuration are preserved.

## Device checks

- Theme grid: Vibe TV 1, Simba (Free-My-TV), Lamplight, Google TV last. Active check and focused tile distinguishable. Simba uses upstream 90908dc; its original hero/tabs/dock rendered on device. Lamplight app, action, resume and discovery rows rendered and scrolled on device.
- Vibe-only Home sections appears conditionally. Weather hide removes the section and closes the gap. Long OK lifts, Down reorders below the dock, OK persists; returning Home shows the new order. Up/Down skips informational weather. Restored default order and weather visibility. Back after moving restores the prior order and keeps focus visible.
- Rail movement immediately reveals section headings. Right enters the first row. Right-side Down crosses Screensaver to Language and updates rail selection. Short groups reveal following sections.
- Shelf eleventh row reachable with focus visible. Sleep picker opens and Back cancels without changing Off. About comparison, recommendations and notices reachable; final shorter status inspected.
- Full-resolution images inspected for clipping, label/value separation and a single active focus treatment. PIL confirms 1920×1080; divider sample (42,43,44) contrasts with adjoining surface (18,19,21). Final home weather icons and temperatures remain separated; default weather/dock boundaries restored.
- Final HOME resolver: io.freemytv/.HomeActivity, isDefault=true. Left on Vibe TV 1 with default section order/visibility. Temporary wake process stopped.

Host checks additionally exercised HomeSections persistence, normalization, reset and last-interactive guard. A geometry model checked all 40,320 orders with full and alternating visibility masks; this is supplemental model evidence, not device coverage of every permutation.

## Limits

Google TV recovery implementation is retained; switching system launcher roles was not repeated in this pass. No new reboot/boot-persistence test was performed after these settings changes. TalkBack node paths were reviewed in source; spoken TalkBack behavior and all 26 languages were not hardware-tested. Lamplight ten-minute refresh was checked in source, not by waiting ten minutes. Provider limits remain as documented in ../PLAYBACK_STATUS.md. Theme previews are layout illustrations, not live screenshots.

## Local screenshots

Final series includes the final native move-hint build; images 14–16 were captured after the subsequent recommendation-copy-only update. Earlier series records actual theme selection, hide/reorder and navigation interactions.

### final

- `/tmp/free-my-tv-tv-check/final/01-theme-picker.png`
- `/tmp/free-my-tv-tv-check/final/02-screensaver.png`
- `/tmp/free-my-tv-tv-check/final/03-cross-section.png`
- `/tmp/free-my-tv-tv-check/final/04-widgets.png`
- `/tmp/free-my-tv-tv-check/final/05-long-shelf.png`
- `/tmp/free-my-tv-tv-check/final/06-sleep-picker.png`
- `/tmp/free-my-tv-tv-check/final/07-sleep-cancel.png`
- `/tmp/free-my-tv-tv-check/final/08-about-end.png`
- `/tmp/free-my-tv-tv-check/final/09-lamplight.png`
- `/tmp/free-my-tv-tv-check/final/10-lamplight-settings.png`
- `/tmp/free-my-tv-tv-check/final/11-section-moving.png`
- `/tmp/free-my-tv-tv-check/final/12-section-editor.png`
- `/tmp/free-my-tv-tv-check/final/13-vibe-final.png`
- `/tmp/free-my-tv-tv-check/final/14-final-picker.png`
- `/tmp/free-my-tv-tv-check/final/15-final-about.png`
- `/tmp/free-my-tv-tv-check/final/16-final-home.png`

### themes

- `/tmp/free-my-tv-tv-check/themes/01-vibe-home.png`
- `/tmp/free-my-tv-tv-check/themes/02-theme-picker.png`
- `/tmp/free-my-tv-tv-check/themes/03-vibe-selected.png`
- `/tmp/free-my-tv-tv-check/themes/04-simba-picked.png`
- `/tmp/free-my-tv-tv-check/themes/05-simba-home.png`
- `/tmp/free-my-tv-tv-check/themes/06-simba-focus.png`
- `/tmp/free-my-tv-tv-check/themes/07-simba-settings.png`
- `/tmp/free-my-tv-tv-check/themes/08-lamplight-home.png`
- `/tmp/free-my-tv-tv-check/themes/09-lamplight-discovery.png`
- `/tmp/free-my-tv-tv-check/themes/10-vibe-layout-editor.png`
- `/tmp/free-my-tv-tv-check/themes/11-weather-hidden-editor.png`
- `/tmp/free-my-tv-tv-check/themes/12-vibe-weather-hidden.png`
- `/tmp/free-my-tv-tv-check/themes/13-weather-lifted.png`
- `/tmp/free-my-tv-tv-check/themes/14-weather-restored.png`
- `/tmp/free-my-tv-tv-check/themes/15-weather-moving.png`
- `/tmp/free-my-tv-tv-check/themes/16-vibe-reordered.png`
- `/tmp/free-my-tv-tv-check/themes/17-default-restored.png`
- `/tmp/free-my-tv-tv-check/themes/18-reorder-cancelled.png`

### continuous-settings

- `/tmp/free-my-tv-tv-check/continuous-settings/01-arrival.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/02-screensaver-anchor.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/03-preview-focused.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/04-cross-section-language.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/05-widgets-anchor.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/06-shelf-anchor.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/07-shelf-first.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/08-shelf-beyond-nine.png`
- `/tmp/free-my-tv-tv-check/continuous-settings/09-home-handoff.png`

