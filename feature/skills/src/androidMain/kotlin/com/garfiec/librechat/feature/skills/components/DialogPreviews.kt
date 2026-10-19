package com.garfiec.librechat.feature.skills.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileEditorState

@PreviewNarrowScreens
@Composable
private fun FileEditorConflictGlassKeyboardPreview() = FileEditorConflictHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun GrantAccessGlassKeyboardPreview() = GrantAccessHost(UiStyle.LIQUID_GLASS, height = 400.dp)

/** A file longer than the field, replaced on the server while it was open. */
@Composable
private fun FileEditorConflictHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    SkillFileEditorDialog(
        editor = SkillFileEditorState(
            relativePath = "scripts/summarize.py",
            isLoading = false,
            content = List(20) { "line_$it = transform(rows[$it])" }.joinToString("\n"),
            fileId = "f1",
            conflict = true,
        ),
        editable = false,
        onSave = {},
        onReload = {},
        onDismiss = {},
    )
}

@Composable
private fun GrantAccessHost(style: UiStyle, height: Dp = 640.dp, results: Int = 3) = DialogPreviewHost(style, height = height) {
    GrantAccessDialog(
        searchQuery = "sam",
        results = List(results) { Principal(type = "user", id = "u$it", name = PrincipalNames[it % PrincipalNames.size]) },
        isSearching = false,
        roles = listOf(AccessRole(accessRoleId = "skill_viewer", name = "Viewer")),
        onQueryChange = {},
        onGrant = { _, _ -> },
        onDismiss = {},
    )
}

private val PrincipalNames = listOf("Sam Carter", "Samira Haddad", "Samuel Okafor", "Samantha Reyes", "Sami Laine")
