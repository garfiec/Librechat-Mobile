package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReplyAvatarsTest {

    private val server = "https://chat.example"
    private val configs = mapOf("My Router" to EndpointConfig(providerId = "openrouter"))

    private fun reply(id: String, endpoint: String? = "My Router", iconUrl: String? = null) =
        Message(messageId = id, conversationId = "c", endpoint = endpoint, iconURL = iconUrl)

    private fun node(message: Message) = MessageNode(message = message, children = emptyList(), siblingIndex = 0, siblingCount = 1)

    @Test
    fun a_custom_endpoint_reply_takes_the_served_provider() {
        assertEquals(
            IconSource("My Router", null, ProviderId.OPENROUTER),
            reply("r").replyAvatar(fallbackEndpoint = null, serverUrl = server, endpointConfigs = configs),
        )
    }

    @Test
    fun without_configs_a_custom_endpoint_resolves_by_name_only() {
        assertNull(reply("r").replyAvatar(fallbackEndpoint = null, serverUrl = server, endpointConfigs = null)?.provider)
    }

    @Test
    fun a_relative_spec_icon_joins_the_server() {
        val avatar = reply("r", iconUrl = "/images/coder.png").replyAvatar(null, server, configs)
        assertEquals("$server/images/coder.png", avatar?.imageUrl)
        assertEquals(ProviderId.OPENROUTER, avatar?.provider)
    }

    @Test
    fun an_icon_naming_a_provider_draws_that_provider() {
        assertEquals(ProviderId.GROQ, reply("r", endpoint = "openAI", iconUrl = "groq").replyAvatar(null, server, configs)?.provider)
    }

    @Test
    fun a_reply_with_no_endpoint_takes_the_fallback() {
        val avatar = reply("r", endpoint = null).replyAvatar(fallbackEndpoint = "My Router", serverUrl = server, endpointConfigs = configs)
        assertEquals(ProviderId.OPENROUTER, avatar?.provider)
    }

    @Test
    fun the_users_own_message_has_no_reply_avatar() {
        val mine = reply("u").copy(isCreatedByUser = true)
        assertNull(mine.replyAvatar(null, server, configs))
    }

    @Test
    fun configs_arriving_later_update_the_mapped_nodes() {
        val path = listOf(node(reply("r")))
        val before = path.withReplyAvatars(null, server, endpointConfigs = emptyMap())
        val after = before.withReplyAvatars(null, server, configs)
        assertNull(before[0].avatar?.provider)
        assertEquals(ProviderId.OPENROUTER, after[0].avatar?.provider)
    }

    @Test
    fun an_already_mapped_path_comes_back_as_the_same_list() {
        val mapped = listOf(node(reply("r"))).withReplyAvatars(null, server, configs)
        assertSame(mapped, mapped.withReplyAvatars(null, server, configs))
    }

    @Test
    fun the_memo_skips_a_repeat_and_redoes_a_changed_input() {
        val memo = ReplyAvatarMemo()
        val path = listOf(node(reply("r")))
        val first = memo.resolve(path, null, server, configs)
        assertSame(first, memo.resolve(path, null, server, configs))

        val newConfigs = mapOf("My Router" to EndpointConfig(providerId = "groq"))
        assertEquals(ProviderId.GROQ, memo.resolve(path, null, server, newConfigs)[0].avatar?.provider)
    }
}
