package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.WorkspaceChange
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspaceChangesTest {

    private fun change(
        fileId: String?,
        path: String?,
        profile: String? = "stateful",
        filepath: String? = "/uploads/${fileId ?: path}",
        operation: String? = "updated",
        agentId: String? = null,
        toolCallId: String = "call_$fileId",
    ) = Attachment(
        fileId = fileId,
        filename = path?.substringAfterLast('/'),
        filepath = filepath,
        toolCallId = toolCallId,
        agentId = agentId,
        workspaceChange = WorkspaceChange(profile = profile, operation = operation, path = path),
    )

    // ─── partitionWorkspaceChanges ──────────────────────────────────

    @Test
    fun `stateful files with a filepath are changes, everything else stays inline`() {
        val stateful = change("a", "reports/result.csv")
        val stateless = change("b", "default.txt", profile = "default")
        val unprofiled = change("c", "c.txt", profile = null)
        val plain = Attachment(fileId = "d", filename = "plot.png", filepath = "/uploads/d", toolCallId = "call_d")

        val split = partitionWorkspaceChanges(listOf(stateful, stateless, unprofiled, plain))

        assertThat(split.changes).containsExactly(stateful)
        assertThat(split.inline).containsExactly(stateless, unprofiled, plain).inOrder()
    }

    @Test
    fun `a change with no filepath cannot be fetched and stays inline`() {
        val missing = change("m", "missing.txt", filepath = null)
        val blank = change("n", "blank.txt", filepath = "")

        val split = partitionWorkspaceChanges(listOf(missing, blank))

        assertThat(split.changes).isEmpty()
        assertThat(split.inline).containsExactly(missing, blank).inOrder()
    }

    @Test
    fun `the last record of a file wins and moves to the end`() {
        val first = change("shared", "reports/result.csv", filepath = "/uploads/first")
        val other = change("other", "notes.txt")
        val latest = change("shared", "reports/result.csv", filepath = "/uploads/latest")

        val split = partitionWorkspaceChanges(listOf(first, other, latest))

        assertThat(split.changes).containsExactly(other, latest).inOrder()
    }

    @Test
    fun `records without a fileId dedupe by agent and path`() {
        val first = change(null, "out/a.csv", agentId = "agent_1", filepath = "/api/files/code/download/s/1")
        val rewrite = change(null, "out/a.csv", agentId = "agent_1", filepath = "/api/files/code/download/s/2")
        val otherAgent = change(null, "out/a.csv", agentId = "agent_2", filepath = "/api/files/code/download/s/3")

        val split = partitionWorkspaceChanges(listOf(first, rewrite, otherAgent))

        assertThat(split.changes).containsExactly(rewrite, otherAgent).inOrder()
    }

    @Test
    fun `a record with no path dedupes by its filepath`() {
        val a = change(null, null, filepath = "/api/files/code/download/s/1")
        val b = change(null, null, filepath = "/api/files/code/download/s/2")

        assertThat(partitionWorkspaceChanges(listOf(a, b)).changes).containsExactly(a, b).inOrder()
    }

    @Test
    fun `no changes returns the same list`() {
        val attachments = listOf(Attachment(fileId = "a", filename = "a.txt", toolCallId = "c"))

        val split = partitionWorkspaceChanges(attachments)

        assertThat(split.inline).isSameInstanceAs(attachments)
        assertThat(split.changes).isEmpty()
        assertThat(partitionWorkspaceChanges(null).inline).isEmpty()
    }

    @Test
    fun `changes are excluded from the tool call buckets`() {
        val stateful = change("a", "result.csv", toolCallId = "call_1")
        val plain = Attachment(fileId = "b", filename = "b.csv", type = "text/csv", toolCallId = "call_1")

        val inline = partitionWorkspaceChanges(listOf(stateful, plain)).inline

        assertThat(partitionToolCallAttachments(inline, "call_1").map { it.attachment }).containsExactly(plain)
        assertThat(collectGroupAttachments(inline, listOf("call_1")).flatMap { it.items }.map { it.attachment })
            .containsExactly(plain)
    }

    // ─── row text ───────────────────────────────────────────────────

    @Test
    fun `row leads with the path's file name and shows the path only when it adds something`() {
        val nested = change("a", "reports/q3/summary.csv")
        val flat = change("b", "notes.txt")

        assertThat(workspaceChangeName(nested)).isEqualTo("summary.csv")
        assertThat(workspaceChangePath(nested)).isEqualTo("reports/q3/summary.csv")
        assertThat(workspaceChangeName(flat)).isEqualTo("notes.txt")
        assertThat(workspaceChangePath(flat)).isNull()
    }

    @Test
    fun `row without a path falls back to the file's own name`() {
        val noPath = change("a", null).copy(filename = "plot.png")
        val noName = change("b", null, filepath = "/api/files/code/download/s/data.json").copy(filename = null)

        assertThat(workspaceChangeName(noPath)).isEqualTo("plot.png")
        assertThat(workspaceChangePath(noPath)).isNull()
        assertThat(workspaceChangeName(noName)).isEqualTo("data.json")
    }

    @Test
    fun `operation maps created and updated, and nothing else`() {
        assertThat(workspaceOperation(change("a", "a", operation = "created"))).isEqualTo(WorkspaceOperation.CREATED)
        assertThat(workspaceOperation(change("a", "a", operation = "updated"))).isEqualTo(WorkspaceOperation.UPDATED)
        assertThat(workspaceOperation(change("a", "a", operation = "deleted"))).isNull()
        assertThat(workspaceOperation(change("a", "a", operation = null))).isNull()
    }
}
