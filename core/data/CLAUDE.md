# core:data

Room database, DataStore preferences, EncryptedSharedPreferences for tokens, and all repository implementations. This is the coordination layer between network and local storage.

## What This Module Provides

- **Room database** (`db/LibreChatDatabase.kt`): 6 entities, 6 DAOs, version 1, `exportSchema = true`.
- **Entities**: `ConversationEntity`, `MessageEntity`, `FileEntity`, `AgentEntity`, `PresetEntity`, `ConversationTagEntity`. Complex fields (lists, nested objects) stored as JSON strings via `Converters`.
- **DAOs**: `ConversationDao`, `MessageDao`, `FileDao`, `AgentDao`, `PresetDao`, `ConversationTagDao`. Read methods return `Flow<T>` for reactive observation.
- **DataStore**: `ServerDataStore` (server URL prefs), `SettingsDataStore` (user preferences).
- **Token storage** (`datastore/TokenDataStore.kt`): Implements `TokenManager` from `:core:network` using `EncryptedSharedPreferences` with AES-256. Uses `Mutex` to ensure only one refresh runs at a time when multiple 401s arrive concurrently.
- **Repositories** (`repository/`): Interface + Impl for each domain: `AuthRepository`, `ConversationRepository`, `MessageRepository`, `ChatRepository`, `FileRepository`, `AgentRepository`, `PresetRepository`, `PromptRepository`, `TagRepository`, `ShareRepository`, `ConfigRepository`, `UserRepository`, `SettingsRepository`.
- **Entity mappers** (`db/mapper/EntityMapper.kt`): Convert between Room entities and domain models.
- **Sync manager** (`sync/ConversationSyncManager.kt`): Orchestrates cache invalidation.

## Key Patterns

### Repository: Interface + Impl

```kotlin
interface ConversationRepository {
    fun observeConversations(...): Flow<Result<List<Conversation>>>
    suspend fun updateTitle(id: String, title: String): Result<Conversation>
}
```

All impls take constructor parameters (api, dao, mapper, dispatcher) wired via Koin named qualifiers.

### Read-Through Cache

1. Emit cached data from Room immediately (first page).
2. Fetch fresh data from network via API service.
3. Upsert into Room. The Room `Flow` auto-emits the updated list.
4. On network error, the cached data remains visible; error is surfaced separately.

### Account-keyed token store (`CommonTokenDataStore`)

Tokens are namespaced by account (`acct:<accountId>:access_token`) and several accounts' tokens are
retained at rest at once (multi-account, issue #179). Concurrency uses four cooperating pieces, not a
single refresh mutex:

- **`stateMutex`** — guards the in-memory identity + cached bearer and short storage reads/writes of
  it. Held only for brief critical sections, **never across the refresh network POST**, so a switch or
  logout never stalls behind a slow refresh.
- **per-account `flightMutex`/`flights`** — single-flights refreshes of the *same* account across their
  POST, so a second 401 reads the rotated token instead of re-POSTing a spent one.
- **`tokenEpoch`** — bumped by every op that invalidates token truth (logout, clear, removal, a new
  authentication, an identity re-home). A refresh captures it before its POST and discards its result
  if it changed, so a refresh racing a teardown can't resurrect a cleared session even with no lock
  held across the POST.
- **`rejectedRefreshes`** — the refresh token a *proactive* renewal last had rejected, per slot, keyed
  on the token's own bytes. **Read and written inside the flight lock, and that placement is the whole
  point** (issue #376). `ensureFreshAccessToken`'s own `proactiveSuppressedUntil` cooldown is read
  before the lock and written after it, so a request fan-out passes its gate N times before the first
  failure has landed; the single-flight coalescing check then keys on the access token having
  *changed*, which is exactly what a failed refresh does not do. The result was one POST per request —
  a 7-wide fan-out sent 7, and 10 counted end to end through the real client. Recording inside the
  lock is what lets waiters 2..N of the **same** burst stand down. Proactive callers only: a reactive
  caller must keep spending its full ladder, because a settled ladder is what drops the slot and
  routes the user to re-auth, and reading a cached failure there would keep a dead session alive
  forever. Reactive waiters already coalesce for free — the first ladder's `invalidateRefresh` empties
  the slot and the rest return without a POST. Two orderings inside the lock are load-bearing: the
  **coalescing check runs before the suppression gate**, and **any successful commit clears the
  marker, on either path**. Both exist because a 2xx that took the server's reuse path does *not*
  rotate the refresh token, so a slot can hold a marker for the very bytes it still stores while its
  access token has already been renewed — checked the other way round, a caller would be told
  `Transient` and sent back out on a bearer already known to be stale.

**A refresh `403` is classified by provenance, not treated as one thing.** Every 403 arm of upstream's
`refreshController` is a dead credential (`jwt.verify` threw, `exp` is past, `?retry` found no
session), so `AuthRejectedTerminal` settles on the first answer instead of spending the ladder — its
401 is the ambiguous one the ladder exists for, and that ladder must stay. A 403 carrying none of those
bodies did not come from LibreChat: a proxy bouncer, a WAF or an IP ban answers 403 too, and it is no
evidence the session is dead, so `ForbiddenByIntermediary` keeps the slot. Reading one as a dead
session is how a temporary IP ban became a logout the user could not undo until the ban lapsed.
Unrecognised therefore means Transient, which fails safe in the direction that matters. The bodies are
registered in `scripts/mirrors.json` (`refresh-403-rejection-bodies`) because upstream rewording one is
silent here — nothing fails to decode, the client just stops recognising a dead session.

**Keeping the slot on that 403 means the reactive path has to be damped explicitly**, and this is the
subtle half. A terminal rejection used to bound the reactive burst by accident: it dropped the slot, so
waiters 2..N found nothing to send and returned without POSTing. Keep the slot and that free coalescing
is gone — every 401 in a fan-out spends its own POST, on every fan-out, for as long as the block lasts
(measured: 8 POSTs for a 7-wide fan-out, repeating indefinitely). So `ForbiddenByIntermediary` records
an **endpoint-wide** stand-down in the same `rejectedRefreshes` map, with a null token meaning "no
credential will get through, so everyone waits" — the one case a *reactive* caller is damped, and it is
sound precisely because that outcome is not terminal: the session is being kept, not dropped. A
proactive outcome must never narrow that record back into a token-keyed one, or the waiters behind it
un-damp. Deliberately not extended to the 5xx ladder, which has the same N-callers-times-3-attempts
shape but predates this and where retrying is legitimate.

Interactive sign-in (`setTokens`) **stages** the pair under the bare keys and drops the active binding;
`onAccountResolved` re-homes it into the account's keyed slot. This makes a re-login while another
account is active unable to corrupt/leak into that account's slot.

Wrap `EncryptedSharedPreferences` access in try/catch -- some OEM devices have broken Keystore
implementations. `TokenDataStore` recovers in place rather than crashing: a single undecryptable entry
is dropped (other retained accounts stay logged in), a broken keyset is wiped and rebuilt with a fresh
master key, and if even a rebuild can't produce a working store it degrades to an in-memory session for
the process. Construction never throws into `startKoin`. The next request then 401s and routes to
re-login through the normal expired-session flow.

**The store is built lazily and the cache is warmed off the main thread** (#326 follow-up). `MasterKey.Builder().build()`
plus `EncryptedSharedPreferences.create` cost 300–900 ms on an emulator's software Keymaster, and the
eager `AccountRegistry` single used to pay that on the thread `startKoin` runs on. So the encrypted
store is created on first use behind a monitor, and `TokenCacheWarmer` (an eager single) calls
`warmTokenCache()` on the IO dispatcher. Its registration position relative to `AccountRegistry` only
sequences construction, not the launches, so it is not an invariant.

Three rules keep that safe, each with a test in `CommonTokenDataStoreWarmTest`:

- **The warmer performs the load; it never awaits one.** A logged-out cold start touches no other entry
  point on the store — `AccountRegistry` takes its `activeEntry == null` branch and never calls
  `selectAccount` — so a warm that waited for someone else's seed would never complete.
- **`warmTokenCache()` takes `stateMutex`, and is the only writer of the cache fields outside a
  mutation.** Every mutator holds that lock across its whole critical section, so a warm either
  completes before one starts or begins after it finished — and in the latter case it reads back what
  the mutation just wrote through to storage, since memory always mirrors disk. Note `tokenInitialized`
  is set *only* by the load, so a post-mutation warm still runs; it is the mutex, not that flag, that
  makes it harmless.
- **`ensureTokenLoaded()` is the synchronous fallback, and it reads through without publishing.**
  Populating the cache fields from there would be an unlocked read-read-write-write against the same
  fields a mutator writes under `stateMutex`: a caller that began reading before `setTokens` wrote and
  assigned after would replace the fresh bearer with the pre-login disk value *and* pin it by marking
  the cache initialized — every later request on a stale bearer until process death. Reading through
  costs a repeated decrypt in the startup window instead. Losing the race therefore costs a duplicated
  read, never a wrong bearer, which is what licenses the design and what keeps `isAuthenticated` on
  `TokenManager`.

Two consequences worth knowing. `emitSessionExpired` is not `suspend`, so it cannot warm the cache; it
suppresses a scoped emit only against a *seeded* identity, because an unseeded `activeAccountKey` is
null and would swallow the expiry signal. And the "a write before the warm must not land in
`memoryFallback`" trap has **no unit test** — Robolectric cannot build `EncryptedSharedPreferences`, so
every write there goes to memory and a test would pass for the wrong reason. It is a device-pass item.

### DataModule Bindings

`DataModule.kt` defines a Koin `module { }` that binds repository interfaces to their implementations via `singleOf(::Impl) bind Interface::class` and provides the Room database, DAOs, and DataStore instances.

## Room TypeConverters

`Converters.kt` handles JSON serialization for complex Room fields: `List<String>`, `MessageContentPart` lists, `Feedback`, `FileReference` lists, etc. Uses the same `Json` instance from the Koin graph.

## Rules

- Dependencies: `:core:network`, `:core:model`, `:core:common`, Room, DataStore, security-crypto, Ktor, kotlinx-serialization, Timber, Koin.
- Convention plugins: `librechat.mobile.library` + `librechat.mobile.koin` + `librechat.mobile.room` + `librechat.kotlin.serialization`.
- Repositories must use `safeApiCall` from `:core:common` for all network calls. It carries the IO
  dispatcher hop as well as the error mapping (#326), so an API invocation reached any other way runs
  on whatever dispatcher the caller happened to launch from — the UI thread, for anything started
  from `viewModelScope`. **The invariant: every `:core:network` API-service invocation sits inside
  `safeApiCall`, inside `onApiDispatcher` (the same hop without the mapping, for calls that classify
  their own failures), or inside a flow that ends in `.flowOn(dispatcher)`.** The one HTTP call in
  this module that isn't an API-service method — `CommonTokenDataStore`'s token-refresh POST — is
  always driven from inside a request that already took the hop.
- Entities are internal to this module -- feature modules work with domain models from `:core:model`.
- All DAO read methods that the UI observes should return `Flow`, not suspend functions.

### New Repositories (Round 2)
- `MemoryRepository` / `MemoryRepositoryImpl` — wraps `MemoriesApi` for memory CRUD + preferences
- `McpRepository` / `McpRepositoryImpl` — wraps `McpApi` for server CRUD, tools, connection status, reinitialize
- Both bound in `DataModule.kt` via `singleOf`
- Both use `safeApiCall` — no local caching (server is sole source of truth for memories and MCP)
- **Gotcha**: Memory delete/update use `key` as identifier (not a separate ID field)
- **Gotcha**: MCP server operations use `serverName` as identifier

### New Repositories (Round 3)
- `BannerRepository` / `BannerRepositoryImpl` — wraps `BannerApi` for fetching server banners
- `AgentRepository.getAgentsPaginated()` — server-side paginated agent fetch, maps response to `PaginatedAgents` domain model
- Both bound in `DataModule.kt` via `singleOf`, both use `safeApiCall`, no local caching

### Per-server gateway headers (`servers` table)

`ServerRepository` / `ServerRepositoryImpl` own the gateway headers of issue #287 and are the
`ServerHeadersProvider` the HTTP clients read them through.

- **Device-scoped, no `accountId`.** Like `artifact_shortcuts`, deliberately absent from
  `AccountDataPurger` and from the detekt tenancy rule's table list: the headers are what let a user
  log back *in*, so logout must not take them.
- **Natural primary key.** `server_id` is the derived server id, not a surrogate — Room resolves
  `@Upsert` by primary key, so a surrogate would make every save after the first match zero rows and
  vanish silently. The whole-row `@Upsert` is safe *only* while the table has one non-key column and
  one writer; **any added per-server column (cert pinning, detected version, a label) has to make the
  writes column-scoped first.**
- **`headersFor` is non-suspend** — it is called inside `SwitchGate`'s lock and in the request
  pipeline's `State` phase. Persistence stays behind a `@Volatile` map guarded by `awaitWarm()`.
- **One seed read, then patch-on-write.** The repository is the table's only writer, so it keeps the
  map current by patching its own writes under the same lock. Observing the table instead hands those
  writes back a moment later, and a Room emission whose query snapshot predates a save can arrive
  after it, reverting a credential the user just typed.
- **Resolved `createdAtStart`.** The warm gate blocks the first HTTP request, so the database open it
  forces should overlap cold start rather than be serialized after it.
- **No migration — correct only while the release timeline holds.** An interim version kept these in
  the preference store under `srv:<serverId>:custom_headers`. No release tag contains it (verify with
  `git tag --contains <PR1 merge>`), so there is nothing on any device to move and no drain code.
  **This is a fact about the calendar, not a property of the code**: cut a release from `develop`
  between PR1 and this change and every user who configures headers on that build loses them silently
  on upgrade — no error, just a server they can no longer reach. If that ever happens, a drain is
  required after all. Otherwise don't add one back "for safety" — it would reintroduce a second writer
  and with it the patch-on-write race above.
- **A read failure is not "no headers".** `headersForServer` returns null when the store could not be
  read, and both editors render that as a warning instead of an empty list — an empty editor reads as
  "your credential is gone", and saving from it would make that true. Rules that keep the contract
  honest, each with a test: a row that won't decode is tracked per-server rather than skipped, as is
  one whose pairs all sanitize away (both otherwise report as "none configured"); a successful write
  does not clear the failure flag (a write proves one row is writable, not that the others were ever
  read); and the read is re-attempted after a failure (it is otherwise attempted once, so one
  transient error disables headers until the process is killed).
- **The empty-write refusal lives in `setHeaders`, not in the editors.** An empty map is a delete, and
  this repository is the only thing that knows whether the value it is about to destroy was ever
  successfully read — so it refuses that one combination itself and reports
  `HeaderWriteFailure.UnverifiedDelete`. Both editors write through it. Enforcing it caller-side means
  a copy of the rule in each editor, either of which can omit it. A **non-empty** write always goes
  through: re-entering the credential is the recovery path.
- **Two read paths, and the request path never retries.** `headersFor` / `awaitWarm` serve the request
  pipeline from a snapshot seeded once at startup; `headersForServer` serves the editors and reads
  through to the table every call. **Don't merge them.** No single retry policy serves both: bounded,
  one transient failure disables headers for the rest of the process; unbounded, an unreadable
  database queues every request behind the same failing query. Split, there is no policy to tune —
  opening an editor is what heals a store that failed at startup, and that is the moment a user is
  waiting for the answer anyway.
- **A write outranks a later failed read** (`writtenHere`). This class is the table's only writer, so
  a value it wrote is not in doubt even when the table stops reading. Without it a store whose reads
  fail but whose writes land confirms a save and then reports it unreadable, and the user re-enters a
  secret every request is already carrying.
- **Plaintext, deliberately.** The realistic exfiltration path for the app-private database is an ADB
  backup, which `android:allowBackup="false"` already closes; on iOS the app container is sandboxed.
  Against that, encrypting it would add the wipe-and-rebuild failure mode `TokenDataStore` has on OEMs
  with broken Keystores — and losing this particular value doesn't degrade to a re-login prompt, it
  degrades to a server the user can no longer reach at all.
- **The table is named for the deployment, not for the headers**, because per-server state with
  nowhere else to live (cert pinning, detected backend version, a user-chosen label) belongs here. It
  carries only the headers today; see the natural-primary-key bullet before adding a column.
