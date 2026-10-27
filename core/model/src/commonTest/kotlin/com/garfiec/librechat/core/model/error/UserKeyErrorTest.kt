package com.garfiec.librechat.core.model.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UserKeyErrorTest {

    @Test
    fun parses_no_user_key_without_endpoint() {
        val result = parseUserKeyError("""{"type":"no_user_key"}""")
        assertEquals(UserKeyError.NoUserKey(null), result)
    }

    @Test
    fun parses_no_user_key_with_endpoint() {
        val result = parseUserKeyError("""{"type":"no_user_key","endpoint":"openAI"}""")
        assertEquals(UserKeyError.NoUserKey("openAI"), result)
    }

    @Test
    fun parses_expired_user_key_preserves_locale_string_verbatim() {
        // Servers before the pin sent expiresAtDate.toLocaleString(), which no ISO parser reads.
        val raw = """{"type":"expired_user_key","expiredAt":"5/1/2026, 12:00:00 AM","endpoint":"openAI"}"""
        val result = parseUserKeyError(raw)
        val expired = assertIs<UserKeyError.ExpiredUserKey>(result)
        assertEquals("openAI", expired.endpoint)
        // Byte-identical preservation: the parser keeps the raw string, and display code shows
        // it unchanged when it isn't an instant.
        assertEquals("5/1/2026, 12:00:00 AM", expired.expiredAt)
        assertEquals("kotlin.String", expired.expiredAt::class.qualifiedName)
    }

    @Test
    fun parses_expired_user_key_with_the_pins_iso_instant() {
        // At the pin (v0.8.8), packages/api/src/utils/key.ts checkUserKeyExpiry sends
        // expiresAtDate.toISOString() for the client to format.
        val raw = """{"type":"expired_user_key","expiredAt":"2026-10-26T14:03:00.000Z","endpoint":"openAI"}"""
        assertEquals(
            UserKeyError.ExpiredUserKey(endpoint = "openAI", expiredAt = "2026-10-26T14:03:00.000Z"),
            parseUserKeyError(raw),
        )
    }

    @Test
    fun expired_user_key_missing_expiredAt_returns_null() {
        // expiredAt is required for the localized snackbar template; endpoint is optional
        // (server omits it on some emission paths, mirroring NoUserKey/InvalidUserKey).
        assertNull(parseUserKeyError("""{"type":"expired_user_key"}"""))
        assertNull(parseUserKeyError("""{"type":"expired_user_key","endpoint":"openAI"}"""))
    }

    @Test
    fun expired_user_key_with_only_expiredAt_parses_with_null_endpoint() {
        val result = parseUserKeyError("""{"type":"expired_user_key","expiredAt":"5/1/2026"}""")
        assertEquals(UserKeyError.ExpiredUserKey(endpoint = null, expiredAt = "5/1/2026"), result)
    }

    @Test
    fun parses_invalid_user_key() {
        val result = parseUserKeyError("""{"type":"invalid_user_key"}""")
        assertEquals(UserKeyError.InvalidUserKey(null), result)
    }

    @Test
    fun parses_no_base_url() {
        // custom/initialize.ts throws `{type: ErrorTypes.NO_BASE_URL}` with no endpoint field.
        assertEquals(UserKeyError.NoBaseUrl(null), parseUserKeyError("""{"type":"no_base_url"}"""))
        assertEquals(
            UserKeyError.NoBaseUrl("openrouter"),
            parseUserKeyError("""{"type":"no_base_url","endpoint":"openrouter"}"""),
        )
    }

    @Test
    fun parses_invalid_base_url() {
        assertEquals(UserKeyError.InvalidBaseUrl(null), parseUserKeyError("""{"type":"invalid_base_url"}"""))
        assertEquals(
            UserKeyError.InvalidBaseUrl("openrouter"),
            parseUserKeyError("""{"type":"invalid_base_url","endpoint":"openrouter"}"""),
        )
    }

    @Test
    fun adversarial_substring_input_returns_null() {
        // The whole reason this is a JSON parser, not a substring match: prevent false
        // positives where an unrelated stream error happens to mention "no_user_key".
        assertNull(parseUserKeyError("foo no_user_key bar"))
        assertNull(parseUserKeyError("Error: expired_user_key occurred at some point"))
    }

    @Test
    fun malformed_json_returns_null() {
        assertNull(parseUserKeyError("{broken json"))
        assertNull(parseUserKeyError("[]"))
        assertNull(parseUserKeyError(""))
    }

    @Test
    fun unknown_type_returns_null() {
        assertNull(parseUserKeyError("""{"type":"some_other_error"}"""))
        assertNull(parseUserKeyError("""{"endpoint":"openAI"}"""))
    }

    @Test
    fun parser_returns_null_when_type_is_jsonarray_without_crash() {
        assertNull(parseUserKeyError("""{"type":[],"endpoint":"openAI"}"""))
        assertNull(parseUserKeyError("""{"type":["no_user_key"],"endpoint":"openAI"}"""))
    }

    @Test
    fun parser_returns_null_when_type_is_jsonobject_without_crash() {
        assertNull(parseUserKeyError("""{"type":{"value":"no_user_key"},"endpoint":"openAI"}"""))
    }

    @Test
    fun parser_degrades_gracefully_when_optional_endpoint_is_jsonobject_without_crash() {
        // `endpoint` is optional on NoUserKey/InvalidUserKey, so a malformed non-primitive
        // shape safe-casts to null and the parser still returns the typed envelope (with a
        // null endpoint). The point of this test is no-crash, not null-return.
        assertEquals(
            UserKeyError.NoUserKey(null),
            parseUserKeyError("""{"type":"no_user_key","endpoint":{}}"""),
        )
        assertEquals(
            UserKeyError.InvalidUserKey(null),
            parseUserKeyError("""{"type":"invalid_user_key","endpoint":{"name":"openAI"}}"""),
        )
    }

    @Test
    fun parser_treats_malformed_endpoint_on_expired_user_key_as_null_without_crash() {
        // endpoint is optional on ExpiredUserKey (mirroring NoUserKey/InvalidUserKey); a
        // malformed non-primitive endpoint safe-casts to null and the parser still returns
        // the typed envelope. expiredAt remains required.
        assertEquals(
            UserKeyError.ExpiredUserKey(endpoint = null, expiredAt = "5/1/2026"),
            parseUserKeyError(
                """{"type":"expired_user_key","endpoint":{},"expiredAt":"5/1/2026"}""",
            ),
        )
    }

    @Test
    fun parser_returns_null_when_expiredAt_is_jsonarray_without_crash() {
        assertNull(parseUserKeyError("""{"type":"expired_user_key","expiredAt":[],"endpoint":"openAI"}"""))
    }

    @Test
    fun parser_returns_null_when_expiredAt_is_jsonobject_without_crash() {
        assertNull(
            parseUserKeyError("""{"type":"expired_user_key","expiredAt":{"date":"5/1"},"endpoint":"openAI"}"""),
        )
    }

    @Test
    fun parser_treats_jsonnull_type_as_missing() {
        assertNull(parseUserKeyError("""{"type":null,"endpoint":"openAI"}"""))
    }
}
