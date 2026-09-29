package com.garfiec.librechat.feature.chat

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Compose resources are not Android resources: their string escapes are processed by the Compose
 * Gradle plugin (`handleSpecialCharacters`), which understands only `\n`, `\t`, `\uXXXX` and `\\`.
 * Anything else reaches the screen with its backslash — an `\'` copied from an Android `res/values`
 * file, where aapt requires it, renders as "couldn\'t".
 *
 * Scans every `composeResources` strings file in the repository, all modules and all locales, so a
 * new string anywhere is covered without registering it. Android-only `res/values` files are left
 * alone on purpose: there `\'` is required.
 */
class ComposeResourceEscapesTest {

    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val stringFiles: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != "upstream" && !it.name.startsWith(".") }
        .filter { it.isFile && it.name == "strings.xml" && it.parentFile.parentFile.name == "composeResources" }
        .toList()

    @Test
    fun `the scan finds the compose string files`() {
        // Guards the guard: a path change that finds nothing would pass the check below vacuously.
        assertThat(stringFiles.size).isGreaterThan(10)
    }

    @Test
    fun `no compose string uses an escape the resource compiler does not process`() {
        val offenders = stringFiles.flatMap { file ->
            file.readLines().flatMapIndexed { index, line ->
                // Pairs are consumed left to right, so `\\'` reads as an escaped backslash then a
                // plain quote, exactly as the plugin reads it.
                ESCAPE.findAll(line)
                    .map { it.value }
                    .filterNot { it in PROCESSED || UNICODE.matches(it) }
                    .map { "${file.relativeTo(repoRoot)}:${index + 1}: $it" }
                    .toList()
            }
        }
        assertWithMessage("Compose resources render these backslashes literally; write the character plain")
            .that(offenders)
            .isEmpty()
    }

    private companion object {
        val ESCAPE = Regex("""\\(u[0-9a-fA-F]{4}|[\s\S]?)""")
        val UNICODE = Regex("""\\u[0-9a-fA-F]{4}""")
        val PROCESSED = setOf("""\n""", """\t""", """\\""")
    }
}
