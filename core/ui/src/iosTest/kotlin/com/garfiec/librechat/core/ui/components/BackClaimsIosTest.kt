package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An enabled [PlatformBackHandler] claims back on the shell's [BackClaims], which turns the phone
 * drawer's swipe off so the iOS edge swipe reaches the handler (a dirty editor's discard prompt).
 */
@OptIn(ExperimentalTestApi::class)
class BackClaimsIosTest {

    @Test
    fun anEnabledHandlerClaimsUntilDisabledOrGone() = runComposeUiTest {
        val claims = BackClaims()
        var dirty by mutableStateOf(false)
        var shown by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalBackClaims provides claims) {
                if (shown) PlatformBackHandler(enabled = dirty, onBack = {})
            }
        }
        waitForIdle()
        assertFalse(claims.isClaimed)

        dirty = true
        waitForIdle()
        assertTrue(claims.isClaimed)

        dirty = false
        waitForIdle()
        assertFalse(claims.isClaimed)

        dirty = true
        waitForIdle()
        shown = false
        waitForIdle()
        assertFalse(claims.isClaimed)
    }
}
