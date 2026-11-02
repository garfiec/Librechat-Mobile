package com.garfiec.librechat.core.model.schedule

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v0.8.9-rc1 scheduled-run MCP receipts: reason, recovery and automaticReplay (upstream `projectScheduleMCPReceipt`). */
class ScheduleMcpReceiptTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun schedule(lastRun: String, extra: String = "") = json.decodeFromString<Schedule>(
        """
        {"id": "s", "name": "n", "prompt": "p", "agent_id": "a",
         "cadence": {"frequency": "daily", "hour": 9, "minute": 0},
         "timezone": "UTC", "enabled": true $extra, "lastRun": $lastRun}
        """.trimIndent(),
    )

    private fun receipt(reason: String, recovery: String?) =
        """{"server":"github","status":"mcp_reauth_required","reason":"$reason",""" +
            (recovery?.let { """"recovery":"$it",""" } ?: "") + """"automaticReplay":false}"""

    @Test
    fun decodesTheReceiptAndConsentFields() {
        val s = schedule(
            """{"status": "error", "mcp": [${receipt("credential_rejected", "authorize")}], "firedAt": "t"}""",
            extra = """, "hasMCPConsent": true""",
        )
        val outcome = s.lastRun!!.mcp!!.single()
        assertEquals("credential_rejected", outcome.reason)
        assertEquals("authorize", outcome.recovery)
        assertEquals(false, outcome.automaticReplay)
        assertEquals(true, s.hasMcpConsent)
        assertEquals(true, json.decodeFromString<ScheduleLimits>("""{"mcpConsent": true}""").mcpConsent)
    }

    @Test
    fun aDeniedRunNamesTheServerAndTheRepair() {
        val run = """{"status": "error", "mcp": [${receipt("credential_rejected", "authorize")}]}"""
        assertEquals(BlockedRun("github", BlockedRunRepair.SIGN_IN), schedule(run).blockedRun())
    }

    @Test
    fun aConsentReasonAsksForConsentWhateverTheRecovery() {
        val run = """{"status": "error", "mcp": [${receipt("consent_expired", "authorize")}]}"""
        assertEquals(BlockedRunRepair.CONSENT, schedule(run).blockedRun()?.repair)
    }

    @Test
    fun withoutAStructuredListTheReceiptIsReadFromTheErrorStringNeverShown() {
        val error = "mcp_reauth_required: [" + receipt("rbac_denied", "restore_permission") + "]"
        val run = """{"status": "error", "error": ${json.encodeToString(String.serializer(), error)}}"""
        assertEquals(BlockedRun("github", BlockedRunRepair.ACCESS), schedule(run).blockedRun())
    }

    @Test
    fun aReadinessSnapshotOrASuccessfulRunIsNotABlock() {
        val snapshot = """{"status": "error", "mcp": [{"server":"github","status":"mcp_unavailable"}]}"""
        assertNull(schedule(snapshot).blockedRun())
        val success = """{"status": "success", "mcp": [${receipt("credential_rejected", "authorize")}]}"""
        assertNull(schedule(success).blockedRun())
    }

    @Test
    fun anUnknownReasonFailsTheShapeAsUpstreamsSchemaDoes() {
        val run = """{"status": "error", "mcp": [${receipt("something_new", "authorize")}]}"""
        assertNull(schedule(run).blockedRun())
    }
}
