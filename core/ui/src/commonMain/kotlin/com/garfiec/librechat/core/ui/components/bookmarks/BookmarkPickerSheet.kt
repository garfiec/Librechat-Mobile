package com.garfiec.librechat.core.ui.components.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.bookmark_chat_count
import com.garfiec.librechat.core.ui.resources.bookmark_new
import com.garfiec.librechat.core.ui.resources.bookmarks
import com.garfiec.librechat.core.ui.resources.bookmarks_no_match
import com.garfiec.librechat.core.ui.resources.bookmarks_none_yet
import com.garfiec.librechat.core.ui.resources.cd_bookmarked
import com.garfiec.librechat.core.ui.resources.cd_not_bookmarked
import com.garfiec.librechat.core.ui.resources.search_bookmarks
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One bookmark as the checklist shows it. */
@Immutable
data class BookmarkItem(
    val name: String,
    val description: String? = null,
    /** How many chats carry it. */
    val count: Int = 0,
)

/**
 * The per-chat bookmark checklist. [items] in the order the user keeps them (Saved never among
 * them); [attached] the ones this chat carries; [pending] the ones whose save is still out, shown
 * disabled; [loading] while the chat's own bookmarks are being read, when no tick can be trusted.
 */
@Immutable
data class BookmarkPickerUiState(
    val items: List<BookmarkItem> = emptyList(),
    val attached: Set<String> = emptySet(),
    val pending: Set<String> = emptySet(),
    val loading: Boolean = false,
)

/** At this many bookmarks the checklist gets a search field. */
const val BOOKMARK_SEARCH_THRESHOLD = 8

/**
 * The checklist of a chat's bookmarks. Each tap is the caller's to save at once ([onToggle]); the
 * sheet only renders [state]. "New bookmark" comes first and goes to [onNewBookmark].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkPickerSheet(
    state: BookmarkPickerUiState,
    onToggle: (name: String) -> Unit,
    onNewBookmark: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.clearFocusOnTap(),
        dragHandle = { LowProfileDragHandle() },
    ) {
        BookmarkPickerSheetContent(state = state, onToggle = onToggle, onNewBookmark = onNewBookmark)
    }
}

/** The sheet's body, apart from the sheet so previews can render it. */
@Composable
fun ColumnScope.BookmarkPickerSheetContent(
    state: BookmarkPickerUiState,
    onToggle: (name: String) -> Unit,
    onNewBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Session-only, like every local search here: a reopened sheet starts with the whole list.
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = state.items.size >= BOOKMARK_SEARCH_THRESHOLD
    val trimmed = query.trim()
    val shown = remember(state.items, trimmed, searchable) {
        if (!searchable || trimmed.isEmpty()) {
            state.items
        } else {
            state.items.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    Text(
        text = stringResource(Res.string.bookmarks),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics { heading() },
    )
    if (searchable && !state.loading) {
        AdaptiveOutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.search_bookmarks)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "new", contentType = "new") {
            NewBookmarkRow(onClick = onNewBookmark, enabled = !state.loading)
        }
        when {
            state.loading -> items(PLACEHOLDER_ROWS, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                BookmarkRowPlaceholder()
            }
            state.items.isEmpty() -> item(key = "none", contentType = "message") {
                SheetMessage(stringResource(Res.string.bookmarks_none_yet))
            }
            shown.isEmpty() -> item(key = "no_match", contentType = "message") {
                SheetMessage(stringResource(Res.string.bookmarks_no_match, trimmed))
            }
            else -> items(shown, key = { "bookmark_${it.name}" }, contentType = { "bookmark" }) { item ->
                BookmarkRow(
                    item = item,
                    attached = item.name in state.attached,
                    pending = item.name in state.pending,
                    onToggle = { onToggle(item.name) },
                )
            }
        }
    }
}

@Composable
private fun NewBookmarkRow(onClick: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(Res.string.bookmark_new),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookmarkRow(item: BookmarkItem, attached: Boolean, pending: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (attached) Res.string.cd_bookmarked else Res.string.cd_not_bookmarked)
    val countLabel = pluralStringResource(Res.plurals.bookmark_chat_count, item.count, item.count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = attached, enabled = !pending, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = state }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (pending) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (attached) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (pending) PENDING_ALPHA else 1f),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.description?.trim()?.takeIf { it.isNotEmpty() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.count.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = countLabel },
        )
    }
}

@Composable
private fun SheetMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

/** Stand-in for a row while the chat's bookmarks load, at the row's height so nothing jumps. */
@Composable
private fun BookmarkRowPlaceholder() {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).background(block, PlaceholderShape))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(block, PlaceholderShape))
            Box(Modifier.fillMaxWidth(0.8f).height(8.dp).background(block, PlaceholderShape))
        }
    }
}

private const val PLACEHOLDER_ROWS = 3
private const val PENDING_ALPHA = 0.6f
private val PlaceholderShape = RoundedCornerShape(4.dp)
