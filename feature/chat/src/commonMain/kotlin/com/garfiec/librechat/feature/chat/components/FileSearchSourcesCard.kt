package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_file_search_source
import com.garfiec.librechat.feature.chat.resources.cd_open_cited_file
import com.garfiec.librechat.feature.chat.resources.file_search_pages
import com.garfiec.librechat.feature.chat.resources.file_search_relevance
import com.garfiec.librechat.feature.chat.resources.files_searched
import com.garfiec.librechat.feature.chat.resources.files_searching
import com.garfiec.librechat.feature.chat.resources.search_cancelled
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * One cited passage from a `file_search` attachment, already merged across the chunks that came
 * from the same file. [fileId] is the dedup key; [relevance] is 0..1.
 */
data class FileSearchCitation(
    val fileId: String,
    val fileName: String,
    val relevance: Double,
    val content: String,
    val pages: List<Int> = emptyList(),
    val fileType: String? = null,
    /** [fileId] is the server's id for the file, so it can be opened; false when it is only a name. */
    val openable: Boolean = false,
    val bytes: Long? = null,
    /** The server's storage strategy (`local`, `s3`, …); upstream offers no download for `local`. */
    val storageType: String? = null,
)

/** A [FileSearchCitation] as an attachment the shared file opener understands. */
internal fun FileSearchCitation.asAttachment(): Attachment? =
    if (openable) Attachment(fileId = fileId, filename = fileName, type = fileType, bytes = bytes) else null

/**
 * File-search citations, shaped like [WebSearchSourcesCard] and mirroring upstream
 * `RetrievalCall.tsx`.
 */
@Suppress("LongParameterList")
@Composable
internal fun FileSearchSourcesCard(
    citations: List<FileSearchCitation>,
    modifier: Modifier = Modifier,
    stateKey: String = "",
    verdict: ToolCallVerdict = ToolCallVerdict.COMPLETED,
    /** The model's own label for the call; replaces "Searching/Searched your files". */
    intent: String? = null,
    durationMs: Long? = null,
    /** Opens a file the search cited (a row's name); null where nothing can open one. */
    onOpen: ((FileSearchCitation) -> Unit)? = null,
) {
    var isExpanded by rememberSaveable(key = "filesearch:$stateKey") { mutableStateOf(false) }
    val running = verdict == ToolCallVerdict.RUNNING
    val label = when (verdict) {
        ToolCallVerdict.RUNNING -> intent ?: stringResource(Res.string.files_searching)
        ToolCallVerdict.CANCELLED -> stringResource(Res.string.search_cancelled)
        else -> intent ?: stringResource(Res.string.files_searched)
    }
    val localeTag = LocalDateTimeFormat.current.localeTag
    val duration = durationMs?.takeIf { !running }?.let { formatTraceDuration(it, localeTag) }
    val expandable = !running && citations.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .then(if (expandable) Modifier.clickable { isExpanded = !isExpanded } else Modifier)
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .semantics(mergeDescendants = true) {
                    if (expandable) role = Role.Button
                    if (running) liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (running) {
                ShimmeringLabel(text = label, modifier = Modifier.weight(1f, fill = false))
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            duration?.let {
                Text(
                    text = " · $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expandable) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded && expandable,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            ) {
                citations.forEachIndexed { index, citation ->
                    CitationRow(
                        citation = citation,
                        stateKey = "$stateKey:${citation.fileId}",
                        onOpen = onOpen?.takeIf { citation.openable }?.let { open -> { open(citation) } },
                    )
                    if (index < citations.lastIndex) {
                        AdaptiveDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CitationRow(citation: FileSearchCitation, stateKey: String, onOpen: (() -> Unit)?) {
    var showContent by rememberSaveable(key = "filesearchsrc:$stateKey") { mutableStateOf(false) }
    val hasContent = citation.content.isNotBlank()
    val rowCd = stringResource(Res.string.cd_file_search_source, citation.fileName)
    val relevancePercent = remember(citation.relevance) { (citation.relevance * 100).roundToInt() }
    val kind = remember(citation.fileName, citation.fileType) { fileKindOf(citation.fileName, citation.fileType) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasContent) { showContent = !showContent }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                if (hasContent) role = Role.Button
                contentDescription = rowCd
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTypeTile(kind = kind, size = 24.dp)
            Spacer(modifier = Modifier.width(12.dp))
            // The name opens the file (upstream's preview); the rest of the row toggles the passage.
            val openLabel = stringResource(Res.string.cd_open_cited_file, citation.fileName)
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = citation.fileName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (onOpen != null) TextDecoration.Underline else null,
                    ),
                    color = if (onOpen != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onOpen != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                    } else {
                        Modifier
                    },
                )
            }
            if (relevancePercent > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.file_search_relevance, relevancePercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (citation.pages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.file_search_pages, citation.pages.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (hasContent) {
            AnimatedVisibility(
                visible = showContent,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = citation.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
