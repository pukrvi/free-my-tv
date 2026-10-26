# Continue Watching: TCL status, 27 October 2026

This records the installed `continue-watching` branch on the owner’s TCL Android 12 TV. It is the feature-status reference
for README/documentation updates and for the parallel Settings/home-theme work. Preserve these tested behaviors when
integrating a new home theme. `stable` remains at `b920f4a`; this feature is not merged into main.

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
| Netflix | A real PLAYING session had null metadata, null session extras and no session launch intent. It now creates an explicit **Continue in Netflix** card, with an app-name tile rather than a fabricated title. | Card opens Netflix. Automatic playback did not reliably restart; exact-title resume and thumbnails remain unresolved. |
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
