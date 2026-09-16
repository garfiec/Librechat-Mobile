# RFP text for gitlab.com/fdroid/rfp

Kept in the repo so it stays in sync with the release process it describes. Two placeholders
below can only be filled by an actual verified release — see *Before filing*.

**Title:** Switchboard — native LibreChat client

---

### App description

Switchboard is a native Android client for [LibreChat](https://www.librechat.ai/), a
self-hosted chat front-end for language models. The user points it at their own LibreChat
server during onboarding; the app has no backend of its own and ships no default server.

- **Source code:** https://github.com/garfiec/Librechat-Mobile
- **License:** MIT
- **Application ID:** `com.garfiec.librechat`
- **Issue tracker:** https://github.com/garfiec/Librechat-Mobile/issues
- **Current release:** `v<VERSION>` (versionCode `<CODE>`)
- **minSdk / targetSdk:** 26 / 35 (compileSdk 36)

### Signing

Requesting **developer-signed with reproducible-build verification**
(`Binaries:` + `AllowedAPKSigningKeys`), not F-Droid-signed.

The app already distributes via GitHub Releases and Obtainium under this key. Android
refuses in-place upgrades across a signing-key change, so an F-Droid-signed build would
create an install base that could never migrate to or from the existing distribution
without uninstalling — which destroys the local chat database. Going developer-signed from
the first published version means that population never exists.

Signing certificate SHA-256:
`668a71966a070614d144955d83e7236a3ced77f8640857c3fa84b03ccde40e59`

It is also published in the project README so users can verify their own downloads, and
every release carries a SLSA build-provenance attestation from GitHub Actions.

### Reproducibility

Verified reproducible in `registry.gitlab.com/fdroid/fdroidserver` against the published
release. <!-- FILL IN: paste the apksigcopier compare result before filing. -->

Two things were fixed specifically to make this work, both already merged:

- The release workflow previously assembled the APK *before* committing the version bump
  and tagging. `BuildConfig.GIT_SHA` is stamped from `git rev-parse --short=8 HEAD` at
  build time and compiled into the dex, so every published APK carried the SHA of the
  commit *preceding* its own tag and could never be rebuilt from that tag. The workflow now
  commits and tags locally, builds, and pushes only on success — so a failed build burns
  neither a tag nor a release.
- `versionCode` is now spelled out as a literal in `version.properties` rather than only
  being computed, so update detection can find it. The Gradle plugin re-derives it and
  fails the build on a mismatch, so the literal cannot silently drift.
- Native libraries are no longer stripped. The project compiles no native code — every
  `.so` arrives prebuilt from Maven — but stripping them requires an NDK, and AGP silently
  skips it when none is present (`Unable to strip the following libraries, packaging them
  as they are`). Our CI runners have an NDK and your buildserver image does not, so the
  same tag yielded different `.so` bytes in the two places. The app now sets
  `jniLibs.keepDebugSymbols`, so the packaged file is byte-for-byte what Maven serves and
  no toolchain sits in the path. This was found by running `fdroid build` against your
  image before submitting, and it is why the reproducible version is one we cut after it.

Releases before that fix are permanently unreproducible and are not offered for inclusion.

### Notes for the recipe

- **`UpdateCheckData` is required.** The `versionCode`/`versionName` literals live in the
  *root* `version.properties`. `common.manifest_paths` only scans `AndroidManifest.xml` and
  `build.gradle(.kts)` under the app module, so nothing points at that file by default —
  without the line in the recipe below, the app would build and publish and then never be
  offered as an update.
- **`UpdateCheckMode` needs its tag filter.** Release candidates are tagged
  `vYYYY.MM.P-rcN`, and the version scheme strips the `-rcN` suffix when packing
  `versionCode` — so `v2026.09.1-rc1` and `v2026.09.1` share versionCode `20260901`. A scan
  that runs while only the candidate exists would set `CurrentVersionCode` from it, after
  which the finalized release's equal code never registers as an update; and with
  `AutoUpdateMode: Version` that scan pins a build to the rc tag, so `Binaries:` resolves to
  the candidate's own APK and ships it to stable users.
- **No `submodules: true`.** The repo has an `upstream/` submodule pinning the LibreChat
  server for API reference, but nothing in the build reads it: the one task that walks it
  is not wired into compilation and its generated output is committed. A plain clone
  builds, and omitting the submodule keeps the server's whole tree out of the scanner.
- **`gradleprops: [unsignedRelease]`** drops the release signing config so AGP emits
  `app-release-unsigned.apk`. Passed bare — the build tests for presence, not a value.
- **`scandelete` is required.** `build-logic/` is an included build holding the project's
  Gradle convention plugins. Because the scan runs after `gradle clean`, and any Gradle
  invocation compiles those plugins, `build-logic/convention/build/` contains ~60 `.class`
  files and a jar by the time the scanner looks — enough to abort the build. None of it is
  in git. The one `scandelete` entry in the recipe removes it.
- `app/debug.keystore` is committed and the scanner warns about it (a warning, not an
  error). It is Android's standard debug keystore, used only so debug builds have a stable
  signature across machines; it plays no part in a release build, which is what
  `gradleprops: [unsignedRelease]` leaves unsigned. Happy to remove it if preferred.
- `network_security_config.xml` permits cleartext, because users commonly run LibreChat on
  a LAN address or a `.local` hostname. There is no hardcoded server.
- All dependencies are Apache-2.0 or MIT except `desugar_jdk_libs`, which is
  GPLv2-with-Classpath-Exception.
- Summary, description, changelogs and screenshots come from
  `fastlane/metadata/android/en-US/` in the repo, so they change with the app rather than
  through a separate merge request.

### Toolchain

The project builds with Gradle 9.5.1, AGP 9.2.1, Kotlin 2.4.10, JDK 21, compileSdk 36.
We ran `fdroid build` against `registry.gitlab.com/fdroid/fdroidserver:buildserver` to check
these in advance rather than leaving them for a first failed build:

- **JDK 21** is the image default — no action needed.
- **compileSdk 36 / build-tools 36.0.0** are not preinstalled (the image's provisioning list
  stops at android-33) but install automatically during the build; the licences in
  `/opt/android-sdk/licenses` are already accepted.
- **Gradle 9.5.1** resolves correctly from `gradle-wrapper.properties` and is present in the
  gradle-transparency-log, **but only the Python `gradle` shim in the image can use it.** The
  `gradlew-fdroid` shipped by the Debian `fdroidserver` package (2.4.2, trixie) is an older
  bash script with a static hash table ending at 8.14.2, and it aborts with
  `No hash for gradle version 9.5.1!`. Since `common.py` defaults `gradle` to the packaged
  script, a builder using the distro package needs `gradle: /usr/local/bin/gradle` in its
  config — or a newer fdroidserver. Please let us know if this is a problem on your
  infrastructure; we can discuss the Gradle pin.

### Proposed metadata

See `com.garfiec.librechat.yml` alongside this file.

---

## Before filing

1. Replace `<VERSION>` / `<CODE>` with the release that actually reproduced.
2. Paste the `apksigcopier compare` result into *Reproducibility*.
3. Fill the *Toolchain* section from the container probe.
4. Paste the contents of `com.garfiec.librechat.yml` into *Proposed metadata*, and confirm
   its `Builds:` / `CurrentVersion` block names that same verified release.
