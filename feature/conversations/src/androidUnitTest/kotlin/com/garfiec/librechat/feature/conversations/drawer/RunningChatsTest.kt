package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.Conversation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Ported from upstream `client/src/components/Conversations/__tests__/running.test.ts` semantics. */
class RunningChatsTest {

    private fun c(id: String) = Conversation(conversationId = id)

    private val today = DrawerGroupKey.Date(DateGroup.Today)
    private val yesterday = DrawerGroupKey.Date(DateGroup.Yesterday)
    private val previous7 = DrawerGroupKey.Date(DateGroup.Previous7Days)

    private val groups = listOf(
        DateGroup.Today to listOf(c("a"), c("b")),
        DateGroup.Yesterday to listOf(c("c")),
        DateGroup.Previous7Days to listOf(c("d"), c("e")),
    )

    private fun ids(result: List<Pair<DrawerGroupKey, List<Conversation>>>) =
        result.map { (name, convos) -> name to convos.map { it.conversationId } }

    @Test
    fun runningChatsMoveToALeadingGroupInTheirExistingOrder() {
        val result = groups.withRunningFirst(setOf("e", "b"))

        assertThat(ids(result)).containsExactly(
            DrawerGroupKey.Running to listOf("b", "e"),
            today to listOf("a"),
            yesterday to listOf("c"),
            previous7 to listOf("d"),
        ).inOrder()
    }

    @Test
    fun aDateGroupLeftEmptyIsDropped() {
        val result = groups.withRunningFirst(setOf("c"))

        assertThat(result.map { it.first }).containsExactly(DrawerGroupKey.Running, today, previous7).inOrder()
    }

    @Test
    fun nothingRunningOrNothingListedLeavesTheGroupsAlone() {
        val dated = groups.map { (group, convos) -> DrawerGroupKey.Date(group) to convos }
        assertThat(groups.withRunningFirst(emptySet())).isEqualTo(dated)
        assertThat(groups.withRunningFirst(setOf("elsewhere"))).isEqualTo(dated)
    }
}
