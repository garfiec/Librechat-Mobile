package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclUiState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

// The detail page's header with a long title that wraps, both badges and a translated category;
// and the sharing sheet's content with grants, with nothing shared yet, with every failure it
// reports in place (grants, roles, a change, a search), and without the people picker.

private const val AUTHOR = "65f0c0ffee0000000000abcd"

@PreviewNarrowScreens
@Composable
private fun SkillDetailHeaderPreview() = HeaderHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SkillDetailHeaderGlassPreview() = HeaderHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetGrantsPreview() = ShareHost(UiStyle.MATERIAL, grantsState)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetGrantsGlassPreview() = ShareHost(UiStyle.LIQUID_GLASS, grantsState)

// The tightest grant and public-access rows: a 320dp phone at 2x in German, where each role
// moves under its name or label.
@Preview(name = "320dp · 2.0x · de", widthDp = 320, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun SkillShareSheetGrantsLargeFontPreview() = ShareHost(UiStyle.MATERIAL, grantsState)

@Preview(name = "320dp · 2.0x · de", widthDp = 320, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun SkillShareSheetGrantsLargeFontGlassPreview() = ShareHost(UiStyle.LIQUID_GLASS, grantsState)

// An admin (or someone given SHARE) on a skill Ada wrote: her row shows her email, not "You".
@PreviewNarrowScreens
@Composable
private fun SkillShareSheetNotAuthorPreview() = ShareHost(UiStyle.MATERIAL, grantsState, viewerIsAuthor = false)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetNotAuthorGlassPreview() = ShareHost(UiStyle.LIQUID_GLASS, grantsState, viewerIsAuthor = false)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetEmptyPreview() = ShareHost(UiStyle.MATERIAL, emptyState)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetEmptyGlassPreview() = ShareHost(UiStyle.LIQUID_GLASS, emptyState)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetErrorPreview() = ShareHost(UiStyle.MATERIAL, errorState)

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetErrorGlassPreview() = ShareHost(UiStyle.LIQUID_GLASS, errorState)

// SKILLS.SHARE without the people picker (USER's upstream default): a note where the search was.
@PreviewNarrowScreens
@Composable
private fun SkillShareSheetNoPickerPreview() = ShareHost(UiStyle.MATERIAL, grantsState.copy(canSearchPeople = false))

@PreviewNarrowScreens
@Composable
private fun SkillShareSheetNoPickerGlassPreview() =
    ShareHost(UiStyle.LIQUID_GLASS, grantsState.copy(canSearchPeople = false))

@Composable
private fun HeaderHost(style: UiStyle) {
    val skill = Skill(
        id = "65f0c0ffee0000000000a001",
        name = "quarterly-financial-statement-reconciliation-helper",
        displayTitle = "Quarterly financial statement reconciliation helper for every region",
        author = "65f0c0ffee0000000000beef",
        authorName = "Grace Hopper",
        category = "teach_or_explain",
        alwaysApply = true,
        isPublic = true,
        updatedAt = (Clock.System.now() - 3.hours).toString(),
    )
    PreviewHost(style) {
        SkillDetailHeader(
            skill = skill,
            isOwnedByViewer = false,
            isPublic = true,
            categories = listOf(Category(label = "com_ui_teach_or_explain", value = "teach_or_explain")),
        )
    }
}

// The server's role names are web localization keys; the sheet translates them.
private val roles = listOf(
    AccessRole(accessRoleId = "skill_viewer", name = "com_ui_role_viewer", permissionBits = 1),
    AccessRole(accessRoleId = "skill_editor", name = "com_ui_role_editor", permissionBits = 3),
    AccessRole(accessRoleId = "skill_owner", name = "com_ui_role_owner", permissionBits = 15),
)

private val owner = Principal(
    type = "user",
    id = AUTHOR,
    name = "Ada Lovelace",
    email = "ada@example.com",
    accessRoleId = "skill_owner",
)

private val grantsState = SkillAclUiState(
    loaded = true,
    canShare = true,
    canSearchPeople = true,
    canSharePublic = true,
    isPublic = true,
    publicAccessRoleId = "skill_viewer",
    availableRoles = roles,
    principals = listOf(
        owner,
        Principal(
            type = "user",
            id = "65f0c0ffee0000000000b002",
            name = "Bartholomew Featherstonehaugh-Montgomery",
            email = "bartholomew.featherstonehaugh@example.com",
            accessRoleId = "skill_editor",
        ),
        Principal(type = "group", id = "65f0c0ffee0000000000b003", name = "Finance", accessRoleId = "skill_viewer"),
    ),
    searchQuery = "sam",
    searchResults = listOf(
        Principal(type = "user", id = "65f0c0ffee0000000000c001", name = "Sam Carter", email = "sam@example.com"),
        Principal(type = "group", id = "65f0c0ffee0000000000c002", name = "Sales Americas"),
    ),
)

private val emptyState = SkillAclUiState(
    loaded = true,
    canShare = true,
    canSearchPeople = true,
    canSharePublic = true,
    availableRoles = roles,
    principals = listOf(owner),
)

private val errorState = SkillAclUiState(
    loaded = true,
    canShare = true,
    canSearchPeople = true,
    rolesFailed = true,
    updateFailed = true,
    principals = listOf(owner),
    searchQuery = "sam",
    searchFailed = true,
)

private val noActions = SkillShareActions(
    onDismiss = {},
    onSetPublic = {},
    onPublicRoleChange = {},
    onQueryChange = {},
    onRetrySearch = {},
    onAdd = {},
    onChangeRole = { _, _ -> },
    onRevoke = {},
    onRetryLoad = {},
    onDismissError = {},
)

@Composable
private fun ShareHost(style: UiStyle, state: SkillAclUiState, viewerIsAuthor: Boolean = true) =
    PreviewHost(style, inset = false) {
        SkillShareSheetContent(state = state, authorId = AUTHOR, viewerIsAuthor = viewerIsAuthor, actions = noActions)
    }

@Composable
private fun PreviewHost(style: UiStyle, inset: Boolean = true, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        ProvideDateTimeFormat(DateTimeFormatPrefs()) {
            Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 16.dp)) {
                // The sheet's content insets itself.
                Box(Modifier.padding(horizontal = if (inset) 16.dp else 0.dp)) { content() }
            }
        }
    }
}
