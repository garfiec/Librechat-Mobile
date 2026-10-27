package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The state change that masks a secret also turns the sheet's `secure` off. The window must stay
 * secure until the masked frame has been drawn and shown, not drop the flag in the same frame.
 */
@OptIn(ExperimentalTestApi::class)
class LingeringSecureIosTest {

    @Test
    fun theWindowStaysSecureForTheFramesAfterSecureTurnsOff() = runComposeUiTest {
        mainClock.autoAdvance = false
        var secure by mutableStateOf(false)
        var windowSecure = false
        setContent { windowSecure = rememberLingeringSecure(secure) }
        mainClock.advanceTimeByFrame()
        assertFalse(windowSecure)

        secure = true
        mainClock.advanceTimeByFrame()
        assertTrue(windowSecure)

        secure = false
        repeat(SECURE_LINGER_FRAMES) {
            mainClock.advanceTimeByFrame()
            assertTrue(windowSecure, "released in frame ${it + 1}")
        }
        // Then it does let go: the resume and the recomposition after it take a frame or two more.
        repeat(4) { mainClock.advanceTimeByFrame() }
        assertFalse(windowSecure)
    }

    @Test
    fun aRevealDuringTheLingerStartsItAgain() = runComposeUiTest {
        mainClock.autoAdvance = false
        var secure by mutableStateOf(true)
        var windowSecure = false
        setContent { windowSecure = rememberLingeringSecure(secure) }
        mainClock.advanceTimeByFrame()

        secure = false
        mainClock.advanceTimeByFrame()
        secure = true
        mainClock.advanceTimeByFrame()
        // Masked again: a fresh linger, not what was left of the first one.
        secure = false
        mainClock.advanceTimeByFrame()
        assertTrue(windowSecure)
        repeat(4) { mainClock.advanceTimeByFrame() }
        assertFalse(windowSecure)
    }
}
