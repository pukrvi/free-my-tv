# Matching three recommendation sources into twelve home shelves

Researched 27 October 2026 against official TMDB documentation, the two addon implementations, and the Academy's own ceremony records. This is evidence and proposed matching behavior; no TV operation or production source change was performed by this research pass.

## Product contract

The user's correction is twelve separate rows: five main shelves, then seven Explore shelves. Explore is an umbrella in the information hierarchy, not one shelf containing all the themes. All-sources means a union of eligible candidates, not a requirement to insert a fixed number from every source into every row.

| Order | Shelf | Recommended candidate rule |
|---|---|---|
| 1 | For Me | All sources; mix movies, series and anime. Rank for the explicitly stated fantasy/science-fiction/Dune taste, quality, fresh/trending relevance, and diversity. |
| 2 | Anime Airing | Only `kitsu-anime-airing`; preserve the source's ranked Kitsu entries, up to its twenty-entry first page. No TMDB or Streaming Catalogs fill. |
| 3 | New Movies | All sources, movie type; require a known past release date for a recent-release claim, with trending as a secondary score. |
| 4 | New Shows | All sources, series type; distinguish a new premiere from a returning series with recent episodes/seasons. |
| 5 | Trending | Membership exclusively from TMDB movie/TV trending endpoints; metadata and routing may still be enriched elsewhere. |
| 6 | Indian | All sources, movies and TV; explicit India origin/production metadata, including regional languages. |
| 7 | Sci-Fi | All sources, movies and TV; explicit science-fiction genre/keyword evidence. Fantasy preference also boosts For Me. |
| 8 | Korean | All sources, movies and TV; explicit South Korean origin/production metadata. |
| 9 | Japanese | All sources, movies and TV, including anime; explicit Japanese origin/production evidence. |
| 10 | Oscars | Films from a locally curated, Academy-verified award record. Awards are not a genre or a generic high-rating threshold. |
| 11 | Must-Watch Movies | All sources; quality-qualified film pool with country/region and decade rotation. This is our editorial recommendation rule. |
| 12 | Must-Watch TV | All sources; quality-qualified series pool with country/region and decade rotation. This is our editorial recommendation rule. |

The exact recency window and quality thresholds are product choices, not properties supplied uniformly by the sources. Suggested initial windows are ninety days for premieres/releases and returning seasons, with a clearly labeled wider fallback rather than treating old trending titles as new. Oscars is film-only; the Academy's 2026 ceremony explicitly honors movies released in 2025. ([Academy ceremony record](https://www.oscars.org/oscars/ceremonies/2026))

## Source facts that affect matching

### TMDB

Movie and TV identities occupy different namespaces. `/find/{external_id}?external_source=imdb_id` can return movie, TV and episode results; select the matching media type and reject ambiguity. Do not assume an IMDb episode ID identifies an entire series. TMDB also provides separate movie and TV external-ID endpoints. ([Find by ID](https://developer.themoviedb.org/reference/find-by-id), [movie external IDs](https://developer.themoviedb.org/reference/movie-external-ids), [TV external IDs](https://developer.themoviedb.org/reference/tv-series-external-ids))

Movie Discover supports release-date bounds, vote count/average, genres, keywords, origin country, original language and regional provider/monetization filters. TV Discover separately exposes `first_air_date` and `air_date` constraints. These distinguish recent series premieres from series with recently aired content. Neither is a service's date-added timestamp. Origin country and original language are distinct filters; the response/display language does not establish origin. Resolve official movie/TV genre lists separately. ([Movie Discover](https://developer.themoviedb.org/reference/discover-movie), [TV Discover](https://developer.themoviedb.org/reference/discover-tv), [movie genres](https://developer.themoviedb.org/reference/genre-movie-list), [TV genres](https://developer.themoviedb.org/reference/genre-tv-list))

Trending supplies separate movie and TV feeds with `day` or `week` windows. Feed membership is the reliable source boundary for the dedicated Trending row. Preserve its rank/window rather than replacing membership with a generic popularity score. Dune seed recommendations can be obtained from the movie recommendation endpoint after the intended film is identified. ([Movie trending](https://developer.themoviedb.org/reference/trending-movies), [TV trending](https://developer.themoviedb.org/reference/trending-tv), [movie recommendations](https://developer.themoviedb.org/reference/movie-recommendations))

Per-title watch-provider data describes availability by country, including subscription/rental/purchase categories. It does **not** return complete provider title deep links. TMDB requires JustWatch attribution when using this data. Keep availability, selected app and launch URL as separate fields. ([Watch-provider documentation](https://developer.themoviedb.org/reference/movie-watch-providers))

### Anime Kitsu addon

The addon defines separate trending, airing, popular and rating catalogs. Airing uses `status=current` sorted by descending average rating. Its Kitsu API helper uses a twenty-entry page for ordinary catalogs, whereas trending defaults to fifty. Therefore the requested Anime Airing row should consume its full ordinary page, without the draft client's six-item sampling cap. Twenty is a requested maximum; if the live response has fewer usable entries, do not fill from another source. ([Addon handlers](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/addon.js), [Kitsu helper](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/lib/kitsu_api.js))

Catalog fields include Kitsu ID, optional IMDb ID, type, subtype, aliases, year/range, genres, artwork and a field called `imdbRating`. The generator fills that rating from Kitsu's `averageRating / 10`; the name does not prove it is an IMDb score. The preview whitelist does not include a precise start date or status, and `releaseInfo` can be `2013-` or a year range. A year-only value cannot establish a ninety-day release window. Full metadata carries episode information, but fallback episode dates may be synthesized from the series start when exact dates are missing. Keep date precision and rating provenance. ([Metadata implementation](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/lib/metadata.js))

Multiple Kitsu season/part entries can map to one IMDb series, with `fromSeason`, `fromEpisode`, episode exceptions and specials. Consequently, one IMDb ID is enough for a **title-group** association, but not enough to replace every Kitsu season/part's exact identity. Use two levels: a group for cross-source recommendation deduplication and a preserved Kitsu entry for airing membership and Stremio launch. Do not collapse the twenty-entry airing page into a handful of IMDb franchises or send every season to season one. ([Mapping/enrichment implementation](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/lib/metadataEnrich.js))

### Streaming Catalogs addon

The upstream regular catalog loader fetches provider-specific JustWatch trending results and enriches them with Cinemeta metadata. `getLatest` is a TODO in the JustWatch service. Regular provider catalog order is therefore evidence for that feed's trending/discovery ranking, not an authoritative latest-release or newly-added ordering. Cinemeta enrichment can fail, leaving only a basic ID, name, type and poster. Enrich missing dates, countries, genres and TMDB identity before assigning a specialized shelf. ([JustWatch service](https://github.com/rleroi/Stremio-Streaming-Catalogs-Addon/blob/master/src/services/justwatch.js), [metadata enrichment](https://github.com/rleroi/Stremio-Streaming-Catalogs-Addon/blob/master/src/services/cinemeta.js))

A critical upstream limitation: the loader requests Netflix and Disney in GB, Prime in US, and JioHotstar in IN. The configured regular catalog handler parses a country value but returns already loaded provider arrays without applying that country. A configured IN manifest is consequently not sufficient evidence of Indian availability. The hosted deployment can differ from current upstream source; independently verifying per-title IN offers remains necessary in either case. ([Server loader](https://github.com/rleroi/Stremio-Streaming-Catalogs-Addon/blob/master/src/server/index.js), [catalog handler](https://github.com/rleroi/Stremio-Streaming-Catalogs-Addon/blob/master/src/server/routes/catalog.js))

## Proposed normalized record

Keep these logical fields even if the eventual Java representation is smaller:

- Stable title-group ID and media kind; separate edition/season entry ID.
- Alias set: `tmdb:movie:ID`, `tmdb:tv:ID`, `imdb:ttID`, and each `kitsu:ID`.
- Every originating feed plus its source rank, fetch timestamp and optional trend window.
- Title, alternate titles, release/premiere date with precision, recent episode/season date with precision.
- Genre/keyword evidence, production/origin countries, original language, and rating value/source/vote count.
- Verified award assertions with category, ceremony year, win/nomination status and Academy URL.
- Availability offers, country, checked-at time, chosen package, exact launch link, and unresolved state.

Merge by explicit, type-compatible IDs. Use normalized title/year only to create a review candidate; never silently merge remakes, sequels, a movie and a TV adaptation. Maintain field provenance and unknown values. An unresolved entry can still appear in a broad mixed row, but should not be guessed into Indian/Korean/Japanese, recent-release, or Oscar rows.

For multi-source ranking, normalize each source's rank to a comparable percentile; do not add raw TMDB popularity, Kitsu rating and Streaming Catalogs position. A second source may provide a small corroboration bonus, not a second card. Avoid multiplying scores merely because a title appears in several provider feeds from the same addon.

## Country, genre and award edge cases

Use IN/KR/JP origin evidence; a country-of-availability match says nothing about the work's origin. Preserve co-productions. Missing-country entries remain unclassified. Japanese is broader than anime; Anime Airing's source boundary remains stricter than the Japanese Explore row. Indian includes more than Hindi-language content. These are matching policy recommendations based on the distinct origin, language and watch-region fields above.

TV's combined science-fiction/fantasy classification can be broader than a strict Sci-Fi label. If precise science-fiction keywords or source genre evidence are unavailable, either leave the entry out of strict Sci-Fi or label the row Sci-Fi & Fantasy. Do not turn all Animation into anime. Fetch and cache the separate official movie and TV genre dictionaries rather than assuming their IDs are interchangeable.

There is no award filter among TMDB's documented Discover parameters. Use a small versioned local award file sourced from the Academy, then resolve its movie IDs deliberately. This adds no third addon or live runtime award service. Record release year separately from ceremony year; retain the exact nominated/winning category and do not equate Best Picture nomination with a Best Picture win. The 2025 Academy record lists *Dune: Part Two* as a Best Picture nominee and a Sound/Visual Effects winner; the 2022 record lists *Dune* in its own distinct ceremony. ([2025 record](https://www.oscars.org/oscars/ceremonies/2025), [2022 record](https://www.oscars.org/oscars/ceremonies/2022))

## Diversity and refresh policy recommendations

A For Me profile should start from the user's declared science-fiction/fantasy and Dune preference; do not claim a learned viewing profile or access to private Netflix/Prime history. Add quality and recent/trending relevance without forcing every title to match the same genre. This avoids making twelve rows different views of the same ten titles.

For must-watch rows, a vote-count threshold and a shrunk rating estimate reduce tiny-sample outliers. Country/region and decade buckets should rotate deliberately, so high-volume English-language and recent titles do not fill every visit. Treat thresholds, buckets, seed choices and the personal profile as editable local policy that another user's agent can adapt.

Keep shelf order and selected titles stable during a Home visit. Rotate on Home return or the existing ten-minute selection boundary; network/image callbacks should enrich cards without independently reshuffling them. Do not globally remove a title from Anime Airing or TMDB Trending merely because it already appears in For Me. Apply a repeat penalty to mixed/explore shelves while preserving strict-source rows.

Before release, validate missing keys/IDs/dates/countries, many-Kitsu-to-one-IMDb mappings, movie/TV ID collisions, unknown availability, unavailable addons, empty/small airing responses, duplicate provider feeds, and award nomination versus win semantics. Provider app launch is separate acceptance work: watch-provider metadata alone cannot establish exact-title or playback-position reopening.
