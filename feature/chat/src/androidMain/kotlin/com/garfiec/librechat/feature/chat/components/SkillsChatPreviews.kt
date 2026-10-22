package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData

// The chat side of skills: the combined `/` + `$` picker with its typed rows, the picked-skill
// chips above the composer, the pills on a sent user message, and the skill call card's states.

@PreviewNarrowScreens
@Composable
private fun SkillPickerMixedMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("\$pd") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerMixedGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { PickerHost("\$pd") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerSlashMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("/") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerNoMatchesMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("\$zzzz") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerNoMatchesGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { PickerHost("\$zzzz") }

@PreviewNarrowScreens
@Composable
private fun SkillPickerLoadingMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("$", skills = emptyList(), loading = true)
}

@PreviewNarrowScreens
@Composable
private fun SkillChipsMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PickerHost("Summarize the attached report") }

@PreviewNarrowScreens
@Composable
private fun SkillChipsGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { PickerHost("Summarize the attached report") }

@PreviewNarrowScreens
@Composable
private fun SkillPillsMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { PillsHost() }

@PreviewNarrowScreens
@Composable
private fun SkillPillsGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { PillsHost() }

@PreviewNarrowScreens
@Composable
private fun SkillCallCardsMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) { CardsHost() }

@PreviewNarrowScreens
@Composable
private fun SkillCallCardsGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) { CardsHost() }

@Composable
internal fun SkillsSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

private val previewPrompts = listOf(
    PromptMentionDisplayData(
        id = "p1",
        name = "PDF summary for the quarterly board pack and the investor appendix",
        command = null,
        oneliner = "Five bullets, then the risks",
        category = "writing",
        promptText = null,
    ),
)

private val previewSkills = listOf(
    PickerSkill(name = "pdf-tools", title = "PDF tools", description = "Extract tables and text from PDFs"),
    PickerSkill(
        name = "pdf-redaction-and-sanitizing-for-legal-review",
        title = "PDF redaction and sanitizing for legal review",
        description = null,
    ),
)

/** The composer with the picker open on [text] (or closed, showing the picked-skill chips). */
@Composable
internal fun PickerHost(
    text: String,
    skills: List<PickerSkill> = previewSkills,
    loading: Boolean = false,
    failed: Boolean = false,
    chips: List<String> = listOf("brand-voice", "quarterly-report-formatting-house-style"),
) {
    ChatInput(
        inputText = text,
        isStreaming = false,
        onInputChange = {},
        onSend = {},
        onStop = {},
        onOpenTools = {},
        pendingManualSkills = chips,
        promptSuggestions = previewPrompts,
        skillSuggestions = skills,
        isLoadingSkills = loading,
        skillsLoadFailed = failed,
        canPickSkills = true,
    )
}

@Composable
private fun PillsHost() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        SkillPills(
            alwaysApplied = listOf("house-style"),
            manual = listOf("pdf-tools", "pdf-redaction-and-sanitizing-for-legal-review", "brand-voice"),
        )
    }
}

@Composable
private fun CardsHost() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PendingSkillCallRow(skillName = "pdf-tools")
        SkillCallCard(
            skillName = "pdf-redaction-and-sanitizing-for-legal-review",
            verdict = ToolCallVerdict.COMPLETED,
            durationMs = 1_240,
            output = "Skill \"pdf-redaction-and-sanitizing-for-legal-review\" loaded. Follow the instructions below.",
        )
        SkillCallCard(skillName = "brand-voice", verdict = ToolCallVerdict.CANCELLED, durationMs = 300)
        SkillCallCard(skillName = "pdf-tools", verdict = ToolCallVerdict.FAILED, output = "Skill not found")
        SkillCallCard(skillName = null, verdict = ToolCallVerdict.COMPLETED)
    }
}
