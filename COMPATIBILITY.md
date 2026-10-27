# Hardware and app compatibility

[Project](README.md) · [Feature status](FEATURES.md) · [Report your device](.github/ISSUE_TEMPLATE/hardware_report.yml)

Reviewed October 27, 2026. Compatibility belongs to an exact device, firmware, app version and build. A logo on the dock proves neither playback nor resume support. “Not tested” is not a failure or a promise.

## Hardware tested by this fork

| Device | OS / layout | Evidence | Limits |
|---|---|---|---|
| TCL Smart TV Pro; 55Q6CS retail reference; Android reports `Smart TV Pro` | Android 12; firmware display ID `STT2.230203.001 release-keys`; 1920 × 1080 reference | Home/navigation, settings panels, YouTube resume and one reboot returning to Home reported in the Home chat; final themes/section controls and playback/settings captures in the gallery | Streaming-app versions were not recorded in the earlier provider tests; do not generalize to every TCL |

The saved network address and local workstation paths are operational details, not hardware identifiers. Public reports should omit addresses, serial numbers, MAC addresses, account details and credentials.

## Streaming app results

These summarize the TCL playback tests for `9d4473b`, preserved in final Settings build `e740614` and integrated into main. All 19 isolated playback regression cases passed in the playback chat. Theme/navigation checks are recorded in [Settings verification](design/SETTINGS_VERIFICATION.md); a passing launch or synthetic data test does not prove provider playback.

| App | Launch | Title / progress | Artwork | Reopen / resume | Current limitation |
|---|---|---|---|---|---|
| YouTube | Device checked | Title, duration and progress checked | Real video thumbnail captured in the integrated revision | Same public video reopened at saved playback position in baseline test | Direct video opening requires a usable video ID; other sessions can fall back to app opening |
| Stremio | Launch path checked | Episode and progress shown in the integrated capture | Artwork visible in the integrated capture | App reopen path implemented | Exact-title resume not established by an end-to-end public report |
| JioHotstar | Device checked | Episode title and progress/duration observed during playback | Missing; app logo fallback | Reopen checked by playback chat | No artwork, series name or content ID in inspected live session; title-specific resume not proven |
| Netflix | Launch path checked | Live session supplied no title or content ID; user reported capture and reopen failures | Branded app-art fallback checked in `3e382d0`; no verified title artwork | Integrated revision implements “Continue in Netflix” app-level fallback | Exact-title capture/resume remains unresolved; app opening checked; reliable playback restart unresolved |
| Prime Video | Launch path checked | Tests exposed generic “PrimeVideo” metadata; user reported failure | No verified title artwork | Integrated revision implements app-level fallback | Exact-title capture/resume remains unverified; app opening checked; reliable playback restart unresolved |
| TCL Channel and other dock apps | Visible on the test dock | Not tested | Not tested | Not tested | Presence is not evidence of playback integration |

Streaming app versions were not captured in the available test summaries. New reports must include versions to make comparisons meaningful. DRM may hide video screenshots; a black capture alone does not establish playback failure.

## Inherited platform coverage

Upstream documents Android 8+, LG webOS 3+, and Samsung Tizen 2.4+ packaging. This fork has not revalidated the new experience on those platforms. Examples in `CLAUDE.md` (Sony, Xiaomi, onn, Haier, projectors, LG and Samsung) are inherited engineering notes rather than new Vibe TV certifications.

Keep the documented safety exceptions: launcher switching is refused on Amazon Fire TV and Kalley 43G200/R3_GTV. LG and Samsung run an app alongside their factory launcher. Older TVs have browser/GPU constraints and platform-specific UI differences. Consult the [installation guide](INSTALLATION.md) before testing.

## Submit results from your fork

Please open a hardware report in this repository, including unsuccessful tests. Give the exact manufacturer, retail model, reported model code, OS/API, firmware, app versions, commit/build and steps. Report **launch, metadata capture, progress, artwork, reopen, exact-position resume, remote buttons, reboot and Original recovery separately**. Attach sanitized screenshots when useful. You may contribute a documentation-only PR adding a matrix row linked to your report; maintainers review evidence before marking a feature verified.
