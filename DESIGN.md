# Vibe TV TCL Android TV Design & Build Guide

This is the source of truth for the Android TV home screen and settings UI on the TCL Smart TV Pro running Android 12. It records the user's decisions from the October 2026 redesign; older screenshots and handoff notes may describe superseded layouts. The Android reference is 1920 × 1080, with remote-only navigation and the TV's dark glass visual language. When a later user instruction changes a rule, update this guide and follow the newer instruction. The webOS and Tizen builds remain separate platform implementations.

## Home themes

The public project is Vibe TV; Free My TV remains the upstream attribution and current runtime label. Home Screen presents thumbnail choices in this order:

1. **Vibe TV 1** — the primary theme and the user's current home design. Its section composition is customizable.
2. **Simba (Free-My-TV)** — the exact original upstream home design from commit `90908dc5bce178feb52e9868947cc039c64730fc`, preserved in `RepositoryHomeView.java` with only integration required to select and run it. Preserve its Explore/Movies/Shows tabs, hero, dock, drawer and original navigation; do not reinterpret it as another version of Vibe TV 1.
3. **Lamplight** — an independent extra theme using `ImpeccableHomeRenderer.java` and `LamplightHomeView.java`. Its composition and palette are separate from Vibe TV 1 and Simba.
4. **Google TV** — the factory launcher, shown last as the recovery path. Selecting it uses the existing Original mode, including its restoration and watchdog behavior.

Choosing among the first three designs changes the custom renderer without discarding saved settings or playback history. The appearance setting in Home Settings controls light/dark behavior independently. The thumbnail grid uses Left/Right within a row and Up/Down between rows; a single focused tile is distinct from the check marking the active choice.

The following composition and home focus rules describe **Vibe TV 1's default order**. They do not replace Simba's upstream behavior or Lamplight's independent composition.

## Vibe TV 1 composition and order

Keep the home screen calm, legible from across a room, and consistent with its existing visual language. Use the dark night palette with smoked/frosted surfaces and white text at night, and the existing light palette by day. Avoid adding decorative emphasis that competes with the apps or recommendations.

The default vertical order is:

1. One continuous row of quick actions and TV inputs.
2. The weather widget.
3. The streaming app dock.
4. The Continue Watching row when it has saved items.
5. Recommendation shelves: For You (mixed), Anime, Movies, and TV Shows.

The Android reference canvas is 1920 × 1080, scaled proportionally for the TV. Keep a consistent 40-unit rhythm between the quick-action row, weather, dock, and home rows. Default landmarks are actions at y=24–150, weather at y=190–410, dock glass at y=450–656, and the first home row at y=696. Continue Watching adds its natural row height only while it contains saved items. When the user hides or reorders sections, derive positions from the saved order and each visible section's height rather than retaining empty slots. Preserve even spacing between visible recommendation shelves.

**Home sections** appears only while Vibe TV 1 is selected. It lists Top Buttons, Weather widget, Shelf, Continue Watching, For You, Anime, Movies and TV Shows in their saved order. OK toggles visibility; hold OK to lift, arrows to reorder, OK to drop, and Back to restore the previous order. Reset layout restores default order and visibility after confirmation. Keep a navigable recovery route when all populated content sections would otherwise disappear. Section composition does not change Shelf app membership or Top Buttons membership.

Keep the weather and dock the same width, with matching corner radii. The dock keeps its current smoked, frosted-glass treatment. Preserve adequate safe-area margins and ensure focus growth and shadows do not clip at the screen edge.

## Remote focus and resting state

The resting and startup focus is the first app in the current dock order (currently YouTube) when the dock is visible and populated. If it is hidden or empty, choose the first visible populated actionable section in the saved order. Pressing Home, starting the TV, or returning from an app closes transient sheets and restores that entry focus. Do not make the weather widget focusable.

- From the dock, Up goes directly to All Apps in the quick-action row, skipping the display-only weather widget.
- From the quick-action row, Down returns to the first dock app.
- From the dock, Down enters Continue Watching when it is populated, otherwise the first populated recommendation shelf.
- From Continue Watching, Down enters the first populated recommendation shelf and Up returns to the dock.
- From the first recommendation shelf, Up returns to Continue Watching when present, otherwise to the dock.
- Left and Right move within the current actionable row; preserve sensible nearest-column movement between recommendation shelves.

These transitions describe the default order. With a customized order, Up/Down follows visible populated actionable sections in that order and skips informational weather. Keep the selected item unmistakable at TV viewing distance and preserve remote-only navigation without requiring a pointer.

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

In Vibe TV 1's default order, show this row above recommendations only when it contains saved items. It is opt-in in **Home Settings → Widgets** and shares media-session access with Now Playing. Keep at most twelve entries; YouTube expires after two days and other services after ten. The listener captures playback while other apps are in front. Prefer session artwork, then an exact-title TMDB fallback for movies/shows, then app artwork. Only show progress and remaining time when valid duration data exists, and keep the progress fill contrasting against a dark backing.

YouTube title/artwork/progress and Stremio title/artwork/progress are verified on this TCL. A known YouTube video ID can open the video directly; Stremio reopens its own saved playback. Netflix's observed session has no useful metadata, so show **Continue in Netflix** with an app tile. Prime Video similarly uses an app-level fallback. Their app launches do not establish exact-title capture, thumbnails or reliable automatic playback. JioHotstar title/progress are verified, but its session has no unambiguous thumbnail or series identifier. Preserve the app-logo fallback. Provider facts and remaining limitations are recorded in [PLAYBACK_STATUS.md](PLAYBACK_STATUS.md), based on the `9d4473b` playback baseline.

Hold OK on a resume card for **Remove from Continue Watching**, or on a recommendation for **Hide from recommendations**. Back/Cancel dismisses the menu and the original held-key release must not activate an action. Removal persists across visits and paused-session callbacks; hiding applies across recommendation shelves without rotating the remaining choices. Preserve this tested Vibe TV 1 behavior during theme integration; record per-theme checks before claiming equivalent support elsewhere.

## Platform consistency and review

The Android home screen is the current reference for this layout and focus model. The webOS/Tizen implementation should preserve the same hierarchy, spacing intent, visual states, and remote behavior while respecting older-browser and TV-GPU limits. Its existing forecast selector and Explore/Movies/Shows tabs are platform-specific legacy structure; do not treat them as the approved Android layout. When the web home is in scope, compare it against this guide and bring differences into alignment deliberately rather than assuming the two implementations already match.

Before calling a home-screen change complete, review the full-screen result at TV scale and exercise the D-pad path, including startup/Home focus, Up from the dock, Down into recommendations, Home return, sleep-timer contrast, and focus visibility. Check that all the equal-spacing relationships above still hold after localization and responsive scaling. For layout changes, prefer a screenshot from the actual target TV when it is available; a desktop preview alone can miss viewing-distance and device-specific rendering issues.

## Settings on the TCL Android TV

Settings shares the home screen’s quiet neutral charcoal, existing font and icon families, and unmistakable white focus. Use one full-screen surface with a fixed navigation column and a single subtle vertical divider. Do not put either column in a full-height rounded card. Keep a TV-safe outer margin and enough room for focused rows to grow without clipping.

The right column is one continuous document. Sections have their natural height, grouped by headings, compact rows, current values, and a deliberate gap before the next heading. A short Screensaver section reveals Home Settings below it. No section owns an artificial viewport or a nine-row limit; long app lists scroll with the rest of the document. Use bounded pickers only for choosing a value, such as a language or sleep duration.

The navigation order is **Home Screen**, **Home sections** (Vibe TV 1 only), **Screensaver**, **Home Settings**, **Widgets**, **Shelf**, **Top Buttons**, **Sleep Timer**, **Remote Buttons**, **TV Settings**, and **About this TV**. Omit Home sections from both the rail and document for Simba and Lamplight, and recompute anchor positions without leaving a gap.

### Remote behavior

- Settings opens at the beginning with Home Screen selected in the left column.
- Up and Down on the left jump the document to that section’s heading. The right content is already visible; OK is not required to reveal it.
- Right or OK enters the section at its first row. Up and Down in the right column move through every row and across section boundaries, keeping the focused item visible and updating the selected left destination. In the Home Screen thumbnail grid, Up/Down moves by grid row and Left/Right moves between its two columns.
- Left or Back from the document returns to the matching left destination. Back from the left exits. OK activates a control; Right may advance a value. Returning to the navigation never changes a value.
- Back cancels a value picker before returning to navigation. In reorder mode, Back restores the previous order and keeps the restored item visible; OK drops the item. Hold OK lifts a Home sections row or an existing Shelf or Top Buttons item, then arrows move it.
- Smooth scrolling is brief and interruptible, with no entrance choreography. Preserve stable section-and-row TalkBack identities, visible bounds, and logical document order.

### Contextual sections

- **Home Screen** uses a thumbnail picker ordered Vibe TV 1, Simba (Free-My-TV), Lamplight and Google TV. Vibe TV 1 is the primary choice, Simba preserves the original upstream home, and Google TV remains last as the factory recovery choice. Show active selection and switching/error feedback without duplicating diagnostics.
- **Home sections** is Vibe TV 1's order/visibility editor, with a confirmed Reset layout action. It is absent for other custom themes.
- **Screensaver** groups preview, personal photos, and watching from the screensaver. Only show photo removal when photos exist.
- **Home Settings** groups language, temperature, theme, and navigation sounds.
- **Widgets** groups weather/city, Now Playing, Continue Watching, and quotes. Show individual coin controls only when Quotes is enabled. The home weather card already shows six hours and six days together; do not add a forecast-range selector.
- **Shelf** and **Top Buttons** show the current order and membership. Long lists have no arbitrary display cap.
- **Sleep Timer** groups duration and the time-left display; its position appears only when that display is enabled.
- **Remote Buttons** shows this TV’s supported app buttons and assignments.
- **TV Settings** opens Android Settings. Do not duplicate system picture, sound, account, or network controls.
- **About this TV** shows version/model, the existing launcher comparison, recommendation status/credits, and notices. Read-only information remains reachable by remote and TalkBack.

The weather widget remains informational and does not receive home-screen focus.

## Build and device verification for this version

Build the Android app from the `settings-nav` branch with the repository's Java 17 `build.sh` workflow. Install the resulting `FreeMyTV.apk` on the TCL target using Wi-Fi ADB, then launch it as the Home activity. Preserve the configured signing key so an update replaces the existing app instead of forcing an uninstall. Never read, print, or commit `config.properties`; its TMDB key stays private.

The current TCL firmware exposes the background-start operation as `APP_AUTO_START`; it must be allowed for the boot integration to start the launcher. After a reboot, verify that Free My TV resolves as the Home activity and that the installed version is the build just installed. Keep **Home Screen → Google TV** available as the UI route to Original recovery mode.

For exact ADB commands, capture names, screenshot inspection, and the remote verification sequence, follow [ANDROID_TV_TESTING.md](ANDROID_TV_TESTING.md). For a settings build, also inspect the divider and continuous document, jump to Screensaver and confirm Home Settings follows below, cross section boundaries from the right column, inspect long Shelf lists, and verify Back/cancel behavior. A build passing compilation does not replace visual inspection on the TV.
