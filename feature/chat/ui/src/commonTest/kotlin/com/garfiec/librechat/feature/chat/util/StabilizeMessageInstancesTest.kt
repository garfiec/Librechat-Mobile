package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Message
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StabilizeMessageInstancesTest {

    private val onScreen = Message(
        messageId = "u1",
        conversationId = "c",
        isCreatedByUser = true,
        text = "Summarize this",
        createdAt = "2026-10-21T10:00:00Z",
    )

    @Test
    fun aRoomEmissionDifferingOnlyInTimestampsKeepsTheOnScreenInstance() {
        val fromRoom = onScreen.copy(createdAt = "2026-10-21T10:00:00.123Z", tokenCount = 12)
        assertSame(onScreen, stabilizeMessageInstances(listOf(fromRoom), listOf(onScreen)).single())
    }

    @Test
    fun persistedSkillListsCountAsRenderedSoTheirArrivalRecomposes() {
        // Room stores both lists, so an emission that carries them is new content (the pills),
        // not a cosmetic reconcile to conflate away.
        val withManual = onScreen.copy(manualSkills = listOf("pdf-tools"))
        val manualResult = stabilizeMessageInstances(listOf(withManual), listOf(onScreen)).single()
        assertTrue(manualResult === withManual)

        val withAlways = onScreen.copy(alwaysAppliedSkills = listOf("house-style"))
        val alwaysResult = stabilizeMessageInstances(listOf(withAlways), listOf(onScreen)).single()
        assertTrue(alwaysResult === withAlways)
    }
}
