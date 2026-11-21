package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.ui.text.TextRange

/**
 * The drawer's view position, kept for the next drawer composed: the search field's selection and
 * the list's scroll. Plain fields, written as they change and read only when a drawer is created,
 * so nothing recomposes on them.
 */
class DrawerViewMemory {
    /** The search field's selection; null until the user has typed, so a new field puts it at the end. */
    var searchSelection: TextRange? = null
    var listIndex: Int = 0
    var listOffset: Int = 0
}
