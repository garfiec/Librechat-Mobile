package com.garfiec.librechat.core.ui.components.topbar

import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.accessibilityLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The native glass bar honours [BarAction.Icon.busy] the way the Compose bars do: a spinner, not a dimmed icon. */
class NativeGlassTopBarBusyIosTest {

    private fun spec(busy: Boolean, enabled: Boolean = true) = AdaptiveTopBarSpec(
        navigation = null,
        title = BarTitle("Agent"),
        actions = listOf(
            BarAction.Icon(id = "more", icon = BarIcons.More, label = "More", enabled = enabled, busy = busy, onClick = {}),
        ),
    )

    @Test
    fun busyIconIsSnapshottedAsBusyAndDisabled() {
        val action = spec(busy = true).snapshot().actions.single()

        assertTrue(action.busy)
        assertFalse(action.enabled)
    }

    @Test
    fun idleIconKeepsItsEnabledState() {
        val enabled = spec(busy = false).snapshot().actions.single()
        val disabled = spec(busy = false, enabled = false).snapshot().actions.single()

        assertFalse(enabled.busy)
        assertTrue(enabled.enabled)
        assertFalse(disabled.enabled)
    }

    @Test
    fun busyFlipChangesTheSnapshotSoTheBarRebuilds() {
        assertTrue(spec(busy = true).snapshot() != spec(busy = false).snapshot())
    }

    @Test
    fun busyButtonIsAnAnimatingSpinnerWithTheActionLabel() {
        val item = busyBarButton(label = "More", tint = null)

        val indicator = assertIs<UIActivityIndicatorView>(item.customView)
        assertTrue(indicator.isAnimating())
        assertEquals("More", item.accessibilityLabel)
    }
}
