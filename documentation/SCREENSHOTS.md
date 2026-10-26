# Vibe TV screenshot record

[Project](../README.md) · [Feature register](../FEATURES.md) · [Settings evidence](../design/SETTINGS_VERIFICATION.md)

Updated October 27, 2026. These are genuine 1920 × 1080 TCL Android 12 captures, inspected at full resolution. The integrated runtime is identical to Settings commit `e740614`, which preserves playback `9d4473b`. Final captures 14–16 are from that installed build; earlier theme/editor captures use the same UI before a recommendation-status wording correction. They are not screenshots of a tagged binary release. Theme-picker thumbnails are illustrations, while the full-screen views below are actual device captures.

| Checked-in capture | Original local capture | Provenance / observed state |
|---|---|---|
| [Final home](screenshots/home-playback.png) | `final/16-final-home.png` | Final `e740614` installed build; Vibe TV 1/default order, Netflix app-level card, JioHotstar logo fallback, YouTube/Stremio artwork and progress |
| [Theme picker](screenshots/settings-themes.png) | `final/14-final-picker.png` | Final installed build; three custom homes, Google TV last, Vibe selected |
| [Widgets](screenshots/settings-widgets.png) | `final/04-widgets.png` | Same final UI before copy-only update; continuous settings and following Shelf content |
| [Home sections](screenshots/settings-sections.png) | `final/12-section-editor.png` | Same final UI before copy-only update; section order/visibility controls |
| [Lamplight](screenshots/theme-lamplight.png) | `final/09-lamplight.png` | Same final UI before copy-only update; independent warm theme with playback row |
| [Simba (Free-My-TV)](screenshots/theme-simba.png) | `themes/05-simba-home.png` | Earlier theme-selection test in the same implementation series; original upstream layout preserved |
| [Earlier reboot evidence](screenshots/home-after-reboot.png) | `post-reboot-home.png` | Historical Home + initial playback baseline, before final themes/artwork; does not establish final-build reboot acceptance |

The source captures live under `/tmp/free-my-tv-tv-check/`. Firmware/identity details and final integration checks are recorded in [integration verification](../design/INTEGRATION_VERIFICATION.md). Streaming-app versions were not recorded for the earlier provider tests. City weather and test title metadata remain visible; no network address, account identity or credentials appear in these captures.

## Final home

![Vibe TV 1 final installed home](screenshots/home-playback.png)

## Theme selection

![Final Settings theme picker](screenshots/settings-themes.png)

## Section ordering and visibility

![Vibe TV 1 Home sections editor](screenshots/settings-sections.png)

## Continuous settings

![Widgets and subsequent Shelf content](screenshots/settings-widgets.png)

## Additional home designs

![Lamplight on the TCL TV](screenshots/theme-lamplight.png)

![Simba original upstream home on the TCL TV](screenshots/theme-simba.png)

## Updating the gallery

After runtime changes, capture an identifiable combined build and record commit, dirty state, build/version, exact model, OS/firmware, date, remote state and toggles. Inspect the whole image and review location, viewing history and account details before publication. Prefer public test content. Keep fallback states visible when they explain a limitation. Update the associated test result rather than inferring playback or recovery success from a screenshot.

`docs/assets/` and `.github/preview.webp` contain inherited upstream demos; they are not current Vibe TV acceptance evidence.
