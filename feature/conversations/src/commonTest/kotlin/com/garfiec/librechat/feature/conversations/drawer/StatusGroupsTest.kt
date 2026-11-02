package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.UNSEEN_REPLY_WATERMARK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ported from upstream `client/src/components/Conversations/__tests__/running.spec.ts` (v0.8.9-rc1). */
class StatusGroupsTest {

    private val reply = Instant.parse("2026-10-30T10:00:00Z")

    private fun c(id: String, updated: String = "2026-10-30T09:00:00Z") =
        Conversation(conversationId = id, updatedAt = Instant.parse(updated))

    private fun finished(id: String) =
        c(id).copy(lastResponseAt = reply, isMarkedUnread = false, lastSeenAt = UNSEEN_REPLY_WATERMARK)

    private fun ids(result: List<Pair<DrawerGroupKey, List<Conversation>>>) =
        result.map { (key, rows) -> key to rows.map { it.conversationId } }

    private val today = DrawerGroupKey.Date(DateGroup.Today)
    private val yesterday = DrawerGroupKey.Date(DateGroup.Yesterday)

    @Test
    fun finishedUnseenRepliesFollowRunning() {
        val groups = listOf(
            DateGroup.Today to listOf(c("a"), finished("b")),
            DateGroup.Yesterday to listOf(c("c"), finished("d")),
        )
        val result = groups.withRunningFirst(setOf("a"))
        assertEquals(
            listOf(
                DrawerGroupKey.Running to listOf("a"),
                DrawerGroupKey.Finished to listOf("b", "d"),
                yesterday to listOf("c"),
            ),
            ids(result),
        )
    }

    @Test
    fun aRunningChatIsNeverFinished() {
        val result = listOf(DateGroup.Today to listOf(finished("a"))).withRunningFirst(setOf("a"))
        assertEquals(listOf(DrawerGroupKey.Running to listOf("a")), ids(result))
    }

    @Test
    fun aHandMarkedChatKeepsItsDateGroup() {
        val manual = c("m").copy(lastResponseAt = reply, isMarkedUnread = true, lastResponseIsManual = true)
        val result = listOf(DateGroup.Today to listOf(manual, c("x"))).withRunningFirst(emptySet())
        assertEquals(listOf(today to listOf("m", "x")), ids(result))
    }

    @Test
    fun aSeenReplyIsNotFinished() {
        val seen = finished("s").copy(lastSeenAt = reply)
        val result = listOf(DateGroup.Today to listOf(seen)).withRunningFirst(emptySet())
        assertEquals(listOf(today to listOf("s")), ids(result))
    }

    @Test
    fun unlistedRunningChatsJoinRunningNewestFirst() {
        val groups = listOf(DateGroup.Today to listOf(c("a", "2026-10-30T08:00:00Z"), c("b")))
        val unlisted = listOf(
            c("project", "2026-10-30T09:30:00Z"),
            c("archived", "2026-10-30T09:40:00Z").copy(isArchived = true),
            c("temp", "2026-10-30T09:50:00Z").copy(isTemporary = true),
            c("idle", "2026-10-30T09:55:00Z"),
        )
        val result = groups.withRunningFirst(setOf("a", "project", "archived", "temp"), unlisted)
        assertEquals(
            listOf(
                DrawerGroupKey.Running to listOf("project", "a"),
                today to listOf("b"),
            ),
            ids(result),
        )
    }

    @Test
    fun anUnlistedRowAlreadyListedIsNotDuplicated() {
        val groups = listOf(DateGroup.Today to listOf(c("a")))
        val result = groups.withRunningFirst(setOf("a"), unlisted = listOf(c("a")))
        assertEquals(listOf(DrawerGroupKey.Running to listOf("a")), ids(result))
    }

    @Test
    fun unlistedRunningIdsAreTheRunningOnesNoGroupHolds() {
        val groups = listOf(DateGroup.Today to listOf(c("a")), DateGroup.Yesterday to listOf(c("b")))
        assertEquals(setOf("x"), unlistedRunningIds(groups, setOf("a", "x")))
        assertTrue(unlistedRunningIds(groups, emptySet()).isEmpty())
    }

    @Test
    fun statusGroupsAreMarked() {
        assertTrue(DrawerGroupKey.Running.isStatus)
        assertTrue(DrawerGroupKey.Finished.isStatus)
        assertFalse(today.isStatus)
    }

    @Test
    fun aRunningRowShowsNoDot() {
        val row = finished("a")
        assertTrue(row.toDrawerDisplayData(null, emptyMap(), "https://srv").isUnread)
        assertFalse(row.toDrawerDisplayData(null, emptyMap(), "https://srv", runningIds = setOf("a")).isUnread)
    }
}
