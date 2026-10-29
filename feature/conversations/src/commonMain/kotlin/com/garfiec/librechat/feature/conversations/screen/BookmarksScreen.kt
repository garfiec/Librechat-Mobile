package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveExtendedFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.UnderBarsPullToRefreshBox
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.components.bookmarks.BookmarkEditorSheet
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.core.ui.resources.bookmark_new
import com.garfiec.librechat.core.ui.resources.bookmarks
import com.garfiec.librechat.core.ui.resources.bookmarks_no_match
import com.garfiec.librechat.core.ui.resources.bookmarks_none_yet
import com.garfiec.librechat.core.ui.resources.search_bookmarks
import com.garfiec.librechat.feature.conversations.components.BookmarkCard
import com.garfiec.librechat.feature.conversations.components.BookmarkCardPlaceholder
import com.garfiec.librechat.feature.conversations.components.BookmarkDeleteDialog
import com.garfiec.librechat.feature.conversations.components.lazyReorder
import com.garfiec.librechat.feature.conversations.components.rememberLazyReorderState
import com.garfiec.librechat.feature.conversations.components.rememberPickUpHaptic
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.back
import com.garfiec.librechat.feature.conversations.resources.bookmark_create_failed
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete_failed
import com.garfiec.librechat.feature.conversations.resources.bookmark_move_failed
import com.garfiec.librechat.feature.conversations.resources.bookmark_save_failed
import com.garfiec.librechat.feature.conversations.resources.bookmarks_count
import com.garfiec.librechat.feature.conversations.resources.bookmarks_empty_description
import com.garfiec.librechat.feature.conversations.resources.bookmarks_load_error
import com.garfiec.librechat.feature.conversations.resources.bookmarks_reorder_hint
import com.garfiec.librechat.feature.conversations.resources.project_move_down
import com.garfiec.librechat.feature.conversations.resources.project_move_up
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksContent
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksEvent
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksFailure
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksUiState
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

// One column on phones; two once each would still be this wide (tablets, an unfolded Fold).
private val BookmarkCardMinWidth = 300.dp
private val FabClearance = 96.dp
private const val PLACEHOLDER_COUNT = 3

/** Card keys carry a prefix: a bookmark is named by the user, and could be named like a header item. */
private const val CARD_KEY_PREFIX = "bookmark:"

private fun cardKey(name: String) = CARD_KEY_PREFIX + name

/** What a card does, for the grid and its previews. */
internal class BookmarkCardActions(
    val onClick: (name: String) -> Unit,
    val onEdit: (name: String) -> Unit,
    val onDelete: (ConversationTag) -> Unit,
    val onMoveBy: (name: String, delta: Int) -> Unit,
)

/**
 * Every bookmark of the account as cards: tap one for its chats, ⋮ to edit or delete it, hold one
 * to move it. [onBookmarkClick] opens the bookmark's chats. [onCloseSelf] runs once bookmarks are
 * turned off for the account (role or `interface.bookmarks`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBookmarkClick: (name: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseSelf: () -> Unit = onNavigateBack,
    viewModel: BookmarksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    val currentOnCloseSelf by rememberUpdatedState(onCloseSelf)
    LaunchedEffect(uiState.enabled) {
        if (!uiState.enabled) currentOnCloseSelf()
    }

    // Resolved in composition, so an in-app language switch reaches the snackbar too.
    val failureMessages by rememberUpdatedState(BookmarksFailure.entries.associateWith { stringResource(it.message) })
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is BookmarksEvent.Failed -> snackbarHostState.showSnackbar(failureMessages.getValue(event.failure))
            }
        }
    }

    val content = uiState.content
    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onNavigateBack),
                    title = BarTitle(stringResource(CoreRes.string.bookmarks)),
                ),
                materialStyle = MaterialBarStyle(translucent = true),
            )
        },
        floatingActionButton = {
            // The empty state carries its own New bookmark button; two would compete.
            if (content != BookmarksContent.EMPTY) {
                AdaptiveExtendedFloatingActionButton(
                    text = stringResource(CoreRes.string.bookmark_new),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = viewModel::openCreate,
                    expanded = fabExpanded,
                )
            }
        },
    ) { innerPadding ->
        UnderBarsPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            barPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            BookmarksGrid(
                state = uiState,
                gridState = gridState,
                contentPadding = innerPadding,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onCreate = viewModel::openCreate,
                onRetry = viewModel::retry,
                onDrop = viewModel::onDrop,
                actions = BookmarkCardActions(
                    onClick = onBookmarkClick,
                    onEdit = viewModel::openEdit,
                    onDelete = { deleteTarget = it.tag },
                    onMoveBy = viewModel::moveBy,
                ),
            )
        }
    }

    uiState.editor?.let { editor ->
        BookmarkEditorSheet(
            existingNames = uiState.bookmarks.mapNotNull { it.tag },
            onConfirm = { name, description, _ -> viewModel.save(name, description) },
            onDismiss = viewModel::closeEditor,
            editing = editor.editing?.tag,
            initialDescription = editor.editing?.description.orEmpty(),
            saving = editor.saving,
        )
    }

    deleteTarget?.let { name ->
        BookmarkDeleteDialog(
            name = name,
            chatCount = uiState.bookmarks.firstOrNull { it.tag == name }?.count ?: 0,
            onConfirm = {
                viewModel.delete(name)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private val BookmarksFailure.message: StringResource
    get() = when (this) {
        BookmarksFailure.LOAD -> Res.string.bookmarks_load_error
        BookmarksFailure.CREATE -> Res.string.bookmark_create_failed
        BookmarksFailure.SAVE -> Res.string.bookmark_save_failed
        BookmarksFailure.DELETE -> Res.string.bookmark_delete_failed
        BookmarksFailure.MOVE -> Res.string.bookmark_move_failed
    }

/** The index's body: search, the count line and the cards, or the state standing in for them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookmarksGrid(
    state: BookmarksUiState,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onSearchQueryChange: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onDrop: (orderedNames: List<String>) -> Unit,
    actions: BookmarkCardActions,
    modifier: Modifier = Modifier,
) {
    val reorder = rememberLazyReorderState(gridState)
    val cards = state.shown
    LaunchedEffect(cards) { reorder.onItemsChanged() }
    val cardKeys = remember(cards) { cards.mapNotNull { it.tag?.let(::cardKey) } }
    val currentCardKeys by rememberUpdatedState(cardKeys)
    val byKey = remember(cards) { cards.associateBy { cardKey(it.tag.orEmpty()) } }
    val shownCards = reorder.shownIds(cardKeys).mapNotNull(byKey::get)
    val pickUp = rememberPickUpHaptic {}
    val currentOnDrop by rememberUpdatedState(onDrop)
    // Searching shows part of the list, where a move has no clear meaning; Move up / Move down still
    // work there, against the whole list.
    val canDrag = state.canReorder && state.content == BookmarksContent.CARDS
    val allNames = remember(state.bookmarks) { state.bookmarks.mapNotNull { it.tag } }
    // Read through derivedStateOf: the layout info changes on every scroll frame.
    val singleColumn by remember(reorder) { derivedStateOf { reorder.canReorder } }
    val moveUpLabel = stringResource(Res.string.project_move_up)
    val moveDownLabel = stringResource(Res.string.project_move_down)

    val minCardWidth = BookmarkCardMinWidth * LocalDensity.current.fontScale
    LazyVerticalGrid(
        state = gridState,
        columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
        modifier = modifier
            .fillMaxSize()
            .lazyReorder(
                state = reorder,
                ids = { currentCardKeys },
                onPickUp = pickUp,
                onDrop = { keys -> currentOnDrop(keys.map { it.removePrefix(CARD_KEY_PREFIX) }) },
                enabled = canDrag,
            ),
        // Bottom room so the last card scrolls clear of the FAB.
        contentPadding = contentPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = FabClearance),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.showsSearch) {
            fullWidthItem(key = "search") {
                AdaptiveOutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(CoreRes.string.search_bookmarks)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
        }
        if (!state.isSearching && state.bookmarks.isNotEmpty()) {
            fullWidthItem(key = "count") {
                CountLine(count = state.bookmarks.size, showHint = state.canReorder && singleColumn)
            }
        }

        when (state.content) {
            BookmarksContent.PLACEHOLDERS -> items(PLACEHOLDER_COUNT, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                BookmarkCardPlaceholder()
            }
            BookmarksContent.ERROR -> fullWidthItem(key = "error") {
                ErrorBanner(message = stringResource(Res.string.bookmarks_load_error), onRetry = onRetry)
            }
            BookmarksContent.EMPTY -> fullWidthItem(key = "empty") {
                EmptyState(
                    title = stringResource(CoreRes.string.bookmarks_none_yet),
                    description = stringResource(Res.string.bookmarks_empty_description),
                    icon = Icons.Default.Bookmark,
                    modifier = Modifier.padding(vertical = 48.dp),
                    action = {
                        AdaptiveButton(onClick = onCreate) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(CoreRes.string.bookmark_new))
                        }
                    },
                )
            }
            BookmarksContent.NO_RESULTS -> fullWidthItem(key = "no_results") {
                EmptyState(
                    title = stringResource(CoreRes.string.bookmarks_no_match, state.searchQuery.trim()),
                    icon = Icons.Default.Search,
                    modifier = Modifier.padding(vertical = 48.dp),
                )
            }
            BookmarksContent.CARDS -> items(shownCards, key = { cardKey(it.tag.orEmpty()) }, contentType = { "bookmark" }) { bookmark ->
                val name = bookmark.tag.orEmpty()
                val key = cardKey(name)
                val held = reorder.heldId == key
                val index = allNames.indexOf(name)
                BookmarkCard(
                    bookmark = bookmark,
                    onClick = { actions.onClick(name) },
                    onEdit = { actions.onEdit(name) },
                    onDelete = { actions.onDelete(bookmark) },
                    lifted = held,
                    modifier = Modifier
                        // The held card follows the finger; the rest slide aside to open its new slot.
                        .then(if (held) Modifier else Modifier.animateItem())
                        .zIndex(if (held) 1f else 0f)
                        .graphicsLayer { translationY = reorder.translationOf(key) }
                        .semantics {
                            customActions = cardMoveActions(index, allNames.lastIndex, moveUpLabel, moveDownLabel) { delta ->
                                actions.onMoveBy(name, delta)
                            }
                        },
                )
            }
        }
    }
}

private fun cardMoveActions(
    index: Int,
    lastIndex: Int,
    moveUpLabel: String,
    moveDownLabel: String,
    moveBy: (Int) -> Unit,
): List<CustomAccessibilityAction> = buildList {
    if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { moveBy(-1); true })
    if (index in 0 until lastIndex) add(CustomAccessibilityAction(moveDownLabel) { moveBy(1); true })
}

/** "6 bookmarks", and how to reorder them when a hold would pick a card up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountLine(count: Int, showHint: Boolean) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = pluralStringResource(Res.plurals.bookmarks_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (showHint) {
            Text(
                text = stringResource(Res.string.bookmarks_reorder_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable LazyGridItemScope.() -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key, content = content)
