# feature:auth

## Screens
- **ServerUrlScreen** -- first-run server URL entry with validation and QR scan option
- **LoginScreen** -- email/password + social OAuth buttons + LDAP username mode
- **RegisterScreen** -- name, username, email, password, confirm password
- **ForgotPasswordScreen** -- email entry for password reset link
- **TwoFactorScreen** -- 6-digit OTP input with backup code fallback

## Navigation
- Sealed interface: `AuthRoute : NavKey` with typed route classes
- Routes: `ServerUrl`, `Login`, `Register`, `ForgotPassword`, `TwoFactor(tempToken)`, `VerifyEmail(email)`, `ResetPassword(userId, token)`, `Terms`, `SsoLogin(provider)` (all `@Serializable`)
- Feature entries registered via `EntryProviderScope<NavKey>.authEntries()`
- Flow: `ServerUrl` → `Login` → (2FA if `tempToken` returned) → `onAuthComplete`
- Register and ForgotPassword are lateral routes from Login
- `TwoFactor(val tempToken: String)` data class carries the nav argument directly

## OAuth Flow
- Social logins run the whole round-trip in an in-app WebView (`SsoLoginScreen` + `SsoWebView`
  expect/actual) on the `SsoLogin(provider)` route — NOT Custom Tabs. Custom Tabs can never work:
  the server sets `refreshToken` httpOnly on its own origin, and neither `CustomTabsIntent` nor
  `ASWebAuthenticationSession` lets the app read that jar. The old path also requested
  `/api/oauth/{provider}`, which is a 404 — the router is mounted at `/oauth`.
- One jar for the whole round-trip is load-bearing, not incidental: from v0.8.8-rc3 the server
  binds the OAuth `state` to a cookie it sets at authorize time and checks at the callback, so
  authorize and callback have to share a cookie store.
- `SsoWebView` owns that jar. Android wipes it whole before every SSO round-trip and issues the
  load from inside `removeAllCookies`' async callback, so the wipe cannot race the load. Whole,
  not just the server's `refreshToken`: the provider's own session cookie is process-global and
  outlives our sign-out, so leaving it lets the flow re-select whoever was last signed in at the
  provider and complete as them, with no chooser and no error. Nothing else in that jar holds a
  session, and `AndroidSwitchCacheCleaner` already clears it whole on every account switch. iOS
  gets a `nonPersistentDataStore()`, so all of this is true there by construction.
  The token is expired again at capture (with `HttpOnly`, or Chromium refuses the overwrite) so it
  does not sit at rest in the jar.
- `SsoNavigationGate` (commonMain, unit-tested) cancels the server's post-callback redirect. Left
  alone it loads the LibreChat web app, whose `AuthContext` fires `silentRefresh()` against the same
  endpoint the app is about to call — and the backend rotates `session.refreshTokenHash` on every
  refresh, so whichever lands second gets a 401.
  The gate only arms on providers whose callback is a GET. Apple and SAML are `router.post`
  upstream, and neither Android callback sees a POST callback url — `shouldOverrideUrlLoading`
  skips POSTs, and `doUpdateVisitedHistory` reports the final committed url, which for a callback
  answering 302 is the redirect target. On those two the post-callback page is fetched and it is
  the cookie probe, not the gate, that ends the round-trip. Closing it properly means arming from
  `shouldInterceptRequest`; unverified for want of an Apple device.
- Failure is "stopped with no token" (`onCaptureFail`), not a query parameter: upstream dropped
  `failureMessage`, so a rejected callback is a bare redirect carrying no `error` at all. The code,
  when there is one, is diagnostic only.
- `SsoLoginViewModel` consumes the token once behind a guard, then calls
  `authRepository.loginWithOAuthToken`. It reports an error *type*; the screen owns the wording.
- `LoginViewModel` shows a one-time warning before the first social sign-in — the flow works only
  because the WebView sends a browser User-Agent, which providers can stop honouring at any time.
- That agent is `SSO_ANDROID_USER_AGENT` / `safariApplicationName`, **not**
  `LibreChatHttpClient.BROWSER_USER_AGENT`. The API constant answers the server's `ua-parser-js`
  soft-ban; this one answers a provider's embedded-browser detection, and the two are free to
  diverge. Android uses Chrome's reduced form with the device segment frozen at `Android 10; K`,
  because M110 froze it for every device — a string naming a model is a shape no shipping Chrome
  emits, and claiming no device leaves nothing for `screen.width` or `navigator.platform` to
  contradict. iOS appends only `Version/… Safari/604.1` to WebKit's own prefix, so the device,
  idiom and OS it reports stay real. Unverified and louder than any of this if it is wrong:
  `Sec-CH-UA` client hints, which need `androidx.webkit`'s `setUserAgentMetadata` to stay
  consistent with an overridden agent.
- Supported providers configured by server: Google, GitHub, Discord, Facebook, Apple, OpenID

## Token Storage
- Tokens stored in `EncryptedSharedPreferences` via `TokenDataStore` in `:core:data`
- Refresh token sent as Cookie header (backend reads `cookies.parse(req.headers.cookie)`)
- Access token sent as Bearer header
- Token refresh is an explicit POST, not automatic cookie-based

## ViewModels
- One ViewModel per screen: `ServerUrlViewModel`, `LoginViewModel`, `RegisterViewModel`, `ForgotPasswordViewModel`, `TwoFactorViewModel`, `SsoLoginViewModel`
- All use `AuthRepository` from `:core:data`

## Key Implementation Notes
- If server URL is already stored, skip ServerUrl screen on launch
- `openidAutoRedirect` from server config triggers automatic redirect instead of showing login form
- LDAP mode: show "Username" field instead of "Email" (check server config)

### Terms Screen
- `TermsScreen` + `TermsViewModel` — displays server terms, "I Accept" button
- Route: `Terms` data object (part of `AuthRoute` sealed interface) in `AuthNavigation.kt`
- Loads terms text via `UserRepository`, posts acceptance on confirm
- `TermsViewModel.consumeAccepted()` resets navigation trigger
- **Gotcha**: Terms check should happen after login if `startupConfig.requireTerms` is true
- **Note**: `VerifyEmailScreen` already existed pre-Round 2

### Localization
- `strings.xml` created for all 8 modules (app, core/ui, feature/auth, chat, conversations, settings, agents, files)
- Contains key toolbar titles, button labels, section headers — NOT exhaustive extraction
- Full string extraction is a future pass
