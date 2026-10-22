package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

// The chat side of skills under stress: the picker when the catalog failed to load, the chip
// section at the server's cap, and the streaming reply's "Running X" rows in each layout. Hosts
// are shared with SkillsChatPreviews.kt.

@PreviewNarrowScreens
@Composable
private fun SkillPickerFailedMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("\$pd", skills = emptyList(), failed = true)
}

@PreviewNarrowScreens
@Composable
private fun SkillPickerFailedGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) {
    PickerHost("\$pd", skills = emptyList(), failed = true)
}

/** Ten long names (the server's cap): the chips scroll inside a bounded section. */
@PreviewNarrowScreens
@Composable
private fun SkillChipsAtCapMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("Summarize the attached report", chips = tenLongChips)
}

@PreviewNarrowScreens
@Composable
private fun SkillChipsAtCapGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) {
    PickerHost("Summarize the attached report", chips = tenLongChips)
}

private val tenLongChips = (1..10).map { "quarterly-report-formatting-house-style-$it" }

/**
 * The chips over scrolled message text, as they sit in the chat: the composer overlays the list
 * and is transparent where the chips draw, so each chip needs its own fill to stay legible.
 */
@PreviewNarrowScreens
@Composable
private fun SkillChipsOverTextMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { ChipsOverTextHost() }

@PreviewNarrowScreens
@Composable
private fun SkillChipsOverTextGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { ChipsOverTextHost() }

@Composable
private fun ChipsOverTextHost() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = List(4) { "An older reply scrolled up behind the composer, long enough to wrap." }
                .joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PendingSkillChipsSection(
            skills = listOf("pdf-tools", "pdf-redaction-and-sanitizing-for-legal-review"),
            onRemove = {},
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The streaming reply with its "Running X" rows, in each layout: they sit under the sender row. */
@PreviewNarrowScreens
@Composable
private fun SkillRunningInReplyMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { StreamingHost() }

@PreviewNarrowScreens
@Composable
private fun SkillRunningInReplyGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { StreamingHost() }

@Composable
private fun StreamingHost() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val picks = listOf("pdf-tools", "pdf-redaction-and-sanitizing-for-legal-review")
        // Thread with avatars, Thread with bubbles, Thread without avatars, two-sided.
        StreamingMessageBubble(streamingContent = "", senderName = "Claude", senderIconUrl = null, pendingSkills = picks)
        StreamingMessageBubble(
            streamingContent = "Reading the PDF",
            senderName = "Claude",
            senderIconUrl = null,
            showBubbles = true,
            pendingSkills = picks,
        )
        StreamingMessageBubble(
            streamingContent = "",
            senderName = "Claude",
            senderIconUrl = null,
            showAvatars = false,
            pendingSkills = picks.take(1),
        )
        StreamingMessageBubble(
            streamingContent = "",
            senderName = "Claude",
            senderIconUrl = null,
            chatLayoutStyle = ChatLayoutConstants.TWO_SIDED,
            pendingSkills = picks.take(1),
        )
    }
}
