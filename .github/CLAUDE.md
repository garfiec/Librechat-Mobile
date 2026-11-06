# GitHub CI/CD

Setup runbooks for every release workflow: `docs/RELEASING.md`.

## Apple App Store upload — `.github/workflows/appstore.yml`
- **Manual only** (`workflow_dispatch`, required `tag`); cutting a GitHub release never uploads to Apple.
- One `upload` job on `xcode-27` (Xcode pinned, see Environment), `environment: release` (reviewer-gated secrets).
- Xcode **cloud signing** via an App Store Connect API key: no certificates or profiles stored anywhere;
  `-allowProvisioningUpdates` manages them.
- Archives the tag with scheme `iosApp AppStore`, configuration `Release-AppStore`
  (`SWITCHBOARD_CHANNEL=appstore`), and fails unless the archived Info.plist says `SwitchboardChannel=appstore`.
  Plain `Release` is the open-source channel (release.yml's sideload IPA).
- `xcodebuild -exportArchive` with `method: app-store-connect`, `destination: upload` (no altool/fastlane).
- `IOS_BUILD_NUMBER_SUFFIX` (run_number×100 + run_attempt) makes the "Stamp Version" phase emit a unique
  `CFBundleVersion` (`YYYYMMPP.N`) per upload.
- Uploads `dSYMs/` as artifact `dSYMs-<tag>-<run>-<attempt>` (90 days) before the App Store upload;
  after that, App Store Connect's copy is the only one.
- Secrets (`release` environment): `ASC_API_KEY_P8_BASE64`, `ASC_API_KEY_ID`, `ASC_API_ISSUER_ID`, `APPLE_TEAM_ID`.

## Google Play upload — `.github/workflows/play.yml`
- **Manual only** (`workflow_dispatch`: `tag`, `track` default `internal`, `status` — `draft` until the app's
  first release has been reviewed). Cutting a GitHub release never uploads to Google.
- Refuses any tag not `vYYYY.MM.N`: an `-rcN` shares its final release's versionCode, which Play accepts once.
- `ubuntu-latest`, `environment: release`. Builds `:app:bundlePlayRelease -PbuildPlay` (the `playRelease`
  variant is disabled without the flag), signed with the same `SIGNING_*` key as the open-source APK.
- Fails unless the bundle is `com.garfiec.librechat.play` with the release certificate, then uploads it and the
  R8 mapping via `r0adkll/upload-google-play` (SHA-pinned). Native debug symbols ride in the bundle
  (`play` flavor `debugSymbolLevel`).
- Extra secret: `PLAY_SERVICE_ACCOUNT_JSON` (runbook: "Distribution channels").

## F-Droid reproduce — `.github/workflows/fdroid-reproduce.yml`, `scripts/fdroid-reproduce.sh`
- Triggers: stable release **published** (prereleases skipped), `workflow_dispatch` with `tag`, and PRs to
  `develop` touching the workflow or script (rebuild the latest release). Don't run it from release.yml: a
  draft's assets 404, which reads as a mismatch.
- Rebuilds the tag in `registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie` with fdroidserver master and the **live** fdroiddata
  recipe; `fdroid build` compares against our published APK and checks the signer against
  `AllowedAPKSigningKeys`. A tag missing a `Builds:` entry gets one copied from the last. Artifact
  `fdroid-reproduce-<tag>` has the log and recipe.
- Can't gate a release (needs the published APK). On `release`, checkout is the tag, so only tags containing
  this workflow run automatically; dispatch for older ones.

## F-Droid watch — `.github/workflows/fdroid-watch.yml`, `scripts/check-fdroid-published.py`
- Daily 15:17 UTC + `workflow_dispatch`; builds nothing. Fails when F-Droid's latest cycle lists our latest
  stable release under `failedBuilds`, or the release is > 4 days old and F-Droid still suggests an older
  versionCode. So it is red daily until F-Droid catches up.
- Both F-Droid workflows alert **only** via GitHub's failed-workflow email (reproduce: the release publisher;
  watch: whoever last changed its `cron:` line); nothing posts to issues or releases. GitHub disables
  scheduled workflows after 60 days without repository activity.

## CI — `.github/workflows/ci.yml`
Triggers: push to `develop`, PRs targeting `develop`. Concurrency cancels in-progress runs on the same branch.

- `lint`: `scripts/check-native-test-names.py` (fails on any backtick-quoted declaration name, main or test, Kotlin/Native rejects:
  `. , ; : / \ < > [ ]`, in any iOS-compiled source set — the JVM accepts some, so otherwise only `ios`
  notices); `./gradlew detekt :app:lint --continue` (detekt covers every source set; `DetektConventionPlugin`);
  uploads detekt SARIF to Code Scanning (needs Advanced Security on private repos) + lint HTML.
- `test`: `./gradlew test`.
- `android`: `:app:assembleDebug` (both `fossDebug` and `playDebug`);
  `:feature:chat:assembleDebugAndroidTest` as a compile-only gate (keep it scoped: a repo-wide
  `assembleDebugAndroidTest` also compiles `:app`'s stale `androidTest`); `scripts/verify-speech-services.py
  --manifest-only` (both flavors declare the speech `<queries>`; the device half runs locally only). Uploads
  the `fossDebug` APK (90 days) and posts its link to the PR.
- `ios`: `iosSimulatorArm64Test` for `:shared`, `:core:ui`, `:core:common`, `:core:data`, `:core:model`,
  `:core:network`, `:core:logging`, `:feature:chat`, `:feature:conversations` (runs `iosTest` and `commonTest`
  for iOS). **A new module with a `commonTest` or `iosTest` must be added to this list**, or its tests never
  compile or run for iOS in CI (the ubuntu `test` job can't run Kotlin/Native). Caches `~/.konan`. Then
  `xcodebuild` for the simulator (arm64, no signing); the framework comes from the Xcode "Compile Kotlin
  Framework" phase (`:shared:embedAndSignAppleFrameworkForXcode`), no separate link step.
- `ios-release-link` (OOM canary): `:shared:linkReleaseFrameworkIosArm64`, the Release device link the
  release/appstore archives run. Debug/simulator builds skip whole-program optimization, so only this catches
  the Release link's DevirtualizationAnalysis OOMing the 7 GB runner (KT-80367); don't drop it.
- CI does no release signing. Instrumented tests are never executed in CI (no emulator); `:feature:chat`'s
  suite is compile-only. The iOS app is built, not uploaded.

## Environment
- `lint`, `test`, `android`: `ubuntu-latest`, JDK 21.
- iOS jobs (`ios`, `ios-release-link`, release's `ios`, appstore's `upload`): `xcode-27` (Apple Silicon; there
  is no `iosSimulatorX64` target), JDK 21.
- Xcode is pinned to `'27.1'`, not `latest-stable`: only an iOS 27.1 SDK build fills the iPhone Duo inner
  display, and `latest-stable` drifts. setup-xcode matches a plain version only against non-beta Xcode, so a
  beta pin fails at "Select Xcode". Bump the pin deliberately, in all four jobs together.
