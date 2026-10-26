# Free My TV Home Screen Design Guide

This is the source of truth for the home screen's layout and interaction preferences. Read it before changing the Android TV home screen or its webOS/Tizen counterpart. It records the user's final decisions from the October 2026 redesign; older screenshots and handoff notes may describe superseded layouts. When a later user instruction changes a rule, update this guide and follow the newer instruction.

## Composition and order

Keep the home screen calm, legible from across a room, and consistent with its existing visual language. Use the dark night palette with smoked/frosted surfaces and white text at night, and the existing light palette by day. Avoid adding decorative emphasis that competes with the apps or recommendations.

The final vertical order is:

1. One continuous row of quick actions and TV inputs.
2. The weather widget.
3. The streaming app dock.
4. Recommendation shelves: For You (mixed), Anime, Movies, and TV Shows.

The Android reference canvas is 1920 × 1080, scaled proportionally for the TV. Keep a consistent 40-unit rhythm between the quick-action row, weather, dock, and recommendation sections. The current Android landmarks are actions at y=24–150, weather at y=190–410, dock glass at y=450–656, and recommendations starting at y=696. Treat these as a responsive reference, not independent coordinates to tweak casually. Preserve the same even vertical spacing between recommendation shelves.

Keep the weather and dock the same width, with matching corner radii. The dock keeps its current smoked, frosted-glass treatment. Preserve adequate safe-area margins and ensure focus growth and shadows do not clip at the screen edge.

## Remote focus and resting state

The resting and startup focus is always the first app in the current dock order (currently YouTube). Pressing Home, starting the TV, or returning from an app resets focus there and closes transient sheets. Do not make the weather widget focusable.

- From the dock, Up goes directly to All Apps in the quick-action row, skipping the display-only weather widget.
- From the quick-action row, Down returns to the first dock app.
- From the dock, Down enters the first populated recommendation shelf.
- From the first recommendation shelf, Up returns to the dock.
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

## Platform consistency and review

The Android home screen is the current reference for this layout and focus model. The webOS/Tizen implementation should preserve the same hierarchy, spacing intent, visual states, and remote behavior while respecting older-browser and TV-GPU limits. Its existing forecast selector and Explore/Movies/Shows tabs are platform-specific legacy structure; do not treat them as the approved Android layout. When the web home is in scope, compare it against this guide and bring differences into alignment deliberately rather than assuming the two implementations already match.

Before calling a home-screen change complete, review the full-screen result at TV scale and exercise the D-pad path, including startup/Home focus, Up from the dock, Down into recommendations, Home return, sleep-timer contrast, and focus visibility. Check that all the equal-spacing relationships above still hold after localization and responsive scaling. For layout changes, prefer a screenshot from the actual target TV when it is available; a desktop preview alone can miss viewing-distance and device-specific rendering issues.
