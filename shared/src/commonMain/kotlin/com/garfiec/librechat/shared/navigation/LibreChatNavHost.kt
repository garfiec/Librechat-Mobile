package com.garfiec.librechat.shared.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.garfiec.librechat.core.common.conversation.OpenConversationRegistry
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.lifecycle.DeferredWorkWindow
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.BannerDisplay
import com.garfiec.librechat.core.ui.components.share.LocalExternalUriHandler
import com.garfiec.librechat.core.ui.components.share.LocalOpenSharedChat
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.glass.GlassStyle
import com.garfiec.librechat.core.ui.glass.glassBackdropSource
import com.garfiec.librechat.core.ui.glass.rememberGlassBackdrop
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.AppLocale
import com.garfiec.librechat.feature.agents.navigation.AgentMarketplace
import com.garfiec.librechat.feature.agents.navigation.agentsEntries
import com.garfiec.librechat.feature.auth.navigation.AddAccountServerUrl
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.auth.navigation.authEntries
import com.garfiec.librechat.feature.auth.navigation.isAddAccountFlowRoute
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.ModelShortcutBus
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.chat.navigation.chatEntries
import com.garfiec.librechat.feature.chat.navigation.handoffKey
import com.garfiec.librechat.feature.chat.navigation.isPlainLanding
import com.garfiec.librechat.feature.chat.viewmodel.ChatNoticeHandoff
import com.garfiec.librechat.feature.chat.viewmodel.PromptInsertionHandoff
import com.garfiec.librechat.feature.chat.viewmodel.ServerFileSelectionHandoff
import com.garfiec.librechat.feature.conversations.drawer.DrawerViewModel
import com.garfiec.librechat.feature.conversations.navigation.ArchivedConversations
import com.garfiec.librechat.feature.conversations.navigation.BookmarkChats
import com.garfiec.librechat.feature.conversations.navigation.Bookmarks
import com.garfiec.librechat.feature.conversations.navigation.ProjectChats
import com.garfiec.librechat.feature.conversations.navigation.Projects
import com.garfiec.librechat.feature.conversations.navigation.conversationsEntries
import com.garfiec.librechat.feature.files.navigation.Files
import com.garfiec.librechat.feature.files.navigation.FilesPicker
import com.garfiec.librechat.feature.files.navigation.filePickerEntries
import com.garfiec.librechat.feature.files.navigation.filesEntries
import com.garfiec.librechat.feature.schedules.navigation.SchedulesList
import com.garfiec.librechat.feature.schedules.navigation.schedulesEntries
import com.garfiec.librechat.feature.settings.navigation.Memories
import com.garfiec.librechat.feature.settings.navigation.SettingsTabbed
import com.garfiec.librechat.feature.settings.navigation.WhatsNew
import com.garfiec.librechat.feature.settings.navigation.mcpServersEntry
import com.garfiec.librechat.feature.settings.navigation.memoriesEntry
import com.garfiec.librechat.feature.settings.navigation.settingsEntries
import com.garfiec.librechat.feature.skills.navigation.SkillsList
import com.garfiec.librechat.feature.skills.navigation.skillsEntries
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.dismiss
import com.garfiec.librechat.shared.resources.dont_warn_again
import com.garfiec.librechat.shared.resources.session_banned_message
import com.garfiec.librechat.shared.resources.session_banned_message_named
import com.garfiec.librechat.shared.resources.session_banned_title
import com.garfiec.librechat.shared.resources.session_expired_message
import com.garfiec.librechat.shared.resources.session_expired_message_named
import com.garfiec.librechat.shared.resources.session_expired_switched
import com.garfiec.librechat.shared.resources.session_expired_switched_named
import com.garfiec.librechat.shared.resources.session_expired_title
import com.garfiec.librechat.shared.resources.version_mismatch_message
import com.garfiec.librechat.shared.resources.version_mismatch_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Sentinel for "no identity recorded yet" in the saved hygiene marker — distinct from null,
 *  which means a recorded logged-out state. */
private const val HYGIENE_UNRECORDED = "__unrecorded__"

/**
 * Shared root composable for LibreChat navigation.
 *
 * With no [content], picks [TabletLayout] or [PhoneLayout] from the window (iOS). Android passes
 * [content] to add deep links and share intents, and makes the same [usesSidebarLayout] choice there.
 */
@Composable
fun LibreChatNavHost(
    modifier: Modifier = Modifier,
    appLocaleTag: String? = null,
    hasPendingDeepLink: Boolean = false,
    navHostViewModel: NavHostViewModel = koinViewModel(),
    drawerViewModel: DrawerViewModel = koinViewModel(),
    content: (@Composable (Navigator, NavHostViewModel, Modifier) -> Unit)? = null,
) {
    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()

    // Publish foreground state for deferred background work. onDispose reports background because a
    // host leaving composition is the app going away as far as any consumer is concerned.
    val foregroundSignal = koinInject<ForegroundSignal>()
    val deferredWorkWindow = koinInject<DeferredWorkWindow>()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, foregroundSignal, deferredWorkWindow) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foregroundSignal.set(true)
                    // Latches, so this is only ever the first ON_START that matters. It is what
                    // lets deferred work outlive the foreground, and what keeps it off cold start.
                    deferredWorkWindow.markUiStarted()
                }
                Lifecycle.Event.ON_STOP -> foregroundSignal.set(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            foregroundSignal.set(false)
        }
    }

    val openConversationRegistry = koinInject<OpenConversationRegistry>()

    // Start key comes from the synchronous logged-in seed, not a fixed NewChat. Starting logged-in
    // and redirecting away composes the chat shell for real and then plays NavDisplay's transition
    // animation over it — a third of a second of "signed in" before the auth screen, on every
    // logged-out cold start. This reads the same seed the redirect below does, which leaves that
    // redirect a backstop for the deep-link path rather than the thing that picks the screen.
    val backStack = rememberNavBackStack(
        navigationSavedStateConfig,
        if (navHostViewModel.isLoggedIn.value) NewChat() else ServerUrl,
    )
    // Leaving a screen drops focus and the keyboard; see Navigator's onScreenChange.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dropFocus by rememberUpdatedState {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val navigator = remember(backStack) { Navigator(backStack, onScreenChange = { dropFocus() }) }

    // Redirect to auth if not logged in — once per saved-state lifecycle, NOT on every recreation.
    // LaunchedEffect(Unit) restarts on each Activity recreation (config change / process-death restore,
    // see the hygiene note below), but by then the back stack is already restored — possibly to a
    // logged-out-viewable deep-link target (an artifact viewer atop the auth base). Re-firing would
    // clear() that away, so a rememberSaveable latch makes this run only on a genuinely fresh start.
    // Also skipped while a deep link is pending: the deep-link handler owns the initial stack then.
    var initialAuthRedirectDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialAuthRedirectDone) {
            initialAuthRedirectDone = true
            if (!navHostViewModel.isLoggedIn.value && !hasPendingDeepLink) {
                navigator.navigateToAuth()
            }
        }
    }

    // iOS home-screen quick action: the Swift handler pushes the tapped (endpoint, model) onto the
    // bus; open a NewChat pre-selected on it. Deferred until logged in so a cold launch from a quick
    // action lands the model once auth resolves. Android sets this bus never — it deep-links instead.
    val modelShortcutBus = koinInject<ModelShortcutBus>()
    val pendingModelShortcut by modelShortcutBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingModelShortcut, isLoggedIn) {
        val ref = pendingModelShortcut ?: return@LaunchedEffect
        if (!isLoggedIn) return@LaunchedEffect
        navigator.navigateToTopLevel(NewChat(endpoint = ref.endpoint, model = ref.model, spec = ref.spec))
        modelShortcutBus.consume()
    }

    // Track active conversation from nav back stack
    LaunchedEffect(navigator.currentRoute) {
        val conversationId = (navigator.currentRoute as? Chat)?.conversationId
        drawerViewModel.setActiveConversation(conversationId)
        // Same answer, different audience: the drawer highlights this conversation, background cache
        // work has to leave it alone. A non-chat route clears it — nothing open, nothing protected.
        openConversationRegistry.set(conversationId)

        // Navigation breadcrumb: route type name only — low cardinality, content-free.
        val screen = navigator.currentRoute?.let { it::class.simpleName } ?: "none"
        Diag.i("Breadcrumb", attrs = mapOf("screen" to screen)) { "nav" }
    }

    // Session expiry and failover. The ViewModel decides between signing in and moving to another
    // account that is still signed in; this only carries out the route.
    LaunchedEffect(Unit) {
        navHostViewModel.sessionRoute.collect { route ->
            when (route) {
                SessionRoute.SignIn -> navigator.navigateToAuth()
                // Only off the sign-in screens: on chat, the account flip has already reset the stack.
                SessionRoute.Chat -> if (navigator.isInAuthFlow) {
                    navigator.navigateToChat(
                        accountScope = (navHostViewModel.accountState.value as? AccountState.Resolved)?.id?.value,
                    )
                }
            }
        }
    }

    // A restored back stack can still hold add-account routes after process death, but the pending
    // add session is memory-only and never survives it. Without the session the restored add-mode
    // screens silently fall back to the LIVE server — credentials typed for the new server would be
    // POSTed to the wrong host, and a "success" would replace the active session instead of adding.
    // Strip the flow (and anything stacked above it) before it can render. On a plain Activity
    // recreation the pending session is still alive, so nothing is stripped.
    LaunchedEffect(Unit) {
        if (!navHostViewModel.hasPendingAdd()) {
            val firstAddRoute = backStack.indexOfFirst { it.isAddAccountFlowRoute }
            if (firstAddRoute >= 0) {
                while (backStack.size > firstAddRoute) backStack.removeLastOrNull()
                if (backStack.isEmpty()) backStack.add(NewChat())
            }
        }
    }

    // Cancel an in-progress add-account flow once none of its routes remain on the back stack —
    // covers back-out, the session-expiry reset, and any navigation that abandons the flow. On a
    // successful completion the pending session is already consumed, so the cancel no-ops. Watching
    // the stack (instead of per-screen dispose hooks) survives the flow spanning several routes.
    LaunchedEffect(Unit) {
        snapshotFlow { backStack.any { it.isAddAccountFlowRoute } }
            .collect { inAddFlow ->
                if (!inAddFlow) navHostViewModel.cancelPendingAdd()
            }
    }

    // The active account changed underneath the UI. Drop the process-global Coil cache — it is
    // account-blind (bare-URL keys), so the outgoing account's decoded images must not survive into
    // the next session; the disk half only exists on Android (iOS configures no diskCache). On an
    // account-to-account flip also reset the back stack: the outgoing account's routes point at
    // rows the new account can't read (or, on remove, rows that no longer exist).
    //
    // The identity this UI last ran hygiene for is SAVED STATE, compared against the live resolved
    // one — not a transition-flow collector. A cold collector restarted by Activity recreation or
    // process death initializes its marker to the already-flipped account and silently swallows a
    // flip that landed in the gap; the saved comparison catches up on whatever was missed while
    // this composition did not exist.
    val platformContext = LocalPlatformContext.current
    val accountState by navHostViewModel.accountState.collectAsStateWithLifecycle()
    var hygieneAccountId by rememberSaveable { mutableStateOf<String?>(HYGIENE_UNRECORDED) }
    LaunchedEffect(accountState) {
        val resolved = accountState as? AccountState.Resolved ?: return@LaunchedEffect
        val current = resolved.id?.value
        val previous = hygieneAccountId
        if (previous != HYGIENE_UNRECORDED && previous != current) {
            val imageLoader = SingletonImageLoader.get(platformContext)
            imageLoader.memoryCache?.clear()
            withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
            // A shared link that asked for this switch is taken on every flip, so one that can't
            // open now (the flip landed signed out) can't open over some later, unrelated switch.
            val pendingSharedChat = navHostViewModel.takePendingSharedChat(current)
            // Reset the stack only on an account-to-account flip; on account-to-logged-out the
            // initiating flow (or the session-expired signal) owns navigation.
            // A flip that lands with the session already gone (the account switched to expired, and
            // its sign-in route went out first) must not be routed back to chat on top of it.
            if (previous != null && current != null && navHostViewModel.isLoggedIn.value) {
                // Scoped to the incoming account, or the landing's ViewModel would survive the
                // flip — see Navigator.navigateToChat.
                navigator.navigateToChat(accountScope = current)
                // The shared link opens over the fresh stack. Here and not in a collector of its
                // own, which would race the reset above.
                pendingSharedChat?.let(navigator::navigateToSharedChat)
            }
        }
        hygieneAccountId = current
    }

    // Shared links tapped anywhere in the app (chat messages, the Shared chats page): one on a
    // signed-in account's server opens the in-app viewer; anything else goes to the browser, as
    // every other link does. The platform handler stays reachable for the viewer's own
    // "Open in browser", which must not route back into the viewer.
    val platformUriHandler = LocalUriHandler.current
    val linkScope = rememberCoroutineScope()
    val sharedLinkUriHandler = remember(platformUriHandler, navigator, navHostViewModel) {
        SharedLinkUriHandler(platformUriHandler) { url ->
            linkScope.launch {
                when (val route = navHostViewModel.routeSharedLink(url)) {
                    is SharedLinkRoute.Open -> navigator.navigateToSharedChat(route.shareId)
                    is SharedLinkRoute.OfferSwitch -> navHostViewModel.offerSharedLinkSwitch(route)
                    SharedLinkRoute.NotOurs -> runCatching { platformUriHandler.openUri(url) }
                }
            }
        }
    }
    val openSharedChat = remember(navigator) { { shareId: String -> navigator.navigateToSharedChat(shareId) } }
    LaunchedEffect(navHostViewModel, platformUriHandler) {
        navHostViewModel.browserLinks.collect { url -> runCatching { platformUriHandler.openUri(url) } }
    }

    // The locale wrapper must sit BELOW the back stack: changing language recreates the rendered
    // UI so every stringResource re-resolves, while the back stack created above survives the swap
    // and the user stays on their current screen.
    AppLocale(tag = appLocaleTag) {
        CompositionLocalProvider(
            LocalUriHandler provides sharedLinkUriHandler,
            LocalExternalUriHandler provides platformUriHandler,
            LocalOpenSharedChat provides openSharedChat,
        ) {
            // Date/time format + the app-wide relative-time ticker. Inside AppLocale, which is where
            // the language override reaches the configuration the format resolves its locale from.
            val dateTimePrefsStore = koinInject<DateTimePrefsStore>()
            val dateTimePrefs by dateTimePrefsStore.prefs.collectAsStateWithLifecycle(
                initialValue = dateTimePrefsStore.initial,
            )
            // Liquid Glass sheets are drawn over the whole app from here (see GlassSheetHost) — around
            // both the shared layouts and a platform's own [content] (Android's), or sheets there fall
            // back to the opaque M3 sheet.
            ProvideDateTimeFormat(dateTimePrefs) {
                GlassSheetHost {
                    // Ctrl/⌘+Shift+F. This handler sees keys only while something is focused, so the
                    // platform hosts also catch it before Compose (AppShortcuts).
                    DisposableEffect(navigator) {
                        val openSearch = { navigator.openSearchFromShortcut() }
                        AppShortcuts.openSearch = openSearch
                        onDispose { if (AppShortcuts.openSearch === openSearch) AppShortcuts.openSearch = null }
                    }
                    Box(
                        modifier = modifier.onPreviewKeyEvent { navigator.openSearchOnShortcut(it) },
                        propagateMinConstraints = true,
                    ) {
                        if (content != null) {
                            content(navigator, navHostViewModel, Modifier)
                        } else {
                            // Read from the window rather than a size class so iPad Split View / Slide
                            // Over, which resize the window without a rotation, flip the layout too. iOS
                            // resizes in place, so both layouts share one movable NavDisplay; see
                            // rememberMovableNavDisplay.
                            val navDisplay = rememberMovableNavDisplay(navigator)
                            val window = LocalWindowInfo.current.containerSize
                            val usesSidebar = with(LocalDensity.current) {
                                usesSidebarLayout(window.width.toDp(), window.height.toDp())
                            }
                            if (usesSidebar) {
                                TabletLayout(navigator = navigator, navDisplay = navDisplay)
                            } else {
                                PhoneLayout(navigator = navigator, navDisplay = navDisplay)
                            }
                        }
                    }
                }
            }

            // Version mismatch warning dialog
            val versionMismatch by navHostViewModel.versionMismatch.collectAsStateWithLifecycle()
            versionMismatch?.let { mismatch ->
                VersionMismatchDialog(
                    supportedVersion = mismatch.supportedVersion,
                    backendVersion = mismatch.backendVersion,
                    onDismiss = navHostViewModel::dismissVersionWarning,
                    onDismissPermanently = navHostViewModel::dismissVersionWarningPermanently,
                )
            }

            // Rendered here rather than inside the auth screens: it sits above NavDisplay and so survives
            // the back-stack reset that routed the user to auth in the first place.
            val sessionExpiredNotice by navHostViewModel.sessionExpiredNotice.collectAsStateWithLifecycle()
            sessionExpiredNotice?.let { notice ->
                SessionExpiredDialog(
                    notice = notice,
                    onDismiss = navHostViewModel::dismissSessionExpiredNotice,
                )
            }

            val sharedLinkOffer by navHostViewModel.sharedLinkOffer.collectAsStateWithLifecycle()
            sharedLinkOffer?.let { offer ->
                SharedLinkSwitchDialog(
                    offer = offer,
                    onSwitch = navHostViewModel::acceptSharedLinkSwitch,
                    onOpenInBrowser = {
                        navHostViewModel.dismissSharedLinkOffer()
                        runCatching { platformUriHandler.openUri(offer.url) }
                    },
                    onDismiss = navHostViewModel::dismissSharedLinkOffer,
                )
            }
        }
    }
}

/** Reports an unannounced sign-out and the account the app moved to when there was one, or a ban,
 *  which doesn't move the user. */
@Composable
internal fun SessionExpiredDialog(notice: SessionExpiredNotice, onDismiss: () -> Unit) {
    val accountLabel = notice.accountLabel
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (notice.banned) Res.string.session_banned_title else Res.string.session_expired_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = when {
                    notice.banned && accountLabel.isBlank() -> stringResource(Res.string.session_banned_message)
                    notice.banned -> stringResource(Res.string.session_banned_message_named, accountLabel)
                    notice.switchedTo != null && accountLabel.isNotBlank() ->
                        stringResource(Res.string.session_expired_switched_named, accountLabel, notice.switchedTo)
                    notice.switchedTo != null ->
                        stringResource(Res.string.session_expired_switched, notice.switchedTo)
                    accountLabel.isBlank() -> stringResource(Res.string.session_expired_message)
                    else -> stringResource(Res.string.session_expired_message_named, accountLabel)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dismiss))
            }
        },
    )
}

@Composable
internal fun VersionMismatchDialog(
    supportedVersion: String,
    backendVersion: String,
    onDismiss: () -> Unit,
    onDismissPermanently: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.version_mismatch_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.version_mismatch_message, supportedVersion, backendVersion),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dismiss))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissPermanently) {
                Text(stringResource(Res.string.dont_warn_again))
            }
        },
    )
}

/** Phone layout with a modal drawer sidebar: the path on both platforms when [usesSidebarLayout] is false. */
@Composable
fun PhoneLayout(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    navDisplay: NavDisplaySlot = rememberMovableNavDisplay(navigator),
    navHostViewModel: NavHostViewModel = koinViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val banner by navHostViewModel.banner.collectAsStateWithLifecycle()
    val sessionRenewalBlocked by navHostViewModel.sessionRenewalBlocked.collectAsStateWithLifecycle()

    val glass = rememberGlassStyle()
    val drawerBackdrop = rememberGlassBackdrop()
    // Any part of the sheet on screen, including mid-drag, where currentValue and targetValue can
    // both still read Closed. The offset runs from minus the sheet's width (closed) to 0 (open).
    var drawerWidthPx by remember { mutableIntStateOf(0) }
    val drawerShowing by remember {
        derivedStateOf {
            val offset = drawerState.currentOffset
            !offset.isNaN() && drawerWidthPx > 0 && offset > -drawerWidthPx + 1f
        }
    }

    // The drawer is an authenticated surface, and once the session ends its gestures are off (below),
    // which also takes away the scrim tap that would close it. Shut it here, or it stays open over
    // the sign-in screen with nothing to dismiss it on iOS.
    val signedOut = !isLoggedIn || navigator.isInAuthFlow
    LaunchedEffect(signedOut) {
        if (signedOut && !drawerState.isClosed) drawerState.snapTo(DrawerValue.Closed)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // The drawer is an authenticated surface (conversations, account, settings). Require a session,
        // not just "not in the auth flow" — a logged-out deep link (e.g. an artifact viewer atop the
        // auth base) leaves a non-auth route on top, and without this its edge-swipe would open the
        // drawer over a session-less state.
        gesturesEnabled = isLoggedIn && !navigator.isInAuthFlow,
        // Liquid Glass shows the screen through the panel, so it dims it only lightly.
        scrimColor = if (glass != null) GlassDrawerScrim else DrawerDefaults.scrimColor,
        drawerContent = {
            val sidebar: @Composable () -> Unit = {
                // targetValue, not isOpen: start polling as the drawer begins to open, not after it settles.
                val drawerOpen by remember { derivedStateOf { drawerState.targetValue == DrawerValue.Open } }
                // The share sheet's Preview opens the viewer from here: shut the drawer first, as every
                // other way out of it does, or it stays open over the viewer.
                val openSharedChat = remember(navigator, drawerState, scope) {
                    { shareId: String ->
                        scope.launch { drawerState.close() }
                        navigator.navigateToSharedChat(shareId)
                    }
                }
                CompositionLocalProvider(LocalOpenSharedChat provides openSharedChat) {
                    SidebarScaffold(
                        isOpen = drawerOpen,
                        onNewChat = {
                            scope.launch { drawerState.close() }
                            // A project's landing is not the plain one, so New chat still leaves it.
                            if ((navigator.currentRoute as? NewChat)?.isPlainLanding != true) {
                                navigator.navigateToTopLevel(NewChat())
                            }
                        },
                        onConversationClick = { conversationId ->
                            scope.launch { drawerState.close() }
                            navigator.navigateToChat(conversationId)
                        },
                        // Leave the drawer open (no drawerState.close()) so the user can keep browsing,
                        // unlike onNewChat above.
                        onActiveConversationDelete = {
                            navigator.navigateToTopLevel(NewChat())
                        },
                        onSettingsClick = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(SettingsTabbed)
                        },
                        onAgentsClick = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(AgentMarketplace)
                        },
                        onFilesClick = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(Files)
                        },
                        onSkillsClick = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(SkillsList)
                        },
                        onSchedulesClick = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(SchedulesList)
                        },
                        onBookmarksClick = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(Bookmarks)
                        },
                        onOpenProjectsIndex = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(Projects)
                        },
                        onNewChatInProject = { projectId, projectName ->
                            scope.launch { drawerState.close() }
                            navigator.navigateToNewChatInProject(projectId, projectName)
                        },
                        onOpenSearch = { query ->
                            scope.launch { drawerState.close() }
                            navigator.navigateToSearch(query)
                        },
                        onMessageClick = { conversationId, messageId, query, inReasoning ->
                            scope.launch { drawerState.close() }
                            navigator.navigateToMessage(conversationId, messageId, query, inReasoning)
                        },
                        onSwitchAccount = { accountId ->
                            navHostViewModel.switchAccount(accountId)
                        },
                        onAddAccount = {
                            scope.launch { drawerState.close() }
                            navigator.navigate(AddAccountServerUrl)
                        },
                    )
                }
            }
            if (glass == null) {
                MaterialDrawerSheet(drawerState = drawerState, content = sidebar)
            } else {
                GlassDrawerSheet(
                    drawerState = drawerState,
                    glass = glass,
                    backdrop = drawerBackdrop,
                    onWidthChange = { drawerWidthPx = it },
                    content = sidebar,
                )
            }
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Recorded only while the drawer is opening or open: recording redraws the screen
                // every frame, and nothing samples it while the drawer is shut.
                .glassBackdropSource(drawerBackdrop?.takeIf { drawerShowing }),
        ) {
            if (!navigator.isInAuthFlow) {
                SessionRenewalBlockedBanner(
                    visible = sessionRenewalBlocked,
                    onSignInAgain = navHostViewModel::signInAgain,
                )
                BannerDisplay(
                    banner = banner,
                    onDismiss = navHostViewModel::dismissBanner,
                )
            }
            navDisplay({ scope.launch { drawerState.open() } }, Modifier.fillMaxSize())
        }
    }
    // The drawer is drawn in the Compose canvas, under any native glass bar; hide those bars from
    // the moment it starts opening until it has fully closed. drawerShowing covers a short edge-swipe,
    // where both values still read Closed while the panel is on screen.
    CoversNativeBars(
        active = drawerShowing ||
            drawerState.currentValue == DrawerValue.Open ||
            drawerState.targetValue == DrawerValue.Open,
    )
}

/**
 * The phone drawer's sheet in Material. It ends above the keyboard, as the glass panel does, rather
 * than running on behind it with only its contents padded.
 */
@Composable
internal fun MaterialDrawerSheet(drawerState: DrawerState, content: @Composable () -> Unit) {
    ModalDrawerSheet(drawerState = drawerState, modifier = Modifier.leaveScrimStrip().imePadding()) { content() }
}

/**
 * The phone drawer's sheet in Liquid Glass: a transparent sheet keeps M3's drawer gestures and
 * predictive back, and the panel inside it is the visible sidebar.
 */
@Composable
internal fun GlassDrawerSheet(
    drawerState: DrawerState,
    glass: GlassStyle,
    backdrop: GlassBackdrop?,
    onWidthChange: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    ModalDrawerSheet(
        drawerState = drawerState,
        // A start-edge cutout is padded outside M3's width cap, so the panel isn't narrowed by it; the
        // padding consumes the inset, so GlassSidebarPanel doesn't pad it again.
        modifier = Modifier
            .leaveScrimStrip()
            .onSizeChanged { onWidthChange(it.width) }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
        drawerShape = RectangleShape,
        drawerContainerColor = Color.Transparent,
        drawerTonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
    ) {
        GlassSidebarPanel(style = glass, backdrop = backdrop) { content() }
    }
}

/**
 * Narrows the drawer sheet so at least [ScrimStripWidth] of the screen stays beside it to tap away on,
 * as Material's modal drawer asks. Only a phone narrower than the drawer plus the strip is affected.
 */
private fun Modifier.leaveScrimStrip(): Modifier = layout { measurable, constraints ->
    val maxWidth = if (constraints.hasBoundedWidth) {
        (constraints.maxWidth - ScrimStripWidth.roundToPx()).coerceAtLeast(constraints.minWidth)
    } else {
        constraints.maxWidth
    }
    val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private val ScrimStripWidth = 56.dp

/** [MainNavDisplay] taking `(onMenuClick, modifier)`, as produced by [rememberMovableNavDisplay]. */
typealias NavDisplaySlot = @Composable (onMenuClick: () -> Unit, modifier: Modifier) -> Unit

/**
 * [MainNavDisplay] as movable content. Hand the same instance to both layouts: a layout switch then
 * moves the display instead of disposing it, and disposing it clears every back-stack entry's
 * ViewModels (cancelling an in-flight stream). Pick the layout in the caller's own composition, not
 * in a subcomposition such as BoxWithConstraints; only the single-composition move is tested.
 */
@Composable
fun rememberMovableNavDisplay(navigator: Navigator): NavDisplaySlot = remember(navigator) {
    movableContentOf { onMenuClick: () -> Unit, modifier: Modifier ->
        MainNavDisplay(navigator = navigator, onMenuClick = onMenuClick, modifier = modifier)
    }
}

/** Core NavDisplay with entry providers for all feature modules. Used by both PhoneLayout and TabletLayout. */
@Composable
fun MainNavDisplay(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    navHostViewModel: NavHostViewModel = koinViewModel(),
    drawerViewModel: DrawerViewModel = koinViewModel(),
    serverFileSelectionHandoff: ServerFileSelectionHandoff = koinInject(),
    promptInsertionHandoff: PromptInsertionHandoff = koinInject(),
    chatNoticeHandoff: ChatNoticeHandoff = koinInject(),
) {
    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        modifier = modifier,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) togetherWith
                fadeOut(animationSpec = tween(150))
        },
        popTransitionSpec = { popTransform() },
        predictivePopTransitionSpec = { predictivePopTransform() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberPredictiveBackCardDecorator(navigator.backStack),
        ),
        entryProvider = entryProvider {
            authEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onAuthComplete = {
                    navHostViewModel.onAuthComplete()
                    // accountTransitions() doesn't fire on login-from-logged-out, so the drawer's
                    // conversation list is refreshed explicitly here (the other half — banners,
                    // version — rides onAuthComplete above). Conversations-only: the login session
                    // tasks already refreshed tags, so this must not re-fetch them (double-fetch).
                    drawerViewModel.refreshConversationsAfterLogin()
                    // An add-account completion lands here over a stack whose landing belongs to the
                    // previous account; scoping it to the now-active one discards that ViewModel.
                    navigator.navigateToChat(
                        accountScope = (navHostViewModel.accountState.value as? AccountState.Resolved)?.id?.value,
                    )
                },
            )
            chatEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onRemove = { navigator.remove(it) },
                onNavigateToChat = { conversationId, isTemporary ->
                    navigator.navigateToChat(conversationId, isTemporary)
                },
                onOpenDrawer = onMenuClick,
                onNavigateToProviderKeys = { endpointName ->
                    // From chat: a save there returns to the chat.
                    navigator.navigateToProviderKeys(endpointName, returnAfterSave = true)
                },
                onAttachFromServer = {
                    // Tag the picker with the launching conversation (null on the new-chat
                    // landing) so its selection routes back only to this chat.
                    val launchingId = when (val route = navigator.currentRoute) {
                        is Chat -> route.conversationId
                        is NewChat -> route.handoffKey
                        else -> null
                    }
                    navigator.navigate(FilesPicker(targetConversationId = launchingId))
                },
                onOpenMemories = { navigator.navigate(Memories) },
                onOpenWhatsNew = { navigator.navigate(WhatsNew) },
            )
            conversationsEntries(
                onConversationClick = { navigator.navigateToChat(it) },
                onNavigateToProject = { projectId, projectName ->
                    navigator.navigate(ProjectChats(projectId, projectName))
                },
                onNewChatInProject = { projectId, projectName ->
                    navigator.navigateToNewChatInProject(projectId, projectName)
                },
                onNavigateToBookmark = { tag -> navigator.navigate(BookmarkChats(tag)) },
                onBack = { navigator.goBack() },
                onRemove = { navigator.remove(it) },
                onOpenMessage = { conversationId, messageId, query, inReasoning ->
                    navigator.navigateToMessage(conversationId, messageId, query, inReasoning)
                },
            )
            agentsEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onReplace = { route, replacement -> navigator.replace(route, replacement) },
                onRemove = { navigator.remove(it) },
                onPopWhile = { navigator.popWhile(it) },
                onStartChat = { agentId ->
                    // Carry the agent id into the new chat so it opens on that agent
                    // (Tier-0 override in ModelSelectionDelegate) rather than falling
                    // back to last-used / first-agent / first-model.
                    navigator.navigateToTopLevel(NewChat(agentId))
                },
                // The starter lands in the new chat's composer unsent: staged here, taken by the
                // landing's ChatScreen when it enters composition (a fresh NewChat(agentId) entry,
                // or the existing one re-entering when it already was that agent's landing).
                onStartChatWithPrompt = { agentId, prompt ->
                    promptInsertionHandoff.put(prompt)
                    navigator.navigateToTopLevel(NewChat(agentId))
                },
            )
            skillsEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onReplace = { route, replacement -> navigator.replace(route, replacement) },
            )
            schedulesEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onOpenConversation = { navigator.navigateToChat(it) },
            )
            filesEntries(
                onBack = { navigator.goBack() },
            )
            filePickerEntries(
                onConfirm = { targetConversationId, files ->
                    serverFileSelectionHandoff.publish(targetConversationId, files)
                    navigator.goBack()
                },
                onBack = { navigator.goBack() },
            )
            settingsEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                // Nav is reactive: signing out promotes the most-recent survivor and stays in the app,
                // while the last-account teardown emits session-expired (handled above) to route to
                // auth. Forcing navigateToAuth() here would wrongly leave the auth screen up after a
                // successor was promoted.
                onLogout = { navHostViewModel.logout() },
                onNavigateToArchive = { navigator.navigate(ArchivedConversations) },
                onOpenConversation = { navigator.navigateToChat(it) },
                onProviderKeyReturn = { message ->
                    chatNoticeHandoff.put(message)
                    navigator.goBack()
                },
            )
            memoriesEntry(
                onBack = { navigator.goBack() },
            )
            mcpServersEntry(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
            )
        },
    )
}

private val GlassDrawerScrim = Color.Black.copy(alpha = 0.12f)
