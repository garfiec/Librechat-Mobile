# fdroiddata merge-request text for Switchboard

Kept in the repo so it stays in sync with the release process it describes.

**Where this goes:** a merge request against
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), adding
`metadata/com.garfiec.librechat.yml` on a branch named `com.garfiec.librechat`.
Not an RFP issue. The RFP tracker's own template says *"If you are looking to submit
an app to F-Droid, please open a merge request instead."*

- **MR title:** `New app: Switchboard`
- **MR template:** *App inclusion*

Everything between the two rules below is the MR body: a one-sentence `## Note`
about the pipeline, a `---`, then the App-inclusion template reproduced exactly
with every box ticked and nothing else added. The note is there because box 44
asks for one when GitLab demands identity verification; keeping it in the body
rather than a follow-up comment means nothing is left pending after the MR is
created. Everything below the `---` must stay byte-verbatim. If the template
changes upstream, regenerate with:

    sed 's/^\* \[ \]/* [x]/' \
      "<fdroiddata>/.gitlab/merge_request_templates/App inclusion.md"

Generated against template commit `5fbc86c4` (2026-09-20, "Rewrite the MR template").

Supporting detail lives below the second rule, as
prepared answers to post only if a reviewer asks.

---

## Note

CI cannot run on my fork: GitLab requires identity verification for shared runners, which I have
not done, so the pipeline fails immediately with no jobs
([2901625332](https://gitlab.com/chiu.garfie/f-droid-data/-/pipelines/2901625332)).

---

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). If you are not the author, please paste the link of the reply from the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. The summary and description must be included and images, icon, and changelog should also be provided for better user experience. The `en-US` locale must be included.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected. See <https://docs.gitlab.com/user/project/repository/branches/protected/>.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [x] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs. You can update git submodules without opening an MR in this repo and the submodule is covered by our scanner.
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). We'll use your signature for improved security/reliability, also allowing users to switch between different channels. Do note that if you don't enable reproducible build then the apk will be signed with our key so you can't enable it later. If you can't enable this, please add the reasons here.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller.
* [x] Only the latest versions should be kept in the metadata before it's merged. If you update the metadata, please replace the old versions with the new ones.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit.

### Pipeline

* [x] All pipelines should pass.
* [x] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

---

## Not part of the MR body, notes for us

### Do these before pasting

1. **Make the fork public and leave the branch unprotected.** Box 19 claims both. The fork
   `chiu.garfie/f-droid-data` is already public, and only `master` carries a protection rule, so
   `com.garfiec.librechat` is unprotected as created. Reviewers push suggestions straight onto the
   branch, and "Unprotect your branch" is one of the most frequent review comments.
2. **If GitLab asks for a phone number or a credit card, stop.** Box 44 says not to submit in that
   case. Leave a note instead so they can trigger CI themselves. Nothing has asked so far.

### The fork's pipeline cannot run, and that is expected

`CONTRIBUTING.md` asks you to confirm the pipeline is green on your fork before opening the MR.
That is not achievable here. The branch is pushed and GitLab created a pipeline, which failed
instantly with no jobs:

```
failure_reason: The pipeline failed due to the user not being verified.
```

GitLab requires identity verification, by phone or card, before a free account may use shared
runners. **Do not verify.** Template box 44 covers precisely this:

> F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any
> CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just
> leave a note in the MR so we know we need to trigger the CI.

So: the note goes **in the description body**, above the checklist, and the template below it stays
byte-verbatim. That is the !50344 pattern (`## Note` / `---` / `## Checklist`), which merged. No
follow-up comment is needed, and nothing is left pending on you after the MR is created.

The note, one sentence:

```
## Note

CI cannot run on my fork: GitLab requires identity verification for shared runners, which I have
not done, so the pipeline fails immediately with no jobs
([2901625332](https://gitlab.com/chiu.garfie/f-droid-data/-/pipelines/2901625332)).

---
```

Verified against the live endpoint on 2026-10-01: `failure_reason` is exactly "The pipeline failed
due to the user not being verified.", status failed, 0 jobs, for sha 52115a1c.

The APK-for-the-tester detail and the local-gates list are deliberately **not** in the body. Once a
maintainer triggers CI the Code Quality report appears and the tester gets the APK the normal way,
so that material is only needed if someone asks first, and it is kept verbatim just below.

If a reviewer does ask for the APK before CI is triggered, this is the answer:

```
The APK for the tagged release, since there is no Code Quality artifact yet:

  https://github.com/garfiec/Librechat-Mobile/releases/download/v2026.10.0/switchboard-v2026.10.0.apk
  sha256 6134909746f65109cf024aa98b079c7ea663a710789f99fba1dd5229551f03ed
  14,414,363 bytes, minSdk 26

I ran the metadata jobs locally against a real fdroiddata clone, with fdroidserver installed the
way .gitlab-ci.yml does it: fdroid readmeta, fdroid lint (no output), fdroid rewritemeta (no diff),
check-jsonschema against schemas/metadata.json, and fdroid checkupdates (resolves 2026.10.0).
fdroid build -v com.garfiec.librechat:20261000 verified that published APK against its own rebuild,
and fdroid scanner --exit-code on it reported nothing.
```

**Box 43 commits us to explaining the Reports tab, which we cannot see.** Running the `check apk`
job's own logic over the published APK predicts it exactly, and there is nothing critical or
major in it:

| severity | entry |
|---|---|
| minor | `INTERNET`, `CAMERA`, `RECORD_AUDIO`, `WRITE_EXTERNAL_STORAGE` |
| info | `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| info | Signing Key `CN=garfiec, O=LibreChat Mobile`, not the debug key, so the job's EXITVALUE stays 0 |

No cleartext finding: the job greps the manifest for `android:usesCleartextTraffic="true"`, and we
do not set it. Cleartext is permitted through `networkSecurityConfig`, which that grep does not
read. Not debuggable, not testOnly.

If a reviewer asks about the four minor entries, this is the answer:

```
On the Code Quality permissions:

INTERNET is the app's whole purpose; it talks to the LibreChat server the user supplies.
CAMERA and RECORD_AUDIO are both requested at the point of use, not at startup: CAMERA from the
attachment picker and RECORD_AUDIO from speech-to-text. The app runs normally if either is
declined. WRITE_EXTERNAL_STORAGE is capped at android:maxSdkVersion="28" and so never applies on
a minSdk 26 device running anything current. MANAGE_EXTERNAL_STORAGE is not requested.

WAKE_LOCK, RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE are not declared by the app; they are
merged in from WorkManager, which backs the background conversation prefetcher.
```

**On box 42, "All pipelines should pass".** It is ticked, and the MR will show red until someone
triggers CI, because a fork MR runs its pipeline on the fork's runners. Reading the box as a
requirement being acknowledged rather than a result being claimed, the tick is right, and the
note explains the red far better than an empty checkbox would. MR 50244 merged with two boxes
left blank, so unticking it is also defensible if you would rather.

**On verifying your GitLab account.** F-Droid says not to, and that is the default. The tradeoff
if you ever reconsider: verifying would let the fork run the full pipeline, including `check apk`
and its Code Quality report, which is what testers pull the APK and the permission list from. It
would make this MR cheaper for a volunteer to pick up. It is your call, not a requirement, and
nothing here depends on it.

Everything in that note is measured, not claimed. Do not paste it if any of it stops being true.

### Prepared answers, post only if asked

**Why there is no `submodules: true`, if they ask.** `upstream/` pins the LibreChat server repo
as an API reference. Nothing in the build reads it — the one task that walks it is not wired into
compilation and its generated output is committed — so a plain clone builds, and leaving
`submodules:` unset keeps the server's whole tree out of the scanner.

These are not in the MR body, which is the template verbatim, but
every one of them answers a question the recently merged MRs show reviewers actually asking.

**Reproducibility, if they ask whether it has been verified.** `fdroid build -v
com.garfiec.librechat:20261000` downloads the published asset and runs `verify_apks` against its
own rebuild:

```
INFO: ...successfully verified
INFO: compared built binary to supplied reference binary successfully
INFO: supplied reference binary has allowed signer 668a71966a070614d144955d83e7236a3ced77f8640857c3fa84b03ccde40e59
INFO: 1 build succeeded
```

Checked a second way without leaning on that verdict: all 499 non-signature zip entries match the
published APK by name and CRC32, none present on one side only, including `classes.dex`,
`resources.arsc`, `AndroidManifest.xml`, `version-control-info.textproto` and all twelve `.so`
files. Whole-file hashes differ by the 67,777-byte signature block, which is expected, because the
rebuild is unsigned and `verify_apks` copies the published signature on before calling apksigner.
`2026.10.0` is the second consecutive release to reproduce.

**Why no AntiFeatures, if they ask.** The app requires no named third-party service. It talks to a
LibreChat server the user supplies, which is MIT-licensed software they host themselves, and it
ships no default endpoint and no provider presets. That puts it with `com.nextcloud.client` and
`org.jellyfin.mobile` rather than with a client for a hosted AI service. Cleartext is permitted in
`network_security_config.xml` because people commonly run LibreChat on a LAN address or a `.local`
hostname. Note that 11 of 14 published `AI Chat` apps do carry `NonFreeNet` or `TetheredNet`, so
this is the likeliest thing to be challenged.

**There is deliberately no `scandelete`, and it must not be re-added.** An earlier draft carried
`scandelete: build-logic/convention/build`, on the reasoning that any Gradle invocation compiles
the convention plugins in `build-logic/`, so the scanner would find ~60 `.class` files and a jar.
That reasoning is wrong about fdroid's order of operations. `fdroid build` applies
`scandelete`/`scanignore` and runs the scanner against a **clean checkout, before Gradle runs at
all**, so that directory never exists at scan time. Verified 2026-10-01 against fdroidserver
master (`c21c177`) in `buildserver-trixie`:

```
ERROR: Non-exist scandelete path: build-logic/convention/build
ERROR: Unused scandelete path: build-logic/convention/build
ERROR: Could not build app com.garfiec.librechat: Can't build due to 2 errors while scanning
```

Current fdroidserver requires every `scandelete` path to exist *and* be used, and fails the build
on two counts when it is neither. Debian-packaged fdroidserver 2.4.2 ignored it silently, which is
why an earlier pre-flight missed it. With the two lines removed the scanner passes clean and the
build proceeds. `build-logic/` is not in git, so nothing is being withheld from the scanner.

**Why `UpdateCheckData` and the tag filter cannot be dropped.** The version literals live in the
root `version.properties`, which `common.manifest_paths` does not scan, so without
`UpdateCheckData` the app would publish once and never be offered as an update. The tag filter on
`UpdateCheckMode` is there because the version scheme drops `-rcN` when packing `versionCode`, so
a release candidate and its final release share one code. Unfiltered, a scan run while only the
candidate existed would pin the build to the rc tag.

**The missing changelog, if they notice.** `changelogs/20261000.txt` did not make it into the
tagged tree, so `2026.10.0` has no What's New text. The release commit touched only
`version.properties`, so it cannot be added to that tag after the fact. The release workflow now
refuses to cut a release without a changelog, so no later version can repeat it. Raise this
proactively only if the Fastlane box is questioned.

**compileSdk 37, if the toolchain is queried.** The buildserver image ships `platforms/` and
`build-tools/` empty, and the build pulled `platforms;android-37.0` and `build-tools;36.0.0`
itself at configuration time. Both reference `android-sdk-license`, which the image already
accepts, so there is no licence prompt. API 37 publishes no plain `platforms;android-37`: it ships
as `android-37.0`, and that is what AGP resolves `compileSdk = 37` to.

**The ABI split, if they ask for one.** The universal APK is 14.4 MB, of which about 5.0 MB is
four ABIs' worth of prebuilt `.so` from three libraries, none compiled by this project. A split
saves a given user roughly 3.7 MB and would rename the assets that `Binaries` and the existing
Obtainium channel resolve against. CubeTimer declined at about 12 MB and Voice Memos at 7.9 MB.
DragTree and Ğ1nkgo show the alternative if they insist: one `Builds` entry per ABI, each with its
own `binary:`.

**Licensing, if queried.** Every Gradle dependency is Apache-2.0 or MIT except `desugar_jdk_libs`,
which is GPLv2 with the Classpath Exception. The nine browser libraries the WebViews run (KaTeX,
mermaid, marked, marked-highlight, highlight.js, Tailwind, @babel/standalone, React, react-dom)
are vendored as the publishers' own released browser builds with licences alongside, all MIT
except highlight.js which is BSD-3-Clause. `scripts/web-assets.json` records version, licence,
source and reason per pin, and `scripts/vendor-web-assets.py --check` verifies the tree against a
sha256 lock in CI. Tailwind 3.4.17 never published a browser build to npm, so that one file comes
from Tailwind Labs' own immutable version URL.

### Re-run all five metadata gates before submitting any later version

Against a **real fdroiddata clone**, never a synthesized metadata directory: `readmeta`, `lint`,
`rewritemeta`, `check-jsonschema` against `schemas/metadata.json`, and `checkupdates`, plus
`fdroid build` and `fdroid scanner --exit-code` on the published APK. Then:

1. Re-verify reproducibility. A release that has not been verified must not be listed, because
   `Binaries:` has no fallback.
2. Confirm the `Builds:` and `CurrentVersion` block names that release and that `commit:` is the
   full 40-character hash. "Use the full commit hash instead" is the most common review comment in
   fdroiddata.
3. Re-check that `changelogs/<versionCode>.txt` is in the tagged tree. Only the file matching the
   current versionCode is ever read.
4. Re-run `rewritemeta` specifically. It wraps any **line** past 90 characters, which is what caught
   the `Binaries:` line on this submission. `Binaries: ` + the URL is 95, so the value sits on a
   continuation line and the key keeps a **trailing space**. That space is load-bearing: strip it
   and rewritemeta reports a diff and CI fails. Note `.editorconfig` sets
   `trim_trailing_whitespace = true` for `[*]` and exempts only `[*.md]`, so an editor honouring it
   will silently remove the space from this `.yml`. Check for it before committing a recipe change.

**Expect a long wait, and keep the MR current.** Merging is gated on a volunteer tester rather
than the reviewer. The standard reply is *"This MR is mostly ready. We'll test it later …
Currently we have lots of MRs waiting for test so it may take a long time."* If a release goes out
while the MR is open, update the recipe in the MR to that version rather than leaving a stale one.

### Rig notes

**The verification run used settings our CI does not.** The probe container has far less memory
than F-Droid's builders, so it ran with `org.gradle.jvmargs=-Xmx3g
-Djdk.lang.Process.launchMechanism=FORK`, `org.gradle.parallel=false` and
`kotlin.compiler.execution.strategy=in-process`. It verified anyway, which is evidence the output
does not depend on those settings.

Two of those exist because of an emulation artifact, not the project. Running the amd64 image
under qemu on Apple Silicon, the JVM's default `POSIX_SPAWN` deadlocks in `jspawnhelper`: the
build sits with `aapt2` alive at 0% CPU and no error anywhere. `FORK` avoids it. Separately the
peak is `:app:minifyReleaseWithR8` at a measured **6.2 GiB of the 7.75 GiB** available, so one
attempt lost its Gradle daemon with `daemon disappeared unexpectedly`. Give the VM headroom over
8 GiB.

**Images.** `registry.gitlab.com/fdroid/fdroidserver:buildserver` carries no `fdroid` binary; it
is the environment `fdroid build --on-server` provisions into. The local `fdroid-probe:local` is
that image with the Debian `fdroidserver` package (2.4.2) added. CI itself does neither: it clones
the fdroidserver git repo at a pinned commit and puts that on PATH.

**The listing icon is in place.** `fastlane/metadata/android/en-US/images/icon.png` is a 512x512
export of the master logo, flattened onto the adaptive icon's own background so it does not
disappear against a dark theme. Regenerate it with `scripts/make-fdroid-icon.py`, never by hand.
Without it F-Droid falls back to the launcher icon from the APK, which works but draws review
comments.

**Gradle 9.5.1 and the packaged wrapper script**, kept here because it does not affect F-Droid's
own infrastructure: 9.5.1 resolves from `gradle-wrapper.properties` and is in the
gradle-transparency-log, but the `gradlew-fdroid` shipped by the Debian `fdroidserver` package
(2.4.2, trixie) is an older bash script whose hash table ends at 8.14.2 and aborts with `No hash
for gradle version 9.5.1!`. Only the Python `gradle` shim in the buildserver image handles it.
Raise it only if a build fails.
