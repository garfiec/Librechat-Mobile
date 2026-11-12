# feature:chat

## State Architecture (ChatUiState slices + narrowed handles)
`ChatUiState` is `@Immutable` slices (`content` holds the message tree and all streaming fields), the
mapped `settingChips`, plus top-level `error` (shared transient banner) and `mediaPreview`.
- UI reads flat compat accessors (`uiState.isStreaming`). Adding a field: put it on the owning slice,
  add a flat accessor on `ChatUiState`, add the slice to the owning delegate's writer.
- Delegates never receive the root `ChatStateHandle`; each gets a narrowed handle from
  `DelegateHandles.kt` whose `update {}` exposes only its slices. `error` is writable from every
  handle (`setError`). Only `ChatViewModel` holds the root.
- **Single emissions:** completion-flash finalize, begin-stream, reset-with-carryover,
  `applyComposer` and `switchBranch` each stay ONE `StateFlow` emission; don't split them across
  `update`/`copy` calls (why tree and streaming fields share `MessagesState`).
- **Chrome/hot split (`ChatScreen`):** the thread collects at full rate; everything else collects
  `map { it.neutralizeStreamingChurn() }.distinctUntilChanged()` (`ChatChromeEquivalence.kt`).
  Chrome must not read a neutralized field (they are always empty there). A new high-frequency
  streaming field goes in both `neutralizeStreamingChurn` and `ChatChromeEquivalenceTest`. Known
  exception: `subagentProgress` (chrome-visible, unthrottled; open follow-up).

## One screen, five platform seams
`ChatScreen`/`NewChatScreen` are one commonMain composable. Platform differences go through an
`expect`, never `if (platform)`: `ChatComposer` (`ChatInput`/`IosChatInput`),
`rememberChatAttachmentActions`, `rememberChatStartRecording`, `Modifier.addToChatSelectionItem`,
`McpAppWebView`/`rememberMcpAppViewPool`. A feature added to one composer reaches one platform; add it
to the screen. Each composer actual takes pasted images itself and hands copies to `onPasteFiles`
(Android: `imageContentReceiver`). Known iOS gaps: the document picker ignores the endpoint MIME
allowlist; no "Add to chat" selection item.

## Screen States
`ChatScreenState`: `LANDING` | `LOADING` (spinner only while nothing is cached and the fetch hasn't
settled) | `ACTIVE`.
- Landing starters (`util/ConversationStarters.kt`): the agent's own (from `GET /api/agents/:id`;
  the list omits them), else the spec's, max 4. `null` = not resolved yet: show nothing, not the
  spec's. They render as `LandingStarters` in `ChatScreen`'s bottom box docked on the composer, not
  inside `LandingContent`, so they ride the keyboard. A tap sends (`sendStarter`), a hold fills the
  composer unsent (`editStarter`); hidden while the composer has text.
- Landing words/icon follow web's `Landing.tsx`/`ConvoIcon` (`landingText`, `landingHeroIcon`).
  Compact mode measures the description's height; don't reserve its line limit.

### Cache-first open
`loadConversation(conversationId, cacheFirst = true)` paints Room first and revalidates in a child
coroutine. **Only `ChatViewModel.init` opts in.** Every other caller (send completion, comparison
branch, stream ends, `refreshMessages`) must stay network-first: it reloads because the server holds
what the cache doesn't, and after a Final a cache paint re-renders the pre-Final tree.
- The fetch's settle is a `combine` input, not a flag read in `collect`: a zero-message conversation
  never re-emits from Room and would spin forever. The comparison auto-rehydrate latch uses the same
  input.
- `isRefreshingMessages` = any messages fetch in flight; `refreshMessages` early-returns while set,
  so a pull during the open's revalidate is dropped, not queued.

## Navigation
- `ChatRoute : NavKey`: `NewChat`, `Chat(conversationId)`, `PromptsLibrary`, `PromptEditor(groupId)`,
  via `EntryProviderScope<NavKey>.chatEntries()`.
- A landing send navigates to `Chat(id)` at `StreamEvent.Created`, keeping `NewChat` on the stack.
  The landing VM resets (`onPendingNavigationHandled`); the `Chat(id)` VM resumes the stream and
  handles its Final, so new-chat-only work there (title generation) is gated on `isHandedOffNewChat`.
- `navigateToChat()` replaces the current chat entry so Back returns to `NewChat`.

## New-chat model seeding
`ModelSelectionDelegate.seedInitialSelection` is the only thing that picks a new chat's model.
Tiers: 0 start-chat route agent/model (one-shot) → 0b **prioritized** spec → 1 last-used → 1b
**soft-default** spec → 2 first agent → 3 first config model. Spec choice is `util/SpecSeed.kt`, a
mirrored port of web's `getDefaultModelSpec` + `useNewConvo`.
- **Prioritized** (`modelSpecs.prioritize`, schema default true, or `interface.modelSelect` off) is a
  standing tier: it beats last-used on every seeder emission until a pick on this landing or a Tier-0
  override yields it; `onPendingNavigationHandled` re-arms it. A spec agent the list lacks falls
  through.
- **A `default: true` spec is prioritized whatever `prioritize` says** (`withUngatedDefault`,
  `util/NewChatStart.kt`), as web's first `/c/new` load and its `defaultPreset` apply it.
  `SpecSeed.kt` stays the port of `useNewConvo`'s gate. It matters only when an admin sets
  `prioritize: false` and `modelSelect: true`.
- **Last spec**: a picked spec is stored with last-used (`LastUsedSelection.spec`; a raw pick clears
  it). Unless some spec is `default`, new chats reopen on it, under `prioritize` too. It reopens on
  the spec's *current* pair (`reopenedPick`); a spec the list doesn't serve (dropped or hidden) falls
  back to the raw pair, as web's `getDefaultModelSpec` treats it as absent.
  Divergence: web also rewrites this when a conversation is opened; mobile writes it only on a pick.
- **Soft default** applies only when last-used can't.
- **"Start new chats on: My last-used model"** (`AppPreferences.PreferLastUsedModel`, mobile-only)
  demotes a prioritized spec to soft (`util/NewChatStart.kt`). It still yields to `modelSelect:
  false` and to `enforce` with a raw last-used. **Keep this override there, never in `SpecSeed.kt`**
  (which mirrors upstream). Switching it while the landing is open re-seeds the landing.
- **Under `enforce`** (both modes): a raw last-used never seeds (`seedableLastUsed`) and a raw model
  seeded before the config arrived gives way, but a raw pick on the landing stays for the preflight
  to ask. If the spec tiers open nothing, fall back to the first spec that can open
  (`fallbackSpecSeeds`); only when none can do the raw tiers run.

| Server config | "Server's choice" | "My last-used model" |
|---|---|---|
| No `modelSpecs` | Last-used, first agent, first model | Same |
| `prioritize: true` (or omitted) | `default` spec, else first spec unless one is `softDefault` (that stays soft) | Last-used if usable, else that spec |
| …last pick was a spec | That spec, unless a spec is `default` | That spec |
| `prioritize: false` + `default` | That spec, ahead of last-used and a last spec | Last-used if usable, else that spec |
| `prioritize: false` + `softDefault`, no `default` | Last-used if usable, else the soft spec | Same |
| `prioritize: false`, no `default` or soft default | A last spec, else as no specs | Same |
| `interface.modelSelect: false` | `default`/soft/first spec always (a stored last spec reopens when none is `default`); agent card or spec shortcut still wins | Same (setting ignored) |
| `enforce: true` | As above, then any openable spec before a raw model; a raw pick can't be sent | Last-used only if a spec; else as "Server's choice" |

A spec that can't apply (missing agent, no agent access, a model the endpoint doesn't offer, config
not loaded) falls back to the normal order; existing conversations reopen on their own model. On the
landing: anything the seeder didn't write (pick, preset, composer swap, start-chat link) is the user's,
detected by comparing with `seederChoice` (no writer reports itself). It displaces the spec, isn't
replaced by last-used while its models load, and lasts until that chat starts. After the handoff
`rearmModelSpecDefault`/`reseedCarried` take over, so a start-chat link lasts one chat. A raw
last-used on an endpoint `addedEndpoints` no longer lists is unusable. Once the model list loads (it
arrives whole), an endpoint missing from it or empty is unusable, not pending.

**`interface.modelSelect: false` hides the picker.** With `modelSpecs` set and `modelSelect` omitted,
upstream defaults it to "`addedEndpoints` non-empty", so spec-only configs arrive `false`: the picker
and Compare's quick picks list only specs (`showsRawEndpoint`). With no specs there is no picker
(`ChatUiState.modelPickerShown`): the model chip and new-chat label are plain text, the tools-sheet
row and Compare hide, and the preflight states its reason without a sheet. A start-chat link still
wins over the spec, as on web.

**The selection is one value, `ModelChoice`** (`Raw` | `Spec(name, endpoint, model)`), written whole
via `ModelSelectionDelegate.applySelection`; the preset load, the queued-edit composer swap and the
seeder's spec-config update instead write it in the same emission as another field. Build a `Spec`
from config (`SpecSeed.toChoice`) or a stored value (`ModelChoice.restore`); the type doesn't prove
the spec is still offered. Don't add a separate spec field to the selection, queued turns, snapshots
or the handoff. Compare's staged picks and `LastUsedSelection` keep separate fields and convert at the
boundary; the paused-run pin (`ResumeTurnPin`) records what was sent and stays as it is.

**The spec name travels with the selection and is sent as `spec`.** A raw pick clears it. Nothing
infers a spec from a model match, except under `enforce`, where a Tier-0 entry point adopts the spec
when exactly one opens on its pair (`uniqueSpecFor`); with none or several the preflight asks. Every
send path carries it: queue and composer snapshots, the new-chat handoff, the conversation cache
(`spec` column), comparison (on send only), presets, edit/regenerate and paused-run pins. Sending
`spec` matters even without `enforce`: the server merges the spec's private preset fields
(`promptPrefix`, `instructions`, `system`, …).

**A spec's preset becomes the chat's parameters** (`util/SpecParameters.kt`). Without `enforce` the
server applies none of the preset's public fields, so the client sends them. **Only the keys of the
selection's parameter set go out, and the server unsets every stored field a request omits**
(`getUnsetFields`): a dropped or misspelled key erases what web set. The set is
`ChatUiState.parameterTarget` (a custom endpoint's `customParams.defaultParamsEndpoint`, else its
`type`, never its name, and an agent's provider resolved the same way); sheet, payload and
`savePreset` all read it. Spellings differ per provider
(`top_p`/`topP`, `max_tokens`/`maxOutputTokens`/`maxTokens`): write via
`EndpointParameterRegistry.wireKeyFor`.
- Only a *change of spec* touches the parameters (`acrossSpecChange`), never a re-emission of the
  same spec, so the user's tweaks survive config refreshes. Onto a spec: its preset over the
  defaults; off a spec onto a raw pick: the defaults.
- A config that drops the chat's spec (the stored-spec table below; on the landing, the seeder's spec
  once it isn't served on its pair) drops the name and keeps the parameters. The next move to another
  pair then resets to the defaults unless the user tweaked a parameter in between (`DroppedSpecMemo`).
  On the landing a retargeted prioritized spec re-applies through the tiers with its new preset.
- A user preset replaces the spec's values; only a preset naming a spec and nothing else layers over
  it. `savePreset` omits values equal to the defaults.
- A conversation's own spec never re-applies its preset (`applySelection(specPreset = false)`); a
  reopened chat restores stored parameters. The list route sends none, so list upserts keep the
  cached `modelParams` blob (`parametersAsOf`); a missing or stale blob is fetched once per process
  and version, and replaces the cache only if the user changed nothing meanwhile (a config refresh is
  not the user's change: `SelectionWatches` follows the seeder's rewrite). A handed-off new chat takes
  the landing's parameters from `NewChatSelectionHandoff`; the next new chat starts from the carried
  spec's preset.
- Under `enforce` the server replaces the body with the preset: the sheet is read-only while a spec is
  active (`parametersSetBySpec`, and `updateModelParameters` ignores it) and the parameters are the
  preset even on reopen and after a loaded preset. Tool flags still apply (`ephemeralAgent` is read
  even under `enforce`).
- Spec `artifacts` go out only as `ephemeralAgent.artifacts` (`true` → `"default"`). The gauge takes
  the spec's `maxContextTokens` after the agent's and before token-config (`modelCatalogKey`:
  `vertexai` → `google`).

**A spec's pair comes from `preset.endpoint` alone** (`toSeed`): the server materializes inferred
endpoints, so the client infers nothing; a stray `agent_id` on another endpoint is ignored, and
`endpoint: null` opens nothing.

**`enforce` has no exception for `addedEndpoints`**: with `modelSpecs.enforce` and a non-empty list
the server rejects any send without `spec` (`buildEndpointOption.js`). `requiresSpec` blocks it in
the preflight and the picker greys the raw rows `addedEndpoints` lists (with none it lists no raw
rows). A cached conversation with no spec is refetched once per account and process and read against
the fetched config, not the selection; a failed refresh retries on the next open.
- Compare's second lane follows the same rule though the server checks only the primary: its picker
  and quick picks list no raw rows (`SendBlockReason.SelectSecondaryModel`).
- A follow-up queued during an agents run goes to the server queue only on v0.8.9-rc1+ for a served
  agents spec whose preset names the chat's agent and whose name is unique: the queued turn carries
  no `spec` and only those are restored server-side. Anything else stays on the local drain
  (`QueueOrchestrationDelegate.serverOwnedSpec`).

**A user-key endpoint with no key is refused before sending** (`SendBlockReason.ApiKeyRequired`);
the send goes through while the key state is loading or unknown. With no picker the reason is text only.

**A stored spec is held as the server holds it** (`ModelChoice.stored` / `storedAgainst` in
`util/SpecSeed.kt`, a port of `resolveModelSpecForEndpoint`): the server resolves it against its full
list, hidden specs included, and checks the endpoint only.

| Stored spec | Server, no `enforce` | Server, `enforce` | Mobile |
|---|---|---|---|
| Not served, hidden | applies it | applies it | kept |
| Not served, deleted | ignores it, runs raw | "Invalid model spec" | kept (can't be told from hidden) |
| Served, preset on another endpoint | "Model spec mismatch" | "Model spec mismatch" | dropped to raw |
| Served, same endpoint, another model | applies it on the stored model | preset replaces the body | kept on the stored model; under `enforce`, moved to the spec's pair |

It applies at every writer of a stored selection: conversation load (`applyResolvedConversationModel`),
config change on an open chat (`withSpecsRefreshed`), preset load (adopting the spec's own pair when
the preset names none), a queued turn restored or drained (`applyComposer`, `doSendWithSpec`, which
re-derives the drained turn's tools, parameters and web-search route on the held choice) and
Compare's picked lane B. The preflight accepts any kept name; the three spec refusals
(`isSpecRefusal`) open the picker with "Select a model" beside the server's sentence.
- The seeder's own landing pick (`seededAgainst`) holds only specs served on that pair; one no longer
  served there re-seeds through the tiers.
- A stored *pick* (last-used restore, model shortcut) follows the spec to its current pair
  (`reopenedPick`).
- The header chip shows a kept spec the list doesn't serve by its model name, where web shows its
  "Select a model" placeholder (and still sends the spec). Neither has a picker row for it.

**Model shortcuts** (mobile-only) count usage per pair and spec and carry the spec in the link
(`librechat://model?…&spec=`). Before publishing, reconcile with the cached spec config
(`ModelRef.reconciledWith`: a spec no longer listed drops out; no config yet leaves the list as is).
A shortcut reopens its spec on the spec's current pair. A spec-less one opens its raw pair only where
the picker lists that endpoint (`showsRawEndpoint` with the live `modelSelect`), else the one spec on
the pair, else nothing; under `enforce` the one spec on its pair, else the raw pair for the preflight.
One applied before the config loads is resolved when it arrives.

**Spec rows** (`visibleSpecs`) sort by `order` (web ignores it) and hide an agent spec only once the
agents list loaded without its agent and is not a full page (`AGENTS_LIST_PAGE_SIZE`; web loads every
page, mobile one), so a full page hides nothing; until then, and after a failed fetch, every spec
shows. Groups follow web's `ModelSelector` (`util/SpecGroups.kt`), with one deliberate divergence: a
spec grouped under an endpoint with no models gets a group of its own (web hides it), which takes that
endpoint's label and icon except under `modelSelect: false`. An endpoint that needs the user's key keeps its
grouped specs under the greyed "Add key" header and hides its raw models.
`pickerRows(PickerInputs)` (`util/PickerRows.kt`) builds the whole picker as data and the sheet only
resolves strings, icons and clicks. Under `enforce` with greyed raw rows, the one (mobile-only) note is
the first row, above Starred.

**Spec icons** (`iconValue`/`resolveIconSource` in `:core:model` `media/IconUrls.kt`, wrapped by
`util/SpecIcons.kt`): the spec's `iconURL`, its preset's, its agent's avatar, then its endpoint; a
value is an image by web's `isImageURL` rule, `agents` draws the agents mark, anything else names an
endpoint or provider to borrow from, and `""` is unset. `showIconInMenu: false` hides it in the picker and quick picks. Conversation rows follow
web's `EndpointIcon` rule: a non-image `iconURL` counts only when it is an endpoint key.

**Brand art** follows web's `resolveProviderIcon` (`:core:model` `media/ProviderIcons.kt`). Pass
`endpointConfigs` to `resolveIconSource`/`SpecIcons` and `EndpointIcon(provider = …)` wherever
configs are in reach, or a custom endpoint draws a monogram. **No third-party logo art is bundled**
(brand terms; F-Droid NonFreeAssets): art loads from the user's server at `{server}/assets/<file>`
(`providerAssetUrl`, subpath-aware), else a two-letter monogram tile, unique per provider. Never tint
loaded art (near-black art sits on a light tile in a dark theme). Web's `byModel` and `modelMakers`
refinements aren't ported.

**Reply avatars are view data.** A bubble never resolves its own icon: `ChatViewModel`'s `uiState`
combine fills `MessageNode.avatar` (`util/ReplyAvatars.kt`), because the server URL and configs
arrive on their own flows; `ReplyAvatarMemo` skips the walk while path, URL, configs and agents are
unchanged. The rule is web's `MessageIcon`, not the picker's: a non-image `iconURL` counts only as an
endpoint key, and an agents reply draws its agent's avatar (the server saves no avatar in `iconURL`).
The streaming reply and Compare lane B (`ChatUiState.avatars`) use the same rule
(`ModelSpec.replyIconSource`), so a reply keeps its icon when saved.

**Model spec fields.** Fields decoded but unused, or not decoded, are deliberate; don't "fix" them in
a sync (undecoded ones are also listed in `ModelSpec`'s KDoc). The served list is already through
`sanitizeModelSpecs`, which strips `skills`, subagent ids and the private preset fields. All other
spec fields are honoured.

| Field | Status | Why |
|---|---|---|
| `groupIcon` | honoured | An endpoint-named icon draws that endpoint's brand (deliberate divergence) |
| `showIconInHeader` | decoded, unused | The header chip is text-only by design |
| `askUserQuestion` | server only | No composer control; web sends none |
| `skills` | decoded, inert | Stripped by `sanitizeModelSpecs`; the server applies it |
| `authType` | not decoded | Dead upstream |
| `runInBackground`, `describeIntent`, `subagents` | not decoded | Server-resolved by spec name; web reads none |
| preset `endpointType` | unused | Taken from `EndpointConfig.type` |
| preset private fields (`promptPrefix`, `instructions`, `system`, `context`, `examples`, …) | server | Never served; the server fills them when the request names the spec |
| preset `artifacts` | not decoded | Only the spec's top-level `artifacts` counts |
| preset `codeApprovalMode` | honoured | A new undecided chat's starting mode only; never remembered |
| preset `codeEnvironmentMode`, `codeWorkspaces` | ignored | The chat sends its own workspace choice |
| preset `tools`, `additionalModelRequestFields`, `assistant_id`, `append_current_datetime` | not used | Legacy / no feature / the assistants endpoint is unsupported (`assistant_id` is never sent) |

**Server limits the client accepts** (don't "fix" without a server change):
- Under `enforce`, a spec with `webSearch` and preset `web_search` on OpenAI runs both searches.
- A custom endpoint's `addParams.web_search` turns the built-in search on whatever the request sends,
  and is never served, so it can run beside LibreChat search or with the control off (web too).
- A hidden spec (`showInMenu: false`) isn't served, so the client can't see its preset or tools, but
  the server applies them: its `enforce` preset `web_search` and its `webSearch` flag both run
  beside whatever the control sends.
- **Lane B's web search:** with an ephemeral primary, lane B copies the primary's tools (`added.ts`)
  with empty `model_parameters`, so only LibreChat search reaches it (hence comparisons route to
  LibreChat search). A spec whose preset sets `web_search`, or a chat that `keepsBuiltInSearch`, keeps
  the built-in search on the primary, so lane B gets none (web too). With a saved-agent primary, lane B
  searches only if its own spec sets `webSearch`.
- Keep this answer loses the Stopped chip (`POST /api/messages/branch` saves `unfinished: false`).
- A stored `url_context: true` is still sent after its control is hidden (the server skips it).
- A preset loaded onto a spec chat keeps a spec (divergence: web nulls it): the preset's own, or the
  chat's when the preset names no endpoint, model or spec (`PresetPromptDelegate.loadPreset`).
- No `@` mention switches spec, model, agent or preset (`@` inserts prompts only), and web start-chat
  URLs (`/c/new?spec=…`) aren't handled; only `librechat://model?…&spec=` is.
- Untraced (v0.8.8, web too): every continue saves a stray user message with parent `NO_PARENT`.

## Message Tree
Flat messages linked by `parentMessageId` (root `NO_PARENT`); `buildActiveMessagePath(messages,
activeBranches, streamingLeafId)` in `MessageNode.kt`. Edits and regenerates create siblings.

### Streaming anchor (in-place edit/regenerate)
- The live reply is a trailing `StreamingMessageBubble` outside the tree. At stream start each send
  path rebuilds `displayMessages` with `streamingLeafId` = the message the reply attaches to, which
  truncates the path there. No stored anchor; on Final `finalizeChatDisplay` rebuilds in memory
  (only comparison chats reload).
- **Invariant:** while streaming, nothing may write to Room or mutate `activeBranches`, or the
  `loadConversation` observer re-emits without `streamingLeafId` and un-truncates. `switchBranch`,
  edits, `regenerateMessage` and `submitFeedback` are gated on `!isStreaming`.
- **Handed-off new chat:** the server persists the request message only at Final (or when a client
  drops its stream), so `NewChatSelectionHandoff` carries the optimistic message, `ChatViewModel.init`
  seeds it, and `loadConversation` keeps it appended until a server row is its copy: same id **or**
  `isServerCopyOf` (user-authored, same parent, same text), since rc3+ servers don't adopt the client
  id. **Don't replace this with a network reload on Final.**
- **A resumed run's `unfinished` partial stays off the thread** while the stream is live
  (`MessagesState.resumedReplyId`, `withoutLivePartial`); only the `unfinished` copy is hidden, so the
  finished reply (same id) shows. It must not be a `combine` input. The ends that reload nothing
  (`AbortFallback`, `ResumeFailed`) rebuild the path.
- **Optimistic id:** the minted id goes as `ChatRequest.messageId`. Servers before v0.8.8-rc3 adopt
  it; rc3+ don't (`resolvePreallocatedUserMessageId`), so the Final's `requestMessage` merges as a
  sibling and the newest-sibling default shows it; in a normal chat the never-persisted optimistic row
  drops on the next Room emission. Normal chats persist the finalized turn via `cacheMessages`, not a
  network reload. Regenerate/continue/edit-AI send no `messageId`.
- **Completion render:** items are keyed by slot (`MessageNode.treeParentKey`) and the streaming
  bubble by the slot its final message takes; the finalize clears streaming fields in the same update
  that swaps the message in; the last message parses markdown synchronously
  (`LocalImmediateMarkdown`). Reverting any of these brings back the completion flicker.

## Editing a message
`editShapeOf` (`util/MessageEditing.kt`) picks the editor and gates the Edit button with the same
call: never offer a shape `MessageEditingDelegate` refuses. `PLAIN_TEXT` saves `{ text }` with no
`index` (a reply rerun is a plain regenerate, and the footer says the draft is discarded);
`CONTENT_PARTS` edits TEXT/THINK parts (not text carrying `tool_call_ids` or artifacts), with the
other parts read-only beside them (divergence: web hides them); `NONE`: no Edit.
- A save closes the editor only after the server took it; a failure keeps the draft and reports
  `ChatNotice.MESSAGE_SAVE_FAILED` or the server's own sentence. Per-part saves run sequentially (each
  `PUT` rewrites the whole content array), and only the baselines that landed advance
  (`partOriginals`).
- Saves are gated on `!isStreaming`, affordance and sink.
- `editedContent` and the wire `responseMessageId` are a pair (the server reads the latter as the
  history head; without it the prefix is silently dropped). Only the one-changed-TEXT-part rerun sends
  them; any other change must be saved first.
- A changed THINK part is never resubmitted (v0.8.4/v0.8.7 write `undefined` over the reasoning); a
  think change counts as a second changed part, so the user must Save first. This is why there's no
  version gate.
- Known divergence: a rerun streams as the trailing bubble and looks like a restart until Final (web's
  `editPrefixLength` seeding isn't ported).

## Compare Models
- A comparison send carries `addedConvo`; the server saves ONE assistant message whose parts carry
  `agentId`, lane B's suffixed `____N`.
- **Live attribution:** deltas carry only a step id. `SseEventMapper` records each `on_run_step`'s
  agentId by step id and resolves deltas by their own step id (the agents' steps interleave).
- Each pane filters parts via `util/ParallelContent.partsForPane` (the streaming buffer is only a
  Final→reload gap fallback); the single list runs `collapseParallelToPrimary`.
- **A lane renders without activity grouping** (upstream), keyed on `groupId`, not the `____N`
  suffix (`partsForPane` has already stripped it). Steer segmentation still applies. Branching strips
  `groupId`, so a kept reply groups again.
- **Attachments are scoped to their lane** (`util/ComparisonDisplay.attachmentsForPane`): an
  attachment follows the lane whose parts contain its call; one owned by no call renders once, in the
  primary pane. Ownership = the recursive `outputToolCallIds` walk, **never** `renderedToolCallIds`.
- **Reopen** (`ComparisonModeDelegate.rehydrateFromMessage`) rehydrates from part attribution, once
  per load, and respects a session toggle-off. Keep this answer = `POST /api/messages/branch`;
  `branch` merges the returned message in the same update that ends the comparison and writes it to
  Room first outside temporary chats. Temporary chats depend on that merge.
- **Lane B's spec on reopen.** Nothing persists lane B's spec; `laneBSpecFor` reads it back from the
  sender in the lane id (`modelLabel ?? spec.label ?? modelDisplayLabel`): the one spec on the pair
  with that label, else the one on its endpoint with that label (an admin moved its model), else,
  under `enforce` only, the one spec on the pair. A hidden spec is never served, so its lane reopens
  without it (a limit). A lane B spec picked this session follows the stored-spec rule instead. It is
  re-read on every use (a reopen can land before the spec config); screens read
  `ChatUiState.laneBSpec`, never `secondarySpec`. Outside `enforce` a sender equal to the endpoint's
  `modelDisplayLabel` reopens raw (a wrong spec would send a preset unasked), as does any read before
  the endpoint config loads. **Depends on `buildAddedConvo` sending no `modelLabel`.**
- **Lane B's settings.** `buildAddedConvo` copies the spec preset's sampling fields flat under the
  names `packages/api/src/agents/added.ts` reads (not `ModelParamPayload`; v0.8.8 applies them only
  with a saved-agent primary), and copies the primary payload's `promptPrefix` (or Bedrock `system`):
  the user's custom instructions are the only primary setting that reaches lane B; drop it and lane B
  runs without them. Not for a saved-agent lane or primary. Use the turn's own primary, never the live
  selection.
- **Edit and regenerate carry lane B; continue and compaction don't.** A comparison can't be continued
  as one (v0.8.8 rewrites or drops a lane), so `continueGeneration` refuses while comparing and each
  pane offers **Keep and continue** (`keepAndContinue`): branch, wait for the branch reload's
  `onSettled` outside temporary chats (a reload landing mid-stream writes Room), then continue
  single-model. Both panes offer it (the server records `unfinished` per message, not per lane). A
  pane needs its lane's agent id (`onFinal` fills a missing one from the final parts); a lane with no
  parts can't be kept. Keeping lane B first switches the chat to lane B's model, spec and parameters
  (`parametersLaneRanOn`: its spec's preset, or raw the defaults, with lane A's custom instructions).
  Its spec is held against the config (`stored()`), so under `enforce` a retargeted spec moves to its
  new model. It writes last-used like any pick, except in a temporary chat.

## Continuing a cut-short reply
The chip (`StoppedReplyFooter`) sits under the latest reply, always visible, not in the tap-to-reveal
action row. `ChatUiState.continuableReplyId` (`util/StoppedReply.kt`) is **not web's rule** (web's
`finish_reason` test almost never passes on v0.8.8 agents): `unfinished` counts. A pending approval or
question also carries `unfinished`, so on a reopened chat the chip waits for `/api/agents/chat/status`
(`runStatusSettled`) and stays hidden if that fails (retried with backoff and on reconnect). No chip
for an error, a compaction summary, a reply with no user turn above, or a comparison reply outside a
comparison.

## Content Rendering
- `ContentPartRenderer` dispatches by `ContentPart.type`. Encrypted reasoning is filtered by
  `reasoningDisplay` (`:core:model` `content/OpaqueReasoning.kt`) for display, find, copy and the
  editor (payload-bearing reasoning is read-only there); not for the search index.
- **Markdown images** go through `MarkdownMedia.sourceFor`, the one gate before any request.
  **Deliberate divergence:** a third-party host loads only after a tap (remembered per server and URL
  for the process); don't "restore parity". The turn's own files, by name, and the server's origin
  load at once; the server's host on another scheme or port is named by its whole origin. `http` from
  an `https` server never loads (the bearer is host-scoped), nor does a host the chip can't name
  exactly as Ktor parses it.
- **Run Code**: behind RUN_CODE.USE via `LocalCodeRunHost` (only `ChatScreen` provides it: no shared
  links, tool cards or artifact panel). A block already running ignores taps; stored runs are read
  once per conversation, when a runnable block first shows. The block index must match web's
  (`fenceIndex`, `webCodeBlockCount`; mirrored); a part split by artifacts or `\ui` markers threads
  each chunk's base (`ProvideCodeRunBase`). `CodeRunDelegate` keeps its own flow, not `ChatUiState`.
  Never on the live bubble (the server would 404), user turns, Compare lanes, or while editing.
- **Inline `$…$`** follows Pandoc's `tex_math_dollars` neighbour rules (`scanInlineLatex()`); `\$`
  never delimits and nothing inspects the span's content. Code wins. While streaming, a closer at the
  buffer end is deferred.

### Long user messages collapse
`CollapsibleUserText` clamps in the measuring pass (first frame already clamped). Visual only: the
text stays composed, selectable and accessible. The focused search match forces it open. Off while
editing. Steers don't collapse (web's do).

## Inline citations (web_search / file_search)
Grammar ported from upstream `client/src/utils/citations.ts` to `:core:model` `content/Citations.kt`.
Models write both the private-use character and its literal escape: convert the escape first.
- `CitationIndex` resolves `turn{T}{type}{I}`; file results keep their own turn map (upstream shares
  one).
- Never touch code spans or fences. A group never splits across leaves. Table cells draw a clickable
  label instead of a pill. While streaming, hold back a partial anchor at the tail.
- Divergence: the pill names `attribution` only when it differs from the title, else the domain.
- Find-in-page renders without citations and `countMarkdownOccurrences` counts the same stripped
  text: keep them in step. Read-aloud, a11y, the search index and previews strip them; Copy numbers
  them `[1][2]` with a `Citations:` footer.

## Search cards and sources
- Only the built-in `web_search` always gets `WebSearchCard`; other "*search*" tools only when they
  produced web sources. After a Stop the sources come from the text output. Divergences: inside an
  activity group a card with no `intent` shows its query; snippets are plain text and ISO dates a
  short local date.
- Every vertical URL must be http(s); vertical pictures load only after a tap (divergence).
- `SourcesPanel` only on parallel content, one per Compare pane (upstream: one above both).

## Streaming
- **Ownership:** `StreamingManagerDelegate` owns the session lifecycle only (dispatch, `endStream`,
  resume, reconnect). Text/reasoning deltas → `StreamTextBuffer` (50 ms flush). Events describing the
  reply in progress (tool calls, attachments, usage, title, traces) → `LiveReplyDelegate`. Put new
  events in the matching one. Two-phase transport: `core/network/CLAUDE.md`.
- **Reasoning has its own buffer** (`streamingThinking`) and follows `streamingContent` everywhere:
  cleared in the atomic finalize, kept with the partial on error/abort, neutralized for chrome.
  Comparison panes fold reasoning into their own buffers. Accepted at settle: the live block's expanded
  state doesn't carry to the saved THINK part, and a multi-step reply's reasoning redistributes.
- The bubble's sender is `replySenderName`: the agent's name, else the spec's label, else the model
  (Compare's pane A too). A raw model's reply takes the server's name only at Final.
- **The live bubble renders through `TextContentPart(streaming = true)`.** The `streaming` flag must
  stay threaded to every `MarkdownContent` and `CodeBlock` beneath it (else a per-delta Loading flash
  and cache pollution, see `CachedMarkdown`'s KDoc). The bubble passes no `searchQuery`.
- **The cursor is inline content** (`StreamingCursor.kt`): a U+2063 sentinel swapped for an
  `InlineTextContent`. Never cache a sentinel-bearing AST or serve one to a settled render. The
  block-level fallback is decided at **three** places, not just `canHostInlineCursor`:
  `TextContentPart` (artifact tail), `MarkdownContent` (search active), `canHostInlineCursor` (code,
  tables, LaTeX, text inside an *unclosed* fence).
- **MCP sign-in mid-run** (`ToolCallAuthRequired`): nothing launches without the user's tap; the run
  resumes by itself. On reconnect the sync frame carries no auth, so `SseEventMapper` replays
  `resumeState.pendingOAuthPrompts` after the snapshot, preceded by a `ToolCallStart` only when the
  snapshot lacks the call. `runSteps` are deliberately not replayed. Server limit: a prompt raised
  inside a real tool call is not projected and is lost on reconnect (web too). A subagent's prompt
  lands on the parent `subagent` call (`LiveReplyDelegate.onAuthRequired`) until the child call ends.
  Comparison panes route it too (`ComparisonModeDelegate.routeToolCallAuth`), subagent prompts
  included. A success needs no follow-up beyond the status reload every sign-in end triggers
  (`McpSignInUiState.ended`).
- **Stream termination chokepoint.** Every end goes through the private `endStream(reason)`
  (`Finalized`/`StreamError`/`AbortFallback`/`ResumeExpired`/`Reconcile`); a flow ending with neither
  Final nor Error ends as `Reconcile`/`ResumeExpired` unless a Stop is pending. A per-session counter
  latches one end. **Add teardown to the reason table, never at call sites.** **Nothing reloads on an
  abort path** (the server emits the aborted frame before persisting). `Finalized` writes no state.
  - `SseClient` completes a flow without Final only on a resume **404** or an account switch. **A
    body that merely closes is not a finished run** (servers before v0.8.9-rc1 send no keepalive;
    proxies idle-close): it resumes with `resume=true`. The attempt counter resets on a parsed event,
    not bytes.
  - Retry exhaustion (`RETRY_EXHAUSTED`, `STATUS_RETRY_EXHAUSTED`) is not a network error: ask
    `/chat/status`: resume if active, else end like a 404 (refetch, no banner). Resume at most once
    per run: the flag resets at every attach (local send, opening a live run, foreground, network
    recovery) but not at the ceiling's own re-attach, so an unreachable live run ends instead of
    looping. Only a failed status read is an error. 401 stays in-band and is never adjudicated. An
    account switch ends nothing (a reload would use the new account); the same guard runs after the
    status read, so a switch during it neither resumes nor reloads.
- **Stop** POSTs `abortChat()` and **must not cancel the stream job**: the server ends the run with a
  `final` frame flagged `aborted` that carries the partial, and `handleFinal` reads `aborted` off the
  frame. On Stop: no auto-TTS, no title, no queue drain (the hold is re-asserted); `abortRequested`
  guards double taps. A 15 s watchdog ends locally (`AbortFallback`, partial kept, no reload). Before
  `created` the server aborts the caller's *oldest* job (known gap with two live streams).
- **Aborted finals follow the server's persistence contract** (`applyAbortContract`,
  `util/FinalMessages.kt`). `abortPersistedServerSide()` mirrors the abort route's save gate.
  Persisted → rebuild `text` with the `parseTextParts` port (TEXT + THINK + STEER, the server's
  literal-space join; `flattenSearchableText` in `:core:model`) so the cache equals a refetch. Not
  persisted → drop the response (else a phantom parent). `cacheTurn` is then unconditional (it is
  the only write that persists the optimistic user message). `saveConversation` is suppressed; the
  title is re-read network-first and never applied as a downgrade to `New Chat`. **Both
  `parseTextParts` and the save gate are mirrored server logic: re-verify on upstream bumps.**
- **earlyAbort = un-send** (`StreamingHost.unsendTurn`): remove the optimistic message (one emission)
  and restore its text to the composer. The id is null for regenerate/continue/edit-AI, whose
  persisted user message must never be removed.
- **A failed send restores its text unless the server kept it.** Before `created`: un-send. After:
  a non-network error reloads with `loadConversation(unsavedTurn = …)` and restores the text when no
  row matches. Network errors are excluded (the run may be live). A `ChatRefusal` on an existing
  non-temporary conversation reloads too (upstream `denyRequest` saves the message).
- **The final-frame merge is monotonic** (`Message.mergedOver`): incoming wins where it carries
  information, and `error`/`unfinished` always come from the frame. Use it only at the final-frame
  chokepoint; full-record syncs (`getMessages`, `refreshMessages`) must never use it.
  `finalizeChatDisplay` returns the post-merge instances, so `cacheTurn` writes exactly what's shown.
- **Backgrounding:** `onPause` must not cancel the collector while a Stop is pending; `onResume`
  touches nothing when the session already ended.

## Key Components
| Component | Purpose |
|-----------|---------|
| `ChatInput` | Text field, send/stop, attach, voice |
| `MessageBubble` / `MessageList` | Bubble with action bar; LazyColumn with scroll-to-bottom |
| `ModelSelectorSheet` | Specs, spec groups, endpoint-grouped models |
| `SiblingNavigator` | "1/3" branch chevrons |
| `LandingContent` | Greeting, description, icon (`LandingStarters` dock apart) |
| `StreamingCursor` | Live insertion point; `StreamingWaitIndicator` before the first delta |

## Prompts
- `PromptsViewModel` loads **every** group (unpaginated `/api/prompts/all`) because sort and filter
  run client-side; don't page it.
- **`PromptRepository.revision` is the single reload path after a mutation**, bumped inside the
  repository after success. A ViewModel mutation must **not** also call `refresh()`. Consumers reload
  from composition (`refreshIfStale`, keyed on the revision), cancel-replace. Each records the revision
  it loaded and the one it is fetching, so a re-entry with nothing changed is free, the first
  composition doesn't duplicate `init`'s load, and a failed load is retried on the next visit. The
  chat's reload sits behind `PROMPTS.USE`; the revision-driven library reload swallows its errors.
- **A save is up to three requests; `saved` flips only when all landed** (`PromptEditorScreen` pops
  on it). `PromptRepository` methods return `Result.Error`, never throw: a `try/catch` is dead code.
- **A body edit isn't saved until promoted:** `addPromptToGroup` leaves `productionId` on the old
  version, so `publishNewVersion` follows with `updatePromptProductionTag`.
- **Two routes break the bare-object convention:** `POST /groups/{id}/prompts` nests under `prompt`
  both ways (flat → 400); `PATCH /{id}/tags/production` takes no body and answers `{ message }` with
  200 regardless. `AddPromptToGroupResponse.prompt` is non-nullable so that fails to decode.
  `PromptsApiWireShapeTest` holds live captures.

## `ask_user_question`: one question, a panel then a card
Never show both at once. While paused, `AskUserQuestionPanel` replaces the composer;
`withoutUnansweredQuestions()` drops the question from the streaming lists. Once answered it renders as
`AskUserQuestionRecordCard` from the wire alone (`parseAskUserQuestion` takes object or string
`args`); nothing is stashed client-side.
- **Stop appears only after a resume failed** (`pendingActionResumeFailed`).
- Panel submits never re-home text into the hidden composer; only `submitAnswerFromComposer`
  restores.
- Show option labels only when every answer segment maps (`askAnswerDisplay`; values may contain
  `", "`). Skip posts `ASK_USER_DECLINED_ANSWER`. A call with `inputValidationError` says it was never
  put to the user.
- Both pause panels (ask and tool approval) keep their drafts, active item and collapse state in
  `MessagesState`, retained across reconnects by `PendingActionDelegate`, not in composable
  `remember` (a fold or backgrounding would lose the user's answers).

## Tool approval: one call at a time, above the composer
- `ToolApprovalPanel` docks above the composer (which keeps its Stop, so the panel has none).
- **Auto-advance is decided in `PendingActionDelegate`**, not the panels, and is cancelled when the
  layout leaves the screen. Compact: a decision on an undecided call moves on and the one completing
  the batch submits; the wide layout's advance never submits. Edit and Respond never advance on their
  own; focusing Reject's reason field cancels the beat. Approve all / Reject all appear only when every
  call's policy permits that decision, and submit at once.
- Wide paging starts only from the `swipeHandle` modifier (option rows / decision row; text fields and
  arguments don't page) and is rubber-banded at the ends, so it never submits. Dragging along the tabs
  selects the tab under the finger (Initial pass). Fling velocity comes from the accumulated delta,
  **never `change.position`** (it stands still under the finger on a device; the instrumented harness
  can't tell).
- **Always allow** = `approve` + `scope: session`. `toolBatchResolutions` strips the scope from calls
  not offered it (the server 403s the whole batch). "Approve all" stays plain (not session-scoped). A
  resume 403 whose body carries `disallowed` maps to `ChatNotice.TOOL_DECISION_NOT_PERMITTED`, not the
  fingerprint copy.
- **Code-action previews** only for `source == librechat_code`, never inferred from the name (port:
  `core/model` `approval/ApprovalPreview.kt`, mirrored). Build them once per batch at the top of
  `ToolApprovalPanel` (one shared 64K budget); the cap applies after the reveal. **The
  character-reveal set is exactly web's** (zero-width characters, the BOM and the soft hyphen are not
  revealed); **don't widen it in one place.** Edit seeds from the raw args, never the bounded preview.
  Divergence: the truncation note drops web's "use the timeline tool card" clause.
- The resume is all-or-nothing (`toolBatchResolutions` is null until every call is decided and
  complete; the route 400s a partial batch and an edit/respond without its payload). Join decisions by
  `tool_call_id`, never position.

## During-run send: queue vs steer
- **Queue** (`MessageQueueDelegate`): the next turn; works everywhere; what steer degrades to.
  **Steer** (`SteeringDelegate`): injected at the next tool boundary; needs the steer route
  (`steeringSupported`, see VERSION_GATES.md). `canSteerNow` (existing conversation, not comparing,
  steer route, no live pause) decides; the composer never does.
- **The button's face and action come from the same value** (`duringRunSendTarget` →
  `sendButtonModeFor`), never from the preference.
- **Invariant: no steer path may lose the text, duplicate it, or send text the user withdrew.** Every
  rejection, failure and lost race re-homes into the follow-up queue, never the live-send path
  (`runWhenSendReady` may refuse); `enqueueSpec` self-drains, so an ended run still sends at once.
- Re-homes into the self-draining queue are announced on a one-shot channel, **not** `error` (an
  inline drain clears `error` before it renders). Re-homes into a held or paused queue are not.
- A steer carries the `QueuedMessage` spec minted at send time; never rebuild it at failure time.
- **Claim-on-read:** the `final` frame, the abort ack and `/chat/status`'s `unrecoveredSteers` hand
  back un-injected steers. `checkStreamStatus`/`abortChat` take a required `claimSteers` lambda; the
  final frame's copy is claimed outside `handleFinal`. Dedupe by steer id. `reclaimLocalChips()` runs
  on a stream error and must NOT run on `Finalized`.
- **One record per steer** (`SteeringDelegate.records`). `clear()` clears chips and KEEPS records
  (they block late re-homes and resurrections). Settled records are tombstones bounded at 32; live
  ones are never evicted. The map is Main-confined: no `withContext` in the delegate; never mutate it
  inside `handle.update {}` (a CAS loop that may re-run).
- Steering is text-only: a send with attachments goes to the queue, and the menu disables Steer and
  Interrupt while attachments are staged.
- **Interrupt** is a steer with `preempt: true` (every no-loss rule applies), falls back to the queue
  or a plain steer, and never becomes Stop + send. The interrupting look comes only from the server,
  never from the request. "Interrupt now" (`SteeringDelegate.arm`): one at a time, one attempt, and
  only `armed: true` changes the chip; an arming or interrupting chip disables the others (not hides).
  Outcomes are `SteerNotice`s, never `error`. `on_steer_updated` is v2-only and never reaches mobile,
  so a withdrawn interrupt keeps its label. The queue fallback (`!canSteerNow`) is silent; the
  plain-steer fallback (202 echoes `preempt != true`) is announced
  (`SteerNotice.INTERRUPT_UNAVAILABLE`). A known record takes the server's `preempt` flag; the higher
  `preemptRevision` wins.
- A tool the interrupt cancelled (`isInterruptedToolOutput`, mirror `steer-interrupted-tool-result`)
  renders "Interrupted by your message" in a neutral tone (an interrupted search keeps the generic
  card), and a group header counts it as cancelled, never failed.

## Composer chrome (what sits around the field)
- **Everything the composer draws around its field is one view-data holder, `ComposerChrome`**, built
  in `ChatScreen` and threaded through the `ChatComposer` expect, both actuals, `ChatInput` /
  `IosChatInput` and `ChatInputState.chrome`. A new row adds a field there, never a parameter to those
  five signatures (the iOS ones only compile in CI).
- **A vertical drag on the composer scrolls the thread** (`Modifier.passVerticalDragsToThread()` →
  `LocalThreadScrollHandle`), watched on the Main pass so a child that claims the gesture keeps it;
  the field and the `/` `$` picker don't carry it. A drag is ONE `scroll(MutatePriority.UserInput)`
  session, so `TailFollower` reads it as the user scrolling away: never one session per delta.
- Steers and the queue share one region capped at 168dp, scrollable only while it overflows; past its
  edge a drag or fling goes on to the thread (`ThreadHandOffConnection`).
- Over the composer's transparent top, Material chip fills are opaque
  (`AdaptiveChipDefaults.overlay*ChipColors`). No composer chip has a caret.

### Send animation (`components/sendflight/`)
- A send's turn comes in through four modifiers, in order: the user's sender line (`sendFlightHeader`)
  and bubble (`sendFlightBubble`, before its background), then the live reply's sender line
  (`sendFlightReplyHeader`) and content (`sendFlightReplyBody`). A new user-message or live-reply
  layout on either platform carries its pair, with everything else inside one of them, or that part
  appears unanimated.

### Attachment strip (files, picked skills, quotes)
- **What goes with the message is one list, `DraftItems`** (pure `draftItemsOf`), drawn as one
  sideways-scrolling row, never a second row. Upload progress stays out of it and out of
  `ComposerChrome`: the rings read `DraftUploadProgress` as they draw, so a tick re-maps nothing.
- **Removal, retry and an upload's outcome name one tray entry (`AttachedFile.key`), never the file**:
  the same content `Uri` picked twice is two entries.
- **One Undo is pending at a time** (`ComposerState.pendingDraftUndo`); every write that replaces the
  draft (`clearComposer`, `applyComposer`, the landing reset) drops it in that same write. A file still
  uploading is cancelled, with no Undo.
- **A failed upload holds Send through `UploadIntakeDelegate.withUploadGate`**, checked before and
  after the upload wait on every send path. Not `SendBlockReason` (that opens the model sheet) and not
  `buildSendSpec` (a steer's re-home and an MCP App's message build there and must not be held).
- **Retry keeps the stored route only while the live selection still offers it**
  (`ChatUiState.retryUploadRoute`); otherwise it routes afresh.
- **iOS keeps each entry's picked data until it uploads** (`IosFileHandler.picked`); `prune`, run
  after every tray or Undo change, is the one rule for what stays. An iOS thumbnail is the picker's
  small JPEG/PNG preview, never the picked bytes (often HEIC, which Coil can't decode on iOS). A String
  `uri` is an id, never loaded as an image.
- **During a queued edit, a recovered draft part belongs to the stashed draft**
  (`ComposerState.draftRestore`) and joins it when the edit ends. A queued message doesn't open for
  editing while the draft's files are attaching or uploading.

## Chat Tools
- `enabledTools` and `selectedMcpServerNames` are global. Web search and URL context are
  `ModelParameters` fields: per chat, they last through model changes within it, and every new chat
  starts with both off. The Skills toggle is per conversation (`SkillsToggleDelegate`). The parameters
  sheet has no Web Search or URL Context row (`hiddenKeys`).
- **The settings line** (`ComposerSettingsLine`) is view data mapped once: `settingChipsOf`
  (`util/SettingChips.kt`, pure) in the `uiState` combine (`SettingChipsMemo`). Inputs are re-gathered
  only when a slice in `ChatUiState.settingChipSlices` is a new instance, so a new read in
  `settingChipInputs` adds its slice to that list. A tool shows only where its own gate is open
  (`ChatUiState.toolGateOpen`), in the sheet's `SETTING_TOOL_ORDER`.
  - **A tap on the line never turns a thing off**: a chip that is on opens the "+" sheet at its row, a
    pinned tool that is off turns on, a spec-locked one says who set it. Sign-in outranks a lock.
  - **MCP chips show the MCP servers page's status** (`mcpDisplayStatus`, with discovery's lapsed
    authorizations folded in; `:core:ui` `mcpStatusLabel`/`mcpStatusColor`), re-folded when the slower
    `GET /api/mcp/tools` lands, which is asked only while the chat shows the tool controls.
  - **Sign-in follows web's `useMCPServerManager`**: reconnect → `oauthUrl` → `LocalMcpSignIn`. **The
    app never cancels an OAuth flow by itself** (not on dialog close, browser return or run end): the
    server keeps one flow per user and server, and another run may be waiting on it. Only the chip's
    Cancel sign-in cancels. A reconnect waits for a cancel still in flight; while either is out,
    sign-in taps are ignored and those chips are disabled.
  - **The statuses reload when any sign-in ends, whatever the outcome**, after a cancel, and on every
    return to the chat (`McpServersDelegate.onForeground`): they change outside the chat.
- Thinking uses the brain glyph, Memory `AutoStories`, picked skills the sparkle (`AutoAwesome`),
  wherever they appear.
- URL context shows only where the server runs it (`supportsUrlContext`: Gemini 2.5+); deliberate
  divergence.
- **Read the tools in use, not the toggles**: `effectiveTools`, `effectiveMcpServerNames`,
  `sendParameters`, and `ChatUiState.webSearchRoute` for web search.

### Web search: one control, two searches
LibreChat's own `web_search` tool (`ephemeralAgent.web_search`) vs the provider's built-in search
(conversation parameter `web_search`). **Mobile shows one control on purpose: don't add a second,
and never send both** (the server runs both on OpenAI and custom endpoints).
- **Each send carries exactly one** (`util/WebSearchRoute.kt`). Order: off or a saved agent → nothing;
  a spec's `webSearch` → LibreChat (without the capability: its preset's built-in where it can
  run, else nothing; without the role, nothing; no control either way), its preset's `web_search` → built-in (on an endpoint with no
  built-in search, LibreChat when it can, else nothing; but under `enforce` the server sends the preset
  anyway, so built-in); a reopened chat whose stored conversation has `web_search` on keeps the
  built-in search until the user turns the control off (`keepsBuiltInSearch`: a send without it makes
  the server unset it); a comparison → LibreChat search when it can (lane B only gets the primary's
  tools); else `AppPreferences.WebSearchFirst`, falling back to the other, else hidden.
- **LibreChat search counts only when ready** (`librechatSearchReady`: the agents `capabilities`
  include `web_search` (a missing list counts, an empty one doesn't: the server keeps an admin's
  `[]`) and `GET /api/agents/tools/web_search/auth` → `authenticated`, asked whether or not config
  serves `webSearch`; cached per account in `WebSearchReadinessRepository`; a failure caches nothing
  and retries; an answer after an account switch is dropped). Strict because the server adds the
  tool without checking keys.
- **While that check is out, a send that would use LibreChat search runs none**
  (`WebSearchRoute.CHECKING`), so the search never depends on timing, but only where config serves
  `webSearch`. Only LibreChat-search sends are held, not built-in ones. Once the readiness retries
  are exhausted the hold lifts and the other search runs (the check keeps going once a minute).
- **Keys the server leaves to the user** (`authTypes` `user_provided`) are entered only in
  `WebSearchKeysSheet`, opened from the "+" sheet. Save and remove go through
  `WebSearchReadinessRepository`, so every chat's cached answer is invalidated.
- `interface.webSearch` rules out LibreChat search only; the built-in needs just `WEB_SEARCH.USE`.
- Built-in exists on openAI, azureOpenAI, anthropic, google and a custom endpoint whose parameter set
  (`paramSettingsKey`) is `anthropic`, `google` or `openrouter` or whose name contains `openrouter`
  (`builtInSearchProvider`), unless `dropParams` names `web_search`.
- **Per-chat memory** (`ConversationWebSearchStore`, account-scoped, 200 chats), written by one
  observer whenever it changes (`rememberWebSearchChanges`) and when a new chat is created. A reopen
  reads it before applying the model and writes it with the restored parameters in one update; the
  server's stored `web_search: true` wins over it. Not under a spec or in a temporary chat.
- **Presets** save `sendParameters`: `web_search` is true for `BUILT_IN`, false when the control is
  off, and omitted while LibreChat search is on (a preset has no field for it, on web too).
- **A model spec drives the tools** (`util/SpecTools.kt`, mirrored). Under a spec:
  - The chat starts from exactly the spec's tools; the user's toggles are neither shown nor sent.
    The spec's own tools and MCP servers are on and locked (`lockedTools`, `lockedMcpServers`). Its
    preset's `web_search`/`url_context` (`presetTools`) start on too; the user can turn those off,
    except under `enforce`, where the server sends the preset anyway.
  - User additions are `SpecTools`: session-only, keyed by spec name; any change of spec starts clean.
    They ride the queued-edit swap and the new-chat handoff; a reopened spec chat starts from the
    spec's tools. Divergence: web keeps them per conversation.
  - **Nothing in a spec chat writes the user's toggles** (sheet URL context, loaded presets, reopened
    `web_search` all go to additions or are kept).
  - `hideBadgeRow` hides the controls and drops the additions; the request still carries the spec's
    tools.
- Known gap: under `enforce` a preset `web_search` on a custom endpoint without the Responses API
  fails the send; the server forces it either way.

### Per-turn thinking (`reasoningOverride`, v0.8.9-rc1)
- `ReasoningSettingResolver` (`:core:ui`) ports upstream `resolveReasoningSettingForTarget`, which the
  server also runs: a difference fails the send (an `Invalid reasoning override` SSE error frame on a
  200). It keeps its own key table, not `EndpointParameterRegistry`'s. Mirrored.
- The chip shows only with: server ≥ 0.8.9-rc1, `interface.parameters` on, not assistants, not
  comparing, a resolved target (a saved agent's provider and model), a key an enforced spec's preset
  doesn't set, and levels (a `thinkingBudget` model gets none). `hideBadgeRow` doesn't hide it. Under
  `enforce` a kept spec the config doesn't serve (`showInMenu: false`) hides it: its locked keys can't
  be known. Web shows it there and the send fails.
- One message only: a staged choice rides that send or its `QueuedMessage`, and makes a mid-run send
  queue, never steer. A switch of endpoint, agent or model drops it, as web does; a changed setting
  drops one it refuses once known. Edit/regenerate/continue send the user message's own
  `reasoningOverride` when the model takes it and no enforced spec locks the key, chip shown or not.
- **OpenRouter catalog:** an endpoint whose efforts come from the catalog (`usesCatalog`) offers none
  until `ReasoningCatalogDelegate` has it; it refetches at the server's `expiresInMs` and retries
  failures. A model the catalog doesn't list keeps the generic list.

## Code workspace: attached machines (`CodeWorkspaceDelegate`)
The workspace choice is **sealed by the server with the first coding turn**: a new chat that sends
neither `codeEnvironmentMode` nor `codeWorkspaces` is sealed "No workspace" for good. Rules live in
`:core:model` `code/CodeWorkspaceResolver.kt` (mirrored); the composer only draws
`CodeWorkspaceUiState` (mapped in `util/CodeWorkspaceView.kt`), through `LocalCodeWorkspaceComposer`.
- **The workspace lives on the settings line, never as a card above the composer**: its chip first,
  then the approval chip, in every state. What needs the user is a notice under the field
  (`CodeWorkspaceNotice`). `CodeWorkspaceUiState.visible` (needs the user) is not web's `visible`;
  polling still follows the resolver's.
- **Gate on fields, never the version** (VERSION_GATES.md): `RUN_CODE.USE`, the `execute_code` +
  `stateful_code_sessions` capabilities, an `attached` machine, an agent routing code there.
  Capability versions compare with `==`, never `>=`. A plain agents chat sends neither field.
- Agents must come from the list cache: only that route projects `code_environment_id(s)` and friends.
- A send carries `submissionForSend()`: the decision on a new or undecided chat, nothing on a decided
  one (resending could only be `locked`). A queued spec snapshots it at queue time. Divergence: with
  workspaces to pick and none picked, the send is held; with nothing to pick it sends "No workspace"
  and says so (`CODE_WORKSPACE_SENT_WITHOUT`). A failed status read is not "nothing to pick": only a
  machine that answered (offline, not ready, no workspaces) or a 404 is.
- The saved decision is read live (no Room), only when a coding agent is in the chat, and again when
  a run ends. Read the live value before re-fetching (`combine` can hand a superseded `Pending`).
- Poll every 10 s only while visible and foregrounded; 30 s after a 429/503/504; never after a 404; read
  once whenever the machine set changes. Compare the poll target before launching: on an immediate
  dispatcher the launch runs inline and re-enters `retarget`.
- `code_workspace_unavailable` rejections carry their `reason` in the marker;
  `required`/`missing`/`worker_unavailable` also refresh status and tint the workspace chip.
- **Transitions:** a locked chat's workspace chip opens the transition sheet; picks only draft
  (`TransitionDraft`); Confirm writes via `PATCH …/decision` and the send waits while it is in flight.
  A `locked` refusal or unknown outcome re-reads the decision. With the transition version a healthy
  saved chat offers Detach from its folded chip.
- **After a failed turn that carried a decision**, re-read with Send held; a failed read keeps
  holding, never sending nothing (that could seal a chat the server never saved). A 404 there means
  never saved: undecided.
- With several machines, each pick on an agent's alternative carries its `agentIds`
  (`selectWorkspace`).
- **Approval mode** (`CodeApproval`): its chip is icon-only on "Ask before changes" while other modes
  are on offer. `submissionForSend()` adds `approvalMode`, so it rides every send path the decision
  does (`ask` without a workspace). Precedence (`requestedApprovalMode`): this chat's pick, the saved
  decision's, the spec preset's `codeApprovalMode` (undecided chat only), the device's last pick. A
  follow-up whose mode isn't `ask` stays in the local queue (`serverOwnedSpec`): the v1 queued-turn
  route refuses a mode.

## Content Cards
- File tools (`read_file`/`create_file`/`edit_file`) route by EXACT name in both `ToolCallDispatcher`
  and `StreamingToolCallCard`, ahead of the `contains` branches. Only tool output is parsed as a diff.
  Tool cards are outside `SearchMatchEnumeration` (they don't count toward find-in-page).
- **PTC traces** (`MessagesState.ptcTraces`) are never persisted by the server and are **cleared when
  the next turn starts**, so a reused `call_0` doesn't show an earlier program's rows.

## Artifacts
- `ArtifactDetector` is a line scanner ported from upstream `findArtifactClose` (the contract is
  "whatever micromark parses"), not a regex.
- **Three deliberate divergences from web; don't "fix" them:** `::: trailing` closes; a tab-indented
  content fence renders; attribute-less `:::artifact` produces nothing.
- **Incomplete artifacts** (no closing `:::`) render their source via `IncompleteArtifact`, bypass the
  inline prefs, and count for search. An unclosed directive with no opening fence is not an artifact.
- An assistant message with no content parts still gets the split
  (`Message.textFallbackSplitsArtifacts`); a user's text never does. Renderer, search and the
  Artifacts tab share that predicate.
- **While streaming, complete artifacts render as `ArtifactButton`** even with an inline pref on
  (`shouldRenderInlineArtifact`, `InlineArtifactPrefsExt.kt`): an inline preview would reload its
  WebView per delta.
- **The FileProvider authority must match the app's manifest** (`ArtifactDownloadHelper`).
- **The open artifact's viewer is composed once, in the chat's own window** (`ArtifactPresentationHost`):
  sheet, split and full screen are placements of that one composition. A change of shape must never
  move it into another window (a dialog sheet) or route, or its page reloads; only the explicit Full
  screen choice uses the `ArtifactFullscreen` route.

### Everything a WebView executes is bundled (no CDNs)
A hard requirement (F-Droid); see the root `CLAUDE.md`. Never edit a vendored file; repin via
`/update-web-assets`. CSPs name no remote origin (`img-src https:` is the one exception). **MCP Apps
don't change this:** the relay page loads no script, and no renderer may take a remote script because
Apps run third-party code.
- Pages reference assets relatively; only the base URL is per platform (`webAssetBaseUrl()`). Android
  serves `file:///android_asset/…`, readable with `allowFileAccess = false`: keep that off. iOS can't
  load local subresources for `loadHTMLString`, so assets are copied to caches (completion marker
  written last) and pages load via `loadFileURL` (`loadVendoredHtml`). Render nothing until the base
  URL resolves.

**React's module problem.** React 18 publishes no browser ESM (only CommonJS and UMD). The UMD builds
load as plain scripts; the runner re-exports `window.React`/`window.ReactDOM` from blob modules and
injects the import map *before* the first dynamic import. Hence a classic script wrapping an async
IIFE: a `type="module"` runner resolves its imports too early.

**npm imports use the same bridge.** Sandpack's `standardDependencies` are vendored as **classic**
scripts (`scripts/artifact-modules/build.mjs`) registering on `globalThis.__switchboardModules`, not
ES-module files (Chromium fetches modules in CORS mode, and `file://` isn't CORS). **A bundle must
never contain React** (hooks break).
- `ReactArtifactImports.plan` decides per specifier (React core, a bundle included only when
  imported, a throwing stub, or the browser); every string in its tables passes a charset guard.
- shadcn import paths are rewritten on Babel's AST, never as text. Babel runs the typescript preset
  with react (it drops type-only imports, which bundles don't export).
- **Flagged libraries ask first** (`ArtifactLibraryGate`; remembered in `ArtifactLibraryConsentStore`,
  keyed by library *and* advisory kind; an unremembered answer holds until restart). Settings lists
  `FlaggedArtifactLibraries`, which `vendor-web-assets.py` generates into `:core:data`.
- **Consent fails closed:** `buildHtml`'s `allowedLibraries` defaults to empty and every flagged
  (`advisory`) library not in it is stubbed. Keep each flagged library its own bundle. A package
  outside the vendored set fails by name ("not bundled").
- Known gap: HTML artifacts that load `cdnjs.cloudflare.com` render without it; closing that needs
  opt-in remote-code consent, not a CSP edit.

### Office previews: we rewrite the server's document (deliberate divergence from web)
`localizeOfficePreviewHtml` (`artifact/OfficePreviewLocalizer.kt`) points the server's exact CDN URLs
in `VENDORED_OFFICE_RENDERERS` at bundled copies, strips every other remote resource and replaces the
CSP.
- DOCX "High-fidelity renderer unavailable" = upstream changed its pinned URL; repin and update the
  map in the same PR (`mirrors.json` watches `DOCX_PREVIEW_CDN`).
- PPTX always uses the slide-list fallback: pptx-preview isn't FOSS; never load it from the CDN.
- Something blocked = our `OFFICE_PREVIEW_CSP`; compare with the server's before loosening.

## Media Players
`VideoContent` and `AudioContent` must release their players on dispose (`DisposableEffect`).

## Speech providers: package visibility
Dictation and read-aloud reach the user's engine only if `src/androidMain/AndroidManifest.xml`
declares the `<queries>` intents. Google's engines are force-queryable, so a missing entry breaks only
no-GMS ROMs and a Pixel can't catch it: test with `scripts/verify-speech-services.py`.

## Upload to Code Environment (legacy upload UX)
With `fileConfig.legacyFileUploadUX: true` (global or per endpoint, `resolveEndpointFileConfig`), the
"+" sheet's "Upload to code environment" row (`codeUploadOffered`) sends every pick, of any type and
images included, as `UploadRoute.CODE` (`tool_resource=execute_code`), with no routing sheet; a retry keeps it. It also
needs the `execute_code` capability, RUN_CODE.USE (the server refuses without it), and either
ephemeral tools or a saved agent whose `tools` include `execute_code`. With ephemeral tools a pick
also turns the code interpreter on. The routing sheet never offers CODE.

## Recovering from a failure the files cause (v0.8.9-rc1)
`AttachmentRecoveryActions` (`util/AttachmentRecovery.kt`, mirrored) offers Retry/Branch without files.
Retry keeps the turn's quotes, skills and reasoning choice.
- A copy that still has files is reported, not opened (older servers ignore `excludeFiles`). Branch
  without files and the fork sheet's "Leave out files" are also gated on `forkExcludeFilesSupported`.
- Hand the success snackbar off by conversation id (`ChatNoticeHandoff.put(…, conversationId)`).
- Withhold fork and feedback on `message.error` rows; show `FORK_FAILED`/`FORK_RATE_LIMITED`, never
  the server's text.

## Redacted user turns: the author's original (v0.8.9-rc1)
`OwnerTextDelegate` reads a PII-filtered turn's original (`POST /owner-text`, not while streaming).
- **The original lives only in `MessagesState.ownerTexts`**: never Room, the search index, drafts,
  exports or share links. Copy, Edit, Regenerate, Read aloud and Fork use `message.text`. A
  conversation switch clears it.
- Accept it only for the same `privacyRevision` and filtered text (`ownerTextStateFor`). No row for
  an id → no state; a failure → Unavailable with Retry.
- The live turn is marked from the `created` frame, in memory, without breaking the streaming anchor.
  `isServerCopyOf` matches a redacted server copy by author and parent (its text never matches).
- Search counts `message.text`, so the query isn't applied while the original is drawn.

## Feedback: rating + reason tag + comment
- The route requires `tag`, so both thumbs open `FeedbackTagSheet`; a bare rating is a 400. Tapping
  the filled thumb clears it (`onFeedback(null)` → `{}`).
- The sheet stays a `ModalBottomSheet` of radio rows: `FilterChip`/`InputChip` force
  `Role.Checkbox`, and `AlertDialog`'s text slot can't scroll.
- The wire type `MinimalFeedback` has no defaults (so `encodeDefaults = false` can't drop them); the
  read type `Feedback` defaults `rating` to `UNKNOWN` (a throw would fail all of `GET /messages`).
  `Feedback.tag` stays a raw `JsonElement`. Room writes go through `feedbackColumnValue`.
- `submitFeedback` is gated on `!isStreaming` **and** the thumbs are disabled (not hidden) via
  `LocalFeedbackEnabled`. Both gates are required. An open `FeedbackTagSheet` disables Submit (not
  dismiss) when a run starts, keeping the draft. (Sibling mutations such as `switchBranch` gate only
  the sink: their buttons stay live mid-stream and silently no-op.)

## Activity groups, steers, and content segmentation
`groupContentParts` (`util/ContentSegments.kt`) is ONE pure transform producing both steer
segmentation and activity grouping. Don't split it into two passes: they would disagree on a
boundary and put the user's words inside a tool block.
- A blank `activity_label` is a reservation: invisible, it only moves the claim boundary. No label →
  the legacy shape.
- The header turns red only when **every** call failed or the label says `failed`
  (`activityGroupOutcome`, each call judged by `toolCallOutcome` as its card judges it); mixed outcomes
  stay neutral. A backgrounded call's `background_task_status` error attachment is not counted.
- **Group identity is the first TOOL CALL, never `parts[0]`** (a label absorbs the leading THINK).
- **Auto-collapse latches once per group id and is suppressed on the message that just replaced the
  streaming bubble**, named by the VM (`MessagesState.justSettledMessageId`), written by
  `finalizeChatDisplay` in the same atomic update. Don't derive it in the UI. **Never clear it at the
  turn boundary** (an inline queue drain would clear it before Compose sees it); the next finalize
  overwrites it. `ActivityGroup` latches the suppressed decision too. A live comparison calls
  `markSettled` from `SendCompletionDelegate`.
- "Preparing {tool}" (`on_tool_preparation`) shows on the generic card only; MCP and action tools keep
  their name.
- **Steers** render as a user turn where they entered. A handoff AT the resume point keeps the
  pre-handoff author; a handed-off agent known only by id gets a neutral badge.
- **Per-part collapse state is keyed and saveable** (`stateKey`), never positional `remember`.
- `SearchMatchEnumeration` counts steer text and rendered activity labels; a collapsed group holding
  the focused match opens.

## Subagent files with no owner yet (v0.8.9-rc1)
A subagent's tool saves files under the CHILD call id, which only the transcript names.
`withSubagentFilesAdopted`/`withLiveSubagentFilesAdopted` **rewrite** an unclaimed file's
`toolCallId` to the message's `subagent` call (the only one, else the one whose agent stamped the
file, else the last); rewriting, not a side map, keeps
`unclaimedOfficePreviewRows` from drawing it twice. Metadata attachments (memory, search) never
move. `SubagentHintsDelegate` asks the server for the exact child call first; a failed lookup keeps
this placement and is retried on an offline → online transition (3 attempts). No version gate: only
an rc1+ server previews.

## Tool-call previews (v0.8.9-rc1, `?toolPreviews=1`)
Never let a preview pass for the whole call.
- **Fetched parts never touch message rows**: memory (`ToolPartHydrationDelegate`, cleared on a
  conversation or account switch; its budget never evicts a part an open card holds) plus the
  `tool_call_parts` table for finished messages (`core/data/CLAUDE.md`).
- A stored call applies only while it `isFullCopyOf` the current preview. Don't compare
  `previewRevision` (it is `updatedAt`, moved by unrelated edits).
- **No Room write while streaming:** parts wait in `pendingWrites`, which a conversation switch
  drops unless they can be written then; parts of `unfinished` messages are never written. A reload
  previewing a call held in full seeds it (`seedsFor`) instead of refetching.
- On offline → online, each failed part an open card pins is loaded again, at most `MAX_AUTO_RETRIES`
  per part; the user's Retry resets the count.
- **The overlay runs in `ChatViewModel.uiState`'s combine** (`ToolPartOverlay`): never a tree
  rebuild, `activeBranches` or the anchor. Delegates read the backing state. Every later field in that
  combine must build on the overlaid state.
- Cards request full content from an effect in `ToolContentDisclosure`, never from the click, so a
  card restored open or carried open from the live reply also asks.
- An open preview never sits on "Loading" with nothing in flight. `LocalToolContent` is null outside
  `ChatScreen` (shared links are never previewed).
- **Never copy a preview:** a `CodeBlock` in a previewed card disables Copy; the code-execution output
  pane (visible without a disclosure) fetches first; message Copy fetches every previewed part and
  copies nothing on a failure.

## MCP Apps (v0.8.9-rc1, `components/mcpapp/`)
- Rows never go inside a fold, and show only while `mcpApps.enabled` (`LocalMcpApps`); the shared
  viewer passes `McpApps.ReadOnly`. No first-open prompt; approval is per action.
- **Web views live in a pool, not the list** (`McpAppViewPool`). Android: `AndroidView.factory`
  returns a container and the pooled `WebView` moves into it; `onReset`/`onRelease` only detach, never
  destroy. `host.pageAttached()` runs once per **new** view, never on a reattach (it drops the queue
  and grants), and `host.send` stays wired while detached. A view is destroyed only when its session closes or the pool leaves composition. Build
  views with the Activity context, never the dialog's.
- **Android views are software-rendered, panel included** (a detach on scroll SIGSEGVs the GL path).
  `onRenderProcessGone` returns true and fails that App with Retry.
- The inline height is **frozen** after the first report (`McpAppsState.inlineHeights`, not
  `remember`), so the list doesn't jump. While its App is in the panel the card shows "Return to app"
  (one view, one parent). Nothing here writes Room, `activeBranches` or item keys.
  **In-thread glass must not sample the list's own backdrop** (stack overflow on iOS).
- Back minimizes (Apps keep running); Close tears that App down; leaving the chat closes all. Retry
  starts a new session (new host, new view). An approved message minimizes rather than closes.
  `ui/request-display-mode` fullscreen expands an App on screen, inline from the panel minimizes,
  anything else answers the current mode.
- Only a foreground App shows its question; one dialog at a time. Grants and the location question
  are per session: a second App's location request while one is open is refused, and closing an App
  refuses only its own. Never evict an App awaiting approval.
- **Host** (`McpAppHost`): every server call is native (`McpAppsRepository`), so **no token reaches
  the page**. Approval dialogs show the exact text, never truncated (over the limit: refused). Approve
  settles only its own action `id` and arms 500 ms after the dialog appears. Cancel pauses the App
  until reopened. Links open only on hosts the delivered CSP declared. Approved messages go via
  `ChatViewModel.sendAppMessage` (text only, composer untouched, refused while a reply runs).
- **"Don't ask again for <tool>"** (mobile-only) lives in `McpAppHost.autoRunTools` only, never
  persisted; tool calls only, never messages, over-limit requests or while paused; revoked by Cancel,
  Stop, Close, Retry, a reload, "Ask again" and a `BUSY` answer. Auto-run calls count against
  `maxPendingActions`.
- At most `min(maxActiveViews, 3)` Apps run at once, 1 on a low-RAM Android device
  (`McpAppSessionRules.viewCap`).
- **Relay bridge:** Android uses `addWebMessageListener` scoped to
  `https://mcp-app.switchboard.invalid`, main frame only. The pre-`WEB_MESSAGE_LISTENER` fallback
  `addJavascriptInterface` has **one** method, `postMessage(token, json)`, gated on a per-page token,
  since the object reaches every frame. **Never add a second method; never hand the token to the
  App.** The fallback adds nothing where the listener exists. Replies go by `evaluateJavascript`, top
  document only. The relay page (`McpAppRelayPage`) loads nothing. iOS uses a dedicated
  `WKContentWorld`, main frame only. Both refuse main-frame navigation and new windows.
- **Permissions:** geolocation and clipboard-write only. **Location** (Android-only, decided natively
  per MCP server, coarse only: `ACCESS_COARSE_LOCATION`): **every path must answer the WebView's
  geolocation callback**, including Close and the prompt's withdrawal, or the App hangs. The consent
  dialog's Allow arms 500 ms after each request appears (`requestId`).
- Not device-verified: panel reparenting, scroll detach/reattach, inline touch handling, rotation,
  the iOS pool.

## Legacy MCP-UI resources and `\ui{…}` markers (v0.8.9-rc1)
- **Markers never show** (`splitUiResourceMarkers`; code untouched); while streaming,
  `withoutTrailingPartialUiMarker` hides a half-written one. Search counts via
  `countUiMarkedOccurrences`: keep both sides on that one function.
- Resources render only while `legacyHtmlEnabled`, in an `allow-scripts`-only srcdoc frame (software
  layer on Android) behind the same `RelayBridge` / content-world bridge as Apps. They come from
  `conversationUiResources`, which runs off the main thread. App actions wait on "Send message from
  app?" (hosted by `rememberLegacyUiResources`, so it survives its row scrolling away) and are refused
  while a reply runs; `link`/`notify` are ignored.
- A single unknown id shows web's "UI Resource not found" chip once the reply settles, after a 300ms
  grace, whatever `legacyHtmlEnabled` says (web checks it first); unknown ids in a carousel and
  non-legacy resources draw nothing.

## Conversation pull requests (v0.8.9-rc1)
- Looked up (`PullRequestDelegate`) only for a saved chat not on its first streaming turn whose cached
  `Conversation.isCodeConversation == true`, when `pullRequestsAvailable`. Nothing else is looked up.
  A row cached before that was known is read once in full on open.
- In memory only (`:core:data` `PullRequestRepository`, per account, shared with the drawer); never
  Room. Poll only while in front; on return an answer over 15 s old is asked at once. A change of
  credential (`PullRequestRepository.revision`) restarts polling.
- Placement follows the chat **pane** width (600dp, `comparisonWide`), not the window, and never
  mixes the two layouts: a wide pane gets the bar chip and no ⋮ item; a narrow pane gets the ⋮ item
  (`ChatOverflowItem.PULL_REQUEST`) plus `PullRequestComposerRow` (via `LocalPullRequestComposer`). The
  ⋮ item is absent until a lookup finds a PR, never disabled. Link only `openableUrl()` (absolute
  http(s) passing `isSafeLinkUrl`). The composer chip spells out the state while unseen (sheet opened,
  or 8 s on screen with the keyboard down), then folds to the glyph; folded during a run, hidden during
  a tool approval, a queued edit, and behind the keyboard on panes under 480dp tall.
- Sheet visibility is delegate state (`PullRequestUiState.sheetOpen`, `openPullRequestSheet`), so
  every surface opens the one sheet; a chat switch closes it.

## Tool intent labels
A card's title is the model's `intent` only when it is the **first key** of the args object (a
non-first `intent` may be a real MCP parameter). A lifted label is stripped from the args dump.

## Context gauge
`ContextProjectionDelegate` resolves from the displayed branch (`util/ContextGaugeResolver.kt`,
mirrored).
- Re-run on the branch itself, not its tail id (a refresh can keep the tail and change only `metadata`).
- `ContextUsageSource.LIVE` alone isn't proof: carry over only a reading that replaced the value
  recorded at stream start.
- The breakdown is always computed in full and filtered at render (`visibleBreakdown`, `:core:model`).
  Its rendering is `:core:ui` `ContextBreakdownCard`, so the Settings preview draws exactly what chat
  does; keep chat rendering through it. Every context percentage goes through `percentLabel`
  (localized: `42 %` in German and French).
- "Current turn (so far)" shows the live usage only once an event arrived, never the previous reply's.
- **Live usage is attributed to the Final frame's response id, never the tail** (at `onStreamEnded`
  the tail is still the user message; anchoring there double-counts).
- The compact suggestion always opens `CompactConfirmDialog`. An estimate never suggests compacting;
  the nudge checks `isCompacting` itself (`canCompactNow` doesn't), which `MessageEditingDelegate`
  sets before `isStreaming` flips. "Not now" snoozes a band per
  conversation, persisted, until a real reading is back under the threshold; a chat still loading must
  not clear it.

## Message Timestamps
`MessageTimestamp` is the only reader of the minute ticker (`LocalRelativeTimeReference`) in the chat
tree and skips it under Absolute; keep it that way, or bubbles recompose every minute. A tap toggles
the full date and time (off under Absolute); an unparseable timestamp renders nothing.
