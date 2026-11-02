# feature:conversations

## Screens
The conversation list lives in the drawer (`drawer/DrawerContent.kt`, `DrawerViewModel`); there is no full-screen list route. `ConversationsRoute : NavKey` covers the screens the drawer opens: `ArchivedConversations`, `Projects`, `Bookmarks`, `BookmarkChats`, `Search`, `ProjectChats`, and `ProjectInstructions` (opened from a project page).

## Pagination
- Cursor-based via `ConversationRepository.loadNextPage(cursor, tags)`
- `nextCursor: String?` in UI state; null = no more pages
- `loadMore()` called when LazyColumn nears the end
- Pull-to-refresh via `refresh()` which resets to first page

## Date Grouping
- `DateGroupHeader` renders section labels: Today, Yesterday, Previous 7 Days, Previous 30 Days, month-year. Grouping keys are `DateGroup` (core/common), localized at render via `DateGroup.resolve()`; the drawer wraps them as `DrawerGroupKey` to add its Running group. Row timestamps read the app-wide `LocalRelativeTimeReference` ticker (core/ui), so no list provides its own
- Conversations are flattened into a sealed `ConversationListItem` (DateHeader | ConvoItem)

## Search
- `ConversationSearchBar` with 500ms debounce (via coroutine delay)
- `onSearchQueryChanged()` cancels previous search job, waits 500ms, calls `SearchRepository.search()`
- When search is active, normal pagination is disabled (`hasMore = false`)
- Clear search restores normal conversation list

## Tags
- `TagFilterBar` displays `FilterChip` per tag (multi-select toggle)
- `TagPicker` for assigning tags to a conversation
- Tags observed via `TagRepository.observeTags()` (Room-backed Flow); `SAVED_TAG` and `count == 0` tags are filtered out in the ViewModel
- `toggleTag()` / `clearTagFilter()` reload conversations with new filter

## CRUD Actions (via `ConversationActions` component)
| Action | API | Notes |
|--------|-----|-------|
| Rename | `POST /api/convos/update` | Inline rename, max 100 chars |
| Archive | `POST /api/convos/archive` | `{ conversationId, isArchived: true }` |
| Delete | `DELETE /api/convos` | Confirmation dialog required |
| Duplicate | `POST /api/convos/duplicate` | Navigates to new conversation |
| Fork | `POST /api/convos/fork` | From message context menu in chat, not here |
| Share | Opens the share sheet (`ShareLinkController`, `ShareLinkSheetHost`) | `sharedLinksEnabled` AND SHARED_LINKS CREATE; no target (shares the newest branch) |

## Export / Import
- `ConversationExporter` supports `ExportFormat.JSON` and `ExportFormat.MARKDOWN`
- Export is client-side: fetches messages, serializes locally, emits `ExportReady` event
- `ExportFormatPicker` component for format selection
- `ConversationImporter` parses JSON (LibreChat/ChatGPT format) via `POST /api/convos/import`
- File I/O uses SAF (Storage Access Framework)

## Events
`ConversationListEvent` sealed interface for one-shot UI events: `NavigateToConversation`, `NavigateToNewChat` (emitted when the currently-open conversation is deleted, so the host moves off the now-gone thread), `ShowError`, `ExportReady`, `ImportSuccess`

## Project folder order (Switchboard-only)
- The drawer's Projects tab lists **every** project in the user's custom order (`ProjectRepository.listAllProjectsInCustomOrder`), never one page of them. Long-press a folder row to drag it (`components/LazyReorder.kt`, shared with the Bookmarks index); TalkBack/VoiceOver get Move up / Move down actions instead.
- The gesture lives on the LazyColumn, not the rows, and consumes in the Initial pass once a folder is held, so the row's click, the list scroll and the drawer swipe never see the drag. Only the folder row picks up; a long-press on an open folder's chat is that chat's menu.
- `DrawerViewModel.reorderProjects` applies the drop at once and saves it; a load in flight is restarted behind the save so it can't land the old order over the drop.
- The Projects index offers the same order as `ProjectSort.CUSTOM` (read-only).

## Project context: instructions and files (v0.8.9-rc1)
- Gate: `projectContextGate` (`viewmodel/ProjectContextGate.kt`) maps config, endpoints and role once into
  `ProjectContextGate`; `ProjectChatsViewModel.projectContext` turns it plus the held project and the files
  state into `ProjectContextUi`. Screens never read config. See VERSION_GATES.md for why the date is the
  day after landing and why it is `== PRESENT`.
- The project page is network-direct; `ProjectChatsViewModel` holds the project. **Every add, remove and
  instructions save bumps `contextRevision`**, and the held project must be replaced with the response
  (`ProjectFilesController.onProjectUpdated`), or the next instructions save sends a stale revision and
  reads as a conflict. The instructions editor (`ProjectInstructionsViewModel`) re-GETs the project on
  open, so it starts from the current revision.
- A save conflict is only a 409 with `code: CHAT_PROJECT_CONFLICT`; on `POST /:id/files` any 409 is the
  file limit (`:core:data` `ProjectContextFailure`). Name/description edits never send `contextRevision`.
- Uploads (`ProjectFilesController`) run one at a time: `POST /api/files` (agents, `file_search`,
  `message_file`), refuse a result that isn't `embedded`, then attach. The upload alone is a temporary
  attachment, so a failed attach keeps the file id and Retry repeats only the attach.
- `ProjectFilePicker` is an expect/actual copy of the agent editor's picker (feature modules can't
  share it). iOS reads the bytes inside the picker delegate, while the security scope is open.
- The draft is not saved across process death: at the 200,000-character ceiling it would come near the
  Bundle limit.
- Project errors are string resources (`ShowErrorRes`); the description field is capped at
  `projects.maxDescriptionLength` (the server answers 400 above it) and the name at 100 (the server cuts it).

## Pull request marks (v0.8.9-rc1)
- `drawer/DrawerPullRequests.kt`: while the drawer is open, the shown rows that may be code chats
  (`Conversation.isCodeConversation != false`) and aren't running are asked through the batch route,
  one batch of up to 50, re-asked once their answer is a minute old. Rows never poll. Gate:
  `pullRequestBatchAvailable` (rc1 + `pullRequestsEnabled` + `pullRequestsBatchVersion == 1`).
- The mark (`PullRequestStateIcon`, 16dp) sits before the star, is decorative (the row still opens the
  chat, whose ⋮ has the detail) and carries the summary as its description. Hidden on running rows.
- Answers come from `:core:data` `PullRequestRepository` (memory only, per account), shared with the chat.
