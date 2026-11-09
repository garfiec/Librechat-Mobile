# feature:auth

## Screens and navigation
- `AuthRoute : NavKey` routes: `ServerUrl`, `Login`, `Register`, `ForgotPassword`, `TwoFactor(tempToken)`,
  `TwoFactorSetup(tempToken)`, `VerifyEmail(email)`, `ResetPassword(userId, token)`, `Terms`,
  `SsoLogin(provider)`; entries via `authEntries()`. Flow: `ServerUrl` → `Login` → (2FA if `tempToken`) →
  `onAuthComplete`. One ViewModel per screen, all on `AuthRepository`.
- A stored server URL skips `ServerUrl` on launch.
- Login shows only what `GET /api/config` turns on, mapped once in `LoginViewModel` from `authServerConfig`.

## Server URL: cleaning and origin fallback
- **The field cleans a pasted web-UI address** (`cleanServerUrl`, `:core:common`): drops fragment, query and one
  trailing LibreChat web route (`/login`, `/c/<id>`, `/share/<id>`, … — registered in `scripts/mirrors.json`) and
  writes the result back. Any other path is kept (a subpath deployment is a different server). Connect and the
  http-warning Confirm both go through it. The stored URL is in the identity hash and roster for good, so junk
  must go before storing; the same function names the request root (see `core/network/CLAUDE.md`).
- **Connect finds the server before storing anything** (`ConfigRepository.resolveServer`): probe the cleaned
  address; if it has a path, try the bare origin second, but only when the path *answered* and wasn't
  LibreChat (a 4xx, or a non-config body like the web app's HTML). Unreachable, timeout, TLS, access gateway or
  5xx stop there (same host, same outcome). Whichever answered is stored (`setServerUrl` +
  `publishStartupConfig`, or `beginAdd` + `attachPendingConfig` in add mode) and written back to the field.
  Probes run under a per-candidate `PendingRequestIdentity`, so a failure stores nothing.
- **When the origin wins, gateway headers move with it** before `beginAdd` mints the identity (their serverId
  hashes the stored URL). Clear the entry under the typed address only if this attempt wrote it; an earlier
  one may belong to another account's server.

## OAuth / SSO
- Social login runs entirely in an in-app WebView (`SsoLoginScreen` + `SsoWebView` expect/actual, route
  `SsoLogin(provider)`), never Custom Tabs / `ASWebAuthenticationSession`: the server sets `refreshToken`
  httpOnly on its origin and the app can't read those jars. The router is mounted at `/oauth` (not
  `/api/oauth`).
- **One cookie jar for the whole round-trip** is required: from v0.8.8-rc3 the server binds OAuth `state` to a
  cookie set at authorize and checked at callback.
- `SsoWebView` owns the jar, and Android issues the load from its clear's callback so the clear can't race
  it. It wipes the jar **whole** before every round-trip, because the provider's session cookie is
  process-global and outlives sign-out, so the flow would silently complete as whoever was last signed in.
  The one exception is `keepProviderSession` (`AccountSwitcher.signInReauthenticatesActive()`): re-signing
  in the still-bound active account expires every cookie on the server's origin and keeps the provider's,
  so the provider can skip its form. A round-trip that ends without a completed sign-in wipes the jar whole, and so
  do account switches and a sign-in that resolves to another account. iOS uses `nonPersistentDataStore()`.
  The token cookie is expired again at capture (with `HttpOnly`, or Chromium refuses the overwrite).
- `SsoNavigationGate` (commonMain) cancels the server's post-callback redirect: the web app would
  `silentRefresh()`, and the backend rotates `refreshTokenHash` on every refresh, so the second caller 401s.
  It arms only for GET callbacks. Apple and SAML callbacks are POST, invisible to `shouldOverrideUrlLoading` /
  `doUpdateVisitedHistory`; there the cookie probe ends the round-trip. Arming from `shouldInterceptRequest`
  would fix it; unverified (no Apple device).
- Failure is "stopped with no token" (`onCaptureFail`), not a query param: upstream sends no `failureMessage`.
  A `code`, when present, is diagnostic only.
- `SsoLoginViewModel` consumes the token once behind a guard, then `authRepository.loginWithOAuthToken`; it
  reports an error *type*, the screen words it. `LoginViewModel` shows a one-time warning before the first
  social sign-in (the flow depends on a browser User-Agent providers may stop honouring).
- That agent is `SSO_ANDROID_USER_AGENT` / `safariApplicationName` (`:core:ui` `web/`, shared with MCP OAuth),
  **not** `LibreChatHttpClient.BROWSER_USER_AGENT` (that one answers the server's `ua-parser-js` soft-ban;
  they may diverge). Android: Chrome's reduced form with device frozen at `Android 10; K` (no shipping Chrome
  names a model). iOS: WebKit's prefix + `Version/… Safari/604.1` only. Unverified: `Sec-CH-UA` hints would
  need `androidx.webkit` `setUserAgentMetadata` to match an overridden agent.
- **OpenID reuse capture**: both SSO WebViews hand off `AuthCookies`, not a token string. Capture the refresh
  token, `token_provider` and `openid_user_id` from one cookie snapshot; a reuse capture missing its user
  marker fails rather than falling through to JWT auth. Bounded second reads let the native store catch up;
  preserve the single-completion latch and composition-scope cancellation. iOS also filters domain, path,
  Secure and expiry. The VM passes the bundle straight to the repository; credentials never enter navigation
  or saved UI state. Normal sessions carry no OpenID marker; a reuse session without one needs a new sign-in
  (the refresh token can't recreate it).

## Coded refusals (rc1)
- `AuthRefusal` (`viewmodel/AuthRefusal.kt`) maps the pre-auth coded 403s (`TENANT_CONTEXT_REQUIRED`,
  `TWO_FACTOR_FEDERATED_LOGIN_BLOCKED`). Key on the body's code, never the status, so the UA soft-ban, bans,
  `terms_not_accepted` and proxy 403s keep their own handling.
- Login, Register, ForgotPassword, ResetPassword, TwoFactor (via `VerifyTwoFactorOutcome.NotEvaluated.code`) set
  `refusal`; screens show `authErrorText(refusal, error)`. SSO words the callback `code` with the same string.
  Required 2FA setup doesn't: a 403 there ends the setup credential and sends the user to sign-in.

## Policy links
- `PolicyLinksFooter` on Login, Register, ForgotPassword, ResetPassword (upstream `Auth/Footer.tsx`), from
  `authServerConfig` (pending add-account server, else live). `PolicyLinks.from` maps at the top. Read
  `safeExternalUrl`, never `externalUrl`, in any new consumer (`isSafeLinkUrl` drops blank/unsafe;
  `absoluteLinkUrl` resolves relative). Register's `LegalConsent` sentence is deliberately not ported.

## Diagnostic log
- `DiagnosticLogLink` offers the log export under a sign-in error (Settings → Data is unreachable signed out):
  ServerUrl (any error), the SSO error pane, and other screens only when UiState `offerDiagnosticLog` is set.
  That follows `Result.Error.offersDiagnosticLog()` (no answer, a non-LibreChat page, or 5xx — not wrong
  password, rate limit, ban). Each submit clears it first. 2FA offers it only on `ConnectionFailure`
  (`NotEvaluated` mixes 5xx with 400/429 and has no status).
- Exports `DiagnosticLogRepository`'s buffer as is (redacted on write) via `TextFileSaver`. The log is kept
  across sign-out on purpose.

## Token storage
- `TokenDataStore` (`:core:data`, EncryptedSharedPreferences). Refresh token goes as a Cookie header (backend
  `cookies.parse(req.headers.cookie)`), access token as Bearer; refresh is an explicit POST.

## Terms
- `TermsScreen` + `TermsViewModel` (terms via `UserRepository`; `consumeAccepted()` resets the trigger). The
  shell pushes it (`Navigator.showTerms`) after sign-in, cold start and account switch when
  `interface.termsOfService.modalAcceptance` is on and `GET /api/user/terms` says not accepted, and on any
  `403 terms_not_accepted` (`AuthGate.TermsNotAccepted`).
- Not dismissable: Accept pops; Decline, top-bar arrow, system and predictive back all decline, which revokes
  this account's server session best-effort and ends its local session — this account only, no failover.
- The screen stays up through the revoke. `TermsRequirementStateHolder.decline` runs once at a time; while it
  or an accept is in flight, buttons are disabled and back is consumed and ignored (`declineUnlessBusy`) —
  never a disabled `BackHandler`, which lets the system pop. The local end is skipped if the session already
  ended or a failover moved off the account.
- The requirement is state (`TermsRequirementStateHolder`), not a route event: the screen opens whenever
  required, signed in, out of the sign-in flow and not already stacked.

## Required 2FA setup (rc1)
- `TwoFactorSetupScreen`/`TwoFactorSetupViewModel`: start → authenticator → verify → backup codes →
  acknowledge + finalize (commits a session like a 2FA verify).
- Login returns `LoginOutcome.TwoFactorSetupRequired`; the setup token must never go to `TwoFactor`/verify-temp
  (401). Mid-session `AuthGate.TwoFactorSetupRequired` makes `NavHostViewModel` end the session for re-auth (no
  failover, no expiry dialog); the stack becomes `ServerUrl, Login, TwoFactorSetup`.
- A background account hitting enrollment has no gate, only the token store's marker (its switcher row leads to
  sign-in); `LoginViewModel` names marked
  signed-out accounts on the screen's server (`twoFactorSetupAccounts`). Finalize commits via `setSession` +
  re-home, which clears it.
- Ack/finalization credentials live only in the ViewModel. 401/403 = start over at sign-in; 400 on
  ack/finalize = spent; a failed finalize retries with the same finalization token.
- **Escape to another account**: when the app parks an account at sign-in with no failover (this setup, "sign
  in again", declined terms), ServerUrl, Login and the setup screen (Start/expired) show "Use another account"
  (`SignInEscape`, passed to `authEntries`): the drawer's `AccountSwitcherSheet` (`:shared`) limited to
  signed-in accounts, no add/remove, switching via `NavHostViewModel.switchAccount` (which also enters the app when made signed out,
  `AccountSwitchStateHolder`). Hidden while signed in,
  so never in add-account mode.

## Password managers
- **Android** (autofill framework): Login email `Username + EmailAddress` (`Username` alone in LDAP username mode); Register/Reset `NewUsername`/
  `NewPassword`; password fields recognised from `PasswordVisualTransformation`. Register calls
  `LocalAutofillManager.commit()` itself because it returns to Login, whose fields keep the session open.
- **iOS** (`KeyboardOptions.withIosTextContentType()`, `:core:ui`): `.username` on email fields, `.newPassword`
  on Register/Reset, `.oneTimeCode` on 2FA. The Login password field sets no iOS role.
- Fields holding other secrets (API keys, gateway tokens, PDF passwords) use `Modifier.excludeFromAutofill()`.
- Don't use Credential Manager `createCredential()` on sign-in: it reaches only Credential Manager providers
  (would send a LastPass user's login to Google).
- Outside our control: saved logins key on package/bundle, not server; Google's autofill service offered no
  save on a tested Pixel (LastPass did); iOS has no save prompt/suggestions without associated domains.
