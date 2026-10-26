# Android TV device test runbook

Use this procedure to verify Android launcher changes on the user's TCL Android TV. A successful build is not device
verification. The task must explicitly authorize installing or launching the app on the TV; otherwise ask first.

Integrated main runtime matches final Settings `e740614` with playback `9d4473b`; the earlier `stable` tag is a historical baseline. Read [FEATURES.md](FEATURES.md) and [COMPATIBILITY.md](COMPATIBILITY.md) before making feature claims.

## Target and privacy

- TV: `192.168.1.9:5555`.
- Settings branch: `settings-nav`; the separate home task uses `ui/yours` in its own worktree. Verify the active branch
  and preserve its existing changes; do not build one task from the other task's checkout.
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

The recorded key sequence is a historical home check. For **Vibe TV 1**, use the current visible section order rather than assuming a fixed number of presses: from the dock, Up reaches quick actions; Down reaches populated Continue Watching or the first populated recommendation shelf in the default order. Custom order/visibility must be followed literally, skipping informational weather. Simba and Lamplight have different navigation and need their own fresh captures.

## Settings verification

Wait for any other thread to release the TV before installing or sending input. Preserve the latest combined home and Continue Watching source in the test build. A prior Settings-only APK must not replace newer playback fixes.

Launch the Home activity with `am start -W -a android.intent.action.MAIN -c android.intent.category.HOME`; inspect the resting screenshot before navigating. From the first dock app, UP then RIGHT twice and OK opens Home Settings. Do not assume remembered home focus after returning from another activity.

Save fresh captures under `/tmp/free-my-tv-tv-check/continuous-settings/`:

1. Capture startup with Home Screen selected in the left column. Its thumbnail choices are ordered **Vibe TV 1**, **Simba (Free-My-TV)**, **Lamplight**, then **Google TV**. Both columns use one surface separated by a vertical rule. Confirm the active check and focused tile are distinguishable.
2. RIGHT enters the first thumbnail. RIGHT/LEFT crosses columns; DOWN/UP crosses grid rows. Read the labels before OK so the navigation test does not accidentally choose Google TV and run factory recovery.
3. When Vibe TV 1 is selected, **Home sections** appears immediately below Home Screen in the rail and continuous document. It is absent for Simba and Lamplight, with no empty gap or inaccessible hidden node. Use labels rather than fixed press counts to navigate afterward.
4. Jump to Screensaver from the left. Capture Home Settings visible below, confirming short sections do not occupy an entire screen. RIGHT enters Preview. DOWN traverses My Photos and Watch from the screensaver, then crosses to Language in Home Settings; confirm the left selection follows it.
5. LEFT returns to Home Settings in the navigation. DOWN to Widgets and capture the anchor. RIGHT starts on Weather; verify no hours-versus-days selector, Continue Watching is preserved, and coin controls appear only when Quotes is on.
6. Jump to Shelf, RIGHT, and navigate beyond the ninth item. Capture lower rows with focus visible. Hold OK on an existing item, move once, then Back to cancel; confirm saved order and focused-item visibility are restored.
7. In Vibe TV 1's Home sections, record the current order/visibility. Reorder once, cancel and verify restoration. Toggle one section and restore it; verify the home honors the change and returns to the recorded composition. Inspect Reset layout confirmation and cancel unless a reset is explicitly part of the check. Do not alter Simba or Lamplight composition through this editor.
8. Inspect Top Buttons and Sleep Timer anchors. Open the duration picker and cancel without changing its value. Back returns to navigation, and another Back exits. Jump to About and traverse the read-only comparison, recommendation status and notices; confirm a real document end.
9. With theme switching authorized, select each custom design and return Home for a fresh full-resolution capture. Vibe TV 1 must preserve its existing row layout and playback baseline. Simba must match the original upstream `90908dc5bce178feb52e9868947cc039c64730fc` home (tabs, hero, dock and drawer), not a redesigned approximation. Lamplight must render through its independent adapter/renderer, with usable app/settings/input navigation. Return to the user's chosen theme afterward. Google TV remains last; verify its presence and recovery wiring without activating factory restoration unless that switch is part of the authorized test.
10. Read the current HOME default; custom themes use `io.freemytv/.HomeActivity`. Do not call `set-home-activity` to implement a custom-theme switch. Leave the TV at home and release it to the next thread. Stop any temporary wake process.

Preserve the playback fixes recorded in [PLAYBACK_STATUS.md](PLAYBACK_STATUS.md), based on commit `9d4473b`. Do not claim Netflix or Prime exact titles/artwork or automatic resume from an app launch. Keep YouTube/Stremio verified artwork/progress and the JioHotstar app-art fallback, expiry/dismissal rules and existing card menus. Report which theme was checked; a passing Vibe TV 1 playback test does not automatically validate every theme's controls.

Inspect the captures at 1920×1080. Check text/value separation, one focused item, header/row rhythm, safe margins, and clipping during cross-section scrolling. Never claim a visual pass without opening the captures. Record any unavailable device check explicitly. Do not change system font, picture, sound, account, or network settings during this pass.

## Inspect the screenshots

Open all five screenshots at full resolution. Use Python/PIL to confirm they are `1920×1080` and sample pixels around
the weather, dock, chips and row boundaries. Check the actual images as well as the samples.

- Weather icons have clear space from their temperature text; hourly and daily cells do not overlap.
- Dock, chips and row labels remain visible during page scrolling. The default Vibe recommendation row labels keep a 384 design-unit pitch.
- The selected card stays within the horizontal viewport and the focused row stays on screen after scrolling.
- Exactly one recommendation card has a focus ring in each row-focused screenshot. Its title is legible over the image
  scrim and its metadata does not collide with the following row.
- UP follows visible populated sections in their configured order; hidden/empty sections close their gaps.
- Toasts do not cover the weather header or row labels.

If a check fails, name the visible or interaction defect, make the smallest source change, then rebuild, reinstall and
repeat the affected checks. Do not report a screenshot check as passing unless the screenshot was inspected. In the final
report include the build result, device steps, all screenshot paths, what passed or failed, and any remaining issue.

Earlier successful captures may be available at `/var/folders/35/r0h10d7x1zx62hx7nxv1xg280000gn/T/opencode/rows-01-rest.png`
and the corresponding `rows-02-down.png` through `rows-05-up.png`; use them as a visual reference only, and capture fresh
screenshots for each new build.
