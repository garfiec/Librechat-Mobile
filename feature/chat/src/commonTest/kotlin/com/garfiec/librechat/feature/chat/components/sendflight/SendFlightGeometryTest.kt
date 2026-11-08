package com.garfiec.librechat.feature.chat.components.sendflight

import androidx.compose.ui.geometry.Rect
import com.garfiec.librechat.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SendFlightGeometryTest {
    private fun drop(bottom: Float, width: Float = 200f) = Rect(left = 50f, top = bottom - 60f, right = 50f + width, bottom = bottom)

    @Test
    fun wordsAreTheRunsBetweenWhitespace() {
        val text = "Yes,  train\nonly. "
        assertEquals(listOf("Yes,", "train", "only."), wordRanges(text).map { text.substring(it) })
        assertTrue(wordRanges(" \n ").isEmpty())
    }

    @Test
    fun theSentRowIsANewUserTailWithTheSameWords() {
        val sent = Message(messageId = "new", conversationId = "c", text = "  hello there \n", isCreatedByUser = true)
        assertTrue(isSentRow("hello there", knownTailId = "old", tail = sent))
        assertFalse(isSentRow("hello there", knownTailId = "new", tail = sent), "the row that was already the tail")
        assertFalse(isSentRow("hello", knownTailId = "old", tail = sent), "other words")
        assertFalse(isSentRow("hello there", knownTailId = "old", tail = sent.copy(isCreatedByUser = false)))
        assertFalse(isSentRow("hello there", knownTailId = "old", tail = null))
    }

    @Test
    fun theNeckExistsOnlyBetweenLeavingTheBoxAndTheSnap() {
        fun neck(gap: Float) = budNeck(
            boxTop = 500f, boxLeft = 0f, boxRight = 400f, drop = drop(500f - gap),
            snapPx = 40f, maxTopWidth = 120f, cornerPx = 16f,
        )
        assertNull(neck(0f), "still inside the box")
        assertNull(neck(-10f))
        assertNotNull(neck(1f))
        assertNotNull(neck(39f))
        assertNull(neck(40f), "snapped")
    }

    @Test
    fun theNeckThinsAsTheGapOpens() {
        fun waist(gap: Float) = budNeck(500f, 0f, 400f, drop(500f - gap), 40f, 120f, 16f)!!.waist
        assertTrue(waist(5f) > waist(20f))
        assertTrue(waist(20f) > waist(35f))
        assertTrue(waist(35f) > 0f)
    }

    @Test
    fun theNeckMeetsOnlyTheDropsStraightEdgeAndItsBaseStaysOnTheBox() {
        val narrow = budNeck(500f, 0f, 400f, drop(490f, width = 60f), 40f, 120f, 16f)!!
        assertTrue(narrow.topWidth <= 60f - 2 * 16f)
        val atEdge = budNeck(500f, 0f, 400f, Rect(-40f, 430f, 20f, 490f), 40f, 120f, 16f)
        if (atEdge != null) assertTrue(atEdge.baseCenter - atEdge.baseWidth / 2 >= 0f)
        assertNull(budNeck(500f, 0f, 400f, drop(490f, width = 30f), 40f, 120f, 16f), "no straight edge to meet")
    }

    @Test
    fun theBoxEdgeIsPulledOnlyWhileJoinedAndNeverPastItsLimit() {
        assertEquals(0f, recoilFor(gap = 0f, snapPx = 40f, maxRecoil = 7f))
        assertEquals(0f, recoilFor(gap = 40f, snapPx = 40f, maxRecoil = 7f))
        assertEquals(4.5f, recoilFor(gap = 10f, snapPx = 40f, maxRecoil = 7f))
        assertEquals(7f, recoilFor(gap = 30f, snapPx = 40f, maxRecoil = 7f))
    }
}
