package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// Compare Models: the setup header (empty and picked), the composer's "Also asking" row and the
// laned tabs, in both interface styles. Long model ids are the case worth checking on a narrow screen.

@PreviewNarrowScreens
@Composable
internal fun CompareSetupLightMaterialPreview() = CompareSetupPreview(false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
internal fun CompareSetupDarkGlassPreview() = CompareSetupPreview(true, UiStyle.LIQUID_GLASS)

@Composable
private fun CompareSetupPreview(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                CompareSetupHeader(
                    primaryLabel = "gpt-5",
                    pickLabel = null,
                    isSameModel = false,
                    target = ComparisonLane.B,
                    onSelectTarget = {},
                    onCancel = {},
                    onStart = {},
                )
                CompareSetupHeader(
                    primaryLabel = "gpt-5",
                    pickLabel = "claude-opus-4-5-20251101-thinking",
                    isSameModel = false,
                    target = ComparisonLane.A,
                    onSelectTarget = {},
                    onCancel = {},
                    onStart = {},
                )
                ComposerComparisonRow(
                    comparison = ComposerComparison.OtherModel(
                        lane = ComparisonLane.B,
                        label = "claude-opus-4-5-20251101-thinking",
                        onChangeModel = {},
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(Modifier.height(160.dp)) {
                    ComparisonTabBar(
                        primaryModelName = "gpt-5",
                        secondaryModelName = "claude-opus-4-5-20251101-thinking",
                        primaryContent = {},
                        secondaryContent = {},
                    )
                }
            }
        }
    }
}
