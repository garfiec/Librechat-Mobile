package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.update.UpdateCheckState
import com.garfiec.librechat.core.model.AppRelease
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.screen.providerkeys.ProviderKeyActions

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

// The sheet's content width on a 320dp phone (24dp each side) at 2x in German: Save must drop to a
// line of its own rather than be squeezed.
@Preview(name = "320dp · 2.0x · de", widthDp = 320, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun ProviderKeyActionsNarrowGermanMaterialPreview() =
    PreviewSurface(UiStyle.MATERIAL) { ProviderKeyActionsHost(horizontalPadding = 24.dp) }

@Preview(name = "320dp · 2.0x · de", widthDp = 320, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun ProviderKeyActionsNarrowGermanGlassPreview() =
    PreviewSurface(UiStyle.LIQUID_GLASS) { ProviderKeyActionsHost(horizontalPadding = 24.dp) }

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

/** A key that is set, so Revoke shows beside Cancel and Save. */
@Composable
private fun ProviderKeyActionsHost(horizontalPadding: Dp = 16.dp) {
    ProviderKeyActions(
        showRevoke = true,
        isRevoking = false,
        isSaving = false,
        onRevoke = {},
        onCancel = {},
        onSave = {},
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp),
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
