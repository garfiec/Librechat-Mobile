# feature:chat

## State Architecture (ChatUiState slices + narrowed handles)
`ChatUiState` is decomposed into **18 `@Immutable` sub-state slices**, plus two top-level fields: `error`
(a shared transient-banner channel) and `mediaPreview`. The slices: `conversation`, `content`
(message tree + all streaming fields),
`editing`, `composer`, `selection` (endpoint/model/tools/params), `queue`, `steer`, `search`,
`presetPrompts`, `voice`, `favorites`, `subagents`, `comparisonState`, `gates`, `account`,
`actions`, `prefs`, `codeWorkspace`.

- **Compat accessors.** `ChatUiState` keeps flat `val field get() = slice.field` accessors for
  every former field, so the ~250 UI read sites and test assertions read `uiState.isStreaming`
  etc. unchanged. UI still reads flat; only writes changed.
- **Narrowed write handles.** Delegates never receive the root `ChatStateHandle`. Each gets a
  per-delegate handle from `DelegateHandles.kt` (e.g. `StreamingHandle`, `QueueHandle`) whose
  `update { … }` block exposes only the slices that delegate may mutate (compile-time enforced
  via a `*Writes` class). `error` is writable from every handle (shared channel); use
  `setError(msg)` or assign `error` inside an `update`. Reads stay global via `handle.state`.
  `ChatViewModel` alone holds the root `ChatStateHandle` for its orchestration transactions.
- **Atomic-transaction invariant.** The completion-flash finalize, begin-stream, reset-with-
  carryover, `applyComposer`, and `switchBranch` each write within a single slice (or a single
  writer block), so they remain **one** `StateFlow` emission — do not split them across multiple
  `update`/`copy` calls. Message-tree + streaming fields deliberately live in ONE slice
  (`MessagesState`) because all five couple them.
- Adding a field: put it on the owning slice, add a flat compat accessor on `ChatUiState`, and
  add the slice to the writer of whichever delegate(s) own it.
- **Chrome/hot collection split (`ChatScreen`).** The screen collects `uiState` twice:
  the thread subtree (`ChatContent`) at full rate, the rest — top
  bar, composer, dialogs, sheets, effects ("chrome") — as
  `map { it.neutralizeStreamingChurn() }.distinctUntilChanged()`
  (`ChatChromeEquivalence.kt`), so the 50ms flush doesn't re-execute the whole screen ~20×/s.
  The neutralized fields are always empty on the chrome's copy — chrome must not read them; if it
  needs one, drop it from `neutralizeStreamingChurn`. New high-frequency streaming fields go in
  both `neutralizeStreamingChurn` and `ChatChromeEquivalenceTest`. Known exclusion:
  `subagentProgress` is chrome-visible by design (ChatRoot → `LocalSubagentProgress`) and written
  per SSE envelope unthrottled, so subagent-heavy runs still re-execute the chrome — needs a
  separate collection path or a write-side throttle in `SubagentTraceDelegate` (open follow-up).

## One screen, five platform seams
`ChatScreen` / `NewChatScreen` are a single commonMain composable for both platforms. Where the
platforms genuinely differ, the screen calls an `expect`, never an `if (platform)`:
- `ChatComposer` — `ChatInput` on Android, `IosChatInput` on iOS. iOS's clipboard-image paste lives
  inside its actual (`onPasteFiles` is ignored on Android).
- `rememberChatAttachmentActions` — activity-result launchers vs the native iOS pickers.
- `rememberChatStartRecording` — Android routes API 26–30 through the system recognizer overlay.
- `Modifier.addToChatSelectionItem` — a no-op on iOS.
- `McpAppWebView` / `rememberMcpAppViewPool` — the MCP Apps' pooled web views and their isolated
  bridge (see "MCP Apps").

Known iOS gaps behind those seams: the document picker ignores the endpoint's MIME allowlist, and
there is no "Add to chat" selection item. A feature added to the screen reaches both platforms; a
feature added to only one composer does not.

## Screen States
`ChatScreenState` enum: `LANDING` | `LOADING` | `ACTIVE`
- **Landing**: no conversation selected, shows greeting + model icon + optional conversation starters
  - Starters (`ChatUiState.landingStarters`, `util/ConversationStarters.kt`): the agent's own, else the
    selected spec's, at most 4. The agent list omits them, so they come from the same
    `GET /api/agents/:id` read that resolves the agent's provider; `null` means not resolved yet and
    shows nothing rather than the spec's for a round trip. A tap sends through the composer
    (`sendStarter`), a hold fills it unsent (`editStarter`); hidden while the composer has text.
  - They render as `LandingStarters` in `ChatScreen`'s measured bottom box, docked on the composer, not
    inside `LandingContent`: that way they ride the keyboard and never sit under the composer. The hero
    centres in the space they leave (`bottomPadding`) and drops its icon when that is too short.
  - The hero draws what web's landing `ConvoIcon` draws (`landingHeroIcon`): the selected spec's icon
    (see "Spec presentation", down to its endpoint's brand), the agent's avatar (the agents glyph
    without one), else the selected endpoint's brand; the app tile only before anything is selected
    (agents with no agent yet). A temporary chat shows the
    temporary toggle's `VisibilityOff` in `ChatInputDefaults.temporaryAccent` instead, whatever is
    selected, swapped as temporary chat's band crosses the heading (`landingHero`; web swaps in
    `HatGlasses`).
  - Compact mode (`maxHeight - bottomPadding` under `CompactHeroBelow`, plus the description's
    measured height at the hero's width, up to its four-line limit, and its gap when there is one)
    drops the icon, shrinks the greeting and cuts the description to two lines, so a spec's landing
    with the keyboard up and starters docked doesn't scroll. Measuring the description (rather than
    reserving its line limit) keeps a one-line description from tipping the hero into compact.
  - The words are `landingText` (web's `Landing.tsx`): the selected agent's name, else a
    `showOnLanding` spec's label, replaces the time-of-day greeting; the agent's description, else
    that spec's, else the spec preset's `greeting` (applied without `showOnLanding`) replaces "How can
    I help", as plain text held to 4 lines. A temporary chat shows its own title and subtitle
    instead: `landingText` returns both sets whatever the flag, and `LandingContent` switches each
    line as temporary chat's band reaches it (`components/veil/ModeVeil.kt`).
- **Loading**: full-screen spinner — *nothing is cached* and the message fetch has not settled
- **Active**: message list + streaming content + input area

### Cache-first open (#300)
`loadConversation(conversationId, cacheFirst)` picks the ordering between the Room read-through
and the server fetch. `cacheFirst = true` subscribes the observer first and revalidates in a child
coroutine, so an **open** paints the cached copy immediately instead of holding the spinner
through a GET that is retried twice with exponential backoff before it can fail. `ChatViewModel.init`
is the only opt-in; every other caller stays network-first **deliberately**. Each of them reloads
precisely because the server holds something the cache does not — a just-finalized turn
(`SendCompletionDelegate`), a just-created branch (`ComparisonModeDelegate.branch`, which already
shows the branch from memory and reloads to bring the cache up to date),
a stream that ended server-side (`StreamingManagerDelegate`'s `StreamError` / `ResumeExpired` /
`Reconcile` / `attemptNetworkRecovery`), or an explicit refresh
(`refreshMessages`) — so a cache emission there serves a snapshot that predates the very thing
being fetched. After a Final that is also the completion flash: the finalized turn is in memory
and Room stays stale until `cacheMessages` lands, so painting the cache re-renders the pre-Final tree.

Two consequences worth knowing before touching that block:
- The fetch's settle is a **`combine` input**, not a flag read inside `collect`. A conversation
  with genuinely zero messages upserts nothing, so Room never emits again — without the
  re-emission the screen spins forever. The comparison auto-rehydrate latch is gated on the same
  input so it still burns on the *authoritative* tail, not a stale cached one.
- `isRefreshingMessages` now means "a messages fetch is in flight" (pull-to-refresh **or** a
  background revalidate) and drives `MessageList`'s pull-to-refresh indicator, which animates off
  that boolean with no gesture. `refreshMessages` early-returns while it is set, which is what
  keeps the two paths from racing each other's clear — and which means a pull issued during the
  open's revalidate is dropped rather than queued, since a fetch is already running.

## Navigation
- Sealed interface: `ChatRoute : NavKey` with typed route classes
- Routes: `NewChat` (landing), `Chat(conversationId: String? = null)`, `PromptsLibrary`, `PromptEditor(groupId: String? = null)` (all `@Serializable`)
- Feature entries registered via `EntryProviderScope<NavKey>.chatEntries()`
- When a new conversation starts from the landing page, `pendingNavigationConversationId` triggers navigation to `Chat(id)` at `StreamEvent.Created`, keeping `NewChat` in the back stack. The landing VM is reset (`onPendingNavigationHandled`) and the new Chat(id) VM resumes the active stream — so the Chat(id) VM (with `isNewConversation = false`) is the one that handles the first stream's Final. New-chat-only work there (title generation) is gated on `isHandedOffNewChat`, derived from consuming `NewChatSelectionHandoff`
- `navigateToChat()` replaces the current chat entry on the `NavBackStack` when switching between chats so back returns to `NewChat`

## New-chat model seeding
`ModelSelectionDelegate.seedInitialSelection` is the only thing that picks a new chat's model. In order:
1. Tier 0: an explicit agent or model from the start-chat route (one-shot).
2. Tier 0b: the server's **prioritized** model spec.
3. Tier 1: last-used.
4. Tier 1b: a **soft-default** spec.
5. Tier 2: the first agent.
6. Tier 3: the first config model.

Which spec applies, and whether it is prioritized, is `util/SpecSeed.kt`, a port of web's
`getDefaultModelSpec` plus `useNewConvo`'s apply gate (registered in `scripts/mirrors.json`).
- **Prioritized** (`modelSpecs.prioritize`, which the server's schema defaults to true, or
  `interface.modelSelect` off): every new chat opens on the spec, as on web. It is a standing tier,
  not a one-shot: it beats last-used on every seeder emission until a pick on this landing or a
  Tier-0 override yields it. `ChatViewModel.onPendingNavigationHandled` re-arms it, so the next new
  chat is back on the spec. A spec agent the loaded list doesn't contain (deleted, not shared, no
  agents at all, agents denied) falls through to the ordinary tiers.
- **A `default: true` spec is prioritized whatever `prioritize` says** (`withUngatedDefault` in
  `util/NewChatStart.kt`). `useNewConvo`'s gate checks `prioritize`, but web's other two paths don't:
  the first load of `/c/new` (`ChatRoute.tsx`, `getNewConvoPreset`) and the `defaultPreset` every
  later "New chat" starts from (`useAppStartup.ts`). Checked on a v0.8.9-rc1 rig with
  `prioritize: false` and `modelSelect: true`: web opened every new chat on the default spec, after a
  raw pick and after opening another spec's chat too. `SpecSeed.kt` stays the port of the gate. An
  unset `prioritize` is served as `true` and, with specs and no `addedEndpoints`, an unset
  `modelSelect` as `false`, so this only matters when an admin sets both.
- **Last spec**: a spec the user picked is stored with the last-used pair (`LastUsedSelection.spec` in `:core:data`'s
  `SettingsDataStore`; a raw pick clears it). Unless a spec is marked `default`, new chats reopen on it, under `prioritize` too
  and ahead of a soft default, as web reopens on `LAST_CONVO_SETUP.spec`. On restore it reopens on the
  spec's current endpoint and model (`reopenedPick`), as the resolver does, so a spec an admin moved
  to another model still opens, in both "Start new chats on" modes; only a spec the server no longer
  lists falls back to the stored raw pair. Known divergence: web
  also rewrites that setup when a conversation is opened, so opening a spec-B chat makes B the next
  new chat's spec; mobile writes last-used only on a pick.
- **Soft default**: applies only when last-used can't, so a user's own selection wins.

- **Start new chats on** (Settings › Chat, `AppPreferences.PreferLastUsedModel`, mobile-only,
  #552): "Server's choice" (the default, web parity) or "My last-used model". The latter demotes a
  prioritized spec to a soft default (`util/NewChatStart.kt`, `demotedForLastUsed`), so last-used
  wins and the spec applies only when last-used can't. It still yields to the server's locks:
  `modelSelect: false`, and `enforce` with a raw last-used pair (a last-used spec still counts).
  Keep the override there, never in `SpecSeed.kt`, which mirrors upstream. Switching it while the
  landing is open re-seeds the landing.
- **Under `enforce`** (both modes): the server refuses a raw pair, `addedEndpoints` or not, so a raw
  last-used never seeds (`seedableLastUsed`) and a raw model the seeder opened before the config arrived
  gives way; a raw pick on the landing stays, for the send preflight to ask. When the spec tiers open
  no spec (no `default` or soft spec and no `prioritize`, a spec agent the user can't reach, a last
  spec the server dropped), Tier 1b goes on to the first spec that can: the `default` one, then list
  order (`fallbackSpecSeeds`). Only when none can do the raw tiers run. Web opens the raw model in
  these cases and the server refuses the send.

| Server config | "Server's choice": new chats open on | "My last-used model": new chats open on | Role of last-used |
|---|---|---|---|
| No `modelSpecs` | Last-used, then first agent, then first model | Same | Decides new chats |
| `prioritize: true` (or field omitted) | The `default` spec, or the first spec if none is marked `default` or `softDefault` (a `softDefault` stays soft: row 4) | Last-used if usable, otherwise that spec | Default: saved, used only when the spec can't apply. Setting: wins |
| …and the user's last pick was a spec | That spec, unless a spec is marked `default` (web: `LAST_CONVO_SETUP.spec`) | That spec | Last spec reopens |
| `prioritize: false` + `default` spec | That spec, ahead of last-used and a last spec (web: `ChatRoute`, `defaultPreset`) | Last-used if usable, otherwise that spec | Default: saved, used only when the spec can't apply. Setting: wins |
| `prioritize: false` + `softDefault` spec, no `default` | Last-used if usable, otherwise the soft spec | Same | Wins over the soft spec |
| `prioritize: false`, no `default` or soft default | A last spec, else as no specs | Same | Same as no specs |
| `interface.modelSelect: false` | The `default`/soft/first spec, always (a stored last spec still reopens when none is marked `default`); an agent card or a spec's shortcut still wins, as a start-chat link does on web | Same: the setting is ignored | Used only when no spec applies |
| `enforce: true` | As above, then any spec that can open (`default` first) before a raw model; a raw pick can't be sent | Last-used only if it's a spec; otherwise as "Server's choice" | Raw last-used ignored |

In both modes: a spec that can't apply (missing agent, no agent access, a model the endpoint doesn't
offer, config not loaded yet) falls back to the normal order; existing conversations reopen on
their own model. On the new-chat screen:
- Anything the seeder didn't write to the selection (a pick, a loaded preset, the composer swap, a
  start-chat link) is the user's: the seeder notices it by comparing against the choice it last left
  (`seederChoice`), so no writer has to report itself. It displaces the spec and, while its models
  load, isn't replaced by last-used. It lasts until that chat starts.
- After the handoff, `rearmModelSpecDefault` adopts the carried-over selection as the seeder's and
  seeds over it (`reseedCarried`), so a start-chat link lasts one chat: the next new chat goes back
  through the tiers.
- A raw last-used on an endpoint `addedEndpoints` no longer lists is unusable: it neither seeds nor
  beats a spec.
- Once the model list has loaded (it arrives whole), an endpoint missing from it or listing no
  models is unusable rather than pending, so nothing waits on it forever.

**`interface.modelSelect: false` hides the picker, as on web.** Mind the server's default: with
`modelSpecs` configured and `modelSelect` left out, upstream sets it to whether `addedEndpoints` is
non-empty (`data-schemas/src/app/interface.ts`), so the common spec-only config arrives as `false`.
Then:
- The picker lists only the specs (`showsRawEndpoint` takes `modelSelect`, as web's `useEndpoints`
  returns no endpoints), and Compare's quick picks too.
- With no specs either there is no picker (`ChatUiState.modelPickerShown`, web's `ModelSelector`
  returning null): the model chip and the new-chat label are plain text, the tools-sheet row and
  Compare are hidden, and the send preflight shows its reason without opening a sheet.
- A start-chat link (agent card, model shortcut, iOS quick action) wins over the spec, as on web:
  `useNewConvo` applies the spec only `if (!preset && …)`, and every link entry point
  (`AgentDetail`, `useQueryParams`) passes a preset. An agent card opens its agent. A model shortcut
  follows the rules under "Model shortcuts" below, which keep it off raw models the picker hides.

**The selection is one value, `ModelChoice`: `Raw(endpoint, model)` or `Spec(name, endpoint,
model)`.** Write it whole, through `ModelSelectionDelegate.applySelection`; the preset load, the
queued-edit composer swap and the seeder's spec-config update write it in the same emission as
another field instead. Build a `Spec` from the server's config (`SpecSeed.toChoice`) or, for a
stored value, `ModelChoice.restore`. A conversation or preset loaded before the spec config keeps
its spec unchecked until the seeder holds it against the config, so the type does not prove a
`Spec` is still offered on its pair. The chat selection, queued turns, composer snapshots and the
new-chat handoff hold a `ModelChoice`; don't give them a separate spec field again. Compare's staged
picks (`ComparisonState`'s second lane, `ComparisonPick`, `CompareSetupPick`) and `LastUsedSelection`
(`:core:data`) still keep separate fields and convert at the boundary; the paused-run pin
(`ResumeTurnPin`) records what was sent and stays as it is.

**The spec name travels with the selection and is sent as `spec`.** Named specs are separate picker
rows even when they share a model, and a raw pick clears the name. Nothing infers a spec from a
model match, with one exception: under `enforce`, a Tier-0 entry point (an agent card, a model
shortcut) adopts the spec when exactly one opens on its pair (`uniqueSpecFor`), since the server
would refuse the raw pair anyway. With none or several, the send preflight asks. Queue and
composer snapshots, the new-chat handoff, the conversation cache (`spec` column, Room 10→11), comparison, presets, edit/regenerate and paused-run pins all carry it.
Comparison carries it on send only: after a reopen, lane B's spec is read back on a best-effort basis
(see "Lane B's spec on reopen" under "Compare Models"). Sending
it changes what the server does even without `enforce`: it fills the spec's unset private preset
fields (`promptPrefix`, `instructions`, `system`, …) into the request (`mergeModelSpecPreset`), so a
chat opened on a spec gets the admin's prompt as on web.

**A spec's preset becomes the chat's parameters** (`util/SpecParameters.kt`), as web copies
`{...spec.preset, spec}` into the conversation. Without `enforce` the server applies none of the
preset's public fields (temperature, `reasoning_effort`, `max_tokens`, `maxContextTokens`, …), so the
client has to send them. **Only the keys of the selection's parameter set go out, and the server unsets
every stored field a request leaves out** (`getUnsetFields`), so a key the client drops or misspells is
also erased from what web set. The set is picked by `ChatUiState.parameterTarget`: a custom endpoint's
`customParams.defaultParamsEndpoint`, else its `type` (`paramSettingsKey`), never its own name, and an
agent's provider resolved the same way; the sheet, the payload and `savePreset` all read it. Top P and max
tokens are spelled per provider (`top_p` / `max_tokens` on openAI, azure and custom, `topP` on the native
providers, `maxOutputTokens` on Anthropic and Google, `maxTokens` on Bedrock): `Preset` and `Conversation`
keep one field per spelling, `mergedFromPreset` coalesces them, and `savePreset` writes each under the key
the set uses (`EndpointParameterRegistry.wireKeyFor`). Only a change of spec touches the parameters (`acrossSpecChange`, from
`applySelection` and the seeder's spec-config write, `withSpecsRefreshed`), never a re-emission of the
same one, so a tweak on top of a spec survives config refreshes and is sent:
- Onto a spec (a pick, a seeded tier, a retargeted spec re-applied): that spec's preset over the defaults.
- Off a spec onto a raw model the user picked: the defaults.
- A config that drops the chat's spec (see the stored-spec table above; on the landing, the seeder's
  own spec once it is not served on its pair) drops the name and keeps the parameters: the endpoint
  and model are unchanged. The next move to another pair counts as leaving that spec and resets to
  the defaults (`ModelSelectionDelegate.applySelection`, `DroppedSpecMemo`), unless the user tweaked a
  parameter in between. So a landing whose removed spec's model is gone too opens its fallback model
  on the defaults, not on the spec's preset. On the landing a retargeted prioritized spec re-applies
  through the tiers with its new preset.
- The user's web search and URL context carry across either way, within the chat (a new chat
  resets both). Under a spec the toolbar shows the spec's tools instead (see "Chat Tools"), so
  these wait for the user to leave it.
- A user preset replaces the spec's values. Only a preset naming a spec and nothing else layers its
  values over that spec's preset. A saved preset (endpoint, model and spec) builds on the defaults:
  `savePreset` leaves out values equal to the defaults, so a value the user reset would otherwise
  come back as the spec's.
- A conversation's own spec never applies the preset (`applySelection(specPreset = false)`). A
  reopened chat restores the parameters it stored: the conversation load maps them back through the
  same `mergedFromPreset`. The list route sends none, so list upserts keep the `modelParams` blob a
  full read cached, stamped with the `updatedAt` it was read at (`parametersAsOf`). A row with no
  blob, or whose `updatedAt` has since moved (the chat changed elsewhere), is fetched once per process
  and version, with the `enforce` spec backfill. The cache applies at once and the fetch replaces it
  only if the user changed nothing meanwhile; offline the cache stays. A config refresh meanwhile is
  not the user's change: `SelectionWatches` follows the seeder's rewrite, so a
  spec drop while the fetch is out still lets the fetched parameters apply.
  A handed-off new chat takes the landing's parameters from `NewChatSelectionHandoff` instead.
- The next new chat starts from the carried spec's preset (`onPendingNavigationHandled`).
- Under `enforce` the server replaces the body with the preset, so while a spec is active the sheet
  is read-only (`parametersSetBySpec`) and `updateModelParameters` ignores it. The parameters are
  that preset there even on the same spec, on a reopened chat (`reopenedParameters`) and after a
  loaded preset (`loadPreset`, parameter-only or naming a spec), so the locked sheet shows what the
  server runs, not what the chat stored before `enforce` was on. A loaded preset's tool flags still
  apply: the server reads `ephemeralAgent` from the body even under `enforce`.
- The spec's `artifacts` go out as `ephemeralAgent.artifacts` (`true` → `"default"`), the only place
  the server reads them. The context gauge takes the spec's `preset.maxContextTokens` after the
  agent's and before token-config, as web's `useTokenLimits` does, and looks token-config up under
  web's catalog alias (`modelCatalogKey`: an API-made agent on `vertexai` reads `google`'s window).

**A spec's pair comes from `preset.endpoint` alone** (`toSeed`, as upstream's
`resolveModelSpecEndpoint`): an agents spec runs its `agent_id`, any other its `model`. The server
writes an endpoint it infers from `agent_id` into the served config (`materializeModelSpecEndpoints`),
so the client infers nothing, and a stray `agent_id` on another endpoint is ignored, as the server
ignores it. A spec served with no endpoint (an explicit `endpoint: null`) opens nothing.

**Model shortcuts** (launcher shortcuts, iOS quick actions; mobile-only). Usage is counted per pair
and spec (`SettingsDataStore.incrementModelUsage`): a spec's sends count apart from the raw model and
keep its label for the shortcut's title, and the spec rides the link
(`librechat://model?…&spec=`, `NewChat.spec`). Before publishing, the ranked list is reconciled
with the cached spec config (`ModelRef.reconciledWith`, Android `MainActivity` and iOS
`IosKoinAccessor`): a spec the server no longer lists drops out and a kept one takes its current
label; with no config yet the list is left as is. At Tier 0 a shortcut reopens its spec on the spec's
current pair (`reopenedPick`). One with no spec (a raw pick, or recorded before shortcuts kept
specs) opens its raw pair only where the picker lists that endpoint (`showsRawEndpoint` with the
live `modelSelect`); elsewhere the one spec on the pair opens, or nothing and the landing seeds as
usual. Under `enforce` it takes the one spec on its pair, as before, and otherwise opens the raw pair
for the send preflight to ask. A shortcut applied before the config loaded is resolved once it
arrives, the same way.

**`enforce` has no exception for `addedEndpoints`.** With `modelSpecs.enforce` and a non-empty list
the server rejects any send without a `spec` (`buildEndpointOption.js`). So `requiresSpec` blocks
such a send in the preflight, and the picker greys the raw rows it still lists (those in
`addedEndpoints`, as web lists them) under a note. With no `addedEndpoints` it lists no raw rows at
all, where web lists everything and every pick fails. A cached conversation with no spec under
`enforce` is refreshed from the server once per account and process, read against the fetched
config rather than the selection (unseeded when the conversation loads); a failed refresh is
retried on the next open.

Compare's second model is held to the same rule, though the server checks only the primary's
`spec` and takes a raw `addedConvo` under `enforce`: its picker and quick picks list no raw rows
there, and the preflight refuses a comparison whose second lane has no spec with
`SendBlockReason.SelectSecondaryModel`, which opens the selector on lane B. That case arises only
from a reopened comparison whose spec can't be read back (see "Compare Models", Reopen).

Under `enforce` a follow-up queued during an agents run stays on the local drain instead of the
server's queue (`QueueOrchestrationDelegate.serverOwnedSpec`). The server's queued turn carries no
spec (its enqueue schema has no such field, and the worker re-posts the turn to the chat route
without one), so `buildEndpointOption` refuses it, even when the composer held a spec. Web's
enqueue payload has no `spec` either.

**A send to a user-key endpoint with no key is refused before it goes out**
(`SendBlockReason.ApiKeyRequired`), as web's `useRequiresKey` disables the composer, instead of
failing on the server with `no_user_key`. It reads `endpointKeyStates` for the selected endpoint and,
like the picker, lets the send through while the key state is loading or unknown. The picker shows
the Set API Key chip on that endpoint; with no picker (`modelSelect` off and no specs) the reason is
text only.

**A stored spec is held as the server holds it** (`ModelChoice.stored` / `storedAgainst` in
`util/SpecSeed.kt`, a port of `resolveModelSpecForEndpoint` as `buildEndpointOption.js` applies it).
The server resolves a stored spec against its full list, hidden specs included, and checks the
endpoint only. Every cell below was reproduced on a v0.8.9-rc1 rig:

| Stored spec | No `enforce` | `enforce` | Mobile |
|---|---|---|---|
| Not in the served list, hidden | applies it: private prompt and tools | applies it | kept |
| Not in the served list, deleted | ignores it, runs raw | "Invalid model spec" | kept (can't be told from hidden) |
| Served, preset on another endpoint | "Model spec mismatch" | "Model spec mismatch" | dropped to raw |
| Served, same endpoint, another model | applies it on the stored model | the preset replaces the body | kept on the stored model; under `enforce`, moved to the spec's pair |

It applies at every writer of a stored selection: a conversation load (`applyResolvedConversationModel`,
from the load, the send completion and the new-chat handoff), a config change on an open chat
(`withSpecsRefreshed`), a preset load (which adopts the spec's own pair when the preset names none),
a queued turn restored to the composer or drained (`applyComposer`, `doSendWithSpec`), and Compare's
picked lane B (`laneBSpec`). The send preflight accepts any kept name and lets the server decide.
The three spec refusals ("No model spec selected", "Invalid model spec", "Model spec mismatch",
`isSpecRefusal`) open the picker with "Select a model" next to the server's sentence.
- The seeder's own landing pick is the exception (`seededAgainst`): it holds only specs the server
  serves on that pair, so one it no longer serves there re-seeds through the tiers (a retargeted
  prioritized spec re-applies with its new preset) instead of being sent.
- A stored *pick* (the last-used restore and a model shortcut) follows the spec to its current pair
  (`reopenedPick`).
- The header chip shows a kept spec the list doesn't serve by its model name. Web shows its
  "Select a model" placeholder there (checked on the rig) and still sends the spec. There is no picker
  row for it, as on web.

**Spec rows** (`visibleSpecs`) sort by `order`, ascending with unordered specs after in list order
(librechatios parity; web ignores `order`), and leave out an agent spec once the agents list has
loaded without its agent (web parity; until then, and after a failed fetch, every spec shows).
The list is one page of up to 100 agents where web loads them all, so a full page hides nothing.
Compare setup stages specs like models, so a spec can be either lane before the comparison starts.

**Spec presentation** (#641):
- **Groups** (`util/SpecGroups.kt`, web's `ModelSelector` / `EndpointItem` / `CustomGroup`): a spec
  with no `group` sits at the top; one whose `group` is exactly a shown endpoint's key (`agents` is
  My Agents) heads that endpoint's section; any other group is a headed, collapsible section after
  the endpoints, iconed by its first `groupIcon` (a folder when none). Under `modelSelect: false`
  every group is its own section, as on web. Sections start closed except the one holding the
  selected spec. A search lists specs flat, without groups, as web's does; Starred is unchanged.
  Divergence: a spec grouped under an endpoint with no models gets a group of its own (web hides it).
  Such a group named after a configured endpoint key or `agents` (also every endpoint-keyed group
  under `enforce` without `addedEndpoints`, where no raw endpoint is listed) takes that endpoint's
  label (`endpointDisplayName`, "My Agents") and icon but stays its own section; under
  `modelSelect: false` it keeps the raw name, as on web.
- **Row order** (`util/PickerRows.kt`): `pickerRows(PickerInputs)` lays out the whole picker as
  data (header / spec / model / agent / note / divider rows with their keys); the sheet only resolves
  strings, icons and clicks, so order and keys are tested in commonTest (`PickerRowsTest`).
  - Under `enforce` with greyed raw rows, one note ("Only the server's model specs can be sent…") is
    the first row, above Starred, so no sendable spec sits above or below a misleading note. The
    note is mobile-only; web explains nothing.
  - An endpoint that needs the user's key keeps its grouped specs listed under the greyed "Add key"
    header (a tap opens the key screen) and hides its raw models, as web's `EndpointItem` lists them.
- **Icons** (`isImageUrl`, `iconValue` and `resolveIconSource` in `:core:model` `media/IconUrls.kt`,
  `util/SpecIcons.kt`): a spec's `iconURL`, its preset's, its agent's avatar, then its endpoint; a
  value is an image by web's `isImageURL` rule, else an endpoint or provider whose glyph to borrow,
  and `""` is unset. The picker rows, landing hero, Compare's spec lane and quick picks use it, and
  so do reply avatars (see "Reply avatars" below);
  `showIconInMenu: false` hides it in the picker and quick picks. The conversation rows apply web's
  `EndpointIcon` rule (`Conversation.iconEndpoint(configs)` / `iconProvider(configs)`: a non-image
  `iconURL` counts only when it is an endpoint key), joining a relative icon path to the server
  (`ConfigRepository.serverUrl`). `showIconInHeader` is decoded only: the header
  chip is text-only by design.
- **Brand art** follows web's `resolveProviderIcon` (`:core:model` `media/ProviderIcons.kt`): an
  image icon, then an icon value naming a provider (`groq`, `grok`, `Together.AI`), then the
  endpoint's served `providerId` (the server derives it from the endpoint's `iconURL`, `baseURL`
  host, `provider` and name), then the first-class brand, then the endpoint name.
  `resolveIconSource` and the `SpecIcons` helpers take optional `endpointConfigs` to fill
  `IconSource.provider`; pass them, and `EndpointIcon(provider = …)`, wherever configs are in reach,
  or a custom endpoint draws its monogram or the generic glyph.
- **Reply avatars are view data.** A bubble never resolves its own icon: `ChatViewModel`'s exposed
  `uiState` combine fills each node's `MessageNode.avatar` (`util/ReplyAvatars.kt`: the message's
  `iconURL`, else its endpoint's brand with configs), because the server URL and the endpoint configs
  arrive on their own flows and a path built before them would keep a stale avatar. `ReplyAvatarMemo`
  skips the walk while the path, URL and configs are unchanged, so streaming emissions cost nothing.
  `ChatUiState.avatars` (`ReplyAvatarsState`) holds the streaming reply's and Compare lane B's, and
  `buildComparisonDisplayMessages` puts lane B's on pane B's nodes. The bubbles, the streaming
  bubble and the steer segment header (`SegmentAuthorHeader`) draw the `IconSource` they are given.
  Shared links map their snapshot the same way without configs, so a custom endpoint resolves by
  name there.
  - **No third-party logo art is bundled.** Brand terms forbid most unlicensed logo use in an app,
    and F-Droid would flag bundled logos as NonFreeAssets. The art loads at runtime from the user's
    own server, which serves it as static files at `{server}/assets/<file>` (`providerAssetFiles` /
    `providerAssetUrl` in `ProviderIcons.kt`, subpath-aware; `LocalServerUrl` is provided by the nav
    host). A provider with no served file (Anthropic, Azure, Bedrock, xAI, Moonshot, Vercel) or a
    file an older server lacks draws a monogram tile of its label. Loaded art is never tinted;
    near-black art (OpenAI, MLX) sits on a light tile in a dark theme. Web's model-level refinement
    (`byModel`) isn't ported.

**Model spec fields** (v0.8.8, #668). Every field `GET /api/config` serves for a spec, and what this
client does with it. A field that is decoded but not honoured, or not decoded at all, is left out
on purpose; the reason is in the last column, so a sync doesn't "fix" it. The served list has
already been through `excludeHiddenModelSpecs` and `sanitizeModelSpecs` (which strips `skills`,
`subagents.agent_ids` / `shareFiles` and the private preset fields). Undecoded fields are also
listed in `ModelSpec`'s KDoc.

| `TModelSpec` field | Decoded | Honoured | Where | Why not / note |
|---|---|---|---|---|
| `name` | yes | yes | sent as `spec`; `ModelChoice.Spec` | |
| `label` | yes | yes | picker, header chip, lane B sender (`laneBSpecFor`) | Falls back to `name` (`ModelSpec.displayLabel`), as `normalizeModelSpecs` |
| `preset` | yes | yes | see the preset rows below | |
| `order` | yes | yes | `visibleSpecs` sort | Web ignores it (librechatios parity) |
| `default` | yes | yes | `SpecSeed` (`resolveDefaultModelSpec`), `withUngatedDefault` | Applies whatever `prioritize` says, as web's `ChatRoute` and `defaultPreset` |
| `softDefault` | yes | yes | `SpecSeed`, Tier 1b | |
| `description` | yes | yes | picker row, landing (`landingText`, with `showOnLanding`) | |
| `group` | yes | yes | `SpecGroups` | |
| `groupIcon` | yes | yes | `SpecGroups`, `ModelSelectorSheet` group header | Endpoint-named icon resolves to that endpoint's brand (deliberate divergence, see "Spec presentation") |
| `showIconInMenu` | yes | yes | `SpecIcons` (picker, quick picks) | |
| `showIconInHeader` | yes | no | | The header chip is text-only by design |
| `showOnLanding` | yes | yes | `landingText` | |
| `conversation_starters` | yes | yes | `resolveLandingStarters` | An agent's own starters win |
| `showInMenu` | yes | filtered | `visibleSpecs` | Never arrives `false` (`excludeHiddenModelSpecs`); filtered again anyway. A hidden spec still exists on the server, which applies it by name, so a stored one is kept (`ModelChoice.stored`) |
| `iconURL` | yes | yes | `iconValue` / `resolveIconSource`, reply avatars | |
| `authType` | no | | | Dead upstream: only the schema and web's fake picker data mention it |
| `hideBadgeRow` | yes | yes | `toolsHiddenBySpec` → `toolbarShown` | |
| `webSearch` | yes | yes | `SpecTools.forcedTools`, `webSearchRoute` → LibreChat search | The server ORs it with the request's flag |
| `fileSearch`, `executeCode`, `memory` | yes | yes | `SpecTools.forcedTools` | Same |
| `askUserQuestion` | yes | server only | | No composer control; web sends none either |
| `runInBackground` | no | | | The server resolves it by spec name (`agents/background.ts`); web reads it nowhere. Its results render (background-task cards) |
| `describeIntent` | no | | | The server resolves it by spec name (`agents/intent.ts`); web reads it nowhere. Intent labels render (see "Tool intent labels") |
| `artifacts` | yes | yes | `artifactsMode` → `ephemeralAgent.artifacts` | The server reads artifacts only from `ephemeralAgent` |
| `mcpServers` | yes | yes | `SpecTools.forcedMcpServers` | |
| `skills` | yes | inert | `skillsSetting` | Stripped by `sanitizeModelSpecs`, so always null against v0.8.8; the server still applies it |
| `subagents` | no | | | Applied by the server (`agents/load.ts`, `added.ts`); only `enabled` / `allowSelf` are served, and web reads neither |

| `modelSpecs` (`TSpecsConfig`) | Decoded | Where |
|---|---|---|
| `enforce` | yes | `requiresSpec`, `parametersSetBySpec`, `seedableLastUsed`, `fallbackSpecSeeds` |
| `prioritize` | yes | `SpecSeed` (Tier 0b) |
| `list` | yes | `visibleSpecs` |
| `addedEndpoints` | yes | `showsRawEndpoint` |

| Spec `preset` fields | Decoded | Honoured | Where / why not |
|---|---|---|---|
| `endpoint`, `model`, `agent_id` | yes | yes | The spec's pair (`toSeed`) |
| `endpointType` | yes | no | Taken from the endpoint's own config (`EndpointConfig.type`) instead |
| Sampling, reasoning and request keys (`temperature`, `top_p` / `topP`, `topK`, the max-token spellings, `maxContextTokens`, penalties, `stop`, `thinking*`, `effort`, `reasoning_*`, `verbosity`, `useResponsesApi`, `disableStreaming`, `promptCache*`, `imageDetail`, `resendFiles`, `fileTokenLimit`, `region`, `modelLabel`) | yes | yes | `mergedFromPreset` → the parameters; only keys the selection's parameter set lists go out, under that set's spelling (`parameterTarget`, `wireKeyFor`). `maxContextTokens` also feeds the context gauge. Under `enforce` the sheet is read-only (`parametersSetBySpec`) |
| `web_search` | yes | yes | `presetTools` → the provider's built-in search (`webSearchRoute`) |
| `url_context` | yes | yes | `presetTools`, shown only where `supportsUrlContext` passes |
| `greeting` | yes | yes | Landing description fallback (`landingText`) |
| `iconURL` | yes | yes | Icon chain after the spec's own (`iconValue`) |
| Private: `promptPrefix`, `instructions`, `additional_instructions`, `system` | yes | server | Never served (`sanitizeModelSpecs`), so always null here; the server fills them in when the request names the spec (`mergeModelSpecPreset`). Decoded for user presets |
| Private: `context`, `examples` | no | server | Never served; the server fills them in the same way |
| `artifacts` (preset level) | no | | The server reads artifacts only from `ephemeralAgent`; the spec's top-level `artifacts` is what counts |
| `codeApprovalMode` | yes | yes | A new (undecided) chat's starting approval mode, ahead of the remembered pick and within the modes policy offers (`requestedApprovalMode` → `CodeApproval.resolve`). A pick in the chat or a saved decision's mode wins; never remembered. A non-`ask` mode keeps a follow-up in the local queue, as any non-`ask` pick does |
| `codeEnvironmentMode`, `codeWorkspaces` | no | | The chat sends both from its own workspace choice (`ChatPayloadBuilder`); a spec preset's values are ignored |
| `tools` | no | | Legacy plugins |
| `additionalModelRequestFields` | no | | No mobile feature (Bedrock passthrough) |
| `assistant_id` | no | | Assistants endpoint unsupported (below) |
| `append_current_datetime` | yes | no | Assistants-only upstream; `mergedFromPreset` ignores it |
| `stream`, `isTemporary`, `userLabel`, `archivedAt`, `pinned`, `isShared`, `subagentThread` | no | | Conversation-only; meaningless on a preset |

**Deliberate omissions and server limits.** Each is server behaviour the client accepts. Don't
"fix" one without a server change:
- **Two searches under `enforce`.** A spec with `webSearch: true` and a preset `web_search: true`
  on OpenAI runs both: the server replaces the body with the preset, and also loads its own tool
  from the spec (`agents/load.ts`, the `modelSpec?.webSearch` OR). `resolveProviderToolConflicts`
  keeps both on OpenAI. The client can't prevent it (see the known gap under "Web search").
- **Lane B's web search.** With an ephemeral primary (a model or spec, not a saved agent), lane B
  copies the primary's tools (`added.ts`, `tools: [...primaryAgent.tools]`) with empty
  `model_parameters`. So LibreChat search reaches it and the provider's built-in search never does,
  which is why a comparison routes to LibreChat search. A spec whose preset sets `web_search`, or a
  chat that keeps a stored built-in search (`keepsBuiltInSearch`), keeps the built-in search on the
  primary, so lane B gets no search, as on web. With a saved-agent primary, lane B searches
  only when its own spec sets `webSearch`: the added convo carries no `ephemeralAgent`, and its
  `model_parameters` take only the sampling keys. Web is the same.
- **Keep this answer loses the Stopped chip.** `POST /api/messages/branch` saves the kept message
  with `unfinished: false` and no finish reason (`routes/messages.js`), so after a plain Keep on a
  cut-short comparison the chip never returns. Keep and continue is unaffected: it continues
  directly.
- **The assistants endpoint is not supported.** `Conversation` decodes `assistant_id` for display,
  but no request model sends it.
- **A stored `url_context: true` is still sent** after the model's URL context control is hidden
  (#667). The server skips URL context on models that don't run it, so this is harmless.
- **A preset loaded onto a spec chat keeps a spec.** Web always sets `newPreset.spec = null`
  (`usePresets.ts`): the spec, its prompt and its tools are dropped, and under `enforce` every later
  send fails. Mobile keeps the preset's own spec, or the chat's when the preset names no endpoint,
  model or spec (`PresetPromptDelegate.loadPreset`). A preset naming its own endpoint or model
  without a spec drops the chat's spec, as web does.
- **No `@` mention to switch spec, model, agent or preset** (web: `useMentions.ts`,
  `useSelectMention.onSelectSpec`). On mobile `@` inserts prompts only.
- **Web start-chat URLs (`/c/new?spec=…`) aren't handled.** Only `librechat://model?…&spec=` is
  (`DeepLinks.kt`).
- **Seen on a v0.8.8 rig during #661, not traced in code** (a candidate for an upstream report):
  every continue saves a stray user message with parent `NO_PARENT` and reparents the reply. Web's
  payload does the same.

## Message Tree
- Messages stored flat, linked by `parentMessageId` (root = `NO_PARENT` UUID)
- `MessageNode.kt` builds the tree via `buildActiveMessagePath(messages, activeBranches, streamingLeafId)`
- `activeBranches: Map<String, Int>` tracks which sibling is displayed per parent
- `switchBranch(parentMessageId, siblingIndex)` updates branch selection and rebuilds display list
- Editing a user message creates a new sibling (new branch); regenerating an AI message also creates a sibling

### Streaming anchor (in-place edit/regenerate)
- The streamed reply is rendered by `MessageList` as a trailing `StreamingMessageBubble` *outside* the tree — the mobile divergence from web, which streams into an in-tree `initialResponse` placeholder node.
- So during edit/regenerate the bubble must land where the new branch belongs, not after the stale one. At stream start each send path rebuilds `displayMessages` via `buildActiveMessagePath(messages, activeBranches, streamingLeafId)`, where `streamingLeafId` is the message the in-flight reply attaches to: the path is truncated there (and that branch is forced over any `activeBranches` override), so the trailing bubble renders as its pending child.
- The leaf is the optimistic user message for `doSendMessage` / `editUserMessage` (passed inline alongside the message insert), and the existing parent user message for `regenerateMessageNow` / `rerunReply` (via `anchorStreamTo`).
- This is a one-shot rebuild — there is no stored anchor field. The truncated `displayMessages` just persists in state for the stream's duration. On Final the untruncated path is rebuilt **in memory** by `finalizeChatDisplay` (normal + temp chats); only comparison chats still rebuild via a background `loadConversation`/Room reconcile. The full tree stays in `messages`/DB, so the old branch remains reachable via sibling navigation.
- **Invariant (load-bearing):** while streaming, nothing may write to Room or mutate `activeBranches` — otherwise the `loadConversation` observer re-emits and rebuilds `displayMessages` with no `streamingLeafId`, un-truncating the path and clobbering the in-place view. The send paths uphold this (no mid-stream Room write); `switchBranch`, `rerunEdit`/`saveEdit`, and `regenerateMessage` are all gated on `!isStreaming` so user taps can't break it either.
- **Handed-off new chat (load-bearing):** when a chat is created from the landing page, the landing VM is reset and a fresh `Chat(id)` VM resumes the stream — so the optimistic user message lives only in the discarded landing VM, and the server persists the *request* message only when the reply completes (`agents/request.js` saves it right before the Final event) or when a client drops its stream (see the next rule; the landing's own stream closes at the handoff, but the read can land first). Without intervention the resumed screen would show only the streaming bubble (no user message) for the whole stream. Fix: `NewChatSelectionHandoff` carries the optimistic `Message`; `ChatViewModel.init` seeds it into `messages`/`displayMessages` and into `pendingResumeUserMessage`. `loadConversation` keeps that seed appended — inside its off-Main `combine` map, guarded so it only appends while no server row is its copy — until the server's copy arrives, then drops it (so a later server-side delete still removes the row). "Its copy" is matched by id **or** by content (`isServerCopyOf`: user-authored, same parent, same text), because rc3+ servers do not adopt the client-minted id (see *Optimistic id reconciliation*). An id-only match never fired there, so a run that ended without a Final — a resume that 404s because the job already finished — left the seed as a second root sibling, and the path's newest-sibling default showed it alone over the persisted turn and its reply. `finalizeChatDisplay` *also* clears `pendingResumeUserMessage` in its atomic Final update. This is what feeds `finalizeChatDisplay`'s in-memory backfill in the handoff case — do **not** replace it with a network `reloadConversation` on Final (that was the #169 regression being avoided).
- **A resumed run's saved partial stays off the thread.** When the client that started a run drops its stream (the landing handing off, a network drop), the server saves the user message and the reply so far, the latter `unfinished` under the run's `responseMessageId`, and keeps writing. The resumed stream's sync frame names that id (`StreamEvent.Sync.responseMessageId` → `MessagesState.resumedReplyId`), and while the stream is live the partial is left out of `displayMessages` (`withoutLivePartial`): in `loadConversation`'s path build, and in the sync update itself for a partial the load read first. Not a `combine` input, so it never re-emits the observer mid-stream. Only the `unfinished` copy goes, so the finished reply (same id) shows. The ends that reload nothing (`AbortFallback`, `ResumeFailed`) rebuild the path, since the bubble goes with `isStreaming`. Without it the partial sat frozen above the streaming bubble until Final.
- **Optimistic id reconciliation:** `doSendMessage` / `editUserMessage` mint the optimistic user message's id and send it as `ChatRequest.messageId` (the `userMessageId` arg on `startChat`), which servers before v0.8.8-rc3 adopt and echo in the Final `requestMessage`. **rc3+ does not:** the agents controller persists the user message under `resolvePreallocatedUserMessageId` (`overrideUserMessageId ?? overrideParentMessageId ?? randomUUID()`, `api/server/controllers/agents/request.js`) and overwrites the request's `messageId` with it. The web client sends `overrideUserMessageId` only for steer recovery and queue drain, never for a plain send. So on those servers the reconciliation below does not happen by id: the Final's `requestMessage` merges in (via `mergeFinalMessagesInMemory`, keyed by id) as a sibling of the optimistic message rather than over it, and the newest-sibling default is what shows the server's copy. For a normal chat the optimistic row was never persisted, so the next Room emission drops it. Whether a **temporary** chat keeps both as a visible sibling pair is not verified. On older servers the optimistic message does reconcile by id, for normal and temp chats alike, via `mergeFinalMessagesInMemory` (`finalizeChatDisplay`, in-memory). Normal chats additionally persist the finalized turn to Room via `cacheMessages` (no network reload); the response's `parentMessageId` matching the adopted id is also what lets `finalizeChatDisplay` backfill the user message into the returned turn when a loose Final payload omits `requestMessage`, so the cached turn never drops it. Mirrors the web client's top-level `messageId`; without it the optimistic message would linger as a phantom sibling. Regenerate/continue/edit-AI send no `messageId` (they create no new user message).
- **Completion render (load-bearing):** `MessageList` keys its `LazyColumn` items by the conversation **slot** (`MessageNode.treeParentKey`), and the trailing streaming bubble is keyed to the slot its finalized message will occupy (the last visible message's id == the reply's parent). This makes the streaming→final swap an in-place update of one item rather than a remove+add, so the list never loses its scroll anchor at completion. The finalize update is also atomic (it clears the streaming fields in the *same* `stateHandle.update` that swaps in the message — see `finalizeChatDisplay`), and the last message renders markdown synchronously via `LocalImmediateMarkdown` so it doesn't mount through a zero-height async-parse frame. Reverting any of these (per-id keys, a separate streaming-clear update, async parse for the last message) reintroduces the completion flicker.

## Editing a message (#579)
`editShapeOf` (`util/MessageEditing.kt`) picks one of two editors when the editor opens, and the
SAME call gates the Edit affordance in `MessageList` — a button is never offered for a shape
`MessageEditingDelegate` would refuse (the both-ends rule the feedback thumbs follow).
- **`PLAIN_TEXT`** — any turn with no `content[]` (almost every user turn). One field over `message.text`.
  Save sends `{ text }` with no `index`. A rerun resubmits a user turn's draft as a new sibling; on a
  **reply** it can only be a plain regenerate, because `editedContent` is index-addressed over a
  content array this message does not have — so the draft is not carried and the footer says so
  (`edit_status_rerun_discards_changes`) rather than discarding it silently, which is what the
  removed `editAiMessage` did.
- **`CONTENT_PARTS`** — any turn with at least one editable part. One field per part, labelled
  Response / Thoughts, with the non-editable parts still rendered read-only beside them (a
  divergence from upstream, which hides them in edit mode) and ONE shared footer. An editable part
  is TEXT or THINK, excluding a TEXT part carrying `tool_call_ids` and excluding artifact-bearing
  text, which keeps its artifact card.
- **`NONE`** — a turn of tool calls or images alone: no Edit button at all.

Four rules are load-bearing:
- **A save closes the editor only after the server has taken it.** Every sink reads its `Result`;
  a failure keeps the draft on screen and reports through the shared `error` channel
  (`ChatNotice.MESSAGE_SAVE_FAILED`, or the server's own sentence when it gave one, such as a
  400's "Content part not found"). Per-part saves run **sequentially**: each `PUT` rewrites the whole stored content
  array, so two in flight would race, and the editor advances only the baselines that actually
  landed (`partOriginals`), leaving just the refused parts marked unsaved.
- **A save is gated on `!isStreaming`**, affordance and sink, like `submitFeedback`: it writes Room,
  which re-emits through the `loadConversation` observer and un-truncates the streaming anchor.
- **`editedContent` and the wire `responseMessageId` are a pair.** The server reads the latter as
  the history `head`; without it `setMessageOptions` loads an unrelated branch and the prefix is
  silently dropped. Both are set only by the one-changed-TEXT-part rerun.
- **A changed THINK part is never resubmitted.** v0.8.4 and v0.8.7 destructure `text` out of
  `editedContent` whatever its `type` says, so a think payload writes `undefined` over the reasoning
  prefix (only 0.8.8 reads `editedContent[type]`). It is saveable — the indexed `PUT` derives the
  type from the stored part on every version — so a think change counts like a second changed part
  and the footer asks for a Save first. This narrowing is why there is no version gate here.

Known divergence: the reply streams as a trailing bubble outside the tree, so a rerun looks like a
restart until Final delivers the server-merged content. Web seeds its placeholder with the edited
prefix and offsets streamed part indices by its length (`useStepHandler`, `editPrefixLength`) — not
ported.

## Compare Models (parallel responses)
- A comparison send carries an `addedConvo` (secondary agent/model) alongside the primary; the server runs both agents in parallel and persists ONE assistant message whose content parts each carry an `agentId`. The added/secondary agent's parts are suffixed `____N` (real `agent_…____1`, or ephemeral `endpoint__model___sender____1`); the primary's are unsuffixed.
- **Live streaming attribution:** SSE deltas carry only a step id, never an agentId. `SseEventMapper` records each `on_run_step`'s agentId keyed by step id and resolves deltas by their own step id — required because two agents' run steps interleave, and a last-write-wins would collapse both panes onto one agent. `ComparisonModeDelegate.isSecondaryEvent` then fans deltas into the primary/secondary buffers.
- **Rendering (`ChatContent.buildComparisonDisplayMessages`):** each pane filters the parallel message's parts via `util/ParallelContent.partsForPane` — so it works for every comparison turn in history, not just the live one. The captured streaming buffer is only a Final→reload-gap fallback. The single (non-comparison) list runs `collapseParallelToPrimary` so a branched-away comparison never renders both agents concatenated.
- **A pane's lane renders WITHOUT activity grouping**, matching upstream: `ContentParts.tsx` early-returns any content carrying a `groupId` to `ParallelContentRenderer`, which calls the bare `renderPart` — no `groupSequentialToolCalls` and no `hideAttachments`, so a lane folds no tool calls into a collapsed block and hoists no attachments out of one. `MessageContentAndActions` passes `groupActivity = !hasParallelGroupIds(parts)` into `groupContentParts`; the lane is keyed on `groupId` rather than the `____N` suffix because `partsForPane` has already run, leaving the primary lane unsuffixed. Only grouping is bypassed — steer segmentation still applies. The server strips `groupId` when branching one agent's parts out of a comparison (`POST /api/messages/branch`), so a branched sibling reads as ordinary content and groups again.
- **A pane's attachments are scoped to its own lane** (`util/ComparisonDisplay.attachmentsForPane`).
  A pane filters `content`, so anything a bubble draws from `message.attachments` rather than from a
  pane-filtered part would otherwise render once per pane — the office previews unconditionally, and
  the memory writes through a join (`collectUnrenderedMemoryArtifacts`) whose two halves then
  disagree about which calls exist, so even an ordinary inline `set_memory` reads as an *orphan* in
  the pane that does not hold its call. An attachment follows the lane whose parts contain the call
  that produced it; one attributable to no call in the turn — the background memory agent writes
  from its own sub-run, whose calls never become content parts — belongs to the turn rather than to
  either agent and renders once, in the primary pane, where upstream also puts it
  (`ParallelContent.tsx` renders `MemoryArtifacts` once above both columns). `collapseParallelToPrimary`
  scopes the same way. **Ownership is the recursive `outputToolCallIds` walk, never
  `renderedToolCallIds`** — the latter stops at one level because that is where the dispatcher stops
  drawing cards, which is a different question.
- **Reopen (`ComparisonModeDelegate.rehydrateFromMessage`):** nothing but the persisted attribution records that a conversation was a comparison, so `loadConversation` rehydrates comparison state from the assistant tail's parts on the first non-empty emission (latched once per load; respects a session toggle-off). Keep this answer = `POST /api/messages/branch` filtered to one agent's parts; the branched sibling becomes a single-agent tail, so the next reopen is the normal view. The route returns the whole saved message (a sibling of the comparison reply), and `ComparisonModeDelegate.branch` merges it into the tree in the same update that ends the comparison, as web appends it without refetching (`useBranchMessageMutation`, registered in `scripts/mirrors.json`). Outside a temporary chat it also writes it to Room first, so a reload whose fetch fails settles on the kept reply instead of the comparison one (which the load's rehydrate would turn back into a comparison). Then it reloads. **Temporary chats depend on the merge**: their load is a no-op (nothing of theirs touches Room), so the merge is the only way the kept reply reaches the screen.
- **Lane B's spec on reopen.** The server saves nothing about the added convo's spec: not on the message, not on the conversation. The only trace is the sender in an ephemeral lane's id, `modelLabel ?? spec.label ?? modelDisplayLabel` (left out when empty). `ComparisonState.reopenedSecondaryId` keeps that id and `laneBSpec` reads it against the spec config through `laneBSpecFor`: the one spec on the lane's pair with that label, else the one on its endpoint with that label (an admin moved it to another model, which the server still applies), else, under `enforce` only, the one spec on the pair. A hidden spec is never served, so a lane sent on one reopens without it: a limit, not a bug. A lane B spec picked in this session is held to the stored-spec rule instead. It is re-read on every use, so a reopen that lands before the spec config still finds its spec; the screens read `ChatUiState.laneBSpec`, never `secondarySpec` directly. A sender equal to the lane endpoint's `modelDisplayLabel` is what a raw lane there saves too, so outside `enforce` it reopens raw even when a spec on the pair has that label: a wrong raw reopen loses only the preset (the user can re-pick), while a wrong spec reopen sends the spec's name and preset unasked. Until the endpoint's config has loaded its label is unknown, so the lane reopens raw meanwhile, and the next read decides once it arrives. **This depends on `buildAddedConvo` sending no `modelLabel`**: the server would save that as the sender in place of the spec's label. A saved-agent lane has no spec, and the server ignores one there.
- **Lane B's settings.** `buildAddedConvo` copies its spec preset's sampling fields flat onto `AddedConversation`, the names `packages/api/src/agents/added.ts` copies. v0.8.8 applies them only when the primary is a saved agent. They are not `ModelParamPayload` output: that is keyed per provider, and the server reads this list by name whatever the provider. **The user's custom instructions do reach lane B**, and are the only primary setting that does: `buildAddedConvo` takes the primary's payload (the same `JsonObject` its request sends) and copies its `promptPrefix` (or Bedrock's `system`) onto the added convo as `promptPrefix`, which the server applies as the lane's instructions whatever the primary is. Web gets the same by cloning the whole conversation into the added slot. A saved-agent lane gets none (it runs its own), and a saved-agent primary gives none (the server ignores its payload, so a Bedrock agent's `system` never reached it). That primary is the turn's own (`spec.endpoint` for a send or queue drain), never the live selection, which may have moved by the time a queued turn drains. Drop this and lane B answers under no instructions while the primary follows the user's. A spec's prompt never reaches lane B, on web either: the served config strips `promptPrefix` and the other private preset fields (`PRIVATE_MODEL_SPEC_PRESET_FIELDS`), and the server merges them into the primary only.
- **Edit and regenerate carry lane B; continue and compaction don't.** `MessageEditingDelegate.launchSend` calls `onSendStart` and sends `buildAddedConvo`, so an edit or regenerate in a comparison re-runs both panes, as web does. **A comparison can't be continued as one** (v0.8.8, checked on the rig): a continue carrying `addedConvo` makes the server rewrite both lanes from scratch in place, partial text lost, and one without it saves the primary's answer alone, dropping lane B's. A single-model continue extends the reply correctly (checked on Anthropic; to an OpenAI-compatible custom endpoint the server sends no partial, so the model writes the reply again and the save replaces it). So `ChatViewModel.continueGeneration` refuses while comparing, and each pane offers **Keep and continue** instead (`ComparisonModeDelegate.keepAndContinue`): branch to that lane (as "Keep this answer" does), wait for the kept reply to be the one shown, then continue it as a single-model chat. The server records cut-short only per message (`unfinished`), never per lane, so both panes offer it. Outside a temporary chat the Continue waits for the branch's reload to settle (`loadConversation`'s `onSettled`): the kept reply is already on screen from memory, but a reload landing mid-stream writes Room and the observer rebuilds the path without the streaming anchor. A failed fetch still settles, on the cached rows (which hold the kept reply), and a load that replaces this one runs the callback when it settles, so the wait's 100s cap is only a backstop sized to the fetch's retry ladder. A pane needs its lane's agent id, which streaming learns only from text and thinking deltas; `onFinal` fills a missing one from the final message's parts (web reads the lane from each part), so a lane stopped inside its first tool call still offers Keep. A lane with no parts stays without: the server refuses to branch to it. Keeping lane B first switches the chat to lane B's model, spec and parameters (`ChatViewModel.keepAndContinue`, `parametersLaneRanOn`): a Continue sends with the chat's model, which while comparing is lane A's. Lane B ran on its spec's preset or, raw, on the server's defaults, with lane A's custom instructions (`buildAddedConvo`), so that is what the chat continues on. It is a pick like any other, so it writes last-used, in a temporary chat too.

## Continuing a cut-short reply
- The chip ("Stopped" + Continue, `StoppedReplyFooter`) sits under the latest reply, always visible rather than in the tap-to-reveal action row: it also says why the reply ends mid-sentence. `MessageList` and `SecondaryMessageList` draw it under the reply whose id `stoppedReply` names.
- Which reply: `ChatUiState.continuableReplyId` (`util/StoppedReply.kt`). **Not web's rule**: web's `continueSupported` needs a `finish_reason` other than `stop`, and a v0.8.8 agents reply only ever carries `tool_call_limit`, which it excludes, so web's Continue almost never shows. A stopped or dropped reply is saved `unfinished` instead, so that counts too. `unfinished` is also what a reply paused on a tool approval or question carries, so a pending action hides the chip. A reopened chat learns of that pause only from `/api/agents/chat/status`, after the cached messages paint, so the chip also waits for that check (`MessagesState.runStatusSettled`, false from opening a conversation until `resumeActiveStreamIfNeeded` answers; new chats and local sends are settled). A failed check keeps it hidden, since Continue needs the network anyway, and the check is asked again: on a backoff (2s doubling to 60s, for a server that failed while the device stayed online) and at once on an offline → online transition. So the chip doesn't stay away until a reopen. Continue replays the user turn above the reply, so a reply with none, an error and a compaction summary get nothing; so does a comparison reply outside a comparison (a single-model continue would drop lane B).

## Content Rendering
- `ContentPartRenderer` dispatches by `ContentPart.type`:
  - `text` -> `MarkdownContent` (custom regex parser with LaTeX support)
  - `think` -> collapsible thinking block. Encrypted reasoning (a provider's base64 payload, v0.8.9-rc1) is filtered by `reasoningDisplay` (`:core:model` `content/OpaqueReasoning.kt`): the readable text around it shows, a part with none draws a "Reasoning (encrypted)" row, and find-in-page, copy and the editor (payload-bearing reasoning is read-only) read the same filter. Not applied to the search index.
  - `tool_call` -> `StreamingToolCallCard` (expandable, shows args/output), except `ask_user_question`
    (see below)
  - `image_file` / `image_url` -> inline AsyncImage, tap opens `FullscreenImageViewer`
- Markdown images in reply text (`MarkdownImages.kt`) resolve through `MarkdownMedia.sourceFor`, the
  one gate before any request. **Deliberate divergence from web:** an image on a third-party host
  shows a "Load image from <host>" chip and is fetched only after a tap (remembered per server and URL
  for the process), because fetching it tells that host who is reading the reply. A turn's own files,
  by name, and anything on the server's origin load at once. Don't "restore parity" by loading them.
  The server's own host on another scheme or port is named by its whole origin; over `http` from an
  `https` server it never loads (the bearer is host-scoped), nor does a host the chip can't name
  exactly as Ktor parses it.
  - `error` -> red-styled error display
- `CodeBlock` renders fenced code with syntax highlighting, language badge, and copy button (3s checkmark)
- **Run Code** (web `RunCode.tsx`, `MarkdownComponents` `code`): a saved reply's fence whose language
  `CodeRunLanguages` knows (c, cpp, d, f90, go, java, js, php, py, rs, ts and their aliases) gets a Run
  button beside Copy, behind RUN_CODE.USE (`LocalCodeRunHost`, provided by `ChatScreen` only: no shared
  links, tool cards or artifact panel). States Run → Running → Complete (1.5 s) / Failed (2 s, plus
  `ChatNotice.RUN_CODE_FAILED`); a block already running ignores taps. Output sits under the code
  ("Output", the latest run, its files through `ToolCallAttachments`, a switcher through earlier runs).
  - **Block index is web's**: per content part, counting fences that aren't `math`, `mermaid` or empty
    (`MarkdownSegment.CodeBlock.fenceIndex`, `webCodeBlockCount`; `latex`/`tex` count though drawn as
    math). A part split by artifacts or `\ui` markers threads each chunk's base (`ProvideCodeRunBase`).
    The part is the raw content index (null for a reply with no parts); results key as web's
    `mapToolCalls`. Both are mirrored (`run-code-block`, `run-code-languages`).
  - `CodeRunDelegate` (its own flow, not `ChatUiState`): `POST /api/agents/tools/execute_code/call`, and
    the stored runs (`GET /api/agents/tools/calls`) read once per conversation when a runnable block first
    shows (web reads them on every open). A new run appends to its block's list.
  - Not on the live bubble (`streaming`): the reply isn't saved yet, so the server would 404, and the
    target is computed only for settled text. Not on user turns, Compare lanes (filtered parts) or
    while a turn is edited. Web's 1 s leading debounce is the Running state's disabled button here.
- LaTeX: `LatexBlock` (block math) and `LatexInline` (inline math) via AndroidMath native rendering
  - Supports `$$...$$`, `\[...\]` (block) and `$...$`, `\(...\)` (inline)
  - `$...$` follows Pandoc's `tex_math_dollars` neighbour rules (`scanInlineLatex()` in
    `MarkdownParsing.kt`): opener followed by non-space, closer preceded by non-space and not
    followed by a digit, `\$` never delimits. Nothing inspects the span's content. Backtick code
    spans are skipped and an open fence stops the scan (code wins, as on the web). While streaming,
    a closer at the very end of the buffer is deferred so `US$5 to US$` + `10` never flashes as math
  - Falls back to monospace text if AndroidMath can't parse the expression

### Long user messages collapse (web's `collapseLongUserMessages`, v0.8.9-rc1)
`CollapsibleUserText` wraps a user turn's text (not its quotes, skill pills or files) in
`MessageContentAndActions`: taller than 256dp × the chat font multiplier (web: 256px × rem), it
clamps with a bottom fade and a Show more / Show less button; the 8dp tolerance only decides whether
the toggle shows. The clamp is decided in the measuring layout pass, so the first frame is already
clamped. Visual only: the text stays composed, selectable and readable by screen readers. The
focused in-conversation search match forces it open. Off while editing. Setting: Settings › Chat
"Collapse long user messages" (`AppPreferences.CollapseLongUserMessages`, default on), provided as
`LocalCollapseLongUserMessages` by `ChatRoot` (chat and shared viewer). Web also collapses steer
parts; ours renders steers through `SteerContentPart` without the clamp.

## Inline citations (web_search / file_search)
The tools make the model write anchors after the claims they cite: `turn0search0`, a group
`…`, a highlight `…`. The grammar is upstream `client/src/utils/citations.ts`,
ported to `:core:model` (`content/Citations.kt`) so search indexing can strip it too. Models write
**both** forms: the private-use character and the literal six-character escape (the v0.8.8 rig's
haiku wrote only escapes), so everything converts the escape first.
- **Resolution** (`CitationIndex`): `turn{T}{type}{I}` is the I-th result of `type` in the reply's
  `web_search` attachment whose `turn` is T (search→organic, news→topStories, ref→references,
  image/video→images/videos), or of the T-th `file_search` attachment for `file`. Files keep their own
  turn map: upstream shares one, so a reply that ran both loses one at turn 0.
- **Rendering is two steps.** `rememberCitedMarkdownSegments` normalizes each prose segment against
  the turn's index (`LocalMarkdownMedia.citations`, built in `ProvideMarkdownMedia`): unresolvable
  anchors go with the space before them, highlights lose their markers, and only resolvable groups
  stay, in canonical form. Then `citationAnnotate` claims the text leaf holding a group and emits an
  inline-content pill (`CitationPill.kt`), sized by measuring its label. A group never splits across
  leaves. Code spans and fences are never touched. Table cells draw a clickable label instead (no
  inline content there). The pill opens `CitationSourcesSheet`, hosted by `ProvideMarkdownMedia`.
- **Deliberate divergence:** the pill names the site (`attribution`) only when it differs from the
  page title, else the domain. Upstream's `attribution || title` puts whole foreign page titles in
  the pill, because some providers (Keenable) send the title as the attribution.
- **While streaming** the tail of the reply holds back a partial anchor, group or escape, so
  `turn0sea` never flashes.
- **Find-in-page renders without citations**, and `countMarkdownOccurrences` counts the same stripped
  text. Keep the two in step.
- **Plain-text surfaces strip them**: read-aloud, the accessibility description, the search index
  (format 4) and its previews. Copy numbers them `[1][2]` with a `Citations:` footer, as web's copy does.

## Search cards and sources (web parity, v0.8.8)
- **Web search** (`WebSearchCard`, `WebSearchResultCard.kt`) ports upstream `WebSearch.tsx`. While the
  reply streams it reads "Searching the web" (or "…again" past turn 0), then "Processing results"
  once sources arrive, then "Reading results" while it is the latest step and no reply text has
  followed (`liveWebSearchPhase`; the streaming card gets `isLatestStep` and `replyLength`). Settled:
  three favicons, the model's `intent` or "Searched the web", a chevron to every source (snippet,
  domain, date), and an info button for the query, count and the provider's answer box. Inside an
  activity group a card without an `intent` shows its query rather than repeating the group's
  "Searched the web" (a divergence: upstream repeats it).
  `searchOutcome`: a failed search falls back to the generic tool card; a cancelled one, or a
  settled one with no status, output or sources, draws nothing, as upstream. A Stop after the search
  saves its output but not the attachment, so the sources then come from the tool's text output
  (`parseFormattedSearchOutput`; upstream's web shows none). Snippets are shown as plain text
  (`plainSnippet`), and ISO dates as a short local date (upstream prints them raw). Only the built-in `web_search` always gets this card; another tool whose
  name merely contains "search" keeps the generic card unless it produced web sources.
- **Images, shopping, places** (`WebSearchVerticals.kt`, upstream `verticals.tsx`) sit under the card,
  or below the activity group when the call is inside one (`hideAttachments`). Every URL must be
  http(s). **Deliberate divergence:** pictures load only after a tap ("Load images"), like a reply's
  third-party images. Places open Apple Maps on iOS and Google Maps elsewhere (`prefersAppleMaps`).
- **File search** (`FileSearchSourcesCard`, upstream `RetrievalCall.tsx`): "Searching your files" live,
  "Cancelled", the run's duration, and a file name that opens the file through `ToolFileActions`
  (PDF viewer, artifact panel, or Open in / Save / Share). When the tool's text output parses, its
  sections list in their own order, matched to files by normalized name (`fileSearchResults`).
- **Activity groups** of searches only read "Searched the web / your files / web and files" with
  favicons (`searchGroupHeader`), unless the server labelled the group.
- **Sources panel** (`SourcesPanel.kt`, upstream `Sources.tsx`) only on parallel content
  (`hasParallelGroupIds`), as upstream: All / News / Images / Files, three cards and a "+N" sheet.
  Upstream draws one panel above both lanes; mobile draws one per Compare pane, from that lane's
  attachments.

## Streaming
- `ChatViewModel.sendMessage()` calls `ChatRepository.startChat()` which returns `Flow<StreamEvent>`
- Two-phase: POST /api/agents/chat -> streamId, then GET SSE stream
- **`StreamingManagerDelegate` owns the session lifecycle only** (dispatch, `endStream`, resume, reconnect).
  Text and reasoning deltas accumulate in `StreamTextBuffer` (50ms throttled flush); events that only
  describe the reply in progress — tool calls, attachments, usage, title, retry progress, subagent
  and PTC traces — are projected onto state by `LiveReplyDelegate`; the ViewModel-owned operations (reload,
  un-send, key-error channel) come through `StreamingHost`. A new lifecycle event belongs in the
  manager; a new "describes the reply" event belongs in `LiveReplyDelegate`
- **Reasoning streams into its own buffer** (`StreamTextBuffer` → `MessagesState.streamingThinking`),
  which the streaming bubble shows in a collapsed `ThinkingContentPart` above the text — the place the
  persisted message renders its THINK parts. It used to share the text buffer, so it streamed as
  body text under the sender label, and a resume's sync frame (reading only `text`) dropped it; the
  sync now restores it from THINK parts' `think`. It follows `streamingContent` everywhere: cleared
  in the same atomic `finalizeChatDisplay` update (no duplicate, no flash), preserved with the partial
  on the error/abort ends, and neutralized for the chrome. Comparison panes still fold reasoning into
  their own buffers. **Two accepted limits at settle:** the live block's expanded state (key
  `think:streaming`) does not carry over to the persisted THINK part (keyed `messageId:index`), so a
  block the user opened mid-stream shows collapsed after Final; and a multi-step reply's reasoning is
  merged into the one live block at the top, so at settle it redistributes into its per-step THINK
  parts and the reply reflows. Carrying the state across would mean hoisting the card's expand state
  through the bubble and threading a "first THINK of the just-settled message" flag through
  `MessageContentAndActions`, the grouping transform and `ActivityGroup` — and that first THINK is
  often folded inside a collapsed activity group, where opening it shows nothing.
- **The live bubble renders through `TextContentPart(streaming = true)`** (#302), so artifact
  detection runs on every flush — an unclosed artifact streams as its source via
  `IncompleteArtifact` instead of raw `:::artifact{…}` text. The `streaming` flag must stay
  threaded down to every `MarkdownContent` and `CodeBlock` beneath it: dropping it reintroduces
  the per-delta Loading flash + LRU pollution documented in `CachedMarkdown`'s KDoc (the #169
  class), and `CodeBlock`'s off-main highlight retention keys off it. The bubble passes no
  `searchQuery` — the streaming message is outside the search enumeration, which is what keeps
  the two search walks lockstep-free here.
- **The cursor is inline content, not a sibling composable** (`StreamingCursor.kt`). `trailingCursor`
  threads from the bubble to whichever segment holds the reply's tail, which appends an invisible
  sentinel (U+2063) to the markdown it hands the renderer; a `MarkdownAnnotator` claims that leaf and
  swaps the sentinel for an `InlineTextContent` placeholder resolving to the animated cursor. That is
  what puts the cursor after the last word instead of on a line of its own, and going through inline
  content (rather than baking a `▌` glyph into the source) is what keeps it animatable. Because it
  rewrites the parsed content, `CachedMarkdown` treats `trailingCursor` like `streaming` for the AST
  cache: nothing sentinel-bearing is written, and the fast-path read is skipped (the tier-2 fallback
  read still consults the cache, harmlessly — its key derives from the sentinel-bearing content, which
  is never written). A sentinel-bearing AST must never be served to a settled render. No current call
  site is affected, since every `trailingCursor = true` caller also passes `streaming = true`, which
  already disabled the cache — this is future-proofing. Tails the markdown library does not render as annotated text fall back to a block-level
  cursor below, decided at **three** points — not one, so don't grep `canHostInlineCursor` expecting
  to find them all: `TextContentPart` when the tail is an artifact card, `MarkdownContent` when a
  search query is active (the find components in `SearchableMarkdown` build their own strings and
  don't run the cursor's annotator — defensive today, since no `trailingCursor` caller passes a
  query), and
  `canHostInlineCursor` on the trailing segment's kind — code blocks, tables, LaTeX blocks,
  and text still inside an **unclosed** code fence, because
  `parseMarkdownSegments` only lifts *closed* fences out — without that last case a long code reply
  would stream with no cursor at all. The segment-kind half is unit-tested.
- The bubble's sender is `replySenderName`: the agent's name, else the selected spec's label (the
  names the server saves those replies under), else the model. Compare's pane A takes the same name
  for its parallel turn. A raw model's reply still takes the server's own name at Final (upstream
  `getResponseSender`: "Claude", "GPT-4o"), which isn't ported.
- Before the first delta there is no insertion point, so the bubble shows `StreamingWaitIndicator`
  instead of a cursor blinking against empty space.
- Tool calls tracked in `activeToolCalls` list (start -> complete lifecycle)
- **MCP sign-in mid-run** (`StreamEvent.ToolCallAuthRequired`, from an `on_run_step_delta` carrying
  `delta.auth`; upstream `oauthPrompt: on_use` sends it on a server's first tool call). It puts
  `authUrl`/`authServerName` on the live call, and `StreamingToolCallCard` draws
  `McpSignInToolCallCard` until the call completes. Its Sign in reads `LocalMcpSignIn` (provided by
  `ChatRoot` from `rememberMcpSignIn` + `McpSignInViewModel`), which binds through `:core:data`'s
  `McpOAuthLauncher` and opens `:core:ui`'s `McpOAuthSignInDialog` (system browser before
  v0.8.9-rc1). Nothing launches without the tap. The run resumes on its own once the callback
  lands, so a success needs no follow-up beyond the status reload every sign-in end triggers
  (`McpSignInUiState.ended`, for the settings chips' dots). Comparison panes route it too
  (`ComparisonModeDelegate.routeToolCallAuth`), subagent prompts included (below).
  - **A reconnect restores it.** The sync frame's `aggregatedContent` carries no auth, and `onSync`
    replaces the tool calls, so the prompt would be wiped. The server projects the live prompts as
    `resumeState.pendingOAuthPrompts` (upstream `projectPendingMCPOAuthPrompts`, v0.8.8-rc2+;
    unexpired, on unfinished steps), and `SseEventMapper` turns each into the same
    `ToolCallAuthRequired`, after the snapshot, preceded by a `ToolCallStart` only when the
    snapshot lacks the call (as web's `applyPendingOAuthPrompt`). `runSteps` stay unread.
    Verified server limit: the projection keeps only `oauth_mcp_*` calls (the synthetic step of a
    turn-start or reconnect prompt, `buildMCPAuthToolCall`). An `on_use` prompt raised inside a
    real tool call (`MCP.js` passes that call's own name) is not projected, so a reconnect during
    it still loses the action, on web too.
  - **A subagent's prompt shows on the `subagent` call.** Its tools run on the parent's stream, so an
    `on_use` prompt arrives as a top-level delta naming a call that lives in the subagent's trace,
    not in `activeToolCalls`. `LiveReplyDelegate.onAuthRequired` puts it on the parent whose trace
    holds the call (or, for a `subagent_update` `run_step_delta` carrying `auth`, the envelope's
    parent), recording the child id in `authToolCallId`; the child call's completion or close
    clears it, since the parent stays running for the rest of the subagent. The lookup is
    `List<ActiveToolCall>.withAuthRequired` / `withoutSubagentAuthFor`, shared with the comparison
    panes: `routeEvent` applies a `subagent_update`'s sign-in and child end to the pane lists and
    returns `false`, so the standard path still records the trace.
- **Stream termination is a chokepoint** for every *event-driven* end — clean/aborted Final, error, failed abort, watchdog, resume-found-expired — which funnel through the single private `endStream(reason)` in `StreamingManagerDelegate` — and so does a flow that ends with neither Final nor Error: `launchStream` ends it as `Reconcile` and `resumeStream` as `ResumeExpired`, unless a Stop is pending (the abort watchdog owns that end). `SseClient` completes a flow that way only when its resume got a **404** (the job is gone, so the reply is on the server) or the account switched. **A body that merely closes is not a finished run**: servers before v0.8.9-rc1 send no keepalive (rc1's comment every 25 s can be switched off), so a proxy/CDN idle timeout during a long tool call or a human-review pause closes a live stream, and the server's own `res.destroy()` on a publication failure asks for a reconnect — `SseClient` resumes those with `resume=true` on its retry ladder, as upstream's transport-failure path does, keeping the partial and the pause card. Its attempt counter resets on a parsed event, not on bytes, so a 200 that carries no events (a captive portal) still climbs the ladder. **The ladder's ceiling (`RETRY_EXHAUSTED`) is not a network error either**, and neither is a persistent HTTP status such as a proxy's 502 on every resume (`STATUS_RETRY_EXHAUSTED`): neither says anything about the run, so the delegate asks `/chat/status` right away, as upstream does at its ceiling — resume if active (once per run: the flag resets at every attach — a local send, opening a conversation with a live run, foregrounding, network recovery — but not at the ceiling's own re-attach, so an unreachable live run ends instead of looping), the same end as a 404 if not (refetch, no banner). Only when the status read fails too is there an error: a network error after the transport ladder (the case the connectivity observer can recover), the status error itself after a persistent status. 401 stays in-band and is never adjudicated. An account switch also completes the flow, and ends nothing: a reload then would read the conversation with the new account's credentials. The same guard runs after the ceiling's status read, which can take seconds, so a switch during it neither resumes nor reloads. The 404 case is the network-drop recovery: the run finished while offline, and before `launchStream` ended it here the chat froze on the partial. The sealed reason (`Finalized` / `StreamError` / `AbortFallback` / `ResumeExpired` / `Reconcile`) decides job cancel, state write, queue policy, and whether a reload is allowed. A per-session Int counter latches each session to at most ONE end, and stale ends from a previous session are no-ops — do not add teardown steps at call sites; add them to the reason table. Structural invariant this encodes: **nothing reloads on an abort path** — the server emits the aborted frame BEFORE persisting, so any refetch there races the save (and the optimistic user message was never in Room, so a reload can lose it entirely). `Finalized` writes no state: the atomic finalize in `finalizeChatDisplay` owns that (the no-completion-flash invariant)
- `stopGeneration()` POSTs `chatRepository.abortChat()` and **deliberately does not cancel the stream job.** The abort POST only acks (`{ success, aborted }`); the server ends the run by emitting an ordinary `final` frame flagged `aborted` over the same SSE stream, and *that* frame carries the partial's content parts. Cancelling the collector was the original bug — the stopped reply vanished. `handleFinal` reads `aborted` off the frame, not off local stop state, so a Stop from another client is handled identically. Stop-specific behavior: no auto-TTS, no `gen_title`, no queue drain (the hold is re-asserted). `abortRequested` guards double-taps. Works before the `created` milestone too — a null stream id makes the abort route fall back to one of the caller's active jobs (the *oldest*, and the client can't tell which, since `ChatAbortResponse` drops the returned `aborted` id — so with a second stream live server-side a null-key Stop can hit the wrong job; known gap). An acked abort arms a 15s watchdog; if the frame never lands (dead socket) or the POST fails, `endStream(AbortFallback)` stops locally with the partial preserved and NO reload
- **Aborted finals follow the server's exact persistence contract** (`applyAbortContract` in `util/FinalMessages.kt`, applied once in `handleFinal` before anything renders or caches). `abortPersistedServerSide()` mirrors the abort route's 4-way save gate — `earlyAbort`, missing `requestMessage`, empty (already-server-filtered) `content`, or the synthesized `"${userMessageId}_"` response id all mean the server saved no response row. Persisted → the missing `text` is rebuilt via the `parseTextParts` port (TEXT + THINK + STEER, like the server's abort call; the port itself is `flattenSearchableText` in `:core:model`, with the server's literal-space join rule) so the cached row equals what a later fetch returns. Not persisted → the response is DROPPED from the frame: merging it would mint a phantom leaf the next send uses as a `parentMessageId` the server has never heard of. `cacheTurn` is then unconditional (the contract already removed anything unsaved, and the request message of a non-early abort IS persisted — caching it is the only write that persists the optimistic user message). Still suppressed: `saveConversation` (the frame's conversation is a stub with a hardcoded `New Chat` title); the title is re-read network-first instead of generated (an `immediate`-mode title that finished before the Stop exists server-side while the cache holds the placeholder) — retried past a fetched `New Chat` and never applied as a downgrade, because the server's title save is gated on the request's unwind and an immediate read races it (the same emit-then-persist race, one layer up). Both `parseTextParts` and the gate are MIRRORED SERVER LOGIC — re-verify on upstream bumps (`/sync-upstream`)
- **earlyAbort = un-send.** A Stop that lands before `created` persisted nothing — not even the user message — so `handleFinal` asks the ViewModel to un-send the turn (`StreamingHost.unsendTurn`): it removes the minted optimistic message (`MessageTreeDelegate.unsendOptimisticTurn`, single atomic emission) and restores its text to the composer (web parity). The minted id is threaded per-turn through `beginStreaming(isEdit, optimisticUserMessageId)`; it is NULL for regenerate/continue/edit-AI, whose user message is a persisted row the un-send must never remove
- **A failed send restores its text unless the server kept it** (#405). Before `created`: the
  `StreamError` end un-sends exactly like an early abort. After `created` the user message is only
  *probably* saved — the server emits `created` before building the prompt (memory, MCP config,
  attachments) and saves the user row after — so a non-network error reloads with the optimistic
  message as `loadConversation(unsavedTurn = …)`, and the settled read restores the text to the
  composer when no row matches it by id or `isServerCopyOf`. Network errors are excluded: the run may
  still be live and the connectivity observer re-attaches to it. **One pre-`created` failure takes
  the reload path too:** a send the server's middleware refused (`ChatRefusal` — rate limit, ban,
  moderation, foreign conversation) on an existing, non-temporary conversation, because upstream's
  `denyRequest` saves the user message and its error turn whenever the request names a conversation
  and a real parent. Un-sending it would put the text in the composer *and* the thread
- **The final-frame merge is monotonic** (`Message.mergedOver` in `util/TemporaryChatMerge.kt`): applying a frame message over an in-memory copy gap-fills — incoming wins where it carries information, absent/blank keeps local, terminal-state booleans (`error`/`unfinished`) always incoming. This is why a skeletal aborted `requestMessage` no longer needs per-field guards to keep the optimistic message's attachments. ONLY valid at the final-frame chokepoint — full-record sync paths (`getMessages`, `refreshMessages`) keep meaningful nulls and must never use it. `finalizeChatDisplay` returns the POST-merge instances so `cacheTurn` writes exactly what the screen shows
- `onPause()`/`onResume()` handle app backgrounding: checks stream status, resumes if still active. Two abort-window rules: `onPause` does NOT cancel the collector while a Stop is pending (it is carrying the aborted final that holds the partial; the watchdog covers a frame that never arrives), and `onResume` touches nothing when the session already ended while backgrounded (the old wipe of `streamingContent` here was the stop-then-background partial loss)

## Key Components
| Component | Purpose |
|-----------|---------|
| `ChatInput` | Auto-growing text field, send/stop button, file attach, voice |
| `MessageBubble` | Icon + content column + action bar + sibling nav |
| `MessageList` | LazyColumn of MessageBubbles with scroll-to-bottom FAB |
| `ModelSelectorSheet` | Bottom sheet: search + specs, spec groups and endpoint-grouped models |
| `SiblingNavigator` | "1/3" with chevrons for branch switching |
| `LandingContent` | Greeting or agent/spec title, description, entity icon (starters dock apart, `LandingStarters`) |
| `StreamingCursor` | Live insertion point during generation; `StreamingWaitIndicator` before the first delta |
| `PresetPicker` | Preset selection menu |
| `SavePresetDialog` | Dialog to name and save current config as preset |

## Prompts
- `PromptsLibraryScreen` and `PromptDetailScreen` live in `chat/prompts/`
- `PromptsViewModel` loads **every** prompt group via `PromptRepository.getAllGroups()`. The
  library's sort, category filter and category chips all run client-side over that list, so a
  paged load (the server orders `/groups` by popularity, then recency) would sort and filter a slice.
  Deliberate divergence from web's infinite scroll, which has no sort picker.
- `handlePromptMention()` on ChatViewModel inserts prompt command text at `@` position
- **`PromptRepository.revision` is how a mutation reaches the screens beneath it.** The editor is
  *pushed on top of* both the library and the chat entry, so their ViewModels are retained and
  `init` — the only caller of `loadGroups` / `loadAvailablePrompts` — never runs again; prompts are
  network-direct with no `Flow` and no Room cache, so there is no observable to re-drive them from
  either. The counter is bumped **inside the repository**, after the API call, so it announces only
  mutations the server accepted and no call site can forget it (same shape as
  `AgentRepository.revision`). A ViewModel mutation must **not** also call `refresh()` itself: the
  revision is the single reload path, and doing both fetches the same list twice.
- **The reload is driven from the composition, not from a ViewModel collector.** Both screens read
  the revision (`ChatRoot`'s required `promptLibraryRevision`/`onRefreshPrompts` pair for the chat,
  a `LaunchedEffect` in `PromptsLibraryScreen` for the library) and call a `refreshIfStale`. That is
  the visibility gate: a bump while the screen is off-screen costs nothing until the user returns,
  so an editor that bumps five times flipping through versions costs one reload — and both read
  the deliberately unpaginated `/api/prompts/all`. Keying the effect on the revision (rather than firing
  once on entry) is what keeps it correct if a screen *stays* composed: it still reloads the moment
  the revision moves, so nothing here depends on Nav3 disposing the entry underneath.
  Each consumer records the revision it loaded and the one it is fetching, so a re-entry with
  nothing changed is free, the first composition does not duplicate `init`'s in-flight load, and a
  load that *failed* (which records nothing) is retried on the next visit. Reloads are
  cancel-replace: the fetches are unordered and the write is last-writer-wins, so a superseded one
  could otherwise re-list a prompt the user just deleted. The chat's refresh sits behind the
  `PROMPTS.USE` gate, so a denied user issues no request; the revision-driven library reload
  swallows its own errors (nobody asked for that fetch — only the pull-to-refresh gesture and the
  initial load report failure).
- **A prompt save is up to three requests, and `saved` may only flip when all of them landed.**
  The create route carries neither the oneliner nor the `/` command, and a body edit rides on
  `addPromptToGroup` rather than the group update — so each save reads every follow-up `Result`
  before reporting success. Every `PromptRepository` method is `safeApiCall`-wrapped: failure
  arrives as a returned `Result.Error`, never as a throw, so a `try/catch` around one is dead code.
  `PromptEditorScreen` pops on `saved`, so discarding a result loses the user's edit behind a
  success animation.
- **A body edit is not saved until the new version is promoted.** `addPromptToGroup` appends a
  version and leaves the group's `productionId` pointing at the old one, so the edit is stored
  where nothing reads it: the library row, the `/` picker and the editor itself all show the
  *production* body. `publishNewVersion` therefore follows the add with `updatePromptProductionTag`
  and reports the save on both, using the id from the add's response — the only place the new
  version's id appears. Web does the same on save (`alwaysMakeProd`, default on); mobile exposes no
  toggle for it, because a Save that visibly changes nothing is not an outcome worth offering.
- **Two prompt routes do not follow the bare-object convention, and both mismatches are invisible
  from inside the app.** `POST /groups/{id}/prompts` nests the body under `prompt` in *both*
  directions, and `PATCH /{id}/tags/production` takes no body and answers `{ message }` rather than
  the prompt — sending HTTP 200 whether it worked or not. A flat add body is rejected with HTTP 400
  ("Prompt text is required and must be a non-empty string"), since the route reads
  `req.body.prompt.prompt`; `AddPromptToGroupResponse.prompt` is non-nullable so that the 200-with-
  `{ message }` failure fails to decode rather than reading as an empty success. Getting either
  wrong compiles, type-checks and fails only against a real server, where it surfaces as a
  `Result.Error` the UI handles correctly and a mutation the user cannot make.
  `PromptsApiWireShapeTest` holds live captures of both; no test above `:core:network` can see a
  JSON key.

## `ask_user_question`: one question, a panel then a card (v0.8.8)

A clarifying question reaches the screen twice over its life, and the two must never overlap.

- **While the run is paused** it is the interactive `AskUserQuestionPanel`, which takes the
  composer's place (`ChatScreen` hides the composer while it is up) with one tab per
  question of a batch — the only thing that can resolve it. Below 600dp (phones, a folded Fold) it
  is a Claude-style card with a page-dot pager (see the next section) where a pick or a
  per-question Skip advances and the answer filling the last blank submits; at 600dp and up it is
  tabbed with explicit Back / Next / Send. Both collapse to one line. **Stop appears only once a
  resume has failed** (`MessagesState.pendingActionResumeFailed`, in the action row): hiding the
  composer hides the only other one, and a pause this client cannot resolve (a fingerprint 403
  fails every answer and Skip alike) would otherwise strand the user until it expires — but
  before anything has failed it only takes width from the question. Panel submits never re-home
  text into the hidden composer — their words stay in the drafts, on the panel; only
  `submitAnswerFromComposer` restores. The thread keeps only a one-line `AskUserQuestionWaitingMarker`
  (tool approval docks too; see the next section). The panel's drafts, active tab and collapse
  state are hoisted into `MessagesState`; `sendDuringRun`'s answer-the-active-tab routing
  remains as the fallback for a send that reaches the ViewModel while the composer is hidden.
  The same question is *also* sitting in `activeToolCalls`, because the agent called a tool to
  ask it; `withoutUnansweredQuestions()` drops it from both streaming lists so the user is
  not asked the same thing twice, once under a spinner for a "call" that cannot finish until they
  answer.
- **Once answered** the same tool call renders as `AskUserQuestionRecordCard` — the durable record
  of the exchange, collapsed to question + answer and expanding to the description and the options
  offered, with the picked ones marked. `ToolCallDispatcher` routes the persisted part there too,
  so history, reload and a mid-run reconnect all show the record rather than the generic card's
  tool name over a JSON dump.

Everything the record shows is already on the wire: the question, its description, options and
`multiSelect` come from the call's own `args` (an object mid-stream, a JSON *string* once
persisted — `parseAskUserQuestion` takes both), and the answer from its `output`. Nothing is
gathered or stashed client-side to make it work, so a conversation opened on a second device
renders identically.

The answer is shown by option **label** only when every segment of it maps back to an option
(`askAnswerDisplay`) — a value may legally contain the `", "` that joins a multi-select answer, so
a partial mapping could split one value into fragments and relabel them as choices the user never
made. Skip posts upstream's `ASK_USER_DECLINED_ANSWER` sentinel (the run must resume either way),
which reads back as "skipped" rather than as a sentence the user typed. A call whose args failed
schema validation carries `inputValidationError` and says so — it was never put to the user, so
rendering it as unanswered would be a lie.

## Tool approval: one call at a time, above the composer (v0.8.8)

A run paused on a `tool_approval` batch shows `ToolApprovalPanel` docked **above** the composer
(`ChatScreen` stacks it over the composer in the measured bottom box), and the thread
keeps a one-line `ToolApprovalWaitingMarker`. Above rather than in place of the composer, unlike
the ask panel: a decision does not compete with the composer, which keeps its Stop and queue — so
this panel carries no Stop of its own.

It pages through the batch the way the ask panel does, sharing its chrome (`PausePanelChrome.kt`:
pager, tabs, collapse toggle, collapsed overlay, waiting marker, the 600dp split and the
auto-advance beat). Below 600dp a `‹ ● ━ ○ ›` page-dot pager, where an Approve or Reject on a
call not yet decided moves on and the decision completing the batch submits it; at 600dp and up
one tab per call with Back / Next / Continue (n/N). Edit and Respond reveal their field and never
advance on their own. Approve all / Reject all appear only when every call's policy permits that
decision, and submit immediately.

**Auto-advance is decided in `PendingActionDelegate`, not the panels**, for both panels
(`pickAskOption` / `pickToolDecision`, tested in `PendingActionAutoAdvanceTest`). A panel only reports
the pick and its layout's `PausePanelAutoAdvance` (compact: next open item or submit; wide: next tab,
never submit). The delegate keeps one cancellable job, and the panel cancels it when the layout leaves
the screen, so a fold inside the beat can't submit a compact pick under the wide layout.

Paging is shared by both panels:
- **Compact: page dots.** Filled = done, open = not yet, a short bar = on screen. Dragging along
  the dots scrubs (iOS home-screen style, a haptic tick per item) — relative to where the drag
  began, 48dp of travel per item (shrunk so every item fits in 80% of the window), because the
  dots themselves are ~20dp apart and mapping the finger onto them overshoots; a tap jumps; the
  arrows step. Past 10 items the dots fall back to `n of N` text. Screen readers get the `n of N`
  as the row's state description and page with the arrows.
- **Wide: a page swipe, started only from the answer controls.** `PausePanelSwipeArea` is a small
  pager: the whole body follows the finger with its neighbour alongside, commits past a quarter of
  the width or on a fling, and is rubber-banded at the ends so a swipe never submits. Tabs,
  Back / Next and the auto-advance beat slide the same way. Only the `swipeHandle` modifier the
  page lambda receives starts a swipe — the ask panel puts it on the option rows, the tool panel on
  the decision row. The question, description, arguments and text fields deliberately don't page.
  The handle moves with the page it drags, so the fling velocity is tracked on the finger's
  accumulated delta, never on `change.position` (which stands still under the finger on a device;
  the instrumented harness can't see the difference — its events land before a frame re-places
  the page).
- **Wide: dragging along the tabs** selects the tab under the finger, ahead of the row's own
  scroll (Initial pass), so an overflowing row scrolls to follow the selection rather than the drag.

**Remembering (v0.8.9-rc1).** A call whose review config has `allow_always` (and allows approve)
gets an "Always allow" button between Reject and Approve, with a static hint under the row (shown
whenever offered: the pick is final and the beat moves on, so a selection-only hint would flash
past). It is `approve` + `scope: session`, and `toolBatchResolutions` strips the scope from any call
not offered it, because the server 403s the whole batch for one. "Approve all" stays plain. A
`remember_scope` (the agent's chat/always mode) shows a notice above the arguments; a
`remember_unavailable` replaces it in the error colour. Reject opens an optional reason field;
focusing it cancels the beat, so the explicit Next / Continue takes over. A resume 403 whose body
carries `disallowed` is `ChatNotice.TOOL_DECISION_NOT_PERMITTED`, not the fingerprint copy.

**Code-action previews (v0.8.9-rc1).** A request whose `source` is `librechat_code` (the server's
own code tools; never inferred from the name, which an MCP tool can share) reviews as the action:
`bash_tool` → "Run command" + the command (generic when the args have an `environmentAction` key,
even a null one), `create_file` → "Create <path>" + the contents, `edit_file` → "Edit <path>" + the
`edits` (or the whole args). The port is `core/model` `approval/ApprovalPreview.kt` (web
`approval/preview.ts`, mirrored as `approval-preview`): the batch is built once at the top of
`ToolApprovalPanel` because it shares one 64K budget (16K and 200 lines per call, name 256,
description and path 1024 each, in that order), and the cap is applied after the reveal, so it can
cut a `\uXXXX` short, as web does. **The reveal is exactly web's two sets**: bodies spell out C0
except tab/LF/CR, DEL and C1, U+061C, U+200E/F, U+202A–E and U+2066–206F; labels (name, description,
path) also spell out tab/LF/CR. Zero-width spaces/joiners, the BOM and the soft hyphen are NOT
revealed, upstream or here; don't widen it in one place. Every other tool keeps its raw arguments.
Edit seeds its field from the raw arguments, never the bounded body. Divergence: the truncation
note drops web's "use the timeline tool card" clause, since the live card of a paused call has no
arguments on mobile (no argument deltas).

The resume is all-or-nothing (`toolBatchResolutions` returns null until every call is decided and
complete — the route 400s a partial batch and an edit/respond without its payload). Allowed
decisions join by `tool_call_id`, never position. Drafts, the active call and the collapse state
live in `MessagesState` (`toolDecisionDrafts`, `toolActiveCallId`, `toolPanelCollapsed`) and ride
`PendingActionDelegate`'s reconnect retention with the ask panel's, so a fold or backgrounding
doesn't cost the user their decisions.

## During-run send: queue vs steer (v0.8.8)
Two different things can happen when the user sends while a reply is generating, and they are not
interchangeable.
- **Queue** (`MessageQueueDelegate`, `QueueState`) — the message becomes the *next turn*, drained FIFO
  when the run ends. Works against every supported server, and is what steer degrades to.
- **Steer** (`SteeringDelegate`, `SteerState`) — the message goes into the reply *being written*, injected
  at the run's next tool boundary and announced back as `on_steer_applied`. Needs a server with
  `POST /api/agents/chat/steer` (`FeatureGatesState.steeringSupported`, a date gate — see VERSION_GATES.md).

`ChatUiState.effectiveDuringRunAction` resolves the user's Settings preference against
`canSteerNow` (existing conversation, not comparison mode, steer route present, no live HITL pause). The
composer never makes that call itself: its send button routes to `ChatViewModel.sendDuringRun()`, and the
picker beside it (`DuringRunSendMenu`, rendered only when both routes are open) calls `steerMessage()` /
`queueMessage()` explicitly.

**The button's face and its action must come from the same value.** `sendDuringRun()` switches on
`ChatUiState.duringRunSendTarget`, so the button's icon/label does too (`sendButtonModeFor`, extracted from
the composable so the mapping is asserted as a pure function). Deriving
the face from the *preference* instead is a bug that nothing catches: a live `ask_user_question` pause
overrides the preference, so the button read "add to queue" over a tap that answered the question. Behaviour
correct, label lying. `duringRunAction` in `ChatInputState` is now the picker's checkmark only.

**Invariant: no steer path may lose the user's text, duplicate it, or send text the user withdrew.**
Every rejection code, transport failure, and lost race re-homes the message into the follow-up queue —
*always* the queue, never the live-send path, because `runWhenSendReady` is allowed to REFUSE (no model
selected, readiness timeout) and a degraded steer has no composer left to put the text back into.
`enqueueSpec` self-drains once the run is over, so an ended run still sends immediately. This is why the
rejection codes are diagnostic only; there is no `SteerFallback` branch.

**Every re-home into the self-draining queue is announced** (`onQueuedAsFollowUp` →
`ChatViewModel.steersQueuedAsFollowUp` → `SteerQueuedSnackbarEffect`), and the chips carry a caption saying
a steer joins at the next tool step or is sent after the reply. A reply with no tool calls never offers an
injection point, so without these the chip would vanish and the words come back as a new turn. It is a one-shot
channel, **not** the `error` field: a clean Final drains the queue inline, and the drained send clears
`error` in the same dispatch, before anything renders it. The parked routes (`reclaimAborted`,
`reclaimLocalChips`, `reclaimParked`) and a refusal do not announce: they hold the queue, so "sent after"
would be untrue. For the same reason `ChatViewModel` drops the notice whenever the queue is already
paused when the steer lands in it (behind a refusal hold, or a Stop / stream-error hold on a non-empty
queue): `enqueueSpec` does not drain a paused queue.

A steer carries the `QueuedMessage` spec it *would* have become, minted at send time: rebuilding one at
failure time would capture whatever model, tools, and attachments the composer holds seconds later.

Three server reports hand back un-injected steers **claim-on-read** (the `final` frame, the abort ack,
`/chat/status`'s `unrecoveredSteers`) — parsing one and ignoring it destroys the words. That obligation is
**structural, not conventional**: `ChatRepository.checkStreamStatus` / `abortChat` take a required
`claimSteers` lambda and invoke it before returning, emptying the field on the value they return, so no
guard or early `return` at a call site can come between the read and the claim. The final frame's copy is
claimed at the event-dispatch site, outside `handleFinal`, whose early returns would otherwise skip it.
The same steer rides more than one report (a Stop gets the ack AND the aborted final), so re-homing is
deduped by steer id. A stream that dies on an error carries no report, so `reclaimLocalChips()` converts
the locally-held records instead; it deliberately does NOT run on `Finalized`, where the frame's own list
is authoritative and converting again would double-send any steer whose applied event was missed.

**`SteeringDelegate` keeps ONE record per steer** (`records: Map<id, SteerRecord>`, status
`SENDING | PENDING | CANCELLED | APPLIED | RECLAIMED`); `SteerState.pendingSteers` is a derived view of the
live ones. `clear()` is a **display** boundary, not a memory one — it clears the chips and KEEPS the
records:
- a mid-run reconnect runs `clear()` (`resumeStream` → `startStreamSession`) and the sync frame re-seeds
  the same steers by server id; dropping the records would strand their specs and the re-homed message
  would be rebuilt from the composer's current selection — the exact failure the specs exist to prevent;
- an `APPLIED` record is what stops a late 202 from re-homing text already in the reply;
- a `CANCELLED` record is what stops a stale sync frame from resurrecting a withdrawn steer;
- a `SENDING` record's POST outlives the boundary and needs its spec to degrade.

Settled records are tombstones, bounded at 32; live ones are never evicted. The map is confined to the
handle's Main-dispatched scope — do not add a `withContext` inside the delegate, and never mutate the map
inside a `handle.update { }` block (that is a `MutableStateFlow.update` CAS loop and may re-run).

Steering is text-only on mobile: a during-run send carrying attachments is routed to the queue, where the
existing upload/usage path already handles them. The during-run menu turns its Steer and Interrupt rows off
while attachments are staged, so neither promises something the tap won't do.

**Interrupt and steer** (`DuringRunAction.INTERRUPT`, `DuringRunSendTarget.INTERRUPT`) is a steer sent with
`preempt: true`: every no-loss rule above applies unchanged. Two fallbacks, each stated: to the queue when
`canSteerNow` is false (silent, like Steer), and to a plain steer when the 202 echoes `preempt != true`
(announced, `SteerNotice.INTERRUPT_UNAVAILABLE`). It never becomes Stop + send.
- The chip's interrupting look comes from the server (the 202 echo, an `armed: true`, or a report's
  `preempt`), never from the request. A reconnect's sync frame carries `preempt` on `pendingSteers`, and a
  known record takes the server's flag (higher `preemptRevision` wins; v1 sends none, so the latest report).
- "Interrupt now" on a waiting chip is `SteeringDelegate.arm` → `POST /chat/steer/arm`. One interrupt at a
  time (any chip interrupting or arming disables the others, it doesn't hide them); one attempt, bounded by
  `gates.steerArmConfirmationMs`; only `armed: true` changes the chip. Outcomes are `SteerNotice`s on the
  `steerNotices` one-shot channel, never `error`: the words are queued in every case.
- `on_steer_updated` is v2-only and never reaches mobile, so a withdrawn interrupt keeps its label.
- A tool the interrupt cancelled carries the server's `INTERRUPTED` sentence (`isInterruptedToolOutput`,
  mirror `steer-interrupted-tool-result`). It renders as "Interrupted by your message" in a neutral tone,
  an interrupted search keeps the generic card, and a group header counts it as cancelled, not failed.

## Composer chrome (what sits around the field)
- **Everything the composer draws around its field is one view-data holder, `ComposerChrome`**,
  built in `ChatScreen` and threaded through the `ChatComposer` expect, both actuals, `ChatInput` /
  `IosChatInput` and `ChatInputState.chrome`. A new row adds a field there, never a parameter to
  those five signatures: the iOS ones only compile in CI.
- **A vertical drag on the composer scrolls the thread** (`Modifier.passVerticalDragsToThread()` →
  `LocalThreadScrollHandle`). It watches on the Main pass, so a child that claims the gesture keeps
  it; the field and the `/` `$` picker don't carry it. A drag is ONE `scroll(MutatePriority.UserInput)`
  session, so `TailFollower` reads it as the user scrolling away: never one session per delta.
- Steers and the queue share one region capped at 168dp. Its scroll is enabled only while it
  overflows; past its edge a drag or fling goes on to the thread (`ThreadHandOffConnection`).
- Over the composer's transparent top, Material chip fills are opaque
  (`AdaptiveChipDefaults.overlay*ChipColors`). No composer chip has a caret.

### Attachment strip (files, picked skills, quotes)
- **What goes with the message is one list, `DraftItems`** (pure `draftItemsOf`). Upload progress
  stays out of it and out of `ComposerChrome`: the rings read `DraftUploadProgress` as they draw, so
  a progress tick re-maps nothing. The strip is one sideways-scrolling row, never a second row.
- **Removal, retry and an upload's outcome name one tray entry (`AttachedFile.key`), never the
  file**: the same content `Uri` picked twice is two entries.
- **One Undo is pending at a time** (`ComposerState.pendingDraftUndo`); every write that replaces the
  draft (`clearComposer`, `applyComposer`, the landing reset) drops it in that same write. A file
  still uploading is cancelled, with no Undo.
- **A failed upload holds Send through `UploadIntakeDelegate.withUploadGate`**, checked before and
  after the upload wait, on every send path. Not `SendBlockReason` (that opens the model sheet) and
  not `buildSendSpec` (a steer's re-home and an MCP App's message build there and must not be held).
- **Retry keeps the stored route only while the live selection still offers it**
  (`ChatUiState.retryUploadRoute`); otherwise it routes afresh.
- **iOS keeps each entry's picked data until it uploads** (`IosFileHandler.picked`), and
  `IosFileHandler.prune`, run after every tray or Undo change, is the one rule for what stays. An iOS
  thumbnail is the picker's small JPEG/PNG preview, never the picked bytes (often HEIC, which Coil
  can't decode on iOS). A String `uri` is an id, never loaded as an image.
- **During a queued edit, a recovered draft part belongs to the stashed draft**
  (`ComposerState.draftRestore`) and joins it when the edit ends. A queued message doesn't open for
  editing while the draft's files are attaching or uploading: their outcome lands on a tray entry
  the swap takes away.

## Chat Tools
- The user's own toggles: `enabledTools` and `selectedMcpServerNames` are global (stored in
  `SettingsDataStore`, carried into every chat). Web search and URL context are
  `ModelParameters.webSearch` / `urlContext`, so they belong to the chat: they last through model
  changes within it, every new chat starts with both off, and a reopened chat has web search as
  this device left it (`ConversationWebSearchStore`, next section). The Skills toggle is per
  conversation (`SkillsToggleDelegate`).
- Tool toggles and MCP servers live in the paged "+" options sheet (`ChatToolsSheetContent`),
  reached from the composer's `onOpenTools`. What is on shows on the composer's settings line (next).
- **The settings line** (`ComposerSettingsLine`) is view data mapped once: `settingChipsOf`
  (`util/SettingChips.kt`, pure) in the ViewModel's `uiState` combine (`SettingChipsMemo`). Its inputs
  are re-gathered only when a slice in `ChatUiState.settingChipSlices` is a new instance, so a new
  read in `settingChipInputs` adds its slice to that list. A tool shows only where its own gate is
  open (`ChatUiState.toolGateOpen`), in the sheet's `SETTING_TOOL_ORDER`.
  - **A tap on the line never turns a thing off**: a chip that is on opens the "+" sheet at its row,
    a pinned tool that is off turns on, a spec-locked one says who set it. Sign-in outranks a lock.
  - **MCP chips show the MCP servers page's status** (`mcpDisplayStatus` over the status map with
    discovery's lapsed authorizations folded in; `:core:ui` `mcpStatusLabel` / `mcpStatusColor`). They
    paint from the list and status routes and re-fold when the slower `GET /api/mcp/tools` lands,
    which is asked only while the chat shows the tool controls.
  - **Sign-in follows web's `useMCPServerManager`**: reconnect → `oauthUrl` → `LocalMcpSignIn`.
    **The app never cancels an OAuth flow by itself** (not on dialog close, browser return or run
    end): the server keeps one flow per user and server, and another run may be waiting on it. Only
    the chip's Cancel sign-in cancels. A reconnect waits for a cancel still in flight (the server
    would replay the flow being cancelled); while either is out, sign-in taps are ignored and those
    chips are disabled.
  - **The statuses reload when any sign-in ends, whatever the outcome**, after a cancel, and on every
    return to the chat (`McpServersDelegate.onForeground`): they change outside the chat.
- Thinking uses the brain glyph, Memory `AutoStories`, picked skills the sparkle (`AutoAwesome`),
  wherever they appear.
- URL context shows only on a Google parameter set and a model the server runs it on
  (`urlContextProviderGate`, `supportsUrlContext`: Gemini 2.5+, not image/live/TTS/audio). The server
  skips it elsewhere with only a debug log. Web shows it on every Google model; the divergence is deliberate.
- **Everything reads the tools in use, not the toggles**: `ModelSelectionState.effectiveTools`,
  `effectiveMcpServerNames` and `sendParameters` (the parameters with web search and URL context
  from the tools in use). The toolbar, the sheet and the request's `ephemeralAgent` go through
  them; web search on the wire goes through `ChatUiState.webSearchRoute` (next section).

### Web search: one control, two searches (#663)
LibreChat has two web searches: its own `web_search` tool (`ephemeralAgent.web_search`, needs a
search provider on the server, results in the sources card and citations) and the provider's
built-in search (the conversation parameter `web_search`: OpenAI's Responses API tool, OpenRouter's
`web` plugin, Anthropic's and Google's search tools). Web shows two controls. **Mobile shows one, on
purpose**: two web-search switches confused users. Don't add a second one, and don't "restore
parity" by sending both: the server runs both on OpenAI and custom
endpoints (`resolveProviderToolConflicts` drops the built-in one only on Anthropic and Google), and
moves a custom OpenAI-compatible endpoint to a Responses API it may not serve.
- **Each send carries exactly one** (`util/WebSearchRoute.kt`, `ChatUiState.webSearchRoute`):
  `ChatRequestBuilder` puts `ephemeralAgent.web_search` for `LIBRECHAT`, the top-level and payload
  `web_search` for `BUILT_IN` (`builtInWebSearch()`, read by the live send, the queued snapshot and
  `MessageEditingDelegate`). In order: off or a saved agent sends nothing (a saved agent runs its
  own tools and `model_parameters`); a spec's `webSearch` is LibreChat search and its preset's
  `web_search` the built-in one (the admin's choice, not the preference's; on an endpoint with no
  built-in search the preset's runs LibreChat search when it can, else nothing, except under
  `enforce`, where the server sends the preset on any openAI, azureOpenAI, anthropic, google or
  custom endpoint, so the route is the built-in search there); a reopened chat whose stored
  conversation has `web_search` on (web's parameters switch) keeps the built-in search the same way
  until the user turns the control off (`keepsBuiltInSearch`), since a send without it makes the
  server unset it (`getUnsetFields`; `excludedKeys` doesn't list it); a comparison uses
  LibreChat search when it can (the second model only gets the first's tools, `added.ts`); otherwise the
  user's Settings › Chat › Web search choice (`AppPreferences.WebSearchFirst`, LibreChat first by
  default), falling back to the other, else nothing and the control is hidden.
- **LibreChat search counts only when it will work** (`FeatureGatesState.librechatSearchReady`):
  the agents endpoint's `capabilities` include `web_search` (a missing list counts, as the server
  falls back to the defaults, and so does an empty one, which decodes the same), `/api/config` serves `webSearch` and
  `GET /api/agents/tools/web_search/auth` says `authenticated`,
  checked by `ChatConfigDelegate` on every config, account or role change (not asked at all when the
  role or `interface.webSearch` rules LibreChat search out) and false until answered. The answer is
  cached per account and config in `:core:data`'s `WebSearchReadinessRepository`, so only a process's
  first chat waits for it; a failed request (offline, 5xx) caches nothing and is retried (2s … 60s).
  An answer that lands after an account switch is dropped (it went out with the other account's
  token). Strict on purpose: the server adds its tool's definition without checking the keys
  (`loadToolDefinitionsWrapper`), so an unconfigured search fails when the model calls it, and on
  Anthropic and Google it also drops the built-in search. Keys each user enters themselves read as
  not set up (no key screen here). A wrong key or a provider that's down can't be detected.
- **While that check is out, a send that would run LibreChat search runs none**
  (`WebSearchRoute.CHECKING`, `librechatSearchChecking`; the caption reads "Checking LibreChat
  search…"), so the search a send runs never depends on how fast the check answered. A route that
  picks the built-in search isn't held. When the retries run out the hold lifts and the other search
  runs; the check keeps going once a minute, so a connection that comes back is noticed.
- **`interface.webSearch` rules out LibreChat search only.** The built-in search needs just the
  role's `WEB_SEARCH.USE` (`webSearchPermitted`; the server strips it without), as web's parameters
  panel offers its switch whatever the interface flag says.
- **Built-in search exists** on openAI, azureOpenAI, anthropic, google and a custom endpoint whose
  parameter set (`paramSettingsKey`) is `anthropic`, `google` or `openrouter` (the server serves
  `openrouter` for an openrouter.ai base URL) or whose name contains `openrouter`, which
  `getOpenAIConfig` still reads (`builtInSearchProvider`), unless `dropParams` names `web_search`.
  The control's second line names the search it runs ("LibreChat search", "Claude's built-in
  search"; `webSearchName`).
- The parameters sheet has no Web Search row (`hiddenKeys`); the registry keeps it for the payload,
  presets and the agent editor, where it is the agent's own built-in search.
- **Each chat remembers its switch on the device** (`ConversationWebSearchStore`, account-scoped,
  200 chats), as web keeps `LAST_WEB_SEARCH_TOGGLE_<id>`: a chat that ran LibreChat search sends no
  `web_search` the server could save, so a reopen would otherwise come back off. Written by one
  observer whenever it changes within a conversation, whoever wrote it (`rememberWebSearchChanges`),
  and when a new chat is created. A reopen reads it before applying the model and writes it with the
  restored parameters in one update, so the load's "user changed nothing meanwhile" check (which
  decides whether a backfill fetch replaces the cache) sees it. Not under a spec (a reopened spec chat
  starts from the spec's tools) nor in a temporary chat.
- **Presets** save what the chat shows (`sendParameters`): `web_search` true when the route is
  `BUILT_IN`, false when the control is off, and left out while LibreChat search is on (a preset is
  conversation fields and has no field for it, on web too), so loading that one keeps the control
  as it is.
  Under a spec, a loaded preset's `web_search` and `url_context` go into the spec's additions
  (`withSpecTool`), not the user's hidden toggles.
- **Known gap:** a spec with both `webSearch` and preset `web_search` under `enforce` shows
  LibreChat search, but the server sends the preset anyway, so OpenAI runs both. Under `enforce` a
  preset `web_search` on a custom endpoint without the Responses API fails the send, which the
  server forces either way.
- **A model spec drives the tools** (`util/SpecTools.kt`, registered in `scripts/mirrors.json` as
  `model-spec-tools` and `model-spec-tools-web`). The server ORs the spec's `webSearch`,
  `fileSearch`, `executeCode` and `memory` with the request's flags and adds its `mcpServers`, so
  under a spec:
  - The chat starts from exactly the spec's tools, as web's `applyModelSpecEphemeralAgent` does; the
    user's toggles are neither shown nor sent. The spec's own are on and locked (`lockedTools`,
    `lockedMcpServers`, carried in `ChatInputGates`). Its preset's `web_search` and `url_context`
    (`presetTools`, the provider's grounding parameters) start on too, as web copies the preset in;
    the user can turn those off, except under `enforce`, where the server sends the preset anyway.
  - The user can add tools. Additions are `SpecTools`, kept for the session only (never stored),
    named by their spec: they count only while that spec is selected, so any change of spec starts
    the next one clean whoever writes `choice`. `withChoice` and the preset load also drop them on a
    change of spec. They ride the queued-edit swap and the new-chat handoff; a reopened spec chat
    starts from the spec's tools. Web keeps them per conversation (a deliberate divergence).
  - Nothing a spec chat does writes the user's toggles, so leaving the spec brings them back. That
    includes the parameters sheet's URL context control (`updateModelParameters` routes it into the
    additions), a loaded preset's web search and URL context (`withSpecTool`) and a reopened chat's
    stored `web_search` (`reopenedParameters` keeps the current values on a spec).
  - `hideBadgeRow` hides the tool controls (`ChatUiState.toolbarShown`) and drops the additions;
    the request still carries the spec's own tools and artifacts, as web sends them.
  - `ephemeralAgent` is read from the body even under `enforce`, so nothing more is locked there.

### Per-turn thinking (v0.8.9-rc1 `reasoningOverride`)
- **What resolves it.** `ReasoningSettingResolver` (`:core:ui`) ports upstream
  `resolveReasoningSettingForTarget`, which the server also runs on the request: a key or level the
  client resolves differently is a 400 `invalid-reasoning-override`. It carries its own reasoning-key
  table (`SETTINGS`) rather than reading `EndpointParameterRegistry`'s lists, which differ from
  upstream's (Bedrock Anthropic's effort key). Every mirror is in `scripts/mirrors.json`.
- **When the chip shows** (`ChatUiState.reasoningSetting` → `thinkingChip`): server ≥ 0.8.9-rc1,
  `interface.parameters` on, not assistants, not a comparison, a resolved target (a saved agent's
  provider and model, fetched with its provider), a key an enforced spec's preset doesn't set, and
  levels only: a `thinkingBudget` model gets no chip. `hideBadgeRow` does not hide it.
- **OpenRouter catalog.** An endpoint whose efforts come from the catalog (`usesCatalog`) offers
  none until `ReasoningCatalogDelegate` has it; it refetches at the server's `expiresInMs` and
  retries failures. A model the catalog doesn't list keeps the generic list.
- **One message only.** `ComposerState.pendingReasoningOverride` is taken by a fresh send or a
  composer-origin queue (`takePendingReasoningOverride`, which drops one the selection refuses) and
  rides the `QueuedMessage` (and the server queue's enqueue body). A staged choice makes a mid-run
  send queue, never steer (`canSteerNow`). A model switch drops it only once the new setting is
  known and refuses it. Edit, regenerate and continue send the user message's own
  `reasoningOverride` (cached in Room, DB 14), not the pending one; nothing shows it on the bubble.

## Code workspace: attached machines (`CodeWorkspaceDelegate`)
An agent with stateful code sessions can run files and commands on the user's own machine (the
`librechat-code` worker). The chat's choice of machine and workspace is a **decision the server seals
with the first coding turn**: a new chat that sends neither `codeEnvironmentMode` nor `codeWorkspaces`
is sealed "No workspace" for good. The rules are `:core:model` `code/CodeWorkspaceResolver.kt` (web's
`useCodeWorkspace`, mirrored); the delegate gathers inputs, polls, and keeps the draft; the composer
only draws `CodeWorkspaceUiState` (mapped in `util/CodeWorkspaceView.kt`), through
`LocalCodeWorkspaceComposer` so both platform composers draw it.
- **The workspace lives on the settings line, never as a card above the composer**: its chip first,
  then the approval chip, in every state. What needs the user is a notice under the field
  (`CodeWorkspaceNotice`). `CodeWorkspaceUiState.visible` (needs the user) is not web's `visible`;
  polling still follows the resolver's.
- **Gate on the fields, never the version** (VERSION_GATES.md): `RUN_CODE.USE`, the agents endpoint's
  `execute_code` + `stateful_code_sessions` capabilities, an `attached` machine in
  `statefulCodeSessions.environments`, a reachable agent that routes code there. Capability versions
  are `==` checks. A plain agents chat sends neither field.
- **Agents come from the list** (`ModelSelectionState.agents`, the in-memory list cache): only that
  route projects `code_environment_id(s)`, `tools`, `agent_ids`, `edges` and `subagents`.
- **What a send carries is `submissionForSend()`**: the resolved decision on a new or undecided chat,
  nothing on a chat that holds one (the server applies it; resending could only be `locked`), null
  while the row holds the send. A queued spec snapshots it at queue time. Deliberate divergence: with
  workspaces to pick and nothing picked the send is held (web sends "No workspace", which the phone
  cannot undo); with nothing to pick it sends "No workspace" and says so (`CODE_WORKSPACE_SENT_WITHOUT`).
- **The saved decision is read live** (`CodeEnvironmentRepository.savedDecision`, no Room), only when a
  coding agent is in the chat, and again whenever a run ends. Read the live value before re-fetching:
  `combine` can hand the transform a superseded `Pending`, and re-reading on it never settles.
- **Status polling** every 10 s only while the row is visible and the app is in front (`onPause` /
  `onResume`), 30 s after a 503/504, never again after a 404. One read is always made when the set of
  machines changes. The poll target is compared before launching: on an immediate dispatcher the
  launch runs inline and re-enters `retarget`.
- **Rejections** (`code_workspace_unavailable`) carry their `reason` in the marker
  (`StreamErrorType.ARG_SEPARATOR`); `required`/`missing`/`worker_unavailable` also refresh status and
  tint the workspace chip.
- **Transitions** (web's move / attach / detach / recover, `Resolution.transition`): a locked chat's
  workspace chip opens the transition sheet instead of refreshing. Picks only draft the change
  (`TransitionDraft`, per chat); Confirm or "Continue without workspace" writes it
  (`PATCH …/decision`, `CodeEnvironmentRepository.moveDecision`), and the answer becomes the saved
  decision. While it is in flight the send waits (web's `replacingDecision`). A `locked` refusal or an
  unknown outcome re-reads the decision; busy and other refusals only say so (`ChatNotice`
  `CODE_WORKSPACE_MOVE_*`). With the transition version a healthy saved chat offers Detach from its
  folded chip; web shows it as a visible row.
- **Reconcile after a failed turn**: a send that carried a decision and ended in an error (a `ChatNotice`
  marker is not one; the flag clears when the run ends either way) re-reads the
  saved decision with Send held and "Confirming this chat's workspace decision…" (`CodeConfirming`);
  a failed read offers Retry (`refresh`) and keeps holding, rather than sending nothing (which could
  seal a chat the server never saved). A 404 on that read means the chat was never saved: undecided.
- **Several machines**: per-chat machine choice can put different agents on different machines; each
  pick on an agent's alternative carries its `agentIds` (`selectWorkspace`). The chips then read
  "{n} machines" / "{n} code workspaces", explicit checkouts get a line each, and picks that leave an
  agent uncovered show web's `graph_selection_conflict` line. "Linked worktrees" shows for a
  `git_linked_worktree` workspace that is not isolated (Auto on a worktree-capable one isolates).
- **Code approval mode** (`CodeApproval` in `:core:model`): the chip is on the settings line, icon-only
  on "Ask before changes" while other modes are on offer, so a mode that does more shows its name.
  `submissionForSend()` adds `approvalMode` to the submission, so the mode
  rides every send path the decision does; `ask` without a workspace. The mode asked for is this
  chat's pick, else the saved decision's, else (no decision yet) the spec preset's `codeApprovalMode`,
  else the device's last pick (`requestedApprovalMode`, web's `conversation.codeApprovalMode ?? preference`). A follow-up whose mode is not
  `ask` stays in the local queue (`QueueOrchestrationDelegate.serverOwnedSpec`): the v1 queued-turn
  route refuses a mode.

## Content Cards
- **File tools** (`FileToolCalls.kt` + `FileToolCallCard.kt`, web `ReadFileCall` / `FileAuthoringCall`
  / `DiffView`): `read_file`, `create_file`, `edit_file`, routed by EXACT name in both
  `ToolCallDispatcher` and `StreamingToolCallCard` ahead of the `contains` branches. Titles "Reading /
  Read <name>", "Creating / Created" ("Updated" when the output opens `Updated `), "Editing / Edited";
  the model's `intent` wins, a call without a path reads as the tool's name. Behind the chevron: the
  output highlighted by `langFromPath` (read), the authored contents (create), or a diff — the
  output's own unified diff when `hasDiff` finds a header after a line break (numbered from its hunk
  headers; file headers before the first hunk dropped), else `edits[].old_text/new_text` from the
  args as removals then additions with a rule between edits. Only output is ever parsed as diff text.
  The tool's summary follows when it differs from what the pane shows. Live, the args are usually
  unknown until the completion (no argument deltas), so the card names the file only then; a resumed
  or reopened running call has them. The partial-JSON reader (`parseJsonField`, web's) is ported for
  snapshots that carry cut args. Tool cards are outside `SearchMatchEnumeration`, so nothing here
  counts toward find-in-page.
- **Code card extras (v0.8.9-rc1).** The completion's `executor` rides `ToolCallComplete`, so the live
  card reads an attached command's exit trailer as the saved one does (`withAttachedCommand`). The
  trailer shows dimmed after the output; after a timeout it hides behind Details / Hide under "Time
  limit reached. Output may be incomplete." (web `BashCall`), carried across the live → saved handoff.
- **PTC trace** (`PtcToolTrace.kt`, web `PtcToolTrace` + `common/ptc.ts`): the inner calls of a
  programmatic tool-calling program, `on_ptc_tool_call` → `StreamEvent.PtcToolCall` →
  `MessagesState.ptcTraces` (folded in `LiveReplyDelegate`, `util/PtcTrace.kt`: running appends,
  a settle updates its row by `call_id`, a settle without its row is dropped, 100 rows + a dropped
  count; a resume marks running rows interrupted). Never persisted by the server, so it is read
  through `LocalPtcTraces` by the live and the saved code card alike, and outlives the stream. It is
  cleared when the next turn starts (web keeps it until the conversation changes, keyed by message +
  call id); clearing per turn is what keeps a provider's reused `call_0` from drawing an earlier
  program's rows under a later card. Visible on the card, not behind the code chevron, since the
  running card has no code to expand. Names go through `parseToolName` (`ToolLabels.kt`, web
  `toolLabels.ts`): `server · tool` for MCP (split at the last `_mcp_`; no server list), else the
  friendly name.
- `ImageGenCard` renders DALL-E / image generation results (spinner while generating, AsyncImage when done)
- `LogContentCard` renders expandable log output blocks with monospace text
- Both dispatched from `ContentPartRenderer` — tool calls with name containing `dall` route to ImageGenCard

## Artifacts
- `ArtifactDetector` is a **line scanner**, not a regex — ported in shape from upstream's
  `findArtifactClose`/`getOpeningCodeFence` (`packages/api/src/artifacts/update.ts`), because the real
  contract is "whatever micromark parses", not a pattern. It accepts backtick **and** tilde fences of
  any length ≥3 (closing fence `{n,}`, i.e. *at least* as long), unfenced content, leaf directives
  (`::artifact{…}`), CRLF, `[label]` between name and attributes, and quoted/unquoted/valueless
  attributes. `groupArtifactVersions()` groups by identifier.
- **Three divergences from the web client are deliberate** — do not "fix" them: `::: trailing` closes
  the artifact here (web swallows the rest of the message; upstream's own two implementations
  disagree), a tab-indented content fence renders here (web emits garbage), and attribute-less
  `:::artifact` produces nothing (web paints no card either — `updateArtifact` early-returns on the
  all-defaults key). A 25-case differential corpus pins all of this in `ArtifactDetectorCorpusTest`:
  **22/25 agreement, divergent set exactly {10, 23, 25}**. Any *other* row diverging is a regression.
- **`Artifact.isComplete`** is false when the closing `:::` never arrived — truncated mid-write, or
  still streaming. Incomplete artifacts render their **source** via `IncompleteArtifact` → `CodeBlock`,
  bypassing the inline prefs entirely, and they *do* count for in-conversation search. Both rules are
  load-bearing: every inline pref defaults to off, so the normal path would collapse them to a button
  and take partial content off screen, and half-written markup in a WebView is a blank box. An
  unclosed directive with **no** opening fence is *not* emitted at all — that's a prose mention, and
  emitting it would swallow the rest of the reply.
- **A message with no content parts still gets the artifact split** when it is the assistant's
  (`Message.textFallbackSplitsArtifacts`): replies saved before LibreChat v0.7.9 by the classic
  endpoints have no `content`, so their artifacts live only in `text`. A user's own text is never
  split, as on web. The renderer, `countMessageOccurrences` and the Artifacts tab all read that one
  predicate.
- **While streaming, complete artifacts are gated to `ArtifactButton`** even when an inline pref is
  on (`shouldRenderInlineArtifact`): the segment subtree recomposes per delta, and an inline preview
  reloads its WebView on every content change (mermaid recreates its view outright). The real
  preview mounts once, at settle, where the streaming→final swap replaces the subtree anyway.
- Supported types: `text/html`, `image/svg+xml`, `application/vnd.react`, `application/vnd.mermaid`, `text/markdown`/`text/md`, `text/plain`, `application/vnd.code-html`
- `MermaidWebContent` renders Mermaid diagrams with zoom controls and dark theme
- `MarkdownWebContent` renders Markdown (marked + highlight.js) with GFM and syntax highlighting
- HTML/React/SVG templates include Tailwind, theme CSS vars, and error handling
- React artifacts compile in-browser (Babel) and load as a real ES module; the artifact's `import`/`export` run against an import map the runner builds over the bundled React/ReactDOM globals and the vendored npm bundles. The only rewrite is of shadcn import paths, on Babel's AST (see "npm imports use the same bridge")
- `ArtifactPanel` supports fullscreen Dialog mode, version switching, loading indicator, and WebView error overlay
- `ArtifactButton` shows type-specific icons and subtitle (e.g. "Mermaid Diagram", "React Component")
- `ContentPartRenderer` wires `groupArtifactVersions()` to pass version lists to ArtifactButton/ArtifactPanel
- `ArtifactVersionNav` provides prev/next arrows with "v2/3" indicator
- `ArtifactDownloadHelper` shares artifacts via FileProvider temp file + system share sheet. Maps 25+ language extensions including `.mmd` for Mermaid. Sanitizes filenames to 100 chars
- **Gotcha**: FileProvider authority must match app's declared authority in AndroidManifest

### Everything a WebView executes is bundled (no CDNs)

Every script and stylesheet these renderers load ships inside the APK and the iOS bundle.
Nothing is fetched at render time. That is a **hard requirement**, not a preference:
F-Droid's inclusion policy rejects apps that download executable code without explicit
opt-in consent, and a `<script src="https://cdn…">` in a WebView is exactly that. It is
also why the CSPs in these documents name no remote origin — `img-src https:` is the one
deliberate exception, because a remote `<img>` in an artifact is content the user asked to
see rather than code the app chose to run.

**MCP Apps are not covered by this rule and do not change it.** An App is third-party code its MCP
server supplies at run time, and its declared CSP may let it load more. The app itself bundles
nothing for it (the relay page loads no script) and keeps it in an opaque sandboxed frame; see
"MCP Apps". None of the renderers above may take a remote script on the strength of that.

What is vendored, at which version, and why each pin sits where it does is in
`scripts/web-assets.json`; `scripts/vendor-web-assets.py` moves the bytes and CI runs
`--check` against a sha256 lock. Repin through **`/update-web-assets`**, never by editing a
vendored file. ~3.2 MB compressed in the APK: mermaid and Babel are about half, the artifact
module bundles (below) about 0.7 MB.

**How a page finds its assets.** Each document references its scripts *relatively*
(`katex/katex.min.js`), so the only platform-specific part is the document base URL, from
`webAssetBaseUrl()` (`components/web/`). Android resolves Compose Resources in place and
serves `file:///android_asset/…` — which stays readable with `allowFileAccess = false`, so
that setting stays off. iOS **cannot**: WKWebView will not load local subresources for a
page passed to `loadHTMLString`, whatever base URL it gets, so the assets are copied out of
the read-only framework bundle into caches on first use and pages are written next to them
and loaded via `loadFileURL(…, allowingReadAccessTo:)` (`loadVendoredHtml`). The generated
`VendoredWebAssets.FILES` manifest is what tells the copier what to copy, and the copy's
completion marker is written last so a crash mid-copy redoes it rather than trusting it.
Both hosts render nothing until the base URL resolves: a page loaded against a wrong base
renders unstyled and scriptless instead of failing.

**React's module problem.** React 18 publishes no browser-ready ESM — only CommonJS and
UMD — which is the entire reason this used to resolve `import 'react'` through esm.sh. The
UMD builds load as plain scripts and the runner generates small blob modules re-exporting
`window.React` / `window.ReactDOM`, then injects an import map pointing at them *before*
the first dynamic import (hence the classic-script-wrapping-an-async-IIFE shape — a
`type="module"` runner would have resolved its own imports too early).

**npm imports use the same bridge.** Web's Sandpack offers artifacts a fixed set of npm packages
(upstream `standardDependencies`; lucide-react, recharts, three, date-fns, react-router, shadcn/ui
and its Radix base, ...). Those are vendored as `bundle` assets: esbuild packs each into one
**classic** script (`scripts/artifact-modules/build.mjs`) that registers module namespaces on
`globalThis.__switchboardModules`, and the runner re-exports them from blob modules like React.
Not ES-module files: Chromium fetches module scripts in CORS mode and `file://` is not a CORS
scheme, so with file-from-file access off, `import 'file:///…'` is refused. A bundle never
contains React (every `react` import inside resolves to `window.React`, or hooks break).
- `ReactArtifactImports.plan` decides per specifier: React core, a bundle (its `<script src>`
  goes in the page, only when imported), a stub that throws naming it (not bundled, or declined),
  or left to the browser (relative files, URLs). The tables it emits are embedded in the runner,
  so every string in them passes a charset guard first.
- **shadcn/ui is imported by path** (`/components/ui/button`, as web mounts it). A path can't key
  the import map from a `blob:` module, so a Babel visitor rewrites those import sources to
  `@switchboard/shadcn/…` keys (AST, never text). `/`, `./` and `@/` spellings all resolve.
- Babel runs the **typescript** preset as well as react (web's template is `react-ts`); it also
  drops type-only imports, which bundles don't export at runtime.
- **Flagged libraries ask first.** A bundle with an `advisory` (unmaintained, deprecated or
  vulnerable: dat.gui, vaul today) is gated by `ArtifactLibraryGate`, inline and fullscreen: Load or
  Don't load, optionally remembered device-wide (`ArtifactLibraryConsentStore`, `:core:data`); an
  unremembered answer holds for that artifact until restart. A remembered answer is keyed by
  library *and* advisory kind, so an escalated advisory asks again. Once answered, a row above the
  preview says what loaded and what didn't; its Change takes back the answers that apply in that
  artifact. Settings > Chat > Artifacts > Flagged libraries sets the remembered answer for each
  library (Ask / Load / Don't load); it lists `FlaggedArtifactLibraries`, which
  `vendor-web-assets.py` generates into `:core:data` because Settings can't read this module's
  manifest. `VendoredAssetReferenceTest` checks the two name the same subjects.
- **Consent fails closed.** `buildHtml` takes `allowedLibraries` (empty by default), and every
  flagged library not in it is stubbed. A host that renders React artifacts without the gate
  therefore loads none of them. Each flagged library is its own bundle, so not loading it keeps
  its code from running at all; keep it that way when adding one.
- Anything outside the set still fails by name ("not bundled"), including three's addons and
  date-fns locales. `VendoredWebAssetInstrumentedTest` renders one artifact per bundle on a device.
- **Known gap, HTML artifacts:** the server's prompt lets them load scripts from
  `cdnjs.cloudflare.com`, which our CSP blocks, so such a page renders without them. Closing it
  needs an opt-in consent for remote code (F-Droid allows that only with explicit consent), not a
  CSP edit.

**Two things this replaced were silently broken**, which is the argument for pinning in
general: markdown syntax highlighting had never worked (marked deleted its `highlight`
option in v5 and ignores one passed to `setOptions`; separately the CDN URL served
CommonJS that cannot define `hljs` in a browser), and the unversioned Babel URL had drifted
onto a major version nothing here was tested against. Neither broke a build or a test.
`VendoredAssetReferenceTest` now pins both halves — no remote origin, and every referenced
path exists in the manifest.

### Office previews: we rewrite the server's document (deliberate divergence from web)

The server builds DOCX/PPTX previews as complete HTML documents
(`packages/api/src/files/documents/html.ts`) whose `<script src>` tags load renderers from
`cdn.jsdelivr.net` and whose CSP allows that host. The web runs that document unchanged in a
sandboxed iframe. **We don't**: before it reaches the WebView, `localizeOfficePreviewHtml`
(`artifact/OfficePreviewLocalizer.kt`, applied in `buildOfficePreviewHtml`) points the exact
upstream URLs in `VENDORED_OFFICE_RENDERERS` at the bundled copies (jszip, docx-preview),
strips every other remote script, link and `@import`, and replaces the server's CSP with one
naming no remote origin. So the page the user sees is not the page the server sent.

When an office preview misbehaves, start here:
- **"High-fidelity renderer unavailable" on a DOCX** = the server's pinned URL no longer
  matches `VENDORED_OFFICE_RENDERERS` (an upstream bump), so the tag was stripped and the
  document fell back to its mammoth HTML. `scripts/mirrors.json` watches `DOCX_PREVIEW_CDN` for
  exactly this; repin through `/update-web-assets` and update the map in the same PR.
- **PPTX always shows the slide-list fallback.** pptx-preview is not FOSS (its README forbids
  republishing), so it is never bundled. That is not a bug to fix by loading it from the CDN.
- **Something the server's renderer needs is blocked** (fonts, media, a new script) = our CSP,
  not theirs, is in force. Compare `OFFICE_PREVIEW_CSP` with the server's before loosening it.
- The document also passes through `buildEnhancedHtml`, which adds its own CSP and the Tailwind
  reset on top of the server's styling — a layout difference from web can come from there.

## Media Players
- `VideoContent` uses ExoPlayer (media3) on Android, AVPlayer on iOS — 16:9, released on dispose
- `AudioContent` decodes base64 audio and plays it via ExoPlayer on Android, AVAudioPlayer on iOS
- **Gotcha**: Both players must release resources on dispose — use `DisposableEffect`

## Speech providers: package visibility (#601)
Dictation and read-aloud use whatever recognizer and TTS engine the user has set, which is only
reachable if `src/androidMain/AndroidManifest.xml` declares its `<queries>` intent
(`RecognitionService`, `TTS_SERVICE`, `RECOGNIZE_SPEECH`). Google's recognizer and TTS engine are
force-queryable, so on a phone with Google a missing entry changes nothing. On a ROM without Google
(GrapheneOS with Whisper+), the recognizer is invisible and the mic says "not available". A Pixel
can't test this. `scripts/verify-speech-services.py` can: it boots a no-GMS AOSP emulator, installs
`:testing:speech-fixture` as the default recognizer and TTS engine, and runs the `audio/` speech
instrumented tests. Those tests skip anywhere else.

## Upload to Code Environment (legacy upload UX)
A deployment with `fileConfig.legacyFileUploadUX: true` (global or per endpoint, resolved by
`resolveEndpointFileConfig` for `fileConfigEndpoint`) keeps web's destination chooser, where web
offers "Upload to Code Environment" (`useAttachItems`). Mobile adds one attach card, Code
(`ChatUiState.codeUploadOffered`): that flag, the `execute_code` capability, RUN_CODE.USE (web leaves it
to the server, which refuses the upload without it), and either ephemeral tools or a saved agent
whose `tools` include `execute_code`. Its picker takes any type; every pick goes as
`UploadRoute.CODE` (`tool_resource=execute_code`) with no routing sheet, images included, and a retry
keeps it. On a chat with ephemeral tools the pick also turns the code interpreter on (web sets
`ephemeralAgent.execute_code`; mobile's toggle is the global one). The routing sheet never offers CODE.
Not ported: web's file search destination and the rest of the legacy chooser. iOS: wired, unverified.

## Recovering from a failure the files cause (v0.8.9-rc1)
A reply that failed (a persisted failed turn, or an `error` part) on a branch carrying files gets a
card under it (`AttachmentRecoveryActions`, web's `recovery.tsx`): why, then **Retry without files**
(the failed turn again as a new sibling: no files, its quotes, skills and reasoning choice kept;
`MessageEditingDelegate.retryWithoutFiles`) and/or **Branch without files** (fork `DIRECT_PATH` at the
reply before the failed turn with `excludeFiles`, stage the turn's text as the copy's draft, open it;
`ConversationActionsDelegate.branchWithoutFiles`). The decision is the pure port in
`util/AttachmentRecovery.kt` (mirrored, see `scripts/mirrors.json`), computed once per displayed
branch in `MessageList` and read by each bubble through `LocalAttachmentRecovery`.
- **A copy that still has files is not a recovery.** An older server ignores `excludeFiles`; the
  response's messages are checked and such a copy is reported, not opened. Branch and the fork sheet's
  "Leave out files" are gated on rc1 besides (`forkExcludeFilesSupported`, VERSION_GATES.md).
- The success snackbar is handed to the copy's screen by conversation id (`ChatNoticeHandoff.put(…,
  conversationId)`): the screen being left could otherwise take it in the instant before it is replaced.
- Fork and feedback are withheld on `message.error` rows (web's `HoverButtons`): a copy would carry the
  provider's error as an answer. Fork failures are `ChatNotice.FORK_FAILED` / `FORK_RATE_LIMITED`
  (429), never the server's text.

## Redacted user turns: the author's original (v0.8.9-rc1)
A server with PII protection stores a user turn filtered (`text` = `[EMAIL]`…) and stamps
`privacyRevision`. Its author sees the original (`PrivateTextBody`, web's `PrivateText`), read by
`OwnerTextDelegate` from `POST /owner-text` once the chat is not streaming, in batches of 50.
- **The original lives in `MessagesState.ownerTexts` only.** Never Room (only the
  `privacyRevision` marker is cached, DB 14), never the search index, drafts, exports or share links;
  Copy, Edit, Regenerate, Read aloud and Fork keep `message.text`. A conversation switch clears it.
- **Accepted only for the stored turn it was read against**: same revision, same filtered text
  (`ownerTextStateFor`). An answer with no row for an id is no state (a client-minted id the server
  never adopted); a failed request or a row without text is Unavailable, with Retry.
- The live turn is marked from the `created` frame (`StreamEvent.Created.privacyRevision`), in memory,
  node for node, so it says its details were hidden without breaking the streaming anchor; its text
  is what was typed. `isServerCopyOf` matches a redacted server copy by author and parent, since its
  text never equals the typed text.
- Search counts `message.text`, so the query is not applied while the original is drawn.

## Feedback: rating + reason tag + comment
- **Both** thumbs open `FeedbackTagSheet` — the route validates against an object schema whose
  `tag` is REQUIRED, so a bare rating is rejected 400 and there is no one-tap path. Tapping the
  already-filled thumb clears instead (`onFeedback(null)` → `{}`, which the route's `feedback ==
  null` guard reads as a clear).
- The wire shape is `MinimalFeedback` (`{rating, tag, text?}`), deliberately a different type from
  the read/persist model `Feedback`: neither `rating` nor `tag` carries a default, so
  `encodeDefaults = false` cannot drop them from the body. `Feedback.rating` *does* default (to
  `FeedbackRating.UNKNOWN`) so `coerceInputValues` absorbs a rating from a newer server — it rides
  on `Message`, and a throw there fails the whole `GET /messages` decode.
- `Feedback.tag` stays a raw `JsonElement`: the validated route persists a bare key string, but
  rows written before it can hold the full tag object.
- The Room write goes through `feedbackColumnValue` in `MessageMapper`, beside the decode that
  reads it. The write used to store a bare rating string into a column that file round-trips as
  `Feedback` JSON, so every read threw into a catch that returned null and the thumb emptied
  itself on the next emission.
- `submitFeedback` is gated on `!isStreaming` like `switchBranch`/`rerunEdit`/`regenerateMessage`
  — it caches to Room, which would re-emit through the `loadConversation` observer and un-truncate
  the streaming anchor. **The affordance is gated too** (`LocalFeedbackEnabled`, provided by
  `MessageList`): the thumbs are *disabled* — not hidden, which would reflow the action row —
  while streaming. Both are required. A sink-only guard sits at the end of a multi-step flow, so
  the user would pick a reason and type a comment before anything refused; an affordance-only gate
  would leave the Room write unprotected against the next caller. `FeedbackTagSheet` reads the same
  local and disables Submit if a run starts while the sheet is already open (`onResume` adopting
  another client's run, a queued message draining) — the sheet stays up and keeps the draft rather
  than dismissing, so Submit re-enables when the run ends. Note the sibling mutations
  (`switchBranch`, `regenerateMessage`, `continueGeneration`) do NOT gate their affordances — their
  arrows and buttons stay live mid-stream and silently no-op.
- The sheet is a `ModalBottomSheet` of radio rows, not chips or an `AlertDialog`: `FilterChip`/
  `InputChip` hardcode `Role.Checkbox` over any caller-supplied role, and `AlertDialog`'s text slot
  has no scroll modifier, so eleven reasons plus a comment field clip out of reach.

## Activity groups, steers, and content segmentation (v0.8.8)
`groupContentParts` (`util/ContentSegments.kt`) is ONE pure transform producing both the steer
segmentation and the activity grouping, memoized at `MessageContentAndActions`. Two passes over the
same list would eventually disagree about a boundary and render the user's words inside a collapsed
tool block. It is pure so its boundaries are asserted directly.

- **Activity groups.** Consecutive reasoning + tool calls fold under one header, terminated by an
  `activity_label` part. A blank label is a *reservation* — invisible, and it only moves the claim
  boundary so a later filled label cannot reach back past it. With no label at all the block
  re-splits into the legacy shape (reasoning standalone, runs of ≥2 tools grouped), so a server
  without the feature renders exactly as before.
- **The header's verdict is `activityGroupOutcome`, joined with attachments at the render site**
  (web's `outcome.ts`, #16949). Each call is judged by `toolCallOutcome` as its card judges it: the
  run step's terminal status, a refused memory write (`isFailedMemoryToolCall`), error-shaped output,
  an attached-workspace `bash_tool` whose trailer reports a non-zero exit (`attachedCommandFailed`,
  only when the server stamped `executor`), a background-task poll whose task failed or stopped.
  The header turns red only when **every** call failed or the label says `failed`; mixed outcomes
  keep it neutral and add "n/N failed" / "n cancelled". A `partial` label is mixed by definition
  and shows a warning glyph only when the per-call verdicts can't corroborate it. Not counted: a
  backgrounded call's `background_task_status` error attachment.
- **Live cards say "Preparing {tool}"** while the model writes a call's arguments
  (`on_tool_preparation` → `ActiveToolCall.isPreparing`, cleared by `on_tool_calls_dispatched`, a
  result or a close). Generic card only; MCP and action tools keep their name. Upstream also reads
  incomplete arguments as preparing; this client doesn't track argument deltas, so it doesn't.
- **Group identity is anchored to the first TOOL CALL, never the first part.** A label absorbs the
  block's leading `THINK` when its text lands, so `parts[0]` flips at the instant the block becomes
  a group; keying on it remounts the group and drops the user's expansion.
- **Auto-collapse is latched once per group id**, and suppressed entirely on the message that just
  took over from the streaming bubble — the live tool cards vanish in that same swap, and folding
  the same calls in the same frame drops the reply's height by the whole stack.
  **The suppressed message is named by the ViewModel, not derived in the UI**
  (`MessagesState.justSettledMessageId` → `MessageList` → `LocalSuppressGroupAutoCollapse`).
  `finalizeChatDisplay` writes it in the SAME atomic update that swaps the message in, so the flag
  is already true the first time the finalized message composes. Both UI-side derivations fail:
  a `LaunchedEffect` on `isStreaming` commits *after* the composition that registered it, so the
  groups have already collapsed and nothing re-opens them; deriving during composition instead
  marks the last message of every *opened* conversation as freshly settled and keeps its groups
  open forever. Only a finalize writes it, which is what makes it a transition.
  **It is deliberately NOT cleared at the turn boundary.** A drain of a non-empty queue re-enters
  `beginStreaming` inline in the *same* Main dispatch as the finalize — nothing on that path
  suspends, because `awaitReplySettled`'s predicate is already true and `viewModelScope.launch`
  on an already-Main dispatcher runs inline — so a clear there lands before Compose ever sees the
  flag. Persisting is safe because the value is a message id: it can only re-match the one message
  it named, and the next finalize overwrites it. `ActivityGroup` latches the suppressed decision
  as well as the collapsing one, so a group cannot simply fold later when the flag moves on.
  A live comparison never reaches `finalizeChatDisplay` (it rebuilds from a background reload), so
  `SendCompletionDelegate` calls `markSettled` on that branch.
  Guarded by `JustSettledMessageTest` (single-emission, mount trap, overwrite-without-clear) plus
  `ChatViewModelDuringRunSendTest`'s drain case, which is the only place the inline-drain
  interleaving is visible. Each assertion was verified to FAIL on the corresponding broken version
  — three earlier attempts at this mechanism compiled and passed every gate while doing nothing.
- **Steers.** A `steer` part renders as a user turn where the words entered the run, and each
  segment resuming after one restates attribution. Attribution follows the agent that had taken
  over when the steer landed; a handoff AT the resume point keeps the pre-handoff author so the
  marker announces the transition itself. Only the id is on the wire for a handed-off agent, so it
  renders under a neutral badge rather than the previous agent's avatar.
- **Per-part collapse state is keyed and saveable** (`stateKey`, threaded through
  `ContentPartRenderer`). It was positional `remember`: grouping shifts children under a wrapper,
  which migrates an expanded thinking block to whichever part now occupies its slot, and lazy-item
  disposal dropped it on scroll.
- Steer text and rendered activity labels are counted by `SearchMatchEnumeration`, so in-conversation
  search can reach them and a collapsed group holding the focused match opens.

## Subagent files with no owner yet (v0.8.9-rc1, slice 1)
A tool inside a subagent saves its files under the CHILD call's id, which only the subagent's
transcript names. After a mid-run resume there is no transcript (run steps are not replayed), and the
live list never draws child calls at all, so such a file matched nothing. `withSubagentFilesAdopted`
(saved messages, claimed = `outputToolCallIds`) and `withLiveSubagentFilesAdopted` (the streaming
reply, claimed = the live call ids) rewrite an unclaimed stored file's `toolCallId` to the message's
`subagent` call: the only one, else the one whose agent stamped the file (a saved call's agent is
its `subagentIdentity.subagentAgentId`; a live one's comes from its trace), else the last. Rewriting
(rather than a side map) is what keeps `unclaimedOfficePreviewRows` from drawing the same file again.
Metadata attachments (memory, search) never move.

**Slice 2 (`GET …/subagent-calls`, web's `useStoredSubagentHints`).** For a displayed saved message
holding a previewed subagent call (`subagentContentOmitted`) and stored tool files no call claims
(`subagentFilesInDoubt`), `SubagentHintsDelegate` asks the server which child call each was saved
under (batches of 32, once per file set, in memory only). `ToolPartOverlay` adds the answer to the
matching call's `subagentAttachmentCalls` (`withStoredSubagentHints`, after hydration), so the file
is claimed by the exact subagent and slice 1 never sees it. A failed lookup keeps slice 1's placement
and is asked again on an offline → online transition (3 attempts). No version gate: only an rc1+
server previews, so the route exists whenever the condition holds; a pre-rc1 server sends full
transcripts and needs no lookup.

## Tool-call previews (v0.8.9-rc1, `?toolPreviews=1`)
The chat load asks for bounded previews of settled tool calls; a previewed call carries
`AgentToolCall.isPreview` and its full lengths. Rules that keep a preview from passing for the whole:
- **Fetched parts never touch the message rows.** `ToolPartHydrationDelegate` holds them in memory
  (cleared on a conversation or account switch, ~4M-char budget that never evicts a part an open card
  holds) and keeps those of **finished** messages in the `tool_call_parts` side table
  (`MessageRepository.storeToolCallParts`; see `core/data/CLAUDE.md`). The message row stays what the
  server last said, markers included.
- **Room is the first tier of `request`**, and a stored call applies only while `isFullCopyOf` the
  preview the message carries now (same identity and full lengths, and the full text starts and ends
  with what the preview kept around its cuts, whitespace aside). So a refresh that re-previews an
  unchanged call keeps it; a call the server changed fails the check, is fetched, and its row is
  overwritten. `previewRevision` is deliberately not compared: it is the message's `updatedAt`, which
  a feedback or title-unrelated edit moves.
- **No Room write while streaming** (the streaming-anchor invariant, user decision "write after
  Final"): a part landing mid-stream waits in `pendingWrites` and is written when `isStreaming` goes
  false; a conversation switch while it waits drops it. A part of an `unfinished` message is never
  written. Seeds (a reload previewing a call held in full) are kept the same way.
- **Reconnect retry.** When `ConnectivityObserver` goes offline → online, each `Failed` part whose
  identity an open card pins (`retain`) is loaded again, at most `MAX_AUTO_RETRIES` (3) times per
  part; the user's Retry resets the count. Unpinned failures wait for the card to reopen.
- **The overlay runs in `ChatViewModel.uiState`'s combine** (`ToolPartOverlay`), over the built
  `displayMessages`: never a tree rebuild, never `activeBranches`, never the streaming anchor.
  Delegates read the backing state, which keeps the previews. The loaded map rides beside the server
  URL flow so it adds no hop to every state emission. Every later field in that combine (the reply
  avatars) builds on the overlaid state; one copied from the backing state puts the previews back.
- **An open preview never sits on "Loading" with nothing in flight**: the disclosure asks again
  whenever its preview has no status. A previewed command's Show all reads "Show full output",
  since a preview's line count is not the output's.
- **Cards request from an effect, not the click** (`ToolContentDisclosure`, at the top of every
  disclosure that reveals args, output or transcript): a card restored open, or carried open from
  the live reply, asks too. `ToolCallDispatcher` provides the call's `LocalToolPreview`; nested
  transcript calls get their own. `LocalToolContent` is null outside `ChatScreen` (shared links are
  never previewed), and cards render as before there.
- **Never copy a preview.** A `CodeBlock` inside a previewed card disables Copy; the code-execution
  output pane (visible without a disclosure) fetches first; message Copy fetches every previewed part
  (8 at a time) and copies nothing on a failure (`COPY_TOOL_CONTENT_FAILED`).
- A reload that replaces a call held in full with a preview of the same identity and lengths seeds
  the full call (`seedsFor`) instead of refetching it.
- A subagent's transcript-less preview routes its child calls' files by `subagentAttachmentCalls`
  (`omittedTranscriptCalls`, also folded into `outputToolCallIds`).

## MCP Apps (v0.8.9-rc1, `components/mcpapp/`)
An MCP tool can ship a View (`text/html;profile=mcp-app`) that arrives as a `ui_resources`
attachment. The chat shows an inert "Open app" row; a tap runs the App inline in that row's card,
with Expand to a full-screen panel.
- **Rows** (`McpAppRows`, from `selectToolCallUiResources`): after the tool card
  (`ToolCallContentPart`, `StreamingToolCallCard`), or under an activity group in its hoisted slot,
  never inside a fold. Only while `mcpApps.enabled` (`McpAppsViewModel.policy` → `LocalMcpApps`);
  a missing binding reads "could not be loaded" with no button; the shared viewer passes
  `McpApps.ReadOnly`. A subagent card's rows cover its child calls too (`outputToolCallIds`, plus
  the omitted-transcript hints), saved and live (the live trace's parts); a background-task card
  gets its own call's rows like any tool card.
- **No first-open prompt**: "Open app" goes straight to the panel, as on web. Approval is per
  action (below), not per App.
- **Web views live in a pool, not in the list** (`McpAppViewPool`, remembered by `rememberMcpApps` above
  `MessageList`). A lazy item only attaches its session's view: Android returns a `FrameLayout`
  container from `AndroidView.factory` and moves the pooled `WebView` into it in `update`
  (`onReset`/`onRelease` only detach, never destroy), because the lazy list reuses the node; iOS moves
  the `WKWebView` between `UIKitView` containers. So scrolling a card away, the streaming→final swap
  and Expand only reparent the live App: `host.pageAttached()` runs once per **new** view, never on a
  reattach (it would drop the App's queue and grants), and `host.send` stays wired while detached. A
  view is destroyed only when its session closes (`retain`, keyed by session id) or the pool leaves
  composition (leaving the chat, Android recreation: each App then reloads in a new view and
  initializes again). Views are built with the Activity's context, never the panel dialog's.
  **Android views are software-rendered, panel included** (`configureLazyListWebView`): a detach on
  scroll is exactly the recycle that SIGSEGVs the GL functor path. `onRenderProcessGone` returns true
  and fails that App with Retry (`McpAppHost.close(viewLost = true)`), or the whole app would die.
- **Inline card** (`McpAppInlineCard`): header (tool · server · dot when waiting · Expand · Close) over
  the App at 320dp until it reports, then its first reported height clamped to 80dp..min(4000, 60% of
  the window) (`McpAppSessionRules.inlineHeightDp`) and **frozen** in `McpAppsState.inlineHeights` (the
  card leaves composition on scroll, so not `remember`): a slot that resized would jump the list.
  Later `size-changed` only toggles a 40dp fade + Expand. While its App is in the panel the card shows
  "Return to app" (one view, one parent). Nothing here writes Room, `activeBranches` or item keys.
  In-thread glass must not sample the list's own backdrop (`MessageList` nulls `LocalGlassBackdrop`
  for its rows): a surface sampling its ancestor's recording recurses to a stack overflow on iOS.
- **Several at once** (`McpAppsViewModel.state`): up to `min(maxActiveViews, 3)`, 1 on a low-RAM
  Android device (`McpAppSessionRules.viewCap`); another "Open app" is refused with upstream's
  capacity line and "Show apps". The panel (`McpAppPanel`, an `AdaptiveDialog`) shows one App, with a
  tab per open App at 2+ (dot = waiting); **Back minimizes** (every App back to its card, still
  running), Close X tears that App down (the panel moves to the last-used other tab), leaving the chat
  tears all down (`closeAll`). While Apps run minimized, the chat ⋮ menu starts with "Open apps (n)"
  (`ChatOverflowItem.OPEN_APPS`, count read from `LocalMcpApps` in `ChatFloatingTopBar`), which opens
  the panel on the last-used App (`showApps`). An approved message minimizes rather than closes, so the App can follow
  up. Retry replaces the session with a new id (new host, new view). `displayMode` is `inline` in a
  card and `fullscreen` in the panel (`availableDisplayModes` both; inline sends `maxHeight`, as web);
  `ui/request-display-mode` fullscreen expands an App on screen, inline from the panel minimizes,
  anything else answers the current mode.
- **Only a foreground App shows its question** (`McpAppSessionRules.foreground` / `presented`): the
  panel's App while it is up, else an inline card in the viewport (`onGloballyPositioned` bounds, so a
  prefetched, unplaced item is not foreground). One dialog at a time, drawn by `rememberMcpApps`
  (inside the panel's window while it is up). Others wait in their own queue with a dot and a
  "<server> wants to run a tool" snackbar whose Show **expands** that App (a divergence from the
  spec's scroll-to-card: the list has no scroll-to-attachment seam). Grants (`autoRunTools`) and the
  location question are per session: a second App's location request while one is open is refused,
  and closing an App refuses only its own.
- **Eviction** (`onMemoryPressure`: Android critical trim / `onLowMemory`): every App not in the
  foreground and waiting on nothing is torn down; its card keeps the picture taken when it last
  detached, dimmed, with "Tap to resume" (a reload). An App awaiting approval is never evicted. iOS has
  no memory hook or picture yet.
- **Host** (`McpAppHost`): the spec `2026-01-26` JSON-RPC in Kotlin, unit-tested without a WebView
  (`McpAppHostTest`). Every server call is native through `McpAppsRepository`, so no token reaches
  the page. Tool calls and messages wait on the approval dialog (exact text, never truncated; over
  `maxActionPreviewChars` refused without asking; queue bounded by `operationLimits.maxActive`;
  Cancel pauses the App until reopened). Approve settles only the action its dialog drew (each
  queued action has its own `id`), and arms 500ms after the dialog appears, so a double tap never
  approves the next one unseen. Links open only on hosts the delivered CSP declared.
- **"Don't ask again for <tool> until I close this app"** (mobile-only; web confirms every action):
  a checkbox on tool-call dialogs only, under the same arm delay, recorded only when "Run tool"
  settles that dialog's own action. Per tool, any arguments, in `McpAppHost.autoRunTools` only
  (never persisted). Never for messages, over-limit requests or while paused. Revoked by Cancel,
  Stop, Close, Retry (a new host), a page reload (`pageAttached`), "Ask again" (the panel subtitle
  menu, which reads "server · Auto-running tool" meanwhile), and a `BUSY` answer (429/502-504) to
  an auto-run call. Auto-run calls count against `maxPendingActions` with the waiting queue.
  An approved message goes through `ChatViewModel.sendAppMessage`: only its text, composer
  untouched, refused while a reply runs.
- **Relay page** (`McpAppRelayPage`): a port of `mcp-sandbox.html`'s inner-frame half (mirror
  `mcp-app-sandbox-relay`). It loads nothing; the App's own CSP may name hosts its frame loads from.
  The bridge (`McpAppWebView`, the platform seam): Android `RelayBridge` —
  `WebViewCompat.addWebMessageListener` scoped to `https://mcp-app.switchboard.invalid`, main frame
  only; on a WebView without `WEB_MESSAGE_LISTENER` (pre-83, un-updated or Play-less devices) a
  fallback `addJavascriptInterface` object with **one** method, `postMessage(token, json)`, that
  forwards only calls carrying a 256-bit per-page token held in the relay's own closure. That object
  is injected into every frame, the App's included, and native can't see which frame called, so the
  token is what confines it to the relay (the App's opaque frame can't read the relay's globals or
  source). Never add a second method to it, and never hand the token to the App. Replies go by
  `evaluateJavascript` (top document only). The fallback adds nothing where the listener exists.
  iOS a dedicated `WKContentWorld`, main frame only. Both refuse main-frame navigation and new
  windows.
- **Permissions** (`McpAppPermissions`): geolocation and clipboard-write only, from the resource or the
  read content's `_meta.ui.permissions`, into the frame's `allow`. Location is Android-only
  (`mcpAppLocationSupported`; WebKit refuses opaque origins) and decided natively per MCP server:
  `McpAppHostCallbacks.mayOfferLocation` drops `geolocation` from `allow` for a refused server, the
  WebView's `onGeolocationPermissionsShowPrompt` goes to `McpAppHost.onLocationPrompt` →
  `McpAppsViewModel` (consent dialog when never asked, then `ACCESS_COARSE_LOCATION` through
  `rememberCoarseLocationRequester`). Every path must answer the WebView's callback, including Close
  (`answerLocation(false)`) and the WebView withdrawing its prompt (`onGeolocationPermissionsHidePrompt`
  → the `LocationPromptWithdrawal` `onLocationPrompt` returned), or the App's request hangs. The consent dialog's Allow arms
  500ms after each request appears (`requestId`), like the action dialogs. Answers live in `:core:data`
  `McpAppLocationConsentStore`, which Settings' server sheet also edits.
- **Not verified on a device yet:** reparenting the pooled WebView into the panel's dialog window and
  back, scroll detach/reattach and the eviction picture, touch handling of an inline WebView in the
  list (no gesture arbitration is done: no 8dp claim, no drawer exclusion), rotation/fold reloads, the
  iOS pool as a whole, whether the relay's loader CSP needs a `frame-src` entry for its `about:srcdoc`
  child, and that WebKit delivers the inner frame's `message` events to the isolated content world.

## Legacy MCP-UI resources and `\ui{…}` markers (v0.8.9-rc1)
Plain `text/html` `ui_resources` entries the model places with `\ui{id}` / `\ui{a,b,c}` in its reply.
- **Markers never show.** `TextContentPart` runs every artifact-free text run through
  `UiMarkedMarkdownContent` (`splitUiResourceMarkers`: fenced code and inline code spans are left
  alone; markdown escapes resolve as web's text nodes see them). While streaming, a half-written
  marker at the end is hidden (`withoutTrailingPartialUiMarker`). In-chat search counts text the
  same way (`countUiMarkedOccurrences`, clause 2 of the `SearchMatchEnumeration` contract): keep the
  two sides on that one function.
- **Rendering** (`LegacyUiResourceBlock`, only while `legacyHtmlEnabled`): the conversation's resources
  by id come from `conversationUiResources` (ChatScreen, off the main thread, rebuilt only when the
  shown messages or the live attachments change) through `LocalLegacyUiResources`. A placeholder
  while streaming; then an inline web view (`LegacyUiResourceWebView`: shell at
  `https://mcp-ui.switchboard.invalid`, resource in an `allow-scripts`-only srcdoc frame, Android
  software layer, same `RelayBridge` / content-world bridge as Apps) sized by `ui-size-change`.
  Several ids = a horizontal carousel. A single unknown id shows web's "UI Resource not found
  (index: …)" chip once the reply settles (after a 300ms grace, since the resource map is rebuilt off
  the main thread just after the messages), whatever `legacyHtmlEnabled` says, as web checks it first;
  unknown ids in a carousel and non-legacy resources draw nothing.
- **Actions** (`LegacyUiFrameHost`): `tool`/`intent`/`prompt` become upstream's `handleUIAction` text and
  wait on "Send message from app?" (`McpAppsViewModel.requestLegacyMessage`, dialog hosted by
  `rememberLegacyUiResources` so it survives the row scrolling away); refused while a reply runs.
  `link`/`notify` are ignored, as on web.

## Conversation pull requests (v0.8.9-rc1, slice 1)
A code chat (one that ran against an attached workspace) can have a pull request; web shows it in the
header, its small-screen layout in the ⋮ menu, which is where it goes here.
- **Who is asked** (`PullRequestDelegate`): `pullRequestsAvailable` (rc1 + `pullRequestsEnabled`), a
  saved chat not on its first streaming turn, and `Conversation.isCodeConversation == true` from the
  cached row. A row cached before that was known is read once in full on open (the
  `ConversationLoadDelegate` backfill), which settles it. Nothing else is ever looked up.
- **In memory only**: `:core:data` `PullRequestRepository`, per account, shared with the drawer's
  marks so the open chat isn't fetched twice. Never Room. Polls on upstream's interval only while the
  screen is in front (`onPause`/`onResume`); on return an answer over 15 s old is asked at once.
- **UI**: `ChatOverflowItem.PULL_REQUEST` leads the conversation section once a lookup found one
  (absent otherwise; never disabled). Material draws `PullRequestStateIcon` with the CI dot; glass and
  the native iOS menu get a `BarMenuItem` whose subtitle says the state and checks in words.
  `PullRequestSheet` opens from it, reads the live state, shows its link only for
  `ConversationPullRequest.openableUrl()` (absolute http(s) passing `isSafeLinkUrl`), and a Retry row
  when a refresh failed after a success.
- **Where it shows depends on the chat pane's width** (600dp, `comparisonWide`, not the window), so
  exactly one of these exists: on a wide pane a bar chip (`PullRequestBarChip` in Material, a
  `BarAction.Text` with `leadingIcon`/`iconTint`/`badgeTint` in glass and the native iOS bar, which
  draws no dot and reads the summary as its accessibility label) and no ⋮ item; on a narrow pane the ⋮
  item plus a composer row (`PullRequestComposerRow`, provided through `LocalPullRequestComposer` so
  both platform composers draw it, first row of the composer card). The composer chip spells out
  "PR #482 · Checks running" while `PullRequestUiState.unseen`, then folds to the glyph; seen = the
  sheet opened, or 8 s on screen with the keyboard down (`markPullRequestSeen`), remembered per
  (chat, state, checks) in the delegate only. It is folded during a run and hidden during a tool
  approval, while a queued message is edited, and behind the keyboard on panes under 480dp tall.
- **The sheet's visibility is delegate state** (`PullRequestUiState.sheetOpen`, `openPullRequestSheet`),
  so all three surfaces open the one sheet `ChatFloatingTopBar` hosts; a chat switch closes it.
- **A change of credential** (`PullRequestRepository.revision`, after an account connect or
  disconnect in Settings) restarts polling, which asks at once.

## Tool intent labels (v0.8.8)
A tool card's title is the model's own `intent` string when present, else the raw tool name.
**Accepted only when it is the FIRST key of the args object** — the opt-in (capability +
per-tool `describe_intent`) is entirely server-side and unobservable here, so a plain
"is there an `intent` string" test would retitle a user's MCP tool that legitimately takes a
parameter by that name. Upstream injects it at position zero, and that ordering is the only part
of the contract this side can check. Lifted labels are stripped from the expanded args dump.
Shipped ahead of upstream's own presentation, which scopes rendering as a follow-up.

## Context gauge (v0.8.8)
v0.8.8 removed the context-projection endpoint, so outside a stream `ContextProjectionDelegate`
resolves the gauge from the displayed branch: the finished turn's saved snapshot
(`metadata.contextUsage`), the live reading while its turn is on the branch, the deepest saved
snapshot, the projection (older servers), then an on-device estimate (`util/ContextGaugeResolver.kt`,
ported from upstream `tokens.ts`; registered in `scripts/mirrors.json`). Two rules are load-bearing:
- **It re-runs on the branch itself, not its tail id.** A Room emission and the network refresh that
  follows it usually share a tail id and differ only in `metadata`. Keying on the tail (as the old
  `ProjectionKey` did) leaves the cached estimate on screen after the server's snapshot has arrived.
- **`ContextUsageSource.LIVE` alone does not prove a live reading.** The stamp survives into the next
  run until something replaces it, so the delegate records the gauge's value at stream start and only
  carries over a reading that replaced it. Otherwise a run that reported nothing pins the previous
  run's value.

The breakdown (`util/ContextBreakdown.kt`, rendered by `ContextBreakdownCard`) ports web's
`Breakdown.tsx`; Totals and Cost come from `util/UsageTotals.kt`. Every context percentage goes
through `percentLabel` (localized: `42 %` in German and French). One more load-bearing rule:
- **Live usage is attributed to the Final frame's response id, never the tail.** `LiveReplyDelegate`
  folds every `on_token_usage` (buckets included, deduped by `runId:seq`, plus the resume frame's
  `collectedUsage`) into `pendingUsage`, which counts until its reply carries `metadata.usage`.
  `onStreamEnded` runs before the reply is swapped in, so the tail there is the user message;
  anchoring on it makes the reply's own usage never cancel pending, and Totals and Cost double for
  the rest of the session (`ChatViewModelUsageTotalsTest` fails with 2000 instead of 1000).

**Last turn and All branches (v0.8.9-rc1).** Under Totals the breakdown lists, as web's three
`Usage` panels do: the tail reply's usage (`lastTurnUsage`; "Current turn (so far)" while streaming,
the live usage only once an event arrived, never the previous reply's), Totals for this branch, and
All branches when any bucket or a fully priced cost differs (`showsAllBranches`, web's `showTotal`).
Under the "both" cost scope each block carries its cost in its header and the separate Cost line
goes. Upstream's `turnInProgress` (live response == viewed tail) is plain `isStreaming` here: the
live reply always renders after the displayed tail, and `switchBranch` is refused mid-stream.

**Detail levels and the compact suggestion.** The breakdown is always computed in full and filtered
at render by the user's `ContextDetailSections` (Settings → Chat → Context usage: Simple / Standard /
Detailed presets, or a custom mix), through `visibleBreakdown` in `:core:model`. The rendering itself
is `ContextBreakdownCard` in `:core:ui`, so the Settings preview draws exactly what the chat does.
`ChatUiState.compactNudge` suggests compacting from the user's threshold (default 70%), in the active
placement only (+ a dot on the + button under the default options-sheet placement), and every
suggestion opens `CompactConfirmDialog`; it is never one tap. Three rules, each with a test in
`ChatUiStateCompactNudgeTest`:
- **`canCompactNow` does not exclude an in-flight compaction**, so the nudge checks `isCompacting`
  itself; `MessageEditingDelegate` sets it before `isStreaming` flips.
- **An estimate never suggests compacting.** Only a real server reading (snapshot, live, projection).
- **"Not now" snoozes a band, per conversation, persisted**, and the snooze clears once a real reading
  is back under the threshold (`ChatViewModel.clearCompactNudgeSnoozeWhenBelowThreshold`), so the next
  fill suggests again from the start. A chat still loading has no reading and must not clear it.

## Message Timestamps
- `MessageTimestamp` renders in the user's style (Relative / Smart / Absolute, #445) from
  `LocalDateTimeFormat`. Tapping it toggles to the full date and time; under Absolute the tap is off.
- It is the only reader of the minute ticker (`LocalRelativeTimeReference`) in the chat tree, and it skips
  the read under Absolute. Keep it that way: reading the ticker in a bubble or the list would recompose
  them every minute.
- An unparseable timestamp renders nothing.
- Integrated into `MessageBubble` action row
