package com.garfiec.librechat.feature.agents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.isSearchablePrincipalQuery
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.sharing.accessRoleLabel
import com.garfiec.librechat.core.ui.sharing.defaultAccessRoleId
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.resources.acl_add_principal
import com.garfiec.librechat.feature.agents.resources.acl_grant_dialog_title
import com.garfiec.librechat.feature.agents.resources.acl_load_failed
import com.garfiec.librechat.feature.agents.resources.acl_no_grants
import com.garfiec.librechat.feature.agents.resources.acl_principal_group
import com.garfiec.librechat.feature.agents.resources.acl_principal_role
import com.garfiec.librechat.feature.agents.resources.acl_principal_unknown
import com.garfiec.librechat.feature.agents.resources.acl_principal_user
import com.garfiec.librechat.feature.agents.resources.acl_public_description
import com.garfiec.librechat.feature.agents.resources.acl_public_label
import com.garfiec.librechat.feature.agents.resources.acl_retry
import com.garfiec.librechat.feature.agents.resources.acl_revoke_principal
import com.garfiec.librechat.feature.agents.resources.acl_role
import com.garfiec.librechat.feature.agents.resources.acl_roles_failed
import com.garfiec.librechat.feature.agents.resources.acl_search_empty
import com.garfiec.librechat.feature.agents.resources.acl_search_failed
import com.garfiec.librechat.feature.agents.resources.acl_search_hint
import com.garfiec.librechat.feature.agents.resources.acl_search_unavailable
import com.garfiec.librechat.feature.agents.resources.acl_share_check_failed
import com.garfiec.librechat.feature.agents.resources.acl_sharing_title
import com.garfiec.librechat.feature.agents.resources.cd_collapse
import com.garfiec.librechat.feature.agents.resources.cd_expand
import com.garfiec.librechat.feature.agents.resources.close
import com.garfiec.librechat.feature.agents.resources.sharing_and_permissions
import com.garfiec.librechat.feature.agents.viewmodel.AgentAclViewModel
import com.garfiec.librechat.feature.agents.viewmodel.aclKey
import com.garfiec.librechat.feature.agents.viewmodel.newSearchResults
import org.jetbrains.compose.resources.stringResource

/**
 * v0.8.5+ ACL-based sharing section. List grants, grant new principals, toggle
 * public access, revoke. Gate this composable at the call site behind
 * BackendVersion.isCompatibleOrNewer(actual, "0.8.5-rc1"); on older servers, keep
 * showing the legacy [AgentSharingSection].
 *
 * Drawn only when the viewer may share this agent (role AGENTS.SHARE, and author, admin or the
 * agent's SHARE bit), and the public switch only with AGENTS.SHARE_PUBLIC: the server refuses
 * everything else (as the skills sharing sheet gates). A failed check that could be hiding a yes
 * draws a Retry in its place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentAclSharingSection(
    viewModel: AgentAclViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    if (!uiState.canShare) {
        // A failed check is said, with its Retry; hiding the section would read as "can't share".
        if (uiState.shareCheckFailed) {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.acl_sharing_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (uiState.isCheckingShare) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    DialogErrorRow(
                        message = stringResource(Res.string.acl_share_check_failed),
                        onRetry = viewModel::retryShareCheck,
                    )
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.acl_sharing_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) Res.string.cd_collapse else Res.string.cd_expand,
                ),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.canSharePublic) {
                    // Every control waits for a change in flight: two racing changes could land out of order.
                    PublicToggle(
                        isPublic = uiState.isPublic,
                        publicAccessRoleId = uiState.publicAccessRoleId,
                        roles = uiState.availableRoles,
                        // Public access is unknown until the grants load, and turning it on needs a role.
                        enabled = !uiState.isUpdating && uiState.loaded && (uiState.isPublic || uiState.canGrant),
                        roleEnabled = !uiState.isUpdating && uiState.canGrant,
                        onSetPublic = viewModel::setPublic,
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = stringResource(Res.string.sharing_and_permissions),
                    style = MaterialTheme.typography.labelLarge,
                )

                // As the skills sheet's GrantsList: until a fetch lands the grants are unknown, so
                // "No one has been granted access" is said only of a list that loaded empty.
                when {
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        AdaptiveCircularProgressIndicator()
                    }
                    uiState.loadFailed -> DialogErrorRow(
                        message = stringResource(Res.string.acl_load_failed),
                        onRetry = viewModel::retryLoad,
                    )
                    !uiState.loaded -> Unit
                    else -> {
                        // The author first: their owner entry is what keeps the agent theirs.
                        uiState.principals.sortedBy { !uiState.isAuthor(it) }.forEach { principal ->
                            PrincipalGrantRow(
                                principal = principal,
                                roles = uiState.availableRoles,
                                enabled = !uiState.isUpdating,
                                onRevoke = if (uiState.isAuthor(principal)) null else ({ viewModel.revoke(principal) }),
                            )
                        }
                        if (uiState.principals.none { !uiState.isAuthor(it) }) {
                            Text(
                                text = stringResource(Res.string.acl_no_grants),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (uiState.rolesFailed) {
                    Text(
                        text = stringResource(Res.string.acl_roles_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Opening the dialog refetches roles that failed, so it stays reachable then.
                // The grants must have loaded: the dialog leaves out who already has access, which
                // it can only tell from them.
                AdaptiveOutlinedButton(
                    onClick = viewModel::openGrantDialog,
                    enabled = !uiState.isUpdating && uiState.loaded && (uiState.canGrant || uiState.rolesFailed),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.acl_add_principal))
                }

                uiState.error?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (uiState.showGrantDialog) {
        GrantAccessDialog(
            searchQuery = uiState.searchQuery,
            results = uiState.newSearchResults(),
            isSearching = uiState.isSearching,
            searchFailed = uiState.searchFailed,
            canSearchPeople = uiState.canSearchPeople,
            roles = uiState.availableRoles,
            rolesFailed = uiState.rolesFailed,
            enabled = !uiState.isUpdating,
            onQueryChange = viewModel::onSearchQueryChanged,
            onRetrySearch = viewModel::retrySearch,
            onRetryRoles = viewModel::retryRoles,
            onGrant = viewModel::grant,
            onDismiss = viewModel::dismissGrantDialog,
        )
    }
}

@Composable
private fun PrincipalGrantRow(
    principal: Principal,
    roles: List<AccessRole>,
    enabled: Boolean,
    /** Null on the author's row: their own grant isn't offered for removal, as on the skills sheet. */
    onRevoke: (() -> Unit)?,
) {
    val name = principalName(principal)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val typeLabel = stringResource(
                    when (principal.type) {
                        PrincipalType.USER -> Res.string.acl_principal_user
                        PrincipalType.GROUP -> Res.string.acl_principal_group
                        PrincipalType.ROLE -> Res.string.acl_principal_role
                        else -> Res.string.acl_principal_user
                    },
                )
                val roleLabel = principal.accessRoleId?.let { accessRoleLabel(ResourceType.AGENT, it, roles) }
                val subtitle = listOfNotNull(typeLabel, roleLabel).joinToString(" • ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRevoke != null) {
                IconButton(onClick = onRevoke, enabled = enabled) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.acl_revoke_principal, name),
                    )
                }
            }
        }
    }
}

/**
 * The public switch, as one toggleable row like the skills sheet's: the switch is announced with
 * its label, and the label toggles it. Turning it on lets the view model pick the role (the current
 * one, else the viewer default).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicToggle(
    isPublic: Boolean,
    publicAccessRoleId: String?,
    roles: List<AccessRole>,
    enabled: Boolean,
    roleEnabled: Boolean,
    onSetPublic: (Boolean, String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = isPublic,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { on -> onSetPublic(on, null) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(Res.string.acl_public_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.acl_public_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AdaptiveSwitch(checked = isPublic, onCheckedChange = null, enabled = enabled)
        }
        if (isPublic && roles.isNotEmpty()) {
            var roleMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = roleMenu,
                onExpandedChange = { roleMenu = it && roleEnabled },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AdaptiveOutlinedTextField(
                    value = publicAccessRoleId?.let { accessRoleLabel(ResourceType.AGENT, it, roles) }.orEmpty(),
                    enabled = roleEnabled,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.acl_role)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                    roles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(accessRoleLabel(ResourceType.AGENT, role.accessRoleId, roles)) },
                            onClick = {
                                onSetPublic(true, role.accessRoleId)
                                roleMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrantAccessDialog(
    searchQuery: String,
    results: List<Principal>,
    isSearching: Boolean,
    searchFailed: Boolean,
    canSearchPeople: Boolean,
    roles: List<AccessRole>,
    rolesFailed: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onRetryRoles: () -> Unit,
    onGrant: (Principal, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The viewer role, not the list's first: the roles route doesn't sort, so that may be owner.
    var selectedRole by remember(roles) { mutableStateOf(defaultAccessRoleId(ResourceType.AGENT, roles)) }
    var roleMenu by remember { mutableStateOf(false) }
    // Nothing can be granted without a role; the search waits for the roles, as the skills sheet's does.
    val canGrant = roles.isNotEmpty() && !rolesFailed

    AdaptiveAlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.acl_grant_dialog_title)) },
        text = {
            if (!canSearchPeople) {
                // The server refuses every search without the people picker; say why there's no field.
                Text(
                    text = stringResource(Res.string.acl_search_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (rolesFailed) {
                        DialogErrorRow(
                            message = stringResource(Res.string.acl_roles_failed),
                            onRetry = onRetryRoles,
                        )
                    }
                    AdaptiveOutlinedTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        enabled = canGrant,
                        label = { Text(stringResource(Res.string.acl_search_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ExposedDropdownMenuBox(
                        expanded = roleMenu,
                        onExpandedChange = { roleMenu = it && canGrant },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AdaptiveOutlinedTextField(
                            value = selectedRole?.let { accessRoleLabel(ResourceType.AGENT, it, roles) }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            enabled = canGrant,
                            label = { Text(stringResource(Res.string.acl_role)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(accessRoleLabel(ResourceType.AGENT, role.accessRoleId, roles)) },
                                    onClick = {
                                        selectedRole = role.accessRoleId
                                        roleMenu = false
                                    },
                                )
                            }
                        }
                    }

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (searchFailed) {
                        DialogErrorRow(message = stringResource(Res.string.acl_search_failed), onRetry = onRetrySearch)
                    } else if (isSearchablePrincipalQuery(searchQuery) && results.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.acl_search_empty),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (results.isNotEmpty()) {
                        val role = selectedRole
                        // Bounded, so a long result list scrolls under the search field instead of
                        // pushing it out of the dialog.
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = ResultsMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(results, key = { it.aclKey() }) { principal ->
                                AdaptiveAssistChip(
                                    onClick = { if (role != null) onGrant(principal, role) },
                                    label = {
                                        Text(principalName(principal))
                                    },
                                    // A chip with no role to grant would take the tap and do nothing.
                                    enabled = enabled && canGrant && role != null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AdaptiveButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

/**
 * A failure inside the dialog with its Retry under the message, as the skills sheet's SheetErrorRow
 * does: beside it, a long translated label ("Erneut versuchen" at 2x) leaves the message a sliver.
 */
@Composable
private fun DialogErrorRow(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text(stringResource(Res.string.acl_retry)) }
    }
}

private val ResultsMaxHeight = 240.dp

/** The principal's name, else email, else id, as the skills sheet's `principalName`. */
@Composable
private fun principalName(principal: Principal): String =
    listOf(principal.name, principal.email, principal.id).firstOrNull { !it.isNullOrBlank() }
        ?: stringResource(Res.string.acl_principal_unknown)
