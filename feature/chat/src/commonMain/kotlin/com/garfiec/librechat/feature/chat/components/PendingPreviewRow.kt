package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.artifactTypeLabel
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactButton
import com.garfiec.librechat.feature.chat.components.artifact.artifactTypeIcon
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_fallback
import com.garfiec.librechat.feature.chat.resources.cd_download_file
import com.garfiec.librechat.feature.chat.resources.cd_open_artifact
import com.garfiec.librechat.feature.chat.resources.preview_failed_download
import com.garfiec.librechat.feature.chat.resources.preview_preparing
import com.garfiec.librechat.feature.chat.resources.text_n_lines
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class PreviewPhase { PENDING, FAILED, READY }

private val ToolAttachment.PanelRow.phase: PreviewPhase
    get() = when (this) {
        is ToolAttachment.ArtifactContent -> PreviewPhase.READY
        is ToolAttachment.Pending -> if (failed) PreviewPhase.FAILED else PreviewPhase.PENDING
    }

/** Cached per core/ui Compose perf rule 13. Matches [ArtifactButton]'s card. */
private val ROW_SHAPE = RoundedCornerShape(12.dp)

private const val PULSE_MS = 240
private const val PULSE_COUNT = 2
private const val GLYPH_START_SCALE = 0.6f

/**
 * One entry in a tool call's artifact slot, in whichever phase its preview is in:
 *  - **pending:** a spinner, the filename, "Preparing preview…" and a download glyph;
 *  - **failed:** an alert, the filename, "Preview unavailable · download to view";
 *  - **ready:** the [ArtifactButton] for it.
 *
 * Upstream's `PreviewPlaceholderRow`, with the same box as the artifact card so the list doesn't
 * reflow when the preview lands. Tapping a pending or failed row downloads the file, the one thing
 * there is to do with it, through the same [ToolFileActions.openToolFile] a ready row opens with.
 *
 * A row this composition saw go from pending to ready changes in place: the glyph morphs into
 * the panel icon, the text crossfades and the card pulses its container twice. Nothing opens. Under
 * reduce motion the colours just swap. A row that is ready when it first composes — a reloaded
 * message, a preview that resolved while the row was off screen — is a plain [ArtifactButton] and
 * never pulses, which is what keeps the pulse to once per file without any bookkeeping.
 */
@Composable
internal fun PendingPreviewRow(
    item: ToolAttachment.PanelRow,
    actions: ToolFileActions,
    modifier: Modifier = Modifier,
) {
    val phase = item.phase
    // Remembered under the caller's per-file key: whether this composition has seen the file
    // before it was ready. Set once; a row that starts ready keeps the plain button.
    val startedUnresolved = remember { phase != PreviewPhase.READY }
    val artifact = (item as? ToolAttachment.ArtifactContent)?.artifact
    val subtitle = artifact?.let { readySubtitle(it, item.attachment) }
    if (!startedUnresolved && artifact != null) {
        ArtifactButton(
            artifact = artifact,
            onClick = { actions.openToolFile(item.attachment) },
            modifier = modifier,
            subtitle = subtitle,
        )
        return
    }
    PreviewRowCard(
        attachment = item.attachment,
        phase = phase,
        artifact = artifact,
        readySubtitle = subtitle,
        actions = actions,
        modifier = modifier,
    )
}

/** "Plain Text · 5 lines" for a plain-text file; every other type keeps its type label. */
@Composable
private fun readySubtitle(artifact: Artifact, attachment: Attachment): String? {
    if (artifact.type != "text/plain") return null
    // Counted on the file's own text: [Artifact.content] is the same here, but a code file's is fenced.
    val lines = attachment.text.orEmpty().trimEnd('\n').count { it == '\n' } + 1
    return "${artifactTypeLabel(artifact.type)} · ${pluralStringResource(Res.plurals.text_n_lines, lines, lines)}"
}

@Composable
private fun PreviewRowCard(
    attachment: Attachment,
    phase: PreviewPhase,
    artifact: Artifact?,
    readySubtitle: String?,
    actions: ToolFileActions,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val colors = MaterialTheme.colorScheme
    val title = attachmentName(attachment)?.let { displayFilename(it) }
        ?: stringResource(Res.string.attachment_fallback)

    // The pulse plays only on an edge this composition watched happen.
    var pulsing by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        val resolved = seen == PreviewPhase.PENDING && phase == PreviewPhase.READY
        seen = phase
        if (!resolved || reduceMotion) return@LaunchedEffect
        repeat(PULSE_COUNT) {
            pulsing = true
            delay(PULSE_MS.toLong())
            pulsing = false
            delay(PULSE_MS.toLong())
        }
    }
    val colorSpec = if (reduceMotion) snap() else tween<Color>(PULSE_MS)
    val container by animateColorAsState(
        targetValue = when {
            pulsing -> colors.tertiaryContainer
            phase == PreviewPhase.READY -> colors.secondaryContainer
            else -> colors.surfaceContainerHigh
        },
        animationSpec = colorSpec,
        label = "previewRowContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            pulsing -> colors.onTertiaryContainer
            phase == PreviewPhase.READY -> colors.onSecondaryContainer
            else -> colors.onSurface
        },
        animationSpec = colorSpec,
        label = "previewRowContent",
    )
    Card(
        onClick = { actions.openToolFile(attachment) },
        enabled = actions.canOpen(attachment),
        modifier = modifier
            .fillMaxWidth()
            // Drawn here rather than as the card's colour so the animation is read in the draw
            // phase, not by recomposing the row each frame (core/ui Compose perf rule 16).
            .drawBehind { drawOutline(ROW_SHAPE.createOutline(size, layoutDirection, this), container) },
        shape = ROW_SHAPE,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = glyphMorph(reduceMotion),
                contentAlignment = Alignment.Center,
                label = "previewRowGlyph",
            ) { shown ->
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when (shown) {
                        PreviewPhase.PENDING -> AdaptiveCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        PreviewPhase.FAILED -> Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = colors.error,
                        )
                        PreviewPhase.READY -> Icon(
                            imageVector = artifactTypeIcon(artifact?.type.orEmpty()),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = content,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact?.title ?: title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (phase) {
                    PreviewPhase.PENDING -> stringResource(Res.string.preview_preparing)
                    // The server's reason, when it gave one, ahead of the generic hint.
                    PreviewPhase.FAILED -> attachment.previewError?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.preview_failed_download)
                    PreviewPhase.READY -> readySubtitle ?: artifactTypeLabel(artifact?.type.orEmpty())
                }
                AnimatedContent(targetState = subtitle, transitionSpec = crossfade(reduceMotion), label = "previewRowSubtitle") {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        // Opaque theme role rather than copy(alpha=…) per core/ui Compose perf rule 15.
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val ready = phase == PreviewPhase.READY
            AnimatedContent(targetState = ready, transitionSpec = glyphMorph(reduceMotion), label = "previewRowTrailing") { open ->
                Icon(
                    imageVector = if (open) Icons.AutoMirrored.Filled.OpenInNew else Icons.Outlined.Download,
                    contentDescription = if (open) {
                        stringResource(Res.string.cd_open_artifact)
                    } else {
                        stringResource(Res.string.cd_download_file, title)
                    },
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** A glyph shrinking out as the next grows in; a plain swap under reduce motion. */
private fun <S> glyphMorph(reduceMotion: Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
    if (reduceMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (fadeIn(tween(PULSE_MS)) + scaleIn(tween(PULSE_MS), initialScale = GLYPH_START_SCALE)) togetherWith
            (fadeOut(tween(PULSE_MS)) + scaleOut(tween(PULSE_MS), targetScale = GLYPH_START_SCALE))
    }
}

/** Text crossfade; a plain swap under reduce motion. */
private fun <S> crossfade(reduceMotion: Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
    if (reduceMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(tween(PULSE_MS)) togetherWith fadeOut(tween(PULSE_MS))
    }
}
