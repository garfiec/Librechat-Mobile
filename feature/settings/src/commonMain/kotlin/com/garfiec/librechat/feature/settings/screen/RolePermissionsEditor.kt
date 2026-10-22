package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.RolePermissionsAdminError
import org.jetbrains.compose.resources.stringResource

/** One permission switch on a role-permissions editor. */
@Immutable
data class RolePermissionToggle(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    /**
     * Set on the permission type's USE switch: while the ADMIN role is selected, the editor warns
     * under it that a saved "off" can only be undone in librechat.yaml (web's `showAdminWarning`).
     */
    val showAdminWarning: Boolean = false,
)

/**
 * What a role-permissions editor shows, from the ViewModel of the permission type it edits
 * (skills, memories). The editor itself is stateless.
 */
@Immutable
data class RolePermissionsEditorState(
    val roles: List<String>,
    val selectedRole: String,
    val isLoading: Boolean,
    val loaded: Boolean,
    val isSaving: Boolean,
    val canSave: Boolean,
    val hasUnsavedChanges: Boolean,
    val showDiscardConfirm: Boolean,
    val exitRequested: Boolean,
    /** A LOAD failure offers Retry in place of the switches; a SAVE failure keeps them. */
    val error: RolePermissionsAdminError?,
    /** The last save landed. */
    val saved: Boolean,
)

/**
 * The admin editor for one permission type's role flags: a role picker, one switch per flag and
 * Save. Back asks before throwing pending switches away ([RolePermissionsEditorState.hasUnsavedChanges]),
 * and leaves once the ViewModel sets [RolePermissionsEditorState.exitRequested].
 *
 * [onRetryLoad] is required: a role that failed to load draws no switches, and picking the same
 * role again changes nothing, so Retry is the only way back to an editable screen.
 */
@Composable
internal fun RolePermissionsEditor(
    title: String,
    description: String,
    state: RolePermissionsEditorState,
    toggles: List<RolePermissionToggle>,
    onSelectRole: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onSave: () -> Unit,
    onRequestBack: () -> Unit,
    onDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) currentOnNavigateBack()
    }
    // Only while dirty or saving, so a clean screen keeps the predictive-back preview. While a save
    // runs, back goes to the ViewModel, which leaves once the save answers instead of cancelling it.
    PlatformBackHandler(enabled = state.hasUnsavedChanges || state.isSaving, onBack = onRequestBack)
    if (state.showDiscardConfirm) {
        DiscardChangesDialog(
            onDiscard = onDiscard,
            onDismiss = onDismissDiscard,
        )
    }

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onRequestBack),
                    title = BarTitle(title),
                    actions = emptyList(),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Locked while a save runs: its answer replaces every switch, so a change made in the
            // meantime would be silently overwritten and then shown as saved.
            RolePicker(
                roles = state.roles,
                selected = state.selectedRole,
                enabled = !state.isSaving,
                onSelect = onSelectRole,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AdaptiveCircularProgressIndicator()
                }
            } else if (state.loaded) {
                // No gap between rows: each is a full-height target, so a tap between two lands on one.
                Column(Modifier.fillMaxWidth()) {
                    toggles.forEach { toggle ->
                        PermissionToggle(
                            label = toggle.label,
                            checked = toggle.checked,
                            enabled = state.loaded && !state.isSaving,
                            onCheckedChange = toggle.onCheckedChange,
                        )
                        if (toggle.showAdminWarning && state.selectedRole == ADMIN_ROLE) {
                            AdminAccessWarning()
                        }
                    }
                }
            }

            // A role that failed to load draws no switches: they would read as "all off" for
            // values the screen never read. The error and its Retry take their place.
            when (state.error) {
                RolePermissionsAdminError.LOAD -> ErrorBanner(
                    message = stringResource(Res.string.role_permissions_load_failed),
                    onRetry = onRetryLoad,
                    retryLabel = stringResource(Res.string.action_retry),
                )
                // Announced when they appear: otherwise a save's only sign is Save turning off.
                RolePermissionsAdminError.SAVE -> Text(
                    stringResource(Res.string.role_permissions_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                null -> Unit
            }
            if (state.saved) {
                Text(
                    stringResource(Res.string.role_permissions_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            AdaptiveButton(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    // Sized like the label, as the other in-button spinners in Settings: the 40dp
                    // default would grow the button for the length of the save.
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(Res.string.action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolePicker(
    roles: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
        modifier = Modifier.fillMaxWidth(),
    ) {
        AdaptiveOutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(Res.string.role_permissions_role_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role) },
                    onClick = {
                        onSelect(role)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Web's ADMIN warning (`AdminSettingsDialog`, `com_ui_admin_access_warning`): shown whenever ADMIN
 * is selected, whatever the switch reads, because a saved "off" locks the admins out of this
 * screen's permission type. Registered in scripts/mirrors.json as `admin-access-warning`.
 */
@Composable
private fun AdminAccessWarning() {
    val uriHandler = LocalUriHandler.current
    // Set when nothing could open the link: the address is then shown to copy instead.
    var linkUnopened by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = stringResource(Res.string.role_permissions_admin_access_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            // Android throws when no browser is installed (de-Googled ROMs, work profiles).
            onClick = { linkUnopened = runCatching { uriHandler.openUri(ADMIN_ACCESS_DOCS_URL) }.isFailure },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            Text(stringResource(Res.string.role_permissions_more_info))
        }
        if (linkUnopened) {
            SelectionContainer {
                Text(
                    text = ADMIN_ACCESS_DOCS_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** Upstream SystemRoles.ADMIN. */
private const val ADMIN_ROLE = "ADMIN"

/** The page web's warning links to: the `interface` block of librechat.yaml. */
private const val ADMIN_ACCESS_DOCS_URL =
    "https://www.librechat.ai/docs/configuration/librechat_yaml/object_structure/interface"

@Composable
private fun PermissionToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // The row is the control, so a screen reader reads the label with the switch and the whole
    // row toggles; the switch itself only draws.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            // The switch drawn without a handler adds no touch target of its own, so the row is it.
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AdaptiveSwitch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}
