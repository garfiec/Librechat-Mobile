# App Module

Single Activity; `MainActivity` is the sole entry point. Depends on `:shared`, all `:core:*` and `:feature:*`.

## Navigation
`LibreChatNavHost.kt` (this module) wraps the shared `LibreChatNavHost` via a `content` lambda, adding deep
links, share-intent routing and phone/tablet branching. The shared module owns the rest (`Navigator`,
`NavHostViewModel`, `MainNavDisplay`, `PhoneLayout`, `TabletLayout`, drawer, all entry providers): see
`shared/CLAUDE.md`. Feature modules get `onNavigate`/`onBack` lambdas, never `NavBackStack`.

### Layout modes
- Tablet layout at 680dp+ wide and 480dp+ tall (320dp sidebar + 360dp pane), else the phone drawer. Both
  platforms choose in the shared `WindowLayout` via `usesSidebarLayout` (`SidebarLayout.kt`): change the
  breakpoint there, not at a call site.
- Both platforms switch layouts in place (`MainActivity` handles size changes itself), so `WindowLayout`
  hands one `rememberMovableNavDisplay` to both layouts (a `MainNavDisplay` leaving composition clears
  every entry's ViewModels). Keep the choice in the host's own composition, not a subcomposition like
  `BoxWithConstraints` — only that case is covered (`MovableNavDisplayTest`).

## Deep links
- Scheme `librechat://`; hosts `conversation/{id}`, `artifact/{uuid}`, `model?endpoint=&model=[&spec=]`, `code-setup/{id}`, `oauth`.
- **Single source of truth**: `DeepLinks.resolve()` (`:shared/commonMain`) → `DeepLinkResolution`
  (`Route` / `ArtifactApp` / `Consumed` / `None`). Add a link as one branch there; do NOT reintroduce a
  per-host allowlist here.
- `MainActivity.handleDeepLink()` (and `onNewIntent`) resolves to accept/drop (`DeepLinkUriAdapter.kt`) and posts
  what it accepts to `DeepLinkInbox` (`:shared`), the one intake iOS's URLs and quick actions use too; the shared
  `LibreChatNavHost` places it (`placeDeepLink`); a `Route` goes to login when signed out. Don't hold a link in
  activity state. `ArtifactApp` never reaches the nav host: a pinned artifact runs in `ArtifactAppActivity`, its
  own task, signed in or not.
  `oauth` is `Consumed` (sign-in is in-app WebView); keep the mapping so links from older builds still bring
  the app forward.

## Share intents
- `MainActivity.handleShareIntent()` only **stages** on `ShareIntentConsumer`. `LibreChatNavHost` reads
  `navigator.currentRoute` and calls `dispatchTo(conversationId)` (`null` = `NewChat` landing), navigating to a
  chat first if none is on screen.
- **A share must be addressed, never broadcast**: several `ChatViewModel`s are alive (the landing sits beneath
  an open `Chat`), and an unaddressed share goes to whichever collects first. Each screen uses
  `sharesFor(conversationId)`; addressing lives in the nav host.
- Drive delivery off `ShareIntentConsumer.undelivered`, not an activity counter: the launch intent is processed
  only on a fresh start, so a recreation during the theme/locale warm-up would strand the share.

## Server banners
- `GET /api/banner` returns one object or a 200 with empty body and no `Content-Type`, never an array.
  `BannerApi` reads the raw body; anything else still throws, so a proxy answer can't read as "no banner".
- `NavHostViewModel` fetches via `BannerRepository` on init, account switch and auth-complete; no timer.
- The server applies `displayFrom`/`displayTo` and `type: 'banner'`; don't re-check client-side (clock skew).
- Dismissal lives in `BannerStateHolder`: in-memory **(serverId, bannerId)** key. Both halves are needed: by
  banner alone, a bannerId shared across servers arrives pre-dismissed; cleared on switch, A's banner returns.
  `clearForAccountChange()` drops the banner but must **not** touch dismissals.
- `BannerDisplay` takes a nullable banner and decides visibility itself; don't hoist the null check (kills the
  enter/exit animation).
- One treatment, `secondaryContainer`/`onSecondaryContainer`. Don't re-introduce a `type`-keyed palette or
  hardcoded hex (server only sends `type: "banner"`; fixed colours ignore the accent seed and dark mode).
- `persistable` hides dismiss; a banner with no `bannerId` is not rendered.

## Other
- Session (shared `NavHostViewModel`): start at `NewChat` if signed in, else `ServerUrl`; `sessionExpired`
  clears the whole back stack into auth; drawer gestures are off during auth.
- Top-level routes: `NewChat`, `Conversations` (drawer body), `AgentMarketplace`, `Files`, `SettingsTabbed`
  (drawer footer), all registered in shared `MainNavDisplay`.
- `ConnectivityObserver` (injected into `MainActivity`) drives the offline banner.
- `PresetManager` route is registered in `SettingsNavigation.kt` (Settings → Chat).
