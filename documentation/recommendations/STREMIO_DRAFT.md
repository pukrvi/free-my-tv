# Stremio recommendation draft — 27 October 2026

Historical initial survey: this document describes the earlier design draft, now preserved on
`codex/stremio-design-draft`. Its initial read-only survey preceded all installation and device testing.
The runtime branch is now `codex/stremio-recommendations`, based on optimized checkpoint `197db6e`.
See [TV_FEEDBACK_BUILD.md](TV_FEEDBACK_BUILD.md) for the installed feedback build, verified flicker fix and performance results.

The user requested exactly two catalog addons alongside TMDB: **Anime Kitsu** and **Streaming Catalogs**. The initial data survey led to a newer twelve-shelf direction with per-section refresh. See
[HOME_RECOMMENDATIONS.md](HOME_RECOMMENDATIONS.md) and [home-profile.json](home-profile.json) for the current contract;
the earlier four-row picker and blanket source-weight proposal are superseded. The current source is therefore a **draft**, not accepted UI or device verification.

## Read-only inventory and catalog survey

The saved TCL target reported these launchable streaming apps: YouTube, Netflix, Prime Video, Stremio, and JioHotstar.
Apple TV's package is installed but disabled (`enabled=3`), with no launch activity. Disney+ has no launchable app.
Fifteen leanback activities were listed, including manufacturer tools, Play Store, Live TV, and launcher settings.
The installed app is not debuggable, so its private preferences/cache and custom dock order could not be read.
The proposed provider priority in the picker is editable and must not be described as the actual saved dock order.

Public catalog endpoints responded without signup:

- Anime Kitsu: `https://anime-kitsu.strem.fun/manifest.json` — 50 trending, 20 top-airing entries.
- Streaming Catalogs: `https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json`.
  Netflix: 96 movies / 99 shows; Prime: 100 / 98; JioHotstar: 100 / 97; Disney+: 99 / 100; Apple TV: 100 / 99.
- TMDB, configured region **IN**: first-page samples of 20 recent subscription movies, 20 popular subscription shows,
  20 globally trending movies and 20 globally trending shows. Popular shows have no recent-air-date filter in this
  survey; that query differs from the older launcher's recent-show discovery query.

Total: **1,138 returned entries**, **959 sampled identities** after joining explicit IMDb/TMDB aliases, with **54**
individual title availability responses checked for India. These are samples and catalog responses, not complete
service libraries. Unmapped identities and separate anime seasons can remain distinct. Exact-ID source overlaps:
TMDB/Kitsu 3, TMDB/Streaming 38, Kitsu/Streaming 15. Native availability is not inferred from addon feed membership.

The interactive picker lives in the chat's visualization directory. It enables/disables all sixteen sampled feeds,
adjusts For You source weights, controls anime shelf inclusion, excludes individual titles, previews provider priority,
and sends explicit decisions back to the chat. Its defaults are proposals. No decision has been applied to the TV.

## Draft implementation and checks

- `StremioCatalogs`: fixed two-addon HTTP catalog client, manifest checks, configured provider catalogs including `jhs`,
  bounded entries, public HTTPS artwork, adult metadata filtering, exact Kitsu/IMDb detail links, partial feed survival.
- `RecommendationRouting`: regional subscription offers ranked by launchable dock apps, including JioHotstar and
  alternate Prime packages; excludes rental stores and reseller channels.
- `Recs`: independent bounded source fetching, 36-title provisional round-robin pool, explicit external identity,
  source metadata, HTTPS artwork cache names, optional TMDB enrichment, Stremio fallback, and persisted routes.
  Existing HomeView shelf layout is unchanged. Kitsu fallback requires a matching addon installed in Stremio.
- Native title-page links retain the existing optional Watchmode path. Without a usable link, the existing app launcher
  may open the service home. Exact-title/exact-position playback is not established by this work.

Validation: Android build ended with `Built FreeMyTV.apk`; `tools/test_recommendations.sh` passed **36** checks for
routing, parser rejection, feed failures, configured manifests, namespaced identity, cache serialization, and mixing.
`python3 tools/translate.py --check` passed (252 keys); `git diff --check` passed. Picker source checks cover weights,
shelf selection, title search/exclusion, app-priority swaps, verified-only filtering, saved state, and its send action.
The browser could not open the local preview, so no visual browser check is claimed. No physical-TV acceptance is claimed.

## Decisions and acceptance still pending

1. The user has chosen twelve independent shelves and context-specific refresh; the tested reference planner covers
   matching and selection. Integrate that contract into the Android renderer.
2. Replace the provisional 36-title balanced pool and six-per-catalog cap; retain source membership, exact Kitsu
   season entries, date/country/quality evidence and independent row snapshots.
3. Align recommendation status copy with multi-source behavior and document public-addon requests before shipping.
4. Run the repository's full checks before a push, then separately obtain authorization for TV install/launch and
   exercise actual native/Stremio title opening, artwork, refresh/focus stability and no-key/source-outage paths.

The Continue chat consolidated these tracked findings; update existing issues instead of duplicating them:
[TMDB gate #18](https://github.com/pukrvi/vibe-tv/issues/18),
[regional/dock routing #19](https://github.com/pukrvi/vibe-tv/issues/19),
[identity/artwork cache #20](https://github.com/pukrvi/vibe-tv/issues/20), and
[title opening fallback #21](https://github.com/pukrvi/vibe-tv/issues/21).

Primary protocol references: [Stremio addon protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md),
[Anime Kitsu](https://github.com/TheBeastLT/stremio-kitsu-anime),
[Streaming Catalogs](https://github.com/rleroi/Stremio-Streaming-Catalogs-Addon), and
[TMDB watch providers](https://developer.themoviedb.org/reference/movie-watch-providers).
