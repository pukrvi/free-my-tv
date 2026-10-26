# Free My TV TCL Android TV Design & Build Guide

This is the source of truth for the Android TV home screen and settings UI on the TCL Smart TV Pro running Android 12. It records the user's decisions from the October 2026 redesign; older screenshots and handoff notes may describe superseded layouts. The Android reference is 1920 × 1080, with remote-only navigation and the TV's dark glass visual language. When a later user instruction changes a rule, update this guide and follow the newer instruction. The webOS and Tizen builds remain separate platform implementations.

## Composition and order

Keep the home screen calm, legible from across a room, and consistent with its existing visual language. Use the dark night palette with smoked/frosted surfaces and white text at night, and the existing light palette by day. Avoid adding decorative emphasis that competes with the apps or recommendations.

The final vertical order is:

1. One continuous row of quick actions and TV inputs.
2. The weather widget.
3. The streaming app dock.
4. The Continue Watching row when it has saved items.
5. Recommendation shelves: For You (mixed), Anime, Movies, and TV Shows.

The Android reference canvas is 1920 × 1080, scaled proportionally for the TV. Keep a consistent 40-unit rhythm between the quick-action row, weather, dock, and home rows. The current Android landmarks are actions at y=24–150, weather at y=190–410, dock glass at y=450–656, and the first home row at y=696. When Continue Watching has entries, it occupies that first row and the four recommendation shelves move down one 384-unit row pitch; otherwise For You starts at y=696. Treat these as a responsive reference, not independent coordinates to tweak casually. Preserve the same even vertical spacing between recommendation shelves.

Keep the weather and dock the same width, with matching corner radii. The dock keeps its current smoked, frosted-glass treatment. Preserve adequate safe-area margins and ensure focus growth and shadows do not clip at the screen edge.

## Remote focus and resting state

The resting and startup focus is always the first app in the current dock order (currently YouTube). Pressing Home, starting the TV, or returning from an app resets focus there and closes transient sheets. Do not make the weather widget focusable.

- From the dock, Up goes directly to All Apps in the quick-action row, skipping the display-only weather widget.
- From the quick-action row, Down returns to the first dock app.
- From the dock, Down enters Continue Watching when it is populated, otherwise the first populated recommendation shelf.
- From Continue Watching, Down enters the first populated recommendation shelf and Up returns to the dock.
- From the first recommendation shelf, Up returns to Continue Watching when present, otherwise to the dock.
- Left and Right move within the current actionable row; preserve sensible nearest-column movement between recommendation shelves.

This focus path is intentional even though the weather appears between buttons and dock visually. Keep the selected item unmistakable at TV viewing distance, and preserve remote-only navigation without requiring a pointer.

## Quick actions and inputs

Keep these in one uninterrupted, evenly spaced horizontal row in this order: All Apps, Sleep Timer, Home Settings, TV Settings, Live TV, HDMI 1, HDMI 2, HDMI 3. Home Settings and TV Settings are separate actions. Do not create a large visual gap between shortcut buttons and input buttons.

At rest, all action cards and icons use quiet muted gray, including Live TV. Unconnected HDMI inputs stay subdued. Use the strong highlight only for the focused action; its state must read clearly without making the whole row bright by default.

Sleep Timer opens its picker in place. Darken the backdrop enough to separate the picker and make its text easy to read. Keep the active timer visible as a small pill when the picker closes.

## Weather widget

The widget has two balanced columns: current conditions on the left and forecasts on the right. Give the left content enough vertical room to use the taller widget. The top inset above the city and bottom inset after sunrise/sunset must match.

The right column is split into two equally weighted forecast bands: the next six hours above the divider, and the next six days below it. Do not add headings that consume space. Each forecast item uses two lines: time/day, then the weather icon inline before the temperature. For a day, keep high and low temperatures on that same second line; never put the icon on a separate line.

Treat the center divider as an active edge when measuring vertical padding. The gap from the top edge to the hourly content must equal the gap from the hourly content to the divider's upper edge. Below the divider, the gap from its lower edge to the daily content must equal the gap from the daily content to the card's bottom edge. Keep all four gaps equal, and measure from divider edges rather than its center. Keep the divider visually separate from both text rows.

Preserve clear, consistent spacing within the left column as well. Weather is informational and never receives D-pad focus.

## App dock

The dock matches the weather card's width and corner radius and retains its frosted surface. Size the current six primary app marks generously for a TV, keeping each logo's original aspect ratio. If the configured shelf contains more apps, preserve its existing overflow and scroll behavior instead of squeezing or distorting logos.

The focused app grows modestly and rises above neighboring tiles. Add a slim, high-contrast white outline around the selected app card in dark mode, echoing the recommendation-card focus treatment. The rim should make selection easy to find without becoming a heavy border at rest.

## Recommendations and refresh behavior

Use separate shelves in this order: For You (mixed picks), Anime, Movies, TV Shows. Keep card titles and useful metadata readable. Maintain equal section spacing, stable left-to-right focus, and a clear selected-card treatment.

Hold recommendation choices steady while the home screen remains open. Advance them when the user returns to Home or reaches the ten-minute refresh limit. Image downloads, metadata callbacks, animation frames, and ordinary redraws must not independently rotate the visible picks. Preserve the focused title when a shelf updates if that title remains available.

## Continue Watching

Show this row above recommendations only when it contains saved items. It is opt-in in **Home Settings → Widgets** and
uses the same media-session access as the Now Playing widget. Keep up to twelve recent items: YouTube expires after two
days; other services after ten. Capture playback while other apps are open, before their session is discarded. Display
session artwork first, with a cached exact-title TMDB fallback for movies/shows and app artwork as the last fallback.
Progress bars use a dark backing and a contrasting fill so they remain legible on white YouTube tiles and bright art.
Only show remaining time when a valid duration exists. YouTube can reopen a known video; Netflix, Prime Video, Stremio
and JioHotstar reopen their app and retain that app's own resume behavior. Generic/null metadata from Netflix and Prime gets an explicit “Continue in {app}” card, rather than a fabricated title.
Its action preserves the provider's current task and requests live playback after the foreground transition. App-level
cards never seek to an old saved position because the provider may have changed titles. JioHotstar currently shares
an episode title/progress but no art or series identifier; retain its app logo when no unambiguous artwork is available.

Hold OK/Enter on a Continue Watching card for **Remove from Continue Watching**, or on any recommendation for **Hide
from recommendations**. The menu captures focus, Back/Cancel dismisses it, and releasing the original hold does not
activate a menu action. Removal persists across Home visits and paused-session callbacks. Hiding a recommendation
removes that title from every home shelf without rotating the remaining choices. Keep recommendation headings aligned
with their row's actual layer position, including the offset introduced by Continue Watching.

## Platform consistency and review

The Android home screen is the current reference for this layout and focus model. The webOS/Tizen implementation should preserve the same hierarchy, spacing intent, visual states, and remote behavior while respecting older-browser and TV-GPU limits. Its existing forecast selector and Explore/Movies/Shows tabs are platform-specific legacy structure; do not treat them as the approved Android layout. When the web home is in scope, compare it against this guide and bring differences into alignment deliberately rather than assuming the two implementations already match.

Before calling a home-screen change complete, review the full-screen result at TV scale and exercise the D-pad path, including startup/Home focus, Up from the dock, Down into recommendations, Home return, sleep-timer contrast, and focus visibility. Check that all the equal-spacing relationships above still hold after localization and responsive scaling. For layout changes, prefer a screenshot from the actual target TV when it is available; a desktop preview alone can miss viewing-distance and device-specific rendering issues.

## Settings on the TCL Android TV

Settings uses the same dark smoked-glass cards, quiet white type, and clear white focus treatment as the home screen. Use the full TV canvas: a fixed navigation rail on the left and a wide panel on the right. Scale the layout from the 1920 × 1080 reference rather than shrinking it into a phone-style single column.

The rail is ordered to match the questions people bring to the TV: **Home Screen**, **Screensaver**, **Home Settings**, **Widgets**, **Shelf**, **Top Buttons**, **Sleep Timer**, **Remote Buttons**, **TV Settings**, and **About this TV**. The panel shows the selected section's actual choices and current values. Keep the current setting visible beside each label, give the focused row a strong highlight, and keep helpful remote instructions near the bottom of the panel.

### Remote behavior

- Settings opens with **Home Screen** selected in the rail and its panel already visible. Up and Down in the rail move between sections and immediately update the panel; the first row stays at the top when you enter a section.
- Right or OK moves from the rail into that section's panel. Back or Left returns to the rail with the section still selected; Back from the rail leaves Settings.
- Left and Right change values where that is useful; lists remain explicit when a choice needs more context.
- Keep focus visible, keep TalkBack bounds aligned with the visible rows, and make unavailable TV capabilities explain why they cannot be used.
- In Shelf and Top Buttons, OK adds or removes an item. Hold OK to lift an item, use the arrows to reorder it, OK drops it, and Back restores the previous order.

### Contextual sections

- **Home Screen** presents Free My TV and Original together, shows which mode is active, and keeps the existing switch status, comparison, recommendation status, and notices in the same panel.
- **Screensaver** groups preview, personal photos, and the “watch from the screensaver” option. Photo removal is only shown when there are photos to remove.
- **Home Settings** groups language, temperature, theme, and navigation sounds.
- **Widgets** groups weather and its city, Now Playing, quotes, and the enabled quote currencies. The TCL home card always presents the next six hours and six days together; do not add an hours-versus-days picker here.
- **Shelf** and **Top Buttons** show installed apps in place, with the current order and add/remove state visible.
- **Sleep Timer** exposes the active timer, the on-screen time-left option, and its position only while that pill is enabled.
- **Remote Buttons** shows the buttons available on this TV and the app each one opens. Keep TCL's native button behavior clear.
- **TV Settings** is a direct action into the TV's own Android Settings. Do not mirror picture, sound, account, or network controls in Free My TV.
- **About this TV** shows the app version and the current device model/Android version.

The weather widget itself is informational and is not focusable. Its display already contains both forecast bands, so a settings control for selecting forecast days or hours would duplicate content and contradict the home design.

## Build and device verification for this version

Build the Android app from the `settings-nav` branch with the repository's Java 17 `build.sh` workflow. Install the resulting `FreeMyTV.apk` on the TCL target using Wi-Fi ADB, then launch it as the Home activity. Preserve the configured signing key so an update replaces the existing app instead of forcing an uninstall. Never read, print, or commit `config.properties`; its TMDB key stays private.

The current TCL firmware exposes the background-start operation as `APP_AUTO_START`; it must be allowed for the boot integration to start the launcher. After a reboot, verify that Free My TV resolves as the Home activity and that the installed version is the build just installed. Keep the normal route back to **Original** working.

For exact ADB commands, capture names, screenshot inspection, and the remote verification sequence, follow [ANDROID_TV_TESTING.md](ANDROID_TV_TESTING.md). For a settings build, also inspect the left-to-right pane layout, enter Widgets and confirm there is no forecast-range picker, navigate Shelf/Top Buttons, and verify Back returns to the correct rail section. A build passing compilation does not replace visual inspection on the TV.
