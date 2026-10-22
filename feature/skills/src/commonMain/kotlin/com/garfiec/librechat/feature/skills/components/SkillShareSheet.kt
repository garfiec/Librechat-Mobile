package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.skills.isAuthor
import com.garfiec.librechat.feature.skills.principalKey
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_acl_add
import com.garfiec.librechat.feature.skills.resources.skill_acl_dismiss
import com.garfiec.librechat.feature.skills.resources.skill_acl_grant_dialog_title
import com.garfiec.librechat.feature.skills.resources.skill_acl_load_failed
import com.garfiec.librechat.feature.skills.resources.skill_acl_no_grants
import com.garfiec.librechat.feature.skills.resources.skill_acl_people
import com.garfiec.librechat.feature.skills.resources.skill_acl_principal_group
import com.garfiec.librechat.feature.skills.resources.skill_acl_principal_role
import com.garfiec.librechat.feature.skills.resources.skill_acl_principal_unknown
import com.garfiec.librechat.feature.skills.resources.skill_acl_principal_user
import com.garfiec.librechat.feature.skills.resources.skill_acl_public_description
import com.garfiec.librechat.feature.skills.resources.skill_acl_public_label
import com.garfiec.librechat.feature.skills.resources.skill_acl_revoke
import com.garfiec.librechat.feature.skills.resources.skill_acl_role
import com.garfiec.librechat.feature.skills.resources.skill_acl_role_for
import com.garfiec.librechat.feature.skills.resources.skill_acl_roles_failed
import com.garfiec.librechat.feature.skills.resources.skill_acl_search_all_shared
import com.garfiec.librechat.feature.skills.resources.skill_acl_search_empty
import com.garfiec.librechat.feature.skills.resources.skill_acl_search_failed
import com.garfiec.librechat.feature.skills.resources.skill_acl_search_hint
import com.garfiec.librechat.feature.skills.resources.skill_acl_search_unavailable
import com.garfiec.librechat.feature.skills.resources.skill_acl_update_failed
import com.garfiec.librechat.feature.skills.resources.skills_owner_you
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.skillRoleLabel
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclUiState
import com.garfiec.librechat.feature.skills.viewmodel.newSearchResults
import org.jetbrains.compose.resources.stringResource

/** What the sharing sheet can ask for. */
internal class SkillShareActions(
    val onDismiss: () -> Unit,
    val onSetPublic: (Boolean) -> Unit,
    val onPublicRoleChange: (String) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onRetrySearch: () -> Unit,
    val onAdd: (Principal) -> Unit,
    val onChangeRole: (Principal, String) -> Unit,
    val onRevoke: (Principal) -> Unit,
    val onRetryLoad: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Everything about who can use a skill, in one sheet: the public switch (SKILLS.SHARE_PUBLIC
 * only), a people search whose results add at the viewer role, and the grants, each with a role
 * menu whose last item removes access. Failures show inside the sheet, where the user is looking.
 *
 * [authorId]'s row comes first and can't be changed. It reads "You" only when [viewerIsAuthor]:
 * an admin, or someone given SHARE, can open this on a skill someone else wrote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillShareSheet(
    state: SkillAclUiState,
    authorId: String?,
    viewerIsAuthor: Boolean,
    actions: SkillShareActions,
) {
    AdaptiveModalBottomSheet(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { LowProfileDragHandle() },
    ) {
        SkillShareSheetContent(state = state, authorId = authorId, viewerIsAuthor = viewerIsAuthor, actions = actions)
    }
}

@Composable
internal fun SkillShareSheetContent(
    state: SkillAclUiState,
    authorId: String?,
    viewerIsAuthor: Boolean,
    actions: SkillShareActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.skill_acl_grant_dialog_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        ShareSheetErrors(state, actions)
        if (state.canSharePublic) {
            PublicAccessRow(state, actions)
            AdaptiveDivider()
        }
        Text(
            text = stringResource(Res.string.skill_acl_people),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (state.canSearchPeople) {
            PeopleSearch(state, actions)
        } else {
            // The server refuses every search without the people picker; say why there's no field.
            Text(
                text = stringResource(Res.string.skill_acl_search_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GrantsList(state, authorId, viewerIsAuthor, actions)
    }
}

@Composable
private fun ShareSheetErrors(state: SkillAclUiState, actions: SkillShareActions) {
    if (!state.loadFailed && !state.rolesFailed && !state.updateFailed) return
    val retry = stringResource(Res.string.skills_retry)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.loadFailed -> SheetErrorRow(stringResource(Res.string.skill_acl_load_failed), retry, actions.onRetryLoad)
            state.rolesFailed -> SheetErrorRow(stringResource(Res.string.skill_acl_roles_failed), retry, actions.onRetryLoad)
        }
        if (state.updateFailed) {
            SheetErrorRow(
                message = state.errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.skill_acl_update_failed),
                actionLabel = stringResource(Res.string.skill_acl_dismiss),
                onAction = actions.onDismissError,
            )
        }
    }
}

/** A failure inside the sheet, with what the user can do about it. */
@Composable
private fun SheetErrorRow(message: String, actionLabel: String, onAction: () -> Unit) {
    // The action sits under the message, as ErrorBanner's do: beside it, a long translated label
    // ("Erneut versuchen" at 2x) leaves the message a sliver to wrap in.
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
    }
}

@Composable
private fun PublicAccessRow(state: SkillAclUiState, actions: SkillShareActions) {
    val enabled = state.loaded && !state.isUpdating && (state.isPublic || state.defaultRoleId != null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // One toggleable node, so the switch is announced with its label and the label toggles it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = state.isPublic,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = actions.onSetPublic,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(Res.string.skill_acl_public_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.skill_acl_public_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AdaptiveSwitch(checked = state.isPublic, onCheckedChange = null, enabled = enabled)
        }
        val publicRole = state.publicAccessRoleId
        if (state.isPublic && publicRole != null) PublicRoleRow(state, publicRole, actions)
    }
}

/** Public access's role: beside its label while the row has room, under it (uncapped) where a grant row stacks. */
@Composable
private fun PublicRoleRow(state: SkillAclUiState, publicRole: String, actions: SkillShareActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < GrantRowSideBySideMinWidth * LocalDensity.current.fontScale
        val roleLabel = @Composable {
            Text(
                text = stringResource(Res.string.skill_acl_role),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val roleButton = @Composable { modifier: Modifier ->
            RoleMenuButton(
                currentRoleId = publicRole,
                roles = state.availableRoles,
                enabled = state.canGrant && !state.isUpdating,
                label = stringResource(Res.string.skill_acl_role_for, stringResource(Res.string.skill_acl_public_label)),
                onSelect = actions.onPublicRoleChange,
                onRemove = null,
                modifier = modifier,
                maxWidth = if (stacked) Dp.Unspecified else RoleButtonMaxWidth,
            )
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                roleLabel()
                roleButton(Modifier.align(Alignment.End))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { roleLabel() }
                roleButton(Modifier)
            }
        }
    }
}

@Composable
private fun PeopleSearch(state: SkillAclUiState, actions: SkillShareActions) {
    val enabled = state.canGrant && state.loaded && !state.isUpdating
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveOutlinedTextField(
            value = state.searchQuery,
            onValueChange = actions.onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(stringResource(Res.string.skill_acl_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        // A query too short to search shows nothing yet, not "No matches".
        if (state.isQuerySearchable) SearchResults(state, enabled, actions)
    }
}

@Composable
private fun SearchResults(state: SkillAclUiState, enabled: Boolean, actions: SkillShareActions) {
    val results = state.newSearchResults()
    when {
        state.isSearching -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        state.searchFailed -> SheetErrorRow(
            message = stringResource(Res.string.skill_acl_search_failed),
            actionLabel = stringResource(Res.string.skills_retry),
            onAction = actions.onRetrySearch,
        )
        results.isEmpty() -> Text(
            // Found, but every one of them is already in the list below.
            text = stringResource(
                if (state.allResultsAlreadyShared) Res.string.skill_acl_search_all_shared else Res.string.skill_acl_search_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column {
            results.forEach { principal ->
                SearchResultRow(principal = principal, enabled = enabled, onAdd = { actions.onAdd(principal) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(principal: Principal, enabled: Boolean, onAdd: () -> Unit) {
    val addLabel = stringResource(Res.string.skill_acl_add)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClickLabel = addLabel, role = Role.Button, onClick = onAdd)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrincipalTile(principal)
        PrincipalText(principal, subtitle = principalSubtitle(principal), modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GrantsList(state: SkillAclUiState, authorId: String?, viewerIsAuthor: Boolean, actions: SkillShareActions) {
    when {
        !state.loaded && state.isLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            AdaptiveCircularProgressIndicator()
        }
        !state.loaded -> Unit
        else -> Column(Modifier.padding(bottom = 16.dp)) {
            // The author first: their owner entry is what keeps the skill theirs.
            state.principals.sortedBy { !it.isAuthor(authorId) }.forEach { principal ->
                val isAuthor = principal.isAuthor(authorId)
                GrantRow(
                    principal = principal,
                    isAuthor = isAuthor,
                    isViewer = isAuthor && viewerIsAuthor,
                    roles = state.availableRoles,
                    // Removing needs no roles, so a failed roles fetch leaves "Remove access" reachable.
                    enabled = state.canRevoke,
                    canChangeRole = state.canGrant,
                    onChangeRole = { roleId -> actions.onChangeRole(principal, roleId) },
                    onRevoke = { actions.onRevoke(principal) },
                )
            }
            if (state.principals.none { !it.isAuthor(authorId) }) {
                Text(
                    text = stringResource(Res.string.skill_acl_no_grants),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GrantRow(
    principal: Principal,
    isAuthor: Boolean,
    isViewer: Boolean,
    roles: List<AccessRole>,
    enabled: Boolean,
    canChangeRole: Boolean,
    onChangeRole: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    val subtitle = if (isViewer) stringResource(Res.string.skills_owner_you) else principalSubtitle(principal)
    val role = GrantRole(
        principal = principal,
        isAuthor = isAuthor,
        roles = roles,
        enabled = enabled,
        canChangeRole = canChangeRole,
        onChangeRole = onChangeRole,
        onRevoke = onRevoke,
    )
    // Beside the name while the row has room; under it on a narrow sheet or at a large font, where
    // a capped button beside the name would leave the name a letter or two and clip the role.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < GrantRowSideBySideMinWidth * LocalDensity.current.fontScale
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrincipalTile(principal)
            Column(Modifier.weight(1f)) {
                PrincipalText(principal, subtitle = subtitle)
                if (stacked) GrantRoleControl(role, stacked = true, modifier = Modifier.align(Alignment.End))
            }
            if (!stacked) GrantRoleControl(role, stacked = false)
        }
    }
}

/** What a grant row's role control needs. */
private class GrantRole(
    val principal: Principal,
    val isAuthor: Boolean,
    val roles: List<AccessRole>,
    val enabled: Boolean,
    /** The roles are known, so the menu offers them; without them it offers only "Remove access". */
    val canChangeRole: Boolean,
    val onChangeRole: (String) -> Unit,
    val onRevoke: () -> Unit,
)

/** The grant's role: a menu, or plain text on the author's row. Uncapped when [stacked] under the name. */
@Composable
private fun GrantRoleControl(role: GrantRole, stacked: Boolean, modifier: Modifier = Modifier) {
    val roleId = role.principal.accessRoleId ?: return
    if (role.isAuthor) {
        // The author's own grant isn't offered for change: losing it would lock them out.
        Text(
            text = skillRoleLabel(roleId, role.roles),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.padding(horizontal = 12.dp, vertical = if (stacked) 4.dp else 0.dp),
        )
    } else {
        RoleMenuButton(
            currentRoleId = roleId,
            roles = role.roles,
            enabled = role.enabled,
            rolesSelectable = role.canChangeRole,
            label = stringResource(Res.string.skill_acl_role_for, principalName(role.principal)),
            onSelect = role.onChangeRole,
            onRemove = role.onRevoke,
            modifier = modifier,
            maxWidth = if (stacked) Dp.Unspecified else RoleButtonMaxWidth,
        )
    }
}

/**
 * The role a grant (or public access) has, as a button that opens the roles by name; with
 * [onRemove], "Remove access" closes the menu.
 */
@Composable
private fun RoleMenuButton(
    currentRoleId: String,
    roles: List<AccessRole>,
    enabled: Boolean,
    label: String,
    onSelect: (String) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    maxWidth: Dp = RoleButtonMaxWidth,
    rolesSelectable: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = skillRoleLabel(currentRoleId, roles)
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            // Announced as "Role for Ada, Editor": the label names the control, the state its role.
            modifier = Modifier.widthIn(max = maxWidth).semantics {
                contentDescription = label
                stateDescription = currentLabel
            },
        ) {
            Text(
                text = currentLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.takeIf { rolesSelectable }.orEmpty().forEach { role ->
                val selected = role.accessRoleId == currentRoleId
                DropdownMenuItem(
                    text = { Text(skillRoleLabel(role.accessRoleId, roles)) },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (!selected) onSelect(role.accessRoleId)
                    },
                )
            }
            if (onRemove != null) {
                if (rolesSelectable && roles.isNotEmpty()) AdaptiveDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.skill_acl_revoke), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun PrincipalTile(principal: Principal) {
    InitialTile(
        seed = principal.principalKey(),
        label = principalName(principal),
        imageUrl = principal.avatar?.takeIf { it.startsWith("http") },
    )
}

@Composable
private fun PrincipalText(principal: Principal, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = principalName(principal),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The principal's name, else its email, else its id; a principal with none of them reads "Unknown". */
@Composable
private fun principalName(principal: Principal): String =
    listOf(principal.name, principal.email, principal.id).firstOrNull { !it.isNullOrBlank() }
        ?: stringResource(Res.string.skill_acl_principal_unknown)

/** "ada@example.com" for a person when known, else what kind of principal it is. */
@Composable
private fun principalSubtitle(principal: Principal): String {
    val email = principal.email?.takeIf { it.isNotBlank() }
    if (principal.type == PrincipalType.USER && email != null) return email
    return stringResource(
        when (principal.type) {
            PrincipalType.GROUP -> Res.string.skill_acl_principal_group
            PrincipalType.ROLE -> Res.string.skill_acl_principal_role
            else -> Res.string.skill_acl_principal_user
        },
    )
}

private val RoleButtonMaxWidth = 160.dp

/**
 * The narrowest grant row, at 1x font, that keeps the role beside the name; it scales with the
 * font. Material's sheet is 272dp wide on a 320dp phone, so that phone stacks from about 1.1x.
 */
private val GrantRowSideBySideMinWidth = 240.dp
