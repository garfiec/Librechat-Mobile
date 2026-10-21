# feature:settings

## Screen
`SettingsScreen` -- sealed interface `SettingsRoute : NavKey` with primary route `SettingsTabbed` (`@Serializable` data object). Receives `onLogout` and `onNavigateBack` callbacks.

## Sections
- **Account**: displays user profile from `UserApi.getUser()`
- **Appearance**: its own page (General → Appearance): theme (System / Light / Dark), interface style, accent colour, wallpaper colours, via `ThemeDataStore`
- **Server**: shows current server URL from `ServerDataStore`
- **About**: app version info
- **Danger Zone**: delete account with confirmation dialog

## Theme
- `ThemeMode` enum: `SYSTEM`, `LIGHT`, `DARK`
- Persisted in `ThemeDataStore` (DataStore<Preferences>)
- `setThemeMode()` writes to DataStore; the app's root composable observes `themeDataStore.themeMode` flow
- Applied via Compose `MaterialTheme` with `isSystemInDarkTheme()` for SYSTEM mode

## Logout
- `AccountSettingsViewModel.logout()` calls `AuthRepository.logout()` which clears tokens from `EncryptedSharedPreferences`
- Sets `isLoggedOut = true` in UI state; screen calls `onLogout` callback to navigate to auth graph

## Delete Account
- `AccountSettingsViewModel.deleteAccount()` calls `UserApi.deleteUser()` then `AuthRepository.logout()`
- Sets `isAccountDeleted = true` in UI state; triggers navigation to auth graph

## ViewModel Dependencies
- `UserApi` -- fetch/delete user profile
- `AuthRepository` -- logout (clear tokens)
- `ThemeDataStore` -- observe and set theme preference
- `ServerDataStore` -- observe current server URL

## Future Sections (from spec, not yet implemented)
- Chat tab (font size, message layout, auto-scroll toggle)
- Speech tab (STT/TTS engine config)
- Data tab (import/export, clear chats, Shared chats summary row)
- Balance tab (conditional, token credits display)
- 2FA setup/disable in Account section

### New Settings Sections
- `SettingsScreen` now organized into: Account, Appearance, General (Language & region: Language, Date & time), Chat (Presets), Advanced (Fork Behavior, Commands), Server, About, Danger Zone
- Each new setting opens a dialog or navigates to a dedicated screen

### Settings Sub-screens (via SettingsNavigation)
- `MemoriesScreen` — full memory CRUD, enable/disable toggle, own ViewModel (`MemoriesViewModel`); opened from the Data tab's Memories row (route `Memories`)
- `McpServersScreen` — server cards (`McpServerCard`), search, a "Needs attention" filter, CRUD, reinitialize, tools sheet (`McpViewModel`); opened from the Data tab's MCP servers row (route `McpServers`). Add is hidden without MCP_SERVERS CREATE (`McpUiState.canCreateServer`)
  - Status comes from `mcpDisplayStatus` (`:core:model`, a registered mirror of upstream `MCPStatusBadge`), never from `isConnected`: needs-sign-in, request-scoped-idle and failed are all `disconnected` on the wire and must not look alike. Each card shows the status in words plus the one action that fixes it (Sign in / Retry / Connect) and an overflow menu (Reconnect when there is no action, Edit/Delete where allowed)
  - Tapping a card opens `McpServerSheet` (status, full error, URL, actions, filterable tools) via `McpViewModel.openServer`; it disappears once `selectedRow` is null (server deleted). `McpToolsSheet` is the all-tools view from the top bar only
  - Rows, sort and filter are pure functions in `McpServerRows.kt`, derived in `McpViewModel.uiState`; Edit/Delete follow `McpServerAccess` (no `dbId` or `consumeOnly` = read-only; else the effective permission bits; unknown = editable)
- `PresetManagerScreen` — list/delete presets (`PresetManagerViewModel`)
- `CommandsConfigScreen` — enable/disable slash commands
- `AppearanceSettingsScreen` — theme, interface style, accent colour, wallpaper colours; reached from one summary row on the General tab (route `AppearanceSettings`, `AppearanceNavigation.kt`). Own ViewModel (`AppearanceSettingsViewModel`, ThemeDataStore only); the General tab's summary row reads it too
- `PrefetchSettingsScreen` — background prefetch switches, depth and status summary; reached from one row in the Data tab (route `PrefetchSettings`, `PrefetchNavigation.kt`, which also holds `PrefetchActivity`). Own ViewModel (`PrefetchSettingsViewModel`, `PreferenceStore` only) plus `PrefetchActivityViewModel` for status
- `ChatLayoutSettingsScreen` — thread or two-sided, bubbles, avatars, under a live preview of a short conversation; reached from the Chat tab's Chat layout summary row (route `ChatLayoutSettings`, `ChatLayoutNavigation.kt`). Own ViewModel (`ChatLayoutSettingsViewModel`, SettingsDataStore only); the Chat tab's summary row reads it too. `:feature:chat` is out of reach, so the preview redraws the message layout at `MessageBubble`'s measures (sharing `BubbleShape` from `:core:ui`) — change one, change the other
- `ChatSettingsScreen` (Chat tab) — display, behaviour, artifacts, speech, fork mode. Own ViewModel (`ChatSettingsViewModel`, hosting the platform speech delegate); `ChatPreferencesController` merges its ~30 DataStore prefs into `ChatSettingsUiState` because `combine` tops out at five flows. Errors (e.g. a failed voice test) show in the tab's own snackbar
- `GeneralSettingsScreen` (General tab) — language, date & time, tablet gesture, About. Own ViewModel (`GeneralSettingsViewModel`): DataStore + `/api/config` only, no network loads; the language and date & time dialog flags live there too
- `AccountSettingsScreen` (Account tab) — profile, avatar, balance, 2FA, sign out, delete account. Own ViewModel (`AccountSettingsViewModel`, hosting `AccountDelegate` + `TwoFactorSecurityDelegate`): loads the profile and balance only. The Data tab's Memories row reads the opt-out from the profile separately (`DataSettingsViewModel.refreshSummaries`)
- `DataSettingsScreen` (Data tab) — clear/archive/export, cache, keys, diagnostic logs, and one summary row each for Memories and MCP servers (count, or "Off" for the memories opt-out), which open `MemoriesScreen` / `McpServersScreen`. Own ViewModel (`DataSettingsViewModel`, hosting `DataManagementDelegate`); the summaries reload on every resume (`refreshSummaries`) so they catch up after editing on either page. A row is hidden when the role lacks that feature's USE permission. Role gates and the archive-all version gate are derived in its `uiState` combine; the internal `archiveAllSupported` means "the server has not 404'd it" and is ANDed with the version gate there
- `SharedLinksScreen` — the "Shared chats" page (route `SharedLinks`, from the Data tab's summary row): `SharedLinkCard`s in an `UpToTwoColumns` grid, search (only when `SearchCapabilityRepository` says server search is on; 300 ms debounce, `MAX_SHARED_LINK_SEARCH_LENGTH` cap) and a session-only sort (no saved pref yet). A card opens the share sheet (`SharedLinkSheetHost` over the VM's `ShareLinkController`, `focusShareId` = the card's link, so a link that isn't the conversation's newest opens reduced); ⋮ = Open chat (`settingsEntries(onOpenConversation)`, wired to the chat in `LibreChatNavHost`), View shared version (the in-app viewer, via `LocalOpenSharedChat`; hidden when absent), Copy link (page snackbar), Delete (confirm). Update and the files choice live only in the sheet. Own ViewModel (`SharedLinksViewModel`): paging with a generation token (a sort/search change starts from a null cursor and drops older pages), `shouldLoadMore` read from the current state (a captured one never loaded past page one), quiet reloads on ON_RESUME (`refreshOnReturn`) and on `ShareRepository.revision`, refetching as many rows as were loaded. A failed first load is an error state with Retry, never the empty state
- Delegates take a `SettingsStateHandle<S>` typed to their host ViewModel's state, so a delegate moves with the page that uses it
- Routes: `Memories`, `McpServers`, `PresetManager` (all `@Serializable` data objects extending `SettingsRoute`)
- **Gotcha**: Memories and MCP navigation routes defined in their own files (`MemoriesNavigation.kt`, `McpNavigation.kt`) but wired through `SettingsNavigation.kt`
- `McpServerEditorScreen` (route `McpServerEditor(serverName: String?)`, null = add) is the full-screen add/edit form, built like the schedule editor: `McpServerEditorViewModel` owns the draft, the discard prompt and the save, including the re-entry prompts (`MCP_OAUTH_SECRET_REENTRY_REQUIRED` / `MCP_API_KEY_REENTRY_REQUIRED`) and the iconPath/obo carry-over (`McpPreservedFields`). `McpServersScreen` refreshes on ON_RESUME (`refreshOnReturn`) to pick up a save
- **Trust checkbox**: adding a server requires "I trust this server", as the web form does. It is client-side only (the server doesn't check it); an edit starts checked. The wording comes from `interface.mcpServers.trustCheckbox` when configured (`mcpTrustCheckbox()` + `resolveLocalizedConfigText`, markup stripped), else our strings
- **Gotcha**: the editor offers `McpServerType` SSE and STREAMABLE_HTTP only; a stored `HTTP` shows as Streamable HTTP and is saved back as-is unless the user changes it
- **Gotcha**: Memory keys are immutable after creation (edit dialog disables key field)

### API Keys
- `ApiKeysScreen` — list/create/delete API keys, own ViewModel (`ApiKeysViewModel`)
- Route: `ApiKeys` (`@Serializable` data object extending `SettingsRoute`), accessible from Settings → Security section
- `ApiKeyCreateDialog` is two-phase: name input → show created key value with copy button
- **Gotcha**: The key value is only available in the creation response — it cannot be retrieved again from the server. The dialog must show it immediately after creation.

### Server connection (gateway headers, issue #287)
- `ServerHeadersDialog` + `ServerHeadersViewModel` — edits the **active** server's static request
  headers (Cloudflare Access service tokens and equivalents). Reached from **Settings → Account →
  Server connection**, grouped with the other credentials rather than with data/diagnostics.
- The dialog is **stateless** (the screen owns the ViewModel and passes state + callbacks down) —
  forwarding the ViewModel into it trips detekt-compose's `ViewModelForwarding`.
- Two things the dialog does NOT own: it closes on `saved` turning true, driven from the screen, so a
  save the store could not persist keeps it open with the typed rows intact; and dismissing with
  `isDirty` prompts before discarding, then calls `discardEdits()` — the ViewModel outlives the
  dialog, so an abandoned edit would otherwise still be there on the next open.
- **Gotcha**: `AlertDialog` clips its content rather than scrolling it, so the dialog body wraps the
  editor in its own `verticalScroll` — otherwise a few rows plus the keyboard put the fields out of
  reach.
- The same headers are editable pre-login on `ServerUrlScreen`; both render `CustomHeadersEditor` from
  `:core:ui` so the two can't drift.
- **Why it exists post-login at all**: a gateway credential can be rotated or revoked mid-session, and
  the pre-login editor is only reachable by signing out — not a step anyone would guess from the
  resulting "could not reach the server."
- **A null read is not an empty header set.** `headersForServer` returning null sets `loadFailed`: the
  editor shows a warning instead of an empty list, and a server change clears the typed rows even
  though the new server's read failed, because those rows belong to the server that just went away.
  Refusing the *empty save* that would delete an unloaded credential is deliberately **not** here —
  `ServerRepository.setHeaders` owns it, so the pre-login editor is covered by the same rule instead
  of a second copy of it. A non-empty save is the recovery path and goes through.
- **The dialog re-reads when it opens** (`reload()`, called from the row's `onClick`). The URL is
  observed, so for one server the load happens exactly once — a read that failed would otherwise keep
  warning about a store that has since recovered, for the life of the process. Unsaved edits win over
  the re-read, and that is checked on **both** sides of the read: it genuinely suspends while the
  store is recovering, and the dialog is already on screen by then, so the user is typing into it.
- `showServerHeadersDialog` is `rememberSaveable`, unlike the screen's other dialog flags. This
  ViewModel outlives the composition and holds half-typed rows, so a rotation that closed the dialog
  without running the discard prompt would strand them behind a `reload()` that (correctly) refuses to
  overwrite unsaved edits.
- **Save failures render inside the dialog**, not as a snackbar: a refused save leaves the dialog open
  and otherwise unchanged, and the Scaffold's snackbar draws behind its scrim — so the message would
  be invisible and the Save button would look dead. `saveFailure` is therefore state that clears on
  the next edit, not a one-shot event.
- Save is explicit and does **not** re-probe: `ServerRepository` patches its in-memory map under the
  same lock that performs the write (it is the table's only writer, and deliberately does *not*
  observe it), so the next ordinary request picks the values up. In-flight requests keep the headers
  they were snapshotted with — immutable by design.

### Dialogs
- `LanguageSelectorDialog` — 37+ locales with search, single-select radio
- `DateTimeSettingsDialog` (#445) — timestamp style, clock and date format in one dialog with a live preview of the pending choices; Save writes all three in one DataStore edit. The General-tab row shows only a summary (a live sample + style name) — keep new date options in the dialog, not on the tab
- `ForkSettingsDialog` — 3 fork modes (`DIRECT_PATH`, `INCLUDE_BRANCHES`, `TARGET_LEVEL`); labels/descriptions come from `fork_mode_*` string resources via `forkModeLabel()` / `forkModeDescription()`, not from the enum

### MCP OAuth consent (v0.8.8)
- `reinitialize` can answer `oauthRequired` + `oauthUrl`, meaning the server is waiting on the user
  rather than connected. `McpViewModel` holds that as `pendingOAuth` and `McpServersScreen` renders
  a consent dialog.
- **Gotcha**: do NOT auto-launch the browser. The redirect hands a third-party provider this
  session, so the launch has to be an explicit user action, not a side effect of tapping reconnect.
- `connectionDeferred` is a third outcome: the ack landed but the connection is still being made.
  Reported via the `McpViewModel.DEFERRED_MARKER` sentinel, which the screen swaps for a localized
  string (the VM has no access to compose resources).
