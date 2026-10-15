# GitHub CI/CD

## Workflow: App Store Upload

File: `.github/workflows/appstore.yml`

- **Manual only** (`workflow_dispatch`) with a required `tag` input (e.g. `v2026.07.3`) —
  cutting a GitHub release never uploads to Apple.
- Single `upload` job: `xcode-27` runner with Xcode pinned to 27.1 (see Environment), `environment: release` (reviewer-gated secrets).
- Xcode **cloud signing** via an App Store Connect API key — no certificates or profiles
  stored anywhere; `-allowProvisioningUpdates` manages them in Apple's cloud.
- Archives the tagged commit with the `iosApp AppStore` scheme and `Release-AppStore` configuration
  (`SWITCHBOARD_CHANNEL=appstore`), then fails unless the archived Info.plist says
  `SwitchboardChannel=appstore`. The plain `Release` configuration is the open-source channel,
  which release.yml's sideload IPA uses.
- Then `xcodebuild -exportArchive` with
  `method: app-store-connect`, `destination: upload` uploads during export (no altool/fastlane).
- Passes `IOS_BUILD_NUMBER_SUFFIX` (run_number×100 + run_attempt) so the "Stamp Version"
  build phase emits a per-upload-unique `CFBundleVersion` (`YYYYMMPP.N`).
- Secrets (in the `release` environment): `ASC_API_KEY_P8_BASE64`, `ASC_API_KEY_ID`,
  `ASC_API_ISSUER_ID`, `APPLE_TEAM_ID`. Setup runbook: `docs/RELEASING.md`.

## Workflow: Google Play Upload

File: `.github/workflows/play.yml`

- Refuses any tag that is not `vYYYY.MM.N`: an `-rcN` shares its final release's versionCode,
  which Play accepts only once.
- **Manual only** (`workflow_dispatch`) with `tag`, `track` (default `internal`) and `status`
  (`draft` until the app's first release has been reviewed) inputs. Cutting a GitHub release
  never uploads to Google.
- `ubuntu-latest`, `environment: release`. Builds `:app:bundlePlayRelease -PbuildPlay` — the
  `playRelease` variant is disabled without that flag — signed with the same `SIGNING_*` release
  key as the open-source APK.
- Fails unless the bundle is `com.garfiec.librechat.play` and carries the release certificate,
  then uploads it and the R8 mapping with `r0adkll/upload-google-play` (SHA-pinned).
- Extra secret: `PLAY_SERVICE_ACCOUNT_JSON`. Setup runbook: `docs/RELEASING.md` "Distribution
  channels".

## Workflow: CI

File: `.github/workflows/ci.yml`

### Triggers

- Push to `develop`
- Pull requests targeting `develop`

### Jobs

#### `lint`
- `scripts/check-native-test-names.py` — fails on a backtick-quoted name Kotlin/Native rejects
  (`. , ; : / \ < > [ ]`) in any source set compiled for iOS. The JVM accepts some of them (`,`
  for one), so without this only the `ios` job notices.
- `./gradlew detekt :app:lint --continue` (`detekt` covers every source set of every module; see `DetektConventionPlugin`)
- Uploads merged detekt SARIF to GitHub Code Scanning + lint HTML report

#### `test`
- `./gradlew test`
- Uploads test results XML

#### `android` (Build Android App)
- `./gradlew :app:assembleDebug`, which builds both channel flavors (`fossDebug`, `playDebug`)
- `./gradlew :feature:chat:assembleDebugAndroidTest` — compile-only gate for the chat instrumented
  suite, which *runs* on a local emulator (`connectedDebugAndroidTest`), never in CI. Scoped to the
  one module deliberately: a repo-wide `assembleDebugAndroidTest` would also compile `:app`'s stale
  `androidTest` sources.
- Uploads the `fossDebug` APK as an artifact (90-day retention) and posts its download link to the PR

#### `ios` (Build iOS App)
- Selects Xcode 27.1 (pinned; see Environment)
- Runs `iosSimulatorArm64Test` for every KMP module with tests: `:shared`, `:core:ui`,
  `:core:common`, `:core:data` (which have an `iosTest`), plus `:core:model`, `:core:network`,
  `:core:logging`, `:feature:chat` and `:feature:conversations` (`commonTest` only). That task runs
  the module's `iosTest` and its `commonTest` compiled for iOS. The `ubuntu` `test` job cannot run
  Kotlin/Native tests, so a module left off this list never has its tests compiled or run for iOS
  in CI: a new module with a `commonTest` or `iosTest` must be added.
- Caches `~/.konan`
- Runs `xcodebuild` for the iOS simulator (arm64-only, no signing). The Kotlin framework is built by the Xcode "Compile Kotlin Framework" build phase (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`) — no separate link step.

#### `ios-release-link` (iOS Release Link (OOM canary))
- `./gradlew :shared:linkReleaseFrameworkIosArm64` — the Release device link that release.yml/appstore.yml archives run
- Exists because the Release link's DevirtualizationAnalysis pass OOM'd the 7 GB runner on every release Jul 2–25, 2026 (KT-80367) while PR CI stayed green: Debug/simulator builds skip whole-program optimization, so code growth crossing the heap ceiling was invisible until release day. This job fails the PR that crosses it instead.

### Environment

- `lint` + `test` + `android` jobs: `ubuntu-latest`, JDK 21
- iOS jobs (`ios`, `ios-release-link`, release's `ios`, appstore's `upload`): `xcode-27` (Apple Silicon — required since there's no `iosSimulatorX64` target), JDK 21
- Xcode is pinned to `'27.1'`, not `latest-stable`: per Apple's iPhone Duo guidance only an iOS 27.1 SDK build fills the inner display, and `latest-stable` follows whatever non-beta Xcode the image adds next. setup-xcode matches a plain version only against a non-beta Xcode, so a pin to a beta fails at "Select Xcode". Bump the pin deliberately, in all four jobs together.
- Concurrency: cancels in-progress runs for same branch

### Notes

- No release signing configured yet
- Instrumented/UI tests are never *executed* in CI (no emulator) — `:feature:chat`'s suite is only
  compiled, as a gate. Executed test coverage in CI is unit tests plus the iOS tests of the nine modules above.
- The debug APK is uploaded as an artifact; the iOS app is built but not uploaded
- Detekt SARIF is uploaded to GitHub Code Scanning (requires GitHub Advanced Security for private repos)
