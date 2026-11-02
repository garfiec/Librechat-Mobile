package com.garfiec.librechat.core.model.schedule

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleMcpConsentTest {

    // The network module's settings: a null data-class property would be dropped.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun a_first_confirmation_sends_expectedRevision_null_rather_than_omitting_it() {
        val body = confirmScheduleMcpConsentBody("a".repeat(64), expectedRevision = null, lifetimeHours = 1)
        val encoded = json.encodeToString(JsonObject.serializer(), body)

        assertTrue(encoded.contains("\"expectedRevision\":null"), encoded)
        assertTrue(encoded.contains("\"lifetimeHours\":1"), encoded)
    }

    @Test
    fun a_renewal_sends_the_current_revision() {
        val body = confirmScheduleMcpConsentBody(offerDigest = "d", expectedRevision = "rev-2", lifetimeHours = 24)
        val encoded = json.encodeToString(JsonObject.serializer(), body)

        assertTrue(encoded.contains("\"expectedRevision\":\"rev-2\""), encoded)
    }

    @Test
    fun the_view_decodes_with_epoch_millisecond_expiry_and_an_offer() {
        val view = json.decodeFromString(
            ScheduleMcpConsentView.serializer(),
            """
            {"state":"active","revision":"r1","expiresAtMs":1793000000000,
             "targets":[{"resource":{"serverName":"github","url":"https://mcp.github.com/sse",
               "configurationRevision":"c","credentialMode":"stored_oauth","issuer":"https://github.com",
               "audience":null,"scopes":["repo:read"]},
               "permittedTools":[{"agentId":"agent_1","tools":["list_pull_requests"]}],"policyRevision":"p"}],
             "offer":{"digest":"abc","maxLifetimeHours":168}}
            """.trimIndent(),
        )

        assertEquals(ScheduleMcpConsentState.ACTIVE, view.state)
        assertEquals(1_793_000_000_000L, view.expiresAtMs)
        assertEquals("github", view.targets.single().resource.serverName)
        assertNull(view.targets.single().resource.audience)
        assertEquals(168, view.offer?.maxLifetimeHours)
    }

    @Test
    fun a_missing_view_has_no_revision_offer_or_deadline() {
        val view = json.decodeFromString(
            ScheduleMcpConsentView.serializer(),
            """{"state":"missing","revision":null,"expiresAtMs":null,"targets":[],"offer":null}""",
        )

        assertEquals(ScheduleMcpConsentState.MISSING, view.state)
        assertNull(view.revision)
        assertNull(view.offer)
    }
}
