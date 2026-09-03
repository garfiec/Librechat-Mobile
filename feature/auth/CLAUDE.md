# feature:auth

## Screens
- **ServerUrlScreen** -- first-run server URL entry with validation and QR scan option
- **LoginScreen** -- email/password + social OAuth buttons + LDAP username mode
- **RegisterScreen** -- name, username, email, password, confirm password
- **ForgotPasswordScreen** -- email entry for password reset link
- **TwoFactorScreen** -- 6-digit OTP input with backup code fallback

## Navigation
- Sealed interface: `AuthRoute : NavKey` with typed route classes
- Routes: `ServerUrl`, `Login`, `Register`, `ForgotPassword`, `TwoFactor(tempToken)`, `VerifyEmail(email)`, `ResetPassword(userId, token)`, `Terms` (all `@Serializable`)
- Feature entries registered via `EntryProviderScope<NavKey>.authEntries()`
- Flow: `ServerUrl` → `Login` → (2FA if `tempToken` returned) → `onAuthComplete`
- Register and ForgotPassword are lateral routes from Login
- `TwoFactor(val tempToken: String)` data class carries the nav argument directly

## OAuth Flow
- **TCHAT**: runs in an app-owned `OAuthWebView` (`WebView`/`WKWebView`), not an external browser.
  The old design (a Chrome Custom Tab / `ASWebAuthenticationSession`) could never work — those
  present pages in the *browser's* cookie jar (Chrome's own profile / Safari's), never the store
  `OAuthLauncher.extractTokenFromCookies` reads from (`CookieManager` / `NSHTTPCookieStorage`). See
  the kdoc on `OAuthWebView` (`feature/auth/oauth/OAuthWebView.kt`) for the full explanation.
- Entry point is `{serverUrl}/oauth/{provider}` (not `/api/oauth/{provider}` — a genuine upstream
  bug fixed alongside the WebView rewrite; LibreChat mounts OAuth at `/oauth`).
- `LoginViewModel.launchOAuth` sets `LoginUiState.oAuthWebView`, which `LoginScreen` renders as a
  full-screen overlay. The overlay's own `onComplete` (fired when the WebView navigates back to an
  ordinary page on `serverUrl` — see `isOAuthReturnUrl`) calls `onOAuthWebViewComplete`, which hides
  it and calls `checkOAuthResult()`. `LifecycleEventEffect(ON_RESUME)` still calls
  `checkOAuthResult()` too, as a belt-and-braces fallback — the in-app WebView never leaves the app
  the way the old design did, so this fires far less often now, but process death mid-flow is still
  possible.
- `checkOAuthResult()` reads `refreshToken=` via `OAuthLauncher.extractTokenFromCookies()` and
  clears it after extraction to prevent stale reads.
- Supported providers configured by server: Google, GitHub, Discord, Facebook, Apple, OpenID

## Token Storage
- Tokens stored in `EncryptedSharedPreferences` via `TokenDataStore` in `:core:data`
- Refresh token sent as Cookie header (backend reads `cookies.parse(req.headers.cookie)`)
- Access token sent as Bearer header
- Token refresh is an explicit POST, not automatic cookie-based

## ViewModels
- One ViewModel per screen: `ServerUrlViewModel`, `LoginViewModel`, `RegisterViewModel`, `ForgotPasswordViewModel`, `TwoFactorViewModel`
- All use `AuthRepository` from `:core:data`

## Key Implementation Notes
- If server URL is already stored, skip ServerUrl screen on launch
- Social logins run in an in-app WebView (see OAuth Flow above) — this reverses an older assumption
  in this doc that Custom Tabs were required for cookie sharing; they were actually the opposite.
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
