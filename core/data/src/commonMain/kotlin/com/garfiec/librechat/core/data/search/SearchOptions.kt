package com.garfiec.librechat.core.data.search

enum class RoleFilter { Any, User, Assistant }

enum class SearchSource { All, Cached, Server }

/** Result order. [MostHits] ranks conversations by how many of their messages matched. */
enum class SearchSort { Newest, Oldest, MostHits }

/**
 * Advanced search options (Settings → General → Advanced search). The default value is the plain
 * search, so callers that never show the options pass nothing.
 *
 * - [role], [model], [endpoint], [since] and [includeArchived] narrow the local query in SQL.
 * - [matchCase] and [wholeWord] re-check each candidate the index found (it matches
 *   case-insensitively and by word prefix).
 * - [regex] treats the query as a pattern and runs it on the bounded scan engine; the server is
 *   not asked, since it can't run one.
 */
data class SearchOptions(
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false,
    val role: RoleFilter = RoleFilter.Any,
    val model: String? = null,
    val endpoint: String? = null,
    /** Only messages created at or after this epoch-millis instant. */
    val since: Long? = null,
    /** Archived chats are left out unless asked for, as the drawer leaves them out. */
    val includeArchived: Boolean = false,
    /** Match only what the reply shows, skipping its reasoning. */
    val visibleTextOnly: Boolean = false,
    val sort: SearchSort = SearchSort.Newest,
    val source: SearchSource = SearchSource.All,
) {
    /** Any option that narrows rows in SQL rather than re-checking text. */
    val filtersRows: Boolean
        get() = role != RoleFilter.Any || model != null || endpoint != null || since != null || !includeArchived

    /** Whether a message with these attributes passes the row filters (for scan and server hits). */
    fun admits(isCreatedByUser: Boolean, model: String?, endpoint: String?, createdAt: Long?): Boolean {
        val roleOk = when (role) {
            RoleFilter.Any -> true
            RoleFilter.User -> isCreatedByUser
            RoleFilter.Assistant -> !isCreatedByUser
        }
        return roleOk &&
            (this.model == null || this.model == model) &&
            (this.endpoint == null || this.endpoint == endpoint) &&
            (since == null || (createdAt != null && createdAt >= since))
    }

    companion object {
        val Default = SearchOptions()
    }
}
