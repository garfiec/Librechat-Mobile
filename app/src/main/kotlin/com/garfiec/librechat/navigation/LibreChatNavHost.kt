package com.garfiec.librechat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.ShareIntentConsumer
import com.garfiec.librechat.feature.chat.SharedContent
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.chat.navigation.isPlainLanding
import com.garfiec.librechat.shared.navigation.NavHostViewModel
import com.garfiec.librechat.shared.navigation.Navigator
import com.garfiec.librechat.shared.navigation.SharedLinkRoute
import com.garfiec.librechat.shared.navigation.WindowLayout
import com.garfiec.librechat.shared.navigation.LibreChatNavHost as SharedLibreChatNavHost

/**
 * Android-specific entry point that wraps the shared [SharedLibreChatNavHost]
 * with share intent support; the layout is [WindowLayout], as on iOS. Deep links are placed by the shared host.
 */
@Composable
fun LibreChatNavHost(
    modifier: Modifier = Modifier,
    /** Shared-in text that is exactly one `/share/` link; see [routeSharedInLink]. */
    sharedLinkText: String? = null,
    onSharedLinkConsume: () -> Unit = {},
    appLocaleTag: String? = null,
) {
    SharedLibreChatNavHost(
        modifier = modifier,
        appLocaleTag = appLocaleTag,
    ) { navigator, navHostViewModel, mod ->
        val currentOnSharedLinkConsume by rememberUpdatedState(onSharedLinkConsume)
        LaunchedEffect(sharedLinkText) {
            val text = sharedLinkText ?: return@LaunchedEffect
            routeSharedInLink(text, navigator, navHostViewModel)
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

        WindowLayout(navigator = navigator, modifier = mod)
    }
}

/**
 * Routes [text], a shared-in `/share/` link: the in-app viewer on the active account, an offer to
 * switch for another signed-in account's link, and the composer (as any other shared text) for a
 * link no signed-in account can open.
 */
private suspend fun routeSharedInLink(text: String, navigator: Navigator, navHostViewModel: NavHostViewModel) {
    when (val route = navHostViewModel.routeSharedLink(text.trim())) {
        is SharedLinkRoute.Open -> navigator.navigateToSharedChat(route.shareId)
        is SharedLinkRoute.OfferSwitch -> navHostViewModel.offerSharedLinkSwitch(route)
        SharedLinkRoute.NotOurs -> ShareIntentConsumer.setPendingShare(SharedContent(text = text))
    }
}
