# Store rules, sizes and limits

Checked October 2026. Re-check the linked pages before a refresh; stores change these quietly.

## Google Play

Source: Play Console Help, "Add preview assets to showcase your app"
(https://support.google.com/googleplay/android-developer/answer/9866151).

| Asset | Rule | How `play.py` meets it |
|---|---|---|
| Phone screenshots | 2–8; JPEG or 24-bit PNG, no alpha; each side 320–3840 px; longest side at most twice the shortest | 1080×1920 canvas, RGB |
| Large-screen screenshots | 16:9 or 9:16, each side 1080–7680 px; Play recommends at least 4 for large-screen visibility | 1920×1080 canvas |
| Feature graphic | 1024×500, JPEG or 24-bit PNG, no alpha; no device imagery | `play.py feature` |
| Text in screenshots | captions are allowed; the screenshot must still show the app. House rule: the caption stays in the top 20% | caption band |
| App name | 30 characters | the F-Droid title (35) doesn't fit |
| Short description | 80 characters | |
| Full description | 4000 characters | |
| Status bar | no notification icons, no carrier name | demo mode |

Device frames are allowed (not on Wear OS). A bare framed Fold shot fails twice: transparent
corners, and a folded Fold is 2.02:1. Hence the canvas.

Nothing in the repo uploads to Play: `play.yml` sends only the bundle and the R8 mapping. The
listing is edited by hand in Play Console, for the Play build (`com.garfiec.librechat.play`),
whose defaults differ from the open-source build. Check every claim about a default against
`ChannelPolicy` and the `perChannel(…)` defaults in `AppPreferences`.

## F-Droid and IzzyOnDroid

The rules are in `fastlane/README.md` and enforced by `scripts/check-fdroid-metadata.py`:
- title 50 characters, short description 80, full description 4000;
- screenshots in `phoneScreenshots/` and `tenInchScreenshots/`, PNG or JPEG, shown in filename
  order (hence the numeric prefixes);
- `icon.png` 512×512, `featureGraphic.png` is the listing banner;
- changelogs are written by CI, never by hand.

Transparency is fine here, so the store screenshots are the plain framed shots at half size:
618×1248 folded, 1100×1134 unfolded. F-Droid reads all of it from the **tagged commit**: a
refresh that merges after the release is cut waits for the next one.

## Apple (App Store and the README)

From Apple's marketing guidelines for apps (Apple Design Resources and the App Store marketing
guidelines):
- Never show an Apple device beside a competing product. That rules out an Android-and-iPhone
  hero, and it's why iOS shots sit in their own README rows.
- Use the product bezels unmodified, and keep the status bar full (signal, Wi-Fi, battery).
- Show the real app, on the current OS, with fictional account data.

The user's reading, recorded 2026-10-15: these govern App Store marketing. The README therefore
uses the Simulator's own device chrome (`frame.py ios`), and App Store screenshots, when they're
made, use Apple's product bezels and are uploaded in App Store Connect. App Store screenshots are
outside this skill.

## GitHub README

- Content is about 880 px wide. The screenshots table uses `width="280"` for a phone and
  `width="380"` for a tablet, and `width="186"` for each tile of a 2×2 grid in a tablet cell. A
  full-width hero uses `width="100%"`.
- Recordings are GIFs (GitHub doesn't autoplay video in a README). Aim for under about 4 MB each:
  280–320 px wide for a phone, 380 px for a tablet, 15 fps, at most about 12 s.
- Every `<img>` needs alt text. `scripts/screenshots/check-readme.py` checks that every image
  exists and that `docs/screenshots/` has no orphans.
