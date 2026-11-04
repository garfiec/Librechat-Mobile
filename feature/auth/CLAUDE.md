# feature:auth

## Screens
- **ServerUrlScreen** -- first-run server URL entry with validation and QR scan option
- **LoginScreen** -- email/password + social OAuth buttons
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
- That agent is `SSO_ANDROID_USER_AGENT` / `safariApplicationName` (in `:core:ui` `web/`, shared
  with the MCP OAuth sign-in WebView), **not**
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

## Coded refusals (v0.8.9-rc1)
- `AuthRefusal` (`viewmodel/AuthRefusal.kt`) is the one mapping for the coded 403s of the pre-auth
  routes: `TENANT_CONTEXT_REQUIRED` (strict tenant isolation; every `/api/auth` route and `/oauth`)
  and `TWO_FACTOR_FEDERATED_LOGIN_BLOCKED`. Keyed on the body's code, never the status, so the UA
  soft-ban, a ban, `terms_not_accepted` and a proxy 403 keep their own handling.
- Login, Register, ForgotPassword, ResetPassword and TwoFactor set `refusal` from it (TwoFactor via
  `VerifyTwoFactorOutcome.NotEvaluated.code`); each screen shows `authErrorText(refusal, error)`, the
  app's wording while `error` is set. SSO words the callback's `code` with the same string. Required
  2FA setup does not: a 403 there ends the setup credential and sends the user to sign-in, whose own
  refusal then says why.

## Policy links
- The footer (`PolicyLinksFooter`) on Login, Register, ForgotPassword and ResetPassword shows the
  deployment's privacy policy and terms of service links, as upstream's `Auth/Footer.tsx` does on
  every `AuthLayout` screen. Each ViewModel maps them from `authServerConfig` (the pending
  add-account server while one is pending, else the live one). Upstream's register-page
  `LegalConsent` sentence is deliberately not ported. `PolicyLinks.from` maps them at the top from
  the config's `safeExternalUrl` only (blank or unsafe-scheme URLs are dropped; see `isSafeLinkUrl`)
  and resolves a relative one against the server (`absoluteLinkUrl`). Read `safeExternalUrl`, never
  `externalUrl`, in any new consumer. Opened through `LocalUriHandler`.

## Diagnostic log
- `DiagnosticLogLink` ("Export diagnostic logs", the same label as Settings) offers the log under a
  sign-in error. Settings → Data has the same export but can't be reached before sign-in.
- Where: ServerUrl (any error), the SSO error pane, and Login / Register / ForgotPassword /
  ResetPassword / TwoFactor only when their UiState's `offerDiagnosticLog` is set. That flag follows
  `Result.Error.offersDiagnosticLog()`: no answer, a page LibreChat didn't write, or a 5xx — not
  LibreChat judging the input (wrong password, rate limit, ban). Each submit clears it first, so a
  later local validation error doesn't inherit it. 2FA offers it only on `ConnectionFailure`:
  `NotEvaluated` mixes 5xx with 400/429 and carries no status.
- It exports `DiagnosticLogRepository`'s buffer as is (already redacted on write) through
  `TextFileSaver` in `:core:ui`, with its own ViewModel. The log is kept across sign-out on purpose:
  an expired session is the case it exists for.

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
- **The URL field accepts a pasted web-UI address and cleans it** (`cleanServerUrl`, `:core:common`):
  the fragment and query go, so does one trailing LibreChat web route (`/login`, `/c/<id>`,
  `/share/<id>`, `/search`, `/agents`, … — the web client's route table, registered in
  `scripts/mirrors.json`), and the cleaned address is written back into the field. Any other path is kept — a subpath deployment is a different server.
  Both Connect and the http-warning Confirm go through it. Whatever is stored lives in the identity
  hash and the roster for good, so junk has to go before then; the same function names the root every
  request resolves under, so a URL stored before this existed still works — see
  `core/network/CLAUDE.md`.
- **Connect finds the server before storing anything** (`ConfigRepository.resolveServer`): it probes
  the cleaned address, and when that has a path, the bare origin second — but only when the path
  *answered* and wasn't LibreChat (a 4xx, or a body that isn't the config, such as the web app's
  HTML). An unreachable host, a timeout, TLS, an access gateway or a 5xx stops there: same host,
  same outcome, and the user needs that error rather than a second wait. Whichever answered is stored
  (`setServerUrl` then `publishStartupConfig`, or `beginAdd` + `attachPendingConfig` in add mode) and
  written back into the field, so a subpath deployment stays as typed and a junk page path is
  dropped silently. Probes run under a `PendingRequestIdentity` per candidate, so a failure stores
  nothing — the previous URL stays as it was.
- **When the origin wins, the gateway headers move with it**, before `beginAdd` mints the identity
  that reads them: the serverId that keys them hashes the stored URL. The entry under the typed
  address is cleared only if this attempt wrote it; one stored earlier may belong to another
  account's server.
- `openidAutoRedirect` from server config triggers automatic redirect instead of showing login form
- No LDAP username mode: the login field is always an email field and `LdapConfig.username` is
  unused. An LDAP server that signs in by username still works (the field accepts any text), but
  the label and keyboard say "Email".

### Terms Screen
- `TermsScreen` + `TermsViewModel` — displays server terms, "I Accept" button
- Route: `Terms` data object (part of `AuthRoute` sealed interface) in `AuthNavigation.kt`
- Loads terms text via `UserRepository`, posts acceptance on confirm
- `TermsViewModel.consumeAccepted()` resets navigation trigger
- Pushed over the signed-in stack by the shell (`Navigator.showTerms`): after sign-in, on cold start
  and on account switch when `interface.termsOfService.modalAcceptance` is on and `GET /api/user/terms`
  says not accepted, and whenever a write answers `403 terms_not_accepted` (v0.8.9-rc1 enforces it
  server-side; the validator raises `AuthGate.TermsNotAccepted`).
- Required means not dismissable: Accept pops; Decline, the top-bar arrow, system back and predictive
  back all decline, which (as upstream's decline-logs-out) revokes this account's server session
  best-effort and ends its local session for sign-in — this account only, no failover.
- The screen stays up through the revoke call. `TermsRequirementStateHolder.decline` runs once at a
  time (`declining`); while it or an accept is in flight, Decline and Accept are disabled and back
  is consumed and ignored (`declineUnlessBusy`) — never a disabled `BackHandler`, which would let
  the system pop the screen. The local end is skipped if the session already ended or a failover
  moved off the declining account during the revoke.
- The shell keeps the requirement as state (`TermsRequirementStateHolder`), not a route event, and
  opens the screen whenever it is required, signed in, out of the sign-in flow and not already on the
  stack: a requirement learned while the stack is still on sign-in is kept until it reaches chat.

### Required 2FA setup (v0.8.9-rc1)
- `TwoFactorSetup(tempToken)` route + `TwoFactorSetupScreen`/`TwoFactorSetupViewModel`: start →
  authenticator (open `otpauth://`, setup key, QR) → verify code → backup codes (save to file or
  confirm) → acknowledge + finalize, which commits a session like a 2FA verify.
- Reached two ways. Login returns `LoginOutcome.TwoFactorSetupRequired` (the setup token must never
  go to `TwoFactor`/verify-temp, which 401s it). Mid-session, a refresh or any request reports
  `AuthGate.TwoFactorSetupRequired`; `NavHostViewModel` ends the session for re-auth (no failover, no
  expiry dialog) and the navigator stacks `ServerUrl, Login, TwoFactorSetup`.
- A background account whose refresh hit enrollment has no gate, only the token store's marker; its
  switcher row leads to sign-in. `LoginViewModel` names the signed-out accounts on the screen's server
  (pending add server, else the live one) that carry the marker, as a one-line notice
  (`twoFactorSetupAccounts`). The finalize commits through `setSession` + re-home, which clears it.
- **Escape to another account**: whenever the app parks an account at sign-in with no failover (this
  setup, "sign in again", declined terms) other accounts may still be signed in. ServerUrl, Login and
  the setup screen (Start phase / expired) then show "Use another account" (`SignInEscape`, passed to
  `authEntries`), which opens the drawer's `AccountSwitcherSheet` in `:shared` on those accounts only
  (no add/remove). The switch is `NavHostViewModel.switchAccount`, the drawer's own path, which also
  enters the app when made signed out (`AccountSwitchStateHolder`). Hidden while signed in, so never in
  add-account mode.
- Ack/finalization credentials live only in the ViewModel. 401/403 = start over at sign-in; 400 on
  ack/finalize = spent; a failed finalize retries with the same finalization token.
- **Note**: `VerifyEmailScreen` already existed pre-Round 2

### Localization
- `strings.xml` created for all 8 modules (app, core/ui, feature/auth, chat, conversations, settings, agents, files)
- Contains key toolbar titles, button labels, section headers — NOT exhaustive extraction
- Full string extraction is a future pass

## Password managers
- **Android** fills and saves through the autofill framework. Login email carries
  `Username + EmailAddress`; Register and Reset password mark `NewUsername`/`NewPassword`; the
  password fields are recognised from `PasswordVisualTransformation`. Compose ends the autofill
  session (and the provider may offer to save) when the fields leave the screen. Register calls
  `LocalAutofillManager.commit()` itself, because it returns to Login, whose fields keep the
  session open.
- **iOS** takes its roles from `KeyboardOptions.withIosTextContentType()` (`:core:ui`): `.username`
  on the email fields, `.newPassword` on Register/Reset, `.oneTimeCode` on the 2FA input.
  `.password` and `.emailAddress` exist too (Settings' change-email and passkey dialogs use them);
  the Login password field still sets no iOS role.
- Fields holding other secrets (API keys, gateway tokens, PDF passwords) use
  `Modifier.excludeFromAutofill()` so a password manager doesn't offer or save the login there.
- Limits, all outside the app's control:
  - Saved logins are keyed by the app's package or bundle, not by the server, so a password saved
    in a browser for the server is not suggested automatically, and the same email on two servers
    collides.
  - On a tested Pixel, Google's autofill service returned no save info for the login form, so
    Google Password Manager never offered to save. Other providers (LastPass) did. Cause unknown.
  - iOS shows no save prompt and no matching suggestions without associated domains, which a
    client for arbitrary servers can't declare. Picking a login from the keyboard's key icon works.
- Credential Manager's `createCredential()` is deliberately not used on sign-in: it reaches only
  Credential Manager providers, so it would send a LastPass user's login to Google.

## OpenID reuse capture

Both SSO WebViews hand off `AuthCookies`, not a refresh-token string. Capture the refresh token,
`token_provider`, and `openid_user_id` from one cookie snapshot. A reuse capture missing its user
marker fails capture rather than falling through to LibreChat JWT authentication. Bounded second
reads allow the native cookie store to catch up; preserve the single-completion latch and composition
scope cancellation. iOS also filters domain, path, Secure and expiry before choosing cookies.

The ViewModel passes the bundle directly to the repository; credentials never enter navigation or
saved UI state. Normal sessions carry no OpenID marker. Existing reuse sessions whose marker was
never captured need a new sign-in; the refresh token cannot recreate that server-issued marker.
