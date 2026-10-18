package com.garfiec.librechat.core.logging

import com.garfiec.librechat.core.logging.io.LogFileHandle
import com.garfiec.librechat.core.logging.io.LogSink
import com.garfiec.librechat.core.logging.redact.LogRedactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentLogWriterThreadTest {

    private val files = mutableMapOf<String, StringBuilder>()

    private fun handle(path: String): LogFileHandle = object : LogFileHandle {
        override fun ensureParentDir() = Unit
        override fun exists() = files.containsKey(path)
        override fun appendLine(text: String) {
            files.getOrPut(path) { StringBuilder() }.append(text).append("\n")
        }
        override fun sizeBytes() = (files[path]?.length ?: 0).toLong()
        override fun readText() = files[path]?.toString() ?: ""
        override fun delete() {
            files.remove(path)
        }
        override fun renameTo(targetPath: String) {
            files.remove(path)?.let { files[targetPath] = it }
        }
        override fun lastModifiedMillis() = 0L
    }

    @Test
    fun an_OkHttp_thread_name_does_not_put_the_server_host_in_the_log() {
        val sink = LogSink(dir = "/d", config = LogConfig(), fileFactory = ::handle)
        val scope = CoroutineScope(SupervisorJob())
        val writer = PersistentLogWriter(
            sink = sink,
            redactor = LogRedactor(),
            // OkHttp renames its call threads after the request URL.
            threadName = { "OkHttp https://chat.example.com/..." },
            scope = scope,
            dispatcher = Dispatchers.Unconfined,
            capacity = 8,
            maxThrowableChars = 100,
            maxCrashThrowableChars = 100,
        )

        writer.writeCrashRecord(tag = "TLS", message = "handshake done", throwable = null)
        scope.cancel()

        val log = sink.readAll()
        assertTrue(log.contains("\"thread\":\"OkHttp"), log)
        assertFalse(log.contains("chat.example.com"), "host leaked through the thread name: $log")
    }
}
