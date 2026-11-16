package com.garfiec.librechat.core.data.gate

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/** A real socket on 127.0.0.1, posted to the way Authelia's form_post page does. */
class AndroidLoopbackReceiverTest {

    private fun post(port: Int, path: String, body: String): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.encodeToByteArray()) }
        val status = connection.responseCode
        val text = (if (status < 400) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.readText().orEmpty()
        return status to text
    }

    @Test
    fun `the form post to the callback is parsed and answered with the page`() = runBlocking<Unit> {
        val receiver = AndroidLoopbackReceiver(Dispatchers.IO)
        val port = receiver.start()
        try {
            val params = async { receiver.awaitCallback("<p>done</p>") }
            val (status, page) = async(Dispatchers.IO) {
                post(port, "/callback", "code=abc%2Bd&state=s1&iss=https%3A%2F%2Fauth.example.com&scope=x")
            }.await()

            assertThat(status).isEqualTo(200)
            assertThat(page).isEqualTo("<p>done</p>")
            assertThat(params.await()).containsExactly(
                "code", "abc+d",
                "state", "s1",
                "iss", "https://auth.example.com",
                "scope", "x",
            )
        } finally {
            receiver.close()
        }
    }

    @Test
    fun `other paths are refused and the receiver keeps waiting`() = runBlocking<Unit> {
        val receiver = AndroidLoopbackReceiver(Dispatchers.IO)
        val port = receiver.start()
        try {
            val params = async { receiver.awaitCallback("ok") }
            val (status, _) = async(Dispatchers.IO) { post(port, "/favicon.ico", "") }.await()
            async(Dispatchers.IO) { post(port, "/callback", "code=c&state=s") }.await()

            assertThat(status).isEqualTo(404)
            assertThat(params.await()).containsExactly("code", "c", "state", "s")
        } finally {
            receiver.close()
        }
    }

    /** Chrome opens a connection to the address before it posts; an idle one must not end the wait. */
    @Test
    fun `an idle speculative connection does not block or fail the callback`() = runBlocking<Unit> {
        val receiver = AndroidLoopbackReceiver(Dispatchers.IO)
        val port = receiver.start()
        val idle = Socket("127.0.0.1", port)
        try {
            val params = async { receiver.awaitCallback("ok") }
            async(Dispatchers.IO) { post(port, "/callback", "code=c&state=s") }.await()

            assertThat(withTimeout(5_000) { params.await() }).containsExactly("code", "c", "state", "s")
        } finally {
            idle.close()
            receiver.close()
        }
    }
}
