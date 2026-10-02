package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle

private val GlassFabShape = RoundedCornerShape(16.dp)

/**
 * [small] is the M3 [SmallFloatingActionButton]. Outside an [AdaptiveScaffold]'s FAB slot, pass a
 * [backdrop] or get the flat fallback. [containerColor], [contentColor] and [elevation] apply in
 * Material only.
 */
@Composable
fun AdaptiveFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    content: @Composable () -> Unit,
) {
    val glass = rememberGlassStyle()
    val glassShape = if (small) CircleShape else GlassFabShape
    val shape = when {
        glass != null -> glassShape
        small -> FloatingActionButtonDefaults.smallShape
        else -> FloatingActionButtonDefaults.shape
    }
    val fabModifier = if (glass == null) modifier else modifier.glassSurface(glass, glassShape, backdrop)
    val fabContainer = if (glass == null) containerColor else Color.Transparent
    val fabContent = if (glass == null) contentColor else MaterialTheme.colorScheme.primary
    val fabElevation = if (glass == null) elevation else FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    if (small) {
        SmallFloatingActionButton(onClick, fabModifier, shape, fabContainer, fabContent, fabElevation, content = content)
    } else {
        FloatingActionButton(onClick, fabModifier, shape, fabContainer, fabContent, fabElevation, content = content)
    }
}
