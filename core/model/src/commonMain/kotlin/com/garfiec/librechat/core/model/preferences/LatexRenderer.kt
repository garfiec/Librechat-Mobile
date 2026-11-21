package com.garfiec.librechat.core.model.preferences

enum class LatexRenderer {
    NATIVE, KATEX;

    companion object {
        fun fromString(value: String?): LatexRenderer = when (value) {
            "native" -> NATIVE
            else -> KATEX
        }
    }

    fun toStorageString(): String = when (this) {
        NATIVE -> "native"
        KATEX -> "katex"
    }
}
