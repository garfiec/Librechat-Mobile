package com.garfiec.librechat.feature.conversations.search

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.search.RoleFilter
import com.garfiec.librechat.core.data.search.SearchSort
import com.garfiec.librechat.core.data.search.SearchSource
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.search_date_any
import com.garfiec.librechat.feature.conversations.resources.search_date_month
import com.garfiec.librechat.feature.conversations.resources.search_date_week
import com.garfiec.librechat.feature.conversations.resources.search_date_year
import com.garfiec.librechat.feature.conversations.resources.search_group_conversation
import com.garfiec.librechat.feature.conversations.resources.search_group_none
import com.garfiec.librechat.feature.conversations.resources.search_role_anyone
import com.garfiec.librechat.feature.conversations.resources.search_role_assistant
import com.garfiec.librechat.feature.conversations.resources.search_role_you
import com.garfiec.librechat.feature.conversations.resources.search_sort_most_hits
import com.garfiec.librechat.feature.conversations.resources.search_sort_newest
import com.garfiec.librechat.feature.conversations.resources.search_sort_oldest
import com.garfiec.librechat.feature.conversations.resources.search_source_all
import com.garfiec.librechat.feature.conversations.resources.search_source_cached
import com.garfiec.librechat.feature.conversations.resources.search_source_server
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun roleLabel(role: RoleFilter): String = stringResource(
    when (role) {
        RoleFilter.Any -> Res.string.search_role_anyone
        RoleFilter.User -> Res.string.search_role_you
        RoleFilter.Assistant -> Res.string.search_role_assistant
    },
)

@Composable
internal fun sourceLabel(source: SearchSource): String = stringResource(
    when (source) {
        SearchSource.All -> Res.string.search_source_all
        SearchSource.Cached -> Res.string.search_source_cached
        SearchSource.Server -> Res.string.search_source_server
    },
)

@Composable
internal fun groupingLabel(grouping: SearchGrouping): String = stringResource(
    when (grouping) {
        SearchGrouping.ByConversation -> Res.string.search_group_conversation
        SearchGrouping.None -> Res.string.search_group_none
    },
)

@Composable
internal fun sortLabel(sort: SearchSort): String = stringResource(
    when (sort) {
        SearchSort.Newest -> Res.string.search_sort_newest
        SearchSort.Oldest -> Res.string.search_sort_oldest
        SearchSort.MostHits -> Res.string.search_sort_most_hits
    },
)

@Composable
internal fun dateLabel(range: DateRange): String = stringResource(
    when (range) {
        DateRange.AnyTime -> Res.string.search_date_any
        DateRange.PastWeek -> Res.string.search_date_week
        DateRange.PastMonth -> Res.string.search_date_month
        DateRange.PastYear -> Res.string.search_date_year
    },
)

/** A model id as shown: an agent's name when it is one and the name is known, else the id. */
internal fun String?.named(agentNames: Map<String, String>): String? = this?.let { agentNames[it] ?: it }
