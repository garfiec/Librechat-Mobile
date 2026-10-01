package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.Conversation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Ported from upstream `client/src/components/Conversations/__tests__/running.test.ts` semantics. */
class RunningChatsTest {

    private fun c(id: String) = Conversation(conversationId = id)

    private val groups = listOf(
        "Today" to listOf(c("a"), c("b")),
        "Yesterday" to listOf(c("c")),
        "Previous 7 Days" to listOf(c("d"), c("e")),
    )

    private fun ids(result: List<Pair<String, List<Conversation>>>) =
        result.map { (name, convos) -> name to convos.map { it.conversationId } }

    @Test
    fun runningChatsMoveToALeadingGroupInTheirExistingOrder() {
        val result = groups.withRunningFirst(setOf("e", "b"))

        assertThat(ids(result)).containsExactly(
            RUNNING_CHATS_GROUP to listOf("b", "e"),
            "Today" to listOf("a"),
            "Yesterday" to listOf("c"),
            "Previous 7 Days" to listOf("d"),
        ).inOrder()
    }

    @Test
    fun aDateGroupLeftEmptyIsDropped() {
        val result = groups.withRunningFirst(setOf("c"))

        assertThat(result.map { it.first }).containsExactly(RUNNING_CHATS_GROUP, "Today", "Previous 7 Days").inOrder()
    }

    @Test
    fun nothingRunningOrNothingListedLeavesTheGroupsAlone() {
        assertThat(groups.withRunningFirst(emptySet())).isSameInstanceAs(groups)
        assertThat(groups.withRunningFirst(setOf("elsewhere"))).isSameInstanceAs(groups)
    }
}
