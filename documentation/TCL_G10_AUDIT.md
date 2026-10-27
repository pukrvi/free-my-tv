> Historical read-only phase. The subsequent owner-authorized changes and restart results are in [TCL_G10_OPTIMIZATION.md](TCL_G10_OPTIMIZATION.md).

# TCL G10: read-only device findings

Observed 27 October 2026 after the owner requested another connection to the saved target.
The target connected successfully. No installation, launch, input event, reboot, cache clear,
package disable, settings write, counter reset or network change was performed.
Work remains on `codex/tcl-privacy-audit`; no push or merge.

## What is actually on this TV

Device properties identify **Smart TV Pro, G10, Android 12 / SDK 31**, with maximum graphics
dimensions of 1920×1080. User 0's package inventory contained 141 packages, of which 21 were
disabled. These counts include existing owner/system choices; they are not a count of changes
made by Vibe TV. ADB reports authentication required. Vibe TV's TCL APP_AUTO_START operation
is allowed and should remain allowed.

The initial audit could not validate the inventory because its parser rejected `package:android`.
That special framework package is now accepted; malformed package names are still rejected.
Eight offline tests pass, including the new regression. The corrected run verified its inventory.

The [sanitized snapshot](audits/tcl-g10-2026-10-27.json) contains selected states and counters;
no target address, serial, account, advertising identifier, raw diagnostics or viewing history.
Supplemental observations below came from read-only package/component, process/service, CPU,
graphics, memory, storage and individual property/settings queries. Their raw dumps are not committed.

## Existing protection confirmed

`tv.samba.ssm.ui`, `com.tcl.logkit` and `com.tcl.smartalexa` are **already disabled**. The stock
Google home, setup wizard and screensaver are also disabled; Vibe TV's HomeActivity was resumed
when inspected. Leave that working launcher/recovery arrangement intact.

The following original research candidates are **absent** on this unit:

- `com.android.tv.feedbackconsent`
- `com.google.android.tv.remotecontrol.logging`
- `com.google.android.apps.tv.netoscope`
- `com.tcl.tv.plus`
- `com.tcl.smartlink.core`

There is no benefit in adding absent packages to a TCL-specific cleanup for this TV.

## Ranked options before any changes

| Option | Observation on this TV | Recommendation / tradeoff |
|---|---|---|
| Google feedback sender (`com.google.android.feedback`) | Enabled; separate app UID; exposes FeedbackActivity and BugReportService; requests log, usage-stat and internet access. No process or active service appeared in the inspection. | Best first optional disable candidate for reducing feedback/report capability. Normal streaming is not its declared role, but feedback submission would stop. Reversible device testing still required; no current RAM/CPU saving demonstrated. |
| Google Play Movies (`com.google.android.videos`) | Enabled; movie UI, cast entry, sync, channel/EPG and preview-input components are present. No process or active service observed. | Optional only if the owner does not use its purchases/rentals, channels or cast entry. Removes features; not an automatic privacy recommendation. |
| Printing (`com.android.printspooler`) | Enabled; PrintActivity and print-management permissions; no process or active service observed. | Likely low-impact if printing is unused, but low value: no ongoing resource cost was observed and its own requested permissions did not include INTERNET. Do not sell this as tracker blocking. |
| Google calendar sync (`com.google.android.syncadapters.calendar`) | Enabled; CalendarSyncAdapterService plus boot/push receivers, calendar and internet permissions; no process or active service observed. | Optional only if calendar/Assistant integrations are unused. Loses syncing; no observed idle cost. Leave the calendar provider alone. |
| Google Partner Setup (`com.google.android.partnersetup`) | Enabled; RLZ providers/receiver alongside configuration and component-state privileges; no process or active service observed. | Keep pending deeper dependency analysis. Attribution-related component names do not establish that disabling the entire package is safe. |
| One-time initializer (`com.google.android.onetimeinitializer`) | Enabled; boot receiver and component-state permission; no evidence of ongoing work gathered. | Keep. Disabling a one-time initialization path offers no demonstrated performance benefit and can affect later setup flows. |
| TCL passive home (`com.tcl.tv.tclhome_passive`) | Enabled; boot receiver and content provider alongside content-app UI. Protected by our current HomeMode policy. | Keep. The dump does not establish who depends on its provider or boot integration. Do not bypass the existing protection just because another model disables it. |

“Candidate” means suitable for an owner-approved reversible experiment, not already proven safe.
Process/service absence is a point-in-time result; it does not rule out jobs, later starts, or
shared-process work. Declared permissions indicate capability, not actual recording/uploading.
No optional package was disabled to produce this table.

## TCL telemetry is bundled with functional services

`com.tcl.ttvs` is enabled and shares the system UID. Its package exposes update activities and
SystemUpdateService, casting components, BI/identifier components and SambaVideoService/SSMReceive.
The active-service listing included:

- `.bi.core.BIService`
- `.identifier.core.service.IdentifierService`
- `.oversea.usagest.core.service.UsagestService`
- `com.tcl.trackdata.service.TrackDataService`
- Core, casting, playback and message services.

No SambaVideoService appeared in that active-service listing. This does not prove it can never
start, nor that ACR is active. Conversely, the separate Samba app being disabled does **not** prove
every viewing/identifier/usage collection path in TCL firmware is disabled. We did not measure
network uploads or inspect private consent data.

**Keep ttvs enabled.** Removing the entire package can remove firmware updating/casting as well
as telemetry. Selective BI/usage-service controls or verified consent/network endpoints are a
promising next investigation, but component names alone do not establish safe disable boundaries.
Shared system UID also makes UID-wide network/AppOps restrictions unsuitable for targeting just
this telemetry. TCL interactive and suspension remain protected. TCL guard has active cleanup/core
services, but prior model reports describe retry loops when disabled; leave it enabled too.

## Performance snapshot

| Reading | Observed value | What it means |
|---|---|---|
| System RAM reported | 1,807,396 KiB, about 1.72 GiB | Kernel-visible memory, not a retail specification |
| Available RAM | 642,392 KiB, about 627 MiB | No immediate severe memory shortage demonstrated at that observation |
| Home process PSS | 75,457 KiB, about 74 MiB | Process proportional memory; not total GPU/device memory |
| Storage | About 4.21 GiB available, 47% used | No immediate storage shortage demonstrated |
| CPU | CPU service's existing interval reported 7.3% total | An uncontrolled historical interval, not a fresh idle benchmark |
| Frames | Snapshot: 84 janky / 257; later read: 88 / 261 (33.72%) | Existing cumulative counters, including startup/earlier interactions; not a controlled steady-state jank rate |
| Frame percentiles at later read | Median 26 ms; 90th percentile 85 ms | Worth profiling, but not proof that background packages caused the slow frames |

The feedback, movies, printing and calendar packages had no observed process/service, so disabling
them cannot be credited with an immediate speed gain. Package removal is unlikely to be the main
performance opportunity on the evidence gathered. Do not purge caches or impose process limits.

The most useful next performance work is a controlled Home/navigation profile, then a focused
experiment with app-list reloads or cached rendering/memory lifetimes. Keep the rendered design,
settings and Continue Watching behavior identical. That active test would involve device input
and possibly a test build, so it was not done during this read-only audit.

## Additional privacy controls

The Private DNS mode query returned unset (`null`), so no explicit Android Private DNS mode is
recorded. This says nothing about router filtering or apps' own DNS. No resolver hostname or
network configuration was changed. Filtering remains a separate decision.

Advertising-ID deletion and usage/diagnostics/Samba consent remain worthwhile checks through the
supported system controls. Their actual status was not read, and no advertising ID was collected.
[Google documents deletion](https://support.google.com/googletv/answer/13392198?hl=en-GB) as avoiding
a replacement identifier, while other ad personalization can remain. Preserve remote functionality:
[Google documents Remote Service](https://support.google.com/androidtv/answer/12305147?hl=en) as
supporting physical-remote audio and phone remotes.

For the broader comparison and pinned evidence, see [CLEANYOURTV_REVIEW.md](CLEANYOURTV_REVIEW.md)
and [ANDROID_PRIVACY_SOURCES.md](ANDROID_PRIVACY_SOURCES.md).
