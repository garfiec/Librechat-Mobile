package com.garfiec.librechat.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectOrderMigrationTest {
    @Test
    fun migrationCreatesAnEmptyAccountKeyedOrderTable() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        try {
            LibreChatDatabase_AutoMigration_12_13_Impl().migrate(connection)
            connection.prepare("SELECT COUNT(*) FROM project_order").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
            connection.execSQL("INSERT INTO project_order VALUES ('a', 'p', 0)")
            connection.execSQL("INSERT INTO project_order VALUES ('b', 'p', 0)")
            connection.prepare("SELECT COUNT(*) FROM project_order WHERE projectId = 'p'").use {
                assertTrue(it.step())
                assertEquals(2L, it.getLong(0))
            }
        } finally {
            connection.close()
        }
    }
}
