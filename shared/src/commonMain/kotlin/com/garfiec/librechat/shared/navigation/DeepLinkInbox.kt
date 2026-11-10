package com.garfiec.librechat.shared.navigation

import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one hand-off of an incoming link to the nav host, on both platforms: Android's intent intake and
 * iOS's URL and quick-action intake [post] here, and the shared `LibreChatNavHost` places the link and
 * [consume]s it. A process singleton, so a link posted before the host composes (cold start, the
 * theme/locale warm-up) or across an activity recreation waits for it.
 */
class DeepLinkInbox {
    private val _pending = MutableStateFlow<DeepLinkUri?>(null)
    val pending: StateFlow<DeepLinkUri?> = _pending.asStateFlow()

    fun post(uri: DeepLinkUri) {
        _pending.value = uri
    }

    /** Clears [uri] only if it is still the pending link, so one posted while it was being placed survives. */
    fun consume(uri: DeepLinkUri) {
        _pending.compareAndSet(uri, null)
    }
}

/**
 * Places a resolved link on the back stack; signed out, a route lands on sign-in instead. A route for
 * an account other than [activeAccountId] is dropped: it would open under the wrong account.
 */
internal fun Navigator.placeDeepLink(resolution: DeepLinkResolution, isLoggedIn: Boolean, activeAccountId: String?) {
    when (resolution) {
        is DeepLinkResolution.Route -> {
            val target = resolution.target
            when {
                // Resuming to the target after sign-in is not done; this at least never lands on a dead screen.
                !isLoggedIn -> navigateToAuth()
                resolution.forAccount != null && resolution.forAccount != activeAccountId ->
                    Logger.w { "Ignoring a link for an account that isn't active" }
                // Switching chats replaces the current chat entry so back returns to NewChat.
                target is Chat -> target.conversationId?.let { id ->
                    val messageId = target.targetMessageId
                    if (messageId != null) navigateToMessage(id, messageId, query = "") else navigateToChat(id)
                }
                // Model shortcut: top-level nav replaces a bare landing NewChat (dedup-by-value) and
                // re-seeds even when already on the landing.
                target is NewChat -> navigateToTopLevel(target)
                // Otherwise push, de-duping a repeat tap of the same target.
                currentRoute != target -> navigate(target)
            }
        }
        // Pinned artifacts open outside the nav host (Android's ArtifactAppActivity); the OAuth
        // redirect is consumed by sign-in. Neither has anything to place.
        is DeepLinkResolution.ArtifactApp, DeepLinkResolution.Consumed -> Unit
        DeepLinkResolution.None -> Logger.w { "Ignoring non-routable deep link" }
    }
}
