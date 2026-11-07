# core:data

Room (`db/`), DataStore (`datastore/`), secure token storage, and repository implementations
(`repository/`, bound in `di/DataModule.kt`). The coordination layer between network and local
storage.

## Rules

- **Every `:core:network` API-service call sits inside `safeApiCall`, `apiCallCatching`, or a flow
  ending in `.flowOn(dispatcher)`.** `safeApiCall` carries the IO hop as well as the error mapping, so
  a call reached any other way runs on the caller's dispatcher (the UI thread, from `viewModelScope`).
  See `core/common/CLAUDE.md`. `CommonTokenDataStore`'s refresh POST is the one non-API HTTP call; it
  is always driven from inside a request that already hopped.
- Entities are internal; feature modules see `:core:model` domain models.
- DAO reads the UI observes return `Flow`, not suspend functions.
- Read-through cache: emit Room, fetch, upsert (Room re-emits); on network error the cache stays
  visible and the error is surfaced separately.

## Account-keyed token store (`CommonTokenDataStore`)

Each account's session is one versioned encrypted/Keychain record (`acct:<accountId>:session_credentials`;
bare `session_credentials` while staging): access token, refresh token, optional opaque OpenID marker.
Several accounts are retained at once.

- `SecureSessionRecords` reads legacy access/refresh keys only when the record is absent, and removes
  them only after verifying the replacement write. **A present but invalid record never falls back to
  older credentials.** All staging, account-resolution and teardown paths handle the whole record.
- Request snapshots read bearer and marker together, from memory for the active slot (`cachedAuth`,
  updated wherever the slot is written): on iOS a storage read is Keychain IPC. Normal login/2FA
  replaces the session without OpenID markers.
- **Interactive sign-in (`setTokens`) stages** under the bare keys and drops the active binding;
  `onAccountResolved` re-homes it, so a re-login can't corrupt another active account's slot.

### Concurrency (four cooperating pieces, not one refresh mutex)

- **`stateMutex`** guards in-memory identity + cached bearer and short storage I/O. **Never held across
  the refresh POST**, so switch/logout never stall behind a slow refresh. OpenID refresh snapshots its
  cookie bundle and epoch under it, releases for the POST, and commits only if the slot's epoch is
  unchanged.
- **Per-account `flightMutex`/`flights`** single-flight same-account refreshes across the POST, so a
  second 401 reads the rotated token instead of re-POSTing a spent one.
- **`tokenEpoch`** is bumped by every op that invalidates token truth (logout, clear, removal, new
  authentication, identity re-home). A refresh captures the epoch before its POST and discards its result if it changed, so it can't
  resurrect a cleared session.
- **`rejectedRefreshes`** stand-down markers are **read and written inside the flight lock** (a check
  outside it is passed N times by a fan-out → one POST per request). Two modes: **token-keyed** (a
  proactive renewal had these bytes rejected; damps proactive callers only, since a reactive ladder
  must settle to route to re-auth) and **null-token** (an unattributed 403; damps everyone; a proactive
  outcome must never narrow it back to token-keyed). The coalescing check runs **before** the
  suppression gate, and **every** successful commit clears the marker (the server's reuse-path 2xx
  doesn't rotate the token, so a slot can hold a marker for the bytes it stores).

### Refresh outcomes

- **A refresh `403` is classified by provenance.** LibreChat's own bodies (`Invalid refresh token`,
  `Invalid OpenID refresh token`, `No session found`; in `scripts/mirrors.json`) are terminal and settle
  without the ladder. **Any other 403 keeps the slot** (bouncer/WAF; logging out over it strands the
  user). 401 keeps its ladder. Upstream's `res.status(4xx).redirect('/login')` arrives as a **302**, so
  a server-rooted `/login` Location is terminal in the 3xx arm; an absolute portal URL is a gateway's
  and is not. The 5xx ladder stays undamped.
- **A refresh `200` can end the session (v0.8.9-rc1)**: `{ code: TWO_FACTOR_ENROLLMENT_REQUIRED,
  tempToken }`, no token, server session deleted. `handleSuccess` checks it **before**
  `CookieHelper.update` (which would throw on the cleared cookie → Retryable limbo). It drops the slot,
  latches the expiry report for the active binding, sets `authGate = AuthGate.TwoFactorSetupRequired`,
  and returns `Transient`, **never `settle()`** (an expiry would fail over to another account).
  `authGate` is cleared wherever the expiry latch is re-armed. Because the gate is raised only for the
  active binding, every enrollment also writes `acct:<id>:two_factor_setup_required`
  (`TokenManager.isTwoFactorSetupRequired`, `AccountSwitcher.needsTwoFactorSetup` /
  `awaitingTwoFactorSetupOn`), starting no failover and leaving the active pointer alone; a re-homing
  sign-in, teardown or removal clears it.
- **A kept session that cannot renew is reported.** The unattributed-403 arm publishes
  `SessionHealth.RenewalBlocked(accountId)`; the shell's banner offers **Sign in again** →
  `endSessionForReauth` (drops the slot and bumps its epoch like a hard expiry, keeps roster + Room
  rows, latches the expiry report, emits nothing; the ViewModel routes). It clears on a successful
  commit, sign-in, teardown or hard expiry, and **not** when the 60 s stand-down lapses (that means
  "we'll POST again", not "the server answers"). The banner doesn't fix a bad stored URL; that is
  `cleanServerUrl` (`core/network/CLAUDE.md`).

### Keystore failure and cold start

- Wrap `EncryptedSharedPreferences` access in try/catch (broken OEM Keystores). `TokenDataStore`
  recovers in place: a one-entry decrypt failure drops only that entry (never rebuild over it, or
  every retained account is logged out); a keyset failure wipes and rebuilds; if that fails, an
  in-memory session. **Construction never throws into `startKoin`.**
- The encrypted store is built lazily on first use, and `TokenCacheWarmer` (eager single) calls
  `warmTokenCache()` on IO, keeping 300–900 ms of keystore work off the `startKoin` thread. Rules:
  - **The warmer performs the load; it never awaits one** (a logged-out cold start triggers no other
    load, so it would never complete).
  - **`warmTokenCache()` takes `stateMutex` and is the only writer of the cache fields outside a
    mutation.** Every mutator holds the lock across its critical section, so a warm runs wholly
    before or after it; the mutex, not `tokenInitialized`, makes a late warm harmless.
  - **`ensureTokenLoaded()` reads through without publishing.** Populating the cache unlocked could
    overwrite a fresh `setTokens` bearer with the pre-login disk value and pin it until process death.
- `emitSessionExpired` isn't `suspend` and can't warm; it suppresses a scoped emit only against a
  *seeded* identity (an unseeded key is null and would swallow the expiry).
- "A write before the warm must not land in `memoryFallback`" has **no unit test** (Robolectric can't
  build `EncryptedSharedPreferences`); verify on a device.

## Preferences (`AppPreferences`, `PreferenceMigrations`)

- New device-wide preferences are declared in `AppPreferences` and accessed through `PreferenceStore`,
  not added to `SettingsDataStore`. When moving an old setting over, **keep its key name exactly** (or
  it resets for everyone) and **delete the old member in the same change** (`AppPreferencesTest` can't
  see keys owned by other stores on the same file). List every new spec in `AppPreferences.all`: that
  list is all the test's default pinning and duplicate-key check cover.
- **Defaults are applied on read, never written.** `always(x)` or `perChannel(openSource, playStore,
  appStore)` (every channel named, so a new channel fails to compile).
- **One-time changes are `PreferenceMigrations`** (run by `PreferenceMigrationRunner` as a DataStore
  `DataMigration`: before any read, change + ledger entry in one atomic write). Ids `YYYYMMDD-slug`,
  append-only, immutable once shipped. `reset(spec)` / `force(spec, value)`.
- Ledger: only migrations run or waived **on this channel** are recorded (iOS store and sideload builds
  share a bundle id). A fresh install, or a corrupt file replaced with an empty one, waives this
  channel's migrations. Unknown ids from a newer build survive a downgrade.
- **A migration body must be total.** A throw fails every read/write of the shared settings store
  (server URL, roster, all settings) on every launch; the corruption handler can't recover it.
- `DateTimePrefsStore` (global, not per-account) is its own class on the shared store because
  `SettingsDataStore` is at detekt's `LargeClass` limit. It warms a first-frame seed like
  `ThemeDataStore`; both app roots gate the first frame on its `isReady`.
- `SettingsDataStore.compactNudgeSnoozes` is **device-scoped** (no account data), so logout's
  `acct:`-key sweep doesn't purge it; it is bounded instead (200, oldest evicted by sequence number,
  read-modify-write inside `dataStore.edit` under `NonCancellable`, like `incrementModelUsage`).

## Network-direct repositories (no Room cache)

- `MemoryRepository`: `memoriesEnabled: StateFlow<Boolean?>` is the account-scoped opt-out (null until
  known, reset on switch/logout), seeded by `refreshMemoriesPreference()`, updated by a successful
  `updatePreferences`; **every screen that shows or obeys the opt-out reads it there.** Failures carry no
  English: status on `ApiException`, or `MemoryContentFilteredException`. Delete/update address `_id`
  and fall back to `key` + `agentId` **only on a 404 without a memory-handler JSON `error`** (missing
  route); a by-id `Memory not found.` / `Agent not found.` is returned as is (falling back would hit a
  re-created row). A rename sends `key` only when it changed. Bound with explicit `single` (takes the
  application scope).
- `McpRepository` identifies servers by `serverName`.
- `CodeEnvironmentRepository`: machine status and a saved chat's code-workspace decision (from the
  single-conversation GET). **Never cache the decision locally**: one that disagrees with the server's
  gets the turn refused as `locked`.
- `CodeWorkspacePreferenceStore`: last workspace per agent and machine (web's
  `workspacePreferences.ts`), account-scoped, most recent first, capped at 100. Hints only; mirrored in
  `scripts/mirrors.json`.

## Per-server gateway headers (`servers` table)

`ServerRepository` owns them and is the `ServerHeadersProvider` the HTTP clients read
(`core/network/CLAUDE.md` for the request side).

- **Device-scoped, no `accountId`**: deliberately absent from `AccountDataPurger` and the detekt
  tenancy rule, since the headers are what let a user log back in.
- **Natural primary key** (`server_id` = derived server id); a surrogate would make `@Upsert` match
  zero rows. The whole-row `@Upsert` is safe only with one non-key column and one writer: **any added
  per-server column must make writes column-scoped first.** The table is named for the deployment so
  such state can live here.
- **`headersFor` is non-suspend** (called inside `SwitchGate`'s lock and in the `State` phase), backed
  by a `@Volatile` map behind `awaitWarm()`, resolved `createdAtStart`.
- **One seed read, then patch-on-write** under the same lock. Don't observe the table: a stale Room
  emission can arrive after a save and revert it.
- **No migration from the old `srv:<serverId>:custom_headers` prefs**, because no release shipped them.
  Don't add one "for safety" (a second writer reintroduces the race). If a release from that window
  ever surfaces, a drain is required.
- **A read failure is not "no headers".** `headersForServer` returns null and editors show a warning.
  Undecodable rows and rows whose pairs all sanitize away are tracked per-server as failures; a
  successful write doesn't clear the failure flag; the read is re-attempted after a failure. **A write
  outranks a later failed read** (`writtenHere`).
- **The empty-write refusal lives in `setHeaders`** (`HeaderWriteFailure.UnverifiedDelete` when the
  value was never read), not in the editors. A non-empty write always goes through (it's the recovery
  path).
- **Two read paths; don't merge them.** `headersFor` / `awaitWarm` serve requests from the startup
  snapshot and never retry; `headersForServer` serves editors and reads through every call (opening an
  editor heals a store that failed at startup).
- **Plaintext, deliberately**: `allowBackup="false"` and the iOS sandbox cover exfiltration;
  encryption would add Keystore wipe-and-rebuild, and losing this value leaves a server unreachable.

## Custom project order (`project_order`), Switchboard-only

Drawer order keyed by `(accountId, projectId)`, purged by `AccountDataPurger`.

- `ProjectRepository.listAllProjectsInCustomOrder` fetches all pages and applies `ProjectOrdering`
  (saved by slot, unplaced after, oldest first; with nothing saved the server order is kept and saved).
- **Only an unfiltered, complete list saves back** (it also prunes deleted projects); never a search or
  a page-capped fetch (`AllProjects.isComplete`).
- Read-arrange-write and `saveProjectOrder` share one `Mutex`. `saveProjectOrder` takes the account
  captured at the drop, not the active one, and bumps `orderRevision`, not `revision`.
- If upstream ships ordering: `@DeleteTable` AutoMigrationSpec and read the server's order.

## Stored full tool calls (`tool_call_parts`, v0.8.9-rc1 previews)

Full content of previewed tool calls for finished messages, written by `ToolPartHydrationDelegate` and
the prefetcher.

- **Beside the message rows, never merged into them**, so `messages` stays exactly what the server sent
  and refreshes need no merge.
- **FK to `messages.messageId` with CASCADE**: every message delete takes the parts. Safe only because
  `messages` is never written with REPLACE. Rows are written only when the message is cached for the
  same account (`storeWithinBudget`).
- `""` for absent `stepId` / `agentId`; `ToolCallPartRef.normalized()` maps blanks to null.
- Bounded: 16M chars per account (oldest `storedAt` evicted); a single call over 2M is never stored; a
  row decoding as a preview is never returned. `isFullCopyOf` (caller's check) decides whether a row
  still stands for the preview. Export never reads this table.
- `PrefetchEngine.warmToolParts` fills it after a re-warm, under its caps (256k per declared call, 12
  per conversation, 48 / ~4M chars per pass), paced and breaker-counted. It skips a stored call by
  identity without re-checking lengths; the chat screen does that check on open. Never prefetches
  subagent calls with omitted transcripts (undeclared size); asks each call at most once per process.
  A conversation's watermark is written as soon as its messages are cached; one cut short is finished
  next pass from the cached messages (in-memory pending set).

## Message search index (`message_search` + `messages_fts`)

FTS4 + `unicode61` (`MessageSearchFtsEntity` says why), external-content over a narrow side table.

- **Search data never lives on `messages`** (wide rows; overflow pages and a full rewrite on backfill).
  `message_search` holds filter/sort columns and `searchTextOf` output (visible text, then reasoning,
  split at `visibleLength`; capped at 128k). The "in reasoning" marker and "answers only" compare a
  match against `visibleLength`, so neither reads `messages` rows.
- **Write messages only through `MessageDao.upsertAllIndexed`** (or `replaceAllForConversation` /
  `updateText` / `upsertReindexed`), never a raw `upsert`/`upsertAll` outside the DAO package.
- The index skips side rows whose `updatedAt` hasn't moved, valid only for server-carried values. **Local
  text edits must go through `updateText` or `upsertReindexed`**, which reindex unconditionally; any new
  local text mutation needs the same.
- Deletes cascade via the side table's FK (Room enables `foreign_keys`), firing the FTS delete trigger.
- **Never index unclaimed rows (`accountId IS NULL`)**; side rows copy the parent's stamped account,
  never the active one.
- **Never select content from `messages_fts`** (spans accounts), never `VACUUM` without `'rebuild'`,
  only add nullable columns to `message_search`, never `REPLACE` either table, never `'optimize'`.
  Entity KDocs say why.
- **Changing `searchTextOf` output means bumping `SearchIndexState.CURRENT_FORMAT`**, or old rows keep
  the old shape.
- `SearchIndexBackfillSessionTask` backfills then repairs each session, gated on `SearchIndexWindow`
  (foreground, idle, unconstrained; never on the prefetch opt-in or the network),
  yielding to a running local search. Typed input goes through `FtsQuery.plan` (quoted tokens only);
  CJK/Thai and regex use `MessageScanEngine`.
- Reset and `SearchIndexMaintenance.purgeAccountMessages`'s fast path run with sync triggers dropped in
  one transaction; `AccountDataPurger` and delete-all go through it.
- A prune of 1k+ messages merges the index afterwards, inside the prefetch window.
