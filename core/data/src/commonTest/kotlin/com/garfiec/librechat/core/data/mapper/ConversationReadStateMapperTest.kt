package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.dao.withReadStateOf
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.UNSEEN_REPLY_WATERMARK
import com.garfiec.librechat.core.model.isFinishedUnseen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ConversationReadStateMapperTest {

    private val reply = Instant.parse("2026-10-30T10:00:00Z")
    private val stamped = Conversation(
        conversationId = "c1",
        lastResponseAt = reply,
        lastResponseMessageId = "m1",
        isMarkedUnread = false,
        lastSeenAt = UNSEEN_REPLY_WATERMARK,
    )

    @Test
    fun readStateRoundTripsThroughTheEntity() {
        val restored = stamped.toEntity().toModel()
        assertEquals(reply, restored.lastResponseAt)
        assertEquals("m1", restored.lastResponseMessageId)
        assertEquals(false, restored.isMarkedUnread)
        assertEquals(UNSEEN_REPLY_WATERMARK, restored.lastSeenAt)
        assertTrue(restored.isFinishedUnseen)
    }

    @Test
    fun aPayloadWithoutReadStateKeepsTheCachedOne() {
        val cached = stamped.toEntity()
        val incoming = Conversation(conversationId = "c1", title = "Renamed").toEntity()
        val merged = incoming.withReadStateOf(cached)
        assertEquals("Renamed", merged.title)
        assertEquals(reply.toEpochMilliseconds(), merged.lastResponseAt)
        assertEquals(0L, merged.lastSeenAt)
        assertEquals(false, merged.isMarkedUnread)
    }

    @Test
    fun aPayloadWithReadStateReplacesTheCachedOne() {
        val cached = stamped.toEntity()
        val seen = stamped.copy(lastSeenAt = reply, isMarkedUnread = null).toEntity()
        val merged = seen.withReadStateOf(cached)
        assertEquals(reply.toEpochMilliseconds(), merged.lastSeenAt)
        assertNull(merged.isMarkedUnread)
    }
}
