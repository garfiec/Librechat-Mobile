package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.UpdateMessageRequest
import com.garfiec.librechat.core.network.di.librechatJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Live captures of `PUT /api/messages/:conversationId/:messageId`, whose two branches the server
 * picks by whether `index` arrived. Nothing above this module can see a JSON key, so getting the
 * body wrong compiles, type-checks and passes every other test — and then writes a field the UI
 * never reads.
 *
 * Encoded through the shipped [librechatJson] rather than a locally rebuilt `Json`: a test that
 * rebuilt the config would be asserting something the app never sends.
 */
class MessageUpdateWireShapeTest {

    private fun encode(request: UpdateMessageRequest) =
        librechatJson.encodeToString(UpdateMessageRequest.serializer(), request)

    @Test
    fun aFlatTextEditSendsNoIndex() {
        // The server's `index === undefined` branch, which updates the message's own `text`.
        assertEquals("""{"text":"edited"}""", encode(UpdateMessageRequest(text = "edited")))
    }

    @Test
    fun aContentPartEditSendsTheIndexAsANumber() {
        // `typeof index !== 'number'` is a 400, so a quoted "2" would be refused.
        assertEquals(
            """{"text":"edited","index":2}""",
            encode(UpdateMessageRequest(text = "edited", index = 2)),
        )
    }

    @Test
    fun theFirstPartIsIndexZeroAndNotOmitted() {
        // Zero is the one index that would vanish if it were ever treated as a default.
        assertEquals(
            """{"text":"edited","index":0}""",
            encode(UpdateMessageRequest(text = "edited", index = 0)),
        )
    }

    @Test
    fun theBodyIsFlatRatherThanArgWrapped() {
        // The convo mutations wrap in `arg`; the message routes read req.body directly.
        assertEquals(false, encode(UpdateMessageRequest(text = "x", index = 1)).contains("arg"))
    }
}
