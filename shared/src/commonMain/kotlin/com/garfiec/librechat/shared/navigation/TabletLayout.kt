package com.garfiec.librechat.shared.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.ui.components.BannerDisplay
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.feature.agents.navigation.AgentMarketplace
import com.garfiec.librechat.feature.auth.navigation.AddAccountServerUrl
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.conversations.navigation.Projects
import com.garfiec.librechat.feature.conversations.navigation.Search
import com.garfiec.librechat.feature.files.navigation.Files
import com.garfiec.librechat.feature.schedules.navigation.SchedulesList
import com.garfiec.librechat.feature.settings.navigation.SettingsTabbed
import com.garfiec.librechat.feature.skills.navigation.SkillsList
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val SidebarWidth = 320.dp

/**
 * Whether a window this size gets [TabletLayout], whose sidebar pushes the content aside, rather than
 * [PhoneLayout]'s drawer over it. Both platform hosts decide with this, so they can't diverge.
 *
 * The sidebar has to leave the content a phone's width, or every screen beside it lays out narrower
 * than the smallest phone. A short window (a phone on its side) keeps the drawer: the sidebar's
 * header and footer would leave its list no room.
 */
fun usesSidebarLayout(windowWidth: Dp, windowHeight: Dp): Boolean =
    windowWidth - SidebarWidth >= MinContentWidth && windowHeight >= MinSidebarWindowHeight

private val MinContentWidth = 360.dp

// The compact-height window size class's upper bound.
private val MinSidebarWindowHeight = 480.dp

/** Velocity (px/s) above which a fling commits the gesture regardless of position. */
private const val FLING_VELOCITY_THRESHOLD = 800f

@Composable
fun TabletLayout(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    navDisplay: NavDisplaySlot = rememberMovableNavDisplay(navigator),
    navHostViewModel: NavHostViewModel = koinViewModel(),
) {
    // Banner state only -- drawer state is collected inside DrawerContent itself
    val banner by navHostViewModel.banner.collectAsStateWithLifecycle()
    val sessionRenewalBlocked by navHostViewModel.sessionRenewalBlocked.collectAsStateWithLifecycle()
    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()
    // The sidebar is an authenticated surface, same rule as PhoneLayout's drawer gestures: a
    // logged-out deep link (artifact viewer atop the auth base) is not in the auth flow either.
    val showSidebar = isLoggedIn && !navigator.isInAuthFlow
    // Search's list and preview pane need the whole width (an unfolded Fold leaves it under the
    // two-pane threshold beside the sidebar), so the sidebar steps aside while Search is on top.
    // Nothing is persisted: closing Search brings it back as it was.
    val searchOpen = navigator.currentRoute is Search
    val sidebarReveal by animateFloatAsState(
        targetValue = if (searchOpen) 0f else 1f,
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
    val sidebarWidth = if (glass != null) {
        SidebarWidth + WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(layoutDirection)
    } else {
        SidebarWidth
    }
    val sidebarWidthPx = with(density) { sidebarWidth.toPx() }
    // Drag deltas are physical (+x = rightward) but the sidebar sits on the start edge, which RTL mirrors.
    val dragSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f

    // Animatable tracks sidebar reveal in pixels: 0 = closed, sidebarWidthPx = open.
    val sidebarOffset = remember { Animatable(0f) }
    var initialStateApplied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Back press closes sidebar before navigating away. Gated on the sidebar being shown: armed while
    // hidden, it swallows back on the root auth screen and silently persists the sidebar closed.
    PlatformBackHandler(enabled = showSidebar && isSidebarOpen && !searchOpen) {
        navHostViewModel.setTabletSidebarOpen(false)
    }

    // Sync: when ViewModel state changes (e.g. hamburger button, back press), animate the
    // sidebar to match. The first resolved value is snapped (not animated) so a tablet that
    // restores "open" doesn't visibly slide the sidebar in on every cold start.
    LaunchedEffect(resolvedSidebarOpen, sidebarWidthPx) {
        val resolved = resolvedSidebarOpen ?: return@LaunchedEffect
        val target = if (resolved) sidebarWidthPx else 0f
        if (!initialStateApplied) {
            sidebarOffset.snapTo(target)
            initialStateApplied = true
        } else if (sidebarOffset.value != target) {
            sidebarOffset.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    if (showSidebar) {
        val swipeModifier = if (gestureEnabled && !searchOpen) {
            Modifier.pointerInput(sidebarWidthPx, dragSign) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x * dragSign
                        val currentOffset = sidebarOffset.value
                        val shouldOpen = when {
                            velocity > FLING_VELOCITY_THRESHOLD -> true
                            velocity < -FLING_VELOCITY_THRESHOLD -> false
                            else -> currentOffset > sidebarWidthPx * 0.5f
                        }
                        scope.launch {
                            sidebarOffset.animateTo(
                                targetValue = if (shouldOpen) sidebarWidthPx else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                            navHostViewModel.setTabletSidebarOpen(shouldOpen)
                        }
                    },
                    onDragCancel = {
                        val target = if (currentIsSidebarOpen) sidebarWidthPx else 0f
                        scope.launch {
                            sidebarOffset.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(
                            change.uptimeMillis,
                            change.position,
                        )
                        scope.launch {
                            val newOffset = (sidebarOffset.value + dragAmount * dragSign)
                                .coerceIn(0f, sidebarWidthPx)
                            sidebarOffset.snapTo(newOffset)
                        }
                    },
                )
            }
        } else {
            Modifier
        }

        Layout(
            content = {
                // Slot 0: Sidebar -- sidebarWidth wide, slides from off-screen left to x=0
                Row(modifier = Modifier.fillMaxHeight()) {
                    val sidebar: @Composable (Modifier) -> Unit = { sidebarModifier ->
                        SidebarScaffold(
                            isOpen = isSidebarOpen,
                            onNewChat = {
                                if (navigator.currentRoute !is NewChat) {
                                    navigator.navigateToTopLevel(NewChat())
                                }
                            },
                            onConversationClick = { conversationId ->
                                navigator.navigateToChat(conversationId)
                            },
                            onSettingsClick = {
                                navigator.navigate(SettingsTabbed)
                            },
                            onAgentsClick = {
                                navigator.navigate(AgentMarketplace)
                            },
                            onFilesClick = {
                                navigator.navigate(Files)
                            },
                            onSkillsClick = {
                                navigator.navigate(SkillsList)
                            },
                            onSchedulesClick = {
                                navigator.navigate(SchedulesList)
                            },
                            onOpenProjectsIndex = {
                                navigator.navigate(Projects)
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
                        sidebar(Modifier.width(SidebarWidth).fillMaxHeight())
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    } else {
                        // Beside the content rather than over it, so the flat frosted tier: there is
                        // nothing behind the panel for a backdrop to show.
                        GlassSidebarPanel(
                            style = glass,
                            backdrop = null,
                            modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                        ) {
                            sidebar(Modifier.fillMaxSize())
                        }
                    }
                }
                // Slot 1: Main content -- resizes to fill remaining space
                MainContent(
                    navDisplay = navDisplay,
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
            // The page colour behind both slots: in Liquid Glass the sidebar is a floating panel, and
            // without it the margin around the panel shows the bare window (black in dark mode).
            // Material's sidebar is edge to edge, so it leaves no margin to fill.
            modifier = modifier
                .fillMaxSize()
                .then(if (glass != null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                .then(swipeModifier)
                .clipToBounds(),
        ) { measurables, constraints ->
            val sidebarPx = sidebarWidth.roundToPx()
            val animatedPx = (sidebarOffset.value * sidebarReveal).toInt()

            val sidebarPlaceable = measurables[0].measure(
                constraints.copy(minWidth = sidebarPx, maxWidth = sidebarPx),
            )
            val mainWidth = (constraints.maxWidth - animatedPx).coerceAtLeast(0)
            val mainPlaceable = measurables[1].measure(
                constraints.copy(minWidth = mainWidth, maxWidth = mainWidth),
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                sidebarPlaceable.placeRelative(animatedPx - sidebarPx, 0)
                mainPlaceable.placeRelative(animatedPx, 0)
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
        navDisplay(onToggleDrawer, Modifier.fillMaxSize())
    }
}
