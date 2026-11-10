package com.garfiec.librechat.core.ui.media

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class AudioClipPlayerTest {

    @Test
    fun audioIosCannotDecodeReportsFailureInsteadOfThrowing() = runTest {
        val player = AudioClipPlayer()
        assertFalse(player.play("""{"error":"not audio"}""".encodeToByteArray()))
        assertFalse(player.play(ByteArray(0)))
    }
}
