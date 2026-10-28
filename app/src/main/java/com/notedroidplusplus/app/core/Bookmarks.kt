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
        set.sorted().mapNotNull { line -> TextUtils.lineAt(text, line).takeIf { it.isNotEmpty() || true } }
            .joinToString(eol)

    /** Removes all bookmarked lines from the text. */
    fun removeLines(text: String, eol: String, set: Set<Int>): String {
        if (set.isEmpty()) return text
        val keep = mutableListOf<String>()
        val lines = text.split(eol)
        lines.forEachIndexed { i, l -> if (i !in set) keep.add(l) }
        return keep.joinToString(eol)
    }
}
