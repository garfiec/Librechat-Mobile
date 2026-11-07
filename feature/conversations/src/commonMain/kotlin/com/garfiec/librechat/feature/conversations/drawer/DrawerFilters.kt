package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationListFilters
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.iconProvider
import com.garfiec.librechat.core.model.resolveEndpointIconUrl
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
    /** Only chats carrying any bookmark. Off whenever [tags] names some. */
    val bookmarked: Boolean = false,
    /** Only chats carrying one of these bookmarks (OR-matched by the server, web's `chatFilterTagsAtom`). */
    val tags: List<String> = emptyList(),
) {
    val isActive: Boolean
        get() = dateRange != FilterDateRange.ANY || endpoints.isNotEmpty() || hasFiles || sharedOnly || bookmarked ||
            tags.isNotEmpty()

    /** [tag] picked or dropped. Picking one turns "any bookmark" off: the two never narrow at once. */
    fun withTagToggled(tag: String): DrawerFilters =
        if (tag in tags) copy(tags = tags - tag) else copy(tags = tags + tag, bookmarked = false)

    /** "Any bookmark" switched; on, it replaces the picked bookmarks. */
    fun withAnyBookmark(on: Boolean): DrawerFilters = copy(bookmarked = on, tags = if (on) emptyList() else tags)

    /**
     * The request for these choices. The date cutoff is anchored to local midnight, as upstream
     * does, so it is stable through the day. Picked [tags] are sent as they are. Otherwise, with
     * [bookmarked] on, [bookmarkTags] (the user's bookmarks) are; with none known, the request names
     * the reserved `Saved` tag so it still narrows.
     */
    fun toListFilters(now: Instant, zone: TimeZone, bookmarkTags: List<String>): ConversationListFilters {
        val cutoff = rangeCutoff(dateRange, now, zone)
        return ConversationListFilters(
            updatedAfter = cutoff?.takeIf { dateField == FilterDateField.UPDATED },
            createdAfter = cutoff?.takeIf { dateField == FilterDateField.CREATED },
            endpoints = endpoints,
            hasFiles = hasFiles,
            sharedOnly = sharedOnly,
            tags = when {
                tags.isNotEmpty() -> tags
                bookmarked -> bookmarkTags.ifEmpty { listOf(com.garfiec.librechat.core.model.SAVED_TAG) }
                else -> emptyList()
            },
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

/** A bookmark the sheet offers as a filter, with how many chats carry it. */
data class FilterBookmarkOption(val tag: String, val count: Int)

/**
 * Upstream `selectableBookmarks` (`client/src/components/Conversations/chatFilters.ts`): bookmarks
 * some chat carries (one no chat carries filters the list to nothing), plus every selected one, even
 * one no chat carries any more or that was deleted, so a chosen bookmark can always be turned off.
 */
fun selectableBookmarks(bookmarks: List<FilterBookmarkOption>, selected: List<String>): List<FilterBookmarkOption> {
    val known = bookmarks.mapTo(HashSet()) { it.tag }
    val listed = bookmarks.filter { it.count > 0 || it.tag in selected }
    return listed + selected.filterNot { it in known }.map { FilterBookmarkOption(it, 0) }
}

/** A filter chip for [name]: its label, and the icon its conversations' rows draw. */
internal fun filterEndpointOption(name: String, endpoints: Map<String, EndpointConfig>, serverUrl: String): FilterEndpointOption {
    val row = Conversation(endpoint = name)
    return FilterEndpointOption(
        name = name,
        label = endpoints[name]?.modelDisplayLabel ?: name,
        iconUrl = row.resolveEndpointIconUrl(endpoints, serverUrl),
        provider = row.iconProvider(endpoints),
    )
}
