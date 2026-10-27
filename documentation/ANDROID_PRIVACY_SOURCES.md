# Android TV privacy: primary-source checks

Reviewed 27 October 2026 for the CleanYourTV comparison. These are documented platform behaviors and implementation recommendations, not measurements or verification of the user's TCL. No TV settings were changed for this research. Menu availability depends on firmware and region.

## Controls worth supporting

| Control | What the primary source establishes | Vibe TV implication |
|---|---|---|
| Delete advertising ID | Google TV offers Privacy → Ads → Delete advertising ID. Deletion does not assign a replacement; reset assigns a new ID. Ads and other forms of personalization can remain. | Prefer a supported system-settings handoff and clear instructions over invented settings keys. Do not claim this blocks every ad or tracker. [Google TV Help](https://support.google.com/googletv/answer/13392198?hl=en-GB) |
| Advertising ID opt-out on Android 12 | With limit-ad-tracking enabled, the Play services advertising-ID API returns an all-zero ID on Android 12 regardless of the requesting app's target SDK. API 33-targeting apps need `com.google.android.gms.permission.AD_ID` to obtain the identifier. | This documents the API result, not a universal writable `Settings.Secure` entry. Do not add the AD_ID permission or an identifier collection library merely to audit privacy. [API reference](https://developers.google.com/android/reference/com/google/android/gms/ads/identifier/AdvertisingIdClient.Info) |
| Optional usage and diagnostics | Google says this control governs additional usage information; disabling it does not stop information essential to security/system updates or data collected by other apps. | Offer or document the real vendor/system control. A status must distinguish a verified setting from an absent/unknown one. No promise of zero Google traffic. [Google Play services explanation](https://support.google.com/android/answer/10546414?hl=en-IN) |
| Samba Interactive TV | Samba says disabling its service prevents collection/possible sharing, whereas its ad-personalization/limit-ad-tracking controls stop tailoring based on viewing choices. Menu names differ by TV. | Identify the actual Samba service before disabling a package through the reversible existing HomeMode path. Prefer disabling collection over merely changing ad personalization. This does not establish which package exists on this TCL. [Samba privacy notice](https://samba.com/privacy-center/notice-of-right-to-opt-out) |
| Cast diagnostics | Google describes crash/diagnostics collection as optional and notes that Cast usage can include the apps and domains cast. | Keep casting functional and review its consent separately where supported, rather than disabling the receiver to reduce optional diagnostics. [Google Cast privacy settings](https://support.google.com/chromecast/answer/6076570?hl=en) |

## Preserve security and functional services

- Keep Play Protect. Google documents scanning at installation and ongoing device checks, including applications installed outside Google Play. Disabling scanning is a security tradeoff, not an ad-tracking control. [Play Protect on-device protections](https://developers.google.com/android/play-protect/client-protections?hl=en), [cloud protections](https://developers.google.com/android/play-protect/cloud-based-protections).
- Keep system/firmware updates. TCL says its updates provide security patches and performance improvements, and distinguishes them from Play Store app updates. Do not add firmware updaters to a telemetry disable list. [TCL update guide](https://support.tcl.com/en_US/us-googletv-troubleshooting/perform-software-update-gtvs).
- Keep Google Play services. Google documents authentication and security/reliability functions; wholesale disabling loses substantially more than advertising functionality. [Google Play services explanation](https://support.google.com/android/answer/10546414?hl=en-IN), [app functionality](https://support.google.com/googleplay/answer/9037938?hl=en-gb).
- Do not classify Android TV Core Services or Remote Service as expendable solely because they collect diagnostics. Google also identifies notification and remote/audio responsibilities. Retain essential functions and use consent controls for optional collection. [Android TV data-use documentation](https://support.google.com/androidtv/answer/12305147?hl=en).

## Network filtering is a separate decision

Android's Private DNS encrypts DNS questions and answers; Google explicitly says it does not protect other traffic. Encrypted DNS alone is not a tracker blocklist. A filtering provider can block selected names, but this is an additional policy choice and should not be presented as comprehensive ad removal. Android phone documentation does not establish that a TCL TV exposes the same controls. [Android network-settings documentation](https://support.google.com/android/answer/9654714?hl=en-eg).

A launcher-integrated VPN blocker has a substantial compatibility cost: Android permits one active VPN service per user/profile, and starting another stops the existing one. First-time consent is required. A persistent service also needs its own performance and recovery validation; it should not be introduced as an invisible launcher optimization. [Android VPN guide](https://developer.android.com/develop/connectivity/vpn), [VpnService reference](https://developer.android.com/reference/android/net/VpnService).

Inference from DNS's scope: hostname filtering cannot selectively distinguish an ad from a wanted video served from the same hostname, and apps using other resolution paths need separate consideration. Do not promise that DNS removes ads within streaming apps. No network changes are proposed for the user's TV without a separate decision.

## Important TCL naming trap

TCL's current X11L-derived manual calls a feature **Automatic Content Recognition** when it selects a matching FILMMAKER/IMAX picture mode. The same guide warns that settings vary by model and firmware. Therefore the words “ACR” or “content recognition” alone are insufficient evidence that a setting is Samba advertising telemetry. Preserve picture settings and establish the actual package/component's role before any change. This manual is evidence of ambiguous terminology, not a claim that the user's Android 12 model has this feature. [TCL settings and manual guide](https://support.tcl.com/en_US/tcl-google-tv-settings-and-user-manual-guide).

## Recommended acceptance criteria

1. Separate privacy evidence from speed claims: reduced optional collection does not demonstrate faster navigation or more free RAM.
2. Confirm package presence, role, enabled state, and firmware before applying an exact allowlisted disable. Keep existing protected-package and Wi-Fi ownership checks.
3. Record only status and intended action; do not log advertising IDs, account data, viewing history, serials, or full network dumps.
4. Test Original restoration and watchdog recovery for package changes. Manual privacy consent should remain a user choice rather than being silently re-enabled by launcher recovery.
5. Preserve Settings, Continue Watching, casting, voice/remote controls, updates and security services. Unknown controls should stay unknown until independently verified.
