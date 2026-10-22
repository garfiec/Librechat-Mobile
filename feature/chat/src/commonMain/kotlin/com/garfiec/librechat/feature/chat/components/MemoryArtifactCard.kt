package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * One memory write, from the call's `memory` attachment payload when the server sent one and from
 * the tool-call output otherwise. The fallback carries the tool's readable sentence as [content]
 * with no [title] — servers predating the payload, and any call whose artifact was dropped, still
 * render a card that way.
 */
data class MemoryArtifact(
    val title: String?,
    val content: String?,
    val key: String? = null,
    val kind: MemoryChangeKind = MemoryChangeKind.UPDATE,
    /** Set only when [kind] is [MemoryChangeKind.ERROR] and the failure blob parsed. */
    val error: MemoryErrorInfo? = null,
)

/**
 * The inline card for one memory write. Tertiary container colours, switching to the error
 * container when the write was refused (upstream restyles its disclosure the same way).
 *
 * [onManage], when non-null, draws a Manage action that opens the Memories page; the caller passes
 * null for a role that may not read memories.
 *
 * [stateKey] ([memoryCardStateKey]) names the card on both sides of the hand-off: a card opened
 * while the reply streams ([live]) stays open when the saved message replaces it.
 */
@Composable
fun MemoryArtifactCard(
    artifact: MemoryArtifact,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    stateKey: String = "",
    live: Boolean = false,
) {
    var isExpanded by rememberCarriedExpansion("memory", stateKey, live)

    val isError = artifact.kind == MemoryChangeKind.ERROR
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    // A delete carries no value, so the card would otherwise be a bare key; an error artifact's
    // value is a JSON blob, which `memoryErrorMessage` turns into the sentence upstream shows. A
    // refusal the tool reported in prose carries that sentence as content instead.
    val body = when (artifact.kind) {
        MemoryChangeKind.ERROR -> artifact.content ?: memoryErrorMessage(artifact.error)
        MemoryChangeKind.DELETE -> artifact.content ?: stringResource(Res.string.memory_deleted)
        MemoryChangeKind.UPDATE -> artifact.content
    }

    // No label of its own: the badge ("Memory" or "Memory error") and the title already say what the
    // card is, and a container label would be one more focus stop saying it again.
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Memory badge: a label, not a control, so it carries no click and reads as text.
                val badgeColor = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                }
                Text(
                    text = if (isError) {
                        stringResource(Res.string.memory_error)
                    } else {
                        stringResource(Res.string.label_memory)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthInFraction(max = 0.6f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )

                if (!artifact.title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Content preview
            if (!body.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                // A toggle only when there is more to show (or, open, to fold it back): a body that
                // fits in two lines is plain text, not a button that does nothing.
                var overflows by remember(body) { mutableStateOf(false) }
                val canToggle = isExpanded || overflows
                val bodyState =
                    stringResource(if (isExpanded) Res.string.state_expanded else Res.string.state_collapsed)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout -> if (!isExpanded) overflows = layout.hasVisualOverflow },
                    // No clickable at all when it can't toggle: a disabled one still reads as
                    // "button, disabled" to TalkBack and VoiceOver.
                    modifier = if (canToggle) {
                        Modifier
                            .clickable(role = Role.Button) { isExpanded = !isExpanded }
                            .semantics { stateDescription = bodyState }
                    } else {
                        Modifier
                    },
                )
            }

            if (onManage != null) {
                val manageCd = stringResource(Res.string.cd_memory_manage)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onManage,
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
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
        }
    }
}
