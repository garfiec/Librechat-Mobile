package com.garfiec.librechat.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.chat.ShareIntentConsumer
import com.garfiec.librechat.feature.chat.SharedContent
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.chat.navigation.isPlainLanding
import com.garfiec.librechat.shared.navigation.DeepLinkResolution
import com.garfiec.librechat.shared.navigation.DeepLinks
import com.garfiec.librechat.shared.navigation.NavHostViewModel
import com.garfiec.librechat.shared.navigation.Navigator
import com.garfiec.librechat.shared.navigation.PhoneLayout
import com.garfiec.librechat.shared.navigation.SharedLinkRoute
import com.garfiec.librechat.shared.navigation.TabletLayout
import com.garfiec.librechat.shared.navigation.usesSidebarLayout
import com.garfiec.librechat.shared.navigation.LibreChatNavHost as SharedLibreChatNavHost

/**
 * Android-specific entry point that wraps the shared [SharedLibreChatNavHost]
 * with deep link handling, share intent support, and tablet layout branching.
 */
@Composable
fun LibreChatNavHost(
    modifier: Modifier = Modifier,
    deepLinkUri: Uri? = null,
    onDeepLinkConsume: () -> Unit = {},
    /** A `/share/` link handed to the app, waiting to be routed; see [routeSharedInLink]. */
    sharedInLink: SharedInLink? = null,
    onSharedLinkConsume: () -> Unit = {},
    /** Opens a link in a browser, never back in this app. */
    openInBrowser: (String) -> Unit = {},
    appLocaleTag: String? = null,
) {
    // Resolve the pending link once (both the auth-suppression flag and the handler below use it).
    val resolution = remember(deepLinkUri) { deepLinkUri?.let { DeepLinks.resolve(it.toDeepLinkUri()) } }

    SharedLibreChatNavHost(
        modifier = modifier,
        appLocaleTag = appLocaleTag,
        // Cold-start deep links are set before setContent, so this is true on the first composition
        // when a link is pending that is *valid without auth* — the shared host then skips its initial
        // auth redirect and lets the handler below own the stack (so a logged-out target isn't wiped by
        // an auth clear). Auth-required and non-routable links must NOT suppress it: the former should
        // land on login, the latter would strand a logged-out user on an un-authed screen.
        hasPendingDeepLink = resolution is DeepLinkResolution.Route && !resolution.requiresAuth,
    ) { navigator, navHostViewModel, mod ->
        // Handle deep links
        val currentOnDeepLinkConsume by rememberUpdatedState(onDeepLinkConsume)
        LaunchedEffect(deepLinkUri) {
            val uri = deepLinkUri ?: return@LaunchedEffect
            when (resolution) {
                is DeepLinkResolution.Route -> {
                    val target = resolution.target
                    val loggedIn = navHostViewModel.isLoggedIn.value
                    when {
                        // Auth-required target while logged out → login. (Resuming to the target
                        // after login is a follow-up; this at least never lands on a broken screen.)
                        resolution.requiresAuth && !loggedIn -> navigator.navigateToAuth()
                        // Open-logged-out target (device-scoped artifact) sits atop an auth base so
                        // backing out lands on login; rebuilds the stack so repeat taps can't pile up.
                        !loggedIn -> navigator.navigateToDeepLinkLoggedOut(target)
                        // Switching chats replaces the current chat entry so back returns to NewChat.
                        target is Chat -> target.conversationId?.let { id ->
                            val messageId = target.targetMessageId
                            if (messageId != null) {
                                navigator.navigateToMessage(id, messageId, query = "")
                            } else {
                                navigator.navigateToChat(id)
                            }
                        }
                        // Model shortcut: NewChat carries an endpoint/model payload. Go through top-level
                        // nav so it replaces a bare landing NewChat (dedup-by-value) and re-seeds even
                        // when already on the landing.
                        target is NewChat -> navigator.navigateToTopLevel(target)
                        // Otherwise push, de-duping a repeat tap of the same target.
                        navigator.currentRoute != target -> navigator.navigate(target)
                    }
                }
                // OAuth redirect: the login screen consumes the refresh-token cookie; nothing to route.
                DeepLinkResolution.Consumed, null -> Unit
                DeepLinkResolution.None -> Logger.w { "Ignoring non-routable deep link: $uri" }
            }
            currentOnDeepLinkConsume()
        }

        // A `/share/` link shared in or opened with the app: the viewer when a signed-in account is
        // on its server (after a switch the user agrees to, for another account); otherwise shared
        // text goes to the composer and an opened link to the browser.
        val currentOnSharedLinkConsume by rememberUpdatedState(onSharedLinkConsume)
        val currentOpenInBrowser by rememberUpdatedState(openInBrowser)
        LaunchedEffect(sharedInLink) {
            val link = sharedInLink ?: return@LaunchedEffect
            routeSharedInLink(link, navigator, navHostViewModel, currentOpenInBrowser)
            currentOnSharedLinkConsume()
        }

        // Handle share intent. This is the only place that knows both that a share is waiting and
        // which chat is on screen, so it owns addressing it: the share is delivered to exactly that
        // chat's composer instead of being offered to every live ChatViewModel, where the landing
        // one sitting in the back stack would often claim it first. Driven off the consumer's own
        // state rather than an activity-held trigger so a recreation mid-warm-up can't strand it.
        val pendingShare by ShareIntentConsumer.undelivered.collectAsStateWithLifecycle()
        LaunchedEffect(pendingShare) {
            if (pendingShare == null) return@LaunchedEffect
            val currentRoute = navigator.currentRoute
            val target = when (currentRoute) {
                is Chat -> currentRoute.conversationId
                // A share never lands in a project's landing: it would be filed there unasked.
                is NewChat -> {
                    if (!currentRoute.isPlainLanding) navigator.navigateToTopLevel(NewChat())
                    null
                }
                // Not on a chat at all — open the landing and address the share to it. It has not
                // composed yet; the consumer holds the share for its first collector.
                else -> {
                    navigator.navigateToTopLevel(NewChat())
                    null
                }
            }
            ShareIntentConsumer.dispatchTo(target)
        }

        val window = LocalWindowInfo.current.containerSize
        val usesSidebar = with(LocalDensity.current) { usesSidebarLayout(window.width.toDp(), window.height.toDp()) }
        if (usesSidebar) {
            TabletLayout(
                navigator = navigator,
                navHostViewModel = navHostViewModel,
                modifier = mod,
            )
        } else {
            PhoneLayout(
                navigator = navigator,
                modifier = mod,
            )
        }
    }
}

/**
 * A `/share/` link handed to the app from outside: [text] shared to it (ACTION_SEND), or a link
 * opened with it ([openedWith], ACTION_VIEW from the "Open with" filter).
 */
data class SharedInLink(val text: String, val openedWith: Boolean)

/**
 * Routes [link]: the in-app viewer on the active account, an offer to switch for another signed-in
 * account's link. A link no signed-in account can open goes where it would have gone without the
 * app: shared text to the composer, as any other shared text; an opened link to the browser.
 */
private suspend fun routeSharedInLink(
    link: SharedInLink,
    navigator: Navigator,
    navHostViewModel: NavHostViewModel,
    openInBrowser: (String) -> Unit,
) {
    val url = link.text.trim()
    when (val route = navHostViewModel.routeSharedLink(url)) {
        is SharedLinkRoute.Open -> navigator.navigateToSharedChat(route.shareId)
        is SharedLinkRoute.OfferSwitch -> navHostViewModel.offerSharedLinkSwitch(route)
        SharedLinkRoute.NotOurs -> if (link.openedWith) {
            openInBrowser(url)
        } else {
            ShareIntentConsumer.setPendingShare(SharedContent(text = link.text))
        }
    }
}
