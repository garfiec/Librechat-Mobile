package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.OwnerText
import com.garfiec.librechat.feature.chat.viewmodel.delegate.needsOwnerText
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ownerTextStateFor
import com.garfiec.librechat.feature.chat.viewmodel.delegate.ownerTextSupported
import com.garfiec.librechat.feature.chat.viewmodel.delegate.protectedTurns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.8.9-rc1 PII owner text: accepted only for the exact stored turn, shown only to its author. */
class OwnerTextTest {

    private val turn = Message(
        messageId = "u1",
        conversationId = "c1",
        text = "mail [EMAIL]",
        isCreatedByUser = true,
        privacyRevision = "r1",
    )

    private fun answered(vararg texts: OwnerText) = texts.associateBy { it.messageId }

    @Test
    fun an_original_counts_only_for_the_same_revision_and_filtered_text() {
        assertEquals(
            OwnerTextState.Loaded("mail maya@x.com", "r1", "mail [EMAIL]"),
            ownerTextStateFor(turn, answered(OwnerText("u1", "r1", "mail [EMAIL]", "mail maya@x.com"))),
        )
        assertEquals(
            OwnerTextState.Unavailable,
            ownerTextStateFor(turn, answered(OwnerText("u1", "r0", "mail [EMAIL]", "old"))),
        )
        assertEquals(
            OwnerTextState.Unavailable,
            ownerTextStateFor(turn, answered(OwnerText("u1", "r1", "other text", "x"))),
        )
        // The server couldn't open it: a row without text.
        assertEquals(OwnerTextState.Unavailable, ownerTextStateFor(turn, answered(OwnerText("u1", "r1", "mail [EMAIL]"))))
    }

    @Test
    fun a_failed_request_is_unavailable_and_an_unknown_id_is_no_state() {
        assertEquals(OwnerTextState.Unavailable, ownerTextStateFor(turn, answered = null))
        assertNull(ownerTextStateFor(turn, answered()))
    }

    @Test
    fun the_view_never_goes_blank_and_says_why() {
        assertEquals(PrivateTextView("mail [EMAIL]", null), privateTextView(turn, null))
        assertEquals(PrivateTextView("mail [EMAIL]", PrivateTextPhase.LOADING), privateTextView(turn, OwnerTextState.Loading))
        assertEquals(
            PrivateTextView("mail [EMAIL]", PrivateTextPhase.UNAVAILABLE),
            privateTextView(turn, OwnerTextState.Unavailable),
        )
        assertEquals(
            PrivateTextView("mail maya@x.com", PrivateTextPhase.SHOWN),
            privateTextView(turn, OwnerTextState.Loaded("mail maya@x.com", "r1", "mail [EMAIL]")),
        )
        // Re-redacted since it loaded: the stale original is not shown.
        assertEquals(
            PrivateTextPhase.UNAVAILABLE,
            privateTextView(turn.copy(privacyRevision = "r2"), OwnerTextState.Loaded("x", "r1", "mail [EMAIL]")).phase,
        )
    }

    @Test
    fun a_turn_is_asked_about_until_answered_for_what_it_is_now() {
        assertTrue(needsOwnerText(turn, null))
        assertTrue(needsOwnerText(turn, OwnerTextState.Loading))
        assertFalse(needsOwnerText(turn, OwnerTextState.Unavailable))
        assertFalse(needsOwnerText(turn, OwnerTextState.Loaded("x", "r1", "mail [EMAIL]")))
        assertTrue(needsOwnerText(turn, OwnerTextState.Loaded("x", "r0", "mail [EMAIL]")))
    }

    @Test
    fun only_the_open_chats_redacted_user_turns_are_protected() {
        val state = ChatUiState(
            conversation = ConversationMetaState(conversationId = "c1"),
            content = MessagesState(
                messages = listOf(
                    turn,
                    turn.copy(messageId = "u2", privacyRevision = null),
                    turn.copy(messageId = "a1", isCreatedByUser = false),
                    turn.copy(messageId = "u3", conversationId = "other"),
                ),
            ),
        )

        assertEquals(listOf("u1"), protectedTurns(state).map { it.messageId })
    }

    @Test
    fun the_route_is_refused_only_on_a_server_known_to_be_older() {
        assertTrue(ownerTextSupported(ChatUiState()))
        assertTrue(ownerTextSupported(ChatUiState(gates = FeatureGatesState(backendVersion = "0.8.9-rc1"))))
        assertFalse(ownerTextSupported(ChatUiState(gates = FeatureGatesState(backendVersion = "0.8.8"))))
    }

    @Test
    fun a_redacted_server_copy_retires_the_handed_off_seed_though_its_text_differs() {
        val seed = Message(messageId = "local", conversationId = "c1", text = "mail maya@x.com", isCreatedByUser = true)

        assertTrue(turn.copy(messageId = "server").isServerCopyOf(seed))
        assertFalse(turn.copy(messageId = "server", privacyRevision = null).isServerCopyOf(seed))
    }
}
