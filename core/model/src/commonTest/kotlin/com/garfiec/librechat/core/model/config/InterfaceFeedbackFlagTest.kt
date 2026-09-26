package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `interface.feedback` (v0.8.8-rc2). The default is the whole contract: a server at or below rc1
 * omits the key entirely, and reading that absence as "hide feedback" would withdraw a working
 * feature from every older deployment.
 */
class InterfaceFeedbackFlagTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) = json.decodeFromString(InterfaceConfig.serializer(), body)

    @Test
    fun absentFeedbackFlagEnablesFeedback() {
        assertEquals(true, decode("""{ "modelSelect": true }""").feedback)
    }

    @Test
    fun explicitFalseDisablesFeedback() {
        assertEquals(false, decode("""{ "feedback": false }""").feedback)
    }

    @Test
    fun explicitTrueEnablesFeedback() {
        assertEquals(true, decode("""{ "feedback": true }""").feedback)
    }
}
