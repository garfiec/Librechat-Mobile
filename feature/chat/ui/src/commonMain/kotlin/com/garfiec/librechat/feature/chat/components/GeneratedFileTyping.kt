package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.TextFormat
import com.garfiec.librechat.core.model.files.CODE_LANGUAGES
import com.garfiec.librechat.core.model.files.fileExtension
import com.garfiec.librechat.core.model.files.filepathLeaf
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType

/**
 * [source] as a fenced code block whose fence outruns every backtick run in it, so a file that
 * itself contains a fence can't close the block early and have the rest render as markdown.
 * Upstream `wrapAsFencedCodeBlock`.
 */
internal fun fencedCodeBlock(source: String, lang: String): String {
    val longestRun = Regex("`+").findAll(source).maxOfOrNull { it.value.length } ?: 0
    val fence = "`".repeat(maxOf(3, longestRun + 1))
    return "$fence$lang\n${source.removeSuffix("\n")}\n$fence"
}

/** The language a code file's artifact is named by ("Python"), or null for any other artifact. */
internal fun codeLanguageLabel(artifact: Artifact): String? =
    if (artifact.type == "text/markdown") artifact.language?.let(CODE_LANGUAGES::get) else null

private fun officeBucketMime(bucket: String) =
    "${ArtifactType.OFFICE_PREVIEW_MIME_PREFIX}$bucket${ArtifactType.OFFICE_PREVIEW_MIME_SUFFIX}"

private val DOCX_PREVIEW = officeBucketMime("docx")
private val SPREADSHEET_PREVIEW = officeBucketMime("spreadsheet")
private val PRESENTATION_PREVIEW = officeBucketMime("presentation")

/**
 * The office subset of upstream's `EXTENSION_TO_TOOL_ARTIFACT_TYPE` (`client/src/utils/artifacts.ts`).
 * `odt` is absent on purpose: upstream keeps it on the plain-text path, as it has no HTML producer.
 */
private val OFFICE_BUCKET_BY_EXTENSION: Map<String, String> = mapOf(
    "docx" to DOCX_PREVIEW,
    "csv" to SPREADSHEET_PREVIEW,
    "xlsx" to SPREADSHEET_PREVIEW,
    "xls" to SPREADSHEET_PREVIEW,
    "ods" to SPREADSHEET_PREVIEW,
    "pptx" to PRESENTATION_PREVIEW,
    "potx" to PRESENTATION_PREVIEW,
)

/** The office subset of upstream's `MIME_TO_TOOL_ARTIFACT_TYPE`, for files whose name has no extension. */
private val OFFICE_BUCKET_BY_MIME: Map<String, String> = mapOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DOCX_PREVIEW,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to SPREADSHEET_PREVIEW,
    "application/vnd.ms-excel" to SPREADSHEET_PREVIEW,
    "application/vnd.oasis.opendocument.spreadsheet" to SPREADSHEET_PREVIEW,
    "text/csv" to SPREADSHEET_PREVIEW,
    "application/csv" to SPREADSHEET_PREVIEW,
    "text/comma-separated-values" to SPREADSHEET_PREVIEW,
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to PRESENTATION_PREVIEW,
    "application/vnd.openxmlformats-officedocument.presentationml.template" to PRESENTATION_PREVIEW,
)

/** Upstream `excelMimeTypes` (`packages/data-provider/src/file-config.ts`), which upstream also routes to the spreadsheet bucket. */
private val EXCEL_MIME = Regex(
    "^application/(vnd\\.ms-excel|msexcel|x-msexcel|x-ms-excel|x-excel|x-dos_ms_excel|xls|x-xls|" +
        "vnd\\.openxmlformats-officedocument\\.spreadsheetml\\.sheet)$",
)

/**
 * The office preview bucket a file belongs to by its name or MIME, whatever its `textFormat`:
 * a code-interpreter document arrives with its real MIME (`text/csv`, the openxml types), not a
 * `-preview` bucket. Extension wins over MIME, as upstream's `detectArtifactTypeFromFile`.
 */
internal fun officeBucketOf(attachment: Attachment): String? {
    if (ArtifactType.isOfficePreviewMime(attachment.type)) return attachment.type
    val name = attachment.filename ?: attachment.filepath?.let(::filepathLeaf)
    name?.let(::fileExtension)?.let(OFFICE_BUCKET_BY_EXTENSION::get)?.let { return it }
    val mime = attachment.type?.substringBefore(';')?.trim()?.lowercase() ?: return null
    return OFFICE_BUCKET_BY_MIME[mime] ?: SPREADSHEET_PREVIEW.takeIf { EXCEL_MIME.matches(mime) }
}

/**
 * The `-preview` MIME an office document renders under, or null when it doesn't render as one.
 * A `-preview` attachment always does: [OfficePreviewCard] runs it through the `textFormat` gate
 * itself. A file of any other type does only when the server marked its text `html`.
 *
 * SECURITY: without that flag the text may be a raw extraction, and routing it here would inject
 * it as HTML. Such a file falls through to [attachmentToArtifact] as escaped plain text.
 */
internal fun officePreviewMimeOf(attachment: Attachment): String? {
    if (ArtifactType.isOfficePreviewMime(attachment.type)) return attachment.type
    if (attachment.textFormat != TextFormat.HTML) return null
    return officeBucketOf(attachment)
}
