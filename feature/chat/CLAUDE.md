# feature:chat

## State Architecture (ChatUiState slices + narrowed handles)
`ChatUiState` is decomposed into **17 `@Immutable` sub-state slices**, plus two top-level fields: `error`
(a shared transient-banner channel) and `mediaPreview`. The slices: `conversation`, `content`
(message tree + all streaming fields),
`editing`, `composer`, `selection` (endpoint/model/tools/params), `queue`, `steer`, `search`,
`presetPrompts`, `voice`, `favorites`, `subagents`, `comparisonState`, `gates`, `account`,
`actions`, `prefs`.

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

## One screen, four platform seams
`ChatScreen` / `NewChatScreen` are a single commonMain composable for both platforms. Where the
platforms genuinely differ, the screen calls an `expect`, never an `if (platform)`:
- `ChatComposer` — `ChatInput` on Android, `IosChatInput` on iOS. iOS's clipboard-image paste lives
  inside its actual (`onPasteFiles` is ignored on Android).
- `rememberChatAttachmentActions` — activity-result launchers vs the native iOS pickers.
- `rememberChatStartRecording` — Android routes API 26–30 through the system recognizer overlay.
- `Modifier.addToChatSelectionItem` — a no-op on iOS.

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
  - The hero shows the selected spec's icon (see "Spec presentation") or the agent's avatar, as
    web's landing does (`landingHeroIcon`), and falls back to the app tile.
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
(`SendCompletionDelegate`), a just-created branch (`ComparisonModeDelegate.branchFromComparison`),
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
- **Last spec**: a spec the user picked is stored with the last-used pair (`lastUsedSpec`; a raw
  pick clears it). Unless a spec is marked `default`, new chats reopen on it, under `prioritize` too
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

| Server config | "Server's choice": new chats open on | "My last-used model": new chats open on | Role of last-used |
|---|---|---|---|
| No `modelSpecs` | Last-used, then first agent, then first model | Same | Decides new chats |
| `prioritize: true` (or field omitted) | The `default` spec, or the first spec if none is marked `default` or `softDefault` (a `softDefault` stays soft: row 4) | Last-used if usable, otherwise that spec | Default: saved, used only when the spec can't apply. Setting: wins |
| …and the user's last pick was a spec | That spec, unless a spec is marked `default` (web: `LAST_CONVO_SETUP.spec`) | That spec | Last spec reopens |
| `prioritize: false` + `softDefault` spec | Last-used if usable, otherwise the soft spec | Same | Wins over the soft spec |
| `prioritize: false`, no soft default | Same as no specs | Same | Same as no specs |
| `interface.modelSelect: false` | The `default`/soft/first spec, always (a stored last spec still reopens when none is marked `default`); an agent card or a spec's shortcut still wins, as a start-chat link does on web | Same: the setting is ignored | Used only when no spec applies |
| `enforce: true` | As above; a raw pick can't be sent | Last-used only if it's a spec; a raw one falls back to the server's spec | Raw last-used ignored |

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
composer snapshots, the new-chat handoff, the conversation cache (`spec` column, Room 10→11), comparison, presets, edit/regenerate and paused-run pins all carry it. Sending
it changes what the server does even without `enforce`: it fills the spec's unset private preset
fields (`promptPrefix`, `instructions`, `system`, …) into the request (`mergeModelSpecPreset`), so a
chat opened on a spec gets the admin's prompt as on web.

**A spec's preset becomes the chat's parameters** (`util/SpecParameters.kt`), as web copies
`{...spec.preset, spec}` into the conversation. Without `enforce` the server applies none of the
preset's public fields (temperature, `reasoning_effort`, `max_tokens`, `maxContextTokens`, …), so the
client has to send them. Only a change of spec touches the parameters (`acrossSpecChange`, from
`applySelection` and the seeder's spec-config write), never a re-emission of the same one, so a tweak
on top of a spec survives config refreshes and is sent:
- Onto a spec (a pick, a seeded tier, a retargeted spec re-applied): that spec's preset over the defaults.
- Off a spec onto a raw model (a pick, or the config dropping it as stale): the defaults.
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
  only if the user changed nothing meanwhile; offline the cache stays.
  A handed-off new chat takes the landing's parameters from `NewChatSelectionHandoff` instead.
- The next new chat starts from the carried spec's preset (`onPendingNavigationHandled`).
- Under `enforce` the server replaces the body with the preset, so while a spec is active the sheet
  is read-only (`parametersSetBySpec`) and `updateModelParameters` ignores it. The parameters are
  that preset there even on the same spec and on a reopened chat (`reopenedParameters`), so the
  locked sheet shows what the server runs, not what the chat stored before `enforce` was on.
- The spec's `artifacts` go out as `ephemeralAgent.artifacts` (`true` → `"default"`), the only place
  the server reads them. The context gauge takes the spec's `preset.maxContextTokens` after the
  agent's and before token-config, as web's `useTokenLimits` does.

**A spec's pair comes from `preset.endpoint` alone** (`toSeed`, as upstream's
`resolveModelSpecEndpoint`): an agents spec runs its `agent_id`, any other its `model`. The server
writes an endpoint it infers from `agent_id` into the served config (`materializeModelSpecEndpoints`),
so the client infers nothing, and a stray `agent_id` on another endpoint is ignored, as the server
ignores it. A spec served with no endpoint (an explicit `endpoint: null`) opens nothing.

**Model shortcuts** (launcher shortcuts, iOS quick actions; mobile-only). Usage is counted per pair
and spec (`SettingsDataStore.incrementModelUsage`): a spec's sends count apart from the raw model and
keep its label for the shortcut's title, and the spec rides the link
(`librechat://model?…&spec=`, `NewChat.spec`). At Tier 0 a shortcut reopens its spec on the spec's
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

**A stored spec is kept only while the server still offers it on the same endpoint and model**
(`offeredOn`): on a conversation load, a preset load (which adopts the spec's own pair when the
preset names none), and again whenever the spec config changes. This is stricter than the server,
whose mismatch check compares the endpoint only, so a spec whose model an admin changed is dropped
too: the spec and pair were sent together, so a moved spec is a different selection. Under
`enforce` the send is then blocked with "Select a model" rather than refused by the server. A stored
*pick* — the last-used restore and a model shortcut — is the exception (`reopenedPick`): it follows
the spec to its current pair.

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
- **Icons** (`isImageUrl`, `iconValue` and `resolveIconSource` in `:core:model` `media/IconUrls.kt`,
  `util/SpecIcons.kt`): a spec's `iconURL`, its preset's, its agent's avatar, then its endpoint; a
  value is an image by web's `isImageURL` rule, else an endpoint or provider whose glyph to borrow,
  and `""` is unset. The picker rows, landing hero, Compare's spec lane and quick picks use it, and
  reply avatars resolve a message's `iconURL` through `resolveIconSource` too;
  `showIconInMenu: false` hides it in the picker and quick picks. The conversation rows apply web's
  `EndpointIcon` rule (`Conversation.iconEndpoint`), joining a relative icon path to the server
  (`ConfigRepository.serverUrl`). `showIconInHeader` is decoded only: the header
  chip is text-only by design.

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
- **Handed-off new chat (load-bearing):** when a chat is created from the landing page, the landing VM is reset and a fresh `Chat(id)` VM resumes the stream — so the optimistic user message lives only in the discarded landing VM, and the server persists the *request* message only when the reply completes (`agents/request.js` saves it right before the Final event). Without intervention the resumed screen would show only the streaming bubble (no user message) for the whole stream. Fix: `NewChatSelectionHandoff` carries the optimistic `Message`; `ChatViewModel.init` seeds it into `messages`/`displayMessages` and into `pendingResumeUserMessage`. `loadConversation` keeps that seed appended — inside its off-Main `combine` map, guarded so it only appends while no server row is its copy — until the server's copy arrives, then drops it (so a later server-side delete still removes the row). "Its copy" is matched by id **or** by content (`isServerCopyOf`: user-authored, same parent, same text), because rc3+ servers do not adopt the client-minted id (see *Optimistic id reconciliation*). An id-only match never fired there, so a run that ended without a Final — a resume that 404s because the job already finished — left the seed as a second root sibling, and the path's newest-sibling default showed it alone over the persisted turn and its reply. `finalizeChatDisplay` *also* clears `pendingResumeUserMessage` in its atomic Final update. This is what feeds `finalizeChatDisplay`'s in-memory backfill in the handoff case — do **not** replace it with a network `reloadConversation` on Final (that was the #169 regression being avoided).
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
- **Reopen (`ComparisonModeDelegate.rehydrateFromMessage`):** nothing but the persisted attribution records that a conversation was a comparison, so `loadConversation` rehydrates comparison state from the assistant tail's parts on the first non-empty emission (latched once per load; respects a session toggle-off). Keep this answer = `POST /api/messages/branch` filtered to one agent's parts; the branched sibling becomes a single-agent tail, so the next reopen is the normal view.
- **Lane B's spec on reopen.** The server saves nothing about the added convo's spec: not on the message, not on the conversation. The only trace is the sender in an ephemeral lane's id, `modelLabel ?? spec.label ?? modelDisplayLabel` (left out when empty). `ComparisonState.reopenedSecondaryId` keeps that id and `laneBSpec` reads it against the spec config through `laneBSpecFor`: the one spec on the lane's pair with that label, else, under `enforce` only, the one spec on the pair. It is re-read on every use, so a reopen that lands before the spec config still finds its spec; the screens read `ChatUiState.laneBSpec`, never `secondarySpec` directly. **This depends on `buildAddedConvo` sending no `modelLabel`**: the server would save that as the sender in place of the spec's label. A saved-agent lane has no spec, and the server ignores one there.
- **Lane B's settings.** `buildAddedConvo` copies its spec preset's sampling fields flat onto `AddedConversation`, the names `packages/api/src/agents/added.ts` copies. v0.8.8 applies them only when the primary is a saved agent. They are not `ModelParamPayload` output: that is keyed per provider, and the server reads this list by name whatever the provider. A spec's prompt never reaches lane B, on web either: the served config strips `promptPrefix` and the other private preset fields (`PRIVATE_MODEL_SPEC_PRESET_FIELDS`), and the server merges them into the primary only.
- **Edit and regenerate carry lane B; continue and compaction don't.** `MessageEditingDelegate.launchSend` calls `onSendStart` and sends `buildAddedConvo`, so an edit or regenerate in a comparison re-runs both panes, as web does. **A comparison can't be continued as one** (v0.8.8, checked on the rig): a continue carrying `addedConvo` makes the server rewrite both lanes from scratch in place, partial text lost, and one without it saves the primary's answer alone, dropping lane B's. A single-model continue extends the reply correctly. So `ChatViewModel.continueGeneration` refuses while comparing, and each pane offers **Keep and continue** instead (`ComparisonModeDelegate.keepAndContinue`): branch to that lane (as "Keep this answer" does), wait for the kept reply to be the one shown, then continue it as a single-model chat. The server records cut-short only per message (`unfinished`), never per lane, so both panes offer it.

## Continuing a cut-short reply
- The chip ("Stopped" + Continue, `StoppedReplyFooter`) sits under the latest reply, always visible rather than in the tap-to-reveal action row: it also says why the reply ends mid-sentence. `MessageList` and `SecondaryMessageList` draw it under the reply whose id `stoppedReply` names.
- Which reply: `ChatUiState.continuableReplyId` (`util/StoppedReply.kt`). **Not web's rule**: web's `continueSupported` needs a `finish_reason` other than `stop`, and a v0.8.8 agents reply only ever carries `tool_call_limit`, which it excludes, so web's Continue almost never shows. A stopped or dropped reply is saved `unfinished` instead, so that counts too. `unfinished` is also what a reply paused on a tool approval or question carries, so a pending action hides the chip. Continue replays the user turn above the reply, so a reply with none, an error and a compaction summary get nothing; so does a comparison reply outside a comparison (a single-model continue would drop lane B).

## Content Rendering
- `ContentPartRenderer` dispatches by `ContentPart.type`:
  - `text` -> `MarkdownContent` (custom regex parser with LaTeX support)
  - `think` -> collapsible thinking block
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
- LaTeX: `LatexBlock` (block math) and `LatexInline` (inline math) via AndroidMath native rendering
  - Supports `$$...$$`, `\[...\]` (block) and `$...$`, `\(...\)` (inline)
  - `$...$` follows Pandoc's `tex_math_dollars` neighbour rules (`scanInlineLatex()` in
    `MarkdownParsing.kt`): opener followed by non-space, closer preceded by non-space and not
    followed by a digit, `\$` never delimits. Nothing inspects the span's content. Backtick code
    spans are skipped and an open fence stops the scan (code wins, as on the web). While streaming,
    a closer at the very end of the buffer is deferred so `US$5 to US$` + `10` never flashes as math
  - Falls back to monospace text if AndroidMath can't parse the expression

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
  traces — are projected onto state by `LiveReplyDelegate`; the ViewModel-owned operations (reload,
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
- Before the first delta there is no insertion point, so the bubble shows `StreamingWaitIndicator`
  instead of a cursor blinking against empty space.
- Tool calls tracked in `activeToolCalls` list (start -> complete lifecycle)
- **Stream termination is a chokepoint** for every *event-driven* end — clean/aborted Final, error, failed abort, watchdog, resume-found-expired — which funnel through the single private `endStream(reason)` in `StreamingManagerDelegate` — and so does a flow that ends with neither Final nor Error: `launchStream` ends it as `Reconcile` and `resumeStream` as `ResumeExpired`, unless a Stop is pending (the abort watchdog owns that end). `SseClient` completes a flow that way only when its resume got a **404** (the job is gone, so the reply is on the server) or the account switched. **A body that merely closes is not a finished run**: upstream sends no heartbeat, so a proxy/CDN idle timeout during a long tool call or a human-review pause closes a live stream, and the server's own `res.destroy()` on a publication failure asks for a reconnect — `SseClient` resumes those with `resume=true` on its retry ladder, as upstream's transport-failure path does, keeping the partial and the pause card. Its attempt counter resets on a parsed event, not on bytes, so a 200 that carries no events (a captive portal) still climbs the ladder. **The ladder's ceiling (`RETRY_EXHAUSTED`) is not a network error either**, and neither is a persistent HTTP status such as a proxy's 502 on every resume (`STATUS_RETRY_EXHAUSTED`): neither says anything about the run, so the delegate asks `/chat/status` right away, as upstream does at its ceiling — resume if active (once per run: the flag resets at every attach — a local send, opening a conversation with a live run, foregrounding, network recovery — but not at the ceiling's own re-attach, so an unreachable live run ends instead of looping), the same end as a 404 if not (refetch, no banner). Only when the status read fails too is there an error: a network error after the transport ladder (the case the connectivity observer can recover), the status error itself after a persistent status. 401 stays in-band and is never adjudicated. An account switch also completes the flow, and ends nothing: a reload then would read the conversation with the new account's credentials. The same guard runs after the ceiling's status read, which can take seconds, so a switch during it neither resumes nor reloads. The 404 case is the network-drop recovery: the run finished while offline, and before `launchStream` ended it here the chat froze on the partial. The sealed reason (`Finalized` / `StreamError` / `AbortFallback` / `ResumeExpired` / `Reconcile`) decides job cancel, state write, queue policy, and whether a reload is allowed. A per-session Int counter latches each session to at most ONE end, and stale ends from a previous session are no-ops — do not add teardown steps at call sites; add them to the reason table. Structural invariant this encodes: **nothing reloads on an abort path** — the server emits the aborted frame BEFORE persisting, so any refetch there races the save (and the optimistic user message was never in Room, so a reload can lose it entirely). `Finalized` writes no state: the atomic finalize in `finalizeChatDisplay` owns that (the no-completion-flash invariant)
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
the composable purely so this module — which has no Compose test harness — can assert the mapping). Deriving
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
existing upload/usage path already handles them.

## Chat Tools
- The user's own toggles: `enabledTools` and `selectedMcpServerNames` are global (stored in
  `SettingsDataStore`, carried into every chat). Web search and URL context are
  `ModelParameters.webSearch` / `urlContext`, so they belong to the chat: they last through model
  changes within it, every new chat starts with both off, and a reopened chat has web search as
  this device left it (`ConversationWebSearchStore`, next section). The Skills toggle is per
  conversation (`SkillsToggleDelegate`).
- Tool toggles and MCP servers live in the paged "+" options sheet (`ChatToolsSheetContent`),
  reached from `ChatInput`'s `onOpenTools`, plus the server's pinned chips (`PinnedToolsRow`).
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
  built-in search the preset's runs LibreChat search when it can, else nothing); a comparison uses
  LibreChat search when it can (the second model only gets the first's tools, `added.ts`); otherwise the
  user's Settings › Chat › Web search choice (`AppPreferences.WebSearchFirst`, LibreChat first by
  default), falling back to the other, else nothing and the control is hidden.
- **LibreChat search counts only when it will work** (`FeatureGatesState.librechatSearchReady`):
  `/api/config` serves `webSearch` and `GET /api/agents/tools/web_search/auth` says `authenticated`,
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
  name contains `openrouter` (`builtInSearchProvider`; the server also matches the base URL, which
  the client can't see), unless `dropParams` names `web_search`. The control's second line names the
  search it runs ("LibreChat search", "Claude's built-in search"; `webSearchName`).
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
  LibreChat search, but the server sends the preset anyway, so OpenAI runs both.
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

## Content Cards
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
- React artifacts compile in-browser (Babel) and load as a real ES module; the artifact's `import`/`export` run verbatim against an import map the runner builds over the bundled React/ReactDOM globals (no source rewriting)
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

What is vendored, at which version, and why each pin sits where it does is in
`scripts/web-assets.json`; `scripts/vendor-web-assets.py` moves the bytes and CI runs
`--check` against a sha256 lock. Repin through **`/update-web-assets`**, never by editing a
vendored file. ~2.5 MB compressed in the APK, mermaid and Babel being two thirds of it.

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
`type="module"` runner would have resolved its own imports too early). A bare specifier
that is **not** one of the bundled four resolves to a module that throws naming the
package. Third-party npm imports (recharts, lucide-react) therefore no longer work; that
is the known cost of this pass, and the follow-up is an opt-in consent toggle that maps
them back to a CDN for users who accept it.

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
tool block. It is pure because this module has no Compose test harness.

- **Activity groups.** Consecutive reasoning + tool calls fold under one header, terminated by an
  `activity_label` part. A blank label is a *reservation* — invisible, and it only moves the claim
  boundary so a later filled label cannot reach back past it. With no label at all the block
  re-splits into the legacy shape (reasoning standalone, runs of ≥2 tools grouped), so a server
  without the feature renders exactly as before.
- **The header's failed colour is `activityGroupFailed`, joined with attachments at the render
  site**: the label's `failed`/`partial` status, or a refused memory write in the block judged by
  the memory card's own rule (`isFailedMemoryToolCall`), as web's `outcome.ts` does. Run-step
  `failed` and generic error output on other tools are not counted yet.
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

The breakdown (`util/ContextBreakdown.kt`, rendered by `ContextUsageGauge.kt`) ports web's
`Breakdown.tsx`; Totals and Cost come from `util/UsageTotals.kt`. One more load-bearing rule:
- **Live usage is attributed to the Final frame's response id, never the tail.** `LiveReplyDelegate`
  folds every `on_token_usage` (buckets included, deduped by `runId:seq`, plus the resume frame's
  `collectedUsage`) into `pendingUsage`, which counts until its reply carries `metadata.usage`.
  `onStreamEnded` runs before the reply is swapped in, so the tail there is the user message;
  anchoring on it makes the reply's own usage never cancel pending, and Totals and Cost double for
  the rest of the session (`ChatViewModelUsageTotalsTest` fails with 2000 instead of 1000).

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
