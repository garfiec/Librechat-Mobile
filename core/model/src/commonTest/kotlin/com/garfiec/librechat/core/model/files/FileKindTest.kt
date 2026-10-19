package com.garfiec.librechat.core.model.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileKindTest {

    @Test
    fun extensionsMapToTheirKind() {
        val table = listOf(
            "summary.csv" to FileKind.SPREADSHEET,
            "book.xlsx" to FileKind.SPREADSHEET,
            "letter.docx" to FileKind.DOCUMENT,
            "report.pdf" to FileKind.PDF,
            "notes.txt" to FileKind.TEXT,
            "README.md" to FileKind.TEXT,
            "analysis.py" to FileKind.CODE,
            "index.html" to FileKind.CODE,
            "data.json" to FileKind.DATA,
            "config.yaml" to FileKind.DATA,
            "table.parquet" to FileKind.DATA,
            "chart.png" to FileKind.IMAGE,
            "chart.svg" to FileKind.IMAGE,
            "deck.pptx" to FileKind.PRESENTATION,
            "bundle.zip" to FileKind.ARCHIVE,
            "song.mp3" to FileKind.AUDIO,
            "clip.mov" to FileKind.VIDEO,
            "mystery.bin" to FileKind.FILE,
        )
        for ((name, kind) in table) assertEquals(kind, fileKindOf(name, null), name)
    }

    @Test
    fun extensionBeatsMime() {
        // Generated files often arrive as application/octet-stream or a generic text type.
        assertEquals(FileKind.CODE, fileKindOf("analysis.py", "text/plain"))
        assertEquals(FileKind.SPREADSHEET, fileKindOf("summary.csv", "application/octet-stream"))
        assertEquals(FileKind.IMAGE, fileKindOf("chart.svg", "text/xml"))
    }

    @Test
    fun mimeDecidesWhenTheExtensionDoesNot() {
        val table = listOf(
            "image/png" to FileKind.IMAGE,
            "audio/mpeg" to FileKind.AUDIO,
            "video/mp4" to FileKind.VIDEO,
            "application/pdf" to FileKind.PDF,
            "text/csv" to FileKind.SPREADSHEET,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to FileKind.SPREADSHEET,
            "application/vnd.ms-excel" to FileKind.SPREADSHEET,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to FileKind.DOCUMENT,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to FileKind.PRESENTATION,
            "application/zip" to FileKind.ARCHIVE,
            "application/json" to FileKind.DATA,
            "text/x-python" to FileKind.CODE,
            "application/javascript" to FileKind.CODE,
            "text/plain" to FileKind.TEXT,
            "application/octet-stream" to FileKind.FILE,
            "TEXT/PLAIN" to FileKind.TEXT,
        )
        for ((mime, kind) in table) assertEquals(kind, fileKindOf("upload", mime), mime)
    }

    @Test
    fun missingOrEmptyTypeFallsBackToFile() {
        // Fallback code-output records carry no type, or an empty one.
        assertEquals(FileKind.FILE, fileKindOf(null, null))
        assertEquals(FileKind.FILE, fileKindOf("blob", ""))
        assertEquals(FileKind.FILE, fileKindOf("", "  "))
        assertEquals(FileKind.DATA, fileKindOf("out.parquet", ""))
    }

    @Test
    fun upperCaseExtensions() {
        assertEquals(FileKind.IMAGE, fileKindOf("PHOTO.JPG", null))
        assertEquals(FileKind.PDF, fileKindOf("Report.Pdf", null))
    }

    @Test
    fun compoundExtensions() {
        assertEquals("tar.gz", fileExtension("backup.tar.gz"))
        assertEquals(FileKind.ARCHIVE, fileKindOf("backup.tar.gz", null))
        assertEquals(FileKind.ARCHIVE, fileKindOf("backup.TAR.BZ2", null))
        assertEquals("gz", fileExtension("log.gz"))
    }

    @Test
    fun pathsAndDotfiles() {
        assertEquals(FileKind.CODE, fileKindOf("/mnt/data/src/main.py", null))
        assertNull(fileExtension(".env"))
        assertNull(fileExtension("Makefile"))
        assertNull(fileExtension("trailing."))
    }

    @Test
    fun sandboxRenameIsUndoneBeforeReadingTheExtension() {
        assertEquals(".config.txt", sandboxDisplayName("_.config-abcdef.txt"))
        assertEquals(".dirkeep", sandboxDisplayName("_.dirkeep-88b30b"))
        assertEquals(".a.b", sandboxDisplayName("_.a.b-ABCDEF"))
        assertEquals(FileKind.TEXT, fileKindOf("_.config-abcdef.txt", null))
        // `_.bashrc-abc123` is the dotfile `.bashrc`, which has no extension.
        assertNull(fileExtension("_.bashrc-abc123"))
    }

    @Test
    fun sandboxRenameLeavesOrdinaryNamesAlone() {
        assertEquals("report-deadbe.csv", sandboxDisplayName("report-deadbe.csv"))
        assertEquals("_.short-abc.txt", sandboxDisplayName("_.short-abc.txt"))
        assertEquals("_.bad-ghijkl", sandboxDisplayName("_.bad-ghijkl"))
        assertEquals("_.-abcdef", sandboxDisplayName("_.-abcdef"))
    }

    @Test
    fun codeLanguageNames() {
        assertEquals("Python", codeLanguageOf("analysis.py"))
        assertEquals("HTML", codeLanguageOf("index.html"))
        assertEquals("Kotlin", codeLanguageOf("Main.KT"))
        assertNull(codeLanguageOf("notes.txt"))
        assertNull(codeLanguageOf(null))
    }
}
