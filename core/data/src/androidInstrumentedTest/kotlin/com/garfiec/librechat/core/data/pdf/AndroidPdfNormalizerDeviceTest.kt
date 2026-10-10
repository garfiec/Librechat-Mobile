package com.garfiec.librechat.core.data.pdf

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.garfiec.librechat.core.data.pdf.AndroidPdfNormalizer.Writer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PdfRenderer is backed by native pdfium, so this runs on a device or emulator only. Each writer
 * is forced through the test seam; [Writer.None] runs against this device's pdfium, not an
 * Android 8–11 one. Fixtures are synthetic; see `resources/pdf/README.md`.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class AndroidPdfNormalizerDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun normalizer(writer: Writer) = AndroidPdfNormalizer(context, writer)

    @Test
    fun copyProtectedPdfIsDecryptedWithItsTextOnBothWriters() {
        for (writer in listOf(Writer.Platform, Writer.PreV)) {
            for (name in listOf("rc4_128_empty_user.pdf", "aes_128_empty_user.pdf")) {
                val input = fixture(name)
                assertThat(failsServerEncryptionScan(input)).isTrue()

                assertDecryptedWithText(normalizer(writer).normalize(input))
            }
        }
    }

    @Test
    fun passwordProtectedPdfIsDecryptedWithTheTypedPassword() {
        for (writer in listOf(Writer.Platform, Writer.PreV)) {
            for (name in listOf("real_password.pdf", "aes_256_real_password.pdf")) {
                assertDecryptedWithText(normalizer(writer).normalize(fixture(name), password = "userpw"))
            }
        }
    }

    @Test
    fun wrongPasswordStillNeedsPassword() {
        for (writer in listOf(Writer.Platform, Writer.PreV)) {
            for (name in listOf("real_password.pdf", "aes_256_real_password.pdf")) {
                assertThat(normalizer(writer).normalize(fixture(name), password = "wrong"))
                    .isEqualTo(PdfNormalizeResult.NeedsPassword)
            }
        }
    }

    @Test
    fun withoutAWriterAPasswordProtectedPdfIsRefusedWithoutAsking() {
        for (password in listOf(null, "userpw")) {
            assertThat(normalizer(Writer.None).normalize(fixture("real_password.pdf"), password))
                .isEqualTo(PdfNormalizeResult.PasswordUnsupported)
        }
    }

    /** The padded password must never stand in for a real one. */
    @Test
    fun paddedPasswordDoesNotOpenAPasswordProtectedPdf() {
        assertThat(
            normalizer(Writer.Platform).normalize(fixture("real_password.pdf"), AndroidPdfNormalizer.PADDED_EMPTY_PASSWORD),
        ).isEqualTo(PdfNormalizeResult.NeedsPassword)
    }

    @Test
    fun aes256HasNoPaddingSoItIsLeftToUploadAsIs() {
        for (writer in Writer.entries) {
            assertThat(normalizer(writer).normalize(fixture("aes_256_empty_user.pdf")))
                .isInstanceOf(PdfNormalizeResult.Failed::class.java)
        }
    }

    @Test
    fun realPasswordIsReportedWhereItCanBeUsed() {
        for (writer in listOf(Writer.Platform, Writer.PreV)) {
            assertThat(normalizer(writer).normalize(fixture("real_password.pdf")))
                .isEqualTo(PdfNormalizeResult.NeedsPassword)
        }
    }

    @Test
    fun withoutAWriterACopyProtectedPdfIsLeftToUploadAsIs() {
        assertThat(normalizer(Writer.None).normalize(fixture("rc4_128_empty_user.pdf")))
            .isInstanceOf(PdfNormalizeResult.Failed::class.java)
    }

    @Test
    fun unencryptedPdfIsUnchanged() {
        for (writer in Writer.entries) {
            assertThat(normalizer(writer).normalize(fixture("plain.pdf"))).isEqualTo(PdfNormalizeResult.Unchanged)
        }
    }

    @Test
    fun unparseablePdfFailsWithoutThrowing() {
        for (writer in Writer.entries) {
            assertThat(normalizer(writer).normalize(fixture("corrupt.pdf")))
                .isInstanceOf(PdfNormalizeResult.Failed::class.java)
        }
    }

    @Test
    fun tempFilesAreCleanedUp() {
        val before = pdfTempFiles()
        for (name in listOf("rc4_128_empty_user.pdf", "aes_256_empty_user.pdf", "real_password.pdf", "corrupt.pdf")) {
            normalizer(Writer.Platform).normalize(fixture(name))
        }
        assertThat(pdfTempFiles()).isEqualTo(before)
    }

    /**
     * Why the padded password exists. If this starts failing, the platform writer strips a
     * document opened with the empty password, and [AndroidPdfNormalizer] can drop the padding.
     */
    @Test
    fun platformWriterKeepsEncryptionWhenOpenedWithoutAPassword() {
        val input = File.createTempFile("tripwire-in", ".pdf", context.cacheDir)
        val output = File.createTempFile("tripwire-out", ".pdf", context.cacheDir)
        try {
            input.writeBytes(fixture("rc4_128_empty_user.pdf"))
            PdfRenderer(ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                ParcelFileDescriptor.open(
                    output,
                    ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE,
                ).use { renderer.write(it, true) }
            }
            assertThat(hasEncryptKey(output.readBytes())).isTrue()
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun assertDecryptedWithText(result: PdfNormalizeResult) {
        assertThat(result).isInstanceOf(PdfNormalizeResult.Decrypted::class.java)
        val output = (result as PdfNormalizeResult.Decrypted).bytes
        assertThat(failsServerEncryptionScan(output)).isFalse()
        assertThat(hasEncryptKey(output)).isFalse()
        assertThat(extractText(output)).contains(FIXTURE_TEXT)
    }

    private fun extractText(bytes: ByteArray): String {
        val file = File.createTempFile("extract", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            return PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                (0 until renderer.pageCount).joinToString("\n") { index ->
                    renderer.openPage(index).use { page -> page.textContents.joinToString("") { it.text } }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun pdfTempFiles() = context.cacheDir.list().orEmpty().filter { it.startsWith("pdf-") }.sorted()

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/pdf/$name")) { "missing fixture $name" }.use { it.readBytes() }

    private companion object {
        const val FIXTURE_TEXT = "photosynthesis converts light into chemical energy"
    }
}
