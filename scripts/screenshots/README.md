# Screenshot and recording capture

Framed screenshots and GIF recordings for the README and the F-Droid/IzzyOnDroid store
listing, captured from the Android emulator and the iOS Simulator. Pair it with
`scripts/demo-seed/`, which fills the demo account the captures show.

- `frame.py`: puts a device frame around a raw screenshot. `--self-test` checks the frames.
- `android.py`: demo mode, fold/unfold, dark mode, framed shots and recordings on the Pixel 10
  Pro Fold emulator.
- `ios.py`: status bar override, framed shots and recordings on a booted simulator, and `login`,
  which signs the app in to the demo account through Maestro (`maestro/ios-login.yaml`).
- `sheet.py`: a contact sheet of a capture batch, with sampled frames from each GIF, for review.
- `check-readme.py`: every README image exists, and nothing in `docs/screenshots/` is orphaned.
- `play.py`: the Google Play listing images. `set out/` puts every store screenshot named in
  `play-set.toml` on a captioned 1080x1920 (phone) or 1920x1080 (tablet) canvas, since Play
  rejects a bare framed Fold shot, and adds the feature graphic in use. `feature` draws the
  wordmark alternative. `pair` joins two framed shots side by side for the hero. Upload the
  results by hand in Play Console.

Needs Pillow, ffmpeg and (for iOS frames) poppler's `pdftocairo`.

## Frames

- **Android:** the emulator skins Android Studio's "screenshot with frame" uses, from
  `~/Library/Android/sdk/skins/pixel_10_pro_fold/`. `closed` is the folded phone (outer
  display), `default` the unfolded one (inner display).
- **iOS:** the Simulator's own device chrome from `/Library/Developer/DeviceKit/Chrome`, picked
  by the device type. These are for the GitHub README only. App Store images use Apple's
  product bezels, under Apple's marketing guidelines, which also forbid showing an Apple device
  beside a competing one.

## Typical session

```bash
scripts/screenshots/android.py demo on                 # 12:00, full battery and signal
scripts/screenshots/android.py fold
scripts/screenshots/android.py shot out/phone.png      # 618x1248, the README/store size
scripts/screenshots/android.py unfold
scripts/screenshots/android.py shot out/tablet.png     # 1100x1134
scripts/screenshots/android.py record out/sidebar.gif --seconds 6   # drive the app meanwhile
scripts/screenshots/android.py demo off

scripts/screenshots/ios.py "iPhone 17 Pro" statusbar on   # 9:41, full battery and signal
scripts/screenshots/ios.py "iPhone 17 Pro" shot out/iphone.png

scripts/screenshots/play.py pair out/material.png out/glass.png out/hero.png   # the hero
scripts/screenshots/play.py set out/play/      # Play images from the store screenshots
```

Where the files go is in `fastlane/README.md`: store screenshots are replaced in place under
`fastlane/metadata/android/en-US/images/`, which the README links straight into; GIFs and
README-only shots go in `docs/screenshots/`.

## Gotchas

- The emulator has two displays, so every capture names one (`screencap -d`); `android.py`
  picks whichever is lit. Without `-d` the PNG starts with a warning and won't open.
- The debug build ships LeakCanary, which adds a second launcher entry. Launch the app by
  component: `adb shell am start -n com.garfiec.librechat.debug/com.garfiec.librechat.MainActivity`.
- `simctl io recordVideo` only writes a frame when the screen changes, so a still screen
  records as a single frame.
