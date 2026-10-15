package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.data.search.RemoteSkipReason
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.search_cached_only
import com.garfiec.librechat.feature.conversations.resources.search_date_cached_only
import com.garfiec.librechat.feature.conversations.resources.search_note_failed
import com.garfiec.librechat.feature.conversations.resources.search_note_offline
import com.garfiec.librechat.feature.conversations.resources.search_note_server_off
import com.garfiec.librechat.feature.conversations.resources.search_regex_cached_only
import org.jetbrains.compose.resources.stringResource

/** Why a search's results are cached-only, each with its own wording. */
enum class SearchNote { ServerOff, Offline, Failed, Regex, DateFilter, CachedOnly }

/** The note for a result, or null when the server answered (or wasn't needed for a short query). */
fun searchNote(status: RemoteSearchStatus, skipped: RemoteSkipReason?): SearchNote? = when {
    status == RemoteSearchStatus.Failed -> SearchNote.Failed
    else -> when (skipped) {
        RemoteSkipReason.ServerOff -> SearchNote.ServerOff
        RemoteSkipReason.Offline -> SearchNote.Offline
        RemoteSkipReason.Regex -> SearchNote.Regex
        RemoteSkipReason.DateFilter -> SearchNote.DateFilter
        RemoteSkipReason.CachedOnly -> SearchNote.CachedOnly
        null -> null
    }
}

@Composable
fun SearchNote.text(): String = stringResource(
    when (this) {
        SearchNote.ServerOff -> Res.string.search_note_server_off
        SearchNote.Offline -> Res.string.search_note_offline
        SearchNote.Failed -> Res.string.search_note_failed
        SearchNote.Regex -> Res.string.search_regex_cached_only
        SearchNote.DateFilter -> Res.string.search_date_cached_only
        SearchNote.CachedOnly -> Res.string.search_cached_only
    },
)

private val NoteShape = RoundedCornerShape(12.dp)

@Composable
fun SearchNoteCard(note: SearchNote, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, NoteShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = note.text(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
