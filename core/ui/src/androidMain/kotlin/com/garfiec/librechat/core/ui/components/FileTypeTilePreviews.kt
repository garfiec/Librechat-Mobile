package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.files.FileKind
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun TilesLightMaterialPreview() = FileTypePreviewSurface(dark = false, UiStyle.MATERIAL) { AllTiles() }

@PreviewNarrowScreens
@Composable
private fun TilesDarkMaterialPreview() = FileTypePreviewSurface(dark = true, UiStyle.MATERIAL) { AllTiles() }

@PreviewNarrowScreens
@Composable
private fun TilesLightGlassPreview() = FileTypePreviewSurface(dark = false, UiStyle.LIQUID_GLASS) { AllTiles() }

@PreviewNarrowScreens
@Composable
private fun TilesDarkGlassPreview() = FileTypePreviewSurface(dark = true, UiStyle.LIQUID_GLASS) { AllTiles() }

@PreviewNarrowScreens
@Composable
private fun ChipsLightMaterialPreview() = FileTypePreviewSurface(dark = false, UiStyle.MATERIAL) { ChipStates() }

@PreviewNarrowScreens
@Composable
private fun ChipsDarkMaterialPreview() = FileTypePreviewSurface(dark = true, UiStyle.MATERIAL) { ChipStates() }

@PreviewNarrowScreens
@Composable
private fun ChipsLightGlassPreview() = FileTypePreviewSurface(dark = false, UiStyle.LIQUID_GLASS) { ChipStates() }

@PreviewNarrowScreens
@Composable
private fun ChipsDarkGlassPreview() = FileTypePreviewSurface(dark = true, UiStyle.LIQUID_GLASS) { ChipStates() }

@Composable
internal fun FileTypePreviewSurface(dark: Boolean, style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** Every kind's tile over its label, as the chip names it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllTiles() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileKind.entries.forEach { kind ->
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FileTypeTile(kind = kind)
                Text(
                    text = fileKindLabel(kind, null),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Resting, tappable, loading, disabled, unknown size, a state line, and a name too long to fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipStates() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewChip("summary.csv", 1_234)
        PreviewChip("analysis.py", 3_400, onClick = {})
        PreviewChip("report.pdf", 245_000, onClick = {}, isLoading = true)
        PreviewChip("bundle.tar.gz", 2_000_000, onClick = {}, enabled = false)
        PreviewChip("out.parquet", null)
        FileTypeChip(filename = "chart.svg", kind = FileKind.IMAGE, subtitle = "Expired", enabled = false, onClick = {})
        PreviewChip("a_really_long_quarterly_revenue_breakdown_final_v2.xlsx", 40_000, onClick = {})
    }
}

@Composable
private fun PreviewChip(
    name: String,
    bytes: Long?,
    onClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val kind = fileKindOf(name, null)
    FileTypeChip(
        filename = name,
        kind = kind,
        subtitle = fileTypeSubtitle(kind, name, bytes),
        isLoading = isLoading,
        enabled = enabled,
        onClick = onClick,
    )
}
