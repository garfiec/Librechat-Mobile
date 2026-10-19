package com.garfiec.librechat.core.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

/**
 * A screen of [height] for previewing an `AdaptiveAlertDialog`: in Liquid Glass the dialog is
 * portalled into this host, so [height] is the room it has. A dialog with a field open stands in
 * for the keyboard with a shorter [height] (an iPhone SE with the keyboard up leaves about 400pt).
 */
@Composable
fun DialogPreviewHost(
    style: UiStyle,
    modifier: Modifier = Modifier,
    height: Dp = 640.dp,
    content: @Composable () -> Unit,
) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        GlassSheetHost(modifier.fillMaxWidth().height(height)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}
