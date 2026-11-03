package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `interface.termsOfService.modalContent` is a string or an array of lines; web joins the lines with `\n`. */
class TermsModalContentTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun terms(body: String) = json.decodeFromString(TermsOfServiceConfig.serializer(), body)

    @Test
    fun aStringIsTakenAsIs() {
        val config = terms("""{ "modalTitle": "Our Terms", "modalContent": "You agree to **this**." }""")
        assertEquals("Our Terms", config.modalTitle)
        assertEquals("You agree to **this**.", config.modalContent)
    }

    @Test
    fun linesAreJoinedWithNewlines() {
        assertEquals("# Terms\n\nBe nice.", terms("""{ "modalContent": ["# Terms", "", "Be nice."] }""").modalContent)
    }

    @Test
    fun anAbsentNullOrMalformedValueIsNull() {
        assertNull(terms("{}").modalContent)
        assertNull(terms("""{ "modalContent": null }""").modalContent)
        assertNull(terms("""{ "modalContent": 42 }""").modalContent)
        assertNull(terms("""{ "modalContent": { "a": 1 } }""").modalContent)
    }
}
