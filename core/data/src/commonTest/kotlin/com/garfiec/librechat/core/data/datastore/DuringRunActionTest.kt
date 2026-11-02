package com.garfiec.librechat.core.data.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuringRunActionTest {

    @Test
    fun everyModeRoundTripsThroughWebsOwnValue() {
        assertEquals(listOf("steer", "interrupt", "queue"), DuringRunAction.entries.map { it.toStorageString() })
        DuringRunAction.entries.forEach { assertEquals(it, DuringRunAction.fromString(it.toStorageString())) }
    }

    @Test
    fun anUnknownOrMissingValueReadsAsSteer() {
        assertEquals(DuringRunAction.STEER, DuringRunAction.fromString(null))
        assertEquals(DuringRunAction.STEER, DuringRunAction.fromString("preempt"))
    }

    @Test
    fun onlyQueueIsNotLive() {
        assertTrue(DuringRunAction.STEER.isLive)
        assertTrue(DuringRunAction.INTERRUPT.isLive)
        assertFalse(DuringRunAction.QUEUE.isLive)
    }
}
