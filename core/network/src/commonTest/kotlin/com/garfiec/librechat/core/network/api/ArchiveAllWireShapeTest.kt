package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.response.ArchiveAllConversationsResponse
import com.garfiec.librechat.core.network.di.librechatJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `POST /api/convos/archive/all` (v0.8.8-rc2). Decoded with the real [librechatJson] so the
 * unknown-key tolerance under test is the one the app actually has.
 */
class ArchiveAllWireShapeTest {

    private fun decode(body: String) =
        librechatJson.decodeFromString(ArchiveAllConversationsResponse.serializer(), body)

    @Test
    fun readsTheArchivedCount() {
        assertEquals(12, decode("""{"archivedCount":12}""").archivedCount)
        assertEquals(0, decode("""{"archivedCount":0}""").archivedCount)
    }

    @Test
    fun aResponseGrowingNewFieldsStillDecodes() {
        assertEquals(3, decode("""{"archivedCount":3,"projectsRefreshed":2}""").archivedCount)
    }
}
