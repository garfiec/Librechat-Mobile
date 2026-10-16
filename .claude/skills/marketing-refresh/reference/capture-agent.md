# Capture agent brief

The captures are delegated to one background agent that drives the emulator and the
simulators. This file is what it needs, plus a template for each batch.

## Before the first batch, check:

- [ ] **The emulator.** The AVD is `Pixel_10_Pro_Fold`, and `adb devices` shows `emulator-5554`.
      The foss debug build (`./gradlew :app:installFossDebug`, package
      `com.garfiec.librechat.debug`) is installed and signed in as the persona, at
      `http://10.0.2.2:3080` for a local server. Background prefetch is on.
- [ ] **The simulators**, if the README has iOS rows: booted, with the Debug build installed
      (`com.garfiec.librechat.ios`) and signed in with
      `scripts/screenshots/ios.py "<device>" login --env-file <seed env>`.
- [ ] **The skins.** `~/Library/Android/sdk/skins/pixel_10_pro_fold/{closed,default}` exist, and
      Roboto is at `~/Library/Android/sdk/platforms/android-29/data/fonts`. `play.py` uses that
      font.
- [ ] **The output folder** is in the session's scratchpad, not the repo. Files are copied into
      the repo only in Phase 7.
- [ ] **The host isn't overloaded.** `uptime` should show a 1-minute load under about 15 before
      anything is recorded.

## Brief template

Fill in the shot list and send it as the agent's prompt. Send later batches to the same agent
with `SendMessage`; it keeps what it learned about navigating the app.

```text
Capture these Android shots for the README and store listing. Save into <folder>.

Run `scripts/screenshots/android.py demo on` first and `demo off` at the end. Use the /android
skill to drive the UI (taps, scrolling, reading the screen). Use android.py for posture, dark
mode, shots and recordings.

For every still, write both the framed shot and `--raw NAME-raw.png`.

Shots, in this order (grouped so the look changes as rarely as possible):
1. NAME.png: <posture>, <accent>, <light|dark>, <Material|Liquid Glass>. <Screen, and how to reach
   it>. <What must be visible>. <What must not be (keyboard, a button over the content)>.
2. NAME.gif: <posture>, record <N> s: <the gesture, step by step>.
…

For every shot, check before moving on:
- the clock reads 12:00 and the status bar has no notification icons;
- the theme matches the row;
- no keyboard (unless the shot is about typing), no toast, no error banner, no dialog;
- only the persona's name and avatar appear;
- nothing renders broken. If something does, note it and skip the shot; don't work around it.

For every recording, check the frame count before encoding. Under about 30 frames means the
capture starved; retry once, then report it rather than keep trying.

Don't commit anything, and don't change the repo. Leave the app in <resting look>.

Report: each file with its size and what it shows, everything that failed and why, any bug
noticed, and the state you left the device and app in.
```

## How each kind of shot is reached

- **Chats** open by deep link: `adb shell am start -a android.intent.action.VIEW -d
  'librechat://conversation/<id>'`. Add `?message=<id>` to land on a message, which is how search
  opens one. The seeder's state file has the conversation ids.
- **Settings pages, the media page and Search** have no deep link; tap to them. Search is also
  Ctrl+Shift+F with a hardware keyboard.
- **Live-only panels:**
  - The agent's clarifying questions: send the prompt the seeder printed and capture while the
    panel is up.
  - Tool approval: needs the server policy on, and an agent that actually calls the tool. In
    2026-10 the agent never called it, so there is no approval shot.
- **Inline artifacts** render only when that artifact type's toggle is on (Settings → Chat →
  Artifacts). Every toggle defaults to off.
- **A pinned home-screen icon:** "Add to home screen" from the artifact's menu, with an emoji and a
  label. Clear the launcher of Google and other third-party icons before shooting the home
  screen, and leave off the debug build's "Switchboard Dev" icon.

## Android gotchas

- **The emulator has two displays.** `screencap` without `-d <id>` writes a warning into the
  PNG. `android.py` picks whichever display is lit.
- **The debug build has two launcher entries** (LeakCanary adds one). Launch by component:
  `adb shell am start -n com.garfiec.librechat.debug/com.garfiec.librechat.MainActivity`.
- **Folding or unfolding turns the screen off and locks it.** `android.py fold`/`unfold` wake and
  unlock it again.
- **The frame's rounded corner clips the clock**, and the camera hole covers an icon. `demo on`
  turns on the emulator's corner cutout, which moves both clear.
- **Gboard floats a toolbar over the app** in the emulator. Press Back to hide it.
- **Recording starves under host load.** At a load around 27, the folded display recorded 1–5
  frames for a back gesture. At around 48, the app itself stopped responding ("isn't
  responding"). Record when the host is quiet. The emulator's own recorder
  (`adb emu screenrecord start <file>`, then `adb emu screenrecord stop`) got more frames than
  `screenrecord` when one wouldn't record.
- **screenrecord writes one keyframe** and only the frames where the screen changed. Re-encode
  with `android.py encode` (it trims after resampling) instead of seeking with `-ss`.
- **The /android skill can restart system_server.** That once reset another app's state. Don't
  run other device work alongside a capture.
- **A database from a newer build crashes an older one on launch.** Clear the app's data
  (`pm clear`) before installing an older build.

## iOS gotchas

- **The status bar is `ios.py statusbar on`**: 9:41, full signal, Wi-Fi and battery, no carrier
  name. Apple's guidelines ask for all of that.
- **`simctl recordVideo` writes frames only when the screen changes**, and it renders the corner
  mask black. `ios.py record` covers the mask with the frame.
- **Deep links don't navigate on iOS.** Everything is reached by tapping. Maestro flows
  (`maestro test`) are the scriptable way to tap. `login` is in `scripts/screenshots/maestro/`.
- **An http:// server needs App Transport Security to allow local networking.** If the first
  login is blocked, patch only the built `.app`'s Info.plist (PlistBuddy, `NSAllowsLocalNetworking`),
  never the repo.
- **Known iOS rendering bugs** (check they're still open before relying on this): Mermaid renders
  blank, and the artifact sheet is see-through below its header. Shoot artifacts full screen and
  avoid Mermaid on iOS.
- **No simulator for an unreleased device.** iPhone Duo needs the Xcode that ships its runtime.
  Use the newest device that has one, and say which in the PR.

## Reviewing a batch

```bash
scripts/screenshots/sheet.py <folder>/sheet.jpg <folder>/*.png
scripts/screenshots/sheet.py <folder>/gifs.jpg --frames 6 <folder>/*.gif
```

Look at every tile, against the checklist in the brief. Send the agent one message with all the
retakes, each with what was wrong.
