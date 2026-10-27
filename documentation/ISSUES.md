# Issue history and current work

Collected from Home, Settings, Continue Watching, Documentation, Optimization and Stremio recommendation chats on 27 October 2026. GitHub Issues was disabled until this collection. Existing reports were backfilled in reported conversation-phase order; original timestamps were often unavailable. Independent chats are not assigned an invented precise order. GitHub creation dates describe filing, not discovery.

Fixed reports are closed for traceability. Open reports include real defects, provider limitations and explicitly unverified acceptance; those categories must not be conflated. Current playback hotfix: [#22](https://github.com/pukrvi/vibe-tv/issues/22).

| Issue | Area | State at collection | Finding |
|---|---|---|---|
| [#2](https://github.com/pukrvi/vibe-tv/issues/2) | Settings | Closed: historical fix | Settings wastes width and requires nested-page navigation |
| [#3](https://github.com/pukrvi/vibe-tv/issues/3) | Settings | Closed: historical fix | Home Layout long OK can toggle instead of lifting an item |
| [#4](https://github.com/pukrvi/vibe-tv/issues/4) | Settings | Closed: historical fix | Settings weather controls and recommendation copy are misleading |
| [#5](https://github.com/pukrvi/vibe-tv/issues/5) | Playback | Closed: historical fix | Continue Watching hides other home row headings |
| [#6](https://github.com/pukrvi/vibe-tv/issues/6) | Playback | Closed: historical fix | Continue Watching misses thumbnails and YouTube progress visibility |
| [#7](https://github.com/pukrvi/vibe-tv/issues/7) | Playback | Closed: historical fix | Home cards need persistent removal and viewing-history expiry |
| [#8](https://github.com/pukrvi/vibe-tv/issues/8) | Playback | Closed: historical fix | Playback history can be missed while the home is hidden |
| [#9](https://github.com/pukrvi/vibe-tv/issues/9) | Playback | Open | Netflix and Prime do not expose a usable saved title or content link |
| [#10](https://github.com/pukrvi/vibe-tv/issues/10) | Playback | Open | JioHotstar provides progress but no episode thumbnail |
| [#11](https://github.com/pukrvi/vibe-tv/issues/11) | Settings | Closed: historical fix | Project and Settings labels do not match the Vibe TV name |
| [#12](https://github.com/pukrvi/vibe-tv/issues/12) | Documentation | Closed: historical fix | Documentation overstates playback support and has stale privacy/screenshot claims |
| [#13](https://github.com/pukrvi/vibe-tv/issues/13) | Verification | Open | Launcher recovery and final Settings accessibility acceptance need current-device evidence |
| [#14](https://github.com/pukrvi/vibe-tv/issues/14) | Playback | Closed: historical fix | Netflix app-level fallback card loses branded artwork |
| [#15](https://github.com/pukrvi/vibe-tv/issues/15) | Optimization | Closed: historical fix | Optional cleanup restoration can block essential Google TV recovery |
| [#16](https://github.com/pukrvi/vibe-tv/issues/16) | Optimization | Closed: historical fix | Interrupted optional cleanup can falsely report success on retry |
| [#17](https://github.com/pukrvi/vibe-tv/issues/17) | Optimization | Closed: historical fix | App availability cache becomes stale in screensaver and retained themes |
| [#18](https://github.com/pukrvi/vibe-tv/issues/18) | Recommendations | Open | Public Stremio recommendation catalogs are blocked by the TMDB-key gate |
| [#19](https://github.com/pukrvi/vibe-tv/issues/19) | Recommendations | Open | Recommendation routing ignores dock preference and needs regional availability checks |
| [#20](https://github.com/pukrvi/vibe-tv/issues/20) | Recommendations | Open | Non-TMDB recommendation identities and image paths can collide |
| [#21](https://github.com/pukrvi/vibe-tv/issues/21) | Recommendations | Open | Recommendation title opening depends on optional Watchmode links |
| [#22](https://github.com/pukrvi/vibe-tv/issues/22) | Playback | Open | Older Continue Watching cards reopen the wrong destination or lose the saved position |
| [#23](https://github.com/pukrvi/vibe-tv/issues/23) | Infrastructure | Open | GitHub Actions starts no runs for the fork |
| [#24](https://github.com/pukrvi/vibe-tv/issues/24) | Verification | Open | Platform and hardware acceptance matrix is incomplete |

## Reports after the initial collection

- [#25](https://github.com/pukrvi/vibe-tv/issues/25): wiki/performance publication gap, fixed through [PR #26](https://github.com/pukrvi/vibe-tv/pull/26), main `f4a1492`.
- [#27](https://github.com/pukrvi/vibe-tv/issues/27): isolated history-test preference cleanup defect found during #22 reruns; fixed and independently verified in the hotfix branch, pending its PR merge.

## Working agreement

1. Search existing issues before filing; add new evidence to the matching report. Record original discovery date/time only when known.
2. Reproduce the bug and record the tested revision, app version and hardware. Saving artwork/progress, opening an app, opening the title and resuming at a saved point are separate results.
3. Work on an isolated branch from current main. Link the issue in the PR, include meaningful regression coverage and document device evidence and limits.
4. Coordinate shared files and exclusive TV access before input/install. Preserve newer work when integrating.
5. Merge only after review, passing checks and explicit owner authorization. A green local build is not a remote CI run.

The live issue state on GitHub supersedes this historical index. Do not publish credentials, signed stream URLs, serials, network addresses or account details in reports.
