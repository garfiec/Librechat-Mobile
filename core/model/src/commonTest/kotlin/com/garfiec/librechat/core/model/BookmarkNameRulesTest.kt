package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.request.TagCreateRequest
import com.garfiec.librechat.core.model.request.TagEditRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkNameRulesTest {

    private val existing = listOf("Work", "Reading list", SAVED_TAG)

    private fun check(name: String, editing: String? = null) =
        BookmarkNameRules.validateName(name, existing, editing)

    @Test
    fun `limits match upstream BookmarkForm`() {
        assertEquals(128, BookmarkNameRules.MAX_NAME)
        assertEquals(1048, BookmarkNameRules.MAX_DESCRIPTION)
    }

    @Test
    fun `a new name passes`() {
        assertEquals(BookmarkNameCheck.Ok, check("Recipes"))
    }

    @Test
    fun `blank and whitespace-only names are blank`() {
        assertEquals(BookmarkNameCheck.Blank, check(""))
        assertEquals(BookmarkNameCheck.Blank, check("   "))
    }

    @Test
    fun `length is measured after trimming`() {
        val max = "a".repeat(BookmarkNameRules.MAX_NAME)
        assertEquals(BookmarkNameCheck.Ok, check(max))
        assertEquals(BookmarkNameCheck.Ok, check("  $max  "))
        assertEquals(BookmarkNameCheck.TooLong, check(max + "a"))
    }

    @Test
    fun `duplicates are case-insensitive and trimmed`() {
        assertEquals(BookmarkNameCheck.Duplicate, check("work"))
        assertEquals(BookmarkNameCheck.Duplicate, check(" WORK "))
        assertEquals(BookmarkNameCheck.Duplicate, check("reading LIST"))
    }

    @Test
    fun `the name being edited is not a duplicate of itself`() {
        assertEquals(BookmarkNameCheck.Ok, check("Work", editing = "Work"))
        assertEquals(BookmarkNameCheck.Ok, check("work", editing = "Work"))
        assertEquals(BookmarkNameCheck.Duplicate, check("reading list", editing = "Work"))
    }

    @Test
    fun `Saved is reserved in any case even when it isn't an existing tag`() {
        assertEquals(BookmarkNameCheck.Reserved, check("Saved"))
        assertEquals(BookmarkNameCheck.Reserved, check(" saved "))
        assertEquals(BookmarkNameCheck.Reserved, BookmarkNameRules.validateName("SAVED", emptyList()))
        assertEquals(BookmarkNameCheck.Reserved, check("saved", editing = "Work"))
    }

    private val wireJson = Json { encodeDefaults = false }

    @Test
    fun `create body omits unset fields`() {
        assertEquals("""{"tag":"Work"}""", wireJson.encodeToString(TagCreateRequest(tag = "Work")))
        assertEquals(
            """{"tag":"Work","description":"d","addToConversation":true,"conversationId":"c1"}""",
            wireJson.encodeToString(
                TagCreateRequest(tag = "Work", description = "d", addToConversation = true, conversationId = "c1"),
            ),
        )
    }

    @Test
    fun `edit body sends an empty description and omits a null one`() {
        assertEquals("""{"description":""}""", wireJson.encodeToString(TagEditRequest(description = "")))
        assertEquals("""{"position":2}""", wireJson.encodeToString(TagEditRequest(position = 2)))
        assertEquals("{}", wireJson.encodeToString(TagEditRequest()))
    }
}
