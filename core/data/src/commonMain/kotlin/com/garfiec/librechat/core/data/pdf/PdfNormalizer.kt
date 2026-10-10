package com.garfiec.librechat.core.data.pdf

/**
 * Removes a PDF's encryption layer when it only restricts permissions (empty user password), so
 * the file survives the server's send-time PDF check.
 *
 * LibreChat's `validateAnthropicPdf` rejects any PDF whose bytes contain `/Encrypt `, `/U (` or
 * `/O (` ([failsServerEncryptionScan]), which collapses "copy-protected" and "password-protected"
 * into one error. Copy-protected files open in every reader, so decrypting them loses nothing; a
 * file that genuinely needs a password is only ever decrypted with the password its owner typed.
 *
 * Both platforms use their own PDF frameworks (PDFKit, pdfium's `framework-pdf`), and each has
 * cases it cannot strip — see `AndroidPdfNormalizer` — which it reports as
 * [PdfNormalizeResult.Failed].
 */
interface PdfNormalizer {
    /**
     * [password] is the user-entered password for a file that needs one; it is ignored for a file
     * that opens without one. Must not throw for bad input — report it as
     * [PdfNormalizeResult.Failed].
     */
    fun normalize(bytes: ByteArray, password: String? = null): PdfNormalizeResult
}

sealed interface PdfNormalizeResult {
    /** Not encrypted; upload the original bytes. */
    data object Unchanged : PdfNormalizeResult

    /** [bytes] is the same document without encryption. */
    class Decrypted(val bytes: ByteArray) : PdfNormalizeResult

    /** Needs a password to open, and none was given or the one given is wrong. */
    data object NeedsPassword : PdfNormalizeResult

    /** Could not be parsed or rewritten. */
    class Failed(val cause: Throwable?) : PdfNormalizeResult

    /**
     * Needs a password, and this device could not decrypt it even with the right one — so asking
     * for it would only lead to a failure after the user typed it.
     */
    data object PasswordUnsupported : PdfNormalizeResult
}

/**
 * The upload was refused before it was sent: the PDF needs a password to open. The UI asks for
 * one and retries the upload with it; [incorrectPassword] is true when the retry's was wrong.
 */
class PdfPasswordProtectedException(val incorrectPassword: Boolean) :
    Exception(if (incorrectPassword) "Incorrect PDF password" else "PDF requires a password to open")

/** The PDF needs a password, and this device cannot write a decrypted copy of it. */
class PdfDecryptionUnavailableException(cause: Throwable?) :
    Exception("PDF could not be decrypted on this device", cause)

internal const val PDF_PASSWORD_PROTECTED_MESSAGE =
    "This PDF is password-protected. Remove the password and try again."
internal const val PDF_INCORRECT_PASSWORD_MESSAGE = "Incorrect password for this PDF."
internal const val PDF_DECRYPTION_UNAVAILABLE_MESSAGE =
    "This PDF is password-protected and can't be decrypted on this device. " +
        "Remove its password in a PDF app and attach it again."

private val PDF_HEADER = "%PDF-".encodeToByteArray()
private val ENCRYPT_KEY = "/Encrypt".encodeToByteArray()

/**
 * The byte scan in upstream `validateAnthropicPdf` (`packages/api/src/files/validation.ts`),
 * registered in `scripts/mirrors.json`. A match means the server refuses the file for Anthropic.
 */
private val SERVER_ENCRYPTION_NEEDLES = listOf("/Encrypt ", "/U (", "/O (").map { it.encodeToByteArray() }

internal fun isPdfUpload(type: String, filename: String, bytes: ByteArray): Boolean =
    (type.equals("application/pdf", ignoreCase = true) || filename.endsWith(".pdf", ignoreCase = true)) &&
        bytes.startsWith(PDF_HEADER)

/**
 * Whether the file could be encrypted at all. Deliberately looser than the server's scan (no
 * trailing space): the trailer may write `/Encrypt\n12 0 R`. An encryption dictionary may not live
 * in an object stream, so the key always appears in plain bytes when it is present.
 */
internal fun hasEncryptKey(bytes: ByteArray): Boolean = bytes.indexOf(ENCRYPT_KEY) >= 0

fun failsServerEncryptionScan(bytes: ByteArray): Boolean =
    SERVER_ENCRYPTION_NEEDLES.any { bytes.indexOf(it) >= 0 }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    val first = needle[0]
    val last = size - needle.size
    var i = 0
    while (i <= last) {
        if (this[i] == first) {
            var j = 1
            while (j < needle.size && this[i + j] == needle[j]) j++
            if (j == needle.size) return i
        }
        i++
    }
    return -1
}
