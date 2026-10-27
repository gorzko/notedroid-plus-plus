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

    fun detectIndentation(text: String): String {
        val lines = normalize(text).split("\n")
        val tabs = lines.count { it.startsWith("\t") }
        val spaces = lines.count { it.startsWith(" ") }
        return if (tabs > spaces) "\t" else "    "
    }

    fun lineStartOf(text: String, pos: Int): Int {
        var i = pos - 1
        while (i >= 0 && text[i] != '\n') i--
        return i + 1
    }

    fun lineEndOf(text: String, pos: Int): Int {
        var i = pos
        while (i < text.length && text[i] != '\n') i++
        return i
    }

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
