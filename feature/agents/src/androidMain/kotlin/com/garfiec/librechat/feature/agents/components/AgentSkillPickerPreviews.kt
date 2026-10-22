package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The agent editor's skill picker sheet: a catalog with two picks (one long title that ellipsizes,
// one skill with no description), a search with no matches, and an empty catalog. The Glass sheet
// itself is also shown portalled into a 400dp screen, the room an iPhone SE leaves with the
// keyboard up.

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetPreview() = ContentHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetGlassPreview() = ContentHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetNoMatchesPreview() = ContentHost(UiStyle.MATERIAL, query = "zebra")

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetNoMatchesGlassPreview() = ContentHost(UiStyle.LIQUID_GLASS, query = "zebra")

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetEmptyPreview() = ContentHost(UiStyle.MATERIAL, skills = emptyList())

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetEmptyGlassPreview() = ContentHost(UiStyle.LIQUID_GLASS, skills = emptyList())

@PreviewNarrowScreens
@Composable
private fun SkillPickerSheetGlassKeyboardPreview() = DialogPreviewHost(UiStyle.LIQUID_GLASS, height = 400.dp) {
    SkillPickerSheet(
        availableSkills = PreviewSkills,
        selectedSkillIds = PreviewSelection,
        onToggle = {},
        onDismiss = {},
    )
}

@Composable
private fun ContentHost(
    style: UiStyle,
    skills: List<SkillSummary> = PreviewSkills,
    query: String = "",
) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(top = 16.dp),
        ) {
            SkillPickerSheetContent(
                availableSkills = skills,
                selectedSkillIds = PreviewSelection,
                query = query,
                onQueryChange = {},
                onToggle = {},
                onDone = {},
            )
        }
    }
}

private val PreviewSkills = listOf(
    SkillSummary(
        id = "65f0c0ffee0000000000a001",
        name = "spreadsheet-helper",
        displayTitle = "Spreadsheet helper",
        description = "Reads, edits and summarises workbooks, keeping formulas and formatting intact",
    ),
    SkillSummary(
        id = "65f0c0ffee0000000000a002",
        name = "quarterly-report-writer",
        displayTitle = "Quarterly financial report writer for the regional sales teams",
        description = "Drafts the quarterly report from the sales export",
    ),
    SkillSummary(id = "65f0c0ffee0000000000a003", name = "commit-messages"),
    SkillSummary(
        id = "65f0c0ffee0000000000a004",
        name = "meeting-notes",
        displayTitle = "Meeting notes",
        description = "Turns a transcript into decisions, owners and dates",
    ),
    SkillSummary(
        id = "65f0c0ffee0000000000a005",
        name = "translate-de",
        displayTitle = "Übersetzer Deutsch",
        description = "Übersetzt Texte ins Deutsche und behält die Formatierung bei",
    ),
)

private val PreviewSelection = setOf("65f0c0ffee0000000000a002", "65f0c0ffee0000000000a003")
