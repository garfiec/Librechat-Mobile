package com.garfiec.librechat.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.garfiec.librechat.core.data.db.entity.ProjectOrderEntity

/**
 * **Switchboard-only** — see [ProjectOrderEntity].
 *
 * Every query is account-scoped. `project_order` is not one of the tables the tenancy detekt rule
 * infers, so nothing would fail the build if it weren't — which is why it is written that way by hand.
 */
@Dao
interface ProjectOrderDao {

    @Query("SELECT * FROM project_order WHERE accountId = :accountId ORDER BY position")
    suspend fun positionsForAccount(accountId: String): List<ProjectOrderEntity>

    /** Replaces the account's whole order with [projectIds], in that order. */
    @Transaction
    suspend fun replaceForAccount(accountId: String, projectIds: List<String>) {
        deleteAllForAccount(accountId)
        insertAll(projectIds.mapIndexed { index, id -> ProjectOrderEntity(accountId, id, index.toLong()) })
    }

    @Insert
    suspend fun insertAll(rows: List<ProjectOrderEntity>)

    @Query("DELETE FROM project_order WHERE accountId = :accountId AND projectId = :projectId")
    suspend fun delete(accountId: String, projectId: String)

    @Query("DELETE FROM project_order WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}
