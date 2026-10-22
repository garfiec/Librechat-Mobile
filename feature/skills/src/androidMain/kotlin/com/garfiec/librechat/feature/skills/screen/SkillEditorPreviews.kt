package com.garfiec.librechat.feature.skills.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorUiState

// The editor filled in for an edit (category label, not value, in the picker), the same form with
// a reserved name and an over-long display title, and a save that hit a version conflict.

private val categories = listOf(
    Category(label = "com_ui_teach_or_explain", value = "teach_or_explain"),
    Category(label = "com_ui_code", value = "code"),
)

private val filledState = SkillEditorUiState(
    isEditMode = true,
    skillId = "sk-1",
    name = "quarterly-report-table-extraction",
    displayTitle = "Quarterly report table extraction for regional finance teams",
    description = "Use when the user shares a quarterly PDF report and wants its tables as CSV.",
    body = "# Extract tables\n\n1. Find every table in the report.\n2. Normalize the headers.",
    category = "teach_or_explain",
    availableCategories = categories,
)

private val invalidState = filledState.copy(
    name = "claude-report-tables",
    displayTitle = "Quarterly report table extraction for regional finance teams, ".repeat(3),
)

private val conflictState = filledState.copy(showConflictNotice = true)

@PreviewNarrowScreens
@Composable
private fun SkillEditorFilledPreview() = EditorHost(UiStyle.MATERIAL, filledState)

@PreviewNarrowScreens
@Composable
private fun SkillEditorFilledGlassPreview() = EditorHost(UiStyle.LIQUID_GLASS, filledState)

@PreviewNarrowScreens
@Composable
private fun SkillEditorFieldErrorsPreview() = EditorHost(UiStyle.MATERIAL, invalidState)

@PreviewNarrowScreens
@Composable
private fun SkillEditorFieldErrorsGlassPreview() = EditorHost(UiStyle.LIQUID_GLASS, invalidState)

@PreviewNarrowScreens
@Composable
private fun SkillEditorConflictPreview() = EditorHost(UiStyle.MATERIAL, conflictState)

@PreviewNarrowScreens
@Composable
private fun SkillEditorConflictGlassPreview() = EditorHost(UiStyle.LIQUID_GLASS, conflictState)

@Composable
private fun EditorHost(style: UiStyle, state: SkillEditorUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        SkillEditorScreenContent(
            state = state,
            actions = SkillEditorActions(
                onBack = {},
                onSave = {},
                onNameChange = {},
                onDisplayTitleChange = {},
                onDescriptionChange = {},
                onBodyChange = {},
                onCategoryChange = {},
                onAlwaysApplyChange = {},
                onDismissConflict = {},
                onRetryLoad = {},
            ),
        )
    }
}
