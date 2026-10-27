package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Instant

class DrawerDisplayMappingTest {

    @Test
    fun specLabelIsDisplayedWithoutChangingTheRawModel() {
        val convo = Conversation(conversationId = "c1", model = "internal-model", spec = "second")
        val specs = ModelSpecs(list = listOf(
            ModelSpec(name = "first", label = "First Assistant"),
            ModelSpec(name = "second", label = "Second Assistant"),
        ))

        val data = convo.toDrawerDisplayData(null, emptyMap(), "", specs)

        assertThat(data.modelLabel).isEqualTo("Second Assistant")
        assertThat(data.model).isEqualTo("internal-model")
    }

    @Test
    fun blankSpecLabelFallsBackToSpecName() {
        val convo = Conversation(conversationId = "c1", model = "internal-model", spec = "curated-spec")
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "curated-spec", label = "   ")))

        assertThat(convo.toDrawerDisplayData(null, emptyMap(), "", specs).modelLabel).isEqualTo("curated-spec")
    }

    @Test
    fun missingOrUnknownSpecPreservesTheRawModelLabel() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "other", label = "Other Assistant")))
        for (spec in listOf(null, "unknown")) {
            val convo = Conversation(conversationId = "c1", model = "raw-model", spec = spec)
            assertThat(convo.toDrawerDisplayData(null, emptyMap(), "", specs).modelLabel).isEqualTo("raw-model")
        }
    }

    @Test
    fun iconURLPrefersConversationOverEndpointConfig() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = "https://convo-icon.example.com/icon.png",
        )
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(iconURL = "https://config-icon.example.com/icon.png"),
        )

        val data = convo.toDrawerDisplayData(activeConversationId = null, endpointConfigs = configs, serverUrl = "")

        assertThat(data.endpointIconUrl).isEqualTo("https://convo-icon.example.com/icon.png")
    }

    @Test
    fun iconURLFallsBackToEndpointConfig() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = null,
        )
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(iconURL = "https://config-icon.example.com/icon.png"),
        )

        val data = convo.toDrawerDisplayData(activeConversationId = null, endpointConfigs = configs, serverUrl = "")

        assertThat(data.endpointIconUrl).isEqualTo("https://config-icon.example.com/icon.png")
    }

    @Test
    fun iconURLNullWhenNeitherSet() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = null,
        )
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(iconURL = null),
        )

        val data = convo.toDrawerDisplayData(activeConversationId = null, endpointConfigs = configs, serverUrl = "")

        assertThat(data.endpointIconUrl).isNull()
    }

    @Test
    fun iconURLNullWhenEndpointMissingFromConfig() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = null,
        )

        val data = convo.toDrawerDisplayData(
            activeConversationId = null,
            endpointConfigs = emptyMap(),
            serverUrl = "",
        )

        assertThat(data.endpointIconUrl).isNull()
    }

    /**
     * The mapping carries the timestamp through untouched — it must NOT format it. Formatting is
     * clock-dependent, so doing it here would freeze the label at whenever the mapping last ran —
     * the stale-"Just now" bug. (Malformed wire timestamps are handled upstream, at
     * deserialization: see LenientInstantSerializerTest.)
     */
    @Test
    fun updatedAtIsCopiedNotFormatted() {
        val convo = Conversation(
            conversationId = "c1",
            updatedAt = Instant.parse("2026-07-19T12:00:00Z"),
        )

        val data = convo.toDrawerDisplayData(activeConversationId = null, endpointConfigs = emptyMap(), serverUrl = "")

        assertThat(data.updatedAt).isEqualTo(Instant.parse("2026-07-19T12:00:00Z"))
    }

    @Test
    fun updatedAtNullSurvivesMapping() {
        val convo = Conversation(conversationId = "c1", updatedAt = null)

        val data = convo.toDrawerDisplayData(activeConversationId = null, endpointConfigs = emptyMap(), serverUrl = "")

        assertThat(data.updatedAt).isNull()
    }

    @Test
    fun aRelativeIconJoinsTheServer() {
        val convo = Conversation(conversationId = "c1", endpoint = "openAI", iconURL = "/images/spec.png")

        val data = convo.toDrawerDisplayData(activeConversationId = null, endpointConfigs = emptyMap(), serverUrl = "https://srv")

        assertThat(data.endpointIconUrl).isEqualTo("https://srv/images/spec.png")
        assertThat(data.iconEndpoint).isEqualTo("openAI")
    }
}
