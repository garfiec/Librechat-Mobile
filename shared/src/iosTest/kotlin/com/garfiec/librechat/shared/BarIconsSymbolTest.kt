package com.garfiec.librechat.shared

import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import platform.UIKit.UIImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A misspelled SF Symbol name compiles, passes every structural check and renders as an empty bar
 * button. Only asking UIKit for the image catches it.
 */
class BarIconsSymbolTest {

    @Test
    fun everyBarIconNamesARealSystemSymbol() {
        val missing = BarIcons.all.map { it.sfSymbol }.filter { UIImage.systemImageNamed(it) == null }
        assertTrue(missing.isEmpty(), "unknown SF Symbols: $missing")
    }
}
