package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.NO_PARENT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The way out of a reply that keeps failing because of the files its branch carries (v0.8.9-rc1).
 * Every later turn resends those files, so a plain retry fails the same way.
 *
 * MIRRORED from upstream (scripts/mirrors.json `attachment-recovery-*`):
 * `client/src/components/Messages/Content/Error/attachments.ts` (the patterns, the two code sets,
 * `readErrorCode`, `findAttachmentRecovery`), `messageCarriesFiles` and `parseLangChainErrorCode`
 * (`packages/data-provider/src/messages.ts`, `langchain.ts`).
 */

/**
 * Failures that come from what a conversation carries rather than from the model or the network:
 * a file the code environment refused, a file that could not be restored, a turn over the
 * attachment limits, documents the provider rejected, and a request too long for the context
 * window, which a conversation full of screenshots or documents reaches.
 *
 * `\b` is spelled out as lookarounds, and every pattern is plain enough for Android's ICU engine:
 * a pattern that compiles on the JVM can still throw at class-init on a device.
 */
private val ATTACHMENT_ERROR_PATTERNS: List<Regex> = listOf(
    """code environment file""",
    """resource_recovery_required""",
    """"(INPUT_LENGTH|final_context_overflow)"""",
    """prompt is too long|input is too long|context_length_exceeded|maximum context length""",
    """request_too_large|request entity too large|payload too large""",
    """(?<![A-Za-z0-9_])attach(ed|ments?)(?![A-Za-z0-9_])""",
    """duplicate document names?""",
    """(?<![A-Za-z0-9_])(image|document|pdf|file)s?(?![A-Za-z0-9_])[^.\n]{0,60}""" +
        """(?<![A-Za-z0-9_])(too large|too big|exceeds?|too many|could not be (processed|read)|unsupported|not supported)""" +
        """(?![A-Za-z0-9_])""",
).map { Regex(it, RegexOption.IGNORE_CASE) }

/**
 * Typed failures whose cause the payload already states, where files may still be to blame: the
 * provider refused or could not complete the request without saying it was the files. `error` is
 * Anthropic's bare envelope type.
 */
private val FILE_AGNOSTIC_ERROR_CODES = setOf(
    "error",
    "upstream_model_error",
    "invalid_request_error",
    "google_error",
    "empty_messages",
)

/** LangChain codes with a remedy of their own: the model, its key, or its rate limit. */
private val REMEDIAL_LANGCHAIN_CODES = setOf("MODEL_NOT_FOUND", "MODEL_RATE_LIMIT", "MODEL_AUTHENTICATION")

/** Whether a failed turn's text reads as a failure caused by the conversation's files. */
fun isAttachmentError(text: String): Boolean = ATTACHMENT_ERROR_PATTERNS.any { it.containsMatchIn(text) }

/**
 * Whether dropping files is a plausible way out of this failure. Anything that reads as a file
 * problem is; so is a provider failure that says nothing about its cause. A failure with its own
 * remedy (a missing key, a balance, a rate or message limit, a moderation block) is not.
 */
fun mayBeAttachmentError(text: String): Boolean {
    if (isAttachmentError(text)) return true
    val langChainCode = parseLangChainErrorCode(text)
    if (langChainCode != null && langChainCode in REMEDIAL_LANGCHAIN_CODES) return false
    val code = readErrorCode(text)
    return code == null || code in FILE_AGNOSTIC_ERROR_CODES
}

/**
 * The code a typed payload names (upstream `readErrorCode`): `code` then `type` at the top level,
 * then inside an `error` envelope; but when the top level is typed just `error`, the envelope
 * first, since that type only names the wrapper. Reads the FIRST balanced object only, as upstream's
 * `extractJson` does; this differs on purpose from `StreamErrorType`'s walk.
 */
internal fun readErrorCode(text: String): String? {
    val json = firstBalancedObject(text) ?: return null
    val payload = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
    val envelope = payload["error"] as? JsonObject
    val outer = listOf(payload["code"], payload["type"])
    val nested = listOf(envelope?.get("code"), envelope?.get("type"))
    val candidates = if (payload["type"].stringOrNull() == "error") nested + outer else outer + nested
    return candidates.firstNotNullOfOrNull { it.stringOrNull()?.takeIf(String::isNotEmpty) }
}

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Upstream `extractJson`: the first brace-balanced object, quote-aware once inside one. */
private fun firstBalancedObject(text: String): String? {
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false
    for (i in text.indices) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when {
            c == '"' && depth > 0 -> inString = true
            c == '{' -> {
                if (depth == 0) start = i
                depth++
            }
            c == '}' && depth > 0 -> {
                depth--
                if (depth == 0 && start != -1) return text.substring(start, i + 1)
            }
        }
    }
    return null
}

/**
 * The classification LangChain encoded in the docs URL it appended (upstream
 * `parseLangChainErrorCode`): the last `/errors/<CODE>` segment of a whitespace-delimited token
 * containing `langchain`, upper-cased. A hand-written scan, as upstream's is.
 */
internal fun parseLangChainErrorCode(text: String): String? {
    val searchable = text.lowercase()
    var searchFrom = 0
    while (searchFrom < text.length) {
        val langChainIndex = searchable.indexOf(LANGCHAIN, searchFrom)
        if (langChainIndex < 0) return null
        val tokenEnd = tokenEnd(text, langChainIndex)
        findErrorCode(text, searchable, langChainIndex, tokenEnd)?.let { (start, end) ->
            return text.substring(start, end).uppercase()
        }
        searchFrom = tokenEnd + 1
    }
    return null
}

private const val LANGCHAIN = "langchain"
private const val ERROR_PATH = "/errors/"

private fun tokenEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length && !text[end].isWhitespace()) end++
    return end
}

private fun findErrorCode(text: String, searchable: String, start: Int, end: Int): Pair<Int, Int>? {
    val langChainIndex = searchable.indexOf(LANGCHAIN, start)
    if (langChainIndex < 0 || langChainIndex >= end) return null
    var code: Pair<Int, Int>? = null
    var pathIndex = searchable.indexOf(ERROR_PATH, langChainIndex + LANGCHAIN.length)
    while (pathIndex in 0 until end) {
        val codeStart = pathIndex + ERROR_PATH.length
        var codeEnd = codeStart
        while (codeEnd < end && text[codeEnd].isErrorCodeCharacter()) codeEnd++
        if (codeEnd > codeStart) code = codeStart to codeEnd
        pathIndex = searchable.indexOf(ERROR_PATH, maxOf(codeStart, codeEnd))
    }
    return code
}

private fun Char.isErrorCodeCharacter(): Boolean = this == '_' || this in 'A'..'Z' || this in 'a'..'z'

/**
 * Whether [message] references a stored file a later turn would collect from history and send
 * again: uploads, file-backed attachments, and file references inside its content (a generated
 * image, a provider file block, a steer's own files).
 */
fun messageCarriesFiles(message: Message): Boolean =
    !message.files.isNullOrEmpty() ||
        message.attachments.orEmpty().any { !it.fileId.isNullOrEmpty() } ||
        message.content.orEmpty().any { it.isFileContentPart() || it.hasContentPartFiles() }

private fun MessageContentPart.isFileContentPart(): Boolean =
    !imageFile?.fileId.isNullOrEmpty() || !fileId.isNullOrEmpty() || file.fileIdOrNull() != null

private fun MessageContentPart.hasContentPartFiles(): Boolean = (files as? JsonArray)?.isNotEmpty() == true

private fun JsonElement?.fileIdOrNull(): String? =
    ((this as? JsonObject)?.get("file_id")?.takeUnless { it is JsonNull } as? JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf(String::isNotEmpty)

/**
 * The ways out a failed reply offers when its branch carries files.
 *
 * [parent] is the user turn the failed reply answered. [canRetry]: that turn carries files and has
 * text to send again without them. [branchTargetId]: earlier turns carry files too, which a retry
 * would still resend, so the branch can only be cleared by copying it without any, ending at the
 * reply before the failed turn (the copy then has no failure in it).
 */
data class AttachmentRecovery(
    val parent: Message,
    val canRetry: Boolean,
    val branchTargetId: String?,
)

/**
 * Upstream `findAttachmentRecovery`. [path] is the displayed branch: the failed reply is on it, so
 * its ancestors are the messages before it. Walks the ancestors only, newest first, and stops at
 * the first that carries files.
 */
fun findAttachmentRecovery(path: List<Message>, failed: Message): AttachmentRecovery? {
    if (failed.isCreatedByUser) return null
    val byId = path.associateBy { it.messageId }
    val parent = failed.parentMessageId?.let(byId::get) ?: return null
    if (!parent.isCreatedByUser) return null

    var earlierCarriesFiles = false
    val visited = mutableSetOf(parent.messageId)
    var currentId = parent.parentMessageId
    while (currentId != null && currentId != NO_PARENT && visited.add(currentId)) {
        val ancestor = byId[currentId] ?: break
        if (messageCarriesFiles(ancestor)) {
            earlierCarriesFiles = true
            break
        }
        currentId = ancestor.parentMessageId
    }

    val canRetry = messageCarriesFiles(parent) && parent.text.isNotBlank()
    val previousId = parent.parentMessageId
    val branchTargetId = previousId.takeIf { earlierCarriesFiles && it != null && it != NO_PARENT }
    if (!canRetry && branchTargetId == null) return null
    return AttachmentRecovery(parent, canRetry, branchTargetId)
}

/**
 * The raw failure a reply carries, as its error card renders it: a persisted failed turn's text,
 * else its last `error` part's. Null for a reply that did not fail.
 */
fun failureText(message: Message): String? {
    if (message.isCreatedByUser) return null
    if (message.error && message.content.isNullOrEmpty() && message.text.isNotBlank()) return message.text
    val part = message.content?.lastOrNull { it.type == ContentType.ERROR } ?: return null
    return part.error ?: part.text.orEmpty()
}

/** What a recovery card says and offers, decided once per failed reply on the displayed branch. */
data class AttachmentRecoveryView(
    val failedMessageId: String,
    /** The failure reads as a file problem, so the explanation leads with the cause. */
    val namesCause: Boolean,
    val canRetry: Boolean,
    val canBranch: Boolean,
)

/**
 * The recovery card for each failed reply on [path], keyed by reply id. [branchSupported] is the
 * rc1 gate: an older server ignores `excludeFiles` and returns an ordinary copy.
 */
fun attachmentRecoveries(path: List<Message>, branchSupported: Boolean): Map<String, AttachmentRecoveryView> {
    if (path.none { !it.isCreatedByUser && (it.error || it.content?.any { p -> p.type == ContentType.ERROR } == true) }) {
        return emptyMap()
    }
    return path.mapNotNull { recoveryViewFor(path, it, branchSupported) }.associateBy { it.failedMessageId }
}

private fun recoveryViewFor(path: List<Message>, message: Message, branchSupported: Boolean): AttachmentRecoveryView? {
    val text = failureText(message)?.takeIf(::mayBeAttachmentError) ?: return null
    val recovery = findAttachmentRecovery(path, message) ?: return null
    val canBranch = branchSupported && recovery.branchTargetId != null
    if (!recovery.canRetry && !canBranch) return null
    return AttachmentRecoveryView(
        failedMessageId = message.messageId,
        namesCause = isAttachmentError(text),
        canRetry = recovery.canRetry,
        canBranch = canBranch,
    )
}
