package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle

private val GlassFabShape = RoundedCornerShape(16.dp)

/**
 * [small] is the M3 [SmallFloatingActionButton]. Outside an [AdaptiveScaffold]'s FAB slot, pass a
 * [backdrop] or get the flat fallback. [containerColor], [contentColor] and [elevation] apply in
 * Material only. In Material it also swells under the finger ([pressBounce]).
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
    val bounce = rememberPressBounce()
    val fabModifier = if (glass == null) {
        modifier.pressBounce(bounce)
    } else {
        modifier.glassSurface(glass, glassShape, backdrop)
    }
    val fabContainer = if (glass == null) containerColor else Color.Transparent
    val fabContent = if (glass == null) contentColor else MaterialTheme.colorScheme.primary
    val fabElevation = if (glass == null) elevation else FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    if (small) {
        SmallFloatingActionButton(onClick, fabModifier, shape, fabContainer, fabContent, fabElevation, content = content)
    } else {
        FloatingActionButton(onClick, fabModifier, shape, fabContainer, fabContent, fabElevation, content = content)
    }
}

/**
 * The labelled FAB: [icon] beside [text], collapsing to the icon alone when [expanded] is false
 * (callers collapse it once the list scrolls). Same glass and colour rules as
 * [AdaptiveFloatingActionButton].
 *
 * [text] is also the button's accessible name, expanded or collapsed: M3 clears the label's
 * semantics, so without it a screen reader would read a bare "Button". The icon's own semantics are
 * cleared, so a caller's content description can't be read twice.
 */
@Composable
fun AdaptiveExtendedFloatingActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
) {
    val glass = rememberGlassStyle()
    val bounce = rememberPressBounce()
    val labelled = modifier.semantics { contentDescription = text }
    val fabModifier = if (glass == null) {
        labelled.pressBounce(bounce)
    } else {
        labelled.glassSurface(glass, GlassFabShape, backdrop)
    }
    ExtendedFloatingActionButton(
        text = { Text(text) },
        icon = { Box(Modifier.clearAndSetSemantics {}) { icon() } },
        onClick = onClick,
        modifier = fabModifier,
        expanded = expanded,
        shape = if (glass == null) FloatingActionButtonDefaults.extendedFabShape else GlassFabShape,
        containerColor = if (glass == null) containerColor else Color.Transparent,
        contentColor = if (glass == null) contentColor else MaterialTheme.colorScheme.primary,
        elevation = if (glass == null) elevation else FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    )
}
