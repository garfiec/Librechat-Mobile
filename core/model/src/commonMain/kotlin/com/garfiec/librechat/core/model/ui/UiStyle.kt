package com.garfiec.librechat.core.model.ui

/**
 * No stored choice means the platform's idiom (`platformDefaultUiStyle()` in `:core:ui`), so the
 * default follows the OS rather than being written down.
 */
enum class UiStyle {
    MATERIAL,
    LIQUID_GLASS,
}
