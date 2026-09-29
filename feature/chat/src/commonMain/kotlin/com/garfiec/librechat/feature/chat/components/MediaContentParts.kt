package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

// ─── ImageContentPart ───────────────────────────────────────────────

@Composable
internal fun ImageContentPart(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (imageUrl == null) return

    val openMedia = LocalChatMediaViewer.current

    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = stringResource(Res.string.cd_embedded_image),
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { openMedia(imageUrl) }
            .semantics { role = Role.Image },
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    stringResource(Res.string.cd_failed_to_load_image),
                    Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ─── ErrorContentPart ───────────────────────────────────────────────

/**
 * An error in the thread: [errorText] as the headline and, when the failure came with its own
 * words, [detail] under it — in place when it reads as one sentence, collapsed under a
 * "Provider response" toggle when it is a body of text (see [isInlineErrorDetail]).
 */
@Composable
internal fun ErrorContentPart(
    errorText: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(errorText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        if (detail == null) return@Column
        val inline = isInlineErrorDetail(detail)
        var expanded by rememberSaveable(detail) { mutableStateOf(false) }
        if (!inline) {
            // A state description, as ActivityGroup announces its own: the Row's label is already
            // its content, so a content description would read the label twice.
            val expansionState =
                stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = expansionState },
            ) {
                Text(
                    stringResource(Res.string.error_details_provider),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        if (inline || expanded) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = DETAIL_ALPHA),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val DETAIL_ALPHA = 0.8f
