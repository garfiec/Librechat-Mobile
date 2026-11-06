# feature:conversations

## Screens
The conversation list lives in the drawer (`drawer/DrawerContent.kt`, `DrawerViewModel`); there is no
full-screen list route. `ConversationsRoute : NavKey` covers what the drawer opens: `ArchivedConversations`,
`Projects`, `Bookmarks`, `BookmarkChats`, `Search`, `ProjectChats`, `ProjectInstructions`.

## List
- Cursor pagination (`ConversationRepository.loadNextPage(cursor, tags)`); `nextCursor == null` = no more.
- Grouping keys are `DateGroup` (core/common), localized at render via `DateGroup.resolve()`; the drawer wraps
  them as `DrawerGroupKey` to add Running. Row timestamps read `LocalRelativeTimeReference` (core/ui); no list
  provides its own ticker.
- Search: 500ms debounce, cancels the previous job; while active, pagination is off (`hasMore = false`).
- Tags: `TagRepository.observeTags()`; the VM filters out `SAVED_TAG` and `count == 0`.
- `ConversationListEvent.NavigateToNewChat` fires when the open conversation is deleted, so the host leaves it.

## Drawer bookmark filter (rc1)
- `DrawerFilters.tags` are sent as `tags` on `GET /api/convos` (server OR-matches). The sheet offers
  `selectableBookmarks`: bookmarks some chat carries plus every picked one (so a deleted one can be turned off).
- "Any bookmark" (`bookmarked`, Switchboard-only) sends every bookmark. It and picked tags exclude each other
  (`withTagToggled` / `withAnyBookmark`); a request never mixes the two.
- Persisted with the other filters in saved-state JSON (until cold start).

## Actions
- Rename (`POST /api/convos/update`, max 100 chars), Archive (`POST /api/convos/archive`), Delete
  (`DELETE /api/convos`, confirmation required), Duplicate (`POST /api/convos/duplicate`, opens the copy). Fork
  is in chat's message menu, not here. Share opens `ShareLinkSheetHost` only with `sharedLinksEnabled` AND
  SHARED_LINKS CREATE (shares the newest branch).
- Export (`ConversationExporter`, JSON/Markdown) is client-side, file I/O via SAF. The JSON must keep web's top-level shape
  (`conversationId`, `endpoint`, `title`, `options`, flat `messages`) so `POST /api/convos/import` accepts it;
  `conversation`/`exportedAt`/`version` are app-only extras. `ConversationImporter` +
  `ConversationRepository.importConversation` exist but no screen calls them yet.

## Project folder order (Switchboard-only)
- The drawer's Projects tab lists **every** project in custom order (`listAllProjectsInCustomOrder`), never one
  page. Long-press drags (`components/LazyReorder.kt`, shared with Bookmarks); TalkBack/VoiceOver get Move
  up/down actions.
- The gesture lives on the LazyColumn, not the rows, and consumes in the Initial pass once held, so row click,
  list scroll and drawer swipe never see the drag. Only folder rows pick up; long-press on a chat in an open
  folder is that chat's menu.
- `DrawerViewModel.reorderProjects` applies and saves at once; a load in flight restarts behind the save so it
  can't land the old order. The Projects index offers it read-only as `ProjectSort.CUSTOM`.

## Project context: instructions and files (rc1)
- Gate: `projectContextGate` (`viewmodel/ProjectContextGate.kt`) → `ProjectContextGate`;
  `ProjectChatsViewModel.projectContext` → `ProjectContextUi`. Screens never read config. VERSION_GATES.md
  explains the date and `== PRESENT`.
- The project page is network-direct. **Every add, remove and instructions save bumps `contextRevision`; replace
  the held project with the response** (`ProjectFilesController.onProjectUpdated`), or the next save sends a
  stale revision and reads as a conflict. `ProjectInstructionsViewModel` re-GETs on open.
- A save conflict is only a 409 with `code: CHAT_PROJECT_CONFLICT`; on `POST /:id/files` any 409 is the file
  limit (`ProjectContextFailure`). Name/description edits never send `contextRevision`.
- Uploads run one at a time: `POST /api/files` (agents, `file_search`, `message_file`), refuse a non-`embedded`
  result, then attach. A failed attach keeps the file id; Retry repeats only the attach.
- `ProjectFilePicker` is an expect/actual copy of the agent editor's (features can't share). iOS must read the
  bytes inside the picker delegate while the security scope is open.
- The instructions draft is not saved across process death (200,000 chars nears the Bundle limit).
- Errors are string resources (`ShowErrorRes`). Description capped at `projects.maxDescriptionLength` (server
  400s above it); name at 100 (server truncates).

## Pull request marks (rc1)
- `drawer/DrawerPullRequests.kt`: while the drawer is open, shown rows that may be code chats
  (`isCodeConversation != false`) and aren't running are asked via the batch route (chunks of 50,
  `pullRequestsMaxConcurrentLookups` in flight), re-asked once an answer is a minute old. Rows never poll.
  Pull-to-refresh (`refreshConversations` → `refreshNow`) re-asks rows older than `PULL_REQUEST_ROW_FORCE_MS`.
  A credential change (`PullRequestRepository.revision`) cancels lookups in flight and re-asks every shown row
  (now, or at the next open); an account switch cancels lookups in flight. The repository drops answers asked
  under an older revision or landing after a switch. Gate:
  `pullRequestBatchAvailable` (rc1 + `pullRequestsEnabled` + `pullRequestsBatchVersion == 1`).
- `PullRequestStateIcon` is decorative and silent; the row carries the state description. Hidden on running
  rows. Data: `:core:data` `PullRequestRepository` (memory only, per account, shared with chat).
