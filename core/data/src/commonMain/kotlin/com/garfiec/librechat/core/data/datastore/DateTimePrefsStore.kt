package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.datetime.ClockFormat
import com.garfiec.librechat.core.common.datetime.DateFormatStyle
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Timestamp style, clock and date format. Global, not per-account; shares the app's preferences
 * [DataStore] with [SettingsDataStore].
 */
class DateTimePrefsStore(
    private val dataStore: DataStore<Preferences>,
    appScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) {

    /**
     * The choices seeded for the first compose frame, warmed up off the Main thread. The app root
     * gates on [isReady], so a user with an override never sees one frame in the default format.
     */
    @Volatile
    var initial: DateTimeFormatPrefs = DateTimeFormatPrefs()
        private set

    private val _isReady = MutableStateFlow(false)

    /** Flips true once the warm-up has resolved [initial] (success, failure, or cancellation). */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        appScope.launch(ioDispatcher) {
            try {
                initial = dataStore.data.first().toDateTimePrefs()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the default; the live flow still drives the UI value.
            } finally {
                _isReady.value = true
            }
        }
    }

    val prefs: Flow<DateTimeFormatPrefs> = dataStore.data
        .map { it.toDateTimePrefs() }
        .distinctUntilChanged()

    /** Writes all three choices in one edit, so collectors never see a half-applied save. */
    suspend fun set(value: DateTimeFormatPrefs) {
        dataStore.edit { prefs ->
            prefs[KEY_TIMESTAMP_STYLE] = value.style.toStorageString()
            prefs[KEY_CLOCK_FORMAT] = value.clock.toStorageString()
            prefs[KEY_DATE_FORMAT] = value.date.toStorageString()
        }
    }

    private fun Preferences.toDateTimePrefs() = DateTimeFormatPrefs(
        style = TimestampStyle.fromString(this[KEY_TIMESTAMP_STYLE]),
        clock = ClockFormat.fromString(this[KEY_CLOCK_FORMAT]),
        date = DateFormatStyle.fromString(this[KEY_DATE_FORMAT]),
    )

    private companion object {
        val KEY_TIMESTAMP_STYLE = stringPreferencesKey("timestamp_style")
        val KEY_CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val KEY_DATE_FORMAT = stringPreferencesKey("date_format")
    }
}
