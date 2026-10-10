package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.pdf.PDF_INCORRECT_PASSWORD_MESSAGE
import com.garfiec.librechat.core.data.pdf.PDF_PASSWORD_PROTECTED_MESSAGE
import com.garfiec.librechat.core.data.pdf.PdfDecryptionUnavailableException
import com.garfiec.librechat.core.data.pdf.PdfNormalizeResult
import com.garfiec.librechat.core.data.pdf.PdfNormalizer
import com.garfiec.librechat.core.data.pdf.PdfPasswordProtectedException
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.network.api.FilesApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The upload chokepoint's PDF handling: every caller (chat, Files, agent files) reaches the server
 * through [FileRepository.uploadFile], so what is posted here is what the server stores.
 */
class FileRepositoryPdfNormalizeTest {

    private val filesApi = mockk<FilesApi>()
    private val normalizer = mockk<PdfNormalizer>()
    private val repository = FileRepositoryImpl(filesApi, mockk(), mockk(), normalizer)
    private val posted = slot<ByteArray>()

    init {
        coEvery {
            filesApi.uploadFile(capture(posted), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers { FileObject(fileId = "f1", filename = "doc.pdf", filepath = "/f1", type = "application/pdf", bytes = 1) }
    }

    @Test
    fun `unencrypted PDF is posted as the same array without consulting the normalizer`() = runTest {
        val plain = pdf("/Root 1 0 R")

        val result = repository.uploadFile(plain, "doc.pdf", "application/pdf", fileId = "f1")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(posted.captured).isSameInstanceAs(plain)
        verify(exactly = 0) { normalizer.normalize(any(), any()) }
    }

    @Test
    fun `copy-protected PDF is posted decrypted`() = runTest {
        val decrypted = pdf("decrypted")
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.Decrypted(decrypted)

        repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1")

        assertThat(posted.captured).isSameInstanceAs(decrypted)
    }

    @Test
    fun `both upload overloads normalize`() = runTest {
        val decrypted = pdf("decrypted")
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.Decrypted(decrypted)

        repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", onProgress = null)

        assertThat(posted.captured).isSameInstanceAs(decrypted)
    }

    @Test
    fun `password-protected PDF is refused with a readable message and never posted`() = runTest {
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.NeedsPassword

        val result = repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        result as Result.Error
        assertThat(result.message).isEqualTo(PDF_PASSWORD_PROTECTED_MESSAGE)
        assertThat((result.exception as PdfPasswordProtectedException).incorrectPassword).isFalse()
        coVerify(exactly = 0) {
            filesApi.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `the typed password reaches the normalizer and the decrypted copy is posted`() = runTest {
        val decrypted = pdf("decrypted")
        every { normalizer.normalize(any(), "hunter2") } returns PdfNormalizeResult.Decrypted(decrypted)

        repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1", pdfPassword = "hunter2")

        assertThat(posted.captured).isSameInstanceAs(decrypted)
    }

    @Test
    fun `a wrong password is refused as incorrect and never posted`() = runTest {
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.NeedsPassword

        val result = repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1", pdfPassword = "nope")

        result as Result.Error
        assertThat(result.message).isEqualTo(PDF_INCORRECT_PASSWORD_MESSAGE)
        assertThat((result.exception as PdfPasswordProtectedException).incorrectPassword).isTrue()
        coVerify(exactly = 0) {
            filesApi.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a password-protected PDF the device cannot decrypt is refused, not uploaded encrypted`() = runTest {
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.Failed(UnsupportedOperationException())

        val result = repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1", pdfPassword = "right")

        result as Result.Error
        assertThat(result.exception).isInstanceOf(PdfDecryptionUnavailableException::class.java)
        coVerify(exactly = 0) {
            filesApi.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a password this device cannot use is refused up front, not prompted for`() = runTest {
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.PasswordUnsupported

        val result = repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1")

        result as Result.Error
        assertThat(result.exception).isInstanceOf(PdfDecryptionUnavailableException::class.java)
        assertThat(result.exception).isNotInstanceOf(PdfPasswordProtectedException::class.java)
        coVerify(exactly = 0) {
            filesApi.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `decrypted output still carrying an Encrypt key after a password is refused`() = runTest {
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.Decrypted(encrypted())

        val result = repository.uploadFile(encrypted(), "doc.pdf", "application/pdf", fileId = "f1", pdfPassword = "right")

        assertThat((result as Result.Error).exception).isInstanceOf(PdfDecryptionUnavailableException::class.java)
    }

    @Test
    fun `normalizer failure uploads the original bytes`() = runTest {
        val original = encrypted()
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.Failed(IllegalStateException())

        repository.uploadFile(original, "doc.pdf", "application/pdf", fileId = "f1")

        assertThat(posted.captured).isSameInstanceAs(original)
    }

    @Test
    fun `normalizer that throws uploads the original bytes`() = runTest {
        val original = encrypted()
        every { normalizer.normalize(any(), any()) } throws IllegalStateException("boom")

        repository.uploadFile(original, "doc.pdf", "application/pdf", fileId = "f1")

        assertThat(posted.captured).isSameInstanceAs(original)
    }

    @Test
    fun `decrypted output still carrying an Encrypt key is discarded`() = runTest {
        val original = encrypted()
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.Decrypted(encrypted())

        repository.uploadFile(original, "doc.pdf", "application/pdf", fileId = "f1")

        assertThat(posted.captured).isSameInstanceAs(original)
    }

    @Test
    fun `non-PDF files never reach the normalizer`() = runTest {
        val text = "/Encrypt 5 0 R".encodeToByteArray()

        repository.uploadFile(text, "notes.txt", "text/plain", fileId = "f1")

        assertThat(posted.captured).isSameInstanceAs(text)
        verify(exactly = 0) { normalizer.normalize(any(), any()) }
    }

    @Test
    fun `PDF with a generic type is recognized by its extension`() = runTest {
        every { normalizer.normalize(any(), any()) } returns PdfNormalizeResult.NeedsPassword

        val result = repository.uploadFile(encrypted(), "doc.PDF", "application/octet-stream", fileId = "f1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
    }

    private fun pdf(body: String) = "%PDF-1.7\n$body\n%%EOF".encodeToByteArray()

    // The trailer form qpdf writes: no space after the key.
    private fun encrypted() = pdf("trailer << /Root 1 0 R /Encrypt\n7 0 R >>")
}
