# Main integration verification — October 27, 2026

## Source and merge

- Home: `b920f4a` already contains the restored/default Vibe TV home. The Home chat confirmed no separate missing redesign commit; older dirty snapshots were superseded by the final tested Settings implementation.
- Playback: `9d4473b`, merged by `2f93e50`, preserves the completed media/history/artwork/provider fallback changes and regression tooling.
- Settings: `e740614`, merged by `441d4d7`, adds continuous settings, three custom themes and the Vibe-only section editor.
- Overlapping source/i18n conflicts were resolved to the tested Settings revision after checking that its playback modules preserved the completed baseline. The integrated `app/`, `web/`, `webos/`, `tizen/` and symbol-generator sources match `e740614` exactly. Playback's test script and HistoryChecks remain present.
- Documentation is updated separately after the runtime merges. No secret configuration, signing key or personal environment file is tracked. Local pre-integration changes in the original checkout are preserved.

## Integration checks

- `tools/check.sh`: **passed** with JDK 17, Android platform 34 and build-tools 34.0.0, using an isolated SDK view. Build-tools were downloaded from Google's SDK repository and their checksum verified. The integration build used disposable local configuration/signing and was not installed over the personalized TV build.
- Android APK compiled and signed; all **269 translation keys / 26 catalogs** matched.
- Generic webOS IPK and Tizen WGT built. The Tizen check artifact is unsigned; packaging success is not retail-TV installation verification.
- ES5 syntax/API and older-TV CSS checks passed.
- `tools/run-history-checks.sh` against the already installed final Settings build: **19 isolated history checks passed**. The test instrumentation was signed through the existing signing-key reference; the temporary test app was removed. No user's history was cleared.
- Documentation file links, section anchors, issue-template YAML, screenshot dimensions and whitespace checked before commit.

## Physical target and final state

TCL retail reference 55Q6CS; Android's reported model is **Smart TV Pro**, Android 12. Firmware display ID: **STT2.230203.001 release-keys**. UI reference/captures: 1920 × 1080. Streaming-app versions were not recorded in the earlier provider checks.

The Settings chat installed and tested the exact `e740614` runtime, then released the TV with Vibe TV 1 selected and default section order/visibility restored. See [Settings verification](SETTINGS_VERIFICATION.md) for themes, ordering, long lists, cross-section focus and picker-cancel results; see [playback status](../PLAYBACK_STATUS.md) for provider limits. No redundant app reinstall was required for the identical integrated source.

The owner authorized TV standby after completion. The final integration chat sent Android's dedicated Sleep key rather than a power toggle and checked the reported power state: **mWakefulness=Asleep**. No device settings were changed for this shutdown.

## Remaining limits

The previous Home/playback baseline passed one approved reboot; the final themes revision was not rebooted. Factory Google TV restoration, spoken TalkBack, every language and Lamplight's timed refresh were not newly hardware-tested. Netflix/Prime exact-title support and reliable automatic resume remain unresolved; JioHotstar artwork is unavailable in the inspected session. A successful app launch does not upgrade those claims. No tagged binary release was created.
