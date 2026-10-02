package com.garfiec.librechat.shared.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassStyle
import com.garfiec.librechat.core.ui.glass.glassSurface

/** Without a [backdrop] it draws an explicit shadow: only the backdrop path draws its own. */
@Composable
internal fun GlassSidebarPanel(
    style: GlassStyle,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(PanelMargin)
            .then(if (backdrop == null) Modifier.shadow(PanelShadow, PanelShape, clip = false) else Modifier)
            .glassSurface(style, PanelShape, backdrop)
            .clip(PanelShape),
    ) {
        content()
    }
}

private val PanelShape = RoundedCornerShape(28.dp)
private val PanelMargin = 8.dp
private val PanelShadow = 12.dp
