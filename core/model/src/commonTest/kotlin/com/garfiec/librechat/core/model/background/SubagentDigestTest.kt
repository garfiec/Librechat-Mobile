package com.garfiec.librechat.core.model.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A background subagent task's `subagent_thread_id` and `activity` digest (upstream `background.ts`, v0.8.9-rc1). */
class SubagentDigestTest {

    private fun task(extra: String) = assertIs<BackgroundTaskDisplay.Task>(
        BackgroundTaskOutput.parse("""{"background_task_id":"t1","tool":"subagent","status":"running"$extra}"""),
    ).task

    private val digest = """
        {"turns":3,"tools":5,"errors":1,"active":"3.2","phase":"thinking","idle_ms":1200,"truncated":true,
         "nodes":[
           {"path":"1-2","kind":"range","status":"ok","summary":"bash_tool ×3, text +2 more","evicted":true},
           {"path":"3","kind":"turn","status":"running","ms":4000},
           {"path":"3.1","kind":"tool","name":"web_search","label":"Search news","status":"error","errors":1,"chars":20},
           {"path":"3.2","kind":"text","status":"running"}
         ]}
    """.trimIndent().replace("\n", "")

    @Test
    fun the_thread_id_and_digest_decode() {
        val parsed = task(""","subagent_thread_id":"thread-9","activity":$digest""")
        assertEquals("thread-9", parsed.threadId)
        val activity = assertNotNull(parsed.activity)
        assertEquals(3L, activity.turns)
        assertEquals(5L, activity.tools)
        assertEquals(1L, activity.errors)
        assertEquals("3.2", activity.active)
        assertTrue(activity.thinking)
        assertEquals(1200L, activity.idleMs)
        assertTrue(activity.truncated)
        assertEquals(listOf("1-2", "3", "3.1", "3.2"), activity.nodes.map { it.path })
        val tool = activity.nodes[2]
        assertEquals(SubagentDigestKind.TOOL, tool.kind)
        assertEquals(SubagentDigestStatus.ERROR, tool.status)
        assertEquals("Search news", tool.label)
        assertEquals(1, tool.errors)
        assertTrue(activity.nodes[0].evicted)
    }

    @Test
    fun a_malformed_digest_is_dropped_but_the_card_stays() {
        for (bad in listOf(
            """{"turns":-1,"tools":0,"errors":0,"nodes":[]}""",
            """{"turns":"1","tools":0,"errors":0,"nodes":[]}""",
            """{"turns":1,"tools":0,"errors":0}""",
            """{"turns":1,"tools":0,"errors":0,"nodes":[{"path":"a","kind":"turn"}]}""",
            """{"turns":1,"tools":0,"errors":0,"nodes":[{"path":"1","kind":"step"}]}""",
            "[]",
        )) {
            val parsed = task(""","activity":$bad""")
            assertNull(parsed.activity, bad)
        }
    }

    @Test
    fun an_empty_or_mistyped_thread_id_is_ignored_not_refused() {
        assertNull(task(""","subagent_thread_id":""""").threadId)
        assertNull(task(""","subagent_thread_id":42""").threadId)
    }

    @Test
    fun an_unknown_node_status_is_dropped_and_an_active_path_must_be_a_path() {
        val activity = assertNotNull(
            task(""","activity":{"turns":1,"tools":0,"errors":0,"active":"x","nodes":[{"path":"1","kind":"turn","status":"paused"}]}""")
                .activity,
        )
        assertNull(activity.active)
        assertNull(activity.nodes.single().status)
    }

    @Test
    fun paths_follow_the_upstream_grammar() {
        for (ok in listOf("1", "12", "3.2", "3.2.1", "1-14", "3.1-20")) assertTrue(SubagentDigestParser.isDigestPath(ok), ok)
        for (bad in listOf("", ".", "1.", "1-", "1-2-3", "a", "1.2-3.4", "١")) assertFalse(SubagentDigestParser.isDigestPath(bad), bad)
    }
}
