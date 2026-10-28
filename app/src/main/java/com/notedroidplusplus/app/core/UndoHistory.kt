package com.notedroidplusplus.app.core

/**
 * Edit-based undo/redo over insert/delete ranges (Notepad++-style granularity),
 * replacing whole-document snapshots so memory does not scale with text
 * length * stack size.
 *
 * Single-character insertions are coalesced when they continue the previous
 * edit (typing a word becomes one step); deletions that extend the previous
 * deletion (backspace runs) also coalesce. Any other edit starts a new step.
 */
class UndoHistory(private val limit: Int = 200) {

    sealed class Edit {
        data class Insert(val start: Int, val text: String) : Edit()
        data class Delete(val start: Int, val text: String) : Edit()
        data class Compound(val edits: List<Edit>) : Edit()
    }

    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()

    val undoSize: Int get() = undo.size
    val redoSize: Int get() = redo.size

    /** Record an insertion that transformed `before` into the text at [start]. */
    fun pushInsert(start: Int, text: String, coalesce: Boolean = true) {
        clearRedo()
        val last = undo.lastOrNull() as? Edit.Insert
        if (coalesce && last != null && start == last.start + last.text.length) {
            undo.removeLast()
            undo.addLast(Edit.Insert(last.start, last.text + text))
            return
        }
        addUndo(Edit.Insert(start, text))
    }

    /** Record a deletion of [text] that used to start at [start]. */
    fun pushDelete(start: Int, text: String, coalesce: Boolean = true) {
        clearRedo()
        val last = undo.lastOrNull() as? Edit.Delete
        if (coalesce && last != null && start + text.length == last.start) {
            undo.removeLast()
            undo.addLast(Edit.Delete(start, text + last.text))
            return
        }
        addUndo(Edit.Delete(start, text))
    }

    /** Record a programmatic multi-part change as one undo step. */
    fun pushCompound(edits: List<Edit>) {
        clearRedo()
        addUndo(Edit.Compound(edits))
    }

    fun canUndo(): Boolean = undo.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()

    /** Apply the inverse of the newest step to [text]; returns the new text. */
    fun undo(text: String): String? {
        val e = undo.removeLastOrNull() ?: return null
        val result = apply(text, e, inverse = true)
        redo.addLast(e)
        return result
    }

    /** Re-apply the newest redo step to [text]; returns the new text. */
    fun redo(text: String): String? {
        val e = redo.removeLastOrNull() ?: return null
        val result = apply(text, e, inverse = false)
        undo.addLast(e)
        return result
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    private fun addUndo(e: Edit) {
        undo.addLast(e)
        while (undo.size > limit) undo.removeFirst()
    }

    private fun clearRedo() {
        redo.clear()
    }

    private fun apply(text: String, e: Edit, inverse: Boolean): String {
        val sb = StringBuilder(text)
        if (inverse) invert(sb, e) else forward(sb, e)
        return sb.toString()
    }

    private fun forward(sb: StringBuilder, e: Edit) {
        when (e) {
            is Edit.Insert -> sb.insert(e.start.coerceIn(0, sb.length), e.text)
            is Edit.Delete -> {
                val from = e.start.coerceIn(0, sb.length)
                sb.delete(from, (from + e.text.length).coerceAtMost(sb.length))
            }
            is Edit.Compound -> e.edits.forEach { forward(sb, it) }
        }
    }

    private fun invert(sb: StringBuilder, e: Edit) {
        when (e) {
            is Edit.Insert -> {
                val from = e.start.coerceIn(0, sb.length)
                sb.delete(from, (from + e.text.length).coerceAtMost(sb.length))
            }
            is Edit.Delete -> sb.insert(e.start.coerceIn(0, sb.length), e.text)
            is Edit.Compound -> e.edits.reversed().forEach { invert(sb, it) }
        }
    }
}
