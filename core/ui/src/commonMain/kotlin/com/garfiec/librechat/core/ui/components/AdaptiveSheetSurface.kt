package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.SheetMargin
import com.garfiec.librechat.core.ui.glass.glassSheetPanel
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.core.ui.platform.insetOf
import com.garfiec.librechat.core.ui.platform.overlap
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme

/**
 * The surface of a sheet a screen moves itself (a pull-up gesture). For it to blur what it covers,
 * record the whole screen beside it as [backdrop], not just a list.
 *
 * The glass inset is inside [modifier], so a sheet offset by its measured height is fully hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheetSurface(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = rememberGlassStyle()
    if (style == null) {
        // A sheet capped short of the window's width can stop short of the bar column: pad only what it covers.
        val bars = LocalWindowFeatures.current.verticalBars
        val density = LocalDensity.current
        // The whole column until placed.
        var covered by remember { mutableStateOf<Dp?>(null) }
        val measure = if (bars != null) {
            Modifier.onPlaced { coordinates ->
                with(density) {
                    covered = bars.overlap(
                        left = coordinates.positionInRoot().x.toDp(),
                        width = coordinates.size.width.toDp(),
                        windowWidth = coordinates.findRootCoordinates().size.width.toDp(),
                    )
                }
            }
        } else {
            Modifier
        }
        val inset = remember(bars, covered) { bars.insetOf(covered ?: bars?.columnWidth ?: 0.dp) }
        Surface(
            color = BottomSheetDefaults.ContainerColor,
            shape = BottomSheetDefaults.ExpandedShape,
            modifier = modifier.then(measure),
        ) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(inset), content = content)
        }
        return
    }
    val thickening = GlassDefaults.panelThickening(MaterialTheme.colorScheme.surface, LocalDarkTheme.current)
    Box(
        // Consumes the insets, so content that pads for the navigation bar itself doesn't add it twice.
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(SheetMargin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.fillMaxWidth().glassSheetPanel(style, backdrop, thickening)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
        }
    }
}
