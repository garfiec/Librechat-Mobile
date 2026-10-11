package com.garfiec.librechat.core.data.datastore

/**
 * Mobile-only preference controlling what the chat floating top bar's bubble shows.
 *
 * - [TITLE] — the conversation title (the historical behavior), with long-press to edit in place.
 * - [MODEL] — the currently selected model/agent label; tapping opens the model selector.
 * - [NONE] — no bubble at all (minimal). The hamburger, overflow menu, and scrim still render.
 */
enum class ChatHeaderContent {
    TITLE, MODEL, NONE;

    companion object {
        fun fromString(value: String?): ChatHeaderContent = when (value) {
            "title" -> TITLE
            "model" -> MODEL
            "none" -> NONE
            else -> DEFAULT
        }

        /**
         * TCHAT: Tchat names the model in the bar unless the user picks otherwise, so the model in
         * use is always visible (design/model-selection-requirements.md, D3). Upstream: [TITLE].
         */
        val DEFAULT: ChatHeaderContent = MODEL
    }

    fun toStorageString(): String = when (this) {
        TITLE -> "title"
        MODEL -> "model"
        NONE -> "none"
    }
}
