package com.garfiec.librechat.feature.settings.viewmodel.delegate

import androidx.compose.runtime.Immutable

/** What the Settings row for Provider Keys shows. */
@Immutable
sealed interface ProviderKeysSummary {
    /** No reachable endpoint takes a user-provided value: the page would be empty. */
    data object Hidden : ProviderKeysSummary

    /**
     * The general subtitle: not every listed endpoint has answered yet, or the endpoint configs
     * haven't loaded (offline at launch), when the row still shows so the page's Retry is reachable.
     */
    data object General : ProviderKeysSummary

    /** [setCount] of the [totalCount] listed endpoints hold a live key. */
    data class Count(val setCount: Int, val totalCount: Int) : ProviderKeysSummary
}
