package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.garfiec.librechat.core.model.auth.SessionCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SecureSessionRecordsTest {
    private val session = SessionCredentials("access", AuthCookies("refresh", OpenIdMarkers("marker")))

    @Test
    fun failedWriteDoesNotRemoveTheLegacySession() {
        val values = mutableMapOf("access_token" to "legacy-access", "refresh_token" to "legacy-refresh")
        val records = SecureSessionRecords(values::get, {}, { values.remove(it) })
        assertFailsWith<IllegalStateException> { records.writeSession(null, session) }
        assertEquals(SessionCredentials("legacy-access", AuthCookies("legacy-refresh")), records.readSession(null))
    }

    @Test
    fun failedRotationPreservesThePreviousRecord() {
        val values = mutableMapOf<String, String>()
        var writeFails = false
        val records = SecureSessionRecords(values::get, { if (!writeFails) values.putAll(it) }, { values.remove(it) })
        records.writeSession("A", session)
        writeFails = true
        assertFailsWith<IllegalStateException> {
            records.writeSession("A", SessionCredentials("new-access", AuthCookies("new-refresh", OpenIdMarkers("new-marker"))))
        }
        assertEquals(session, records.readSession("A"))
    }

    @Test
    fun migrationAndRemovalCoverEveryCredentialField() {
        val values = mutableMapOf("access_token" to "legacy-access", "refresh_token" to "legacy-refresh")
        val records = SecureSessionRecords(values::get, values::putAll, { values.remove(it) })
        records.writeSession(null, session)
        assertEquals(setOf(SecureSessionRecords.KEY_SESSION), values.keys)
        assertEquals(session, records.readSession(null))
        records.removeSession(null)
        assertFalse(values.isNotEmpty())
        assertNull(records.readSession(null))
    }

    @Test
    fun unknownRecordVersionsCannotFallBackToStaleKeys() {
        val values = mapOf(
            "access_token" to "old-access", "refresh_token" to "old-refresh",
            SecureSessionRecords.KEY_SESSION to
                """{"version":2,"accessToken":"new-access","cookies":{"refreshToken":"new-refresh"}}""",
        )
        val records = SecureSessionRecords(values::get, {}, {})
        assertNull(records.readSession(null))
        assertNull(records.readAccess(null))
        assertNull(records.readCookies(null))
    }
}
