package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.toSafeError
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The screens' translated failure text leads; only the server's own words are added. */
class ServerMessageTest {

    @Test
    fun `text the server wrote is kept`() {
        val error = ApiException(statusCode = 400, message = "Maximum favorites reached", serverAuthored = true).toSafeError()
        assertThat(error.serverMessage()).isEqualTo("Maximum favorites reached")
    }

    @Test
    fun `the English fallback for a failure kind is not the server's`() {
        val error = IllegalStateException("socket closed").toSafeError()
        assertThat(error.message).isNotNull()
        assertThat(error.serverMessage()).isNull()
    }

    @Test
    fun `an app-written status message is not the server's`() {
        val error = ApiException(statusCode = 403, message = "Access denied", serverAuthored = false).toSafeError()
        assertThat(error.serverMessage()).isNull()
    }

    @Test
    fun `server text the screen rejected is not shown`() {
        val error = ApiException(statusCode = 502, message = "<html><body>Bad gateway</body></html>", serverAuthored = true)
            .toSafeError()
        assertThat(error.serverMessage()).isNull()
    }

    @Test
    fun `a message with no exception behind it is the app's`() {
        assertThat(Result.Error(message = "Not found").serverMessage()).isNull()
    }

    @Test
    fun `the failure text leads with the screen's words`() {
        assertThat(failureText("Couldn't delete this agent", null)).isEqualTo("Couldn't delete this agent")
        assertThat(failureText("Couldn't delete this agent", "In use by a project"))
            .isEqualTo("Couldn't delete this agent\nIn use by a project")
    }
}
