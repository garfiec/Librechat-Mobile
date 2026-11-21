package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.datetime.DateGroup

/** A drawer section: "Running", "Finished", or a date bucket. All are localized at render. */
sealed interface DrawerGroupKey {
    /** Stable, Bundle-safe string for `LazyColumn` item keys. */
    val key: String

    /** Status groups head the list and carry a count in their heading. */
    val isStatus: Boolean get() = false

    data object Running : DrawerGroupKey {
        override val key = "running"
        override val isStatus: Boolean get() = true
    }

    /** Replies that finished while the user was elsewhere (v0.8.9-rc1). */
    data object Finished : DrawerGroupKey {
        override val key = "finished"
        override val isStatus: Boolean get() = true
    }

    data class Date(val group: DateGroup) : DrawerGroupKey {
        override val key: String get() = group.key
    }
}
