package com.garfiec.librechat.feature.agents.util

import com.garfiec.librechat.core.model.EndpointToolApproval
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class ToolApprovalModesTest {

    private val tool = "create_issue_mcp_github"
    private val revision = "6f1c1f3e-5a43-4c1e-9b1e-0c3f1b2a9d10"
    private val fresh = "11111111-2222-4333-8444-555555555555"

    private fun options(vararg entries: Pair<String, JsonObject>) = JsonObject(entries.toMap())

    @Test
    fun `setting a mode writes it with a fresh revision and keeps the tool's other options`() {
        val start = options(tool to buildJsonObject { put("defer_loading", true) })

        val updated = start.withApprovalMode(tool, ToolApprovalMode.CHAT) { fresh }!!

        assertThat(updated[tool]).isEqualTo(
            buildJsonObject {
                put("defer_loading", true)
                put("approval_mode", "chat")
                put("approval_revision", fresh)
            },
        )
    }

    @Test
    fun `the same remembering mode with a valid revision is a no-op, without one it re-mints`() {
        val withRevision = options(
            tool to buildJsonObject {
                put("approval_mode", "always")
                put("approval_revision", revision)
            },
        )
        assertThat(withRevision.withApprovalMode(tool, ToolApprovalMode.ALWAYS) { fresh }).isSameInstanceAs(withRevision)

        val withoutRevision = options(tool to buildJsonObject { put("approval_mode", "always") })
        val reminted = withoutRevision.withApprovalMode(tool, ToolApprovalMode.ALWAYS) { fresh }!!
        assertThat((reminted[tool] as JsonObject)["approval_revision"]).isEqualTo(JsonPrimitive(fresh))
    }

    @Test
    fun `a change of mode always writes a new revision`() {
        val start = options(
            tool to buildJsonObject {
                put("approval_mode", "chat")
                put("approval_revision", revision)
            },
        )

        val updated = start.withApprovalMode(tool, ToolApprovalMode.ALWAYS) { fresh }!!

        assertThat((updated[tool] as JsonObject)["approval_revision"]).isEqualTo(JsonPrimitive(fresh))
    }

    @Test
    fun `inherit removes both fields, keeps other options and drops an emptied entry`() {
        val keepsOthers = options(
            tool to buildJsonObject {
                put("defer_loading", true)
                put("approval_mode", "allow")
                put("approval_revision", revision)
            },
        ).withApprovalMode(tool, null)
        assertThat(keepsOthers?.get(tool)).isEqualTo(buildJsonObject { put("defer_loading", true) })

        val emptied = options(tool to buildJsonObject { put("approval_mode", "ask") }).withApprovalMode(tool, null)
        assertThat(emptied).isNull()

        assertThat((null as JsonObject?).withApprovalMode(tool, null)).isNull()
    }

    @Test
    fun `reads the saved mode, ignoring a value this build doesn't know`() {
        assertThat(options(tool to buildJsonObject { put("approval_mode", "chat") }).approvalModeOf(tool))
            .isEqualTo(ToolApprovalMode.CHAT)
        assertThat(options(tool to buildJsonObject { put("approval_mode", "later") }).approvalModeOf(tool)).isNull()
        assertThat((null as JsonObject?).approvalModeOf(tool)).isNull()
    }

    @Test
    fun `uuid shape check`() {
        assertThat(isUuid(revision)).isTrue()
        assertThat(isUuid("not-a-uuid")).isFalse()
        assertThat(isUuid("6f1c1f3e05a43-4c1e-9b1e-0c3f1b2a9d10")).isFalse()
        assertThat(isUuid(null)).isFalse()
    }

    // Cases from upstream packages/data-provider/src/approval.spec.ts and its rule order.

    @Test
    fun `enabled with nothing else constrains every tool to ask`() {
        assertThat(toolApprovalConstraint(EndpointToolApproval(enabled = true), tool))
            .isEqualTo(ToolApprovalConstraint.ASK)
    }

    @Test
    fun `no policy, or a disabled one, constrains nothing`() {
        assertThat(toolApprovalConstraint(null, tool)).isNull()
        assertThat(toolApprovalConstraint(EndpointToolApproval(enabled = false, deny = listOf("*")), tool)).isNull()
    }

    @Test
    fun `deny wins over ask and allow, ask over allow`() {
        val policy = EndpointToolApproval(
            enabled = true,
            deny = listOf("*_mcp_github"),
            ask = listOf("create_*"),
            allow = listOf("*"),
        )
        assertThat(toolApprovalConstraint(policy, tool)).isEqualTo(ToolApprovalConstraint.DENY)
        assertThat(toolApprovalConstraint(policy.copy(deny = null), tool)).isEqualTo(ToolApprovalConstraint.ASK)
        assertThat(toolApprovalConstraint(policy.copy(deny = null, ask = null), tool)).isNull()
    }

    @Test
    fun `an unlisted tool is ask by default, denied under dontAsk and free under bypass`() {
        assertThat(toolApprovalConstraint(EndpointToolApproval(enabled = true, mode = "dontAsk"), tool))
            .isEqualTo(ToolApprovalConstraint.DENY)
        assertThat(toolApprovalConstraint(EndpointToolApproval(enabled = true, mode = "bypass"), tool)).isNull()
        assertThat(toolApprovalConstraint(EndpointToolApproval(enabled = true, mode = "default"), tool))
            .isEqualTo(ToolApprovalConstraint.ASK)
    }

    @Test
    fun `globs match whole names, with star as any run and everything else literal`() {
        assertThat(globMatches("*", "")).isTrue()
        assertThat(globMatches("create_issue_mcp_github", tool)).isTrue()
        assertThat(globMatches("create_*", tool)).isTrue()
        assertThat(globMatches("*_mcp_*", tool)).isTrue()
        assertThat(globMatches("*github", tool)).isTrue()
        assertThat(globMatches("create", tool)).isFalse()
        assertThat(globMatches("*gitlab", tool)).isFalse()
        // Regex metacharacters are literal, as upstream escapes them.
        assertThat(globMatches("a.c", "abc")).isFalse()
        assertThat(globMatches("a.c", "a.c")).isTrue()
        assertThat(globMatches("mcp:server:*", "mcp:server:search")).isTrue()
        assertThat(globMatches("a*b*c", "aXbYbZc")).isTrue()
        assertThat(globMatches("a*b*c", "aXbYbZ")).isFalse()
    }
}
