package com.garfiec.librechat.shared.navigation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.auth.navigation.AuthRoute
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.auth.navigation.isAddAccountFlowRoute
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.chat.navigation.isPlainLanding
import com.garfiec.librechat.feature.conversations.navigation.Search
import com.garfiec.librechat.feature.settings.navigation.ProviderKeys

/**
 * Encapsulates all back stack mutations for Nav 3 navigation.
 * Follows UDF: UI events → Navigator → back stack state → NavDisplay recomposition.
 */
class Navigator(val backStack: NavBackStack<NavKey>) {

    val currentRoute: NavKey? get() = backStack.lastOrNull()

    val isInAuthFlow: Boolean get() = currentRoute is AuthRoute

    /** Navigate forward to a route. */
    fun navigate(route: NavKey) {
        backStack.add(route)
    }

    /** Pop the top entry. Safe on empty stacks. */
    fun goBack() {
        backStack.removeLastOrNull()
    }

    /**
     * Removes every copy of [route] wherever it sits, for a screen that closes itself. Unlike [goBack]
     * it can't pop a different screen when [route] isn't on top, e.g. while predictive back draws it
     * underneath. Equal keys share one ViewModel, so a copy left behind would be just as closed.
     * Never empties the stack.
     */
    fun remove(route: NavKey) {
        if (backStack.any { it != route }) backStack.removeAll { it == route }
    }

    /** Swap the top entry for [route], so back skips the screen being left. */
    fun replaceTop(route: NavKey) {
        backStack.removeLastOrNull()
        backStack.add(route)
    }

    /** Pop while [predicate] holds for the top entry. Never pops the last one. */
    fun popWhile(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && predicate(backStack.last())) {
            backStack.removeLastOrNull()
        }
    }

    /**
     * Handles the Search shortcut (Ctrl+Shift+F, or ⌘+Shift+F on a Mac keyboard). Returns whether the
     * event was consumed. Not offered while signing in: there is no account to search.
     */
    fun openSearchOnShortcut(event: KeyEvent): Boolean {
        val shortcut = event.type == KeyEventType.KeyDown && event.key == Key.F && event.isShiftPressed &&
            (event.isCtrlPressed || event.isMetaPressed)
        return shortcut && openSearchFromShortcut()
    }

    /** The Search shortcut, from a platform host that matched the key itself (see [AppShortcuts]). */
    fun openSearchFromShortcut(): Boolean {
        if (isInAuthFlow) return false
        // Already on Search: keep what's typed there rather than replacing it with an empty screen.
        if (backStack.lastOrNull() !is Search) navigateToSearch("")
        return true
    }

    /** Open the Search screen with [query]; an open Search screen is replaced rather than stacked. */
    fun navigateToSearch(query: String) {
        if (backStack.lastOrNull() is Search) backStack.removeLastOrNull()
        backStack.add(Search(query))
    }

    /** Navigate to a chat, replacing any current chat on the stack. [isTemporary] marks a
     *  temporary chat so a restored Chat(id) entry stays temp-aware and never persists
     *  the server-hidden conversation to Room — see [Chat.isTemporary]. A project-scoped landing
     *  is replaced too, so Back from the chat it started returns to the project. */
    fun navigateToChat(conversationId: String, isTemporary: Boolean = false) {
        val top = backStack.lastOrNull()
        if (top is Chat || (top is NewChat && !top.isPlainLanding)) {
            backStack.removeLastOrNull()
        }
        backStack.add(Chat(conversationId, isTemporary))
    }

    /**
     * A new chat filed into [projectId], pushed over the current screen so Back returns there (the
     * project page, say). A chat or another project's landing on top is replaced, as [navigateToChat]
     * replaces one, so chats never stack; the same project's landing is kept.
     */
    fun navigateToNewChatInProject(projectId: String, projectName: String) {
        val top = backStack.lastOrNull()
        if (top is NewChat && top.chatProjectId == projectId) return
        if (top is Chat || (top is NewChat && !top.isPlainLanding)) backStack.removeLastOrNull()
        // The same project's landing further down would share this one's key, and with it its
        // ViewModel: move it up rather than holding two.
        backStack.removeAll { it is NewChat && it.chatProjectId == projectId }
        backStack.add(NewChat(chatProjectId = projectId, chatProjectName = projectName))
    }

    /**
     * Opens [conversationId] at [messageId] with [query] highlighted (a search hit), at a match in the
     * reasoning when [inReasoning]. Replaces a chat on top like [navigateToChat]; a Search screen
     * beneath stays, so back returns to the results.
     */
    fun navigateToMessage(conversationId: String, messageId: String, query: String, inReasoning: Boolean = false) {
        // Replaces what navigateToChat replaces, so both ways of opening a chat leave one back stack.
        val top = backStack.lastOrNull()
        if (top is Chat || (top is NewChat && !top.isPlainLanding)) {
            backStack.removeLastOrNull()
        }
        backStack.add(
            Chat(
                conversationId,
                targetMessageId = messageId,
                highlightQuery = query.ifBlank { null },
                targetInReasoning = inReasoning,
            ),
        )
    }

    /**
     * Navigate to Provider API Keys with optional auto-open dialog. Dedupes when the
     * top of the stack is already [ProviderKeys]: no-op when the pending endpoint is
     * identical, otherwise replace so the dialog refreshes for a different endpoint.
     */
    fun navigateToProviderKeys(endpointName: String?) {
        val top = backStack.lastOrNull()
        if (top is ProviderKeys) {
            if (top.pendingDialogEndpoint == endpointName) return
            backStack.removeLastOrNull()
        }
        backStack.add(ProviderKeys(pendingDialogEndpoint = endpointName))
    }

    /** Navigate to a top-level destination, popping to root first. Dedupes by VALUE
     *  (not class) so a payload-carrying route replaces a same-class root that differs:
     *  e.g. NewChat("agent_X") must replace the bare landing NewChat() so the agent id
     *  actually reaches the chat — a class-only check silently dropped it. Re-selecting
     *  an identical route (data-object tabs, or NewChat()→NewChat()) still no-ops. */
    fun navigateToTopLevel(route: NavKey) {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        if (!backStack.lastOrNull().isSameTopLevel(route)) {
            backStack.removeLastOrNull()
            backStack.add(route)
        }
    }

    /**
     * Value equality, except that an UNSCOPED [NewChat] matches the landing on top whatever account
     * it is scoped to. Every New chat caller passes a bare NewChat(), while the landing an account
     * flip leaves is scoped ([NewChat.accountScope]); read as different routes, a New chat tap right
     * after a flip replaced the landing — a new ViewModel, and the model just picked was lost. The
     * scope only exists to make the FLIP a different entry ([navigateToChat]); within one account
     * the landing is the landing. A payload (agent, endpoint, model) still differs and replaces it.
     */
    private fun NavKey?.isSameTopLevel(route: NavKey): Boolean {
        if (this == route) return true
        return this is NewChat && route is NewChat && route.accountScope == null &&
            copy(accountScope = null) == route
    }

    /**
     * Clear back stack and navigate to auth (session expiry / logout).
     *
     * No-ops when already in the auth flow, matching the dedupe its siblings above do. A dead session
     * is reported by more than one caller (a cold start fans out several requests and each 401 settles
     * independently), and a straggler landing after the user has moved on to Login/Register/2FA would
     * otherwise reset them to [ServerUrl] and discard what they had typed.
     */
    fun navigateToAuth() {
        // An add-account flow is not "already at the destination": it belongs to the session that
        // just ended, and the nav host cancels the pending add by watching those routes leave the
        // stack — so skipping the reset there would strand the flow with a dead parent session.
        if (isInAuthFlow && backStack.none { it.isAddAccountFlowRoute }) return
        backStack.clear()
        backStack.add(ServerUrl)
    }

    /**
     * Cold-start deep link handled while logged out: show [route] atop an auth base, so the target
     * (e.g. a device-scoped artifact shortcut, viewable logged out) is reached deterministically and
     * backing out of it lands on login rather than a half-initialized logged-out screen. The shared
     * host's initial auth redirect is skipped when a deep link is pending, so this owns that setup.
     */
    fun navigateToDeepLinkLoggedOut(route: NavKey) {
        backStack.clear()
        backStack.add(ServerUrl)
        backStack.add(route)
    }

    /**
     * Clear back stack and navigate to chat (auth complete, account flip). [accountScope] is the now
     * active account: it makes the landing a different entry from the one the stack held before, so
     * a flip discards the outgoing account's retained landing ViewModel instead of reusing it. See
     * [NewChat.accountScope].
     */
    fun navigateToChat(accountScope: String? = null) {
        backStack.clear()
        backStack.add(NewChat(accountScope = accountScope))
    }
}
