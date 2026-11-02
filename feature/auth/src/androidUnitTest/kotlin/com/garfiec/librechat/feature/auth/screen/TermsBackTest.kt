package com.garfiec.librechat.feature.auth.screen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TermsScreen hands this one lambda to its back handler (system and predictive back), its top-bar
 * arrow and its Decline button, so this is what each of them does.
 */
class TermsBackTest {

    @Test
    fun `back on required terms declines`() {
        var declines = 0
        declineUnlessBusy(isAccepting = false, isDeclining = false) { declines++ }()
        assertThat(declines).isEqualTo(1)
    }

    @Test
    fun `back does nothing while a decline or an accept is in flight`() {
        var declines = 0
        declineUnlessBusy(isAccepting = false, isDeclining = true) { declines++ }()
        declineUnlessBusy(isAccepting = true, isDeclining = false) { declines++ }()
        assertThat(declines).isEqualTo(0)
    }
}
