package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity

/**
 * **Switchboard-only — LibreChat has no counterpart.** Where the user dragged a project folder to in
 * the drawer. The server stores no order (a project has no position field, and `GET /api/projects`
 * sorts only by name, creation or last activity), so the order lives on the device, per account, and
 * does not follow the user to the web or another device.
 *
 * If upstream ever ships project ordering, drop this table with an `@DeleteTable` AutoMigrationSpec
 * and read the server's order instead: nothing else stores a reference to it.
 *
 * Must be deleted with the account by `AccountDataPurger`.
 */
@Entity(
    tableName = "project_order",
    primaryKeys = ["accountId", "projectId"],
)
data class ProjectOrderEntity(
    val accountId: String,
    val projectId: String,
    /** 0-based slot in the account's order. Rewritten for the whole account on every save. */
    val position: Long,
)
