package com.garfiec.librechat.core.model.files

/**
 * What a file is, for picking its tile and its type label. Finer than the web's `getFileType`,
 * which only matches a handful of extensions and so draws nearly every sandbox output (py, json,
 * svg, zip, pptx) as the same blue "File".
 */
enum class FileKind {
    SPREADSHEET,
    DOCUMENT,
    PDF,
    TEXT,
    CODE,
    DATA,
    IMAGE,
    PRESENTATION,
    ARCHIVE,
    AUDIO,
    VIDEO,
    FILE,
}

/**
 * Extensions rendered as syntax-highlighted code in the artifacts panel, with the language name
 * the file's type label shows ("Python"). Language names are proper nouns, so they aren't
 * translated. The data formats here (`json`, `yaml`, `sql`, …) highlight as code but are
 * [FileKind.DATA].
 */
val CODE_LANGUAGES: Map<String, String> = mapOf(
    "py" to "Python", "js" to "JavaScript", "jsx" to "JavaScript", "ts" to "TypeScript",
    "tsx" to "TypeScript", "kt" to "Kotlin", "kts" to "Kotlin", "java" to "Java", "c" to "C",
    "cc" to "C++", "cpp" to "C++", "h" to "C", "hpp" to "C++", "cs" to "C#", "go" to "Go",
    "rs" to "Rust", "rb" to "Ruby", "php" to "PHP", "swift" to "Swift", "sh" to "Shell",
    "bash" to "Bash", "sql" to "SQL", "json" to "JSON", "yaml" to "YAML", "yml" to "YAML",
    "xml" to "XML", "css" to "CSS", "scss" to "SCSS", "r" to "R", "pl" to "Perl", "lua" to "Lua",
    "dart" to "Dart", "scala" to "Scala", "groovy" to "Groovy", "gradle" to "Gradle",
    "toml" to "TOML", "ini" to "INI",
)

/** Code that the artifacts panel renders some other way (or not at all), named all the same. */
private val OTHER_CODE_LANGUAGES = mapOf(
    "html" to "HTML", "htm" to "HTML", "mjs" to "JavaScript", "cjs" to "JavaScript", "vue" to "Vue",
    "svelte" to "Svelte", "zsh" to "Shell", "ps1" to "PowerShell", "bat" to "Batch", "m" to "Objective-C",
    "mm" to "Objective-C", "ipynb" to "Jupyter",
)

private val DATA_EXTENSIONS = setOf(
    "json", "jsonl", "ndjson", "geojson", "xml", "yaml", "yml", "toml", "ini", "sql", "parquet",
    "avro", "feather", "arrow", "sqlite", "db", "pkl", "pickle", "npy", "npz", "h5", "hdf5", "proto",
)

/** Checked before the last-dot extension, so `data.tar.gz` reads as one archive extension. */
private val COMPOUND_EXTENSIONS = listOf("tar.gz", "tar.bz2", "tar.xz", "tar.zst")

private val KIND_BY_EXTENSION: Map<String, FileKind> = buildMap {
    for (ext in listOf("csv", "tsv", "xls", "xlsx", "xlsm", "ods", "numbers")) put(ext, FileKind.SPREADSHEET)
    for (ext in listOf("doc", "docx", "odt", "rtf", "pages", "epub")) put(ext, FileKind.DOCUMENT)
    put("pdf", FileKind.PDF)
    for (ext in listOf("txt", "text", "md", "markdown", "mdx", "log", "rst")) put(ext, FileKind.TEXT)
    for (ext in CODE_LANGUAGES.keys + OTHER_CODE_LANGUAGES.keys) put(ext, FileKind.CODE)
    for (ext in DATA_EXTENSIONS) put(ext, FileKind.DATA)
    for (ext in listOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "svg", "bmp", "avif", "tif", "tiff", "ico")) {
        put(ext, FileKind.IMAGE)
    }
    for (ext in listOf("ppt", "pptx", "key", "odp")) put(ext, FileKind.PRESENTATION)
    for (ext in listOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "zst") + COMPOUND_EXTENSIONS) {
        put(ext, FileKind.ARCHIVE)
    }
    for (ext in listOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "oga", "opus", "weba")) put(ext, FileKind.AUDIO)
    for (ext in listOf("mp4", "mov", "webm", "mkv", "avi", "m4v")) put(ext, FileKind.VIDEO)
}

/**
 * The kind of [filename], by its extension first and its [mimeType] second — the web's order for
 * generated files, whose MIME type is often generic or, on a fallback record, empty. Either may be
 * null or blank.
 */
fun fileKindOf(filename: String?, mimeType: String?): FileKind {
    val ext = filename?.let { fileExtension(it) }
    if (ext != null) {
        KIND_BY_EXTENSION[ext]?.let { return it }
    }
    return mimeType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::kindOfMime) ?: FileKind.FILE
}

/** The language name for a code file's type label, or null when the extension names none. */
fun codeLanguageOf(filename: String?): String? {
    val ext = filename?.let { fileExtension(it) } ?: return null
    return CODE_LANGUAGES[ext] ?: OTHER_CODE_LANGUAGES[ext]
}

/**
 * The lower-case extension of [filename]'s leaf, after undoing the sandbox's dotfile rename (see
 * [sandboxDisplayName]); null for no extension, including a bare dotfile like `.env`.
 */
fun fileExtension(filename: String): String? {
    val leaf = sandboxDisplayName(filename.substringAfterLast('/')).lowercase()
    COMPOUND_EXTENSIONS.firstOrNull { leaf.endsWith(".$it") && leaf.length > it.length + 1 }?.let { return it }
    val dot = leaf.lastIndexOf('.')
    if (dot <= 0 || dot == leaf.lastIndex) return null
    return leaf.substring(dot + 1)
}

/**
 * Undoes the code sandbox's dotfile rename: `_.config-abcdef.txt` → `.config.txt`,
 * `_.dirkeep-88b30b` → `.dirkeep`. Anything else (incl. `report-deadbe.csv`) passes through.
 * Upstream `getDisplayFileName` / `SANITIZED_DOTFILE_PATTERN`; string ops rather than a regex.
 */
fun sandboxDisplayName(name: String): String {
    if (!name.startsWith(SANITIZED_PREFIX)) return name
    val rest = name.substring(SANITIZED_PREFIX.length)
    // The hash is either the end of the name or sits right before a final, dot-free extension.
    stripHashSuffix(rest)?.let { return ".$it" }
    val dot = rest.lastIndexOf('.')
    if (dot > 0 && dot < rest.lastIndex) {
        stripHashSuffix(rest.substring(0, dot))?.let { return ".$it${rest.substring(dot)}" }
    }
    return name
}

private const val SANITIZED_PREFIX = "_."
private const val HASH_LENGTH = 6

/** `stem-abc123` → `stem`; null unless it ends in a dash and six hex digits after a non-empty stem. */
private fun stripHashSuffix(value: String): String? {
    val dash = value.length - HASH_LENGTH - 1
    if (dash < 1 || value[dash] != '-') return null
    val hash = value.substring(dash + 1)
    if (!hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return value.substring(0, dash)
}

@Suppress("CyclomaticComplexMethod")
private fun kindOfMime(mime: String): FileKind = when {
    mime.startsWith("image/") -> FileKind.IMAGE
    mime.startsWith("audio/") -> FileKind.AUDIO
    mime.startsWith("video/") -> FileKind.VIDEO
    mime == "application/pdf" -> FileKind.PDF
    mime.contains("spreadsheet") || mime.contains("excel") || mime == "text/csv" ||
        mime == "text/tab-separated-values" -> FileKind.SPREADSHEET
    mime.contains("presentation") || mime.contains("powerpoint") -> FileKind.PRESENTATION
    mime.contains("wordprocessing") || mime == "application/msword" || mime.contains("opendocument.text") ||
        mime.contains("rtf") -> FileKind.DOCUMENT
    mime.contains("zip") || mime.contains("x-tar") || mime.contains("gzip") || mime.contains("x-7z") ||
        mime.contains("x-rar") || mime.contains("x-bzip") || mime.contains("x-xz") -> FileKind.ARCHIVE
    mime.contains("json") || mime.contains("xml") || mime.contains("yaml") || mime.contains("sql") ->
        FileKind.DATA
    // Web treats any `text/x-*` as code (`partialTypes`).
    mime.startsWith("text/x-") || mime.contains("javascript") || mime.contains("typescript") -> FileKind.CODE
    mime.startsWith("text/") -> FileKind.TEXT
    else -> FileKind.FILE
}
