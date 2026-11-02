package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.Conversation
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** v0.8.9-rc1's rename handler names its refusals (upstream `conversations/rename.ts`). */
class RenameRefusalTest {

    @Test
    fun `the rolling-upgrade 409 reads as not ready`() {
        val e =
            ApiException(statusCode = 409, message = "x", body = """{"error":"conversation_title_ownership_not_ready"}""")
        assertThat(renameRefusalOf(e)).isEqualTo(RenameRefusal.TITLE_NOT_READY)
    }

    @Test
    fun `a missing conversation reads as not found`() {
        val e = ApiException(statusCode = 404, message = "x", body = """{"error":"conversation_not_found"}""")
        assertThat(renameRefusalOf(e)).isEqualTo(RenameRefusal.NOT_FOUND)
    }

    @Test
    fun `any other failure is not a named refusal`() {
        assertThat(renameRefusalOf(ApiException(statusCode = 500, message = "x", body = "Error updating conversation"))).isNull()
        assertThat(renameRefusalOf(IllegalStateException())).isNull()
    }

    @Test
    fun `the conversation decodes its title ownership`() {
        val json = Json { ignoreUnknownKeys = true }
        val convo = json.decodeFromString(Conversation.serializer(), """{"conversationId":"c","titleSetByUser":true,"titleRevision":3}""")
        assertThat(convo.titleSetByUser).isTrue()
        assertThat(convo.titleRevision).isEqualTo(3)
    }
}
