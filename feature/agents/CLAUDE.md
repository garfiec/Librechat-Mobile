# feature:agents

## Screens and navigation
- `AgentMarketplaceScreen` (index: search, chip row, cards; 2 columns at >= 600dp) and `AgentDetailScreen`
  (agent page). Routes: `AgentsRoute : NavKey` — `AgentMarketplace`, `AgentDetail(agentId)`,
  `AgentEditorCreate`, `AgentEditorEdit(agentId)`; entries via `agentsEntries()`.
- `onStartChatWithPrompt(agentId, prompt)` (a starter tapped): the nav host stages the prompt in feature/chat's
  `PromptInsertionHandoff` and opens `NewChat(agentId)`, unsent. feature/agents never depends on feature/chat.
- Results that land after the user left (entry still composed for its exit animation) must act on that entry
  only: `onReplace(key, …)` (swaps only while on top) for Duplicate, `onRemove(key)` for Delete, never a bare pop.

## Who can reach the index
- Needs AGENTS.USE **and** MARKETPLACE.USE (web parity). Without either the drawer/sidebar hide Agents
  (`DrawerUiState.agentsEntryVisible`, feature/conversations), the VM reads nothing, and a screen reached via a
  stale back stack shows "not available". So no MARKETPLACE.USE = no way into the editor: a decision, not a bug.

## Marketplace ViewModel
- Cursor pagination (`AgentRepository.getAgentsPaginated()`, 20/page): each page's `after` (`nextCursor`) is the
  next `cursor`; the route ignores page numbers. Categories are server-driven (`getAgentCategories()`).
- **Chips** (`AgentChip`): Top picks (only if categories include `promoted`), All, Editable (only with
  AGENTS.CREATE), then categories. Exactly one selected; tapping it again does nothing; a chip no longer
  offered falls back to All. Default: Top picks if offered, else All. The first read waits for role AND
  categories (no flash of All, no second read).
- **Gotcha**: the categories route appends `promoted` and `all`, which aren't categories. `AgentListFilter.of`
  maps `promoted` → `promoted=1`, `all` → no filter, and while searching sends neither. Never send them as
  `category` (mirror in `scripts/mirrors.json`). Editable = `requiredPermission` `PermissionBits.EDIT`; it
  ignores categories and still narrows a search.
- **One load pipeline**: every list-replacing read (chip, search, Retry, pull, return, sort, Mine) goes through
  `startLoad`, which cancels prior reads (incl. next-page) and bumps a generation; older answers are dropped.
  Search is trimmed, debounced 300ms, and not re-read if it trims to the last request.
- **Sort / "My agents"** (rc1, `sortAvailable`): `AgentSortRow` — `mine=1` toggle and `AgentSort` sent as
  `sort=` on every rc1 read (newest default). Both are in `AgentListQuery`. Mine under Top picks selects All.
  Not remembered across visits. A next page answered 409 `cursor_ordering_mismatch` restarts the list once
  per query (`mismatchRecoveredFor`), then shows the footer error. Mine with nothing = `MineEmpty`.
- `loadMore()` no-ops while any read runs, with no cursor, or after a failed page (`loadMoreFailed`) until
  `retryLoadMore()`; without that stop the scroll trigger re-reads a failing page forever.
- **Gotcha**: an unparseable cursor returns the first page with the same `after`. A page whose `after` equals
  the cursor sent ends paging, and the quiet reload stops at a cursor it already sent; else infinite paging.
- **Return** (`refreshOnReturn`, ON_RESUME): quiet reload walking the cursor up to the loaded count, no spinner;
  failure keeps the list, success clears a standing error. No-op before the first read started (ON_RESUME
  fires on first composition, while the role is awaited).
- Errors are `AgentListError(serverMessage)`; the screen words them. First-page failure with nothing listed =
  error page + Retry; with a list for the same query = snackbar. A failed read for a NEW chip/search empties
  the list.

## Index UI
- `LazyVerticalGrid`; search and chips are full-width items (scroll with the list; pull starts anywhere).
  `loadMore()` fires when the last visible item is within 3 of the end, and again if a page lands with the end
  in view. Footer: spinner, or "Couldn't load more agents" + Retry.
- Cards: `AdaptiveCard(onClick)` + `InitialTile(seed = id)`, name (1 line), description (2), meta row (Top pick,
  category unless "general", contact). Every card is the same height (description always 2 lines, meta row
  always 1, ellipsized) so grid rows line up; `AgentCardPlaceholder` mirrors the slots — change both together.
- `AgentCardDisplayData.name` is null when unnamed; the card shows `agent_unnamed`. No English in the VM.
- Descriptions go through `agentDescriptionPreview()` (HTML → `htmlToPlainText()`, plain text whitespace-collapsed).
- Contact = `Agent.resolvedContact()` (`AgentDisplayMappers.kt`, shared with detail): support contact name/email,
  else owner name. Never the raw `author` id. No per-card menu; actions live on the agent page.
- States: first load (and chip change with nothing listed) = placeholders, never the empty state. Pull shows
  placeholders only over the error page (the error stays until the pull lands); over empty/no-matches that answer stays under the spinner. A
  chip/search change keeps the old list dimmed until the answer. Empty server (All, no search): "No agents
  yet" + New agent (with CREATE; FAB hides) or "Check back later". No matches: name what was searched, with a
  way out (Search all agents keeps the search; Show all / Clear search reset).

## Agent Detail
- `AgentDetailScreen` (VM + nav glue) over stateless `AgentDetailContent(uiState, actions)`.
- **Load**: edit-gated `/expanded` first, then VIEW `getAgentForViewing` (see `fetchAgent`). **Never `getAgent`**
  here: its warm cache serves the list projection (no model, `isPublic`, contact detail). VIEW lacks
  `category`/`is_promoted` (decode defaults "general"/false); they come from the tapped list row, else are
  omitted. Model shows for every viewer. `isFullRecord` is true only when `/expanded` answered (a skipped probe
  is not), and Tools render only then (list rows carry cut-down `tools`, VIEW none).
- Header: no "by <author>" byline; contact line opens `mailto:` when there is an email; category via
  `agentCategoryLabel()` (shared with cards). Categories are read once, only for a non-general category.
  "Updated <time>" goes through the date-format setting.
- **Body**: Start chat, the description, one facts line ("Updated …", "Public" when `isPublic`), Model,
  Tools (plain labels, not chips), "Try asking" starter cards. Capped at 600dp wide, centred.
- **HTML descriptions** (mirrors `agent-description-html`, `agent-description-html-detection`): HTML only when,
  trimmed, it STARTS with a tag on `AGENT_DESCRIPTION_TAGS` or `img` (upstream `isHtmlDescription`,
  `client/src/components/ui/Description.tsx` at v0.8.8; other revisions use `startsWith('<')`). Otherwise plain
  text as written. Other tags (incl. `img`) are dropped, text kept. Links: http(s), mailto, tel, or relative
  (resolved against `AgentDetailDisplayData.linkBaseUrl`); anything else is text.
- **Bar**: star (pin), ⋮ = Copy link, Edit (`canOpenEditor`), Duplicate (`canDuplicate`), Delete (`canDelete`).
  All three are `canEdit && canCreate`: PATCH and duplicate need CREATE + EDIT; delete needs CREATE + a DELETE
  bit nothing reports (EDIT stands in; a 403 is an action error). The VM no-ops Duplicate/Delete when gated off.
- **Pin**: writes the WHOLE favorites list via `FavoritesRepository` (like feature/chat's
  `FavoritesDelegate.toggleAgent`). **Never toggle on a list that wasn't read**: refresh on open, again before
  the first write if that failed; a failed read is an error, not a write of `[thisAgent]`. `isPinned` follows
  the repository flow (pins from the chat picker show). Over
  `FavoritesLimits.MAX_FAVORITES` = `PinLimit` error, nothing sent.
- **Copy link**: `agentChatLink()` = `<server>/c/new?agent_id=<id>` (mirror `agent-chat-link`), via this
  module's `ClipboardWriter`.
- Errors: never-loaded page = `loadError` (ErrorBanner + Retry); failed Delete/Duplicate/pin = `actionError`
  snackbar. No English in the VM.

## Agent Editor
- `AgentEditorViewModel` uses `AgentRepository` and `McpRepository`. **Gotcha**: MCP tools need a separate
  `McpRepository.getTools()`; not bundled with agent data. `isPublic`/`isCollaborative` belong to
  `AgentSharingSection`.
- **Exits**: edit sits on the detail page — Save pops to it, Duplicate replaces the editor with the copy's
  page, Delete pops editor and page; Create replaces itself with the new page. None push. The detail page
  refreshes on `AgentRepository.revision`.
- **Unsaved changes** = `hasUnsavedChanges()`: Save's request from the form vs. from the form reset to
  `loadedAgent`. Compare requests, so late reference data (MCP catalog, gates) and instantly-saved
  files/avatar don't count as edits. `applyAgentData` sets `loadedAgent` (load and revert move the baseline).
- Version history loads lazily (`AgentLoaderDelegate.loadVersions`; v0.8.8 stopped inlining `versions[]`).
  Compare the active revision against `versionBasis` captured at load, never live form state.

### Tools picker
- `ToolsMarketplaceDialog` is the ONE picker (capabilities, plugin tools, MCP servers, skills). Elsewhere the
  editor shows only what is ALREADY on the agent (rows in "Tools & Actions", skill chips in
  `AgentSkillsSection`); adding is the picker's job. An MCP tool whose server is gone still gets a row by raw
  name so it can be removed.
- `AgentSkillsSection` keeps its own "Add skills" dialog (as upstream): enabled with an empty allowlist means
  "all skills", which the catalog can't express.
- `AgentCapabilitiesSection` (Artifacts, EndAfterTools, HideSeq, RecursionLimit) is not in the catalog.
- The catalog is derived (`AgentEditorUiState.marketplaceCatalog()`), never stored.
- **Builtins need the role grant** (web `BUILTIN_ROLE_PERMISSIONS`): code interpreter RUN_CODE.USE, web search
  WEB_SEARCH.USE, file search FILE_SEARCH.USE (+ `file_search` capability). `is…Offered` (capability AND grant,
  fail-open on unknown role) hides the row and section. **Never the save**: `buildToolsList` reads only
  `is…Available` and the flag, so a hidden builtin the agent has is saved back unchanged.
- **Gotcha**: a row's toggle routes through the per-kind entry point (`onCodeInterpreterToggled`,
  `onToolToggled`, …), never straight into state: code interpreter's auth check hangs off its toggle, and the picker must not be
  a second path that skips it.
- **Gotcha**: `MarketplaceItem.id` is what the agent stores; `favoriteId` is what `/favorites/tools` pins. They
  differ for MCP (tool name vs. server).
- Favorites: `ToolFavoritesRepository`; star column hidden when `areToolFavoritesSupported` is false
  (VERSION_GATES.md).
- Built-in rows carry `MarketplaceBuiltinLabel` (resolved from resources at render), so `filterMarketplace`
  takes a `builtinLabels` map for search.
- Not ported: rc1's "Orchestration" tool row; mobile edits subagents/handoffs in `AgentSubagentsSection` /
  `AgentHandoffsSection`.

### Tool approval modes (rc1)
- MCP tool rows get `ToolApprovalModeButton` when `isToolApprovalModesAvailable` (rc1 AND `/api/endpoints`
  `agents.toolApproval.agentModes`). Rows are mapped in the VM (`mcpToolRows`, `selectedMcpToolRows()`), not in
  composition. Whole-server rows and tools of a gone server get no picker (upstream's per-server bulk mode is
  deliberately not ported).
- **`tool_options` keys are the full MCP id** `<tool>_mcp_<normalizedServer>` (same as the `agent.tools` entry);
  `selectedMcpTools` holds bare names, so never match options against it alone. `pruneToolOptions` keeps full
  ids, ids under a whole-server marker and legacy bare keys. An update that drops the last option sends `{}`
  (omitting the map keeps the stored one).
- Mode changes go through `withApprovalMode` (port of `withApprovalModes`): fresh `approval_revision` each time;
  inherit removes both fields and keeps the rest. Form-only, saved with the agent. The admin constraint
  (`toolApprovalConstraint`, hand-written glob, not Regex) leaves only inherit and Always ask. Both in
  `scripts/mirrors.json`.
- Reset: per tool in the mode menu only when the SAVED mode is chat/always (server checks the saved agent); per
  agent in the agent page ⋮ for every viewer (VIEW suffices). Don't re-add the MCP server card menu.

### Code section
- `AgentCodeSettingsSection` (web `SidePanel/Agents/Code/Settings.tsx`). Draft `AgentEditorUiState.code` loads
  from `/expanded` only (VIEW has none of it); `agentCodeSection` maps to `AgentCodeSectionView`;
  `AgentCodeSettingsDelegate` handles `AgentCodeEvent`s.
- **Send only what changed** (`updateFields`), so web-set fields survive. Default machine and cleared Git
  identity are explicit `JsonNull`s (`explicitNulls = false` would drop them). Code off saves stateful sessions
  off. Enabling stateful sessions applies the Settings default scope, else the first allowed. Picking a default
  workspace also writes the effective machine. A half-filled/malformed Git identity blocks Save
  (`codeGitIdentityError`).
- **Background execution** (`AgentCodeBackgroundRow`): shown while code interpreter is on and
  `agents.capabilities` has `run_in_background` (fail-closed). `withCodeBackground` (port of
  `withNativeBooleanOptOut`) edits `tool_options` for BOTH `execute_code` and `bash_tool`: off writes
  `run_in_background: false`, on removes the key (and an entry left empty); other keys stay. Read `bash_tool`'s key, then `execute_code`'s. `pruneToolOptions` keeps
  `bash_tool` while `execute_code` is saved (it is never an `agent.tools` entry) and drops both with the code
  interpreter (web keeps them).
- **Only handoff edges reach `AgentHandoffsSection`** (`isHandoffEdge`, upstream
  `Tools/items/orchestration.ts`): `edgeType: "direct"` and untyped unconditional one-to-many edges stay raw in
  `unparsedHandoffEdges`, re-sent untouched. `HandoffEdge` defaults missing `edgeType` to `handoff`, so classify
  on raw JSON, never after decoding. Not version-gated.
