package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.background.SubagentDigestKind
import com.garfiec.librechat.core.model.background.SubagentDigestNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Upstream `SubagentProgress.tsx`'s tree, open-branch and summary rules. */
class SubagentProgressTreeTest {

    private fun node(path: String, kind: SubagentDigestKind = SubagentDigestKind.TURN) = SubagentDigestNode(path, kind)

    @Test
    fun parent_paths() {
        assertEquals("3", digestParentPath("3.2"))
        assertEquals("3", digestParentPath("3.1-20"))
        assertEquals("", digestParentPath("1-14"))
        assertEquals("", digestParentPath("4"))
        assertEquals("3.2", digestParentPath("3.2.1"))
    }

    @Test
    fun the_tree_nests_by_path_and_keeps_order() {
        val tree = buildDigestTree(
            listOf(
                node("1-2", SubagentDigestKind.RANGE),
                node("3"),
                node("3.1", SubagentDigestKind.TOOL),
                node("3.2", SubagentDigestKind.TOOL),
                node("3.2.1"),
                node("4"),
            ),
        )
        assertEquals(listOf("1-2", "3", "4"), tree.map { it.node.path })
        assertEquals(listOf("3.1", "3.2"), tree[1].children.map { it.node.path })
        assertEquals(listOf("3.2.1"), tree[1].children[1].children.map { it.node.path })
    }

    @Test
    fun a_node_whose_parent_is_missing_is_a_root() {
        assertEquals(listOf("5.1"), buildDigestTree(listOf(node("5.1"))).map { it.node.path })
    }

    @Test
    fun only_the_branch_holding_the_current_step_opens() {
        assertTrue(isOnDigestPath("3", "3.2.1"))
        assertTrue(isOnDigestPath("3.2", "3.2"))
        assertFalse(isOnDigestPath("3", "30.1"))
        assertFalse(isOnDigestPath("3", null))
    }

    @Test
    fun summaries_split_into_localizable_fragments() {
        assertEquals(DigestSummary.Nested("1", "2"), parseDigestSummary("1 turn, 2 tools"))
        assertEquals(DigestSummary.Nested("3", "1"), parseDigestSummary("3 turns, 1 tool"))
        assertEquals(
            DigestSummary.Entries(
                listOf(DigestSummary.Entry("bash_tool", "3"), DigestSummary.Entry("text", null)),
                more = "2",
            ),
            parseDigestSummary("bash_tool ×3, text +2 more"),
        )
        assertEquals(
            DigestSummary.Entries(listOf(DigestSummary.Entry("web_search", null)), more = null),
            parseDigestSummary("web_search"),
        )
    }
}
