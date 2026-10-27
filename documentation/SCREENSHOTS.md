# Vibe TV screenshot record

[Project](../README.md) · [Feature register](../FEATURES.md) · [Settings polish evidence](../design/SETTINGS_CLARITY_VERIFICATION.md) · [Earlier Settings evidence](../design/SETTINGS_VERIFICATION.md)

Updated October 27, 2026. These are genuine 1920 × 1080 TCL Android 12 captures, inspected at full resolution. The current Settings captures come from `b88a229` (Vibe TV branding and clearer destinations) built on integrated main `a493c66`, preserving Settings `e740614` and playback `9d4473b`. Home and alternative-theme captures below are earlier device evidence, identified individually. These are source-build captures, not screenshots of a tagged binary release. Theme-picker thumbnails are illustrations; full-screen views are actual device captures.

| Checked-in capture | Original local capture | Provenance / observed state |
|---|---|---|
| [Final home](screenshots/home-playback.png) | `final/16-final-home.png` | Final `e740614` installed build; Vibe TV 1/default order, Netflix app-level card, JioHotstar logo fallback, YouTube/Stremio artwork and progress |
| [Theme picker](screenshots/settings-themes.png) | `settings-clarity/02-themes.png` | Installed `b88a229`; Vibe TV header, Themes palette icon, distinct Home Layout / Preferences destinations |
| [Widgets](screenshots/settings-widgets.png) | `settings-clarity/06-widgets.png` | Installed `b88a229`; updated rail labels, continuous Widgets and following Shelf content |
| [Home Layout](screenshots/settings-sections.png) | `settings-clarity/03-layout.png` | Installed `b88a229`; Home Layout editor, stacked-section icon, first row focused |
| [Lamplight](screenshots/theme-lamplight.png) | `final/09-lamplight.png` | Historical e740614 series before Settings naming polish; independent warm theme with playback row |
| [Simba (Free-My-TV)](screenshots/theme-simba.png) | `themes/05-simba-home.png` | Earlier theme-selection test in the same implementation series; original upstream layout preserved |
| [Preferences](screenshots/settings-preferences.png) | `settings-clarity/05-preferences-crossing.png` | Installed `b88a229`; right-side Down crossed into Language and updated the rail selection |
| [Earlier reboot evidence](screenshots/home-after-reboot.png) | `post-reboot-home.png` | Historical Home + initial playback baseline, before final themes/artwork; does not establish final-build reboot acceptance |

The source captures live under `/tmp/free-my-tv-tv-check/`. Firmware/identity details and final integration checks are recorded in [integration verification](../design/INTEGRATION_VERIFICATION.md). Streaming-app versions were not recorded for the earlier provider tests. City weather and test title metadata remain visible; no network address, account identity or credentials appear in these captures.

## Final home

![Vibe TV 1 final installed home](screenshots/home-playback.png)

## Theme selection

![Final Settings theme picker](screenshots/settings-themes.png)

## Home Layout: ordering and visibility

![Vibe TV 1 Home Layout editor](screenshots/settings-sections.png)

## Preferences

![Preferences after navigating down from Screensaver](screenshots/settings-preferences.png)

Language, temperature, Appearance and navigation sounds live here. Appearance means automatic/light/dark; Themes selects the home design.

## Continuous settings

![Widgets and subsequent Shelf content](screenshots/settings-widgets.png)

## Additional home designs

![Lamplight on the TCL TV](screenshots/theme-lamplight.png)

![Simba original upstream home on the TCL TV](screenshots/theme-simba.png)

## Updating the gallery

After runtime changes, capture an identifiable combined build and record commit, dirty state, build/version, exact model, OS/firmware, date, remote state and toggles. Inspect the whole image and review location, viewing history and account details before publication. Prefer public test content. Keep fallback states visible when they explain a limitation. Update the associated test result rather than inferring playback or recovery success from a screenshot.

`docs/assets/` and `.github/preview.webp` contain inherited upstream demos; they are not current Vibe TV acceptance evidence.
