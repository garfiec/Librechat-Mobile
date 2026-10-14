package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.common.DistributionChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceMigrationRunnerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val file by lazy { File(tmpFolder.root, "prefs.preferences_pb") }
    private val play = ChannelPolicy(DistributionChannel.PLAY_STORE)
    private val unrelated = stringPreferencesKey("unrelated")
    private val ran = mutableListOf<String>()

    private fun migration(id: String, playOnly: Boolean = false, body: (MutablePreferences) -> Unit = {}) =
        PreferenceMigration(
            id = id,
            appliesTo = if (playOnly) { policy -> policy.pick(false, true, false) } else { _ -> true },
        ) { prefs ->
            ran += id
            body(prefs)
        }

    /** Opens the store, hands it to [block], then closes it so the file can be opened again. */
    private suspend fun <T> withStore(
        migrations: List<PreferenceMigration>?,
        policy: ChannelPolicy = play,
        block: suspend (DataStore<Preferences>) -> T,
    ): T {
        val scope = CoroutineScope(testDispatcher + Job())
        val store = PreferenceDataStoreFactory.create(
            migrations = migrations?.let { listOf(PreferenceMigrationRunner(policy, it)) }.orEmpty(),
            scope = scope,
            produceFile = { file },
        )
        return try {
            block(store)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun seedExistingUser() = withStore(migrations = null) { store ->
        store.edit { it[unrelated] = "kept" }
    }

    @Test
    fun freshInstallWaivesThisChannelsMigrationsWithoutRunningThem() = runTest(testDispatcher) {
        val migrations = listOf(migration("20270101-a"), migration("20270102-play", playOnly = true))

        val ledger = withStore(migrations, policy = ChannelPolicy(DistributionChannel.OPEN_SOURCE)) {
            it.data.first()[PreferenceMigrationRunner.LEDGER]
        }

        assertThat(ran).isEmpty()
        // The Play-only one is not this channel's: it stays unrecorded, so a Play build would run it.
        assertThat(ledger).containsExactly("20270101-a")
    }

    @Test
    fun existingUserRunsPendingMigrationsInOrderAndRecordsThem() = runTest(testDispatcher) {
        seedExistingUser()
        val migrations = listOf(
            migration("20270101-reset") { it.force(AppPreferences.SiteIcons, false) },
            migration("20270102-reset-again") { it.reset(AppPreferences.SiteIcons) },
        )

        val prefs = withStore(migrations) { it.data.first() }

        assertThat(ran).containsExactly("20270101-reset", "20270102-reset-again").inOrder()
        assertThat(prefs[AppPreferences.SiteIcons.key]).isNull()
        assertThat(prefs[unrelated]).isEqualTo("kept")
        assertThat(prefs[PreferenceMigrationRunner.LEDGER]).containsExactly("20270101-reset", "20270102-reset-again")
    }

    @Test
    fun aRecordedMigrationNeverRunsAgain() = runTest(testDispatcher) {
        seedExistingUser()
        val migrations = listOf(migration("20270101-a"))
        withStore(migrations) { it.data.first() }
        ran.clear()

        withStore(migrations) { it.data.first() }

        assertThat(ran).isEmpty()
    }

    @Test
    fun otherChannelsMigrationsDoNotRun() = runTest(testDispatcher) {
        seedExistingUser()

        val migrations = listOf(migration("20270101-play", playOnly = true))
        val ledger = withStore(migrations, ChannelPolicy(DistributionChannel.APP_STORE)) {
            it.data.first()[PreferenceMigrationRunner.LEDGER]
        }

        assertThat(ran).isEmpty()
        assertThat(ledger).isNull()
    }

    @Test
    fun idsFromANewerBuildSurviveADowngrade() = runTest(testDispatcher) {
        seedExistingUser()
        withStore(listOf(migration("20270101-a"), migration("20270301-newer"))) { it.data.first() }

        val ledger = withStore(listOf(migration("20270101-a"), migration("20270201-b"))) {
            it.data.first()[PreferenceMigrationRunner.LEDGER]
        }

        assertThat(ledger).containsExactly("20270101-a", "20270301-newer", "20270201-b")
    }

    @Test
    fun aFailingMigrationLeavesTheStoreUntouchedAndRetriesNextOpen() = runTest(testDispatcher) {
        seedExistingUser()

        val failure = withStore(listOf(migration("20270101-a") { error("boom") })) {
            runCatching { it.data.first() }.exceptionOrNull()
        }
        assertThat(failure).hasMessageThat().isEqualTo("boom")

        val prefs = withStore(listOf(migration("20270101-a"))) { it.data.first() }
        assertThat(prefs[PreferenceMigrationRunner.LEDGER]).containsExactly("20270101-a")
        assertThat(prefs[unrelated]).isEqualTo("kept")
    }
}
