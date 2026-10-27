# Vibe TV

**No Ads, No Bloat, Only Vibes.**

A remote-first home for Android TV: your apps, useful weather, and room to keep watching. Open source, built on [Free My TV](https://github.com/simbasjungles/free-my-tv), with full credit to its creators.

[Install](INSTALLATION.md) · [Feature status](FEATURES.md) · [Hardware & apps](COMPATIBILITY.md) · [Contribute](CONTRIBUTING.md) · [Screenshots](documentation/SCREENSHOTS.md) · [Design](DESIGN.md)

![Vibe TV development build on the TCL test TV: shortcuts, weather, app dock and Continue Watching](documentation/screenshots/home-playback.png)

*TCL home capture from the integrated Settings + playback baseline (`e740614`), October 27, 2026. The Settings capture below is updated to `b88a229`; see the [capture record](documentation/SCREENSHOTS.md). Android’s installed app label and package identifiers remain unchanged.*

## The experience

- **A quiet home.** No sponsored launcher rows or autoplaying launcher promotions. Streaming apps retain their own ads and subscriptions.
- **Your remote, your apps.** A generous app dock, All Apps, inputs, live TV, and sleep controls.
- **Weather at a glance.** Current conditions, six hours and six days together, sunrise and sunset.
- **Recommendations by shelf.** For You, Anime, Movies and TV Shows; picks require your own TMDB key.
- **Continue Watching, opt-in.** Playback history from the metadata apps share. YouTube resume has been tested; Netflix and Prime have known metadata limits. [App-by-app results](COMPATIBILITY.md#streaming-app-results).
- **Settings for a TV.** A Vibe TV header and one continuous settings document. **Themes** chooses the home design, **Home Layout** arranges Vibe TV 1’s sections, and **Preferences** holds language, temperature, appearance and sounds. Each has a distinct icon; moving down the left rail reveals its content immediately.
- **Three home designs.** Vibe TV 1, Simba (Free-My-TV) and Lamplight, with Google TV last as the factory-home recovery choice. Vibe TV 1 lets you reorder or hide home sections.
- **A way back.** Android mode switching disables selected packages rather than uninstalling them, with Original mode and a crash watchdog. This still changes the TV: read the installation guide first.

## Where the project stands

Home, playback and Settings have been integrated into `main` after their device checks. This remains an actively developed source build. The public name is **Vibe TV**; its Android package remains `io.freemytv` and its APK remains `FreeMyTV.apk` so existing installations can keep receiving updates signed with the same key. The repository remains `pukrvi/free-my-tv`. No new published Vibe TV release is claimed here.

The primary target is a TCL Smart TV Pro running Android 12, with a 1920 × 1080 reference layout. Other hardware support comes from upstream until a contributor supplies a Vibe TV test report. LG/webOS and Samsung/Tizen packaging is retained, but those interfaces have not been verified to match the latest Android work.

![Vibe TV Settings: Themes, Home Layout and Preferences](documentation/screenshots/settings-themes.png)

**Themes** changes the home design. **Home Layout** moves or hides Vibe TV 1 sections. **Preferences** changes language, temperature, light/dark appearance and sounds. Open **Settings** from the home shortcut; **TV Settings** opens Android’s own controls.

The [feature register](FEATURES.md) records what exists, where its code lives, what was tested, and what remains unfinished. The [compatibility register](COMPATIBILITY.md) distinguishes launching an app from actually recording and resuming a title.

## Try it or help build it

Follow [INSTALLATION.md](INSTALLATION.md) to build and install this fork. A generic build needs no private configuration and has no TMDB recommendations:

```sh
./build.sh --generic
```

Contributions include bug reports, translations, documentation, remote navigation checks and hardware reports. If you fork or test Vibe TV, please submit your **exact manufacturer/model, OS and firmware, tested commit, app versions, and which actions worked or failed** using the [hardware report template](.github/ISSUE_TEMPLATE/hardware_report.yml). This is a community request, not an extra license restriction.

Start with [CONTRIBUTING.md](CONTRIBUTING.md). Device work follows [ANDROID_TV_TESTING.md](ANDROID_TV_TESTING.md). [Privacy](PRIVACY.md), [security reporting](SECURITY.md), and [credits](CREDITS.md) explain the boundaries and provenance.

## License

[MIT](LICENSE). Preserve the upstream copyright and license notices when redistributing.
