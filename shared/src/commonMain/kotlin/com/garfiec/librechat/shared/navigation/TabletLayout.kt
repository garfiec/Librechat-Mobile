package com.garfiec.librechat.shared.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.ui.components.BannerDisplay
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.MinContentWidth
import com.garfiec.librechat.core.ui.components.SidebarWidth
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.navigation.FullWidthRoute
import com.garfiec.librechat.core.ui.navigation.LocalSidebarAsideRequests
import com.garfiec.librechat.core.ui.navigation.SidebarAsideRequests
import com.garfiec.librechat.core.ui.platform.FoldRegion
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.feature.auth.navigation.AddAccountServerUrl
import com.garfiec.librechat.feature.conversations.navigation.Projects
import com.garfiec.librechat.feature.settings.navigation.SettingsTabbed
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// The narrowest sidebar a fold may hand it before it keeps SidebarWidth instead.
private val MinFoldSidebarWidth = 240.dp

/** The sidebar slot's width and the empty band between it and the content, both from the start edge. */
internal data class SidebarSpan(val width: Dp, val contentGap: Dp)

/**
 * Where the sidebar ends and the content starts in a [windowWidth]-wide window. Flat, the sidebar is
 * [SidebarWidth] plus [startInset] (the glass panel's cutout padding). While a vertical [fold] is
 * active the sidebar ends where the fold's band starts and the content begins after the band, as long
 * as the panel keeps [MinFoldSidebarWidth] and the content [MinContentWidth]. The fold is in window
 * coordinates and physical, so RTL, which puts the sidebar on the right, measures from the other side.
 */
internal fun sidebarSpanFor(
    windowWidth: Dp,
    fold: FoldRegion?,
    layoutDirection: LayoutDirection,
    startInset: Dp,
): SidebarSpan {
    val flat = SidebarSpan(width = SidebarWidth + startInset, contentGap = 0.dp)
    if (fold == null || !fold.isVertical) return flat
    val band = fold.bounds
    val sidebarEnd = if (layoutDirection == LayoutDirection.Ltr) band.left else windowWidth - band.right
    val contentStart = if (layoutDirection == LayoutDirection.Ltr) band.right else windowWidth - band.left
    val fits = sidebarEnd - startInset >= MinFoldSidebarWidth && windowWidth - contentStart >= MinContentWidth
    return if (fits) SidebarSpan(width = sidebarEnd, contentGap = contentStart - sidebarEnd) else flat
}

// The open fraction's resting threshold: under a pixel of the widest sidebar, not Float's 0.01 default.
private const val SIDEBAR_FRACTION_THRESHOLD = 0.0005f

private val SidebarSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = SIDEBAR_FRACTION_THRESHOLD,
)

/** Velocity (px/s) above which a fling commits the gesture regardless of position. */
private const val FLING_VELOCITY_THRESHOLD = 800f

@Composable
fun TabletLayout(
    navigator: Navigator,
    navDisplay: NavDisplaySlot,
    modifier: Modifier = Modifier,
    navHostViewModel: NavHostViewModel = koinViewModel(),
) {
    // Banner state only -- drawer state is collected inside DrawerContent itself
    val banner by navHostViewModel.banner.collectAsStateWithLifecycle()
    val sessionRenewalBlocked by navHostViewModel.sessionRenewalBlocked.collectAsStateWithLifecycle()
    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()
    // The sidebar is an authenticated surface, same rule as PhoneLayout's drawer gestures: a
    // logged-out deep link (artifact viewer atop the auth base) is not in the auth flow either.
    val showSidebar = isLoggedIn && !navigator.isInAuthFlow
    // An unfolded Fold leaves a two-pane screen under its threshold beside the sidebar. Nothing is
    // persisted: leaving brings the sidebar back as it was.
    // A screen can also ask for the room while it shows something wide (an artifact beside the chat).
    val asideRequests = remember { SidebarAsideRequests() }
    val sidebarAside = navigator.currentRoute is FullWidthRoute || asideRequests.requested
    val sidebarReveal by animateFloatAsState(
        targetValue = if (sidebarAside) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "sidebarReveal",
    )
    val glass = rememberGlassStyle()

    // Persisted sidebar state from DataStore -- single source of truth in the ViewModel.
    // Null until the persisted value resolves; treat unknown as closed for boolean callers.
    val resolvedSidebarOpen by navHostViewModel.tabletSidebarOpen.collectAsStateWithLifecycle()
    val isSidebarOpen = resolvedSidebarOpen == true
    // Read by the gesture callbacks, which outlive recomposition (pointerInput is not restarted).
    val currentIsSidebarOpen by rememberUpdatedState(isSidebarOpen)

    // Whether swipe gesture is enabled (from settings)
    val gestureEnabled by navHostViewModel.tabletSidebarGestureEnabled.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // The glass panel keeps out of a start-edge cutout (GlassSidebarPanel pads it). The sidebar widens
    // by that inset, so the panel inside it stays SidebarWidth rather than giving the inset up.
    val startInset = if (glass != null) {
        WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(layoutDirection)
    } else {
        0.dp
    }
    // This layout fills the window on both hosts, so the fold's window coordinates are its own.
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val span = sidebarSpanFor(windowWidth, LocalWindowFeatures.current.fold, layoutDirection, startInset)
    val sidebarWidth by animateDpAsState(
        targetValue = span.width,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "sidebarWidth",
    )
    val contentGap by animateDpAsState(
        targetValue = span.contentGap,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "sidebarContentGap",
    )
    val sidebarWidthPx = with(density) { span.width.toPx() }
    // Drag deltas are physical (+x = rightward) but the sidebar sits on the start edge, which RTL mirrors.
    val dragSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f

    // How far the sidebar is open: 0 = closed, 1 = open. A fraction, so a width change resizes it.
    val sidebarOpen = remember { Animatable(0f, visibilityThreshold = SIDEBAR_FRACTION_THRESHOLD) }
    var initialStateApplied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Back press closes sidebar before navigating away. Gated on the sidebar being shown: armed while
    // hidden, it swallows back on the root auth screen and silently persists the sidebar closed.
    PlatformBackHandler(enabled = showSidebar && isSidebarOpen && !sidebarAside) {
        navHostViewModel.setTabletSidebarOpen(false)
    }

    // Sync: when ViewModel state changes (e.g. hamburger button, back press), animate the
    // sidebar to match. The first resolved value is snapped (not animated) so a tablet that
    // restores "open" doesn't visibly slide the sidebar in on every cold start.
    LaunchedEffect(resolvedSidebarOpen) {
        val resolved = resolvedSidebarOpen ?: return@LaunchedEffect
        val target = if (resolved) 1f else 0f
        if (!initialStateApplied) {
            sidebarOpen.snapTo(target)
            initialStateApplied = true
        } else if (sidebarOpen.value != target) {
            sidebarOpen.animateTo(targetValue = target, animationSpec = SidebarSpring)
        }
    }

    if (showSidebar) {
        val swipeModifier = if (gestureEnabled && !sidebarAside) {
            Modifier.pointerInput(sidebarWidthPx, dragSign) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x * dragSign
                        val shouldOpen = when {
                            velocity > FLING_VELOCITY_THRESHOLD -> true
                            velocity < -FLING_VELOCITY_THRESHOLD -> false
                            else -> sidebarOpen.value > 0.5f
                        }
                        scope.launch {
                            sidebarOpen.animateTo(targetValue = if (shouldOpen) 1f else 0f, animationSpec = SidebarSpring)
                            navHostViewModel.setTabletSidebarOpen(shouldOpen)
                        }
                    },
                    onDragCancel = {
                        val target = if (currentIsSidebarOpen) 1f else 0f
                        scope.launch {
                            sidebarOpen.animateTo(targetValue = target, animationSpec = SidebarSpring)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(
                            change.uptimeMillis,
                            change.position,
                        )
                        scope.launch {
                            val next = (sidebarOpen.value + dragAmount * dragSign / sidebarWidthPx).coerceIn(0f, 1f)
                            sidebarOpen.snapTo(next)
                        }
                    },
                )
            }
        } else {
            Modifier
        }

        Layout(
            content = {
                // Slot 0: Sidebar -- sidebarWidth wide, slides in from off-screen at the start edge
                Row(modifier = Modifier.fillMaxHeight()) {
                    val sidebar: @Composable (Modifier) -> Unit = { sidebarModifier ->
                        SidebarScaffold(
                            isOpen = isSidebarOpen,
                            onNewChat = navigator::navigateToNewChat,
                            onConversationClick = { conversationId ->
                                navigator.navigateToChat(conversationId)
                            },
                            onSettingsClick = {
                                navigator.navigate(SettingsTabbed)
                            },
                            onWorkspaceClick = { destination ->
                                navigator.navigate(destination.route())
                            },
                            currentWorkspace = navigator.currentRoute.workspaceDestination(),
                            onOpenProjectsIndex = {
                                navigator.navigate(Projects)
                            },
                            onNewChatInProject = { projectId, projectName ->
                                navigator.navigateToNewChatInProject(projectId, projectName)
                            },
                            onOpenSearch = { query ->
                                navigator.navigateToSearch(query)
                            },
                            onMessageClick = { conversationId, messageId, query, inReasoning ->
                                navigator.navigateToMessage(conversationId, messageId, query, inReasoning)
                            },
                            onSwitchAccount = { accountId ->
                                navHostViewModel.switchAccount(accountId)
                            },
                            onAddAccount = {
                                navigator.navigate(AddAccountServerUrl)
                            },
                            modifier = sidebarModifier,
                        )
                    }
                    if (glass == null) {
                        sidebar(Modifier.fillMaxWidth().fillMaxHeight())
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    } else {
                        // Beside the content rather than over it, so the flat frosted tier: there is
                        // nothing behind the panel for a backdrop to show.
                        GlassSidebarPanel(
                            style = glass,
                            backdrop = null,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            sidebar(Modifier.fillMaxSize())
                        }
                    }
                }
                // Slot 1: Main content -- resizes to fill the space after the sidebar and any fold band
                MainContent(
                    navDisplay = navDisplay,
                    asideRequests = asideRequests,
                    showBanner = true,
                    banner = banner,
                    onDismissBanner = navHostViewModel::dismissBanner,
                    sessionRenewalBlocked = sessionRenewalBlocked,
                    onSignInAgain = navHostViewModel::signInAgain,
                    onToggleDrawer = {
                        navHostViewModel.toggleTabletSidebar()
                    },
                )
            },
            // The page colour behind both slots: without it the margin around Liquid Glass's floating
            // panel and the band left over a fold show the bare window (black in dark mode).
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(swipeModifier)
                .clipToBounds(),
        ) { measurables, constraints ->
            val sidebarPx = sidebarWidth.roundToPx()
            val reveal = sidebarOpen.value * sidebarReveal
            val animatedPx = (sidebarPx * reveal).toInt()
            // The fold band opens and closes with the sidebar, so a hidden sidebar leaves content at 0.
            val mainStart = animatedPx + (contentGap.roundToPx() * reveal).toInt()

            val sidebarPlaceable = measurables[0].measure(
                constraints.copy(minWidth = sidebarPx, maxWidth = sidebarPx),
            )
            val mainWidth = (constraints.maxWidth - mainStart).coerceAtLeast(0)
            val mainPlaceable = measurables[1].measure(
                constraints.copy(minWidth = mainWidth, maxWidth = mainWidth),
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                sidebarPlaceable.placeRelative(animatedPx - sidebarPx, 0)
                mainPlaceable.placeRelative(mainStart, 0)
            }
        }
    } else {
        // Auth flow or logged out -- no sidebar, just main content
        MainContent(
            navDisplay = navDisplay,
            showBanner = false,
            banner = banner,
            onDismissBanner = navHostViewModel::dismissBanner,
            sessionRenewalBlocked = false,
            onSignInAgain = {},
            onToggleDrawer = {},
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MainContent(
    navDisplay: NavDisplaySlot,
    showBanner: Boolean,
    banner: Banner?,
    onDismissBanner: (String) -> Unit,
    sessionRenewalBlocked: Boolean,
    onSignInAgain: () -> Unit,
    onToggleDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    asideRequests: SidebarAsideRequests? = null,
) {
    Column(modifier = modifier) {
        if (showBanner) {
            SessionRenewalBlockedBanner(
                visible = sessionRenewalBlocked,
                onSignInAgain = onSignInAgain,
            )
            BannerDisplay(
                banner = banner,
                onDismiss = onDismissBanner,
            )
        }
        CompositionLocalProvider(LocalSidebarAsideRequests provides asideRequests) {
            navDisplay(onToggleDrawer, Modifier.fillMaxSize())
        }
    }
}
