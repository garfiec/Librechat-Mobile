package com.garfiec.librechat.core.ui.components.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetBusy
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The share sheet with a link, at the narrow widths and large fonts where it runs out of room.
// Material renders light and Liquid Glass dark, so both themes are covered too. The states
// without a link are in ShareLinkSheetStatePreviews.

private val linked = ShareSheetState.Linked(
    url = "https://chat.example-company-intranet.com/share/6f1c2b9e-4d7a-4a55-9c1e-0b8f3d2a7e61",
    shareId = "6f1c2b9e-4d7a-4a55-9c1e-0b8f3d2a7e61",
    canUpdate = true,
    updateKeepsUrl = true,
    filesOption = FilesOption(includeFiles = true),
    audience = ShareAudience.SIGNED_IN,
)

@PreviewNarrowScreens
@Composable
private fun LinkedMaterialPreview() = SheetPreview(UiStyle.MATERIAL, linked)

@PreviewNarrowScreens
@Composable
private fun LinkedGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, linked)

@PreviewNarrowScreens
@Composable
private fun LinkedBusyMaterialPreview() = SheetPreview(UiStyle.MATERIAL, linkedBusyWithError())

@PreviewNarrowScreens
@Composable
private fun LinkedBusyGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, linkedBusyWithError())

@PreviewNarrowScreens
@Composable
private fun ReducedMaterialPreview() = SheetPreview(UiStyle.MATERIAL, reduced())

@PreviewNarrowScreens
@Composable
private fun ReducedGlassPreview() = SheetPreview(UiStyle.LIQUID_GLASS, reduced())

/** The real sheet, portalled into the glass host: what the screen shows over a page. */
@PreviewNarrowScreens
@Composable
private fun FullSheetGlassPreview() {
    LibreChatTheme(darkTheme = true, uiStyle = UiStyle.LIQUID_GLASS) {
        GlassSheetHost(Modifier.fillMaxWidth().height(SCREEN_HEIGHT.dp)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ShareLinkSheet(
                    state = linked,
                    onDismiss = {},
                    onCreate = {},
                    onUpdate = {},
                    onDelete = {},
                    onIncludeFilesChange = {},
                    onCopy = {},
                    onRetry = {},
                )
            }
        }
    }
}

private fun linkedBusyWithError() = linked.copy(
    busy = ShareSheetBusy.UPDATING,
    error = ShareSheetError.ContentBlocked("Message 4 matched the policy \"no internal hostnames\"."),
)

private fun reduced() = linked.copy(reduced = true, canUpdate = false, filesOption = null)

internal const val SCREEN_HEIGHT = 720

@Composable
internal fun SheetPreview(style: UiStyle, state: ShareSheetState) {
    LibreChatTheme(darkTheme = style == UiStyle.LIQUID_GLASS, uiStyle = style) {
        GlassSheetHost(Modifier.fillMaxWidth().height(SCREEN_HEIGHT.dp)) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AdaptiveSheetSurface(Modifier.fillMaxWidth()) {
                    ShareLinkSheetContent(
                        state = state,
                        onCreate = {},
                        onUpdate = {},
                        onDelete = {},
                        onIncludeFilesChange = {},
                        onCopy = {},
                        onRetry = {},
                    )
                }
            }
        }
    }
}
