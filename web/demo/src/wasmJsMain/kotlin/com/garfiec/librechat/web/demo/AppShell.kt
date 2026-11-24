package com.garfiec.librechat.web.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.SidebarWidth
import com.garfiec.librechat.core.ui.components.topbar.LocalSidebarOpen
import com.garfiec.librechat.core.ui.components.usesSidebarLayout
import com.garfiec.librechat.core.ui.glass.GlassSidebarPanel
import com.garfiec.librechat.core.ui.glass.GlassStyle
import com.garfiec.librechat.core.ui.glass.glassBackdropSource
import com.garfiec.librechat.core.ui.glass.rememberGlassBackdrop
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import kotlinx.coroutines.launch

/**
 * The app's window layout, as the Android and iOS nav hosts arrange it: a modal drawer over the
 * screen on a phone-sized window, and a sidebar beside it on a wider one. [sidebar] gets a `close`
 * callback for taps that navigate; [content] gets the top bar's menu button action, which opens the
 * drawer or toggles the sidebar.
 */
@Composable
fun AppShell(
    sidebar: @Composable (close: () -> Unit) -> Unit,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val glass = rememberGlassStyle()
    val backdrop = rememberGlassBackdrop()
    // The two layouts call content from different places; moving it keeps the screen's state (a chat's
    // session and its stream) when the window crosses the sidebar breakpoint.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { openDrawer: () -> Unit -> currentContent(openDrawer) } }

    // The page shrinks above the on-screen keyboard (index.html's resizes-content) and an app's window
    // doesn't, so only a width change decides the layout again: a keyboard that left the window too
    // short would swap the sidebar for the drawer, and close the search field that raised it.
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val usesSidebar = remember(window.width, density) {
        with(density) { usesSidebarLayout(window.width.toDp(), window.height.toDp()) }
    }

    if (usesSidebar) {
        SidebarShell(glass = glass, sidebar = { sidebar {} }, content = movableContent)
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { drawerState.close() } }
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = if (glass != null) GlassDrawerScrim else DrawerDefaults.scrimColor,
        drawerContent = {
            if (glass != null) {
                ModalDrawerSheet(
                    drawerState = drawerState,
                    drawerShape = RectangleShape,
                    drawerContainerColor = Color.Transparent,
                    drawerTonalElevation = 0.dp,
                ) {
                    GlassSidebarPanel(style = glass, backdrop = backdrop) { sidebar(close) }
                }
            } else {
                ModalDrawerSheet(drawerState = drawerState) { sidebar(close) }
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .glassBackdropSource(backdrop),
        ) {
            movableContent { scope.launch { drawerState.open() } }
        }
    }
}

/**
 * The tablet layout's sidebar, as `TabletLayout` runs it with the swipe on (the app's default): the
 * menu button toggles it, a horizontal drag anywhere slides it, and the content narrows beside it.
 * [LocalSidebarOpen] tells a side rail to drop its menu button while the sidebar is open.
 */
@Composable
private fun SidebarShell(
    glass: GlassStyle?,
    sidebar: @Composable () -> Unit,
    content: @Composable (toggle: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 0 = closed, 1 = open.
    val open = remember { Animatable(1f) }
    val sidebarPx = with(LocalDensity.current) { SidebarWidth.toPx() }
    val dragSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val settle: (Boolean) -> Unit = { target ->
        scope.launch { open.animateTo(if (target) 1f else 0f, SidebarSpring) }
    }
    val swipe = Modifier.pointerInput(sidebarPx, dragSign) {
        val velocity = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { velocity.resetTracking() },
            onDragEnd = {
                val speed = velocity.calculateVelocity().x * dragSign
                settle(
                    when {
                        speed > FLING_VELOCITY -> true
                        speed < -FLING_VELOCITY -> false
                        else -> open.value > 0.5f
                    },
                )
            },
            onDragCancel = { settle(open.targetValue > 0.5f) },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                scope.launch { open.snapTo((open.value + dragAmount * dragSign / sidebarPx).coerceIn(0f, 1f)) }
            },
        )
    }
    Layout(
        content = {
            // The sidebar and the drawer in it fill the whole slot, as in TabletLayout: one narrower than
            // the slot leaves a strip of page between it and the content that slides along with them.
            // propagateMinConstraints stretches the drawer past its own 300dp.
            if (glass != null) {
                GlassSidebarPanel(style = glass, backdrop = null, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), propagateMinConstraints = true) { sidebar() }
                }
            } else {
                Row(Modifier.fillMaxHeight()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        propagateMinConstraints = true,
                    ) { sidebar() }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            CompositionLocalProvider(LocalSidebarOpen provides (open.targetValue == 1f)) {
                Box(Modifier.fillMaxSize()) { content { settle(open.targetValue < 0.5f) } }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(swipe)
            .clipToBounds(),
    ) { measurables, constraints ->
        val width = SidebarWidth.roundToPx()
        val shown = (width * open.value).toInt()
        val sidebarPlaceable = measurables[0].measure(constraints.copy(minWidth = width, maxWidth = width))
        val mainWidth = (constraints.maxWidth - shown).coerceAtLeast(0)
        val mainPlaceable = measurables[1].measure(constraints.copy(minWidth = mainWidth, maxWidth = mainWidth))
        layout(constraints.maxWidth, constraints.maxHeight) {
            sidebarPlaceable.placeRelative(shown - width, 0)
            mainPlaceable.placeRelative(shown, 0)
        }
    }
}

// The app's sidebar spring and fling threshold (px/s), from TabletLayout.
private val SidebarSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private const val FLING_VELOCITY = 800f

// The app dims the screen only lightly behind a glass drawer, since the panel shows it through.
private val GlassDrawerScrim = Color.Black.copy(alpha = 0.12f)
