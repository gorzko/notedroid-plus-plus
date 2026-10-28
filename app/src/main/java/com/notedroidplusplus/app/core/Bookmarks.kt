package com.notedroidplusplus.app.core

/**
 * Bookmark set (Notepad++ Search > Bookmark parity): per-tab line numbers.
 * Pure logic; the tab owns the set and the editor draws the gutter markers.
 */
object Bookmarks {

    /** Toggles [line] in [set]; returns the new set. */
    fun toggle(set: Set<Int>, line: Int): Set<Int> =
        if (line in set) set - line else set + line

    fun invert(set: Set<Int>, totalLines: Int): Set<Int> =
        (0 until totalLines).filter { it !in set }.toSet()

    /** Next bookmark strictly after [from] with wrap-around; -1 when none. */
    fun next(set: Set<Int>, from: Int): Int {
        if (set.isEmpty()) return -1
        val sorted = set.sorted()
        return sorted.firstOrNull { it > from } ?: sorted.first()
    }

    /** Previous bookmark strictly before [from] with wrap-around; -1 when none. */
    fun prev(set: Set<Int>, from: Int): Int {
        if (set.isEmpty()) return -1
        val sorted = set.sorted()
        return sorted.lastOrNull { it < from } ?: sorted.last()
    }

    /** Concatenation of the bookmarked lines' text, in document order. */
    fun collectLines(text: String, eol: String, set: Set<Int>): String =
        set.sorted().map { line -> TextUtils.lineAt(text, line) }
            .joinToString(eol)

    /**
     * Removes all bookmarked lines AND their terminating EOLs. A bookmark on
     * the phantom line after a trailing EOL removes just that terminator.
     */
    fun removeLines(text: String, eol: String, set: Set<Int>): String {
        if (set.isEmpty()) return text
        val endsWithEol = text.endsWith(eol)
        val sb = StringBuilder()
        var lineStart = 0
        var line = 0
        var i = 0
        while (i <= text.length) {
            val atEol = i == text.length || text.startsWith(eol, i)
            if (atEol) {
                val hasEol = i != text.length
                if (line !in set) {
                    sb.append(text, lineStart, i)
                    if (hasEol) sb.append(eol)
                }
                if (!hasEol) break
                lineStart = i + eol.length
                line++
                i = lineStart
            } else i++
        }
        // A kept line's EOL must not dangle at the end of the result unless the
        // original ended with an EOL AND the phantom line after it was kept.
        if (endsWithEol) {
            val phantomLine = text.split(eol).size - 1
            if (phantomLine in set && sb.endsWith(eol)) sb.setLength(sb.length - eol.length)
        } else if (sb.endsWith(eol)) {
            sb.setLength(sb.length - eol.length)
        }
        return sb.toString()
    }
}
