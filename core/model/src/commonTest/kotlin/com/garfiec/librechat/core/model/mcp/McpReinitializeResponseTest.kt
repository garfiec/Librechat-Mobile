package com.garfiec.librechat.core.model.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpReinitializeResponseTest {

    @Test
    fun authorizationUrlNeedsOAuthRequired() {
        assertEquals(
            "https://idp/authorize",
            McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize").authorizationUrl,
        )
        assertNull(McpReinitializeResponse(oauthUrl = "https://idp/authorize").authorizationUrl)
        assertNull(McpReinitializeResponse(oauthRequired = false, oauthUrl = "https://idp/authorize").authorizationUrl)
    }

    @Test
    fun aBlankOrMissingLinkIsNone() {
        assertNull(McpReinitializeResponse(oauthRequired = true, oauthUrl = " ").authorizationUrl)
        assertNull(McpReinitializeResponse(oauthRequired = true).authorizationUrl)
    }
}
