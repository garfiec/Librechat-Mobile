package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.update.UpdateCheckState
import com.garfiec.librechat.core.model.AppRelease
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_save
import com.garfiec.librechat.feature.settings.screen.providerkeys.ProviderKeyActions
import org.jetbrains.compose.resources.stringResource

// Rows whose actions share a line with a message, at the widths and scales where they run out of
// room. The provider-key sheet renders its actions only: a sheet is a window of its own, which a
// preview cannot draw.

@PreviewNarrowScreens
@Composable
private fun UpdateAvailableMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { UpdateResultHost(available) }

@PreviewNarrowScreens
@Composable
private fun UpdateAvailableGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { UpdateResultHost(available) }

@PreviewNarrowScreens
@Composable
private fun UpdateFailedMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { UpdateResultHost(failed) }

@PreviewNarrowScreens
@Composable
private fun UpdateFailedGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { UpdateResultHost(failed) }

@PreviewNarrowScreens
@Composable
private fun ProviderKeyActionsMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { ProviderKeyActionsHost() }

@PreviewNarrowScreens
@Composable
private fun ProviderKeyActionsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ProviderKeyActionsHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun UpdateResultHost(state: UpdateCheckState) {
    Column {
        UpdateCheckRow(state = state, onCheck = {}, onOpenWhatsNew = {})
    }
}

/** A key that is set, so Revoke is enabled beside Cancel and Save. */
@Composable
private fun ProviderKeyActionsHost() {
    ProviderKeyActions(
        canRevoke = true,
        isRevoking = false,
        isSaving = false,
        saveLabel = stringResource(Res.string.action_save),
        onRevoke = {},
        onCancel = {},
        onSave = {},
        modifier = Modifier.padding(16.dp),
    )
}

private val available = UpdateCheckState.Available(
    listOf(
        AppRelease(
            tag = "v2026.10.2",
            version = "2026.10.2",
            publishedAt = null,
            htmlUrl = "",
            apkUrl = null,
            highlights = null,
            fullChangelog = null,
        ),
    ),
)

private val failed = UpdateCheckState.Failed(message = null)
