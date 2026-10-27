# Contributing to Vibe TV

Thank you for helping make TV software calmer and more useful. Bug reports, testing on another model, translations, accessibility checks and documentation are welcome alongside code.

## Choose a contribution

Check [feature status](FEATURES.md) and [known compatibility limits](COMPATIBILITY.md), then search existing issues and pull requests. Use a bug report for broken behavior, a feature request for an idea, or a hardware report for a test result. Discuss broad architecture, new dependencies, safety changes or a new platform before investing in a large patch. Small focused fixes can go straight to a pull request.

If you fork Vibe TV, please send back your exact TV model and the apps/actions you tested—even when something fails. This request helps the community and adds no condition to the MIT license.

## Set up a development checkout

```sh
git clone https://github.com/pukrvi/free-my-tv.git
cd free-my-tv
git switch -c codex/describe-your-change
```

Use any descriptive branch name; `codex/` is the assistant default. Android uses Java 8 source with JDK 17, SDK platform android-34 and build-tools 34.0.0 for CI. There is no Gradle project. Python 3 builds the webOS/Tizen packages. See [INSTALLATION.md](INSTALLATION.md) for platform-specific tools and device setup.

```sh
./build.sh --generic
python3 tools/translate.py --check
```

A generic build avoids personal configuration. A personal build uses git-ignored `config.properties`; fill it locally yourself and never include it, API keys or signing keys in a report or patch. Preserve your signing key for updates.

## Make and verify the change

Read [CLAUDE.md](CLAUDE.md) for safety and architecture, and [DESIGN.md](DESIGN.md) for UI work. Keep changes focused and explain the reason for behavior changes. Android uses Canvas drawing and remote focus; keep heavy work and per-frame allocations out of drawing. Older web TVs require ES5 and restricted CSS. Protect system components and preserve Original mode and the watchdog.

See [TRANSLATING.md](TRANSLATING.md) for catalog workflow and language coverage. For changed user-facing strings, update all translation catalogs and run `python3 tools/translate.py --check`. Hand-maintained reference translations should be edited by a speaker, not replaced wholesale. Preserve interpolation placeholders and plural forms. Current right-to-left layout support remains incomplete.

Before proposing a merge, run:

```sh
tools/check.sh
git diff --check
```

`tools/check.sh` covers the Android build, translations, webOS/Tizen packaging and older-browser constraints. Record failures and missing tools accurately. Add meaningful regression coverage for behavior changes; a screenshot alone does not verify playback or recovery.

Device testing follows [ANDROID_TV_TESTING.md](ANDROID_TV_TESTING.md). Get the owner's approval before installing, changing preferences, switching modes, rebooting or sending device input. Coordinate access when another person/chat owns the TV. Document what was changed and restored; leave mode switching to the person at the TV. Never uninstall/clear system app data or alter picture, sound, network or account settings as part of a launcher check.

For privacy and performance investigation, start with the [read-only audit](documentation/TV_AUDIT.md).
It compares selected package states with our existing policy and reads memory/frame counters without
applying cleanup. The [CleanYourTV comparison](documentation/CLEANYOURTV_REVIEW.md) records candidates,
firmware-specific exclusions and measurement limits. Its offline tests run with
`python3 -m unittest discover -s tools -p 'test_tv_audit.py'`.

## Open a pull request

Use the template and describe the problem, resulting behavior, validation and remaining limits. Include issue links, exact tested revision/build, hardware and app versions, and before/after screenshots for UI changes. State “not tested” for unavailable hardware; launching an app is distinct from resuming its content. Update the feature/compatibility registers and screenshot record when claims change.

Maintain upstream attribution and license notices. Separate refactoring from unrelated behavior changes where practical. Maintainers review safety, behavior, clarity and verification before merging; contributors should respond to review and rerun affected checks after changes. No response-time or acceptance guarantee is implied. A maintainer handles release tagging and publication after combined-build checks.

## Community conduct

Be respectful, specific and patient. Critique code and behavior, not people. Harassment, discriminatory remarks and sharing another person's private information are unacceptable. Maintainers may remove harmful content or restrict participation. Raise a concern privately with a repository maintainer through an available contact channel; do not post sensitive details publicly.

For suspected vulnerabilities, follow [SECURITY.md](SECURITY.md). For documentation navigation and ongoing work, start at [README.md](README.md).
