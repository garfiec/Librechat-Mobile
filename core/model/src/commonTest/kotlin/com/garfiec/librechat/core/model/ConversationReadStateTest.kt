package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.config.ConversationListLimits
import com.garfiec.librechat.core.model.config.StartupConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ConversationReadStateTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val reply = Instant.parse("2026-10-30T10:00:00Z")

    @Test
    fun listRowWithAStampedReplyDecodesAsFinishedUnseen() {
        val row = json.decodeFromString<Conversation>(
            """{"conversationId":"c1","lastResponseAt":"2026-10-30T10:00:00.000Z",""" +
                """"lastResponseMessageId":"m1","isMarkedUnread":false,"lastSeenAt":"1970-01-01T00:00:00.000Z"}""",
        )
        assertEquals(reply, row.lastResponseAt)
        assertEquals(UNSEEN_REPLY_WATERMARK, row.lastSeenAt)
        assertTrue(row.isUnseen)
        assertTrue(row.isFinishedUnseen)
    }

    @Test
    fun noReplyStampReadsAsSeen() {
        assertFalse(Conversation(conversationId = "c1").isUnseen)
        assertFalse(Conversation(conversationId = "c1", lastSeenAt = UNSEEN_REPLY_WATERMARK).isUnseen)
    }

    @Test
    fun aMissingCatchUpIsUnseen() {
        assertTrue(Conversation(lastResponseAt = reply).isUnseen)
    }

    @Test
    fun aCatchUpAtOrAfterTheReplyIsSeen() {
        assertFalse(Conversation(lastResponseAt = reply, lastSeenAt = reply).isUnseen)
        assertTrue(Conversation(lastResponseAt = reply, lastSeenAt = Instant.parse("2026-10-30T09:59:59Z")).isUnseen)
    }

    @Test
    fun handMarkedUnreadIsUnseenButNotFinished() {
        val manual = Conversation(lastResponseAt = reply, isMarkedUnread = true, lastResponseIsManual = true)
        assertTrue(manual.isUnseen)
        assertFalse(manual.isFinishedUnseen)
        // Marked unread on a chat that had a reply: no synthetic stamp, but the flag is set.
        val flagged = Conversation(lastResponseAt = reply, isMarkedUnread = true)
        assertTrue(flagged.isUnseen)
        assertFalse(flagged.isFinishedUnseen)
    }

    @Test
    fun legacyRowsWithoutTheFlagAreNotFinished() {
        assertFalse(Conversation(lastResponseAt = reply, lastSeenAt = UNSEEN_REPLY_WATERMARK).isFinishedUnseen)
    }

    @Test
    fun conversationListLimitsDecodeAndDefault() {
        val config = json.decodeFromString<StartupConfig>(
            """{"conversationListLimits":{"maxEndpointFilters":3,"maxEndpointNameLength":20}}""",
        )
        assertEquals(ConversationListLimits(3, 20), config.conversationListLimits)
        assertNull(json.decodeFromString<StartupConfig>("{}").conversationListLimits)
        assertEquals(50, ConversationListLimits.DEFAULT.maxEndpointFilters)
        assertEquals(128, ConversationListLimits.DEFAULT.maxEndpointNameLength)
    }

    @Test
    fun filtersAreActiveOnlyWhenAFacetIsSet() {
        assertFalse(ConversationListFilters.None.isActive)
        assertTrue(ConversationListFilters(hasFiles = true).isActive)
        assertTrue(ConversationListFilters(endpoints = listOf("openAI")).isActive)
        assertTrue(ConversationListFilters(createdAfter = reply).isActive)
        assertTrue(ConversationListFilters(tags = listOf("Saved")).isActive)
    }
}
