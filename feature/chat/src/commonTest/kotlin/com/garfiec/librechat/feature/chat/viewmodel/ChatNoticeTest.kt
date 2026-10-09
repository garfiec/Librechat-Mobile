package com.garfiec.librechat.feature.chat.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatNoticeTest {

    @Test
    fun everyNoticeRoundTripsWithoutArgs() {
        ChatNotice.entries.forEach { notice ->
            assertEquals(ChatNotice.Parsed(notice, emptyList()), ChatNotice.parse(notice.marker()))
        }
    }

    @Test
    fun argsRoundTripInOrder() {
        val marker = ChatNotice.UPLOAD_TOO_LARGE.marker("photo: final.png", "20 MB")
        assertEquals(
            ChatNotice.Parsed(ChatNotice.UPLOAD_TOO_LARGE, listOf("photo: final.png", "20 MB")),
            ChatNotice.parse(marker),
        )
    }

    @Test
    fun numericArgsAreCarriedAsText() {
        assertEquals(listOf("12"), ChatNotice.parse(ChatNotice.FAVORITES_LIMIT.marker(12))?.args)
    }

    @Test
    fun uploadFailedFallsBackToTheUnknownNoticeWithoutAReason() {
        assertEquals(ChatNotice.UPLOAD_FAILED_UNKNOWN.marker("a.pdf"), ChatNotice.uploadFailed("a.pdf", null))
        assertEquals(ChatNotice.UPLOAD_FAILED_UNKNOWN.marker("a.pdf"), ChatNotice.uploadFailed("a.pdf", " "))
        assertEquals(ChatNotice.UPLOAD_FAILED.marker("a.pdf", "Quota exceeded"), ChatNotice.uploadFailed("a.pdf", "Quota exceeded"))
    }

    @Test
    fun anythingElseIsNotANotice() {
        assertNull(ChatNotice.parse("Failed to rename conversation"))
        assertNull(ChatNotice.parse("stream_error:model_not_found"))
        assertNull(ChatNotice.parse("${ChatNotice.MARKER_PREFIX}from_a_newer_build"))
    }
}
