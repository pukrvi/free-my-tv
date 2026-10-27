# Continue Watching: TCL status, 27 October 2026

**Latest hotfix status:** [saved-card resume investigation](#saved-card-resume-hotfix) supersedes the historical reopening claims below. Capture/artwork/progress alone do not prove exact-content resume. Tracking: [#22](https://github.com/pukrvi/vibe-tv/issues/22); [cross-chat issue history](documentation/ISSUES.md).

This records the installed `continue-watching` branch on the owner’s TCL Android 12 TV. It is the feature-status reference
for README/documentation updates and for the parallel Settings/home-theme work. Preserve these tested behaviors when
integrating a new home theme. `stable` remains at `b920f4a`; playback `9d4473b` and final Settings `e740614` are now integrated into main. Historical captures below predate the final themes; the current gallery is [documentation/SCREENSHOTS.md](documentation/SCREENSHOTS.md).

## What works

- Continue Watching is above the recommendation shelves, with a switch in Home Settings → Widgets.
- YouTube and Stremio have real artwork and visible progress on this TV. Older YouTube entries captured before artwork
  support may retain their app logo until a fresh playback publishes the thumbnail. Session artwork is preferred;
  movie/show titles can use an exact-title TMDB fallback. The app never guesses a series from an episode subtitle.
- For You, Anime, Movies and TV Shows headings remain visible when Continue Watching shifts their layer positions.
- Hold OK/Enter on a resume card for Remove from Continue Watching. Hold on a recommendation for Hide from recommendations.
  Back/Cancel closes the menu; releasing the original held button does not choose an action. Recommendation hiding applies
  across all home shelves and leaves the remaining choices steady.
- Removed/expired paused sessions do not recreate cards. YouTube expires after two days; other services after ten days;
  keep at most twelve entries. Short ad durations do not replace an existing episode’s much longer saved duration.
- The media-session listener observes playback while other apps are in front, before they discard their session. It stays
  bound only for Continue Watching; Now Playing and Continue Watching share the existing listener permission.

## Provider results

| App | Title / artwork / progress | Reopening |
| --- | --- | --- |
| YouTube | Title, thumbnail and contrasting progress bar verified with a public Blender video. | Existing session resumes; a video ID can provide a direct video link when the app supplies one. |
| Stremio | Title/progress confirmed by the user; exact-title fallback artwork verified on the TV. | Reopens Stremio and uses its own saved playback. |
| JioHotstar | Episode title and duration/progress verified. No thumbnail: live metadata had no artwork, series name or media ID. | App/current episode reopened and published PLAYING with an advancing position. Exact-title deep links are not available from this session. |
| Netflix | A real PLAYING session had null metadata, null session extras and no session launch intent. It now creates an explicit **Continue in Netflix** card, with a branded app-art tile rather than a fabricated title or movie thumbnail. | Card opens Netflix. Automatic playback did not reliably restart; exact-title resume and thumbnails remain unresolved. |
| Prime Video | Observed sessions supplied only **PrimeVideo** rather than the content title. App-level **Continue in Prime Video** fallback is implemented and covered by data tests. | App launch verified; reliable content capture and automatic resume remain unverified. |

Android’s Watch Next and preview-program providers were empty during this check, so they offered no alternative title or
artwork source. Netflix also blocked screenshots and exposed no readable accessibility text. Do not infer full support
from a successful app launch or from synthetic data tests.

The user approved stopping further provider investigation after this bounded pass and keeping the working YouTube/
Stremio baseline. Future work should start from a new usable provider data source or a fresh reproducible sample; do not
repeat the same launch experiments. Exact Netflix/Prime titles and JioHotstar thumbnails are still open limitations.

## Validation and captures

The Android build, all 257 translation keys, whitespace checks and the UI detector passed. On-device regression checks
passed **19 cases** against isolated preferences/cache, covering elapsed positions, missing pause fields, artwork caching,
completion, ad-duration changes, persistent dismissal, provider expiry/replay, app-level fallback and cross-shelf hiding.
Run `tools/run-history-checks.sh 192.168.1.9:5555` only with explicit device-testing authorization and the current APK
already installed. The temporary test APK is removed after the run. No user history is cleared. The optional second
argument `true` inspects live supported-app media metadata without sending remote input or reading notification bodies.

Fresh 1920 × 1080 hardware screenshots, inspected at full resolution, are stored locally (not committed):

- `/tmp/free-my-tv-tv-check/feedback-final-installed.png`: installed home, Netflix app-level card, Jio progress,
  YouTube thumbnail/progress and Stremio artwork/progress.
- `/tmp/free-my-tv-tv-check/feedback-fixed-for-you.png`, `feedback-anime.png`, `feedback-movies.png`, `feedback-shows.png`:
  all four restored headings and remote focus.
- `/tmp/free-my-tv-tv-check/feedback-remove-menu.png`, `feedback-hide-menu.png`, `feedback-hidden-result.png`:
  both menus and actual recommendation removal.
- `/tmp/free-my-tv-tv-check/continuous-settings/`: the parallel Settings chat’s passed checks on the combined build.

The TV was returned to Free My TV Home, with playback paused. The parallel Settings chat owns subsequent theme work.

## Privacy

Viewing history is private app data on the TV. Media notifications are not read. Artwork URLs supplied by streaming apps
may be fetched; a known YouTube ID can fetch a public thumbnail, and exact movie/show title lookups may be sent to TMDB
when session artwork is absent. README’s privacy section reflects these requests. Credentials and full signed playback
URLs must never be printed or committed.

## Branded-card and gallery polish — 27 October 2026

Runtime `3e382d0` changes the Vibe TV 1 card renderer to request app art even when a provider supplies no content title.
Netflix now uses its installed app artwork through the same tile path as JioHotstar. Real content artwork still takes
priority; text is a temporary fallback only if app artwork is unavailable. This is a visual change: Netflix/Prime
exact-title capture, thumbnails and reliable automatic resume remain unresolved as recorded above.

Built and installed with Java 17 / platform 34 / build-tools 34.0.0. Full `tools/check.sh` passed, including all 271
translation keys, generic LG/Samsung packaging and legacy web checks. Both 1920 × 1080 captures were inspected at original
resolution. The user-requested gallery view was prepared with the existing long-press removal menu: Netflix, JioHotstar
and an older logo-only YouTube entry were dismissed, leaving YouTube and Stremio artwork/progress. Home return confirmed
those entries stayed removed. The normal dock apps remain present. This pass did not repeat provider playback or the
19 data-regression cases, since session handling and storage did not change. Current images and provenance are in
[documentation/SCREENSHOTS.md](documentation/SCREENSHOTS.md).

## Saved-card resume hotfix

Branch `codex/continue-watching-resume` starts from main `f4a1492`, retaining the optimization and wiki/documentation work. Tested on TCL Smart TV Pro, Android 12, with **YouTube TV 7.27.302** and **Stremio 1.8.1** on 27 October 2026.

### Changes

- Persist validated provider title links from media metadata, preserving them when paused metadata omits fields. YouTube links use the saved timestamp; credential-bearing, foreign-host and signed stream URLs are rejected. Netflix/Prime/JioHotstar use genuine supported public title links when supplied and otherwise retain app-level fallback. No new live title-link success is claimed for those three services.
- Recover Stremio episode identity from its own public Metahub artwork ID and explicit `SxxExx` title. Older cards can make one bounded background query to Stremio’s exported TV search provider. Only a unique exact title/type match is accepted; ambiguous titles are refused. This may use Stremio’s normal catalog network requests. Its documented `autoPlay=true` detail link attempts the previously used stream; unavailable streams/profile gates remain provider behavior.
- Wait for a fresh matching session, then seek using its current actions. Check title, available channel/subtitle, canonical identity and duration before controls; never seek an opaque app-only card. Another selection or returning Home cancels the request. Final known pause positions are saved even below the normal 30-second playing-update threshold.
- An empty address now uses the app launch fallback rather than a blank `ACTION_VIEW`. For YouTube cards without a usable ID, open the saved title’s search results. The user chooses the matching result; a bounded matching-playback watcher can then apply its saved point. Results are not silently selected.

### Device evidence and limits

| Check | Result |
|---|---|
| Older Stremio card after another episode | Played S01E02, returned Home, then selected the older S01E01 card. Saved point **478,752 ms**; reopened S01E01 reported **482,002 ms**, then **492,236 ms**, PLAYING. This demonstrates the correct older episode and continuing progress near the saved point. |
| Known YouTube video URL with timestamp | Explicit public Blender URL at 120 seconds reported PLAYING at **123,732 ms**. URL target/time support is verified; this does not establish that the app publishes that ID during ordinary playback. |
| Existing YouTube card without ID | Opened Big Buck Bunny search results and selected the visibly matching Blender result with one extra OK. Saved point **193,654 ms**; a YouTube session reported **198,313 ms** shortly afterward. This sample does not independently prove content identity or that Vibe, rather than YouTube’s own history, applied the point. Search destination is verified; one-press exact-video reopening and automatic seek for missing-ID cards remain unresolved. |
| YouTube session identity | Live session supplied title/artist/duration/bitmap, but no MEDIA_ID, MEDIA_URI, artwork URI, session activity or session extras. Watch Next/preview providers were empty. Reliable automatic video identity remains unresolved in **#22**. |
| Netflix / Prime / JioHotstar | Prior app-level fallback and metadata limits remain. New public-link parsing is covered in source; no fresh end-to-end title/position claim is made. See **#9 / #10**. |

The source follows [Stremio’s documented detail/autoplay links](https://stremio.github.io/stremio-addon-sdk/deep-links.html). Stream availability can delay or prevent playback; a usable title link is distinct from a working stream.

### Verification

The independent baseline reproduced **10 failures in 37 assertions** while all 19 original checks passed. The final isolated suite passes **86 assertions**, covering link persistence, position clamps, ambiguous/unsafe lookup refusal, actual MediaSession stale callbacks, action changes, seek-to-zero, cancellation, removal and exact final pauses. A repeated-run fixture issue was fixed by clearing temporary preferences before the test and cleaning them before instrumentation finishes; user history is untouched.

Full `tools/check.sh` passed with JDK 17 / Android platform 34 / build-tools 34.0.0: Android build, 31 existing host checks, 271 translation keys, generic LG/Samsung packaging and legacy web checks. The 10 existing performance-asset tests also passed. No remote CI pass is claimed while **#23** remains open.

Installed APK SHA-256: `41097454ae1a897f5d6e0dc15539a9781da65cc2eb77e96350cbc7d57b22e0c6`.
Screenshots were captured at 1920×1080 and inspected locally under `/tmp/free-my-tv-tv-check/resume-hotfix/`; search/video captures are not added to the public gallery because they can contain account UI and personal viewing history. The existing curated public gallery is preserved.

For a controlled exact-position report, optional inspector usage is `tools/run-history-checks.sh DEVICE true 'Exact public test title'`. It reports only that requested saved sample’s point/duration/link presence plus the existing sanitized live metadata; it never prints a full playback URI. Inspector instrumentation restarts the home process, so sample the stored point **before** the launch and wait until resume polling completes before inspecting again. Screenshot/launch checks alone are insufficient.
