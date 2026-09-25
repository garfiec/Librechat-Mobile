package com.garfiec.librechat.core.model.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamErrorTypeTest {

    /**
     * The exact shape a v0.8.8-rc1 model-not-found failure arrives in: not a typed payload at
     * all, but provider JSON with a LangChain troubleshooting URL buried in the prose.
     */
    private val modelNotFoundInBand = """
        {"error":{"message":"404 {\"error\":{\"message\":\"The model `gpt-5-nonexistent` does not
        exist or you do not have access to it.\",\"type\":\"invalid_request_error\"}}
        Troubleshooting URL: https://js.langchain.com/docs/troubleshooting/errors/MODEL_NOT_FOUND/"}}
    """.trimIndent()

    @Test
    fun the_in_band_error_part_classifies_the_same_as_the_stream_end_reason() {
        // The failure reaches the user twice over: as the reason the run ended, and as an `error`
        // content part on the persisted assistant message. rc1 persists that message with
        // `error: false` and no text, so on a reopened conversation the part is the ONLY record
        // of it. Classify at one site only and the thread renders the raw payload above while the
        // snackbar shows the actionable sentence for the very same error.
        assertEquals(StreamErrorType.MODEL_NOT_FOUND, StreamErrorType.parse(modelNotFoundInBand))
        assertEquals(StreamErrorType.MODEL_NOT_FOUND.marker, StreamErrorType.markerOrText(modelNotFoundInBand))
    }

    @Test
    fun an_unrecognized_payload_keeps_the_servers_own_text() {
        // The contract, not a fallback: a newer server's code must reach the user as the server's
        // sentence, never as a bare identifier or an empty string.
        val future = """{"type":"some_code_this_build_has_never_heard_of","message":"Upstream said no"}"""
        assertNull(StreamErrorType.parse(future))
        assertEquals(future, StreamErrorType.markerOrText(future))

        val prose = "The provider is temporarily unavailable. Try again shortly."
        assertEquals(prose, StreamErrorType.markerOrText(prose))
    }

    @Test
    fun a_typed_payload_still_classifies_through_the_shared_entry_point() {
        val typed = """{"type":"resource_recovery_required","message":"Files could not be restored"}"""
        assertEquals(
            StreamErrorType.RESOURCE_RECOVERY_REQUIRED.marker,
            StreamErrorType.markerOrText(typed),
        )
    }

    @Test
    fun a_blank_error_part_stays_blank() {
        // `part.error ?: part.text.orEmpty()` can legitimately be empty; classifying it must not
        // invent a marker, and the render site draws nothing rather than an empty localized card.
        assertNull(StreamErrorType.parse(""))
        assertEquals("", StreamErrorType.markerOrText(""))
    }

    @Test
    fun the_url_pattern_does_not_fire_on_a_neighbouring_langchain_error_code() {
        // The rc1 fallback is matched by URL, which makes over-matching the risk: every LangChain
        // troubleshooting link shares the prefix.
        val other = "Troubleshooting URL: https://js.langchain.com/docs/troubleshooting/errors/INVALID_TOOL_RESULTS/"
        assertNull(StreamErrorType.parse(other))
    }

    @Test
    fun the_v088_rc2_codes_classify_off_the_typed_payload() {
        // Wire values copied from upstream `ErrorTypes` (packages/data-provider/src/config.ts),
        // except SHARE_LIMIT, which is a `ViolationTypes` member upstream's error registry keys
        // off the same `type` field.
        val expected = mapOf(
            "model_rate_limit" to StreamErrorType.MODEL_RATE_LIMIT,
            "final_context_overflow" to StreamErrorType.FINAL_CONTEXT_OVERFLOW,
            "compaction_skipped" to StreamErrorType.COMPACTION_SKIPPED,
            "compaction_failed" to StreamErrorType.COMPACTION_FAILED,
            "auth_rate_limited" to StreamErrorType.AUTH_RATE_LIMITED,
            "auth_banned" to StreamErrorType.AUTH_BANNED,
            "auth_cross_origin" to StreamErrorType.AUTH_CROSS_ORIGIN,
            "share_limit" to StreamErrorType.SHARE_LIMIT,
        )
        expected.forEach { (wire, type) ->
            assertEquals(type, StreamErrorType.parse("""{"type":"$wire","message":"server text"}"""))
            assertEquals(type.marker, StreamErrorType.markerOrText("""{"type":"$wire"}"""))
        }
    }

    /**
     * The shape rc2's terminal-run handler actually composes: prose, a newline, then the payload
     * (`packages/api/src/agents/failures/terminal.ts`). A case built from a bare JSON object
     * passes whether or not `parse` can find an EMBEDDED payload, which is how a mapped type can
     * still reach the user as raw JSON in the assistant message body.
     */
    @Test
    fun a_payload_prefixed_with_prose_still_classifies() {
        val raw = "The model provider could not complete this request.\n" +
            """{"type":"upstream_model_error","status":503}"""

        assertEquals(StreamErrorType.UPSTREAM_MODEL_ERROR, StreamErrorType.parse(raw))
        assertEquals(StreamErrorType.UPSTREAM_MODEL_ERROR.marker, StreamErrorType.markerOrText(raw))
    }

    /** Braces in the prose must not swallow the payload: the span is BALANCED, not outermost-pair. */
    @Test
    fun braces_in_the_prose_do_not_break_the_extraction() {
        val raw = """Template {placeholder} failed. {"type":"empty_messages"}"""

        assertEquals(StreamErrorType.EMPTY_MESSAGES, StreamErrorType.parse(raw))
    }

    /**
     * Upstream reads `code ?? type` and then unwraps an `error` envelope.
     * `CodeWorkspaceSelectionError` names itself under `code` and never under `type`.
     */
    @Test
    fun the_identifier_is_read_from_code_and_from_an_error_envelope() {
        assertEquals(
            StreamErrorType.CODE_WORKSPACE_UNAVAILABLE,
            StreamErrorType.parse("""{"code":"code_workspace_unavailable","status":409}"""),
        )
        assertEquals(
            StreamErrorType.MODERATION,
            StreamErrorType.parse("""{"error":{"type":"moderation"}}"""),
        )
    }

    /** A message with no payload at all still degrades to the server's own text. */
    @Test
    fun prose_with_no_payload_is_left_alone() {
        val raw = "Something went wrong."

        assertNull(StreamErrorType.parse(raw))
        assertEquals(raw, StreamErrorType.markerOrText(raw))
    }

    @Test
    fun markers_round_trip_through_the_wire_value() {
        // `localizedStreamError` resolves a marker by matching the enum on `wire`, so a marker
        // this side mints has to be resolvable by that lookup for every entry.
        StreamErrorType.entries.forEach { type ->
            assertEquals(type.wire, type.marker.removePrefix(StreamErrorType.MARKER_PREFIX))
        }
    }
}
