# Settings clarity polish — October 27, 2026

Runtime commit: `b88a229`, built on integrated main `a493c66` (retaining the Settings and playback integration). Branch: `codex/settings-clarity`.

## Changes

- The Settings header and accessibility identity show Vibe TV, with Settings beneath.
- Home Screen → Themes (palette); Home sections → Home Layout (stacked sections); Home Settings → Preferences (sliders).
- Home shortcut → Settings. The All Apps launcher entry shows Vibe TV. Android package, installed app label, signing and APK identifiers stay compatible.
- Preferences' light/dark selector is called Appearance, distinct from Themes.
- Comparison heading and accessibility text use Vibe TV. Historical notices retain the text recorded when they happened.

## Verification

Built once with Java 17, platform android-34 and build-tools 34.0.0; installed successfully over the existing app with the existing signing key. All 271 catalog keys match across 26 languages. Whitespace check passed.

Actual TCL Smart TV Pro / Android 12 screenshots at 1920×1080 were opened and inspected: Vibe TV branding and each new icon/label are visible without clipping, with one focused destination or row. Left rail movement immediately reveals sections; Right enters Home Layout at its first row; Down from Screensaver crosses into Language under Preferences, with the selected rail following. Back returns to the rail and exits. About's comparison identifies Vibe TV. The home shortcut opens Settings with Themes first. HOME default remains io.freemytv/.HomeActivity (isDefault=true). No persistent user values changed. Device released at Home to the coordinated playback chat; no keep-awake process remains.

PIL confirmed capture dimensions and the existing divider. The typography, continuous document geometry, media history, recovery wiring and section preferences are unchanged. Other themes and all translated strings were not individually exercised on hardware in this narrow wording/icon pass. No launcher-mode switch or reboot was performed.

## Capture manifest

All source files are under `/tmp/free-my-tv-tv-check/settings-clarity/`:

1. 01-home.png — updated Settings shortcut; temporary TV mute overlay, not selected for publication.
2. 02-themes.png — opening rail, identity, distinct symbols and thumbnail picker.
3. 03-layout.png — Home Layout, first row focused.
4. 04-screensaver.png — Screensaver followed by Preferences.
5. 05-preferences-crossing.png — Language focused after crossing from Screensaver.
6. 06-widgets.png — Widgets and following Shelf, updated rail.
7. 07-about.png — Vibe TV comparison heading.

Published Settings captures are copied without image edits into `documentation/screenshots/`. See `documentation/SCREENSHOTS.md` for exact mapping. Home/alternative-theme images have their own older provenance and are not claimed as fresh polish captures.
