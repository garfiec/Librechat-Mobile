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
  have.
- **App update check** — `TchatBrand.APP_UPDATE_CHECK` (`false`) is passed as
  `AppUpdateRepositoryImpl.isSupported` on Android (iOS already passes `false`). Upstream's check
  reads Switchboard's GitHub Releases, so it would offer Switchboard APKs, which carry a different
  app id and install beside Tchat instead of updating it. Off hides Settings' "Check for updates"
  rows, the daily check and the chat update banner; `pendingUpdate` also checks the flag, so a tag
  stored by an earlier Tchat build can't keep the banner up. The installed version's release notes
  row stays (it still reads Switchboard's notes for the same version tag).
- **Dead `sections/` files** — upstream still ships `screen/sections/{AccountSection,
  ChatPreferencesSection,DataManagementSection,SpeechSection}.kt`, each an `internal` composable
  shadowed by a same-named file-private one in the live screen, so nothing calls them. The fork
  deletes them; a sync where upstream edits one shows up as a modify/delete conflict — keep the
  delete after re-checking the shadowing still holds.
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

## Theme (design system)

The look follows `design/system/` (tokens in `tokens.json`; what changed and why in `Audit.md`).
First pass, all in `:core:ui`:

- **Fixed palette** — `theme/TchatPalette.kt` maps the design tokens onto M3 roles (the mapping is
  in its header comment). `LibreChatTheme` uses it instead of upstream's MaterialKolor scheme, which
  derived every role, neutrals included, from the accent seed. The brand seed gets the token values
  exactly; another accent from the picker changes only the accent roles; wallpaper dynamic color
  still wins when the user turns it on. `LocalFixedPalette` tells the two apart, so
  `GlassControlColors` can use the one accent in dark mode and paint grouped pages `bg` / cells
  `surface`.
- **Type scale** (`theme/Type.kt`) — all twelve token styles mapped to M3, so nothing is under 12sp
  and `titleSmall` out-ranks body text.
- **Corners** (`theme/Shape.kt`) — the six radius steps.
- **Avatars** (`components/AvatarColors.kt`) — five colours darkened so white initials pass 4.5:1.

- **User bubbles** (`theme/UserBubbleTheme.kt`) — filled `bubble-user` (the accent in light, deep
  navy in dark). The bubble's contents are drawn under a recoloured scheme so text, links, code
  wells, timestamps and actions read on the fill; the `MessageBubble` (Android) and
  `PlatformChatComponents.ios.kt` bubble columns are wrapped in it. A bubble being edited keeps
  the tonal fill, so the edit field is unchanged.

- **Chat layout** — chats open two-sided with bubbles (`ChatLayoutConstants.DEFAULT` /
  `DEFAULT_SHOW_BUBBLES`; upstream: thread, bubbles off). In the two-sided layout the user's bubble
  hugs its text on the right, kept `UserBubbleLeadingInset` (48dp) off the left edge, and
  assistant replies have no bubble and run the full width. Both remain settings.
- **Spacing grid** — off-grid spacing literals inside `padding(…)`, `spacedBy(…)`,
  `PaddingValues(…)` and `Spacer(…)` were moved to the nearest 4px step, ties up
  (3→4, 6→8, 10→12, 14→16, 22→24, 30→32; 95 literals in 48 files). Component sizes (icons at
  18dp, bars) were left alone. A later upstream sync may reintroduce off-grid values in files it
  touches; re-run the same rule rather than hand-merging.

- **Radius and font-size literals** — every `RoundedCornerShape(N.dp)` is now a token step
  (4 / 8 / 12 / 20 / 28, plus 0 and 999 pills), chosen by role: `BubbleShape` and the composer 20,
  menus, cards, banners and inputs 12, dialogs and sheets 28, quote and code backgrounds 8, the
  drag handle and indicators 4. Every `fontSize = N.sp` is on the type scale (12 / 13 / 14 code /
  15 / 16 / 24); nothing is below 12sp. Each changed literal carries a `// TCHAT:` note with the
  old value. The Settings → Chat layout preview mirrors `MessageBubble` (filled, compact user
  bubble; no assistant bubble two-sided).

Not yet done: the Reduce Transparency glass fallback.

## Model picker and Image Gen

Tchat's picker replaces upstream's endpoint-grouped `ModelSelectorSheet` everywhere it opens: the
top-bar trigger, send-block auto-opens, comparison mode's second model and the options sheet's
Model page. It ports Tchat web's picker (`Tchat/client/src/components/Chat/Menus/Endpoints/Tchat/`).
The design is `design/system/Model selection.md`; the requirements and the review decisions are
in `design/model-selection-requirements.md`.

- **Fork-only code** lives in `feature/chat/.../tchat/`: `ModelOwners.kt` (the owner table, label
  rules, catalog view and repair matching, all ported from web and kept in step with it),
  `TchatPickerModel.kt` (grouping, dedupe, pinned, search), `TchatModelDelegate.kt` (loads the
  catalog, `imageList` and tool availability; owns Image Gen and the image-model repair),
  `TchatModelPicker.kt` / `TchatModelTrigger.kt` (UI). Strings are in `values/tchat_strings.xml`.
- **Server inputs**, all Tchat additions that stock LibreChat doesn't serve:
  `GET /api/tchat/models/catalog` (`TchatCatalogApi`, `TchatModelCatalogRepository`),
  `modelSpecs.imageList` (`ImageSpec`), and `ephemeralAgent.image_gen` (`EphemeralAgent.imageGen`).
  Image Gen is offered only when `GET /api/agents/tools` lists `image_gen_oai`, as on web.
- **Image Gen is app-wide**, persisted in `TchatSettingsStore.imageGenSelection` (fork-only, same DataStore), like mobile's
  other tool toggles (web keys it per conversation). It's synthesized into `effectiveEnabledTools`
  as `image_gen`, so the tools sheet's switch and `toggleTool` route to the delegate.
- **Upstream files touched** carry `// TCHAT:` as usual. `ModelSelectorSheet.kt` and
  `ModelSelectorButton.kt` are left unchanged (the latter still draws comparison mode's dual-pane
  labels), so syncs merge them cleanly; the options sheet keeps upstream's selector as a fallback
  branch that the Tchat host never takes.
- **The header defaults to the model** (`ChatHeaderContent.DEFAULT`), and composer and pane labels
  use `formatModelLabel`, so a raw model id shows only in the picker.

## Upstream bugs fixed here first

- **OAuth was broken** (wrong `/api/oauth` path, and an external browser whose cookie jar the app
  could never read). The fork rebuilt it on an app-owned WebView (`OAuthWebView`); upstream then
  fixed the same bug independently and more thoroughly in garfiec/Librechat-Mobile#375
  (`SsoLoginScreen` + `SsoWebView`). The 2026-10-10 upstream sync **dropped the fork's
  `OAuthWebView` in favour of upstream's** — see "OIDC" below.
- **The server's own `openidLabel` / `openidAutoRedirect` (`StartupConfig`) are parsed upstream
  and never read.** Still fork-only: `LoginViewModel` surfaces both through `LoginUiState`;
  `LoginScreen` labels the `openid` button with the server's `OPENID_BUTTON_LABEL` (falling back
  to `TchatBrand.OIDC_FALLBACK_LABEL`) and honours `OPENID_AUTO_REDIRECT` by calling upstream's
  own `onSsoProviderSelected(TchatBrand.OIDC_PROVIDER)` once per screen instance (a
  `rememberSaveable` latch, so backing out of the SSO screen lands on a usable login screen).

## OIDC: upstream's in-app WebView

Sign-in runs on upstream's `SsoLogin(provider)` route (`feature/auth/CLAUDE.md` → "OAuth Flow"
documents it). Nothing in that flow is fork-specific; the fork only adds the label and
auto-redirect above. Things worth knowing for Tchat:

- **One-time warning.** Upstream shows an "in-app browser" risk dialog before the first social
  sign-in (`SsoRiskDataStore`), auto-redirect included. It is aimed at providers like Google that
  may block embedded browsers; TensorGrid's own OIDC is not one of them, but the dialog is kept as
  upstream ships it.
- **Every sign-in starts from an empty cookie jar** — Android wipes it before each round-trip and
  iOS uses a non-persistent `WKWebsiteDataStore`. That is deliberate upstream (a leftover provider
  session would otherwise silently sign the next user in as the previous one), and it means a
  TensorGrid session is never reused: the user enters TensorGrid credentials on every sign-in,
  not just once per install as under the fork's old `OAuthWebView`.
- **No SSO with the system browser.** An embedded WebView never sees the user's Chrome/Safari
  session. The fix that would give real SSO is RFC 8252 (system browser + a custom-scheme
  redirect + code exchange), and it needs a **Tchat backend change**: LibreChat's `oauthHandler`
  (`api/server/controllers/auth/oauth.js`) always redirects to `domains.client`, with no way for a
  client to ask for a native-app redirect. Out of scope unless this becomes a real complaint.

## Server-side prerequisite

`Tchat/deploy/tchat/env.example` must set `NON_BROWSER_VIOLATION_SCORE=0` — without it, LibreChat's
ua-parser violation system can accumulate ban points against this client and eventually lock a test
account out. Required before any sustained testing against `chat.tensorgrid.space`.

## Deferred (internal/debug builds only, for now)

Release signing, Play Store, App Store, and Obtainium/SLSA release-provenance updates are all
deferred. Each needs its own pass — see the plan history for what each entails when the time comes.
