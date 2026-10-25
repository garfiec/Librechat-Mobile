package com.garfiec.librechat.feature.chat.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictationBufferTest {

    /** Feeds results like the platforms do: the composer is whatever the buffer last returned. */
    private class Session(initial: String = "") {
        val buffer = DictationBuffer().apply { begin(initial) }
        var field = initial

        fun partial(transcript: String, utteranceEnded: Boolean = false) {
            field = buffer.merge(field, transcript, utteranceEnded)
        }

        fun commit(transcript: String) {
            field = buffer.commit(field, transcript)
        }

        fun commitCurrent() {
            field = buffer.commitCurrent(field)
        }
    }

    @Test
    fun partialsAppendAfterPreTypedText() {
        val s = Session("Note:")
        s.partial("hello")
        s.partial("hello world")
        assertEquals("Note: hello world", s.field)
    }

    @Test
    fun blankPartialKeepsTheShownWords() {
        val s = Session()
        s.partial("hello world")
        s.partial("")
        assertEquals("hello world", s.field)
        s.partial("hello world again")
        assertEquals("hello world again", s.field)
    }

    @Test
    fun blankCommitCommitsTheLastPartial() {
        val s = Session()
        s.partial("first segment")
        s.commit("")
        assertEquals("first segment", s.field)
        s.partial("second")
        assertEquals("first segment second", s.field)
    }

    @Test
    fun blankFinalAfterStopDoesNotEmptyTheField() {
        // iOS merges (not commits) the final that follows a user stop.
        val s = Session()
        s.partial("keep this")
        s.partial("   ")
        assertEquals("keep this", s.field)
        assertTrue(s.buffer.producedText)
    }

    @Test
    fun blankOnlySessionProducesNoText() {
        val s = Session("draft")
        s.partial("")
        s.commit("")
        assertEquals("draft", s.field)
        assertFalse(s.buffer.producedText)
    }

    @Test
    fun commitCurrentSurvivesARestartedTask() {
        // Cloud fallback: the new task's transcript starts over from nothing.
        val s = Session()
        s.partial("on device words")
        s.commitCurrent()
        s.partial("cloud")
        assertEquals("on device words cloud", s.field)
    }

    @Test
    fun commitCurrentWithNothingLiveIsANoOp() {
        val s = Session("typed")
        s.commitCurrent()
        assertEquals("typed", s.field)
        s.partial("then spoke")
        assertEquals("typed then spoke", s.field)
    }

    @Test
    fun resetAfterUtteranceBoundaryCommitsThePreviousTranscript() {
        val s = Session()
        s.partial("I went to the store")
        s.partial("I went to the store today", utteranceEnded = true)
        s.partial("Then")
        s.partial("Then I came home")
        assertEquals("I went to the store today Then I came home", s.field)
    }

    @Test
    fun revisionAfterUtteranceBoundaryIsNotDuplicated() {
        val s = Session()
        s.partial("i went to the store today", utteranceEnded = true)
        s.partial("I went to the store, today. Then")
        assertEquals("I went to the store, today. Then", s.field)
    }

    @Test
    fun withoutABoundaryARestartIsTreatedAsARevision() {
        // Mid-utterance rewrites look like restarts; only the boundary signal may commit.
        val s = Session()
        s.partial("their going")
        s.partial("They're going")
        assertEquals("They're going", s.field)
    }

    @Test
    fun finalAfterBoundaryResetKeepsBothSegments() {
        val s = Session()
        s.partial("first thought", utteranceEnded = true)
        s.commit("second")
        assertEquals("first thought second", s.field)
        s.partial("third")
        assertEquals("first thought second third", s.field)
    }

    @Test
    fun continuationFinalAfterBoundaryIsNotDuplicated() {
        val s = Session()
        s.partial("a b", utteranceEnded = true)
        s.commit("a b c")
        assertEquals("a b c", s.field)
    }

    @Test
    fun commitCurrentWhileArmedCommitsOnce() {
        val s = Session()
        s.partial("mid utterance", utteranceEnded = true)
        s.commitCurrent()
        s.partial("cloud")
        assertEquals("mid utterance cloud", s.field)
    }

    @Test
    fun hasLiveTranscriptTracksUncommittedWords() {
        // IosVoiceInput reads this before a blank final to count the segment as heard, not empty.
        val s = Session()
        assertFalse(s.buffer.hasLiveTranscript)
        s.partial("spoken")
        assertTrue(s.buffer.hasLiveTranscript)
        s.partial("")
        assertTrue(s.buffer.hasLiveTranscript)
        s.commit("")
        assertFalse(s.buffer.hasLiveTranscript)
        assertEquals("spoken", s.field)
    }

    @Test
    fun userEditMidDictationSurvives() {
        val s = Session()
        s.partial("hello world")
        s.field = "hello world!"
        s.partial("next")
        assertEquals("hello world! next", s.field)
    }

    @Test
    fun userEditSurvivesBlankPartialAndBoundary() {
        val s = Session()
        s.partial("one two", utteranceEnded = true)
        s.field = "Intro: one two"
        s.partial("")
        assertEquals("Intro: one two", s.field)
        s.partial("three")
        assertEquals("Intro: one two three", s.field)
    }

    @Test
    fun androidPerUtteranceCommitFlowIsUnchanged() {
        val s = Session("Hi")
        s.partial("this is")
        s.partial("this is one")
        s.commit("this is one")
        s.partial("and two")
        s.commit("and two")
        assertEquals("Hi this is one and two", s.field)
    }

    @Test
    fun revertRestoresOriginalOnlyWithoutEdits() {
        val s = Session("orig")
        s.partial("spoken")
        assertEquals("orig", s.buffer.revert(s.field))
        s.field = "edited"
        assertEquals("edited", s.buffer.revert(s.field))
    }

    @Test
    fun continuationIgnoresCasePunctuationAndTheLastWord() {
        assertTrue(isTranscriptContinuation("i went to their", "I went to there, then"))
        assertTrue(isTranscriptContinuation("", "anything"))
        assertTrue(isTranscriptContinuation("hello", "Hello, world"))
        assertFalse(isTranscriptContinuation("I went to the store", "Then I came home"))
        assertFalse(isTranscriptContinuation("hello", "goodbye"))
        assertFalse(isTranscriptContinuation("two words", ""))
    }

    @Test
    fun singleSpacedWordMustOpenTheNextTranscriptUnchanged() {
        assertTrue(isTranscriptContinuation("hello", "hello world"))
        assertTrue(isTranscriptContinuation("So,", "so I went"))
        assertFalse(isTranscriptContinuation("So", "Sometimes I"))
        assertFalse(isTranscriptContinuation("I", "I'm going"))
    }

    @Test
    fun oneWordUtteranceSurvivesASimilarRestart() {
        val s = Session()
        s.partial("So", utteranceEnded = true)
        s.partial("Sometimes")
        assertEquals("So Sometimes", s.field)
    }

    @Test
    fun continuationOfUnspacedText() {
        assertTrue(isTranscriptContinuation("你好世界", "你好世界今天"))
        assertTrue(isTranscriptContinuation("你好世界", "你好世纪"))
        assertFalse(isTranscriptContinuation("你好世界", "今天天气"))
    }

    @Test
    fun blankCommitAfterEditKeepsTheEditWithoutDuplicating() {
        val s = Session()
        s.partial("hello world")
        s.field = "hello world!"
        s.commit("")
        assertEquals("hello world!", s.field)
        s.partial("next")
        assertEquals("hello world! next", s.field)
    }

    @Test
    fun commitCurrentAfterDeletingTheTranscriptKeepsItDeleted() {
        val s = Session("draft")
        s.partial("oops")
        s.field = "draft"
        s.commitCurrent()
        assertEquals("draft", s.field)
    }

    @Test
    fun repeatedPartialAtBoundaryStaysArmed() {
        val s = Session()
        s.partial("I went to the store today", utteranceEnded = true)
        s.partial("I went to the store today")
        s.partial("I went to the store, today.")
        s.partial("Then")
        assertEquals("I went to the store, today. Then", s.field)
    }

    @Test
    fun boundaryContinuedWithNewWordsDisarms() {
        val s = Session()
        s.partial("their going to", utteranceEnded = true)
        s.partial("their going to the park")
        s.partial("They're going to the park")
        assertEquals("They're going to the park", s.field)
    }

    @Test
    fun classifiesRecognitionErrors() {
        assertEquals(RecognitionErrorKind.NO_SPEECH, classifyRecognitionError("kAFAssistantErrorDomain", 1110))
        assertEquals(RecognitionErrorKind.CANCELLED, classifyRecognitionError("kAFAssistantErrorDomain", 216))
        assertEquals(RecognitionErrorKind.CANCELLED, classifyRecognitionError("kLSRErrorDomain", 301))
        assertEquals(RecognitionErrorKind.FAILURE, classifyRecognitionError("kLSRErrorDomain", 1110))
        assertEquals(RecognitionErrorKind.FAILURE, classifyRecognitionError("kAFAssistantErrorDomain", 1101))
    }
}
