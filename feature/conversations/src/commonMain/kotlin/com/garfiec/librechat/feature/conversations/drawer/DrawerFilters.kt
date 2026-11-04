package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ConversationListFilters
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * MIRRORED from upstream `DATE_RANGE_OPTIONS` / `RANGE_DAYS`
 * (`client/src/components/Conversations/facets.ts`): coarse windows, each counted back in whole
 * days from local midnight, today being the first.
 */
@Serializable
enum class FilterDateRange(val days: Int) {
    ANY(0),
    TODAY(1),
    WEEK(7),
    MONTH(30),
    YEAR(365),
}

/** Which timestamp the date window applies to. One at a time: the sheet's pill picks it. */
@Serializable
enum class FilterDateField { UPDATED, CREATED }

/**
 * The drawer's list filters as the user chose them (v0.8.9-rc1 list facets). Saved through process
 * death, so it holds only choices; the request is built from it at load time ([toListFilters]).
 */
@Immutable
@Serializable
data class DrawerFilters(
    val dateRange: FilterDateRange = FilterDateRange.ANY,
    val dateField: FilterDateField = FilterDateField.UPDATED,
    val endpoints: List<String> = emptyList(),
    val hasFiles: Boolean = false,
    val sharedOnly: Boolean = false,
    /** Only chats carrying a bookmark. */
    val bookmarked: Boolean = false,
) {
    val isActive: Boolean
        get() = dateRange != FilterDateRange.ANY || endpoints.isNotEmpty() || hasFiles || sharedOnly || bookmarked

    /**
     * The request for these choices. The date cutoff is anchored to local midnight, as upstream
     * does, so it is stable through the day. [bookmarkTags] are the user's bookmarks; with
     * [bookmarked] on and none known, the request names the reserved `Saved` tag so it still narrows.
     */
    fun toListFilters(now: Instant, zone: TimeZone, bookmarkTags: List<String>): ConversationListFilters {
        val cutoff = rangeCutoff(dateRange, now, zone)
        return ConversationListFilters(
            updatedAfter = cutoff?.takeIf { dateField == FilterDateField.UPDATED },
            createdAfter = cutoff?.takeIf { dateField == FilterDateField.CREATED },
            endpoints = endpoints,
            hasFiles = hasFiles,
            sharedOnly = sharedOnly,
            tags = if (bookmarked) bookmarkTags.ifEmpty { listOf(com.garfiec.librechat.core.model.SAVED_TAG) } else emptyList(),
        )
    }

    companion object {
        val None = DrawerFilters()
    }
}

/** Upstream `rangeCutoff`: the local midnight [range] starts at, or null for any time. */
fun rangeCutoff(range: FilterDateRange, now: Instant, zone: TimeZone): Instant? {
    if (range == FilterDateRange.ANY) return null
    val today = now.toLocalDateTime(zone).date
    return today.minus(DatePeriod(days = range.days - 1)).atStartOfDayIn(zone)
}

/**
 * Upstream `selectableEndpoints`: the endpoints this deployment serves whose names the list route
 * accepts ([maxNameLength]), plus any still selected that are not among them, so a chosen endpoint
 * can always be turned off.
 */
fun selectableEndpoints(served: List<String>, selected: List<String>, maxNameLength: Int): List<String> {
    val listed = served.filter { it.length <= maxNameLength }
    val known = listed.toSet()
    return listed + selected.filterNot { it in known }
}
