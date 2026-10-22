package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.skills.components.SkillCard
import com.garfiec.librechat.feature.skills.components.SkillCardPlaceholder
import com.garfiec.librechat.feature.skills.viewmodel.SkillRowUi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// The list's cards and states at the widths and translations that squeeze them: a long name that
// ellipsizes with every badge and no description, a shared skill with a translated category, and
// one whose active state hasn't loaded (no Off badge yet).

@PreviewNarrowScreens
@Composable
private fun SkillCardsPreview() = CardsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SkillCardsGlassPreview() = CardsHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SkillCardPlaceholderPreview() = PreviewHost(UiStyle.MATERIAL) { repeat(PLACEHOLDERS) { SkillCardPlaceholder() } }

@PreviewNarrowScreens
@Composable
private fun SkillCardPlaceholderGlassPreview() =
    PreviewHost(UiStyle.LIQUID_GLASS) { repeat(PLACEHOLDERS) { SkillCardPlaceholder() } }

@PreviewNarrowScreens
@Composable
private fun SkillsEmptyPreview() = PreviewHost(UiStyle.MATERIAL) { SkillsEmptyState(canCreate = true, onCreate = {}, onImport = {}) }

@PreviewNarrowScreens
@Composable
private fun SkillsEmptyGlassPreview() =
    PreviewHost(UiStyle.LIQUID_GLASS) { SkillsEmptyState(canCreate = true, onCreate = {}, onImport = {}) }

@PreviewNarrowScreens
@Composable
private fun SkillsEmptyNoCreatePreview() =
    PreviewHost(UiStyle.MATERIAL) { SkillsEmptyState(canCreate = false, onCreate = {}, onImport = {}) }

@PreviewNarrowScreens
@Composable
private fun SkillsEmptyNoCreateGlassPreview() =
    PreviewHost(UiStyle.LIQUID_GLASS) { SkillsEmptyState(canCreate = false, onCreate = {}, onImport = {}) }

@PreviewNarrowScreens
@Composable
private fun SkillsNoResultsPreview() = PreviewHost(UiStyle.MATERIAL) { SkillsNoResults(query = "quarterly reconciliation") }

@PreviewNarrowScreens
@Composable
private fun SkillsNoResultsGlassPreview() =
    PreviewHost(UiStyle.LIQUID_GLASS) { SkillsNoResults(query = "quarterly reconciliation") }

private const val PLACEHOLDERS = 3

@Composable
private fun CardsHost(style: UiStyle) {
    val now = Clock.System.now()
    val categories = listOf(Category(label = "com_ui_teach_or_explain", value = "teach_or_explain"))
    val rows = listOf(
        SkillRowUi(
            summary = SkillSummary(
                id = "65f0c0ffee0000000000a001",
                name = "quarterly-financial-statement-reconciliation-helper",
                displayTitle = "Quarterly financial statement reconciliation helper for every region",
                author = "65f0c0ffee0000000000abcd",
                fileCount = 12,
                isPublic = true,
                alwaysApply = true,
                category = "finance",
                updatedAt = (now - 3.hours).toString(),
            ),
            isActive = false,
            isOwnedByViewer = true,
        ),
        SkillRowUi(
            summary = SkillSummary(
                id = "65f0c0ffee0000000000a002",
                name = "explain-like-im-five",
                description = "Explains a concept in plain words with one everyday analogy, then checks " +
                    "understanding with a short question before going deeper",
                author = "65f0c0ffee0000000000beef",
                authorName = "Grace Hopper",
                fileCount = 1,
                category = "teach_or_explain",
                updatedAt = (now - 40.days).toString(),
            ),
            isActive = true,
            isOwnedByViewer = false,
        ),
        SkillRowUi(
            summary = SkillSummary(id = "65f0c0ffee0000000000a003", name = "pdf-tables", category = "legal"),
            isActive = null,
            isOwnedByViewer = false,
        ),
    )
    PreviewHost(style) {
        rows.forEach { SkillCard(row = it, categories = categories, onClick = {}) }
    }
}

@Composable
private fun PreviewHost(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        ProvideDateTimeFormat(DateTimeFormatPrefs()) {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}
