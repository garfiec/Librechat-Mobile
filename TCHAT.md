# Tchat — how this fork diverges from Switchboard

This app is [Switchboard](https://github.com/garfiec/Librechat-Mobile), rebranded and pinned to
run against **Tchat**, our self-hosted LibreChat deployment at `https://chat.tensorgrid.space`
(`TensorGrid-stacks/Tchat`, itself a 100%-additive fork of LibreChat — see its own
`deploy/tchat/README.md`).

The `upstream` remote (`garfiec/Librechat-Mobile`) is still tracked so `git merge upstream/main`
keeps working. This doc exists so that stays cheap: it names every place Tchat's identity lives,
so nothing gets hardcoded into a file upstream also edits.

## The convention

1. **`:core:brand`** (`core/brand/`) holds every Tchat-specific constant — server URL, app name,
   OIDC provider id/fallback label. Nothing outside this module inlines a Tchat string.
2. Every upstream file this fork touches carries a `// TCHAT:` comment. Find the whole delta with:
   ```bash
   git grep 'TCHAT:'
   ```
3. New files (a new module, a new doc) never carry the marker — being new is itself the signal;
   `git merge upstream/main` can never conflict on a path that doesn't exist upstream.
4. The 8+ per-module `CLAUDE.md` files are untouched except one line in the root `CLAUDE.md`
   pointing here. Don't add Tchat detail to any of them — that's what this file is for.

## What's pinned and why

- **Server URL** — `TchatBrand.SERVER_URL` (`https://chat.tensorgrid.space`), consumed as a
  `ServerPinning(url, allowOverride)` value injected into `ServerDataStore` (`:core:data`) and
  `ServerUrlViewModel` (`:feature:auth`). `allowOverride` is **unconditionally `false`, in every
  build, debug included** (`core/brand/di/BrandModule.kt`) — the pin is enforced with no in-app way
  to see or change it, full stop. `ServerUrlScreen` auto-connects on cold start with no field ever
  shown, and a stale persisted value can never survive. An earlier revision tied `allowOverride` to
  `BuildConfig.DEBUG` so a debug build could still show the URL field as a staging-server prefill —
  deliberately reverted: the requirement is that no build a tester might install ever exposes
  server configuration, not "release doesn't." `ServerPinning.allowOverride` still exists as a
  branch every consumer respects (not a hardcoded constant at each call site), so a hypothetical
  non-Tchat build of this fork could still set it `true` and get upstream's onboarding back — Tchat
  itself never does. A developer who needs a personal build pointed elsewhere edits
  `TchatBrand.SERVER_URL` in source before building.
- **Provider/backend settings** — `TchatBrand.EXPOSE_PROVIDER_BACKEND_SETTINGS` (`false`) hides
  every generic Switchboard control for administering an arbitrary self-hosted server: custom
  per-request headers (`ServerUrlScreen`'s Advanced section, `AccountSettingsScreen`'s "Server
  connection" row — Tchat's nginx has no access gateway needing them), LibreChat's own programmatic
  API keys (`AccountSettingsScreen`'s "API Keys" row, gated server-side behind `REMOTE_AGENTS`), and
  per-endpoint user-provided provider keys (`AccountSettingsScreen`'s "Provider API Keys" row,
  `DataSettingsScreen`'s "Revoke API keys" action). None of these fit Tchat's product model — every
  model call is routed through the broker with a fixed, server-side credential (see
  `Tchat/deploy/tchat/README.md`'s "Why users cannot switch providers") — so there is nothing for a
  user to configure, and showing the controls would look like a capability they don't actually
  have. `sections/DataManagementSection.kt` has a same-named, unreferenced duplicate of the
  "Revoke API keys" button (dead code, unrelated to this gate) — see the task flagged against it.
- **`applicationId`** — `space.tensorgrid.tchat` (`app/build.gradle.kts`). `namespace` stays
  `com.garfiec.librechat` on purpose: `AndroidManifest.xml` uses relative class names
  (`.MainActivity`) that resolve against `namespace`, and every Kotlin source file stays in the
  `com.garfiec.librechat` package tree — renaming *that* would touch all 15 modules' source paths
  and the iOS framework's export list for zero user-facing benefit. `${applicationId}.fileprovider`
  and every other `${applicationId}`-derived string already track the change automatically.
- **iOS bundle id** — `space.tensorgrid.tchat` in `project.pbxproj` (Debug + Release).
  `BGTaskSchedulerPermittedIdentifiers` and the `SwitchboardVersionName` Info.plist key are
  deliberately **not** renamed — they're opaque strings that must match Kotlin/build-script
  constants exactly, and renaming them buys nothing.
- **Deep-link scheme** — deliberately **left as `librechat`**. It's invisible to users, and
  changing it would permanently break any home-screen artifact/model shortcut already pinned by
  an existing install (the stored Android `Intent` carries the old URI). If it's ever changed:
  interpolate `DeepLinks.SCHEME` into the two hardcoded literals (`ModelShortcuts.kt`,
  `ArtifactShortcutHelper.kt`), add a *second*, back-compat intent-filter, and do it as its own commit.
- **Branding** — app name, splash colour (`#0C0F1A`, TensorGrid's ground colour), accent seed
  (`#4F8CFF`), launcher icons. See `design/README.md` for the icon source and regeneration steps.

## Upstream bugs fixed here, and reported upstream

These are genuine Switchboard bugs, not Tchat-specific behaviour — kept as isolated commits so
they cherry-pick cleanly into a PR to `garfiec/Librechat-Mobile`:

- **OAuth URL was wrong.** `AndroidOAuthLauncher` / `IosOAuthLauncher` built
  `"$serverUrl/api/oauth/$provider"`; LibreChat mounts OAuth at `/oauth`, not `/api/oauth`. This
  404'd on every server — social login in Switchboard had never worked against a real LibreChat.
- **The server's own `openidLabel` / `openidAutoRedirect` (`StartupConfig`) were parsed and never
  read.** `LoginScreen` hardcoded `"Continue with OpenID"` and never honoured
  `OPENID_AUTO_REDIRECT`. Both are now wired through `LoginUiState`.

## OIDC: rebuilt on an app-owned WebView

Fixing the URL above was necessary but **not sufficient** — the external-browser design
(`OAuthLauncher.launchOAuth`, a Chrome Custom Tab / `ASWebAuthenticationSession`) presented the
flow in the *browser's* cookie jar (Chrome's own profile / Safari's), never the store
`OAuthLauncher.extractTokenFromCookies` reads from (`CookieManager` / `NSHTTPCookieStorage`). It
has been replaced with `OAuthWebView` (`feature/auth/oauth/OAuthWebView.kt` + its `.android.kt` /
`.ios.kt` actuals) — an app-owned `WebView`/`WKWebView`, embedded as a full-screen overlay by
`LoginScreen` when `LoginViewModel.launchOAuth` is called, whose cookie jar *is* the one
`OAuthLauncher` reads afterwards:

- **Android**: `android.webkit.CookieManager` is a single global, app-wide store — the same one
  both the embedded `WebView` and `AndroidOAuthLauncher.extractTokenFromCookies` use. No bridging
  needed.
- **iOS**: `WKWebView`'s default (persistent, non-ephemeral) `WKWebsiteDataStore` synchronizes with
  `NSHTTPCookieStorage.sharedHTTPCookieStorage` (the store `IosOAuthLauncher` reads from) — a
  documented, long-standing WebKit behaviour, not something this fork bridges by hand. Using an
  *ephemeral* configuration would reproduce the exact bug this exists to fix, so `OAuthWebView.ios.kt`
  deliberately does not use one.

`OAuthWebView` only detects *when* the flow reaches an ordinary page back on the server (`isOAuthReturnUrl`
in the same file) — it never reads cookies itself. The caller reacts via
`LoginViewModel.onOAuthWebViewComplete()`, which calls the same `checkOAuthResult()` extraction the
old design used from `ON_RESUME`; that fallback is still wired up too (`LoginScreen`'s
`LifecycleEventEffect(ON_RESUME)`), now purely belt-and-braces since the in-app WebView no longer
backgrounds the app the way the external-browser design did.

**Android compiles clean** (`./gradlew :app:assembleDebug` — a real build, not just a review — has
since succeeded against this code). **iOS is still unverified**: `:app:assembleDebug` only compiles
the Android target, so `OAuthWebView.ios.kt`'s `WKNavigationDelegateProtocol` override signatures —
the one part of this whole change with no existing precedent elsewhere in the codebase to check
them against — have never been through a Kotlin/Native compile. Before relying on this in
production, run an Xcode/`iosSimulatorArm64` build and exercise the flow end-to-end on both
platforms against a real Tchat OIDC sign-in (with `ALLOW_SOCIAL_LOGIN=true`).

### Known, accepted trade-off: no SSO with the system browser

An embedded `WebView`/`WKWebView` has its own private cookie jar *and* its own private
`localStorage` — isolated from the user's real Chrome/Safari by OS design, on both platforms, with
no exception. Two consequences follow from that one fact, in opposite directions:

- It's *why the old design was completely broken*: a cookie set inside a Custom Tab (Chrome's own
  profile) or `ASWebAuthenticationSession` (Safari's) was never visible to
  `android.webkit.CookieManager` / `NSHTTPCookieStorage` — the stores `OAuthLauncher` reads from —
  so `extractTokenFromCookies` could never find anything, for anyone, ever. Not a login-flow bug;
  a 100%-failure-rate bug.
- It's *why the new design gives up SSO with the system browser*: if the user already has a live
  TensorGrid session in their actual Chrome/Safari (e.g. their JWT sitting in that browser's
  `localStorage` from using the TensorGrid web app), the in-app `OAuthWebView` can't see it either
  — per `OIDC.md`'s own diagram ("Already signed in? straight through"), a Custom Tab *would* have
  skipped straight through; the embedded WebView starts a cold session and makes the user sign in
  to TensorGrid again, inside the app. In practice this should mostly be a one-time cost per
  install — `OAuthWebView`'s cookie jar is the platform's normal *persistent* store, so a TensorGrid
  session established inside the app survives across app launches and a later sign-in attempt
  should skip straight through it. It is not one-time if the user's only prior TensorGrid session
  lives in their system browser rather than inside this app.

**Decision (kept deliberately, revisit only if this becomes a real complaint):** keep the WebView
as-is. The alternative — recommended by OAuth's own spec for native apps, RFC 8252 — is to keep the
system browser for the interactive part (preserving true SSO) and stop depending on cookies
entirely: the IdP redirects to a custom URI scheme the OS hands back to the app
(`tchat://oauth-callback?code=...`), and the app exchanges that code for tokens itself over a
direct HTTPS call, never touching a cookie jar at all. Notably, the *old* `IosOAuthLauncher` code
already had `callbackURLScheme = "librechat"` wired up — whoever wrote it was reaching for exactly
this pattern — but it could never fire, because LibreChat's `oauthHandler`
(`api/server/controllers/auth/oauth.js` in the Tchat repo) unconditionally redirects to
`domains.client` (an ordinary web page), with no way for a client to ask for a different redirect
target. Doing this properly needs a **Tchat backend change** too — either a small addition to
LibreChat's own OAuth controller (a `redirect_uri` override for a registered native-app scheme), or
a redirect-rewriting shim at the nginx/broker layer — not just a mobile-side one. Out of scope
unless the trade-off above turns out to matter more than expected.

## Server-side prerequisite

`Tchat/deploy/tchat/env.example` must set `NON_BROWSER_VIOLATION_SCORE=0` — without it, LibreChat's
ua-parser violation system can accumulate ban points against this client and eventually lock a test
account out. Required before any sustained testing against `chat.tensorgrid.space`.

## Deferred (internal/debug builds only, for now)

Release signing, Play Store, App Store, and Obtainium/SLSA release-provenance updates are all
deferred. Each needs its own pass — see the plan history for what each entails when the time comes.
