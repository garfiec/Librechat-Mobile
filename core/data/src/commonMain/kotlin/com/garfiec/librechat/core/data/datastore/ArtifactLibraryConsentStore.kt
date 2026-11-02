package com.garfiec.librechat.core.data.datastore

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import okio.IOException

/**
 * The user's answers about flagged libraries in React artifacts: an npm package the app bundles
 * for artifacts that is unmaintained, deprecated or vulnerable, which an artifact may load only
 * after the user agrees.
 *
 * An answer is about a [Subject], a library under one advisory kind, so a library whose advisory
 * escalates (unmaintained to vulnerable) is asked about again rather than inheriting the old yes.
 *
 * Two lifetimes:
 * - **Remembered**: device-wide and persisted ([AppPreferences.ArtifactLibraryAnswers]), one answer
 *   per subject. All of them live under one key, so each change is a single write and no reader
 *   sees an in-between state.
 * - **Once**: held in memory for one artifact, keyed by an id the caller derives from it, until the
 *   process ends. So the inline preview and the fullscreen panel of the same artifact ask once
 *   between them, and a different artifact asks again.
 *
 * A remembered answer wins over a once answer. Two ways to change one: [reconsider] takes back
 * what applies in one artifact (the artifact's Change), and Settings sets the answer for every
 * artifact ([remember], or [forget] to ask again). [FlaggedArtifactLibraries] lists the subjects.
 */
class ArtifactLibraryConsentStore(
    private val preferences: PreferenceStore,
    appScope: CoroutineScope,
) {
    /** What applies to one subject in one artifact. */
    enum class Decision { ALLOW, DECLINE, ASK }

    /** One question: may [library], flagged as [advisory], load? Neither may contain [SEPARATOR] or a comma. */
    data class Subject(val library: String, val advisory: String)

    class Snapshot internal constructor(
        private val remembered: Map<Subject, Boolean>,
        private val once: Map<Pair<Subject, String>, Boolean>,
    ) {
        fun decisionFor(subject: Subject, artifactId: String): Decision =
            when (remembered[subject] ?: once[subject to artifactId]) {
                true -> Decision.ALLOW
                false -> Decision.DECLINE
                null -> Decision.ASK
            }

        /** The device-wide answer for [subject], or null when there is none and each artifact asks. */
        fun remembered(subject: Subject): Boolean? = remembered[subject]
    }

    private val once = MutableStateFlow<Map<Pair<Subject, String>, Boolean>>(emptyMap())

    /** Null until the stored answers are read, so a remembered answer never flashes a prompt. */
    val snapshot: StateFlow<Snapshot?> = combine(
        preferences.observe(AppPreferences.ArtifactLibraryAnswers),
        once,
    ) { stored, onceAnswers -> Snapshot(decode(stored), onceAnswers) }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    fun decideOnce(subject: Subject, artifactId: String, allow: Boolean) {
        once.update { it + ((subject to artifactId) to allow) }
    }

    suspend fun remember(subject: Subject, allow: Boolean) = write { entries ->
        entries.filterNot { decodeEntry(it)?.first == subject } + encode(subject, allow)
    }

    /**
     * Drops the remembered answer for [subject], so artifacts ask again. Once answers stand: they
     * aren't shown in Settings, so a Settings choice that silently undid one would surprise.
     */
    suspend fun forget(subject: Subject) {
        write { entries -> entries.filterNot { decodeEntry(it)?.first == subject } }
    }

    /**
     * Takes back the answer that applies to [subject] in [artifactId], so it asks again there: the
     * remembered answer (which applied everywhere) and this artifact's once answer. Other
     * artifacts' once answers stand.
     */
    suspend fun reconsider(subject: Subject, artifactId: String) {
        once.update { it - (subject to artifactId) }
        write { entries -> entries.filterNot { decodeEntry(it)?.first == subject } }
    }

    /**
     * A failed write leaves the previous answers in force, which the screen still shows. Logged
     * rather than thrown: these run from UI coroutines, where an IOException would crash the app.
     * Okio's IOException, as in [bestEffortCache]: on Native it is DataStore's own class.
     */
    private suspend fun write(transform: (List<String>) -> List<String>) {
        try {
            preferences.update(AppPreferences.ArtifactLibraryAnswers, transform)
        } catch (e: IOException) {
            Logger.w(e) { "Could not save an artifact library answer" }
        }
    }

    private companion object {
        const val SEPARATOR = '|'
        const val ALLOW = "allow"
        const val DECLINE = "decline"

        fun encode(subject: Subject, allow: Boolean): String =
            listOf(if (allow) ALLOW else DECLINE, subject.library, subject.advisory).joinToString("$SEPARATOR")

        /** Entries this build can't read are skipped, and dropped by the next write to their subject only. */
        fun decodeEntry(entry: String): Pair<Subject, Boolean>? {
            val parts = entry.split(SEPARATOR)
            if (parts.size != 3) return null
            val allow = when (parts[0]) {
                ALLOW -> true
                DECLINE -> false
                else -> return null
            }
            return Subject(parts[1], parts[2]) to allow
        }

        fun decode(entries: List<String>): Map<Subject, Boolean> = entries.mapNotNull(::decodeEntry).toMap()
    }
}
