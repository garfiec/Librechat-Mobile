package com.garfiec.librechat.core.data.db

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Holds every production write of `messages` to `MessageDao.upsertAllIndexed`, which indexes the
 * rows in the same transaction. A DAO interface can't make its raw `@Upsert` methods private, so a
 * repository calling `upsert`/`upsertAll` directly would compile and quietly leave those messages
 * out of search until the next session's repair.
 */
class MessageWritePairingTest {

    @Test
    fun onlyTheDaoWritesMessagesUnindexed() {
        val sources = File("src/commonMain/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertWithMessage("source scan found no files; is the working directory the module?").that(sources).isNotEmpty()

        val offenders = sources
            .filterNot { it.invariantSeparatorsPath.contains("/db/dao/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (RAW_WRITE.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
                }
            }

        assertWithMessage("write messages through messageDao.upsertAllIndexed").that(offenders).isEmpty()
    }

    private companion object {
        val RAW_WRITE = Regex("""\b[mM]essageDao\(?\)?\.(upsert|upsertAll|upsertSearchRows)\(""")
    }
}
