package com.garfiec.librechat.feature.chat.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechSegmentsTest {

    @Test
    fun aShortReplyIsOneRequest() {
        assertEquals(listOf("Hello there."), splitForSpeech("Hello there."))
    }

    @Test
    fun aBlankReplyIsNoRequest() {
        assertEquals(emptyList(), splitForSpeech("   "))
    }

    @Test
    fun aLongReplyIsCutAtSentencesAndLosesNothing() {
        val sentence = "This sentence is exactly forty chars...."
        val text = sentence.repeat(300)

        val segments = splitForSpeech(text)

        assertEquals(text, segments.joinToString(""))
        assertTrue(segments.size > 1)
        assertTrue(segments.all { it.length <= SERVER_TTS_SEGMENT_CHARS })
        assertTrue(segments.dropLast(1).all { it.endsWith(".") })
    }

    @Test
    fun theFirstPieceTakesItsOwnSmallerLimit() {
        val sentence = "This sentence is exactly forty chars...."
        val text = sentence.repeat(300)

        val segments = splitForSpeech(text, maxChars = 400, firstMaxChars = 100)

        assertEquals(text, segments.joinToString(""))
        assertEquals(sentence.repeat(2), segments.first())
        assertTrue(segments.drop(1).dropLast(1).all { it.length == 400 })
    }

    @Test
    fun withoutSeparatorsItCutsAtASpace() {
        val text = "word ".repeat(30)

        val segments = splitForSpeech(text, maxChars = 12)

        assertEquals(text, segments.joinToString(""))
        assertTrue(segments.all { it.length <= 12 && it.endsWith(" ") })
    }

    @Test
    fun aHardCutNeverSplitsASurrogatePair() {
        val text = "a" + "😀".repeat(10)

        val segments = splitForSpeech(text, maxChars = 4)

        assertEquals(text, segments.joinToString(""))
        assertTrue(segments.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
    }

    @Test
    fun theSegmentStaysUnderTheServersDefaultCap() {
        assertTrue(SERVER_TTS_SEGMENT_CHARS < SERVER_TTS_MAX_INPUT_CHARS)
    }
}
