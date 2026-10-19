package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.isCodeDownloadFallback
import com.garfiec.librechat.core.model.files.isExpired
import kotlin.time.Clock

/**
 * Attachment `type` values that are NOT files — they carry structured payloads (search sources,
 * memory artifacts, MCP UI resources) that mobile renders from tool *output* elsewhere. They must
 * never render as a file chip. Mirrors upstream `TAttachmentMetadata` (schemas.ts).
 */
private val PSEUDO_ATTACHMENT_TYPES = setOf("web_search", "file_search", "memory", "ui_resources")

/** Sandbox placeholder leaves the backend injects to keep empty dirs; never shown. Upstream
 *  `SANDBOX_PLACEHOLDER_LEAVES` (attachmentTypes.ts). */
private val SANDBOX_PLACEHOLDER = Regex("""^_\.(?:dirkeep|gitkeep)-[0-9a-f]{6}$""", RegexOption.IGNORE_CASE)

internal fun isPseudoAttachment(attachment: Attachment): Boolean = attachment.type in PSEUDO_ATTACHMENT_TYPES

internal fun isSandboxPlaceholder(attachment: Attachment): Boolean =
    attachmentName(attachment)?.let { SANDBOX_PLACEHOLDER.matches(it) } == true

/**
 * Whether `FileRepository.downloadAttachment` has a route for this file: a stored one by its id, a
 * code-output fallback by its sandbox path. False once the sandbox has dropped it.
 */
internal fun isDownloadable(attachment: Attachment, nowMs: Long): Boolean =
    !attachment.isExpired(nowMs) && (attachment.fileId != null || attachment.isCodeDownloadFallback)

/** Identifies a file for in-flight tracking and dedupe: a fallback has a path but no id. */
internal fun downloadKey(attachment: Attachment): String? = attachment.filepath ?: attachment.fileId

internal fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
