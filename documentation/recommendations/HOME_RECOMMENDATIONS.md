# Twelve-shelf recommendation and refresh contract

Latest user direction: **27 October 2026**. This supersedes the four-row recommendation specification and the earlier
40/25/35 source-mixing proposal. The twelve-row Android feedback build on `codex/stremio-recommendations` is now
installed and its artwork/scroll stability has been checked on the TV. See [TV_FEEDBACK_BUILD.md](TV_FEEDBACK_BUILD.md)
for verified behavior, quick performance measurements and the remaining production acceptance work.

The matching and refresh reference is `tools/recommendation_plan.py`, with the editable local profile in
`home-profile.json`. Its purpose is to settle and verify selection behavior before changing the TV renderer.

## One library, twelve separate rows

Each source supplies candidates and evidence to one shared title library. A title keeps every source membership;
it does not become multiple cards just because Netflix and Prime feeds both mention it. Each row applies its own
eligibility and ranking rules. “All sources” means their eligible union, without forcing equal source quotas.

| # | Row | Candidate and selection rule |
|---|---|---|
| 1 | For Me | A ten-card starting mix: two anime, two Dune-related recommendations, two other fantasy/science-fiction picks, one recent movie, one recent/new-season show, two quality discoveries. Refill unavailable slot types from the eligible broader pool. Exclude known unreleased works. |
| 2 | Anime Airing | Only Kitsu `kitsu-anime-airing`; preserve all twenty usable entries, exact season IDs and source ranking. If the source supplies fewer, keep fewer; do not fill from another feed. |
| 3 | New Movies | Movies from all sources with a precise past release date. Rank the past ninety days first; allow recent/trending releases up to one year old, with date/year visible. Older trending films belong elsewhere. |
| 4 | New Shows | All-source shows/anime with a premiere or a season premiere in the past ninety days. Label returning shows **New season**, rather than implying their series just premiered. Year-only metadata or a recent episode alone is insufficient. |
| 5 | Trending | TMDB weekly movie and TV trending membership only. Mix both media types. Other sources may enrich the record and route, but cannot add membership. |
| 6 | Indian | Movies + TV from all sources with India origin/production evidence; include regional Indian languages. |
| 7 | Sci-Fi | Movies + TV with explicit science-fiction genre or keyword evidence. TV's broad Sci-Fi & Fantasy category alone does not establish strict science fiction. Fantasy also influences For Me. |
| 8 | Korean | Movies + TV from all sources with South Korean origin/production evidence. |
| 9 | Japanese | Movies + TV/anime from all sources with Japanese origin/production evidence. Anime alone does not imply Japan. |
| 10 | Oscar Winners | Verified Academy-winning films from a versioned local list. Distinguish award ceremony year, film release year, nominations and wins. This is film-only. |
| 11 | Must-Watch Movies | Quality-qualified movies, deliberately rotated across countries and decades. |
| 12 | Must-Watch TV | Quality-qualified series, deliberately rotated across countries and decades. |

The seven Explore rows are independent shelves following the five main shelves. They are not one combined sixth row.
The home order before recommendations remains quick actions, weather, dock and optional Continue Watching.

The profile uses the user's explicit fantasy/science-fiction/Dune preference. It does not claim to learn from Netflix,
Prime or other private watch histories. Dune-specific discovery is seeded by the identified 2021/2024 films and their
TMDB recommendations when available. The recommendation rule may also surface high-quality work outside those genres.

## Matching and field ownership

1. Normalize movie and TV types separately. Join explicit TMDB/IMDb mappings; never silently join by a title string.
2. Retain two identity levels: a title group for broad-row deduplication and a Kitsu season/part edition for airing and
   exact Stremio opening. Multiple Kitsu entries may share one IMDb show without becoming the same airing entry.
3. Preserve feed membership, source rank, country evidence, date precision and rating provenance. Metadata enrichment
   is not a new discovery-source membership. Conflicting explicit identity mappings remain separate and are flagged.
4. Use precise release/season dates for New rows, origin/production metadata for country rows, and verified award
   records for Oscars. Unknown fields remain unknown; do not infer them from display language, a provider feed name,
   an anime label, a year range, or a high rating.
5. Normalize feed ranks before combining them. Keep Kitsu-derived ratings separate from IMDb/TMDB ratings. The
   reference must-watch gate uses at least 200 TMDB votes and a Bayesian rating estimate of at least 7.5/10, shrinking
   toward a 6.5/10 prior with weight 500. Thresholds are editorial policy, not a claim of objective “must-watch” status.
6. Penalize repeats across mixed/Explore rows and balance media types. Do not remove a Kitsu airing entry or a TMDB
   trending member just because it also appeared in For Me. Must-watch rows additionally balance country and decade
   buckets, with stable visit-specific variation.

The Academy starter file is `oscars-starter.json`: twelve verified winning films across eras, including both Dune
films, with primary Academy URLs. IDs are resolved by exact title/original release year; ambiguous results are not
silently accepted. The current public-data preview resolved ten of the twelve; this is an editorial starter, not the
complete Oscar library. Expand the checked-in curation with verified records; no extra live award service or addon is
needed. At ten candidates and ten slots, that sample deliberately stays put because it has no unseen alternatives.

## Refresh is two independent operations

**Data refresh** checks whether catalog/metadata evidence has changed. **Selection rotation** changes which eligible
cards occupy a row. A successful HTTP request, image download, artwork decode or redraw must never itself shuffle a
held row. Keep one stable row snapshot per Home visit, with pending new candidates applied only at a safe boundary.

| Row | Check source data | Genuine Home return | Idle selection rotation |
|---|---|---|---|
| For Me | 6 hours | Up to 4 of 10 cards | Up to 2 after 10 minutes |
| Anime Airing | 12 hours | Keep source order; apply changed feed | Never randomize; apply a changed feed safely |
| New Movies | 6 hours | Up to 4 of 10 | Up to 2 after 10 minutes |
| New Shows | 6 hours | Up to 4 of 10 | Up to 2 after 10 minutes |
| Trending | 1 hour | Up to 3 of 10 | Up to 2 after 15 minutes |
| Indian / Sci-Fi / Korean / Japanese | 24 hours | Up to 3 of 10 per row | Up to 2 after 30 minutes |
| Oscar Winners | Weekly metadata; award-list membership by verified revision | Up to 2 of 10 | Up to 1 after 60 minutes |
| Must-Watch Movies / TV | Weekly | Up to 3 of 10 per row | Up to 1 after 60 minutes |

These are cache-check targets, not promises that an upstream addon updates on that schedule; Kitsu itself caches
responses. Failed refreshes retain working cached candidates and retry after ten minutes without clearing the UI.

- A Home return means a genuine app/standby/activity re-entry or explicit Home revisit. Deduplicate repeated Android
  callbacks using a visit token; do not count every redraw, onRecs callback, input or lifecycle callback as a new visit.
- Pause selection timers when Home is hidden or the screen is off. A genuine return applies one visit rotation and
  rearms each row's interval; do not replay missed idle rotations.
- Idle rotation requires at least sixty seconds without remote activity **and** that row's elapsed selection interval.
  If a menu, picker, reorder operation or another active interaction is open, defer. Resume elapsed scheduling after it
  closes; do not queue many catch-up shuffles at once.
- Freeze the entire focused recommendation row during idle rotation. Keep its title, slot, neighbors and scroll position
  stable. A genuine Home return resets focus to the dock as usual, after which eligible rows may update.
- Replace only a few slots with unseen alternatives; hold the rest in their exact positions. If there are too few new
  eligible titles, change fewer or none. A pool no larger than the row does not get reshuffled merely to look active.
- Use a locally persisted visit/row seed and elapsed-time clocks for deterministic variation within a visit. Wall-clock
  date governs release windows and cache timestamps; a clock correction must not produce a shuffle burst.
- Rotate region/decade buckets for the must-watch rows rather than repeatedly taking the globally most popular ten.
  Maintain a bounded per-row seen history locally when integrating the Android selector.

## Network, routing and TV resource limits

Keep only the requested two addons and TMDB. Their shared collector caches and coalesces identical requests; twelve
rows must not become twelve independent polling loops. Fetch at most a few source requests concurrently, reuse
metadata/IMDb resolution across rows, and enrich missing metadata for visible/nearby candidates first. Returning Home
normally samples the local library and does not call every source again.

Streaming Catalogs regular feeds are provider discovery/trending pools, with upstream country limitations. Verify
India subscription offers independently. Use **Netflix → Prime Video → JioHotstar** in the selected preference order
when a launchable app has a verified offer; otherwise use Stremio. Keep unavailable and unverified states explicit.
The availability response does not establish a working native title deep link; validate those launch paths separately.
YouTube remains in the dock/Continue Watching flow, outside these movie/TV/anime catalog sources.

The old global 36-title pool and six-per-catalog Kitsu cap cannot support these twelve independent rules. Integration
must preserve a larger bounded **metadata** library and independent per-row candidate pools. It must not decode all
130 card slots at once: prioritize visible and neighboring row art, with a bounded bitmap cache. Keep network, ranking,
matching and artwork preparation off the Canvas draw path.

## Evidence and remaining integration

Public-data reference: 1,105 normalized observations became 821 title groups before known-future For Me exclusions.
The preview supplies all twelve rows, including twenty Kitsu airing entries and ten exact-matched Oscar winners.
Availability is only verified where the individual cached India offer response exists. Counts are sample coverage,
not service-library coverage; some metadata requests were unavailable and were left unresolved.

`python3 -m unittest discover -s tests/recommendations -p 'test_*.py'` passes **36** matching, eligibility and refresh tests.
The earlier Android catalog draft independently passes 36 desktop checks. The twelve-row chat preview also passes
source-level interaction checks for all rows, Home/idle changes, focus/menu protections and state handoff; no new
visual browser or physical-TV acceptance is claimed. The new reference is not yet wired into
`HomeView` or `Recs`; the next integration must replace the four-row renderer assumptions, source caps and metadata
fields, localize new labels, add visible-row-aware prefetching, and verify focus/refresh and app-opening paths on an
authorized TV build. No TV was changed by this planning pass.

Source facts and detailed primary citations are in [MATCHING_RESEARCH.md](MATCHING_RESEARCH.md), including
[Kitsu's catalog implementation](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/addon.js),
[Streaming Catalogs' loader](https://github.com/rleroi/Stremio-Streaming-Catalogs-Addon/blob/master/src/server/index.js),
[TMDB movie discovery](https://developer.themoviedb.org/reference/discover-movie),
[TMDB TV discovery](https://developer.themoviedb.org/reference/discover-tv), and
[Academy award records](https://www.oscars.org/oscars/ceremonies/2025).
