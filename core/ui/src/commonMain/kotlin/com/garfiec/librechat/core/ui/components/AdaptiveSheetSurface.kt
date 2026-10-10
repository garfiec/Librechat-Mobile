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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.SheetMargin
import com.garfiec.librechat.core.ui.glass.glassSheetPanel
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
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
        Surface(color = BottomSheetDefaults.ContainerColor, shape = BottomSheetDefaults.ExpandedShape, modifier = modifier) {
            Column(Modifier.fillMaxWidth(), content = content)
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
