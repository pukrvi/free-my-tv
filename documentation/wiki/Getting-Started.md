# Installing Vibe TV

[Back to the project](https://github.com/pukrvi/vibe-tv/blob/main/README.md) · [Hardware and app results](https://github.com/pukrvi/vibe-tv/wiki/Hardware-and-Apps)

Vibe TV is this fork. The Settings screen displays **Vibe TV**. Android’s installed app label remains **Free My TV**; builds use package `io.freemytv`, and produce `FreeMyTV.apk`, `FreeMyTV-webos.ipk` and `FreeMyTV-tizen.wgt`. These names are intentional compatibility identifiers. A documentation rename does not rename the installed app. Build from this fork for its changes; upstream downloads and freemytv.io do not distribute this fork. No Vibe TV release is asserted by this guide.

Before installing, understand the changes and keep **Settings → Themes → Google TV** (Original mode) available. An assistant must ask before device changes and let you perform the mode switch on the TV. Use a JDK 17 and Android SDK build-tools 34.0.0/platform android-34 for the CI baseline; the Mac JDK path below is an example, not a requirement.

## Android TV

About ten minutes. You need a Google TV or Android TV (Android 8 or newer) and a computer on the same Wi-Fi.

### Build this TCL version

From this repository, build the native Android app once and install it over Wi-Fi:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./build.sh

adb connect <TV-IP>:5555
adb -s <TV-IP>:5555 install -r FreeMyTV.apk
adb -s <TV-IP>:5555 shell am force-stop io.freemytv
adb -s <TV-IP>:5555 shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
```

Use the explicit HOME intent for verification; the `monkey` launcher command opens the app icon rather than the TV home activity.

On this TCL firmware, allow `APP_AUTO_START` so the TV's boot integration can start the launcher after a restart:

```bash
adb -s <TV-IP>:5555 shell cmd appops set io.freemytv APP_AUTO_START allow
```

Use the local build configuration and signing key already set up for this checkout. Keep `config.properties` and the
keystore private; the file contains the TMDB key, and updates must use the same signing key.

### The easy way: an AI assistant

1. **On the TV**, turn on developer mode: Settings → System → About → press *Android TV OS build* 7 times. Then Settings → System → Developer options → turn on **USB debugging**. Write down the TV's IP address (Settings → Network & Internet).
2. **On your computer**, open an AI coding assistant ([Claude Code](https://claude.com/claude-code), Codex or Antigravity) and say:
   > Install Free My TV (github.com/pukrvi/free-my-tv) on my TV at 192.168.1.50. I live in Lisbon.

   It downloads the code, installs the tools it needs, builds the app and puts it on your TV, and asks before anything important. Swap in your TV's IP and your city.
3. **On the TV**, open *Free My TV* → *Themes* → *Vibe TV 1* (or Simba / Lamplight). That's when the TV asks *Allow USB debugging?* for Free My TV: tick *Always allow* and accept.

### Upstream APK (Free My TV, not a Vibe TV release)

No tools to install besides adb. The APK on the [upstream release](https://github.com/simbasjungles/free-my-tv/releases/latest) has no TMDB key, so the home screen has no picks, and it takes your city from the TV's time zone (change it in *Home Settings* → *City*).

1. **On the TV**, do step 1 above.
2. **On your computer**, download `FreeMyTV.apk` from the release and install it:
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r FreeMyTV.apk
   ```
   On Android 14, Google Play Protect may hold the install with *Risky app blocked*: choose *More details* → *Install anyway*.
3. **On the TV**, do step 3 above.

If you built Free My TV yourself before, switch back to *Original* and uninstall it first: your build and the release are signed with different keys. For the picks, build it yourself with your TMDB key (below).

### The manual way

1. **On the TV**, do step 1 above.
2. **Tools:** a JDK (17+), the Android SDK command-line tools (build-tools 34, platform android-34) and adb. On a Mac:
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools
   sdkmanager "build-tools;34.0.0" "platforms;android-34"
   ```
   On Linux, get OpenJDK 17, Android's command-line tools and adb from your package manager, then run the same `sdkmanager` line. On Windows, use Git Bash (which Claude Code uses there) or WSL.
3. **Build:**
   ```bash
   ./build.sh
   ```
   The first run creates `config.properties`. Set your city and your [TMDB key](#the-tmdb-key) there, then run it again.
4. **Install:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r FreeMyTV.apk
   ```
   If the install seems stuck, look at the TV: on Android 14, Google Play Protect may hold it with *Risky app blocked* ("built for an older version of Android"), since Free My TV targets Android 13. Choose *More details* → *Install anyway*. Updates don't ask again.

   Chromecast with Google TV on Android 14 keeps port 5555 closed even with USB debugging on: turn on *Wireless debugging* (Developer options), pair with `adb pair <address:port> <code>`, then run `adb tcpip 5555` over that connection. Port 5555 closes again at every restart, so after one, *Original*, the watchdog and the boot check can't reach ADB until you run `adb tcpip 5555` again (the pairing stays).

   On TCL TVs, also let it run in the background (TCL blocks it by default, and the switch never starts):
   ```bash
   adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"
   ```
5. **On the TV**, open *Free My TV* → *Themes* → *Vibe TV 1* (or Simba / Lamplight). The TV asks *Allow USB debugging?* once more, now for Free My TV itself: tick *Always allow* and accept. (Run `adb disconnect 192.168.1.50:5555` first: on Android 8 to 10 the TV can hand that answer to a computer that's still connected.)

## LG TVs (webOS)

The same home screen as an LG app: the picks, the glass shelf with your apps, the drawer with inputs, weather and prices, day and night, Home Settings and the shelf editor. It installs through LG's own Developer Mode, with nothing rooted or changed on the TV. LG's home screen stays where it is, and Free My TV is one press of a number key away (or in LG's app list, on remotes without them).

You need an LG TV (webOS 3 or newer) and a computer on the same Wi-Fi, with Python 3.

1. **On the TV**, open the LG Content Store and install **Developer Mode**. Sign in with a free [LG developer account](https://webostv.developer.lge.com), turn on **Dev Mode Status** (the TV restarts), then turn on **Key Server**. Write down the IP address and the passphrase it shows.
2. **On your computer**, get the code and build. `config.properties` works as for Google TV (your city, your [TMDB key](#the-tmdb-key)):
   ```bash
   git clone https://github.com/pukrvi/free-my-tv && cd free-my-tv
   python3 webos/build.py
   ```
3. **Install.** The first time, it asks for the TV's passphrase; updates don't:
   ```bash
   python3 webos/install.py 192.168.1.50
   ```
4. **On the TV**, while Free My TV is open, hold a number key on the remote. From then on, that key opens it from anywhere. On a remote without number keys, open it from LG's app list on the home screen, like any other app.

With an AI assistant, say *"Install Free My TV (github.com/pukrvi/free-my-tv) on my LG TV at 192.168.1.50. I live in Lisbon."* It builds the app and hands you the install command, so you type the passphrase yourself.

For upstream Free My TV, the [webOS Homebrew Channel](https://github.com/webosbrew/webos-homebrew-channel) lets you skip the computer: add the repository `https://freemytv.io/webos/apps.json` in the Channel's settings, and Free My TV shows up in its list. That's the release build: it takes the city from the TV's time zone, and the picks need your own build with a [TMDB key](#the-tmdb-key). If you installed your own build, don't update it from there, or the release replaces it.

On webOS 6 and newer, LG doesn't let apps installed through Developer Mode see which apps the TV has. Free My TV then shows the well-known ones it finds (Netflix, YouTube, Prime Video, Disney+, Apple TV, Plex and more) and asks the TV, once, for the access LG's phone app uses: a message on the TV asks whether to allow Free My TV. Allow it, and from the next start all your apps show, with their icons.

Older LG TVs (webOS 3) don't let Free My TV see their apps at all, so the shelf stays empty. There your computer asks the TV instead (the TV asks once whether to allow it), and the list and icons are built into the app. Run it again after installing new apps on the TV:
```bash
python3 webos/apps.py 192.168.1.50 && python3 webos/build.py && python3 webos/install.py 192.168.1.50
```

Developer Mode lasts about 40 days: open the Developer Mode app now and then and press **EXTEND**. To remove Free My TV, run `python3 webos/install.py 192.168.1.50 --uninstall`.

## Samsung TVs (Tizen)

The same home screen as a Samsung app, with everything the LG one has: the picks, the shelf, the drawer with the inputs, the weather and the prices, day and night, and its own settings. It installs through Samsung's own Developer Mode, with nothing rooted or changed on the TV, and Samsung's home screen stays where it is.

You need a Samsung TV from 2016 or newer (Tizen 2.4+) and a computer on the same Wi-Fi. (2015 models, on Orsay or Tizen 2.3, can't run it; a Google TV streamer on HDMI can.) 2016 TVs can't run TizenBrew, so on those build it yourself (below); TV Settings isn't in the drawer there, since apps can't open the TV's settings.

1. **On the TV**, open **Apps** and type `1 2 3 4 5` on the remote. Turn **Developer mode** on, type your computer's IP address in **Host PC IP**, and restart the TV.
2. **On your computer**, download [TizenBrew Installer](https://github.com/reisxd/TizenBrewInstaller/releases/latest), the community's installer for Samsung TVs, open it and connect to your TV.
3. **Install from GitHub:** type `simbasjungles/free-my-tv` (the upstream build, not Vibe TV). On 2023 and newer TVs it asks you to sign in to your Samsung account once, to sign the app for your TV.
4. **Straight to it:** in the TV's settings, under Start Screen Options, turn on **Autorun Last App**: the TV turns on into Free My TV.

This build takes your city from the TV's time zone. For your exact city and the trending picks, build it with your own `config.properties` and [TMDB key](#the-tmdb-key): open the folder in Claude Code and say *"Install Free My TV on my Samsung TV at 192.168.1.50. I live in Lisbon."* It sets up Samsung's tools, and on 2023 and newer TVs you sign in to your Samsung account in the browser to make the certificate. Your signed build installs with `python3 tizen/install.py 192.168.1.50`, which needs only Python: no Tizen Studio, and no Rosetta on Apple Silicon Macs.

## Settings

The Android settings screen is one continuous document. The left rail jumps to sections; Right or OK enters their controls. Up/Down can cross section boundaries, and Back returns to the rail. Themes offers Vibe TV 1, Simba (Free-My-TV), Lamplight, then Google TV. Only Vibe TV 1 shows **Home Layout**: OK toggles visibility; hold OK to lift, arrows to move, OK to drop, Back to cancel. Reset layout requires confirmation.

The first values come from `config.properties`, created by the first build (Google TV, LG and Samsung use the same file). Most of them can also be changed on the TV, in *Settings* → *Preferences* or *Widgets*, and the TV's choice wins:

| Key | What it does | Default |
|---|---|---|
| `tmdb_key` | Your free TMDB key, for the picks | empty |
| `tmdb_region` | Your country (two letters): which titles are on which app | `US` |
| `watchmode_key` | Optional, free [Watchmode](https://api.watchmode.com/) key: a pick opens the title's page in its app | empty |
| `latitude`, `longitude` | Your city, for the weather and the sunset | New York |
| `city` | The name on the weather card | `Weather` |
| `hide_animation` | Leave cartoons and anime out of the picks | `false` |
| `language` | `auto` (follows the TV), or a language with a [translation](https://github.com/pukrvi/vibe-tv/blob/main/TRANSLATING.md) (`en`, `pt`, `es`, `de`, `ru`, `fr`, `ja`…) | `auto` |
| `show_crypto` | Show Binance crypto prices on home and screensaver | `true` |
| `currency` | Crypto prices in `USD`, `EUR`, `BRL`… | `USD` |
| `temperature` | `C`, `F` or `auto` | `auto` |
| `theme` | `auto` (dark from sunset to sunrise), `light` or `dark` | `auto` |
| `disable_assistant` | Also turn off Google Assistant | `false` |
| `screensaver` | `false` keeps the TV's own screensaver: Free My TV then changes only the home screen and the promo apps (Google TV) | `true` |
| `hide_services` | Streaming services to leave out of the picks even when their app is on the TV (`netflix`, `prime`, `disney`…), comma separated; for apps the TV ships that you don't subscribe to. Google TV only | empty |
| `hide_apps` | Apps to leave out of the shelf, All Apps and the picks, by id or name (part of it), comma separated; for apps the TV ships that can't be uninstalled, like Samsung's built-in Netflix. LG and Samsung only | empty |
| `shelf_apps` | The shelf's apps in this order, by id or name (part of it), comma separated; changing it wins once over the shelf picked on the TV. On Samsung, a whole app id also brings an app the TV leaves out of its list, like one installed in Developer Mode. LG and Samsung only | empty |

Changed something? Run `./build.sh` and install again. Keep `freemytv.keystore`: updates are signed with it.

### The TMDB key

Free, and it takes two minutes: create an account at [themoviedb.org](https://www.themoviedb.org/signup), then open [Settings → API](https://www.themoviedb.org/settings/api) and request a developer key. Paste the *API Read Access Token* (or the *API Key*) after `tmdb_key=`. It's your key: `config.properties` stays out of git.

### Opening a title, not just its app (optional)

With a free [Watchmode](https://api.watchmode.com/) key (`watchmode_key=` in `config.properties`), a pick opens its own page in the streaming app instead of the app's home. The free plan (2,500 credits a month) is enough: each title costs two requests the first time and is remembered after that. It works where the app takes the title's web address (tested: Prime Video and JioHotstar); where it doesn't, the pick just opens the app, as without a key.

### Your own photos (optional)

On Google TV and Android TV, with the TV connected over adb (see *Install*), send photos or whole folders from your computer:

```bash
python3 tools/photos.py <TV IP> beach.jpg hills.jpg Holidays/
```

They land in the app's own folder on the TV (no permission needed); on Android 11, which keeps adb out of other apps' folders, they go through Free My TV itself. The next time the home screen, the screensaver or Free My TV's settings open, the app imports each one: turned upright, cropped to 16:9, resized for the TV and saved without its GPS and other tags; what was sent is then deleted. The screensaver shows them in the order you sent them, instead of the picks' photos; without a TMDB key, one of them is the home screen's background, a new one every hour. *Settings* → *Screensaver* → *My Photos*: the arrows turn them on or off, OK removes them. Up to 100; JPEG, PNG and WebP, and HEIC on Android 9 and newer.


## Restore the original home

On the TV, open **Vibe TV Settings → Themes → Google TV** (Original mode) before uninstalling. Uninstalling while the replacement mode is active can leave disabled packages disabled. See the recovery instructions in [CLAUDE.md](https://github.com/pukrvi/vibe-tv/blob/main/CLAUDE.md#adapting-it-to-another-tv). Updates require the same signing key; back up your key privately.

---
Source: [INSTALLATION.md](https://github.com/pukrvi/vibe-tv/blob/main/INSTALLATION.md). Edit the repository source, then run `python3 tools/sync_wiki.py`.
