# feature:conversations

## Screen
`ConversationListScreen` -- the drawer content showing all conversations. Sealed interface: `ConversationsRoute : NavKey` with routes `Conversations` and `ArchivedConversations` (both `@Serializable` data objects).

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
- The drawer's Projects tab lists **every** project in the user's custom order (`ProjectRepository.listAllProjectsInCustomOrder`), never one page of them. Long-press a folder row to drag it (`drawer/ProjectFolderReorder.kt`); TalkBack/VoiceOver get Move up / Move down actions instead.
- The gesture lives on the LazyColumn, not the rows, and consumes in the Initial pass once a folder is held, so the row's click, the list scroll and the drawer swipe never see the drag. Only the folder row picks up; a long-press on an open folder's chat is that chat's menu.
- `DrawerViewModel.reorderProjects` applies the drop at once and saves it; a load in flight is restarted behind the save so it can't land the old order over the drop.
- The Projects index offers the same order as `ProjectSort.CUSTOM` (read-only).
