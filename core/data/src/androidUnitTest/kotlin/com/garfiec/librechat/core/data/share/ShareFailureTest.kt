package com.garfiec.librechat.core.data.share

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

/** v0.8.8 share error bodies, as the HTTP client hands them over, mapped to what the sheet says. */
class ShareFailureTest {

    private fun api(status: Int, body: String? = null, message: String = "msg") =
        ApiException(statusCode = status, message = message, body = body, serverAuthored = body != null)

    private fun sheetError(e: Throwable) = e.toSafeError().toShareFailure().toSheetError()

    @Test
    fun `share service codes are read off the body`() {
        val failure = api(400, """{"message":"m","code":"TARGET_MESSAGE_NOT_FOUND"}""").toSafeError().toShareFailure()

        assertThat(failure.code).isEqualTo(ShareErrorCode.TARGET_MESSAGE_NOT_FOUND)
        assertThat(failure.isTargetRejection).isTrue()
        assertThat(failure.status).isEqualTo(400)
    }

    @Test
    fun `each code maps to the sheet's wording`() {
        assertThat(sheetError(api(400, """{"message":"m","code":"TARGET_MESSAGE_NOT_FOUND"}""")))
            .isEqualTo(ShareSheetError.TargetNotSaved)
        assertThat(sheetError(api(400, """{"message":"m","code":"NO_MESSAGES"}""")))
            .isEqualTo(ShareSheetError.NoMessages)
        assertThat(sheetError(api(404, """{"message":"m","code":"CONVERSATION_NOT_FOUND"}""")))
            .isEqualTo(ShareSheetError.NotFound)
        assertThat(sheetError(api(404, """{"message":"m","code":"SHARE_NOT_FOUND"}""")))
            .isEqualTo(ShareSheetError.NotFound)
        assertThat(sheetError(api(409, """{"message":"m","code":"SHARE_REVISION_MISMATCH"}""")))
            .isEqualTo(ShareSheetError.Generic)
    }

    @Test
    fun `a body-less 404 is not found, and statuses map without a code`() {
        assertThat(sheetError(api(404))).isEqualTo(ShareSheetError.NotFound)
        assertThat(sheetError(api(403))).isEqualTo(ShareSheetError.Forbidden)
        assertThat(sheetError(api(429))).isEqualTo(ShareSheetError.RateLimited)
        assertThat(sheetError(api(500, """{"message":"Error creating shared link"}"""))).isEqualTo(ShareSheetError.Generic)
    }

    @Test
    fun `both content filter ids block, keeping the server's sentence`() {
        val sentence = "Submitted file content could not be inspected before processing."
        val uninspectable = api(
            400,
            """{"error":"content_filter_uninspectable","message":"$sentence","source":"file","field":"file_text"}""",
            message = sentence,
        )
        val blocked = api(
            400,
            """{"error":"content_filter_block","message":"Blocked.","source":"message","field":"text"}""",
            message = "Blocked.",
        )

        assertThat(sheetError(uninspectable)).isEqualTo(ShareSheetError.ContentBlocked(sentence))
        assertThat(sheetError(blocked)).isEqualTo(ShareSheetError.ContentBlocked("Blocked."))
    }

    @Test
    fun `server text that fails the display screen is not carried`() {
        val html = "<html><body>blocked</body></html>"
        val failure = api(400, """{"error":"content_filter_block","message":"x"}""", message = html)
            .toSafeError().toShareFailure()

        assertThat(failure.toSheetError()).isEqualTo(ShareSheetError.ContentBlocked(null))
    }

    @Test
    fun `no connection is a network error`() {
        assertThat(sheetError(IOException("offline"))).isEqualTo(ShareSheetError.Network)
    }
}
