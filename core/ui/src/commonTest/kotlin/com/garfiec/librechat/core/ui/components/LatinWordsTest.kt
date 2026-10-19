package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A choice label's words that must each fit a line, since the line breaker only splits them at
 * spaces. Scripts it splits between characters (CJK, Hangul) never count.
 */
class LatinWordsTest {

    @Test
    fun latinLabelsSplitAtSpaces() {
        assertEquals(listOf("Toute", "la", "discussion"), latinWords("Toute la discussion"))
        assertEquals(listOf("shadcn/ui"), latinWords("shadcn/ui"))
    }

    @Test
    fun repeatedSpacesLeaveNoEmptyWords() {
        assertEquals(listOf("API", "Key"), latinWords(" API  Key "))
    }

    @Test
    fun cjkWordsBreakAnywhere() {
        assertEquals(emptyList(), latinWords("アーティファクト"))
        assertEquals(emptyList(), latinWords("全部"))
    }

    @Test
    fun hangulBreaksBetweenSyllables() {
        assertEquals(emptyList(), latinWords("시스템 기본값"))
    }

    @Test
    fun mixedLabelsKeepOnlyTheLatinWords() {
        assertEquals(listOf("128"), latinWords("すべて 128"))
    }

    @Test
    fun anEmojiDoesNotMakeAWordBreakable() {
        assertEquals(listOf("Party🎉"), latinWords("Party🎉"))
        assertEquals(listOf("🎉"), latinWords("🎉"))
    }
}
