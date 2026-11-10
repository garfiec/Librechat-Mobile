package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.model.config.theme.ThemeRole
import com.garfiec.librechat.core.model.ui.UiStyle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * An absent `ui_style` key is how "follow the platform" is stored, so every path here has to keep
 * absent and a written value distinguishable. Real IO dispatchers and `runBlocking` rather than
 * `runTest`: DataStore reads on its own IO scope, which a virtual clock does not wait for.
 */
class ThemeDataStoreUiStyleTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newStore(file: File = File(tmpFolder.root, "theme.preferences_pb")): Pair<DataStore<Preferences>, ThemeDataStore> {
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { file }
        return prefs to ThemeDataStore(prefs, scope, Dispatchers.IO)
    }

    @Test
    fun absentKeyReadsAsNull() = runBlocking {
        val (_, store) = newStore()
        assertThat(store.uiStyle.first()).isNull()
    }

    @Test
    fun setThenClearRoundTrips() = runBlocking {
        val (_, store) = newStore()
        store.setUiStyle(UiStyle.LIQUID_GLASS)
        assertThat(store.uiStyle.first()).isEqualTo(UiStyle.LIQUID_GLASS)
        store.setUiStyle(UiStyle.MATERIAL)
        assertThat(store.uiStyle.first()).isEqualTo(UiStyle.MATERIAL)
        store.setUiStyle(null)
        assertThat(store.uiStyle.first()).isNull()
    }

    @Test
    fun unknownStoredValueDegradesToPlatformDefault() = runBlocking {
        val (prefs, store) = newStore()
        prefs.edit { it[stringPreferencesKey("ui_style")] = "some_future_style" }
        assertThat(store.uiStyle.first()).isNull()
    }

    @Test
    fun warmUpSeedsTheFirstFrameValue() = runBlocking {
        val file = File(tmpFolder.root, "warm.preferences_pb")
        val (_, writer) = newStore(file)
        writer.setUiStyle(UiStyle.LIQUID_GLASS)
        writer.setServerTheme("srv1", acme)

        // A second store over the same file stands in for the next cold start. DataStore allows one
        // active instance per file per scope, so it gets a scope of its own.
        val coldScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // Joined, not just cancelled: the file is only released once the first store's scope ends.
            scope.coroutineContext[Job]!!.cancelAndJoin()
            val cold = ThemeDataStore(
                PreferenceDataStoreFactory.create(scope = coldScope) { file },
                coldScope,
                Dispatchers.IO,
            )
            cold.isReady.first { it }
            assertThat(cold.initialUiStyle).isEqualTo(UiStyle.LIQUID_GLASS)
            assertThat(cold.initialServerTheme?.definition?.name).isEqualTo("Acme")
        } finally {
            coldScope.cancel()
        }
    }

    @Test
    fun serverThemeRoundTripsAndIsOnUntilSwitchboardIsPickedForItsServer() = runBlocking {
        val (_, store) = newStore()
        assertThat(store.serverTheme.first()).isNull()

        store.setServerTheme("srv1", acme)
        assertThat(store.serverTheme.first()?.painted?.name).isEqualTo("Acme")
        assertThat(store.serverTheme.first()?.painted?.light).containsEntry(ThemeRole.ACCENT_PRIMARY, 0x10A37F)

        store.setUseServerTheme("srv1", false)
        assertThat(store.serverTheme.first()?.painted).isNull()
        assertThat(store.serverTheme.first()?.enabled).isFalse()

        // Another server's theme is on by default: the choice is per server.
        store.setServerTheme("srv2", JsonPrimitive("clickhouse"))
        assertThat(store.serverTheme.first()?.painted?.name).isEqualTo("ClickHouse")

        // A withdrawn theme clears it.
        store.setServerTheme("srv2", null)
        assertThat(store.serverTheme.first()).isNull()
    }

    @Test
    fun turningTheServerAccentOffBeforeThemesKeepsSwitchboardsColours() = runBlocking {
        val (prefs, store) = newStore()
        prefs.edit { it[stringPreferencesKey("srv:srv1:use_server_accent")] = "false" }
        store.setServerTheme("srv1", acme)
        assertThat(store.serverTheme.first()?.enabled).isFalse()
    }

    @Test
    fun aCachedValueThatNoLongerReadsAsAThemeIsNone() = runBlocking {
        val (prefs, store) = newStore()
        prefs.edit {
            it[stringPreferencesKey("server_theme_server_id")] = "srv1"
            it[stringPreferencesKey("server_theme")] = "{not json"
        }
        assertThat(store.serverTheme.first()).isNull()
    }

    private val acme = Json.parseToJsonElement(
        """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"16 163 127"}}}}""",
    )
}
