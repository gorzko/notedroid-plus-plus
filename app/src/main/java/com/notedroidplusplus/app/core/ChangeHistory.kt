package com.notedroidplusplus.app.core

/**
 * Per-line change history, mirroring Notepad++'s Change History margin:
 * MODIFIED_UNSAVED (orange), MODIFIED_SAVED (green), REVERT_MODIFIED
 * (pale green), REVERT_ORIGINAL (pale blue).
 *
 * Pure Kotlin; line numbers are zero-based. The map is keyed by line index
 * and updated from edit events, save events, and undo/redo reconciliation
 * against the loaded (or last saved) baseline.
 */
class ChangeHistory {

    enum class LineState {
        NONE,
        MODIFIED_UNSAVED,
        MODIFIED_SAVED,
        REVERT_MODIFIED,
        REVERT_ORIGINAL
    }

    private val states = mutableMapOf<Int, LineState>()

    /** Line content at its last save; REVERT_MODIFIED reconciliation source. */
    private val savedContent = mutableMapOf<Int, String>()

    fun stateAt(line: Int): LineState = states[line] ?: LineState.NONE

    fun markedLines(): Map<Int, LineState> = states.toMap()

    fun isEmpty(): Boolean = states.isEmpty()

    fun clear() {
        states.clear()
        savedContent.clear()
    }

    private fun lineOfOffset(text: String, offset: Int): Int {
        val o = offset.coerceIn(0, text.length)
        var count = 0
        for (i in 0 until o) if (text[i] == '\n') count++
        return count
    }

    private fun lineCount(text: String): Int = text.count { it == '\n' } + if (text.isEmpty()) 0 else 1

    private fun lineContent(text: String, line: Int): String {
        var idx = 0
        var l = 0
        while (l < line) {
            val next = text.indexOf('\n', idx)
            if (next < 0) return ""
            idx = next + 1
            l++
        }
        val end = text.indexOf('\n', idx)
        return if (end < 0) text.substring(idx) else text.substring(idx, end)
    }

    /**
     * Records an edit that transformed the buffer into [afterText].
     * [start] is the edit offset, [deletedLen] the removed run, [insertedLen]
     * the inserted run (as in TextWatcher callbacks). All lines the edit
     * touches (in the new coordinates) become MODIFIED_UNSAVED.
     */
    fun recordEdit(afterText: String, start: Int, deletedLen: Int, insertedLen: Int) {
        if (afterText.isEmpty() && insertedLen == 0) return
        val s = start.coerceIn(0, afterText.length)
        val first = lineOfOffset(afterText, s)
        // a zero-length insert still touches the line at the caret; an insert
        // spanning newlines marks through its last line, but an offset sitting
        // exactly on a newline belongs to the line it ends, not the next one
        val endOffset = if (insertedLen == 0) s else (s + insertedLen - 1)
        val last = lineOfOffset(afterText, endOffset.coerceIn(0, maxOf(0, afterText.length - 1)))
        val spanEnd = maxOf(first, last)
        for (line in first..spanEnd) states[line] = LineState.MODIFIED_UNSAVED
    }

    /** Save event: every unsaved modification becomes saved; remember the saved content. */
    fun recordSave(text: String = "") {
        if (text.isNotEmpty()) {
            for (line in states.keys) {
                if (states[line] == LineState.MODIFIED_UNSAVED || states[line] == LineState.MODIFIED_SAVED) {
                    savedContent[line] = lineContent(text, line)
                }
            }
        }
        states.replaceAll { _, v -> if (v == LineState.MODIFIED_UNSAVED) LineState.MODIFIED_SAVED else v }
    }

    /**
     * Undo/redo reconciliation against the baseline (loaded/last saved text):
     * a line matching the baseline is REVERT_ORIGINAL; a line that was saved
     * but no longer matches is REVERT_MODIFIED; anything else modified stays
     * MODIFIED_UNSAVED. Unmarked lines are untouched (Notepad++ only colors
     * lines with history).
     */
    fun reconcile(text: String, baseline: String) {
        val totalLines = maxOf(lineCount(text), lineCount(baseline))
        for (line in states.keys.toList()) {
            if (line >= totalLines) { states.remove(line); continue }
            val current = states[line] ?: continue
            val matchesBaseline = line < lineCount(baseline) && lineContent(text, line) == lineContent(baseline, line)
            val matchesSaved = savedContent[line] != null && lineContent(text, line) == savedContent[line]
            states[line] = when {
                matchesBaseline -> LineState.REVERT_ORIGINAL
                matchesSaved -> LineState.REVERT_MODIFIED
                else -> LineState.MODIFIED_UNSAVED
            }
        }
    }

    /** Reload/external replace: history resets (Notepad++ semantics). */
    fun reset() = clear()
}
