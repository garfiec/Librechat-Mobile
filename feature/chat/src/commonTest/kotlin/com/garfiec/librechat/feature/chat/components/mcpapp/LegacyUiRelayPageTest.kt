package com.garfiec.librechat.feature.chat.components.mcpapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyUiRelayPageTest {

    @Test
    fun `the inline page is fixed to its view and leaves the frame at the view's height`() {
        val page = LegacyUiRelayPage.build("<p>x</p>")

        assertTrue("overflow:hidden" in page.shellHtml)
        assertTrue("var fitFrame = false;" in page.relayScript)
    }

    @Test
    fun `the full view scrolls and sizes the frame to the height the resource reports`() {
        val page = LegacyUiRelayPage.build("<p>x</p>", fullView = true)

        assertFalse("overflow:hidden" in page.shellHtml)
        assertTrue("var fitFrame = true;" in page.relayScript)
        assertTrue("frame.style.height" in page.relayScript)
    }

    @Test
    fun `a token in the resource's HTML is left as the resource wrote it`() {
        val page = LegacyUiRelayPage.build("/*__FIT_FRAME__*/", fullView = true)

        assertTrue("\"/*__FIT_FRAME__*/\"" in page.relayScript)
    }

    @Test
    fun `both pages keep the body the Android page inlines its script into`() {
        listOf(false, true).forEach { fullView ->
            val page = LegacyUiRelayPage.build("<p>x</p>", fullView)
            assertTrue("<script>prelude" in LegacyUiRelayPage.inlinePage(page, "prelude"))
        }
    }
}
