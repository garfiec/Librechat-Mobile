package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_memory_artifacts_summary
import com.garfiec.librechat.feature.chat.resources.cd_memory_manage
import com.garfiec.librechat.feature.chat.resources.memory_already_exceeded
import com.garfiec.librechat.feature.chat.resources.memory_deleted
import com.garfiec.librechat.feature.chat.resources.memory_deleted_items
import com.garfiec.librechat.feature.chat.resources.memory_error
import com.garfiec.librechat.feature.chat.resources.memory_manage
import com.garfiec.librechat.feature.chat.resources.memory_storage_full
import com.garfiec.librechat.feature.chat.resources.memory_updated
import com.garfiec.librechat.feature.chat.resources.memory_updated_items
import com.garfiec.librechat.feature.chat.resources.memory_would_exceed
import com.garfiec.librechat.feature.chat.resources.state_collapsed
import com.garfiec.librechat.feature.chat.resources.state_expanded
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What a `memory` attachment records. Upstream `MemoryArtifact.type`. */
enum class MemoryChangeKind { UPDATE, DELETE, ERROR }

/**
 * The parsed contents of a [MemoryChangeKind.ERROR] artifact's `value`, which the server sends as
 * a JSON blob rather than a sentence. Absent when the blob was unparseable or carried an
 * `errorType` this client has no message for; the generic error label covers both, exactly as
 * upstream `MemoryInfo` does.
 */
data class MemoryErrorInfo(
    val errorType: String?,
    val tokenCount: Int?,
)

/** The message for a storage-limit error, or the generic label when it is not one we know. */
@Composable
internal fun memoryErrorMessage(error: MemoryErrorInfo?): String {
    val tokens = error?.tokenCount
    val localeTag = LocalDateTimeFormat.current.localeTag
    val formatted = tokens?.let { formatLocalizedNumber(it.toDouble(), localeTag) }
    return when {
        tokens == null || formatted == null -> stringResource(Res.string.memory_error)
        error.errorType == MEMORY_ERROR_ALREADY_EXCEEDED ->
            pluralStringResource(Res.plurals.memory_already_exceeded, tokens, formatted)
        error.errorType == MEMORY_ERROR_WOULD_EXCEED ->
            pluralStringResource(Res.plurals.memory_would_exceed, tokens, formatted)
        else -> stringResource(Res.string.memory_error)
    }
}

/**
 * The memory writes no tool card in this message accounts for (see
 * [collectUnrenderedMemoryArtifacts]), as a collapsed "Updated saved memory" line — red, "Memory
 * Error", when any entry failed — expanding to the keys written, deleted, and refused. Mirrors
 * upstream `MemoryArtifacts.tsx` + `MemoryInfo.tsx`.
 *
 * [onManage], when non-null, draws the same Manage action as [MemoryArtifactCard] at the end of
 * the line; callers pass [LocalOpenMemories], which is null for a role that may not read memories.
 *
 * [stateKey] ([memoryArtifactsStateKey]) names the line on both sides of the live-to-saved hand-off.
 */
@Composable
fun MemoryArtifactsSection(
    artifacts: List<MemoryArtifact>,
    modifier: Modifier = Modifier,
    stateKey: String = "",
    onManage: (() -> Unit)? = null,
    // The reply is still streaming: an open line stays open when the saved message replaces it.
    live: Boolean = false,
) {
    if (artifacts.isEmpty()) return

    var isExpanded by rememberCarriedExpansion("memoryartifacts", stateKey, live)

    val updated = artifacts.filter { it.kind == MemoryChangeKind.UPDATE }
    val deleted = artifacts.filter { it.kind == MemoryChangeKind.DELETE }
    val failed = artifacts.filter { it.kind == MemoryChangeKind.ERROR }

    val accent = if (failed.isEmpty()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }
    val label = if (failed.isEmpty()) {
        stringResource(Res.string.memory_updated)
    } else {
        stringResource(Res.string.memory_error)
    }
    // Read in place of the merged Text, so it carries the label: a screen reader must hear "Memory
    // Error" when a write was refused, not just how many entries there are.
    val localeTag = LocalDateTimeFormat.current.localeTag
    val sectionCd = pluralStringResource(
        Res.plurals.cd_memory_artifacts_summary,
        artifacts.size,
        label,
        formatLocalizedNumber(artifacts.size.toDouble(), localeTag),
    )
    val sectionState = stringResource(if (isExpanded) Res.string.state_expanded else Res.string.state_collapsed)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtifactsToggle(
                label = label,
                accent = accent,
                isExpanded = isExpanded,
                contentDescription = sectionCd,
                stateDescription = sectionState,
                onToggle = { isExpanded = !isExpanded },
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onManage != null) {
                val manageCd = stringResource(Res.string.cd_memory_manage)
                TextButton(
                    onClick = onManage,
                    modifier = Modifier.semantics { contentDescription = manageCd },
                ) {
                    Text(
                        text = stringResource(Res.string.memory_manage),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            MemoryArtifactDetail(updated = updated, deleted = deleted, failed = failed)
        }
    }
}

/** The collapsed line's toggle: bookmark, label and chevron, one button that opens the detail. */
@Composable
private fun ArtifactsToggle(
    label: String,
    accent: Color,
    isExpanded: Boolean,
    contentDescription: String,
    stateDescription: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // A full touch target: the icon and the label alone make the line about 28dp tall.
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = accent,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = accent,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = accent,
        )
    }
}

/** The expanded body: written keys, deleted keys, then refusals. Upstream `MemoryInfo.tsx`. */
@Composable
private fun MemoryArtifactDetail(
    updated: List<MemoryArtifact>,
    deleted: List<MemoryArtifact>,
    failed: List<MemoryArtifact>,
) {
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        if (updated.isNotEmpty()) {
            GroupHeader(stringResource(Res.string.memory_updated_items))
            updated.forEach { artifact ->
                MemoryKeyRow(
                    key = artifact.title,
                    body = artifact.content,
                    bodyColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (deleted.isNotEmpty()) {
            if (updated.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            GroupHeader(stringResource(Res.string.memory_deleted_items))
            deleted.forEach { artifact ->
                MemoryKeyRow(
                    key = artifact.title,
                    body = stringResource(Res.string.memory_deleted),
                    bodyColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    italicBody = true,
                )
            }
        }
        if (failed.isNotEmpty()) {
            if (updated.isNotEmpty() || deleted.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            GroupHeader(
                text = stringResource(Res.string.memory_storage_full),
                color = MaterialTheme.colorScheme.error,
            )
            failed.forEach { artifact ->
                Text(
                    text = memoryErrorMessage(artifact.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun MemoryKeyRow(
    key: String?,
    body: String?,
    bodyColor: Color,
    italicBody: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (!key.isNullOrBlank()) {
            Text(
                text = key,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = if (italicBody) FontStyle.Italic else FontStyle.Normal,
                ),
                color = bodyColor,
            )
        }
    }
}
