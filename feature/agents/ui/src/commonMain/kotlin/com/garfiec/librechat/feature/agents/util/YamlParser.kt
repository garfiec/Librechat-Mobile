package com.garfiec.librechat.feature.agents.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses a YAML string into a [JsonObject].
 * Pure Kotlin implementation — no external YAML library required.
 * Handles the YAML subset used by OpenAPI specs: maps, lists, scalars, and nested structures.
 */
fun parseYamlToJsonObject(yaml: String): JsonObject {
    val parser = YamlParser(yaml)
    val element = parser.parseDocument()
    return element as? JsonObject
        ?: throw IllegalArgumentException(
            "Expected a YAML mapping (object) at the root, but got ${element::class.simpleName}",
        )
}

private class YamlParser(input: String) {
    private val lines: List<String> = input.lines()
    private var pos: Int = 0

    fun parseDocument(): JsonElement {
        skipBlanksAndComments()
        if (pos >= lines.size) return JsonObject(emptyMap())
        return parseValue(currentIndent())
    }

    private fun parseValue(baseIndent: Int): JsonElement {
        if (pos >= lines.size) return JsonNull
        val line = lines[pos]
        val trimmed = line.trimStart()

        // List item at current level
        if (trimmed.startsWith("- ") || trimmed == "-") {
            return parseList(baseIndent)
        }

        // Map entry (key: value)
        if (trimmed.contains(':')) {
            return parseMap(baseIndent)
        }

        // Bare scalar
        pos++
        return scalarToJsonPrimitive(trimmed)
    }

    private fun parseMap(baseIndent: Int): JsonObject {
        val entries = mutableMapOf<String, JsonElement>()
        while (pos < lines.size) {
            skipBlanksAndComments()
            if (shouldStopParsingMap(baseIndent)) break

            val line = lines[pos]
            val trimmed = line.trimStart()
            val colonIdx = findKeyColonIndex(trimmed)

            val key = trimmed.substring(0, colonIdx).trim().unquoteYaml()
            val afterColon = trimmed.substring(colonIdx + 1).trim()

            pos++

            val value: JsonElement = when {
                // Block scalar indicators
                afterColon == "|" || afterColon == ">" || afterColon == "|-" || afterColon == ">-" -> {
                    parseBlockScalar(baseIndent, fold = afterColon.startsWith(">"), strip = afterColon.endsWith("-"))
                }
                // Inline value present
                afterColon.isNotEmpty() -> {
                    parseInlineValue(afterColon)
                }
                // Value on next line(s) — nested map, list, or multiline
                else -> {
                    skipBlanksAndComments()
                    if (pos < lines.size && currentIndent() > baseIndent) {
                        parseValue(currentIndent())
                    } else {
                        JsonNull
                    }
                }
            }
            entries[key] = value
        }
        return JsonObject(entries)
    }

    /**
     * Returns true when [parseMap] should stop consuming lines at the current position.
     * Extracted from the main loop to reduce jump count — preserves the original behaviour
     * exactly: end-of-input, dedent to parent, indent into child, list item, or a line
     * without a key colon all terminate the map.
     */
    private fun shouldStopParsingMap(baseIndent: Int): Boolean {
        if (pos >= lines.size) return true
        val indent = currentIndent()
        if (indent < baseIndent) return true
        if (indent > baseIndent) return true // belongs to parent
        val trimmed = lines[pos].trimStart()
        if (trimmed.startsWith("- ")) return true // must be a map entry, not a list item
        if (findKeyColonIndex(trimmed) < 0) return true
        return false
    }

    /**
     * Returns true when [parseList] should stop consuming lines at the current position.
     * Extracted from the main loop to reduce jump count — preserves the original behaviour
     * exactly: end-of-input, any indent mismatch, or a line that is not a list item all
     * terminate the list.
     */
    private fun shouldStopParsingList(baseIndent: Int): Boolean {
        if (pos >= lines.size) return true
        if (currentIndent() != baseIndent) return true
        val trimmed = lines[pos].trimStart()
        if (!trimmed.startsWith("- ") && trimmed != "-") return true
        return false
    }

    /**
     * Returns true when the continuation loop in [parseListItemMap] should stop at the
     * current position. Extracted from the main loop to reduce jump count — preserves the
     * original behaviour exactly: end-of-input, dedent below the content indent, next list
     * item, or a line without a key colon all terminate the continuation.
     */
    private fun shouldStopParsingListItemMap(contentIndent: Int): Boolean {
        if (pos >= lines.size) return true
        if (currentIndent() < contentIndent) return true
        val line = lines[pos].trimStart()
        if (line.startsWith("- ")) return true // next list item
        if (findKeyColonIndex(line) < 0) return true
        return false
    }

    private fun parseList(baseIndent: Int): JsonArray {
        val items = mutableListOf<JsonElement>()
        while (pos < lines.size) {
            skipBlanksAndComments()
            if (shouldStopParsingList(baseIndent)) break

            val trimmed = lines[pos].trimStart()
            val afterDash = if (trimmed == "-") "" else trimmed.substring(2).trim()
            pos++

            val item: JsonElement = when {
                afterDash.isEmpty() -> {
                    // Value on next line(s)
                    skipBlanksAndComments()
                    if (pos < lines.size && currentIndent() > baseIndent) {
                        parseValue(currentIndent())
                    } else {
                        JsonNull
                    }
                }
                afterDash.startsWith("{") || afterDash.startsWith("[") -> {
                    parseInlineValue(afterDash)
                }
                afterDash.contains(":") && !isQuotedScalar(afterDash) -> {
                    // Inline map entry after dash, e.g. "- key: value"
                    // Rewind and parse as nested map at dash+2 indent
                    pos--
                    val dashIndent = currentIndent()
                    // Replace "- " with "  " conceptually by parsing the content after dash
                    parseListItemMap(dashIndent)
                }
                else -> {
                    scalarToJsonPrimitive(afterDash.unquoteYaml())
                }
            }
            items.add(item)
        }
        return JsonArray(items)
    }

    /**
     * Parses a map that starts inline after a list dash: `- key: value`
     */
    private fun parseListItemMap(dashIndent: Int): JsonObject {
        val entries = mutableMapOf<String, JsonElement>()
        val contentIndent = dashIndent + 2 // content after "- "

        // First line: "- key: value"
        val firstLine = lines[pos]
        val trimmed = firstLine.trimStart()
        val afterDash = trimmed.substring(2).trim()
        val colonIdx = findKeyColonIndex(afterDash)
        if (colonIdx >= 0) {
            val key = afterDash.substring(0, colonIdx).trim().unquoteYaml()
            val afterColon = afterDash.substring(colonIdx + 1).trim()
            pos++
            val value = if (afterColon.isNotEmpty()) {
                parseInlineValue(afterColon)
            } else {
                skipBlanksAndComments()
                if (pos < lines.size && currentIndent() > dashIndent) {
                    parseValue(currentIndent())
                } else {
                    JsonNull
                }
            }
            entries[key] = value
        } else {
            pos++
        }

        // Continuation lines at contentIndent
        while (pos < lines.size) {
            skipBlanksAndComments()
            if (shouldStopParsingListItemMap(contentIndent)) break

            val indent = currentIndent()
            val line = lines[pos].trimStart()
            val ci = findKeyColonIndex(line)

            val key = line.substring(0, ci).trim().unquoteYaml()
            val afterColon = line.substring(ci + 1).trim()
            pos++
            val value = if (afterColon.isNotEmpty()) {
                parseInlineValue(afterColon)
            } else {
                skipBlanksAndComments()
                if (pos < lines.size && currentIndent() > indent) {
                    parseValue(currentIndent())
                } else {
                    JsonNull
                }
            }
            entries[key] = value
        }

        return JsonObject(entries)
    }

    private fun parseBlockScalar(baseIndent: Int, fold: Boolean, strip: Boolean): JsonPrimitive {
        val lines = mutableListOf<String>()
        val contentIndent = if (pos < this.lines.size) {
            // Detect indent from first content line
            skipBlanksAndComments()
            if (pos < this.lines.size) currentIndent() else baseIndent + 2
        } else {
            baseIndent + 2
        }

        while (pos < this.lines.size) {
            val line = this.lines[pos]
            if (line.isBlank()) {
                lines.add("")
                pos++
                continue
            }
            val indent = line.length - line.trimStart().length
            if (indent < contentIndent) break
            lines.add(line.substring(contentIndent))
            pos++
        }

        // Remove trailing blank lines if strip mode
        if (strip) {
            while (lines.isNotEmpty() && lines.last().isEmpty()) {
                lines.removeAt(lines.lastIndex)
            }
        }

        val text = if (fold) {
            lines.joinToString(" ").replace("  ", "\n")
        } else {
            lines.joinToString("\n")
        }

        return JsonPrimitive(if (strip) text else text.trimEnd('\n') + "\n")
    }

    private fun parseInlineValue(value: String): JsonElement {
        if (value.startsWith("{")) return parseInlineFlowMap(value)
        if (value.startsWith("[")) return parseInlineFlowList(value)
        return scalarToJsonPrimitive(value.unquoteYaml())
    }

    private fun parseInlineFlowMap(text: String): JsonObject {
        val inner = text.removeSurrounding("{", "}").trim()
        if (inner.isEmpty()) return JsonObject(emptyMap())
        val entries = mutableMapOf<String, JsonElement>()
        for (part in splitFlowItems(inner)) {
            val ci = part.indexOf(':')
            if (ci >= 0) {
                val key = part.substring(0, ci).trim().unquoteYaml()
                val v = part.substring(ci + 1).trim()
                entries[key] = parseInlineValue(v.unquoteYaml().let { if (it != v) it else v })
            }
        }
        return JsonObject(entries)
    }

    private fun parseInlineFlowList(text: String): JsonArray {
        val inner = text.removeSurrounding("[", "]").trim()
        if (inner.isEmpty()) return JsonArray(emptyList())
        return JsonArray(splitFlowItems(inner).map { parseInlineValue(it.trim()) })
    }

    private fun splitFlowItems(text: String): List<String> {
        val items = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        for (ch in text) {
            when {
                inQuote -> {
                    current.append(ch)
                    if (ch == quoteChar) inQuote = false
                }
                ch == '"' || ch == '\'' -> {
                    current.append(ch)
                    inQuote = true
                    quoteChar = ch
                }
                ch == '{' || ch == '[' -> {
                    depth++
                    current.append(ch)
                }
                ch == '}' || ch == ']' -> {
                    depth--
                    current.append(ch)
                }
                ch == ',' && depth == 0 -> {
                    items.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) items.add(current.toString())
        return items
    }

    private fun skipBlanksAndComments() {
        while (pos < lines.size) {
            val line = lines[pos]
            val trimmed = line.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                pos++
            } else if (trimmed.startsWith("---") || trimmed.startsWith("...")) {
                pos++ // skip document markers
            } else {
                break
            }
        }
    }

    private fun currentIndent(): Int {
        if (pos >= lines.size) return 0
        val line = lines[pos]
        return line.length - line.trimStart().length
    }

    /**
     * Finds the colon that separates key from value, ignoring colons inside quoted strings
     * and colons in URLs (e.g., "http://").
     */
    private fun findKeyColonIndex(text: String): Int {
        var inQuote = false
        var quoteChar = ' '
        for (i in text.indices) {
            val ch = text[i]
            when {
                inQuote -> {
                    if (ch == quoteChar) inQuote = false
                }
                ch == '"' || ch == '\'' -> {
                    inQuote = true
                    quoteChar = ch
                }
                ch == ':' -> {
                    // Key colon must be followed by space, end-of-string, or newline
                    if (i + 1 >= text.length || text[i + 1] == ' ') {
                        return i
                    }
                }
            }
        }
        return -1
    }

    private fun isQuotedScalar(text: String): Boolean {
        return (text.startsWith("\"") && text.endsWith("\"")) ||
            (text.startsWith("'") && text.endsWith("'"))
    }
}

private fun scalarToJsonPrimitive(content: String): JsonElement {
    // Null
    if (content == "null" || content == "~") return JsonNull

    // Boolean
    if (content.equals("true", ignoreCase = true) || content.equals("false", ignoreCase = true)) {
        return JsonPrimitive(content.toBoolean())
    }

    // Empty string treated as a string primitive
    if (content.isEmpty()) {
        return JsonPrimitive(content)
    }

    // Integer
    content.toLongOrNull()?.let { return JsonPrimitive(it) }

    // Floating point
    content.toDoubleOrNull()?.let { return JsonPrimitive(it) }

    // String
    return JsonPrimitive(content)
}

private fun String.unquoteYaml(): String {
    if (length >= 2) {
        if ((startsWith("\"") && endsWith("\"")) || (startsWith("'") && endsWith("'"))) {
            return substring(1, length - 1)
        }
    }
    return this
}
