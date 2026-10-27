# Feature register

[Project](README.md) · [Compatibility](COMPATIBILITY.md) · [Contributing](CONTRIBUTING.md)

Updated October 27, 2026 after combining the three chats into `main`. Home baseline `b920f4a`, playback `9d4473b` and Settings `e740614` are preserved in merge history. The integrated runtime matches the final device-tested Settings source exactly; the playback regression tools are also retained. This is a source integration, not a tagged binary release.

**Device checked** means only the physical-TV paths described below. **Inherited** means upstream behavior without new fork verification. A passing build alone does not establish device support.

| Area | Status and scope | Implementation | Limits / next work |
|---|---|---|---|
| Vibe TV 1 home | Device checked: actions, weather, dock, optional Continue Watching, four recommendation shelves | `app/src/io/freemytv/HomeView.java`, `HomeApps.java`, `Recs.java` | More devices/resolutions and spoken accessibility checks |
| Weather and focus | Device checked: six hours/six days together, legible temperatures, visible D-pad focus | `HomeView.java`, `HomeData.java` | Long translations and broader hardware coverage |
| Continuous Settings | Device checked: rail anchors, cross-section navigation, 11th Shelf item, sleep cancel, reachable About content | `SettingsActivity.java`, `Prefs.java` | All 26 languages and spoken TalkBack not hardware tested |
| Theme picker | Vibe TV 1, Simba (Free-My-TV), Lamplight selected/rendered on TCL; Google TV last | `HomeActivity.java`, `HomeSurface.java`, `HomeThemeThumbs.java`, `RepositoryHomeView.java`, `LamplightHomeView.java`, `ImpeccableHomeRenderer.java` | Factory-mode switch not activated in the final pass; previews are illustrations |
| Vibe TV 1 section editor | Device checked: hide closes gaps, lift/reorder/drop persists, Back restores order/focus; defaults restored | `HomeSections.java`, `HomeView.java`, `SettingsActivity.java` | Conditional on Vibe TV 1; exhaustive order model is not exhaustive hardware coverage |
| Continue Watching | YouTube/Stremio artwork and progress; title/progress and app-level resume limits documented per service | `Resume.java`, `NowPlaying.java`, `HomeView.java` | Netflix/Prime exact-title capture/resume and JioHotstar artwork unresolved |
| Card controls | Device checked: long OK removes history or hides recommendations across shelves; cancel/release suppression | `HomeView.java`, `Resume.java`, `Recs.java` | Other custom themes do not automatically inherit Vibe's card-control verification |
| History retention | 2 days YouTube, 10 days other services, at most 12 entries; dismissed/expired paused sessions stay removed | `Resume.java`; `tools/tests/HistoryChecks.java` | Metadata depends on streaming apps |
| Screensaver, photos, remote buttons | Inherited with shared playback integration | `DreamView.java`, `Screensaver.java`, `ScreensaverAlone.java`, `RemoteKeys.java`, `RemoteKeyService.java` | Validate per firmware; preserve recovery |
| LG/webOS and Samsung/Tizen | Inherited web implementations/package tools | `web/`, `webos/`, `tizen/` | Android themes, section editing and playback parity unverified |
| Vibe TV identity | Documentation renamed, upstream credited | README and linked guides | Settings header and All Apps entry show Vibe TV; Android’s installed app label, package and artifact names remain unchanged |

## Evidence and known limits

[Settings verification](design/SETTINGS_VERIFICATION.md) records the final theme/navigation checks. [Playback status](PLAYBACK_STATUS.md) records 19 isolated on-device regression cases and provider limitations. [Compatibility](COMPATIBILITY.md) separates app launch from exact-title capture and actual playback resume. [Screenshots](documentation/SCREENSHOTS.md) links checked-in captures with provenance.

The earlier combined Home/playback build survived one approved reboot and retained the selected Home activity. That reboot predates the final theme/section-editor revision; no new reboot or Google TV factory-restoration switch was performed in the final Settings pass. Lamplight's ten-minute refresh was reviewed in source, not timed on hardware. Multi-language and spoken TalkBack acceptance remain open.

## Before publishing a release

1. Record the intended source commit, build identity and signing provenance. Keep keys and credentials private.
2. Run `tools/check.sh` with the CI toolchain; follow [device checks](ANDROID_TV_TESTING.md) for relevant behavior. Record failures and untested paths accurately.
3. Obtain the owner's approval for device changes; repeat affected remote/settings/playback and recovery checks after runtime changes.
4. Refresh reviewed screenshots from the same runtime, and update this register, compatibility and privacy documentation.
5. Release notes must distinguish verified fixes, inherited behavior, migrations and known limits. Upload only artifacts matching the recorded source and existing signing keys; a documentation change is not a binary release.
