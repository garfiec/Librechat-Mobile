package com.garfiec.librechat.core.model.steer

import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.request.SteerArmRequest
import com.garfiec.librechat.core.model.request.SteerRequest
import com.garfiec.librechat.core.model.response.SteerArmResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The interrupt half of the steer contract: `preempt` on the POST and its echo, and `/steer/arm`. */
class SteerInterruptWireTest {

    // The network module's settings: defaults and nulls are omitted.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private fun encode(request: SteerRequest): JsonObject =
        json.parseToJsonElement(json.encodeToString(SteerRequest.serializer(), request)).jsonObject

    @Test
    fun anOrdinarySteerSendsNoPreemptKey() {
        val body = encode(SteerRequest(conversationId = "c1", text = "hi"))
        assertFalse("preempt" in body)
    }

    @Test
    fun anInterruptSendsPreemptTrue() {
        val body = encode(SteerRequest(conversationId = "c1", text = "hi", preempt = true))
        assertEquals("true", body["preempt"]?.jsonPrimitive?.content)
    }

    @Test
    fun theAckEchoesWhetherTheInterruptTook() {
        val took = json.decodeFromString(SteerResponse.serializer(), """{"status":"queued","steerId":"s1","preempt":true}""")
        assertEquals(true, took.preempt)
        val kept = json.decodeFromString(SteerResponse.serializer(), """{"status":"queued","steerId":"s1","preempt":false}""")
        assertEquals(false, kept.preempt)
    }

    @Test
    fun armBodyCarriesConversationAndSteer() {
        val body = json.parseToJsonElement(
            json.encodeToString(SteerArmRequest.serializer(), SteerArmRequest("c1", "s1")),
        ).jsonObject
        assertEquals(setOf("conversationId", "steerId"), body.keys)
    }

    @Test
    fun armRepliesDecode() {
        val armed = json.decodeFromString(SteerArmResponse.serializer(), """{"armed":true}""")
        assertTrue(armed.armed)
        assertNull(armed.preemptRevision)

        val lost = json.decodeFromString(SteerArmResponse.serializer(), """{"armed":false}""")
        assertFalse(lost.armed)
        assertFalse(lost.isPreemptUnsupported)

        val unsupported = json.decodeFromString(SteerArmResponse.serializer(), """{"armed":false,"code":"PREEMPT_UNSUPPORTED"}""")
        assertTrue(unsupported.isPreemptUnsupported)

        val v2 = json.decodeFromString(SteerArmResponse.serializer(), """{"armed":true,"preemptRevision":3}""")
        assertEquals(3, v2.preemptRevision)
    }

    @Test
    fun aReportedSteerKeepsItsInterruptFlag() {
        val steer = json.decodeFromString(
            PendingSteer.serializer(),
            """{"steerId":"s1","text":"go","createdAt":1,"preempt":true,"preemptRevision":2}""",
        )
        assertEquals(true, steer.preempt)
        assertEquals(2, steer.preemptRevision)
        val plain = json.decodeFromString(PendingSteer.serializer(), """{"steerId":"s2","text":"go"}""")
        assertNull(plain.preempt)
    }
}
