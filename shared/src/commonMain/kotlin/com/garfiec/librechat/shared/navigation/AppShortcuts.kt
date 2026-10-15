package com.garfiec.librechat.shared.navigation

/**
 * App-wide keyboard shortcuts, for the platform hosts to deliver whatever holds focus.
 *
 * Compose dispatches a key only to the focused node and its ancestors, so a handler on the app's
 * shell misses every key pressed while nothing is focused: after leaving a screen whose field had
 * focus, or after a tap on empty space clears it. The Android activity and the iOS host controller
 * see keys before Compose does and call these instead. Main thread only.
 */
object AppShortcuts {

    /** Opens the Search screen (Ctrl/⌘+Shift+F); returns whether it handled the key. Set by the nav host. */
    var openSearch: (() -> Boolean)? = null
}
