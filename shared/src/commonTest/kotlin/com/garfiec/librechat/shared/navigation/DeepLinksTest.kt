package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.notifications.conversationLink
import com.garfiec.librechat.core.notifications.schedulesLink
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.schedules.navigation.SchedulesList
import com.garfiec.librechat.feature.settings.navigation.CodeSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class DeepLinksTest {

    private fun uri(host: String, path: String? = null, query: Map<String, String> = emptyMap()) =
        DeepLinkUri(host = host, pathSegments = listOfNotNull(path), query = query)

    @Test
    fun conversationValidUuid_routesToChat() {
        // conversationId is a server-generated uuidv4, not a Mongo ObjectID.
        val id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        val resolution = DeepLinks.resolve(uri("conversation", id))
        val route = assertIs<DeepLinkResolution.Route>(resolution)
        assertEquals(id, assertIs<Chat>(route.target).conversationId)
    }

    @Test
    fun conversationWithMessage_opensAtThatMessage() {
        val id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        val message = "0f1e2d3c-4b5a-4968-8776-655443322110"
        val route = assertIs<DeepLinkResolution.Route>(
            DeepLinks.resolve(uri("conversation", id, mapOf("message" to message))),
        )
        val chat = assertIs<Chat>(route.target)
        assertEquals(id, chat.conversationId)
        assertEquals(message, chat.targetMessageId)
    }

    @Test
    fun conversationWithMalformedMessage_opensTheChatOnly() {
        val id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        val route = assertIs<DeepLinkResolution.Route>(
            DeepLinks.resolve(uri("conversation", id, mapOf("message" to "x"))),
        )
        assertEquals(null, assertIs<Chat>(route.target).targetMessageId)
    }

    @Test
    fun modelShortcut_carriesItsSpecWhenItHasOne() {
        val withSpec = assertIs<DeepLinkResolution.Route>(
            DeepLinks.resolve(uri("model", query = mapOf("endpoint" to "openAI", "model" to "gpt-4o", "spec" to "fast"))),
        )
        assertEquals(NewChat(endpoint = "openAI", model = "gpt-4o", spec = "fast"), withSpec.target)

        val raw = assertIs<DeepLinkResolution.Route>(
            DeepLinks.resolve(uri("model", query = mapOf("endpoint" to "openAI", "model" to "gpt-4o", "spec" to ""))),
        )
        assertEquals(NewChat(endpoint = "openAI", model = "gpt-4o"), raw.target)
    }

    @Test
    fun conversationMalformedId_isNotRouted() {
        assertEquals(DeepLinkResolution.None, DeepLinks.resolve(uri("conversation", "not-a-uuid")))
        // A 24-hex Mongo ObjectID is NOT a conversationId — must be rejected (regression lock).
        assertEquals(DeepLinkResolution.None, DeepLinks.resolve(uri("conversation", "0123456789abcdef01234567")))
        assertEquals(DeepLinkResolution.None, DeepLinks.resolve(uri("conversation")))
    }

    @Test
    fun artifactValidUuid_opensAsItsOwnApp() {
        val id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
        assertEquals(DeepLinkResolution.ArtifactApp(id), DeepLinks.resolve(uri("artifact", id)))
    }

    @Test
    fun artifactMalformedId_isNotRouted() {
        assertEquals(DeepLinkResolution.None, DeepLinks.resolve(uri("artifact", "12345")))
    }

    @Test
    fun oauth_isConsumedNotNavigated() {
        assertEquals(DeepLinkResolution.Consumed, DeepLinks.resolve(uri("oauth", query = mapOf("code" to "x"))))
    }

    @Test
    fun unknownHost_isNotRouted() {
        assertEquals(DeepLinkResolution.None, DeepLinks.resolve(uri("wat", "whatever")))
    }

    @Test
    fun nullHost_isNotRouted() {
        // The shape an opaque "librechat:foo" URI adapts to (host null, no segments, empty query).
        assertEquals(
            DeepLinkResolution.None,
            DeepLinks.resolve(DeepLinkUri(host = null, pathSegments = emptyList(), query = emptyMap())),
        )
    }

    @Test
    fun codeSetup_routesAValidAttempt() {
        val id = "a".repeat(42) + "-"
        assertEquals(
            DeepLinkResolution.Route(CodeSetup(id)),
            DeepLinks.resolve(uri("code-setup", id)),
        )
        assertEquals(DeepLinkResolution.None, DeepLinks.resolve(uri("code-setup", "too-short")))
    }

    @Test
    fun parse_readsHostSegmentsAndDecodedQuery() {
        val id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        assertEquals(
            DeepLinkUri(host = "conversation", pathSegments = listOf(id), query = mapOf("message" to "m")),
            DeepLinkUri.parse("librechat://conversation/$id?message=m"),
        )
        // Model ids carry ':' and '/'; a spec name can carry spaces.
        assertEquals(
            DeepLinkUri(
                host = "model",
                pathSegments = emptyList(),
                query = mapOf("endpoint" to "Open Router", "model" to "meta/llama:70b", "spec" to "my spec"),
            ),
            DeepLinkUri.parse("librechat://model?endpoint=Open%20Router&model=meta%2Fllama%3A70b&spec=my%20spec"),
        )
    }

    @Test
    fun parse_acceptsTheSchemeInAnyCase() {
        // iOS matches URL schemes case-insensitively, so it can hand over either spelling.
        assertEquals("conversation", DeepLinkUri.parse("LibreChat://conversation/x")?.host)
    }

    @Test
    fun parse_refusesOpaqueOtherSchemesAndGarbage() {
        assertEquals(null, DeepLinkUri.parse("librechat:conversation/x"))
        assertEquals(null, DeepLinkUri.parse("https://conversation/x"))
        assertEquals(null, DeepLinkUri.parse("not a url"))
        assertEquals(null, DeepLinkUri.parse(""))
    }

    @Test
    fun parsedConversationLink_routesToThatChat() {
        val id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        val link = assertNotNull(DeepLinkUri.parse("librechat://conversation/$id"))
        val route = assertIs<DeepLinkResolution.Route>(DeepLinks.resolve(link))
        assertEquals(id, assertIs<Chat>(route.target).conversationId)
    }

    @Test
    fun notificationLink_roundTripsWithItsAccount() {
        // The link :core:notifications builds, read back the way iOS and Android hand it over.
        val id = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        val account = AccountId("3f1c9a:user/42 x")
        val parsed = assertNotNull(DeepLinkUri.parse(conversationLink(id, account)))
        val route = assertIs<DeepLinkResolution.Route>(DeepLinks.resolve(parsed))
        assertEquals(id, assertIs<Chat>(route.target).conversationId)
        assertEquals(account.value, route.forAccount)
    }

    @Test
    fun conversationLinkWithoutAccount_isForWhicheverAccountIsActive() {
        val route = assertIs<DeepLinkResolution.Route>(
            DeepLinks.resolve(uri("conversation", "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d")),
        )
        assertEquals(null, route.forAccount)
    }

    @Test
    fun schedulesLinkFromANotification_opensTheSchedulesListForItsAccount() {
        val account = AccountId("3f1c9a:user/42 x")
        val parsed = assertNotNull(DeepLinkUri.parse(schedulesLink(account)))
        val route = assertIs<DeepLinkResolution.Route>(DeepLinks.resolve(parsed))
        assertEquals(SchedulesList, route.target)
        assertEquals(account.value, route.forAccount)
    }
}
