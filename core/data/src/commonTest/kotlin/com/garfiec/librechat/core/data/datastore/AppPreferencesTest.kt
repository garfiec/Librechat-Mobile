package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.common.DistributionChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val MIGRATION_ID = Regex("""\d{8}-[a-z0-9]+(-[a-z0-9]+)*""")

class AppPreferencesTest {

    @Test
    fun everyKeyIsUnique() {
        val names = AppPreferences.all.map { it.key.name }
        assertEquals(names, names.distinct(), "two preferences share a key and would overwrite each other")
    }

    @Test
    fun theMigrationLedgerIsNotAPreference() {
        assertFalse(AppPreferences.all.any { it.key.name == PreferenceMigrationRunner.LEDGER.name })
    }

    @Test
    fun migrationIdsAreUniqueDatedAndInOrder() {
        val ids = PreferenceMigrations.all.map { it.id }
        assertEquals(ids, ids.distinct(), "a reused id would be skipped as already applied")
        ids.forEach { assertTrue(MIGRATION_ID.matches(it), "$it is not YYYYMMDD-slug") }
        // By date only: two migrations from one day run in the order they are declared.
        assertEquals(ids.sortedBy { it.take(8) }, ids, "migrations run in declaration order, which must be date order")
    }

    /**
     * Every preference's default on every channel. A changed default fails here, so it is a
     * deliberate edit to this table rather than a side effect nobody reviewed.
     */
    @Test
    fun defaultsPerChannel() {
        val actual = AppPreferences.all.associate { spec ->
            spec.key.name to DistributionChannel.entries.associateWith { spec.default(ChannelPolicy(it)) }
        }
        val expected = mapOf(
            "show_site_icons" to mapOf(
                DistributionChannel.OPEN_SOURCE to null,
                DistributionChannel.PLAY_STORE to true,
                DistributionChannel.APP_STORE to true,
            ),
            "prefetch_enabled" to everyChannel(true),
            "prefetch_attachments" to everyChannel(false),
            "prefetch_on_metered" to everyChannel(true),
            "prefetch_depth" to everyChannel(50),
            "advanced_search" to everyChannel(false),
            "compare_sync_scroll" to everyChannel(true),
            "prefer_last_used_model" to everyChannel(false),
            "web_search_first" to everyChannel(WebSearchPreference.LIBRECHAT_FIRST),
        )
        assertEquals(expected, actual)
    }

    private fun everyChannel(value: Any?) = DistributionChannel.entries.associateWith { value }
}
