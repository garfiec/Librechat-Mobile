# feature:agents

## Screens
- **AgentMarketplaceScreen** -- the agent index: search, a chip row, one column of cards on phones (two at >= 600dp)
- **AgentDetailScreen** -- the agent page: header, Start chat, description, model/tools, starters; Pin and ⋮ in the bar

## Navigation
- Sealed interface: `AgentsRoute : NavKey` with typed route classes
- Routes: `AgentMarketplace` (index), `AgentDetail(agentId: String)` (detail), `AgentEditorCreate`, `AgentEditorEdit(agentId: String)` (all `@Serializable`)
- Feature entries registered via `EntryProviderScope<NavKey>.agentsEntries()`
- `onStartChat(agentId)` callback navigates to chat with the selected agent
- `onStartChatWithPrompt(agentId, prompt)`: a conversation starter was tapped. The nav host owns what it does
  (feature/agents never depends on feature/chat): it stages the prompt in feature/chat's
  `PromptInsertionHandoff` and opens `NewChat(agentId)`, so the starter sits unsent in that chat's composer.
- Duplicate from the page `onReplace`s it with the copy's page, as Duplicate in the editor does

## Who can reach the index
- AGENTS.USE **and** MARKETPLACE.USE (web parity). Without either, the drawer and tablet sidebar hide the
  Agents entry (`DrawerUiState.agentsEntryVisible`, feature/conversations), the VM reads nothing, and a screen
  reached anyway (stale back stack) shows the "not available" state. Users without MARKETPLACE.USE therefore
  have no way into the agent editor in the app: a decision, not a bug.

## Marketplace ViewModel
- Server-side cursor pagination via `AgentRepository.getAgentsPaginated()`, 20 agents a page (`limit`). Each
  page's `after` (`PaginatedAgents.nextCursor`) is the next request's `cursor`; the route ignores page numbers.
- Categories come from `getAgentCategories()` (server-driven, not client-derived).
- **Chips** (`AgentChip`): Top picks (only when categories include `promoted`), All, Editable (only with
  AGENTS.CREATE), then the real categories. Exactly one is selected; tapping the selected chip does nothing.
  A chip the row stops offering (Editable after losing CREATE, a removed category) falls back to All.
- **Default chip**: Top picks when offered, else All. The first read waits for the role AND the categories, so
  the default is known before anything is fetched (no flash of All, no second read). Placeholders cover the wait.
- **Gotcha**: the categories route appends `promoted` and `all`, which are not categories. `AgentListFilter.of`
  maps them like the web grid: `promoted` → `promoted=1`, `all` → no filter, and while searching both send
  neither. Never send them as `category` (registered in `scripts/mirrors.json`). Editable is
  `requiredPermission` = `PermissionBits.EDIT`; it ignores categories and still narrows a search.
- **One load pipeline**: every read that replaces the list (chip, search, Retry, pull-to-refresh, return) goes
  through `startLoad`, which cancels the previous read and any next-page read and bumps a generation; a late
  answer from an older generation is dropped. Search is trimmed and debounced 300ms; a search that trims to
  the request already made doesn't read again.
- `loadMore()` appends the page after `nextCursor`; no-ops while any read runs, with no cursor, or after a
  failed page (`loadMoreFailed`) until `retryLoadMore()` (the footer's Retry). Without that stop, the scroll
  trigger re-arms and re-reads a failing page forever.
- **Return to the screen** (`refreshOnReturn`, ON_RESUME): a quiet reload that walks the cursor until it has as
  many agents as were loaded, with no pull-to-refresh spinner; a failure keeps what is shown, a success clears
  a standing error. It does nothing before the first read has started (ON_RESUME fires on first composition,
  while the role is still awaited).
- Errors are `AgentListError(message)` (the server's message, or null); the screen words them. A first-page
  failure with nothing listed is the error page with Retry; with a list for the same query it is a snackbar.
  A failed read for a NEW chip/search empties the list rather than leave the old chip's agents standing.

## Agent cards
- `AdaptiveCard(onClick)` + `InitialTile(seed = id)`; name (1 line), plain-text description (2 lines) and a meta
  row: Top pick mark (`is_promoted`), category label (not "general"), contact.
- `AgentCardDisplayData.name` is null for an unnamed agent; the card shows `agent_unnamed`. No English in the VM.
- Descriptions are reduced to text with `htmlToPlainText()` (`:core:common`): admins write some as HTML.
- Contact = support contact's name (or email), else the owner's name (`Agent.resolvedContact()` in
  `AgentDisplayMappers.kt`, shared with the detail page). Never the raw `author` id.
- No per-card menu: agent actions live on the agent page.

## Index states
- First load (and a chip change with nothing listed): placeholder cards, never the empty state.
- A chip/search change with a list showing: the old list stays, dimmed, until the answer lands.
- Empty server (All, no search): "No agents yet" + New agent (with CREATE; the FAB hides) or "Check back later".
- Nothing under a chip or search: names what it looked in, with a way out (Search all agents keeps the search,
  Show all agents / Clear search go back to everything).

## Agent Detail
- `AgentDetailScreen` (VM + nav glue) over a stateless `AgentDetailContent(uiState, actions)`; previews in
  `AgentDetailPreviews.kt`.
- **Load**: the edit-gated `/expanded` read first, then the view-only read (see the comments in
  `fetchAgent`). `isFullRecord` is true only when `/expanded` answered; Model and Tools render only then,
  because the view-only read doesn't carry the agent's real ones. A skipped probe (the list said "not
  editable") is not a full record either.
- **Header**: `InitialTile` (64dp), name (heading; `agent_unnamed` when null), contact line (support contact,
  else owner; a `Role.Button` that opens `mailto:` when there is an email), Top pick mark, category label via
  `agentCategoryLabel()` (shared with the cards; "general" has none). No "by <author>" byline.
- **Body**: Start chat, the description (`htmlDescriptionToAnnotatedString`: b/strong, i/em, http(s)/mailto
  links, br, p, lists; other tags dropped, text without tags untouched), one facts line ("Updated <relative
  time>" through the date-format setting, "Public" when `isPublic`), Model, Tools (plain labels, not chips),
  "Try asking" starter cards. Capped at 600dp wide, centred.
- Categories are read once, only for an agent with a non-general category, so an admin's own label shows.
- **Bar**: a star toggle (pin to the chat model menu), then ⋮ = Copy link, Edit (canEdit), Duplicate
  (`canDuplicate`), Delete (`canDelete`). Both are `canEdit && canCreate` (AGENTS.CREATE): the duplicate route
  needs EDIT on the source, the delete route needs CREATE plus a DELETE bit nothing reports (EDIT stands in;
  a 403 is an action error). The VM no-ops either when its gate is false. While one runs, ⋮ is a spinner.
- **Pin**: writes the WHOLE favorites list through `FavoritesRepository`, like `FavoritesDelegate.toggleAgent`
  in feature/chat. **Gotcha**: never toggle on a list that wasn't read: the VM refreshes on open and, if that
  failed, refreshes again before the first write; a failed read is an error, not a write of `[thisAgent]`.
  `isPinned` follows the repository's flow, so a pin made in the chat picker shows here. Over
  `FavoritesLimits.MAX_FAVORITES` is a `PinLimit` error, nothing sent.
- **Copy link**: `agentChatLink()` = `<server URL>/c/new?agent_id=<id>`, the web's own agent URL (mirror
  `agent-chat-link`). The clipboard is this module's own `ClipboardWriter` (same seam as chat/settings).
  A snackbar confirms.
- **Errors**: a page that never loaded is `loadError` (ErrorBanner + Retry). A failed Delete, Duplicate or pin
  is `actionError`, shown once as a snackbar; the page stays. No English in the VM: the screen words both.

## Data Layer
- `AgentRepository` in `:core:data` wraps `AgentsApi` from `:core:network`
- API: `GET /api/agents` (list), `GET /api/agents/:id` (detail), `GET /api/agents/categories`
- Categories are server-driven, not hardcoded; special values: "promoted" (Top picks), "all"

## Infinite Scroll
- `LazyVerticalGrid` (search and chips are full-width items, so they scroll with the list and pull-to-refresh
  starts anywhere) + `derivedStateOf` for scroll detection
- Triggers `loadMore()` when the last visible item is within 3 of the end, and again when a page lands with the
  end still in view
- Footer: a spinner while a page loads, "Couldn't load more agents" + Retry after a failed one

## Spec Notes (not yet implemented)
- Category tabs with slide animation (`AnimatedContent` with `slideInHorizontally`)

### Agent Editor — Advanced Sections
- `AgentEditorScreen` now includes 6 collapsible sections below the basic fields:
  - `AgentActionsPanel` — OpenAPI action CRUD (domain, type, auth)
  - `AgentCodeInterpreterSection` / `AgentFileSearchSection` — simple capability toggles
  - `AgentSharingSection` — visibility (Private/Team/Public) + collaborative toggle
  - `AgentHandoffConfig` — select agents for handoff, displayed as InputChips
- `AgentEditorViewModel` depends on both `AgentRepository` and `McpRepository`
- **Gotcha**: MCP tools load requires a separate `McpRepository.getTools()` call; they're not bundled with agent data
- **Gotcha**: `isPublic`/`isCollaborative` map to the sharing section, not individual toggles in the agent model

### Editor exits and unsaved changes
- The edit editor sits on top of the agent's detail page: Save pops back to it, Duplicate replaces the
  editor with the copy's page, Delete pops the editor and the deleted agent's page. Create replaces
  itself with the new agent's page. None of them push, or back walks through a finished form.
- The detail page refreshes on `AgentRepository.revision`, since a save returns to the same page.
- "Unsaved changes" = `hasUnsavedChanges()`: Save's request built from the form vs. from the form
  reset to `loadedAgent`. Comparing requests is what keeps late reference data (MCP catalog, config
  gates) and instantly-saved files/avatar from counting as edits. `applyAgentData` sets
  `loadedAgent`, so load and revert both move the baseline.

### Unified tools picker (v0.8.8)
- `ToolsMarketplaceDialog` is the ONE picker for what an agent can do — built-in capabilities,
  plugin tools, MCP servers and skills in one searchable list with kind filter chips. It replaced
  `ToolSelectDialog` and `AgentMcpToolsSelector` (both deleted), which each browsed a slice of the
  same catalog.
- Everywhere else in the editor shows only what is ALREADY on the agent, never the catalog:
  plugin tools, MCP tools and actions are rows in "Tools & Actions"; skills are chips in
  `AgentSkillsSection`. Adding is the picker's job. An MCP tool whose server has since gone away
  still gets a row, by raw name, so it can be removed instead of sitting invisibly on the agent.
- `AgentSkillsSection` keeps its own "Add skills" dialog rather than deferring to the picker —
  upstream does the same (`ToolsSection` renders a skills `SelectedSection` next to a dedicated
  `SkillsDialog`), because the skills allowlist has a master switch the catalog has no notion of:
  enabled with an empty allowlist means "all skills", not "none".
- `AgentCapabilitiesSection` (Artifacts, EndAfterTools, HideSeq, RecursionLimit) is NOT part of the
  catalog — those are run-behaviour knobs, not things an agent can be given.
- The catalog is DERIVED, not stored: `AgentEditorUiState.marketplaceCatalog()` builds it from the
  reference lists the editor already loads, so it cannot go stale against them.
- **Gotcha**: a row's toggle routes back through the existing per-kind entry point
  (`onCodeInterpreterToggled`, `onToolToggled`, …), never straight into state. Code interpreter
  hangs an auth check off its toggle and the picker must not be a second path that skips it.
- **Gotcha**: `MarketplaceItem.id` is what the AGENT stores; `favoriteId` is what the FAVORITES
  route pins. They differ for MCP — the agent stores a tool name, the pin is the server.
- Favorites come from `ToolFavoritesRepository` (v0.8.8 `/favorites/tools`). The star column is
  hidden entirely when `areToolFavoritesSupported` is false; see VERSION_GATES.md for the gate.
- Built-in rows carry a `MarketplaceBuiltinLabel` rather than a name string — they are the only
  rows this app names itself, so their text is resolved from compose resources at render time.
  `filterMarketplace` therefore takes a `builtinLabels` map so search can see those labels.
- Version history is fetched lazily (`AgentLoaderDelegate.loadVersions`) because v0.8.8 stopped
  inlining `versions[]`. The active-revision comparison runs against `versionBasis`, captured at
  load — reading live form state would compare against whatever the user has since typed.
- Not ported from upstream's rework: OrchestrationHub and StatefulSessions.
