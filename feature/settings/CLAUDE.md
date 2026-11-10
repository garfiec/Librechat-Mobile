# feature:settings

## Structure
- Sealed `SettingsRoute : NavKey`. `SettingsTabbed` is the home (`SettingsHomeScreen`: profile card +
  category rows); keep its name, saved back stacks restore by it. Each category is a `XxxSettingsPage`
  with no Scaffold of its own, opened by `SettingsCategory(key)` (Appearance by `AppearanceSettings`,
  Advanced by `AdvancedSettings(section)`) inside `SettingsPageScaffold`. Power settings live only on
  Advanced; a category with some ends in an `AdvancedSettingsLink` to its section. Sub-pages are
  `SettingsRoute` objects declared in `navigation/*Navigation.kt` but wired through
  `SettingsNavigation.kt`.
- Delegates take a `SettingsStateHandle<S>` typed to their host ViewModel's state, so a delegate moves
  with the page that uses it.
- Logout / delete account (`AccountDelegate`) set `isLoggedOut` / `isAccountDeleted`; the screen then
  calls `onLogout`.

## Pages
- **Language & region, About** (`GeneralSettingsViewModel`): DataStore + `/api/config` only, no network
  loads. About is web's About tab plus diagnostic logs: build and server details, Copy diagnostics and
  the logs stay there for every user, never on Advanced.
  - Date & time (`DateTimeSettingsScreen`) saves each tap through `DateTimePrefsStore.update`. The
    Language & region row shows only a summary; new date options go on that page.
- **Appearance** (`AppearanceSettingsPage`, `AppearanceSettingsViewModel`, ThemeDataStore only).
  - **Server accent** (`interface.theme`): `DeploymentThemeSync` (`:core:data`) caches the active
    server's accent in `ThemeDataStore.serverAccent`, writing only from a config tagged with the active
    server (`ConfigRepository.startupConfigForServer`) and clearing another server's accent when the
    active server changes. App roots paint `serverAccent?.activeSeed ?: accentColor`; wallpaper colours
    win over both. "Use server accent" is per server (absent = on), its row shows only while the server
    sets an accent; while on, the Accent row dims and a picked colour is kept for when it goes off.
- **Chats, Voice, Models & keys, Advanced** (`ChatSettingsViewModel`, hosts the platform speech
  delegate): `ChatPreferencesController` merges the ~30 prefs into `ChatSettingsUiState` (`combine` tops
  out at five flows); `gates` holds the server gates rows follow, each copying chat's or web's rule.
  Speech errors show in Voice's snackbar. The Flagged libraries dialog uses `ArtifactLibraryConsentStore`
  directly; Ask calls `forget`, which drops only the remembered answer (one-time answers aren't listed,
  so it leaves them).
  - `ChatLayoutSettingsScreen`: `:feature:chat` is out of reach, so its preview redraws the message
    layout at `MessageBubble`'s measures (sharing `BubbleShape`). Change one, change the other.
- **Privacy & data, About's troubleshooting, Advanced's code environments** (`DataSettingsViewModel`, hosts
  `DataManagementDelegate`). Memories and MCP servers open from the drawer's Workspace tray
  (`:feature:conversations`), not from Settings.
  - `archiveAllSupported` means "the server has not 404'd it" and is ANDed with the version gate in the
    `uiState` combine.
- **Account** (`AccountSettingsViewModel`, hosts `AccountDelegate` + `TwoFactorSecurityDelegate`): loads
  the profile and balance only; the home's profile card and Models & keys' provider keys row use it
  too. Administration (role-admin rows) shows only when `isAdmin` (fail-closed).

## Memories (`MemoriesScreen`, `MemoriesViewModel`, `MemoriesUiState.kt`)
- The VM owns search, partition chips, role gates (permissive) and the sheet draft (`MemoryEditorState`).
- Only a failed FIRST load is `loadError`; later failures are one-shot `MemoriesEvent.Failed`, and Retry
  re-runs only a `MemoryOperation.Retryable`. A save fails into its own sheet (`saveFailure`), whose Save
  is the retry; no sheet can be closed or opened over one mid-save. Strings in
  `screen/MemoryFailureText.kt`; only a server 400's own sentence is shown verbatim.
- Gates: the Workspace row uses `canReachMemoriesSettings()` (opt-out needs no READ), chat's Manage uses
  `canOpenMemories()`. When the list is denied (no READ, or first-load 403): a role that may opt out gets
  `switchOnly` (the list is never requested without READ), others get `noAccess`. After a 403 the switch
  stays only on an explicit OPT_OUT grant (the 403 may mean USE is missing).
- The opt-out switch reads `MemoryRepository.memoriesEnabled` (null = unknown, show no value).
- `MemoryEditSheet` uses `AdaptiveModalBottomSheet(interceptDismiss = isDirty || isSaving)`; a dirty
  dismiss raises `DiscardChangesDialog`, and `dismissEditor()` ignores dismissal mid-save. Keep fields
  enabled while saving (disabling drops focus, the keyboard and the sheet); `updateDraft` ignores edits
  until the save answers. Opening the sheet clears page focus, or the keyboard returns to search on
  close. View mode (no UPDATE, or content-filtered) is read-only, Close only. Token share
  (`MemoryEditorState.tokenShare`) is web's formula, personal entries only.
- A key can be renamed: `UpdateMemoryRequest.key` is sent only when it changed (the by-id route
  pattern-checks any key it gets); a key-route rename is create-then-delete, so reload after any rename
  failure.

## MCP servers (`McpServersScreen`, `McpViewModel`)
- Add hidden without MCP_SERVERS CREATE (`canCreateServer`). Edit/Delete follow `McpServerAccess`
  (no `dbId` or `consumeOnly` = read-only; else the effective permission bits; unknown = editable).
- Status comes from `mcpDisplayStatus` (`:core:model`, registered mirror of upstream `MCPStatusBadge`),
  never from `isConnected`: needs-sign-in, idle and failed are all `disconnected` on the wire and must not
  look alike.
- Cards are uniform height: description always 2 lines, meta line always 1 (an error ellipsizes, never
  wraps), so grid rows line up.
- `McpServerSheet` closes when `selectedRow` is null. `McpToolsSheet` opens from the top bar only.
- `McpServerEditorScreen` (`McpServerEditor(serverName?)`, null = add): the VM owns draft, discard prompt,
  save, re-entry prompts and carry-over (`McpPreservedFields`). The list refreshes on ON_RESUME.
  - Offers only SSE and STREAMABLE_HTTP; a stored `HTTP` shows as Streamable HTTP and is saved back
    as-is unless changed.
  - Adding requires "I trust this server" (client-side only; an edit starts checked). Wording from
    `interface.mcpServers.trustCheckbox` when configured, else our strings.
- **OAuth consent:** `reinitialize` may answer `oauthRequired` + `oauthUrl` → `pendingOAuth` → consent
  dialog. Do NOT auto-launch: the redirect hands a third party this session, so it must be an explicit
  user action. `continueOAuth()` asks `McpOAuthLauncher` (`:core:data`, the one version gate, shared
  with chat): `InApp` renders `McpOAuthSignInDialog` (`:core:ui`); `External` (known server before rc1,
  or a bind that sets no binding cookie or 404s; before v0.8.3-rc1 the SPA catch-all answers the bind
  200 HTML with no `Set-Cookie`) opens the system browser. Bind at the tap, not when the prompt
  appears: the CSRF cookie lives ten minutes.
- The in-app sign-in fires no ON_RESUME, so `onOAuthOutcome` reloads itself and reports via
  `OAUTH_SIGNED_IN_MARKER` / `OAUTH_FAILED_MARKER`; closing the page is silent. `connectionDeferred`
  reports via `DEFERRED_MARKER`. Sentinels exist because the VM can't read compose resources.

## Role admin (`RoleSkillsAdminScreen` / `RoleMemoriesAdminScreen`)
- Both render the stateless `RolePermissionsEditor` and PUT a flat partial body. Save is off until a
  switch differs from the server's last answer, and stays on after a refused save as its retry. A load
  generation drops any load/save answering after a newer load. Saving the admin's own role publishes the PUT's answer
  as the app's role (`RoleRepositoryImpl`). `showAdminWarning` toggles carry web's
  ADMIN warning (mirror `admin-access-warning`).

## Shared links (`SharedLinksScreen`, `SharedLinksViewModel`)
- Search shows only when `SearchCapabilityRepository` says server search is on (300 ms debounce,
  `MAX_SHARED_LINK_SEARCH_LENGTH` cap). A card opens the share sheet with `focusShareId`; Update and
  the files choice live only in the sheet.
- Paging uses a generation token (sort/search restarts from a null cursor, drops older pages);
  `shouldLoadMore` must read the current state, not a captured one. Quiet reloads on ON_RESUME and
  `ShareRepository.revision` refetch as many rows as were loaded. A failed first load is an error state
  with Retry, never the empty state.

## Code execution (`CodeExecutionScreen`, `CodeSetupScreen`)
- Advanced's Code environments row shows when `agents.capabilities` has `stateful_code_sessions`.
- Default workspace: `PATCH /api/user/preferences`, optimistic, reverted if refused; a disallowed saved
  scope is listed disabled.
- Machines (network-direct): polled every 10 s while resumed; File changes / Commands only with
  permissions and `canEdit`; Delete only with `canDelete`; Connect VM only when `controlPlanes` exist.
  Every change refreshes `/api/endpoints` so the composer's machine list follows.
- Setup approval (`CodeSetup(attemptId)`, also `librechat://code-setup/<id>`): polls every 2 s while the
  attempt can move; nothing is approved until a control plane is picked and Approve tapped. The routes
  run `requireSameOrigin`, which passes because the app sends no `Origin` or `Sec-Fetch-Site`: don't
  add either.

## Passkeys (management only)
- `PasskeyRepository` is network-direct, never cached (a password reset deletes every passkey). Row gated
  by `isPasskeyManagementAvailable` (`AccountGates.kt`: `passkeyLoginEnabled` and not a tagged build
  below rc1).
- **No sign-in and no Add**: a WebAuthn ceremony from a native app gets origin `"null"` upstream (and iOS
  needs a per-server entitlement). Don't add a Credential Manager / ASAuthorization path until upstream
  accepts native origins.
- Remove: a local account gives its password (`ContentType.Password`, not `SecretTextField`); a non-local
  one sends none until a 403 demands it. 403 = wrong password inline, 404 = already gone, 429 = wait;
  errors render inside the dialog. A list 404 refetches `/api/config`.

## Pull request account (Account › Connected accounts)
- `PullRequestAccountViewModel` over `PullRequestConnectionRepository`; shown when
  `pullRequestConnectionKnowable` and `showsPullRequestAccount` (upstream `hasPullRequestConnect`: a
  grant exists, or `pullRequestsConnect` is set and the status hasn't said `configured: false`). The
  status is re-read when the gate opens, on each return to the page, on an account switch and after
  every connect or disconnect.
- Connect: `GET /oauth/connect`, then `McpOAuthSignInDialog` with those cookies. No bind step and no
  system-browser fallback (the callback refuses a browser without them). A 404 hides the row for the
  session.
- Disconnect failure stays in the dialog; if the re-read shows the grant gone, close as on success.

## Change email
- Gate `isEmailChangeAvailable`: `provider == "local"` (null is NOT local), `emailEnabled`,
  `allowEmailChange`, and hidden only on a server known to predate the route (`isRuledOut`).
- `EmailChangeDelegate`; `EmailChangeError` (`:core:model`) maps every `code`; never show the server's
  message. Field codes go under their field, the rest inside the dialog. A wrong password clears only
  the password. 429 holds Send for `Retry-After`, else 2 min.
  Never retry: each attempt mails the old address.
- The Account page re-reads the profile on every resume after the first (`refreshOnReturn`) and says
  "Email address updated" only when a link was sent in this ViewModel's life.

## API keys (`ApiKeysScreen`)
- The key value exists only in the creation response; `ApiKeyCreateDialog` must show it immediately.

## Server connection (`ServerHeadersDialog`, `ServerHeadersViewModel`)
Edits the **active** server's static request headers (Advanced › Server). Pre-login
`ServerUrlScreen` renders the same `CustomHeadersEditor` (`:core:ui`).
- The dialog is stateless; forwarding the ViewModel trips detekt-compose `ViewModelForwarding`.
- The screen closes it on `saved`, so an unpersisted save keeps the typed rows. Dismissing while
  `isDirty` prompts, then calls `discardEdits()` (the VM outlives the dialog).
- `AlertDialog` clips rather than scrolls, so the body wraps the editor in `verticalScroll`.
- A null `headersForServer` is not an empty set: `loadFailed` shows a warning, and a server change still
  clears the typed rows. Refusing an empty save over an unloaded credential belongs to
  `ServerRepository.setHeaders`, not here; a non-empty save is the recovery path.
- `reload()` on open; unsaved edits win over the re-read, checked on both sides of the (suspending) read.
- `showServerHeadersDialog` is `rememberSaveable` (unlike other dialog flags): a rotation that closed it
  would strand half-typed rows behind a `reload()` that refuses to overwrite them.
- Save failures render inside the dialog (the snackbar draws behind its scrim); `saveFailure` is state
  that clears on the next edit, not a one-shot event.
- Save does not re-probe: `ServerRepository` patches its in-memory map under the write lock (it is
  the table's only writer and deliberately doesn't observe it), so the next
  request picks it up; in-flight requests keep their snapshot.
