package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.ChatProject

/**
 * **Switchboard-only.** Applies the user's saved drawer order (`project_order`) to the server's list.
 *
 * - Nothing saved yet: the server's order is kept as is, so the first load freezes whatever the
 *   user was already looking at (recent activity).
 * - Otherwise saved projects come first, by their saved slot, and projects the device has never
 *   placed (made on the web or another device since) follow, oldest first, so the newest is last.
 *
 * A saved id the server no longer lists simply has nothing to place, which is how a project deleted
 * elsewhere drops out of the order once the result is saved back.
 */
internal object ProjectOrdering {

    fun arrange(server: List<ChatProject>, saved: Map<String, Long>): List<ChatProject> {
        if (saved.isEmpty()) return server
        val (placed, unplaced) = server.partition { it.id in saved }
        // ISO-8601 UTC strings sort chronologically; one without a date counts as newest.
        return placed.sortedBy { saved.getValue(it.id) } +
            unplaced.sortedWith(compareBy(nullsLast()) { it.createdAt })
    }
}
