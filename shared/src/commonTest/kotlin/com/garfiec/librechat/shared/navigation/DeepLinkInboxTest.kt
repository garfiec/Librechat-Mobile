package com.garfiec.librechat.shared.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkInboxTest {

    private val chatId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"

    private fun link(host: String, vararg segments: String, query: Map<String, String> = emptyMap()) =
        DeepLinkUri(host = host, pathSegments = segments.toList(), query = query)

    private fun navigatorOn(vararg keys: NavKey): Pair<Navigator, NavBackStack<NavKey>> {
        val backStack = NavBackStack<NavKey>(*keys)
        return Navigator(backStack, onScreenChange = {}) to backStack
    }

    @Test
    fun consume_clearsTheLinkItPlaced() {
        val inbox = DeepLinkInbox()
        val first = link("conversation", chatId)
        inbox.post(first)

        inbox.consume(first)

        assertNull(inbox.pending.value)
    }

    @Test
    fun consume_keepsALinkPostedWhileTheEarlierOneWasBeingPlaced() {
        val inbox = DeepLinkInbox()
        val first = link("conversation", chatId)
        val second = link("model", query = mapOf("endpoint" to "openAI", "model" to "gpt-4o"))
        inbox.post(first)
        inbox.post(second)

        inbox.consume(first)

        assertEquals(second, inbox.pending.value)
    }

    @Test
    fun conversationLink_opensThatChat() {
        val (navigator, _) = navigatorOn(NewChat())

        navigator.placeDeepLink(DeepLinks.resolve(link("conversation", chatId)), isLoggedIn = true, activeAccountId = null)

        assertEquals(chatId, (navigator.currentRoute as Chat).conversationId)
    }

    @Test
    fun modelLink_opensANewChatOnThatModel() {
        val (navigator, _) = navigatorOn(NewChat())

        navigator.placeDeepLink(
            DeepLinks.resolve(link("model", query = mapOf("endpoint" to "openAI", "model" to "gpt-4o"))),
            isLoggedIn = true,
            activeAccountId = null,
        )

        assertEquals(NewChat(endpoint = "openAI", model = "gpt-4o"), navigator.currentRoute)
    }

    @Test
    fun signedOut_aRouteLandsOnSignIn() {
        val (navigator, backStack) = navigatorOn(NewChat())

        navigator.placeDeepLink(DeepLinks.resolve(link("conversation", chatId)), isLoggedIn = false, activeAccountId = null)

        assertEquals(listOf<NavKey>(ServerUrl), backStack.toList())
    }

    @Test
    fun linksWithNothingToPlace_leaveTheStackAlone() {
        val (navigator, backStack) = navigatorOn(NewChat())

        navigator.placeDeepLink(DeepLinks.resolve(link("oauth")), isLoggedIn = true, activeAccountId = null)
        navigator.placeDeepLink(DeepLinks.resolve(link("wat")), isLoggedIn = true, activeAccountId = null)

        assertEquals(listOf<NavKey>(NewChat()), backStack.toList())
    }

    @Test
    fun aNotificationLink_opensOnlyUnderItsOwnAccount() {
        val link = DeepLinks.resolve(link("conversation", chatId, query = mapOf("account" to "server:alice")))

        val (own, _) = navigatorOn(NewChat())
        own.placeDeepLink(link, isLoggedIn = true, activeAccountId = "server:alice")
        assertEquals(chatId, (own.currentRoute as Chat).conversationId)

        val (other, otherStack) = navigatorOn(NewChat())
        other.placeDeepLink(link, isLoggedIn = true, activeAccountId = "server:bob")
        assertEquals(listOf<NavKey>(NewChat()), otherStack.toList())
    }
}
