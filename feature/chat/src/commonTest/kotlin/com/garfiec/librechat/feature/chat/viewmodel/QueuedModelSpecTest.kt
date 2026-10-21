package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.feature.chat.util.restore
import kotlin.test.Test
import kotlin.test.assertEquals

class QueuedModelSpecTest {
    @Test
    fun queuedSpecSurvivesComposerEditingWithoutInferringFromTheModel() {
        val queued = QueuedMessage(
            localId = "queued",
            text = "hello",
            choice = ModelChoice.restore("custom", "shared-model", "first-spec"),
            dispatch = EndpointDispatch(null, null, null),
        )
        val composer = queued.toComposerSnapshot()
        val next = queued.copy(localId = "next", choice = ModelChoice.restore(queued.endpoint, queued.model, "second-spec"))
        assertEquals("first-spec", composer.modelSpec)
        assertEquals("first-spec", queued.modelSpec)
        assertEquals("second-spec", next.toComposerSnapshot().modelSpec)
        assertEquals(composer.model, next.model)
    }
}
