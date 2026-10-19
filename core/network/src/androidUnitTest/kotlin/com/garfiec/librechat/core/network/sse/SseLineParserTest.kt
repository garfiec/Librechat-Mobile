package com.garfiec.librechat.core.network.sse

import app.cash.turbine.test
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.google.common.truth.Truth.assertThat
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SseLineParserTest {

    private val parser = SseLineParser(lineReadTimeoutMs = 5_000L)

    private fun channel(text: String): ByteReadChannel =
        ByteReadChannel(text.encodeToByteArray())

    @Test
    fun `parses single event`() = runTest {
        val input = "data: {\"text\":\"hello\"}\n\n"
        parser.parse(channel(input)).test {
            val event = awaitItem()
            assertThat(event.event).isEmpty()
            assertThat(event.data).isEqualTo("{\"text\":\"hello\"}")
            awaitComplete()
        }
    }

    @Test
    fun `parses event with type`() = runTest {
        val input = "event: message\ndata: {\"text\":\"hi\"}\n\n"
        parser.parse(channel(input)).test {
            val event = awaitItem()
            assertThat(event.event).isEqualTo("message")
            assertThat(event.data).isEqualTo("{\"text\":\"hi\"}")
            awaitComplete()
        }
    }

    @Test
    fun `parses multiple events`() = runTest {
        val input = "data: {\"n\":1}\n\ndata: {\"n\":2}\n\n"
        parser.parse(channel(input)).test {
            assertThat(awaitItem().data).isEqualTo("{\"n\":1}")
            assertThat(awaitItem().data).isEqualTo("{\"n\":2}")
            awaitComplete()
        }
    }

    @Test
    fun `handles multiline data`() = runTest {
        val input = "data: line1\ndata: line2\ndata: line3\n\n"
        parser.parse(channel(input)).test {
            val event = awaitItem()
            assertThat(event.data).isEqualTo("line1\nline2\nline3")
            awaitComplete()
        }
    }

    @Test
    fun `ignores comment lines`() = runTest {
        val input = ": keepalive\ndata: {\"ok\":true}\n\n"
        parser.parse(channel(input)).test {
            val event = awaitItem()
            assertThat(event.data).isEqualTo("{\"ok\":true}")
            awaitComplete()
        }
    }

    @Test
    fun `stops on DONE marker`() = runTest {
        val input = "data: {\"n\":1}\n\ndata: [DONE]\n\n"
        parser.parse(channel(input)).test {
            assertThat(awaitItem().data).isEqualTo("{\"n\":1}")
            awaitComplete()
        }
    }

    @Test
    fun `emits remaining buffered data on channel close`() = runTest {
        // No trailing \n\n - channel closes with buffered data
        val input = "data: {\"partial\":true}"
        parser.parse(channel(input)).test {
            val event = awaitItem()
            assertThat(event.data).isEqualTo("{\"partial\":true}")
            awaitComplete()
        }
    }

    @Test
    fun `ignores empty events between non-empty events`() = runTest {
        val input = "data: first\n\n\n\ndata: second\n\n"
        parser.parse(channel(input)).test {
            assertThat(awaitItem().data).isEqualTo("first")
            assertThat(awaitItem().data).isEqualTo("second")
            awaitComplete()
        }
    }

    @Test
    fun `resets event type between events`() = runTest {
        val input = "event: type1\ndata: first\n\ndata: second\n\n"
        parser.parse(channel(input)).test {
            val first = awaitItem()
            assertThat(first.event).isEqualTo("type1")
            val second = awaitItem()
            assertThat(second.event).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `handles attachment event type`() = runTest {
        val input = "event: attachment\ndata: {\"file_id\":\"f1\"}\n\n"
        parser.parse(channel(input)).test {
            val event = awaitItem()
            assertThat(event.event).isEqualTo("attachment")
            assertThat(event.data).isEqualTo("{\"file_id\":\"f1\"}")
            awaitComplete()
        }
    }

    @Test
    fun `handles empty channel`() = runTest {
        parser.parse(channel("")).test {
            awaitComplete()
        }
    }

    @Test
    fun `does not emit DONE as data`() = runTest {
        // DONE as sole remaining buffer should not be emitted
        val input = "data: [DONE]"
        parser.parse(channel(input)).test {
            awaitComplete()
        }
    }

    @Test
    fun `cancelling the collector does not log a parse error`() = runTest {
        val logged = mutableListOf<Severity>()
        val previousWriters = Logger.config.logWriterList
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                    logged += severity
                }
            },
        )
        try {
            val open = ByteChannel()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { parser.parse(open).collect {} }
            job.cancel()
            job.join()

            assertThat(job.isCancelled).isTrue()
            assertThat(logged).doesNotContain(Severity.Error)
        } finally {
            Logger.setLogWriters(previousWriters)
        }
    }
}
