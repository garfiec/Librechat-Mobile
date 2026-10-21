package com.garfiec.librechat.core.ui.components.acl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.acl.AclEntry
import com.garfiec.librechat.core.model.acl.AclError
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.acl_error_forbidden
import com.garfiec.librechat.core.ui.resources.acl_error_generic
import com.garfiec.librechat.core.ui.resources.acl_error_load
import com.garfiec.librechat.core.ui.resources.acl_error_not_found
import com.garfiec.librechat.core.ui.resources.acl_error_owner_protected
import com.garfiec.librechat.core.ui.resources.acl_people_empty
import com.garfiec.librechat.core.ui.resources.acl_people_title
import com.garfiec.librechat.core.ui.resources.acl_public_off_hint
import com.garfiec.librechat.core.ui.resources.acl_remove
import com.garfiec.librechat.core.ui.resources.acl_role_editor
import com.garfiec.librechat.core.ui.resources.acl_role_label
import com.garfiec.librechat.core.ui.resources.acl_role_owner
import com.garfiec.librechat.core.ui.resources.acl_role_viewer
import com.garfiec.librechat.core.ui.resources.acl_search_failed
import com.garfiec.librechat.core.ui.resources.acl_search_label
import com.garfiec.librechat.core.ui.resources.acl_search_no_matches
import com.garfiec.librechat.core.ui.resources.acl_type_group
import com.garfiec.librechat.core.ui.resources.acl_type_role
import com.garfiec.librechat.core.ui.resources.acl_type_user
import com.garfiec.librechat.core.ui.resources.cd_loading
import com.garfiec.librechat.core.ui.resources.error_banner_retry
import com.garfiec.librechat.core.ui.resources.share_sheet_error_network
import org.jetbrains.compose.resources.stringResource

/** What the access section reports; the host's controller does the reads and writes. */
@Immutable
class AclSharingActions(
    val onPublicChange: (Boolean) -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onGrant: (Principal) -> Unit = {},
    val onRevoke: (Principal) -> Unit = {},
    val onGrantRoleChange: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
) {
    companion object {
        val None = AclSharingActions()
    }
}

/**
 * Who can open one resource: a public switch, the people and groups with access, and a search to
 * add more. Stateless: it renders [state] and reports taps through [actions]. Lifted from the
 * agent and skill editors' sharing sections so any resource's access page can use it; those
 * editors still carry their own copies (their migration is a follow-up).
 *
 * The owner's row is locked; a role picker for new grants shows only when the resource offers more
 * than one role. [publicLabel] names who the public switch lets in, which depends on the resource
 * and the server's policy.
 */
@Composable
fun AclSharingSection(
    state: AclState,
    publicLabel: String,
    actions: AclSharingActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (state) {
            AclState.Closed -> Unit
            AclState.Loading -> LoadingBody()
            is AclState.Failed -> FailedBody(state.error, actions.onRetry)
            is AclState.Ready -> ReadyBody(state, publicLabel, actions)
        }
    }
}

@Composable
private fun LoadingBody() {
    val description = stringResource(Res.string.cd_loading)
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveCircularProgressIndicator()
    }
}

@Composable
private fun FailedBody(error: AclError, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = if (error == AclError.GENERIC) stringResource(Res.string.acl_error_load) else errorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        AdaptiveOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.error_banner_retry))
        }
    }
}

@Composable
private fun ReadyBody(state: AclState.Ready, publicLabel: String, actions: AclSharingActions) {
    val enabled = !state.busy
    Column(modifier = Modifier.fillMaxWidth()) {
        state.error?.let { ErrorLine(it) }
        if (state.canSharePublic) {
            PublicRow(
                label = publicLabel,
                checked = state.isPublic,
                enabled = enabled,
                onChange = actions.onPublicChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.acl_people_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.busy) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        state.entries.forEach { entry ->
            EntryRow(entry = entry, enabled = enabled, onRevoke = { actions.onRevoke(entry.principal) })
        }
        if (state.entries.none { !it.locked }) {
            Text(
                text = stringResource(Res.string.acl_people_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (state.canSearch) {
            SearchBlock(state = state, enabled = enabled, actions = actions)
        }
    }
}

@Composable
private fun PublicRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(Res.string.acl_public_off_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        AdaptiveSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun EntryRow(entry: AclEntry, enabled: Boolean, onRevoke: () -> Unit) {
    val principal = entry.principal
    val name = principal.displayName()
    PrincipalRow(
        principal = principal,
        subtitle = listOfNotNull(
            principalTypeLabel(principal.type),
            principal.accessRoleId?.let { roleLabel(it) },
        ).joinToString(" · "),
    ) {
        if (entry.locked) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp).size(20.dp),
            )
        } else {
            IconButton(onClick = onRevoke, enabled = enabled) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.acl_remove, name),
                )
            }
        }
    }
}

@Composable
private fun SearchBlock(state: AclState.Ready, enabled: Boolean, actions: AclSharingActions) {
    val search = state.search
    Column(modifier = Modifier.fillMaxWidth()) {
        AdaptiveOutlinedTextField(
            value = search.query,
            onValueChange = actions.onSearchQueryChange,
            label = { Text(stringResource(Res.string.acl_search_label)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (state.roles.size > 1) {
            GrantRolePicker(state = state, enabled = enabled, onChange = actions.onGrantRoleChange)
        }
        when {
            search.query.isBlank() -> Unit
            search.searching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            search.failed -> SearchNote(stringResource(Res.string.acl_search_failed), isError = true)
            search.results.isEmpty() -> SearchNote(stringResource(Res.string.acl_search_no_matches), isError = false)
            else -> Column(modifier = Modifier.padding(top = 8.dp)) {
                search.results.forEach { principal ->
                    PrincipalRow(
                        principal = principal,
                        subtitle = principal.email?.takeIf { it.isNotBlank() } ?: principalTypeLabel(principal.type),
                        onClick = { actions.onGrant(principal) },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantRolePicker(state: AclState.Ready, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.acl_role_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Text(state.grantRoleId?.let { roleLabel(it) }.orEmpty())
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.roles.forEach { role ->
                    DropdownMenuItem(
                        text = { Text(roleLabel(role.accessRoleId)) },
                        onClick = {
                            expanded = false
                            onChange(role.accessRoleId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrincipalRow(
    principal: Principal,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val name = principal.displayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialTile(seed = principal.type + ":" + (principal.id ?: name), label = name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SearchNote(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ErrorLine(error: AclError) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = errorText(error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun errorText(error: AclError): String = stringResource(
    when (error) {
        AclError.NETWORK -> Res.string.share_sheet_error_network
        AclError.FORBIDDEN -> Res.string.acl_error_forbidden
        AclError.NOT_FOUND -> Res.string.acl_error_not_found
        AclError.OWNER_PROTECTED -> Res.string.acl_error_owner_protected
        AclError.GENERIC -> Res.string.acl_error_generic
    },
)

/**
 * A role's name by its id's suffix (`sharedLink_viewer`, `agent_editor`, …): the roles route sends
 * i18n keys, not text, as names. An unknown role shows its id.
 */
@Composable
private fun roleLabel(accessRoleId: String): String = when {
    accessRoleId.endsWith("_owner") -> stringResource(Res.string.acl_role_owner)
    accessRoleId.endsWith("_editor") -> stringResource(Res.string.acl_role_editor)
    accessRoleId.endsWith("_viewer") -> stringResource(Res.string.acl_role_viewer)
    else -> accessRoleId
}

@Composable
private fun principalTypeLabel(type: String): String = stringResource(
    when (type) {
        PrincipalType.GROUP -> Res.string.acl_type_group
        PrincipalType.ROLE -> Res.string.acl_type_role
        else -> Res.string.acl_type_user
    },
)

private fun Principal.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: id ?: idOnTheSource.orEmpty()
