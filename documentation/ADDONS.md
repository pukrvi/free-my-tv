# Stremio catalog add-ons

[Feature guides](FEATURE_STORIES.md) · [Contribute](../CONTRIBUTING.md)

![Two catalog adapters in development](feature-assets/catalog-addons.png)

## Current scope

The recommendation work is testing two catalog adapters alongside the existing TMDB source:

| Add-on | Manifest ID | What the draft uses |
|---|---|---|
| [Anime Kitsu](https://anime-kitsu.strem.fun/manifest.json) | `community.anime.kitsu` | Anime catalog entries and metadata, including airing order and season links |
| [Streaming Catalogs](https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json) | `pw.ers.netflix-catalog` | Movie/show catalog entries and source signals |

These two adapters are included in the Android Vibe TV 1 feedback build on main. Artwork and shelf scrolling have been checked on the TCL; exact-title routing and broader acceptance remain pending. [Feedback build and measurements](recommendations/QUICK_PERFORMANCE_FIXES.md). The implementation reads manifests and catalog metadata. It does not execute add-on streams or subtitles, install arbitrary plug-ins, sync accounts, provide a VPN, or replace a streaming app's player.

The feeds are public and need no signup. TMDB enrichment and optional title links have their own key requirements, which must be stated for the final build. A country label in a source feed does not prove that a title is available in that country; some Streaming Catalogs feeds use fixed provider countries. Routing and regional availability need separate checks.

## Add another catalog

A contributor can implement and test another adapter in code. There is no runtime installer for arbitrary add-ons.

For a catalog contribution, include:

- The manifest URL/ID, supported catalog types, attribution and any key or account requirement.
- Stable IDs, media type and season handling, including collisions with TMDB or another source.
- Date/category rules, refresh behavior, missing fields, empty/error responses and network limits.
- App/title routing and real region-availability evidence, with fallback behavior stated.
- Parser/planner tests, an exact tested commit, and a reviewed device capture before claiming TV support.

Follow the [contribution guide](../CONTRIBUTING.md). Keep credentials, user-installed private add-on URLs and signed playback URLs out of reports and assets.

## When this becomes a published feature

After the implementation merges and device testing finishes, update this page with the merged commit, tested adapter list, actual key/privacy requirements and known limits. The recommendation visual is gated in [feature evidence](feature-assets/evidence.json); it needs a reviewed capture and approved copy before the renderer will produce it.
