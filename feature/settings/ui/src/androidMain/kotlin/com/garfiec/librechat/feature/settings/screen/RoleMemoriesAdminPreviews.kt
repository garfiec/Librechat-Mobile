package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesFlags
import com.garfiec.librechat.feature.settings.viewmodel.RoleMemoriesAdminUiState
import com.garfiec.librechat.feature.settings.viewmodel.RolePermissionsAdminError

private val userFlags = MemoriesFlags(use = true, create = true, update = true, read = true, optOut = false)

/** USER just saved: the switches are clean and the saved line shows. */
private val savedUser = RoleMemoriesAdminUiState(
    loaded = true,
    saved = true,
    flags = userFlags,
    savedFlags = userFlags,
)

/** ADMIN loaded and clean: the access warning sits under its USE switch. */
private val loadedAdmin = RoleMemoriesAdminUiState(
    selectedRole = "ADMIN",
    loaded = true,
    flags = userFlags,
    savedFlags = userFlags,
)

/** ADMIN with an unsaved change whose save the server refused. */
private val failedAdmin = RoleMemoriesAdminUiState(
    selectedRole = "ADMIN",
    loaded = true,
    error = RolePermissionsAdminError.SAVE,
    flags = userFlags.copy(optOut = true),
    savedFlags = userFlags,
)

/** ADMIN's load failed (offline): no switches, the error and Retry in their place. */
private val loadFailedAdmin = RoleMemoriesAdminUiState(
    selectedRole = "ADMIN",
    error = RolePermissionsAdminError.LOAD,
)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminMaterialPreview() = RoleMemoriesAdminHost(dark = false, UiStyle.MATERIAL, savedUser)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminGlassPreview() = RoleMemoriesAdminHost(dark = true, UiStyle.LIQUID_GLASS, savedUser)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminAdminWarningMaterialPreview() =
    RoleMemoriesAdminHost(dark = false, UiStyle.MATERIAL, loadedAdmin)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminAdminWarningGlassPreview() =
    RoleMemoriesAdminHost(dark = true, UiStyle.LIQUID_GLASS, loadedAdmin)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminErrorMaterialPreview() = RoleMemoriesAdminHost(dark = false, UiStyle.MATERIAL, failedAdmin)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminErrorGlassPreview() = RoleMemoriesAdminHost(dark = true, UiStyle.LIQUID_GLASS, failedAdmin)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminLoadFailedMaterialPreview() =
    RoleMemoriesAdminHost(dark = false, UiStyle.MATERIAL, loadFailedAdmin)

@PreviewNarrowScreens
@Composable
private fun RoleMemoriesAdminLoadFailedGlassPreview() =
    RoleMemoriesAdminHost(dark = true, UiStyle.LIQUID_GLASS, loadFailedAdmin)

@Composable
private fun RoleMemoriesAdminHost(dark: Boolean, style: UiStyle, uiState: RoleMemoriesAdminUiState) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RoleMemoriesAdminContent(
                uiState = uiState,
                onSelectRole = {},
                onRetryLoad = {},
                onUseChange = {},
                onCreateChange = {},
                onUpdateChange = {},
                onReadChange = {},
                onOptOutChange = {},
                onSave = {},
                onRequestBack = {},
                onDiscard = {},
                onDismissDiscard = {},
                onNavigateBack = {},
            )
        }
    }
}
