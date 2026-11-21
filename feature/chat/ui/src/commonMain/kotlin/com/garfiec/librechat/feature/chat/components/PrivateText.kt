package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.private_text_hidden
import com.garfiec.librechat.feature.chat.resources.private_text_loading
import com.garfiec.librechat.feature.chat.resources.private_text_retry
import com.garfiec.librechat.feature.chat.resources.private_text_unavailable
import com.garfiec.librechat.feature.chat.viewmodel.OwnerTextState
import com.garfiec.librechat.feature.chat.viewmodel.PrivateTextPhase
import com.garfiec.librechat.feature.chat.viewmodel.privateTextView
import org.jetbrains.compose.resources.stringResource

/** The originals of the open chat's redacted turns, and the retry. Provided by `MessageList`. */
@Immutable
internal class OwnerTextHost(val texts: Map<String, OwnerTextState>, val onRetry: () -> Unit) {
    companion object {
        val NONE = OwnerTextHost(emptyMap(), onRetry = {})
    }
}

internal val LocalOwnerTexts = compositionLocalOf { OwnerTextHost.NONE }

/**
 * A redacted user turn (upstream `PrivateText`): the author's original when it loaded and matches
 * the stored turn, else the filtered text, never blank; then why, and Retry after a failure.
 *
 * Display only. Copy, edit, regenerate, read aloud, fork and share keep `message.text`.
 * In-conversation search counts `message.text`, so its query is applied only while that is what
 * is drawn: highlight offsets into the original would point at the wrong words.
 */
@Composable
internal fun PrivateTextBody(
    message: Message,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    searchQuery: String?,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
) {
    val host = LocalOwnerTexts.current
    val view = privateTextView(message, host.texts[message.messageId])
    val showingOriginal = view.phase == PrivateTextPhase.SHOWN
    Column {
        if (view.text.isNotBlank()) {
            MarkdownContent(
                text = view.text,
                fontSizeMultiplier = fontSizeMultiplier,
                useKatex = useKatex,
                searchQuery = searchQuery.takeUnless { showingOriginal },
                searchFocusedOccurrence = if (showingOriginal) -1 else searchFocusedOccurrence,
                onFocusedOccurrencePosition = onFocusedOccurrencePosition.takeUnless { showingOriginal },
            )
        }
        DisableSelection {
            PrivateTextStatus(phase = view.phase, onRetry = host.onRetry)
        }
    }
}

/** "Private details hidden from the model", with what the original is doing, end-aligned under the turn. */
@Composable
internal fun PrivateTextStatus(phase: PrivateTextPhase?, onRetry: () -> Unit) {
    val hidden = stringResource(Res.string.private_text_hidden)
    val status = when (phase) {
        PrivateTextPhase.LOADING -> "$hidden · ${stringResource(Res.string.private_text_loading)}"
        PrivateTextPhase.UNAVAILABLE -> "$hidden · ${stringResource(Res.string.private_text_unavailable)}"
        PrivateTextPhase.SHOWN, null -> hidden
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.End) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (phase == PrivateTextPhase.UNAVAILABLE) {
            AdaptiveTextButton(onClick = onRetry) {
                Text(stringResource(Res.string.private_text_retry))
            }
        }
    }
}
