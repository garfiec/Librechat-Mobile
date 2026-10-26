package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A spec's `artifacts` key, as web maps it into the ephemeral agent: `true` → `"default"`, a string as is. */
class ModelSpecArtifactsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun spec(artifacts: String?): ModelSpec {
        val field = artifacts?.let { ""","artifacts":$it""" }.orEmpty()
        return json.decodeFromString(ModelSpec.serializer(), """{"name":"s"$field}""")
    }

    @Test
    fun trueIsTheDefaultMode() {
        assertEquals("default", spec("true").artifactsMode)
    }

    @Test
    fun aModeIsSentAsIs() {
        assertEquals("shadcnui", spec("\"shadcnui\"").artifactsMode)
        assertEquals("custom", spec("\"custom\"").artifactsMode)
    }

    @Test
    fun absentFalseAndEmptyTurnNothingOn() {
        assertNull(spec(null).artifactsMode)
        assertNull(spec("false").artifactsMode)
        assertNull(spec("\"\"").artifactsMode)
    }

    @Test
    fun aValueOfAnotherShapeIsIgnored() {
        assertNull(spec("1").artifactsMode)
        assertNull(spec("{}").artifactsMode)
    }
}
