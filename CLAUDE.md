# Switchboard

Native mobile client for LibreChat (Android & iOS). Connects to existing LibreChat backend servers (no backend changes). Users specify server URL during onboarding.

## Tech Stack

- **UI**: Jetpack Compose + Navigation Compose 3 (Nav 3)
- **DI**: Koin (KMP-ready)
- **Network**: Ktor Client (OkHttp engine)
- **Serialization**: Kotlinx Serialization
- **Local Storage**: Room (cache), DataStore (prefs), EncryptedSharedPreferences (tokens)
- **Build**: Gradle 9.8.2, AGP 9.5.1, Kotlin 2.4.10, compileSdk 37, minSdk 26

## Module Layout

```
app/                  → Single Activity, adaptive navigation (phone/tablet)
shared/               → KMP shared framework (iOS entry point, shared navigation)
build-logic/          → 15 convention plugins for consistent Gradle config
core/common/          → Result type, dispatcher DI, coroutine scopes, extensions
core/model/           → @Serializable data classes (pure Kotlin, no Android deps)
core/network/         → Ktor client, 16 API services, SSE client, auth interceptor
core/data/            → Room DB, DataStore, EncryptedSharedPrefs, repository impls
core/ui/              → Material 3 theme, shared composables
core/notifications/   → Reply and scheduled-chat notifications (system + in-app), permission, background watchers
feature/auth/         → Server URL, Login, Register, 2FA, OAuth, Forgot Password
feature/chat/         → Real-time chat with SSE streaming, message rendering
feature/conversations/→ Paginated list, CRUD, tags, search, export/import, drawer, account switcher
feature/settings/     → Account, appearance, about, danger zone
feature/agents/       → Agent marketplace with search and categories
feature/files/        → File upload, management, image viewer
feature/schedules/    → Scheduled chats
feature/skills/       → Skills list, detail, editor
feature/<x>/ui/       → Each feature's stateless UI, strings and previews; also builds for the browser
testing/speech-fixture/ → Stand-in speech provider APK for scripts/verify-speech-services.py (never ships)
```

Each module has its own `CLAUDE.md` with specific guidance.

## Writing code here

- **Build for what users hit.** Handle an edge case when a user on a supported server reaches it in
  normal use, or when it loses data, leaks a credential or strands the session. A rare glitch that a
  retry, refresh or reopen clears doesn't earn new state, a Job, a flag or a new test file. Fix the
  reported scenario with the smallest change, not its hypothetical neighbours; reuse or delete code
  before adding it. This applies to review findings too, including `/code-review --fix`.
- **Comments state present-tense rules** a plausible edit would break, in a line or two. No history,
  no story of how a bug was found or fixed, no issue numbers as narrative: that goes in the commit and PR.
- **A module `CLAUDE.md` holds rules, not history.** Every agent touching the module loads it, so add
  only a rule that governs many files, once, with its condition in the same sentence. A rule that
  guards one line belongs at that line, or in a test or detekt rule.
- **Tests go in the class's existing test file**; a new test file is for a new class.

## Architecture Rules

- Feature modules depend on `:core:*` only, never on each other; the exceptions are a feature's own
  `:feature:<x>:ui` and a `ui` module's use of other `*:ui` modules (see "Feature UI modules")
- Single Activity with Nav 3 (`NavDisplay` + `NavBackStack<NavKey>` + `entryProvider`)
- Unidirectional data flow: UI → ViewModel → Repository → API/Room
- Room is a read-through cache; server is source of truth
- Custom SSE parser over raw ByteReadChannel (not Ktor SSE plugin)
- UI is style-agnostic: the user's Material / Liquid Glass choice flows down as a CompositionLocal
  from `LibreChatTheme`, and `Adaptive*` components in `:core:ui` self-theme into one of two variants.
  Screens use `Adaptive*` (detekt bans raw M3 chrome) and don't branch on style. See "The pattern"
  in `core/ui/CLAUDE.md`.
- Distribution channels: open source (`foss` flavor, `com.garfiec.librechat`; Xcode `Release`) vs
  store (`play` flavor, `com.garfiec.librechat.play`; Xcode `Release-AppStore`). A behaviour that
  differs between them is a field on `ChannelPolicy` (`:core:common`), injected where needed; a
  preference default that differs is a `perChannel(…)` default in `AppPreferences` (`:core:data`).
  Never branch on `BuildConfig.FLAVOR` or `DistributionChannel` at a call site (detekt bans the
  import in non-test code; `:core:common` uses it from its own package), and don't call
  `ChannelPolicy.pick` from a feature either: add a field. A channel that can't be determined fails
  at launch by design.
  See `docs/RELEASING.md` "Distribution channels".

## Feature UI modules (`feature/<x>/ui`)

Every feature with UI is two modules. `:feature:<x>:ui` (`librechat.kmp.feature.ui`) holds the stateless
composables, the view state the ViewModels emit, pure UI helpers, previews (`androidMain`) and the
feature's strings and other compose resources. `:feature:<x>` keeps ViewModels, DI, navigation,
repository glue, platform code and each screen's stateful entry point, and depends on its `ui` with
`api`.

- A `ui` module depends only on `:core:ui`, `:core:model`, `:core:common`, other `*:ui` modules and
  libraries with a wasmJs build. The convention plugin fails configuration on anything else
  (`:core:data`, `:core:network`, Koin, ViewModel, Navigation 3, a Ktor client, Room, DataStore). CI
  compiles every `ui` module for the browser (`./gradlew -Plibrechat.web=true compileUiWasm`); run it
  before a PR that touches one.
- A screen is a stateful `<Name>Screen` in the feature (collects ViewModel state, maps it to view data
  at the top, owns snackbars, effects, pickers) and a stateless `<Name>Content` in `ui`
  (`<Name>ScreenContent` where that name is taken) that takes state and callbacks or an `…Actions`
  class. Anything that needs a ViewModel, Koin or a platform API reaches the Content as a composable
  slot parameter or a CompositionLocal declared in `ui`, with a fallback that draws the simplest thing
  (the source, nothing, a link). An `expect` in `ui`, `:core:ui`, `:core:common` or `:core:model` needs
  a `wasmJsMain` actual (all four compile for the browser).
- Both modules use the feature's packages. A file must not have the same name and package in both (they
  compile to one JVM class and the dex merge fails); give the stateful half another file name.
- A Content reads a state parameter that a `remember`/`derivedStateOf` or long-lived effect lambda
  captures through `rememberUpdatedState`; the raw parameter would freeze on its first value.
- `Res` is generated in `ui`, public, in the feature's `…feature.<x>.resources` package; the feature
  generates none (the convention turns it off when it has no `composeResources`). A feature that must
  keep resources of its own (`:feature:chat`'s vendored `files/web`) sets another `packageOfResClass`. A
  pure type the UI needs from `:core:data` or `:core:network` moves to `:core:model`.

## Adding a New Feature Module

1. Create the module directory under `feature/`
2. Apply the convention plugin in `build.gradle.kts` — this auto-applies Koin + Compose deps:
   - `librechat.kmp.feature` — for KMP modules with shared iOS + Android code (most features)
   - `librechat.mobile.feature` — for Android-only modules
3. Create `di/<Feature>Module.kt` with a Koin `module { }` containing `viewModelOf(::YourViewModel)` definitions
4. Add the module to `sharedKoinModules` (`shared/src/commonMain/.../di/SharedKoinModules.kt`) — the single list both Android (`LibreChatApplication`) and iOS (`IosSharedModule`) start from, so a module registered once is wired on both platforms
5. Use `koinViewModel()` in screen composables to inject ViewModels
6. Put its stateless UI in `feature/<x>/ui` (see "Feature UI modules"), add it to `settings.gradle.kts`
   and to `EXPECTED_MODULES` in `scripts/i18n-coverage.py`

## Backend Quirks

- Mutation endpoints wrap body in `arg` field: `{ "arg": { ... } }`
- Two-phase SSE: POST → `{ streamId }`, then GET stream. `streamId === conversationId`
- `GET /api/config` drives feature availability — never hardcode
- ua-parser-js middleware rejects non-browser User-Agents with 403 (workaround: Chrome UA string)
- Refresh token sent via request body (not HTTP-only cookies)
- iOS SSE streaming uses a custom `NWConnection`-based HTTP/1.1 transport (`core/network/src/iosMain/.../sse/SseHttpTransport.ios.kt`) to bypass NSURLSession's undocumented `text/*` content-type buffering. See `core/network/CLAUDE.md` SSE section for the two-layer buffering story.

## Upstream Sync

- **`upstream/`** — Git submodule of the [official LibreChat repo](https://github.com/LibreChat-AI/LibreChat). Read-only reference for API and web app parity. Do not modify.
- **`UPSTREAM_VERSION`** — Tracks which official tag/commit this mobile build is based on. Updated by the `/sync-upstream` skill.
- **`backendTargetVersion`** (root `version.properties`) — single source of truth for the targeted backend; must match the tag in `UPSTREAM_VERSION` (without `v` prefix). A core/common Gradle task code-generates `BackendVersion.SUPPORTED_BACKEND_VERSION` from it, and `release.yml` reads it for release notes. Edit the property, not the constant.
- **`/sync-upstream`** — Claude Code skill to diff upstream releases, identify gaps, propose changes, and implement them with user approval. Uses Agent Teams (investigator, android-expert, implementer, verifier).
- **`scripts/web-assets.json`** — registry of the third-party JavaScript the artifact/diagram/math
  WebViews execute (KaTeX, mermaid, marked, highlight.js, Tailwind, Babel, React, and the npm
  modules React artifacts may import, bundled from `scripts/artifact-modules/`). All of it is
  **vendored into the app**; none of it is fetched at render time. This is required for F-Droid,
  which rejects apps that download executable code without explicit opt-in consent — a
  `<script src="https://cdn…">` in a WebView is exactly that. It also closes a silent-drift hole:
  an unversioned CDN URL served whatever the CDN resolved that day, and two libraries had already
  broken that way without failing a build or a test. `scripts/vendor-web-assets.py` downloads the
  pins and CI verifies the tree against a sha256 lock. **Repin via `/update-web-assets`**, never by
  editing a vendored file. Adding a new WebView dependency means adding a registry entry in the
  same PR. See `feature/chat/CLAUDE.md` for how a page resolves them per platform.

- **`scripts/mirrors.json`** — registry of upstream constants the client copies by hand, because the server never serves them (which providers take documents natively, which MIME types the parser extracts, which feedback reasons the write route accepts). These drift **silently**: nothing fails to decode and nothing errors, so a sync's ordinary diff sweep reads them as inert constant edits. `scripts/check-mirrors.py` diffs each watched region between two upstream revisions and names the Kotlin file to reconcile; `/sync-upstream` runs it at Phase 0. **Adding a hardcoded mirror means adding a registry entry in the same PR** — a mirror nobody registered is one nobody will notice going stale.
