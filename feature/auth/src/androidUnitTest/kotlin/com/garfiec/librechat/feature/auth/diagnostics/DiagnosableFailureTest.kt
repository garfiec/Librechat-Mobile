package com.garfiec.librechat.feature.auth.diagnostics

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

class DiagnosableFailureTest {

    private fun error(exception: Throwable?) = Result.Error(exception = exception, message = "x")

    @Test
    fun `no answer offers the log`() {
        assertThat(error(IOException("timeout")).offersDiagnosticLog()).isTrue()
        assertThat(error(null).offersDiagnosticLog()).isTrue()
    }

    @Test
    fun `a page LibreChat did not write offers the log`() {
        assertThat(error(ApiException(403, "Forbidden", serverAuthored = false)).offersDiagnosticLog()).isTrue()
    }

    @Test
    fun `a LibreChat server fault offers the log`() {
        assertThat(error(ApiException(502, "Bad gateway", serverAuthored = true)).offersDiagnosticLog()).isTrue()
    }

    @Test
    fun `LibreChat judging the request does not`() {
        listOf(400, 401, 403, 404, 422, 429).forEach { status ->
            assertThat(error(ApiException(status, "no", serverAuthored = true)).offersDiagnosticLog()).isFalse()
        }
    }
}
