package com.garfiec.librechat.shared

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * Writes [text] to the unified OS log as-is. Use this instead of calling `NSLog` directly, which
 * crashes in two ways from Kotlin:
 * - Kotlin text in the format string has its `%` sequences read as specifiers (a `%@` in a log
 *   message reads a garbage argument);
 * - a Kotlin `String` passed as a vararg is not converted to an `NSString`, so `%@` reads it as
 *   an invalid object (#533).
 */
@OptIn(BetaInteropApi::class)
internal fun nsLogVerbatim(text: String) {
    NSLog("%@", NSString.create(string = text))
}
