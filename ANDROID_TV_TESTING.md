# Android TV device test runbook

Use this procedure to verify Android launcher changes on the user's TCL Android TV. A successful build is not device
verification. The task must explicitly authorize installing or launching the app on the TV; otherwise ask first.

## Target and privacy

- TV: `192.168.1.9:5555`.
- Stable baseline: tag `stable` at `b920f4a`. Keep `settings-nav` and `continue-watching` in separate worktrees; verify the
  active branch and preserve its existing changes. Build only after the task branches are deliberately integrated.
- ADB: `/Users/vishmac/Library/Android/sdk/platform-tools/adb`.
- TV-specific project configuration belongs in the repository. PII stays in the user's local-only environment file.
- Read only environment values needed for the task. Never print or commit the environment file.
- `config.properties` contains the private TMDB key. Never read, print, or commit it.

If the TV does not connect at the saved address, check ADB once and ask the user to confirm the current IP before
continuing. Do not repeatedly package or install the same APK while the device is unreachable.
If another task is using this TV, wait for it to release the device before sending input, rebooting, installing, or
capturing screenshots.

## Build and install

1. Inspect `git status` and the source diff. Preserve existing user changes.
2. Build the code being verified once:

   ```sh
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./build.sh
   ```

   `build.sh` produces `FreeMyTV.apk`. Rebuild only after a source change; after a build failure, fix the cause before
   trying again. Do not repeatedly package unchanged code.
3. Install and launch the APK once:

   ```sh
   ADB=/Users/vishmac/Library/Android/sdk/platform-tools/adb
   $ADB connect 192.168.1.9:5555
   $ADB -s 192.168.1.9:5555 install -r FreeMyTV.apk
   $ADB -s 192.168.1.9:5555 shell am force-stop io.freemytv
   $ADB -s 192.168.1.9:5555 shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
   ```

Use the explicit HOME intent for verification; `monkey -p io.freemytv` opens the app icon, which is Settings, rather than
the TV home activity (`HomeActivity`).

## Capture navigation states

Save each screenshot to `/tmp/free-my-tv-tv-check/`. Wait six seconds after launch and about one second after each remote
keypress so animations settle.

```sh
ADB=/Users/vishmac/Library/Android/sdk/platform-tools/adb
SCREEN_DIR=/tmp/free-my-tv-tv-check
mkdir -p "$SCREEN_DIR"
sleep 6
$ADB -s 192.168.1.9:5555 exec-out screencap -p > "$SCREEN_DIR/rows-01-rest.png"

for key in 20 20 20; do
  $ADB -s 192.168.1.9:5555 shell input keyevent "$key"
  sleep 1
done
$ADB -s 192.168.1.9:5555 exec-out screencap -p > "$SCREEN_DIR/rows-02-down.png"

for key in 22 22 22; do
  $ADB -s 192.168.1.9:5555 shell input keyevent "$key"
  sleep 1
done
$ADB -s 192.168.1.9:5555 exec-out screencap -p > "$SCREEN_DIR/rows-03-right.png"

$ADB -s 192.168.1.9:5555 shell input keyevent 19
sleep 1
$ADB -s 192.168.1.9:5555 exec-out screencap -p > "$SCREEN_DIR/rows-04-up.png"

$ADB -s 192.168.1.9:5555 shell input keyevent 19
sleep 1
$ADB -s 192.168.1.9:5555 exec-out screencap -p > "$SCREEN_DIR/rows-05-up.png"
```

From the dock, Down enters Continue Watching when it has entries; otherwise it enters the first populated recommendation
shelf. Left and Right move across the focused row. From Continue Watching, Down enters For You and Up returns to the
resume row; from For You, Up returns to Continue Watching when present, otherwise to the dock.

## Settings verification

After the home-row checks finish and the device is released, open Settings from the home screen. The home starts focused
on the first dock app: press UP, RIGHT twice to **Home Settings**, then OK. Settings should open with **Home Screen**
selected in the rail and its content visible on the right. Capture `settings-01-home-screen.png`.

In Settings, Up and Down on the rail should immediately update the right panel. Right or OK enters the panel, with the
first row ready. Back or Left returns to the selected rail item. Use the following sequence; wait about one second after
each navigation group and capture each state:

1. From the initial rail, press DOWN three times to **Widgets** without pressing Right or OK. Capture
   `settings-02-widgets.png`; confirm the content is already visible, with a weather toggle and city row but no
   hours-versus-days selector. Press Right to enter the rows.
2. Press Back, DOWN once to **Shelf**, and capture `settings-03-shelf-preview.png`. Press Right and capture
   `settings-03-shelf.png`; the first app row should have one clear focus treatment.
3. Press Back, DOWN once to **Top Buttons**, capture `settings-04-top-buttons-preview.png`, then press Right to enter.
4. Press Back, DOWN once to **Sleep Timer**, then Right and OK. Capture `settings-05-sleep-choices.png`; press Back to
   close the list without changing the timer.
5. Press Back from the panel to the rail, then Back once more to leave Settings.

Save these captures under `/tmp/free-my-tv-tv-check/`. Inspect all at full resolution. Confirm the rail stays visible,
the right panel updates on each rail move, the panel uses the remaining screen width, the first row is focused on entry,
Back returns to the matching rail item, and the timer picker closes without applying a change. Do not toggle persistent
preferences during visual checks unless you record and restore their original values.

## Continue Watching verification

After Settings is integrated, enable **Home Settings → Widgets → Continue Watching** using the remote. This grants the
row access to the same media sessions used by Now Playing. Play a short public video in YouTube, pause it, return Home,
and confirm the row shows its title, app and progress. Capture the resting and focused-card states. Select the card and
confirm the video reopens and playback resumes. This task intentionally enables the feature and leaves it on. Keep the
TV awake during the sequence with the remote; do not change the screen timeout. Netflix, Prime Video and Stremio depend
on the metadata and resume behavior each app exposes; verify their app launch paths only when a usable session is
available, and report any service-specific limit.

## Inspect the screenshots

Open all screenshots at full resolution. Use Python/PIL to confirm they are `1920×1080` and sample pixels around
the weather, dock, chips and row boundaries. Check the actual images as well as the samples.

- Weather icons have clear space from their temperature text; hourly and daily cells do not overlap.
- Dock, chips and row labels remain visible during page scrolling. Row labels keep a 384 design-unit pitch.
- The selected card stays within the horizontal viewport and the focused row stays on screen after scrolling.
- Exactly one recommendation card has a focus ring in each row-focused screenshot. Its title is legible over the image
  scrim and its metadata does not collide with the following row.
- UP returns from Anime to For You, then to chips. Empty rows, if present, keep their spacing and show the quiet empty
  state.
- Toasts do not cover the weather header or row labels.

If a check fails, name the visible or interaction defect, make the smallest source change, then rebuild, reinstall and
repeat the affected checks. Do not report a screenshot check as passing unless the screenshot was inspected. In the final
report include the build result, device steps, all screenshot paths, what passed or failed, and any remaining issue.

Earlier successful captures may be available at `/var/folders/35/r0h10d7x1zx62hx7nxv1xg280000gn/T/opencode/rows-01-rest.png`
and the corresponding `rows-02-down.png` through `rows-05-up.png`; use them as a visual reference only, and capture fresh
screenshots for each new build.

## Continue Watching follow-up checks

Read [PLAYBACK_STATUS.md](PLAYBACK_STATUS.md) for the provider results and limits of the current build before retrying.

Run `tools/run-history-checks.sh 192.168.1.9:5555` after installing the current build when device testing is authorized.
The temporary instrumentation APK uses isolated preferences/cache to verify elapsed playback position, missing paused
fields, completion, persistent dismissal, Hotstar support and provider expiry. It never clears the user's history.
Capture and inspect For You, Anime, Movies and TV Shows with a populated Continue Watching row; headings must remain
visible. Hold OK on a resume card and a recommendation, check Cancel/Back and release suppression, then verify removal
with a test title. Revisit Home to confirm it stays hidden. Use a public YouTube video to verify fresh session artwork;
older entries may use an app logo until the next playback publishes artwork. Check JioHotstar's title and app launch.
Prime/Netflix can publish generic/null metadata; distinguish app launch from verified title/progress capture. Android's
Watch Next provider was empty on this TV during the October 27 check. Netflix also blocks screenshots/accessibility text.
