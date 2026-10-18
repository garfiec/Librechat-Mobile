package com.garfiec.librechat.core.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview at the narrow widths and large font scales where layouts run out of room: a
 * 320dp phone, an iPhone SE's 375pt, and a translation that runs longer than English. Every
 * width is a phone below the 600dp split, so these always show the compact layouts.
 *
 * Android-only because the common `@Preview` takes no `fontScale`; the composables it previews
 * are the shared ones, so what renders here is what iOS lays out at the same width and scale.
 */
@Preview(name = "320dp · 1.0x", group = "narrow", widthDp = 320, showBackground = true)
@Preview(name = "320dp · 2.0x", group = "narrow", widthDp = 320, fontScale = 2f, showBackground = true)
@Preview(name = "375dp · 1.0x", group = "narrow", widthDp = 375, showBackground = true)
@Preview(name = "375dp · 1.5x", group = "narrow", widthDp = 375, fontScale = 1.5f, showBackground = true)
@Preview(name = "375dp · 1.3x · de", group = "narrow", widthDp = 375, fontScale = 1.3f, locale = "de", showBackground = true)
annotation class PreviewNarrowScreens
