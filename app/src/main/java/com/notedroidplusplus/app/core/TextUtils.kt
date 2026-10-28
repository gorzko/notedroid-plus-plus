package com.notedroidplusplus.app.core

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Text utilities: encoding detection, line-ending conversion, casing, indent handling. */
object TextUtils {

    val ENCODINGS: List<Charset> = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("UTF-16LE"),
        Charset.forName("UTF-16BE"),
        Charset.forName("UTF-16"),
        Charset.forName("US-ASCII"),
        Charset.forName("ISO-8859-1"),
        Charset.forName("windows-1252"),
    )

    fun names(): List<String> = ENCODINGS.map { it.name() }

    /** Character-set families mirroring the Notepad++ Encoding > Character Sets submenus. */
    val CHARSET_FAMILIES: List<Pair<String, List<String>>> = listOf(
        "Arabic" to listOf("ISO-8859-6", "windows-1256"),
        "Baltic" to listOf("ISO-8859-4", "windows-1257"),
        "Celtic" to listOf("ISO-8859-14"),
        "Central European" to listOf("ISO-8859-2", "windows-1250", "IBM852"),
        "Chinese" to listOf("Big5", "GB2312", "GBK", "GB18030", "EUC-CN"),
        "Croatian" to listOf("ISO-8859-13"),
        "Cyrillic" to listOf("ISO-8859-5", "KOI8-R", "KOI8-U", "IBM855", "IBM866", "windows-1251"),
        "Greek" to listOf("ISO-8859-7", "windows-1253"),
        "Hebrew" to listOf("ISO-8859-8", "windows-1255"),
        "Japanese" to listOf("Shift_JIS", "EUC-JP", "ISO-2022-JP"),
        "Korean" to listOf("EUC-KR", "ISO-2022-KR", "windows-949"),
        "Latin" to listOf("ISO-8859-15", "ISO-8859-1", "ISO-8859-3", "ISO-8859-10"),
        "Nordic" to listOf("ISO-8859-10"),
        "Romanian" to listOf("ISO-8859-16"),
        "South European" to listOf("ISO-8859-3"),
        "Thai" to listOf("ISO-8859-11", "TIS-620", "windows-874"),
        "Turkish" to listOf("ISO-8859-9", "windows-1254"),
        "Vietnamese" to listOf("windows-1258"),
        "Western European" to listOf("ISO-8859-1", "windows-1252", "MacRoman")
    )

    /** All charsets resolvable on this runtime (base list + family members). */
    fun allCharsets(): List<Charset> = (ENCODINGS.map { it.name() } + CHARSET_FAMILIES.flatMap { it.second })
        .mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }
        .distinctBy { it.name() }

    /** True when the charset exists on this runtime (menu shows it only then). */
    fun isCharsetAvailable(name: String): Boolean =
        runCatching { Charset.forName(name) }.isSuccess

    /**
     * Notepad++ Convert-to semantics for a buffer held as text: the glyphs stay
     * unchanged and the save encoding switches to [to]. Fails (null) when the
     * text cannot be represented in [to] without loss.
     */
    fun canRepresent(text: String, to: Charset): Boolean {
        if (to == StandardCharsets.US_ASCII || to.name() == "US-ASCII") {
            return text.all { it.code in 0..127 }
        }
        val encoder = to.newEncoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return try {
            encoder.encode(java.nio.CharBuffer.wrap(text))
            true
        } catch (e: java.nio.charset.CharacterCodingException) {
            false
        }
    }

    /**
     * Detects a charset declared in an XML declaration or HTML meta tag within a
     * bounded 4 KB prefix, mirroring Notepad++ prolog-first detection.
     */
    fun detectFromProlog(text: String): Charset? {
        val head = text.take(4096)
        val trimmed = head.trimStart()
        val declRe = "encoding=\"([A-Za-z0-9._:-]+)\"".toRegex()
        val charsetRe = "charset=[\"']?([A-Za-z0-9._:-]+)".toRegex(RegexOption.IGNORE_CASE)
        val name = when {
            trimmed.startsWith("<?xml") -> declRe.find(head)?.groupValues?.get(1)
            head.contains("<meta", ignoreCase = true) ->
                declRe.find(head)?.groupValues?.get(1) ?: charsetRe.find(head)?.groupValues?.get(1)
            else -> null
        } ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    fun detect(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charset.forName("UTF-16LE")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charset.forName("UTF-16BE")
        }
        if (bytes.size >= 4) {
            val nullsEven = (0 until bytes.size step 2).count { bytes[it] == 0.toByte() }
            val nullsOdd = (1 until bytes.size step 2).count { bytes[it] == 0.toByte() }
            if (nullsEven > 1 && nullsEven > nullsOdd) return Charset.forName("UTF-16BE")
            if (nullsOdd > 1 && nullsOdd > nullsEven) return Charset.forName("UTF-16LE")
        }
        return StandardCharsets.UTF_8
    }

    fun detectLineEnding(text: String): String = when {
        text.contains("\r\n") -> "\r\n"
        text.contains('\n') -> "\n"
        text.contains('\r') -> "\r"
        else -> "\n"
    }

    fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    fun convertLineEndings(text: String, eol: String): String =
        normalize(text).replace("\n", eol)




    fun lineAt(text: String, lineIndex: Int): String {
        var idx = 0
        var line = 0
        while (line < lineIndex) {
            idx = text.indexOf('\n', idx)
            if (idx < 0) return ""
            idx++
            line++
        }
        val end = text.indexOf('\n', idx)
        return if (end < 0) text.substring(idx) else text.substring(idx, end)
    }

    fun lineIndexToOffset(text: String, lineIndex: Int): Int {
        var idx = 0
        repeat(lineIndex) {
            val next = text.indexOf('\n', idx)
            if (next < 0) return text.length
            idx = next + 1
        }
        return idx
    }

    fun toUpper(text: String, start: Int, end: Int): String =
        text.substring(0, start) + text.substring(start, end).uppercase() + text.substring(end)

    fun toLower(text: String, start: Int, end: Int): String =
        text.substring(0, start) + text.substring(start, end).lowercase() + text.substring(end)

    fun titleCase(text: String, start: Int, end: Int): String {
        val source = text.substring(start, end)
        val sb = StringBuilder(source.length)
        var capitalizeNext = true
        for (ch in source.lowercase()) {
            if (ch.isLetter()) {
                sb.append(if (capitalizeNext) ch.uppercaseChar() else ch)
                capitalizeNext = false
            } else {
                sb.append(ch)
                capitalizeNext = true
            }
        }
        return text.substring(0, start) + sb + text.substring(end)
    }

    fun toggleCase(text: String, start: Int, end: Int): String {
        val seg = StringBuilder()
        for (ch in text.substring(start, end)) {
            seg.append(if (ch.isUpperCase()) ch.lowercaseChar() else ch.uppercaseChar())
        }
        return text.substring(0, start) + seg + text.substring(end)
    }

    fun trimTrailingWhitespace(text: String): String =
        normalize(text).split("\n").joinToString("\n") { it.trimEnd() }

    // ---- Notepad++ Edit > Line Operations ----

    /** Zero-based inclusive first/last line index covered by a selection (empty selection = current line). */
    fun lineSpanForSelection(text: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
        val s = selStart.coerceIn(0, text.length)
        val e = selEnd.coerceIn(0, text.length)
        val first = text.substring(0, s).count { it == '\n' }
        val last = text.substring(0, e).count { it == '\n' }
        return first to maxOf(first, last)
    }

    private fun lineRange(text: String, first: Int, last: Int): List<String> {
        val lines = normalize(text).split("\n")
        val f = first.coerceIn(0, lines.size - 1)
        val l = last.coerceIn(f, lines.size - 1)
        return lines.subList(f, l + 1)
    }

    private fun rebuild(text: String, first: Int, last: Int, eol: String, replacement: List<String>): String {
        val lines = normalize(text).split("\n")
        val f = first.coerceIn(0, lines.size - 1)
        val l = last.coerceIn(f, lines.size - 1)
        return (lines.subList(0, f) + replacement + lines.subList(l + 1, lines.size)).joinToString(eol)
    }

    fun duplicateLines(text: String, first: Int, last: Int, eol: String): String {
        val range = lineRange(text, first, last)
        return rebuild(text, first, last, eol, range + range)
    }

    /** Notepad++ Split Lines: insert an EOL at each space in the selected span (word boundaries). */
    fun splitLines(text: String, selStart: Int, selEnd: Int, eol: String): String {
        if (selEnd <= selStart) return text
        val seg = text.substring(selStart, selEnd)
        val replaced = normalize(seg).replace("\n", eol).replace(" ", eol)
        return text.substring(0, selStart) + replaced + text.substring(selEnd)
    }

    /** Notepad++ Join Lines: requires a multi-line selection; EOLs become a single space. */
    fun joinLines(text: String, selStart: Int, selEnd: Int): String {
        if (selEnd <= selStart) return text
        val seg = text.substring(selStart, selEnd)
        if (!seg.contains('\n') && !seg.contains('\r')) return text
        val replaced = normalize(seg).replace('\n', ' ')
        return text.substring(0, selStart) + replaced + text.substring(selEnd)
    }

    fun removeEmptyLines(text: String, first: Int, last: Int, eol: String, includingBlank: Boolean): String {
        val kept = lineRange(text, first, last).filterNot { if (includingBlank) it.isBlank() else it.isEmpty() }
        return rebuild(text, first, last, eol, kept)
    }

    fun moveLineUp(text: String, line: Int, eol: String): String {
        if (line <= 0) return text
        val upper = lineRange(text, line - 1, line - 1)
        val moved = lineRange(text, line, line)
        return rebuild(text, line - 1, line, eol, moved + upper)
    }

    fun moveLineDown(text: String, line: Int, eol: String): String {
        val lines = normalize(text).split("\n")
        if (line < 0 || line >= lines.size - 1) return text
        val moved = lineRange(text, line, line)
        val lower = lineRange(text, line + 1, line + 1)
        return rebuild(text, line, line + 1, eol, lower + moved)
    }

    fun reverseLines(text: String, first: Int, last: Int, eol: String): String =
        rebuild(text, first, last, eol, lineRange(text, first, last).reversed())

    fun randomizeLines(text: String, first: Int, last: Int, eol: String, random: java.util.Random): String =
        rebuild(text, first, last, eol, lineRange(text, first, last).shuffled(random))

    fun sortLines(text: String, first: Int, last: Int, eol: String, descending: Boolean, numeric: Boolean, ignoreCase: Boolean): String {
        val sorted = if (numeric) {
            lineRange(text, first, last).sortedBy { it.trim().toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
        } else {
            lineRange(text, first, last).let { lines ->
                if (ignoreCase) lines.sortedBy { it.lowercase() } else lines.sorted()
            }
        }
        return rebuild(text, first, last, eol, if (descending) sorted.reversed() else sorted)
    }

    /** Keeps the first instance of each line; consecutiveOnly dedupes adjacent repeats. */
    fun removeDuplicateLines(text: String, first: Int, last: Int, eol: String, consecutiveOnly: Boolean): String {
        val lines = lineRange(text, first, last)
        val kept = if (consecutiveOnly) {
            lines.fold(mutableListOf<String>()) { acc, l -> if (acc.isNotEmpty() && acc.last() == l) acc else { acc.add(l); acc } }
        } else {
            val seen = mutableSetOf<String>()
            lines.filter { seen.add(it) }
        }
        return rebuild(text, first, last, eol, kept)
    }

    // ---- Notepad++ Edit > Blank Operations ----

    private fun perLine(text: String, eol: String, transform: (String) -> String): String =
        normalize(text).split("\n").joinToString(eol) { transform(it) }

    fun trimLeadingWhitespace(text: String, eol: String): String = perLine(text, eol) { it.trimStart() }

    fun trimLeadingAndTrailing(text: String, eol: String): String = perLine(text, eol) { it.trim() }

    fun eolToSpace(text: String): String = normalize(text).replace('\n', ' ')

    fun tabToSpace(text: String, eol: String, tabWidth: Int): String {
        require(tabWidth >= 1)
        return perLine(text, eol) { line ->
            buildString {
                for (ch in line) {
                    if (ch == '\t') repeat(tabWidth) { append(' ') } else append(ch)
                }
            }
        }
    }

    /** Replaces leading runs of spaces (of tabWidth size) with tabs; all=false keeps interior spaces. */
    fun spaceToTab(text: String, eol: String, tabWidth: Int, all: Boolean): String {
        require(tabWidth >= 1)
        return perLine(text, eol) { line ->
            val indentEnd = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
            val indent = line.substring(0, indentEnd)
            val body = line.substring(indentEnd)
            val tabbedIndent = buildString {
                var spaces = 0
                for (ch in indent) {
                    if (ch == ' ') {
                        spaces++
                        if (spaces == tabWidth) { append('\t'); spaces = 0 }
                    } else {
                        repeat(spaces) { append(' ') }
                        spaces = 0
                        append(ch)
                    }
                }
                repeat(spaces) { append(' ') }
            }
            if (all) tabbedIndent + buildString {
                var spaces = 0
                for (ch in body) {
                    if (ch == ' ') {
                        spaces++
                        if (spaces == tabWidth) { append('\t'); spaces = 0 }
                    } else {
                        repeat(spaces) { append(' ') }
                        spaces = 0
                        append(ch)
                    }
                }
                repeat(spaces) { append(' ') }
            } else tabbedIndent + body
        }
    }

    // ---- Notepad++ Edit > Convert Case to (extended) ----

    /** Capitalizes letters that follow non-letters; blend=true keeps other letters' case. */
    fun properCase(text: String, start: Int, end: Int, blend: Boolean): String {
        val source = text.substring(start, end)
        val base = if (blend) source else source.lowercase()
        val sb = StringBuilder(source.length)
        var capitalizeNext = true
        for (ch in base) {
            if (ch.isLetter()) {
                sb.append(if (capitalizeNext) ch.uppercaseChar() else ch)
                capitalizeNext = false
            } else {
                sb.append(ch)
                capitalizeNext = true
            }
        }
        return text.substring(0, start) + sb + text.substring(end)
    }

    /** Capitalizes the first letter of each sentence; blend=true keeps other letters' case. */
    fun sentenceCase(text: String, start: Int, end: Int, blend: Boolean): String {
        val source = text.substring(start, end)
        val base = if (blend) source else source.lowercase()
        val sb = StringBuilder(source.length)
        var capitalizeNext = true
        for (ch in base) {
            if (ch.isLetter()) {
                sb.append(if (capitalizeNext) ch.uppercaseChar() else ch)
                if (capitalizeNext) capitalizeNext = false
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?') capitalizeNext = true
            }
        }
        return text.substring(0, start) + sb + text.substring(end)
    }

    /** iNVERT cASE: swaps the case of letters only (leaves non-letters untouched). */
    fun invertCase(text: String, start: Int, end: Int): String {
        val seg = StringBuilder()
        for (ch in text.substring(start, end)) {
            seg.append(
                when {
                    ch.isUpperCase() -> ch.lowercaseChar()
                    ch.isLowerCase() -> ch.uppercaseChar()
                    else -> ch
                }
            )
        }
        return text.substring(0, start) + seg + text.substring(end)
    }

    /** ranDOm cASE: randomizes the case of each letter. */
    fun randomCase(text: String, start: Int, end: Int, random: java.util.Random): String {
        val seg = StringBuilder()
        for (ch in text.substring(start, end)) {
            seg.append(if (ch.isLetter() && random.nextBoolean()) ch.uppercaseChar() else ch.lowercaseChar())
        }
        return text.substring(0, start) + seg + text.substring(end)
    }

    // ---- Notepad++ Edit > Indent ----

    /** Inserts indentUnit at the start of each line in the span. */
    fun indentIncrease(text: String, first: Int, last: Int, eol: String, indentUnit: String): String {
        if (indentUnit.isEmpty()) return text
        return rebuild(text, first, last, eol, lineRange(text, first, last).map { indentUnit + it })
    }

    /** Removes one indentUnit (or up to its space-equivalent) from each line in the span. */
    fun indentDecrease(text: String, first: Int, last: Int, eol: String, indentUnit: String): String {
        val spaceEquivalent = if (indentUnit == "\t") 4 else indentUnit.length
        return rebuild(text, first, last, eol, lineRange(text, first, last).map { line ->
            when {
                line.startsWith(indentUnit) -> line.removePrefix(indentUnit)
                line.startsWith(" ") -> line.dropWhile { it == ' ' }.let { it ->
                    val leading = line.length - it.length
                    line.drop(minOf(leading, spaceEquivalent))
                }
                else -> line
            }
        })
    }

    // ---- Notepad++ Edit > Insert > Date Time ----

    const val DATETIME_SHORT = "h:mm a M/d/yyyy"
    const val DATETIME_LONG = "h:mm a EEEE, MMMM d, yyyy"

    fun formatDateTime(pattern: String, millis: Long): String =
        try {
            java.time.format.DateTimeFormatter.ofPattern(pattern)
                .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))
        } catch (e: Exception) {
            ""
        }

    fun countMatches(text: String, needle: String, ignoreCase: Boolean, wholeWord: Boolean): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            idx = text.indexOf(needle, idx, ignoreCase = ignoreCase)
            if (idx < 0) break
            if (wholeWord && !isWholeWord(text, idx, needle.length)) {
                idx += 1
                continue
            }
            count++
            idx += needle.length
        }
        return count
    }

    fun isWholeWord(text: String, start: Int, length: Int): Boolean {
        fun wordChar(c: Char) = c.isLetterOrDigit() || c == '_'
        val before = start > 0 && wordChar(text[start - 1])
        val after = start + length < text.length && wordChar(text[start + length])
        return !before && !after
    }
}
