# Merge readiness — 27 October 2026

The finalization fixes the three unique blockers found when reviewing optimization implementation
`2554711` against main baseline `9ee35f5`. The owner explicitly authorized finalizing and pushing to
main. Code, tests, the README and the measured report are included in one pull request.

## Review findings resolved

| Finding | Final behavior | Verification |
|---|---|---|
| Optional cleanup could block Original/watchdog recovery | Core restoration catches optional failures; absent, malformed and refused records remain journaled while other targets continue. Pending support packages are excluded from blanket enabling. | Failure-injection host cases plus Android integration source review |
| Journaled cleanup could falsely succeed on retry | Retry probes actual state, checkpoints before mutation and preserves the first original default/enabled state. Refused changes remain recoverable. | Retry/refusal/checkpoint tests against the production recovery engine |
| Package availability could remain stale in themes/screensaver | Every process has a 30-second monotonic cache bound; theme/screensaver sessions invalidate sooner. Older workers cannot replace newer results. | Positive/negative expiry, clock rollback, invalidation and concurrent-probe cases |

Standalone support restoration still reports incomplete work. Essential factory launcher, setup,
screensaver and watchdog recovery proceeds even if an optional restore is incomplete. The three TCL
component entries are restore-only; firmware refused disabling them in the device session.

## Final validation

`tools/run-host-checks.sh` runs 31 offline checks: eight cache scenarios, 15 cleanup scenarios and eight
Python audit tests. `tests/registry.csv` records these suites. The Android CI gate now runs them too.
Full local checks use JDK 17, Android platform 34 and locally available build-tools 35.0.0 in isolated
copies, preserving private configuration and the owner's personalized APK. Exact build-tools 34.0.0
validation runs in the pull request before merging. Translation, LG/Samsung packaging, legacy web and
whitespace checks are included.

The Android recovery integration is source reviewed and build checked; these new failure cases were
not induced on the owner's TV. No new TV install or changes occur during finalization. The installed
optimization build and its one-restart evidence remain separate from subsequent recovery/cache fixes.

## Device and documentation evidence

[TCL_G10_OPTIMIZATION.md](TCL_G10_OPTIMIZATION.md) includes the actual About screenshot, controlled
measurements, unchanged settings/history checks, privacy cleanup and recovery checkpoint details.
README includes that screenshot and retains the historical-baseline caveat. Cold launch improvement
and smoother typical frames are not established. This is a source merge, not a new binary release.

GitHub Wiki lives in a separate repository and is not part of the atomic code/documentation merge.
The consolidated documentation chat can publish Wiki material from the checked-in report afterward.
