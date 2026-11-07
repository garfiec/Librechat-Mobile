package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.media.ProviderId
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DrawerFiltersTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    // 2026-11-02 15:30 in Berlin (UTC+1 in November).
    private val now = Instant.parse("2026-11-02T14:30:00Z")

    @Test
    fun cutoffsCountWholeDaysBackFromLocalMidnight() {
        assertNull(rangeCutoff(FilterDateRange.ANY, now, berlin))
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), rangeCutoff(FilterDateRange.TODAY, now, berlin))
        assertEquals(Instant.parse("2026-10-26T23:00:00Z"), rangeCutoff(FilterDateRange.WEEK, now, berlin))
        // Thirty days back crosses the end of summer time: midnight was UTC+2 then.
        assertEquals(Instant.parse("2026-10-03T22:00:00Z"), rangeCutoff(FilterDateRange.MONTH, now, berlin))
        assertEquals(Instant.parse("2025-11-02T23:00:00Z"), rangeCutoff(FilterDateRange.YEAR, now, berlin))
    }

    @Test
    fun theDateFieldPicksUpdatedOrCreated() {
        val updated = DrawerFilters(dateRange = FilterDateRange.TODAY).toListFilters(now, berlin, emptyList())
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), updated.updatedAfter)
        assertNull(updated.createdAfter)
        val created = DrawerFilters(dateRange = FilterDateRange.TODAY, dateField = FilterDateField.CREATED)
            .toListFilters(now, berlin, emptyList())
        assertNull(created.updatedAfter)
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), created.createdAfter)
    }

    @Test
    fun bookmarksNameTheUsersTagsOrFallBackToSaved() {
        val filters = DrawerFilters(bookmarked = true)
        assertEquals(listOf("Work", SAVED_TAG), filters.toListFilters(now, berlin, listOf("Work", SAVED_TAG)).tags)
        assertEquals(listOf(SAVED_TAG), filters.toListFilters(now, berlin, emptyList()).tags)
        assertTrue(DrawerFilters().toListFilters(now, berlin, listOf("Work")).tags.isEmpty())
    }

    @Test
    fun flagsAndEndpointsPassThrough() {
        val list = DrawerFilters(endpoints = listOf("openAI"), hasFiles = true, sharedOnly = true)
            .toListFilters(now, berlin, emptyList())
        assertEquals(listOf("openAI"), list.endpoints)
        assertTrue(list.hasFiles)
        assertTrue(list.sharedOnly)
        assertTrue(list.isActive)
    }

    @Test
    fun aDateFieldAloneIsNotAFilter() {
        assertFalse(DrawerFilters(dateField = FilterDateField.CREATED).isActive)
        assertTrue(DrawerFilters(dateRange = FilterDateRange.WEEK).isActive)
    }

    @Test
    fun selectableEndpointsKeepServedNamesWithinTheLimitAndEverySelectedOne() {
        val served = listOf("openAI", "anthropic", "x".repeat(10))
        assertEquals(listOf("openAI", "anthropic", "gone"), selectableEndpoints(served, listOf("anthropic", "gone"), maxNameLength = 9))
    }

    @Test
    fun anEndpointChipDrawsTheServedProvider() {
        val configs = mapOf("my-llm" to EndpointConfig(providerId = "groq", modelDisplayLabel = "My LLM"))
        val option = filterEndpointOption("my-llm", configs, "https://chat.example")
        assertEquals(FilterEndpointOption("my-llm", "My LLM", iconUrl = null, provider = ProviderId.GROQ), option)
    }

    @Test
    fun filtersSurviveTheSavedStateRoundTrip() {
        val filters = DrawerFilters(
            dateRange = FilterDateRange.MONTH,
            dateField = FilterDateField.CREATED,
            endpoints = listOf("google"),
            hasFiles = true,
            bookmarked = true,
        )
        val json = Json.encodeToString(DrawerFilters.serializer(), filters)
        assertEquals(filters, Json.decodeFromString(DrawerFilters.serializer(), json))
    }

    @Test
    fun pickedBookmarksAreSentAsTheyAreAndWinOverAnyBookmark() {
        val picked = DrawerFilters(bookmarked = true, tags = listOf("work")).toListFilters(now, berlin, listOf("work", "home"))
        assertEquals(listOf("work"), picked.tags)
        assertTrue(DrawerFilters(tags = listOf("work")).isActive)
    }

    @Test
    fun pickingABookmarkTurnsAnyBookmarkOffAndAnyBookmarkClearsThePicks() {
        val picked = DrawerFilters(bookmarked = true).withTagToggled("work")
        assertEquals(listOf("work"), picked.tags)
        assertFalse(picked.bookmarked)
        assertEquals(emptyList(), picked.withTagToggled("work").tags)
        val any = picked.withAnyBookmark(true)
        assertTrue(any.bookmarked)
        assertEquals(emptyList(), any.tags)
    }

    @Test
    fun selectableBookmarksSkipEmptyOnesButKeepEverySelectedOne() {
        val bookmarks =
            listOf(FilterBookmarkOption("work", 3), FilterBookmarkOption("empty", 0), FilterBookmarkOption("old", 0))
        assertEquals(
            listOf(FilterBookmarkOption("work", 3), FilterBookmarkOption("old", 0), FilterBookmarkOption("deleted", 0)),
            selectableBookmarks(bookmarks, selected = listOf("old", "deleted")),
        )
    }

    @Test
    fun pickedBookmarksSurviveTheSavedStateRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val filters = DrawerFilters(tags = listOf("work", "home"))
        assertEquals(filters, json.decodeFromString(DrawerFilters.serializer(), json.encodeToString(DrawerFilters.serializer(), filters)))
    }
}
