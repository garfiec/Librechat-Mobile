package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The v0.8.9-rc1 account flags: a server that predates them must read as "off". */
class AccountFlagsV089Test {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun config(body: String): StartupConfig = json.decodeFromString(body)

    @Test
    fun absent_flags_are_off() {
        val config = config("{}")
        assertFalse(config.passkeyLoginEnabled)
        assertNull(config.maxPasskeysPerUser)
        assertFalse(config.allowEmailChange)
    }

    @Test
    fun announced_flags_decode() {
        val config = config("""{"passkeyLoginEnabled":true,"maxPasskeysPerUser":5,"allowEmailChange":true}""")
        assertTrue(config.passkeyLoginEnabled)
        assertEquals(5, config.maxPasskeysPerUser)
        assertTrue(config.allowEmailChange)
    }
}
