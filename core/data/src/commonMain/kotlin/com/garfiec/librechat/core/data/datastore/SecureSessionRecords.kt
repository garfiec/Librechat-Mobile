package com.garfiec.librechat.core.data.datastore

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.SessionCredentials
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned secure records, with a read-only fallback for existing two-key sessions. */
internal class SecureSessionRecords(
    private val readValue: (String) -> String?,
    private val writeValues: (Map<String, String>) -> Unit,
    private val removeValue: (String) -> Unit,
) {
    private fun scopedKey(account: String?, key: String): String =
        if (account == null) key else accountScopedName(account, key)
    private fun accessKey(account: String?): String = scopedKey(account, CommonTokenDataStore.KEY_ACCESS_TOKEN)
    private fun refreshKey(account: String?): String = scopedKey(account, CommonTokenDataStore.KEY_REFRESH_TOKEN)

    private fun sessionKey(account: String?): String = scopedKey(account, KEY_SESSION)

    private fun decodeSession(encoded: String): SessionCredentials? {
        return try {
            sessionJson.decodeFromString<SessionCredentials>(encoded).takeIf { it.version == 1 }
        } catch (_: SerializationException) {
            Logger.w { "Stored session record was incomplete; sign in again" }
            null
        } catch (_: IllegalArgumentException) {
            Logger.w { "Stored session record was invalid; sign in again" }
            null
        }
    }

    fun readAccess(account: String?): String? {
        val encoded = readValue(sessionKey(account))
        return if (encoded != null) decodeSession(encoded)?.accessToken else readValue(accessKey(account))
    }

    fun readCookies(account: String?): AuthCookies? {
        val encoded = readValue(sessionKey(account))
        return if (encoded != null) decodeSession(encoded)?.cookies else readValue(refreshKey(account))?.let(::AuthCookies)
    }

    fun readSession(account: String?): SessionCredentials? {
        // A present but unreadable record is authoritative: never resurrect older legacy keys.
        val encoded = readValue(sessionKey(account))
        if (encoded != null) return decodeSession(encoded)
        val access = readValue(accessKey(account)) ?: return null
        val refresh = readValue(refreshKey(account)) ?: return null
        return SessionCredentials(access, AuthCookies(refresh))
    }

    fun writeSession(account: String?, session: SessionCredentials) {
        val encoded = sessionJson.encodeToString(session)
        writeValues(mapOf(sessionKey(account) to encoded))
        check(readValue(sessionKey(account)) == encoded) { "The session could not be stored securely" }
        // Only retire old keys after the complete replacement has been read back successfully.
        removeValue(accessKey(account))
        removeValue(refreshKey(account))
    }

    fun removeSession(account: String?) {
        removeValue(sessionKey(account))
        removeValue(accessKey(account))
        removeValue(refreshKey(account))
    }

    companion object {
        const val KEY_SESSION = "session_credentials"
        private val sessionJson = Json { ignoreUnknownKeys = true }
    }
}
