package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.search_more_chats
import com.garfiec.librechat.feature.conversations.resources.search_no_messages
import com.garfiec.librechat.feature.conversations.resources.search_searching_server
import com.garfiec.librechat.feature.conversations.resources.search_section_conversations
import com.garfiec.librechat.feature.conversations.resources.search_section_messages
import com.garfiec.librechat.feature.conversations.resources.search_see_all
import com.garfiec.librechat.feature.conversations.resources.search_see_all_capped
import com.garfiec.librechat.feature.conversations.resources.search_untitled_conversation
import com.garfiec.librechat.feature.conversations.search.HitMeta
import com.garfiec.librechat.feature.conversations.search.SearchNoteCard
import com.garfiec.librechat.feature.conversations.search.SnippetText
import org.jetbrains.compose.resources.stringResource

private val HitShape = RoundedCornerShape(8.dp)
private val SectionLetterSpacing = 0.8.sp

/** The "Conversations · N" label over the drawer's title matches while searching. */
internal fun LazyListScope.conversationResultsHeader(count: Int) {
    item(key = "search_conversations_header") {
        SearchSectionLabel(stringResource(Res.string.search_section_conversations), count)
    }
}

/**
 * The drawer's Messages section: up to [DrawerMessageSearch.PREVIEW_COUNT] hits, a "See all" link
 * to the Search screen when there are more (and a screen to open), progress while the server is
 * asked, and a caption when only cached messages could be searched.
 */
internal fun LazyListScope.messageResultsSection(
    search: DrawerMessageSearch,
    onHitClick: (MessageSearchHit) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    if (!search.searched) return
    item(key = "search_messages_header") {
        SearchSectionLabel(stringResource(Res.string.search_section_messages), search.totalCount)
    }
    if (search.preview.isEmpty() && !search.searchingServer) {
        item(key = "search_messages_empty") {
            Text(
                text = stringResource(Res.string.search_no_messages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    items(items = search.preview, key = { "msg_${it.messageId}" }, contentType = { "message_hit" }) { hit ->
        DrawerMessageHitItem(hit = hit, onClick = { onHitClick(hit) })
    }
    if (search.searchingServer) {
        item(key = "search_messages_server") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.search_searching_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onSeeAll != null && search.totalCount > 0) {
        item(key = "search_messages_see_all") {
            SearchLinkRow(
                text = stringResource(
                    if (search.countCapped) Res.string.search_see_all_capped else Res.string.search_see_all,
                    search.totalCount,
                ),
                onClick = onSeeAll,
            )
        }
    }
    search.note?.let { note ->
        item(key = "search_messages_note") {
            SearchNoteCard(note, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/** "N more chats in Search" under the drawer's capped title hits. */
internal fun LazyListScope.moreChatsLink(more: Int, onClick: () -> Unit) {
    item(key = "search_conversations_more") {
        SearchLinkRow(text = stringResource(Res.string.search_more_chats, more), onClick = onClick)
    }
}

/** A primary-coloured row with a trailing arrow that leads into the Search screen. */
@Composable
internal fun SearchLinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(HitShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SearchSectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = SectionLetterSpacing,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerMessageHitItem(hit: MessageSearchHit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(HitShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        HitMeta(hit = hit, title = hit.conversationTitle ?: stringResource(Res.string.search_untitled_conversation))
        SnippetText(snippet = hit.snippet, modifier = Modifier.padding(top = 2.dp))
    }
}
