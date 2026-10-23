# Switchboard iOS App

Compose Multiplatform iOS client for LibreChat. The iOS app is a thin SwiftUI wrapper around the full Compose Multiplatform UI — all screens, navigation, and business logic are shared with Android via KMP.

## Architecture

The entire UI is rendered by Compose Multiplatform. SwiftUI is only used as a hosting layer:

```
iOSApp.swift          — App entry point: initializes Koin DI, renders LibreChatComposeView
ComposeView.swift     — UIViewControllerRepresentable wrapping MainViewController() (CMP root)
KoinHelper.swift      — Swift-side Koin dependency resolver (for debugging / Swift-native code)
SharedFrameworkTest.swift — Compile-time smoke test for KMP + SKIE bridging
```

The `:shared` Gradle module exports `core:common`, `core:model`, `core:network`, and `core:data` as a single `Shared.framework`. All feature modules and `core:ui` are included for Compose Multiplatform screen sharing.

### Key components

- **SKIE**: Enhances Kotlin-Swift interop automatically — sealed classes become Swift enums (`onEnum(of:)`), `Flow<T>` becomes `AsyncSequence`, `suspend fun` becomes `async throws`
- **DI**: Koin is initialized in `iOSApp.init()` via `IosKoinHelperKt.startIosKoin()`
- **Crash Reporting**: `installCrashReporting()` sets up a Kotlin/Native unhandled exception hook that logs via Kermit + NSLog and raises as NSException
- **Platform Impls**:
  - `IosTokenDataStore` (`core/data/src/iosMain/`) — Keychain-backed token storage via Security.framework
  - `IosConnectivityObserver` (`core/common/src/iosMain/`) — NWPathMonitor via `nw_path_monitor_*` APIs
  - `IosSharedModule` (`shared/src/iosMain/`) — Koin module wiring Darwin Ktor engine + all platform deps

## Building

### Prerequisites
- Apple Silicon Mac (Intel Macs are not supported — no `iosSimulatorX64` target)
- Xcode 15.0+ with iOS 16.0+ SDK (for toolchain and simulators — IDE not needed)
- JDK 17+ (for Gradle/Kotlin compilation)
- Android Studio or IntelliJ IDEA (recommended IDE for all code editing)

### Build and Run

The Kotlin framework is built automatically by Xcode's **Compile Kotlin Framework**
build phase, which runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
([direct integration](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)).
That task builds the right framework slice for the active SDK/configuration, sets up
linking, and syncs Compose resources — so there is no separate `link*Framework` step to
run by hand. The framework is static (`isStatic = true`), so it is linked directly into
the app binary and is **not** embedded in `Frameworks/`.

> Because the build phase shells out to Gradle, **User Script Sandboxing must stay
> disabled** for the `iosApp` target (`ENABLE_USER_SCRIPT_SANDBOXING = NO`). If you ever
> toggle it on, run `./gradlew --stop` before rebuilding.

#### Simulator

```bash
# Build the Xcode project (Gradle builds the framework as a build phase)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath iosApp/build build

# Boot simulator, install, and launch
xcrun simctl boot "iPhone 16"
xcrun simctl install booted iosApp/build/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.garfiec.librechat.ios
```

#### Physical Device

```bash
open iosApp/iosApp.xcodeproj
```

In Xcode:
1. Select your iPhone from the device picker (top toolbar)
2. Go to **Signing & Capabilities** → set your **Team** (required for device signing)
3. If your team can't register `com.garfiec.librechat.ios`, change **Bundle Identifier** to one of your own (e.g. `com.example.switchboard`). Don't commit it. From the command line, pass `PRODUCT_BUNDLE_IDENTIFIER=<your id>` to `xcodebuild` instead.
4. Press **⌘R** to build and install

> **Note:** The first build takes several minutes while the Kotlin/Native toolchain downloads. You need an Apple Developer account (free tier works) for device signing.

### Known Xcode Behaviour

**Red dot on `Shared.framework` in the navigator** — cosmetic, safe to ignore. The Xcode project keeps a static display path for the framework, but the real artifact lives under `shared/build/xcode-frameworks/<Configuration>/<SDK>/` and is produced by the build phase. The linker resolves it via `FRAMEWORK_SEARCH_PATHS`, so builds are unaffected; running a build clears the indicator.

## Info.plist Permissions

The following keys are configured in `Info.plist`:
- `NSMicrophoneUsageDescription` — Microphone for voice input
- `NSSpeechRecognitionUsageDescription` — Speech-to-text
- `NSCameraUsageDescription` — Camera for photo capture
- `NSPhotoLibraryUsageDescription` — Photo library access
- `CADisableMinimumFrameDurationOnPhone` — 120Hz ProMotion support

## URL Scheme

The app registers the `librechat://` URL scheme for deep linking (conversations, OAuth callbacks), matching the Android app's behavior.

## TestFlight / App Store Distribution

Store builds are produced by the **App Store Upload** workflow
(`.github/workflows/appstore.yml`), not by hand: it archives a tagged release with Xcode
cloud signing (an App Store Connect API key is the only credential — no certificates or
profiles are stored anywhere) and uploads during export. The one-time App Store Connect
setup and the per-release runbook are in [`docs/RELEASING.md`](../docs/RELEASING.md)
("One-time App Store Connect setup" and "Uploading to TestFlight / the App Store").

Things that are already handled, so there is nothing to bump or declare per build:

- **Versions** come from `version.properties` via the *Stamp Version* build phase, which
  overwrites the committed `Info.plist` literals at build time. `CFBundleShortVersionString`
  is the numeric calver core (`-rcN` stripped); `CFBundleVersion` is `YYYYMMPP`, or
  `YYYYMMPP.N` when `IOS_BUILD_NUMBER_SUFFIX` is set, which the upload workflow does so a
  re-run never reuses a build number (App Store Connect rejects those permanently).
- **Export compliance** is declared once in `Info.plist` (`ITSAppUsesNonExemptEncryption =
  false`): the app only uses the OS's standard TLS, so uploads never wait on the manual
  compliance prompt. Revisit the key if the app ever ships its own cryptography.
- **Signing identity** stays `Apple Development` in both configurations. The archive is
  development-signed and `xcodebuild -exportArchive` re-signs it for distribution, which
  is the standard automatic-signing flow; do not switch the Release config to
  `Apple Distribution`, and do not commit a `DEVELOPMENT_TEAM`.

### Local archive (optional)

For a one-off upload from a Mac with a paid developer account, set your Team under
*Signing & Capabilities* (leave it uncommitted), then **Product → Archive** and distribute
from the Organizer. The Kotlin framework is built by the *Compile Kotlin Framework* build
phase inside the archive, so no separate Gradle step is needed. A local archive stamps a
plain `YYYYMMPP` build number, which collides with any workflow upload of the same version;
prefer the workflow, or give the CLI archive an explicit suffix:

```bash
IOS_BUILD_NUMBER_SUFFIX=1 xcodebuild archive \
  -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath iosApp/build/iosApp.xcarchive \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<your-10-char-team-id>
```
