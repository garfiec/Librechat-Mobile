package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SanitizeOtpInputTest {

    @Test
    fun typedDigitIsAppended() {
        assertEquals("123", sanitizeOtpInput(old = "12", new = "123"))
    }

    @Test
    fun deleteIsAccepted() {
        assertEquals("12", sanitizeOtpInput(old = "123", new = "12"))
    }

    @Test
    fun pastedCodeIntoEmptyFieldIsAccepted() {
        assertEquals("123456", sanitizeOtpInput(old = "", new = "123456"))
    }

    @Test
    fun formattingInAPasteIsStripped() {
        assertEquals("123456", sanitizeOtpInput(old = "", new = "123 456"))
        assertEquals("123456", sanitizeOtpInput(old = "", new = "123-456\n"))
        assertEquals("123456", sanitizeOtpInput(old = "", new = "Code: 123456"))
    }

    @Test
    fun letterIsDropped() {
        assertEquals("12", sanitizeOtpInput(old = "12", new = "12a"))
    }

    @Test
    fun nonAsciiDigitsAreNormalized() {
        // Arabic-Indic and fullwidth digits: the server only accepts ASCII.
        assertEquals("123456", sanitizeOtpInput(old = "", new = "١٢٣٤٥٦"))
        assertEquals("123", sanitizeOtpInput(old = "", new = "１２３"))
    }

    @Test
    fun partialPasteThatFitsIsAppended() {
        assertEquals("123456", sanitizeOtpInput(old = "12", new = "123456"))
    }

    @Test
    fun fullCodePastedOverAPartialEntryReplacesIt() {
        assertEquals("654321", sanitizeOtpInput(old = "12", new = "12654321"))
    }

    @Test
    fun fullCodePastedOverAStaleFullEntryReplacesIt() {
        assertEquals("654321", sanitizeOtpInput(old = "123456", new = "123456654321"))
    }

    @Test
    fun seventhTypedDigitIsRejected() {
        assertNull(sanitizeOtpInput(old = "123456", new = "1234567"))
    }

    @Test
    fun pasteWithTooManyDigitsIsRejected() {
        assertNull(sanitizeOtpInput(old = "", new = "12345678"))
        assertNull(sanitizeOtpInput(old = "", new = "123456 valid for 30s"))
    }

    @Test
    fun overflowThatIsNotAnAppendIsRejected() {
        assertNull(sanitizeOtpInput(old = "1234", new = "9912345"))
    }
}
