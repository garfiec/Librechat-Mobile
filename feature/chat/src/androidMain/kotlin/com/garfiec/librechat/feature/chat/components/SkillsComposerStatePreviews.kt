package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.model.PickerMcpServer

// Hosts are shared with SkillsChatPreviews.kt.

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

@PreviewNarrowScreens
@Composable
private fun SkillPickerSkillScopeMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("/skill ") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerSkillScopeGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { PickerHost("/skill ") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerPromptScopeMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("/prompt sum") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerSkillsUnavailableMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("/skill ", skills = emptyList(), canPickSkills = false)
}

/** Ten long names: the server's cap. */
@PreviewNarrowScreens
@Composable
private fun PickerMcpScopeMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("/mcp ", mcp = previewMcpServers)
}

@PreviewNarrowScreens
@Composable
private fun PickerMcpScopeGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) {
    PickerHost("/mcp ", mcp = previewMcpServers)
}

@PreviewNarrowScreens
@Composable
private fun PickerMixedWithMcpMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("/", mcp = previewMcpServers)
}

@PreviewNarrowScreens
@Composable
private fun PickerMcpUnavailableMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("/mcp ") }

internal val previewMcpServers = listOf(
    PickerMcpServer(
        McpServerDisplayData("github", "GitHub", "Repos, issues and pull requests", McpDisplayStatus.CONNECTED),
        on = true,
        locked = false,
    ),
    PickerMcpServer(
        McpServerDisplayData("linear", "Linear", "Issues and projects", McpDisplayStatus.NEEDS_SIGN_IN, requiresOAuth = true),
        on = false,
        locked = false,
    ),
    PickerMcpServer(
        McpServerDisplayData("search", "Company search", null, McpDisplayStatus.CONNECTED),
        on = true,
        locked = true,
    ),
    PickerMcpServer(
        McpServerDisplayData("files", "Files", "Read the shared drive", McpDisplayStatus.DISCONNECTED),
        on = false,
        locked = false,
    ),
)

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
        StreamingMessageBubble(streamingContent = "", senderName = "Claude", avatar = null, pendingSkills = picks)
        StreamingMessageBubble(
            streamingContent = "Reading the PDF",
            senderName = "Claude",
            avatar = null,
            showBubbles = true,
            pendingSkills = picks,
        )
        StreamingMessageBubble(
            streamingContent = "",
            senderName = "Claude",
            avatar = null,
            showAvatars = false,
            pendingSkills = picks.take(1),
        )
        StreamingMessageBubble(
            streamingContent = "",
            senderName = "Claude",
            avatar = null,
            chatLayoutStyle = ChatLayoutConstants.TWO_SIDED,
            pendingSkills = picks.take(1),
        )
    }
}
