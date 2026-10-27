# Privacy and permissions

Vibe TV inherits Free My TV's local settings and opt-in media widgets. It has no project account system or analytics integration. Streaming apps have their own accounts, ads and privacy practices.

## Network services

TMDB supplies recommendations, images and streaming availability when configured. Open-Meteo receives the configured city coordinates (or the time-zone-derived location) for forecasts. Binance is used when market quotes are enabled; Watchmode is used when its optional key is configured. These providers receive ordinary connection data and the requests needed for their features.

The integrated playback implementation may query TMDB using a watched movie/show title to find missing artwork, and download YouTube thumbnails using a known video ID. That can disclose the requested title or ID to the image/data provider. Do not describe that revision as keeping every playback-derived value on the TV. Image requests depend on available metadata; artwork is not guaranteed.

## Playback history

Now Playing and Continue Watching start off and use Android media-session access authorized through a notification listener. The implementation reads media sessions rather than notification messages. While Continue Watching is enabled, the listener stays bound to catch metadata before an app closes its session; Now Playing alone uses the existing immediate-unbind behavior.

Local history holds titles, app identity, playback position, duration, content IDs and validated public title links when supplied. Signed CDN stream URLs, embedded credentials and arbitrary intent links are not retained as launch destinations. It keeps up to twelve entries, expiring YouTube entries after two days and other services after ten days. In Vibe TV 1, hold OK on a history card to remove it. Disabling a widget should not be assumed to erase all previously saved data. Avoid including personal viewing history in public screenshots or logs.

When an older Stremio card lacks a link, the hotfix may query Stremio’s exported search provider using its saved title. Stremio may use its catalog services to answer that query. Exact unique matches only are used. Recognized public artwork IDs can identify an episode locally without a query. A YouTube card without an ID opens YouTube search for its saved title, sending that query to YouTube; the user selects the result. Bounded session polling checks content identity before applying the saved position and stops on cancellation or timeout. No notification messages or account databases are read.

## Installation and secrets

Android switching uses the TV's local ADB access to change the launcher/screensaver and disable selected packages. This is privileged device access; read the installation guide and preserve Original recovery. The app does not remove ads inside streaming apps.

Keep `config.properties`, API tokens, pairing codes, certificates, signing keys and local environment files private. Public bug reports should omit IP/MAC addresses, serial numbers, account names and raw playback dumps. Share only the minimal sanitized evidence needed to reproduce a problem.

## Optional support cleanup in the TCL support workflow

The owner-approved support procedure can reversibly disable Google feedback, Play Movies and printing through HomeMode. It journals original states and is restored through Original recovery or the watchdog. This is not a default removal policy. Calendar sync and functional TCL/security services remain enabled; this does not establish zero telemetry. Profiling reports stay local or contain sanitized counters. See [the device results](https://github.com/pukrvi/vibe-tv/wiki/Performance).

---
Source: [PRIVACY.md](https://github.com/pukrvi/vibe-tv/blob/main/PRIVACY.md). Edit the repository source, then run `python3 tools/sync_wiki.py`.
