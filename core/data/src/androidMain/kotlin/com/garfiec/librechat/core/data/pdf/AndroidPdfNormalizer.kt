package com.garfiec.librechat.core.data.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import java.io.File

/**
 * Uses the platform PDF module (pdfium, `framework-pdf`, updated through Google Play system
 * updates). Its writer saves a copy of the object graph without the security handler, so text,
 * fonts and structure are kept as they were.
 *
 * `write(…, removePasswordProtection = true)` only removes security when the document was opened
 * with a NON-EMPTY password; opened with the empty user password, a copy-protected file is written
 * back byte-for-byte. So the file is reopened with [PADDED_EMPTY_PASSWORD]: for the RC4 and
 * AES-128 handlers (revisions 2–4) every user password is padded with that string, which makes it
 * the same key as the empty password while being non-empty. AES-256 (revision 6) has no padding,
 * so those files go up as-is. A PDF that needs a password is decrypted with the one the user typed,
 * which the writer honours for every handler.
 *
 * The writer exists on Android 15+, and on Android 12–14 when the module is at S-extension 13.
 * Older devices only detect: a PDF that needs a password is refused up front, without asking for it.
 */
class AndroidPdfNormalizer internal constructor(
    private val context: Context,
    private val writer: Writer,
) : PdfNormalizer {

    constructor(context: Context) : this(context, Writer.forDevice())

    internal enum class Writer {
        /** `PdfRenderer.write`, API 35+. */
        Platform,

        /** `PdfRendererPreV.write`, Android 12–14 with S-extension 13. */
        PreV,

        /** No writer on this device; detection only. */
        None,
        ;

        companion object {
            fun forDevice(): Writer = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> Platform
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PDF_WRITER_EXTENSION -> PreV
                else -> None
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun normalize(bytes: ByteArray, password: String?): PdfNormalizeResult {
        if (!hasEncryptKey(bytes)) return PdfNormalizeResult.Unchanged
        // PdfRenderer needs seekable descriptors, so the bytes go through files.
        val input = File.createTempFile("pdf-in", ".pdf", context.cacheDir)
        val output = File.createTempFile("pdf-out", ".pdf", context.cacheDir)
        return try {
            input.writeBytes(bytes)
            // Opening without a password is what proves the user password is empty. Only after
            // that is the padded password used, so it can never unlock a genuinely protected file.
            val copyProtectedOnly = opensWithoutPassword(input)
            when {
                // Asking for a password this device cannot use would only fail after it was typed.
                !copyProtectedOnly && writer == Writer.None -> PdfNormalizeResult.PasswordUnsupported
                !copyProtectedOnly && password == null -> PdfNormalizeResult.NeedsPassword
                writer == Writer.None -> PdfNormalizeResult.Failed(
                    UnsupportedOperationException("no PDF writer on this OS version"),
                )
                copyProtectedOnly && !writeUnprotected(input, output, PADDED_EMPTY_PASSWORD) ->
                    PdfNormalizeResult.Failed(
                        UnsupportedOperationException("encryption handler without password padding (AES-256)"),
                    )
                // A non-empty user password is exactly what the writer needs to drop security.
                !copyProtectedOnly && !writeUnprotected(input, output, requireNotNull(password)) ->
                    PdfNormalizeResult.NeedsPassword
                else -> PdfNormalizeResult.Decrypted(output.readBytes())
            }
        } catch (e: Exception) {
            PdfNormalizeResult.Failed(e)
        } finally {
            input.delete()
            output.delete()
        }
    }

    // A SecurityException is the answer here, not a lost error.
    @Suppress("SwallowedException")
    private fun opensWithoutPassword(input: File): Boolean = try {
        input.openForRead().use { PdfRenderer(it).close() }
        true
    } catch (e: SecurityException) {
        false
    }

    /** False when [password] does not open the file. */
    @Suppress("SwallowedException")
    @SuppressLint("NewApi") // Each writer branch is gated by Writer.forDevice().
    private fun writeUnprotected(input: File, output: File, password: String): Boolean = try {
        input.openForRead().use { source ->
            when (writer) {
                Writer.Platform -> PdfRenderer(source, loadParams(password)).use { renderer ->
                    output.openForWrite().use { renderer.write(it, true) }
                }
                Writer.PreV -> PdfRendererPreV(source, loadParams(password)).use { renderer ->
                    output.openForWrite().use { renderer.write(it, true) }
                }
                Writer.None -> error("no writer")
            }
        }
        true
    } catch (e: SecurityException) {
        false
    }

    // The renderer closes its descriptor too; closing it again is a no-op, and covers a
    // constructor that throws before the renderer owns it.
    private fun File.openForRead(): ParcelFileDescriptor =
        ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun File.openForWrite(): ParcelFileDescriptor = ParcelFileDescriptor.open(
        this,
        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
    )

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = PDF_WRITER_EXTENSION)
    private fun loadParams(password: String): LoadParams = LoadParams.Builder().setPassword(password).build()

    internal companion object {
        const val PDF_WRITER_EXTENSION = 13

        /**
         * The 32-byte padding string of the standard security handler (ISO 32000-1, 7.6.3.3,
         * Algorithm 2 step a), as Latin-1 characters. An empty password is padded to exactly
         * these bytes, so both derive the same key.
         */
        val PADDED_EMPTY_PASSWORD: String = byteArrayOf(
            0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41,
            0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(),
            0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A,
        ).toString(Charsets.ISO_8859_1)
    }
}
