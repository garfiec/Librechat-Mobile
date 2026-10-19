package com.garfiec.librechat.feature.agents.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.resources.tool_auth_code_description
import com.garfiec.librechat.feature.agents.resources.tool_auth_code_field
import com.garfiec.librechat.feature.agents.resources.tool_auth_code_title
import org.jetbrains.compose.resources.stringResource

@PreviewNarrowScreens
@Composable
private fun CodeToolAuthGlassKeyboardPreview() = CodeToolAuthHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun GrantAccessGlassKeyboardPreview() = GrantAccessHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun SkillSelectGlassKeyboardPreview() = SkillSelectHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@Composable
private fun CodeToolAuthHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    ToolAuthDialog(
        title = stringResource(Res.string.tool_auth_code_title),
        fieldLabel = stringResource(Res.string.tool_auth_code_field),
        description = stringResource(Res.string.tool_auth_code_description),
        onSubmit = {},
        onDismiss = {},
    )
}

/** A search with three matches, the most a phone showed before the rest fell off the panel. */
@Composable
private fun GrantAccessHost(style: UiStyle, height: Dp = 640.dp, results: Int = 3) = DialogPreviewHost(style, height = height) {
    GrantAccessDialog(
        searchQuery = "sam",
        results = List(results) { Principal(type = "user", id = "u$it", name = PrincipalNames[it % PrincipalNames.size]) },
        isSearching = false,
        roles = listOf(AccessRole(accessRoleId = "agent_viewer", name = "Viewer")),
        onQueryChange = {},
        onGrant = { _, _ -> },
        onDismiss = {},
    )
}

@Composable
private fun SkillSelectHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    SkillSelectDialog(
        availableSkills = List(6) {
            SkillSummary(id = "s$it", name = "skill-$it", displayTitle = "Spreadsheet helper $it", description = "Edits workbooks")
        },
        selectedSkillIds = setOf("s1"),
        onToggle = {},
        onDismiss = {},
    )
}

private val PrincipalNames = listOf("Sam Carter", "Samira Haddad", "Samuel Okafor", "Samantha Reyes", "Sami Laine")
