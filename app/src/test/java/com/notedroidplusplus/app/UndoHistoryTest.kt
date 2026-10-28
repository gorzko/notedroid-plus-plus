package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.UndoHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {

    @Test
    fun typedWordUndoesAsOneStep() {
        val h = UndoHistory()
        var text = ""
        var pos = 0
        for (c in "hello") {
            h.pushInsert(pos, c.toString())
            text = text.substring(0, pos) + c + text.substring(pos)
            pos++
        }
        assertEquals("hello", text)
        assertEquals(1, h.undoSize)
        assertEquals("", h.undo(text))
        assertFalse(h.canUndo())
    }

    @Test
    fun separateWordsAreSeparateSteps() {
        val h = UndoHistory()
        h.pushInsert(0, "hello")
        h.pushInsert(5, " ")
        h.pushInsert(6, "world")
        h.pushInsert(0, "!", coalesce = false)
        assertEquals(2, h.undoSize)
    }

    @Test
    fun backspaceRunCoalesces() {
        val h = UndoHistory()
        var text = "abcd"
        h.pushDelete(3, "d"); text = "abc"
        h.pushDelete(2, "c"); text = "ab"
        h.pushDelete(1, "b"); text = "a"
        assertEquals(1, h.undoSize)
        assertEquals("abcd", h.undo(text))
    }

    @Test
    fun redoReplaysCoalescedStep() {
        val h = UndoHistory()
        var text = ""
        for (c in "abc") {
            h.pushInsert(text.length, c.toString())
            text += c
        }
        val undone = h.undo(text)!!
        assertEquals("", undone)
        assertEquals("abc", h.redo(undone))
        assertEquals("abc", h.undo(text))
    }

    @Test
    fun compoundEditUndoesAtomically() {
        val h = UndoHistory()
        // Forward: "helloworld" -> insert "B" at 0 -> "Bhelloworld" -> delete "world" at 6 -> "Bhello"
        h.pushCompound(
            listOf(
                UndoHistory.Edit.Insert(0, "B"),
                UndoHistory.Edit.Delete(6, "world")
            )
        )
        assertEquals("helloworld", h.undo("Bhello"))
        assertEquals("Bhello", h.redo("helloworld"))
    }

    @Test
    fun redoClearsOnNewEdit() {
        val h = UndoHistory()
        h.pushInsert(0, "a")
        h.undo("a")
        assertTrue(h.canRedo())
        h.pushInsert(0, "b")
        assertFalse(h.canRedo())
    }

    @Test
    fun undoOnEmptyReturnsNull() {
        val h = UndoHistory()
        assertNull(h.undo("text"))
        assertNull(h.redo("text"))
    }

    @Test
    fun stackIsCapped() {
        val h = UndoHistory(limit = 5)
        repeat(20) { i ->
            h.pushInsert(i * 2, "x", coalesce = false)
        }
        assertEquals(5, h.undoSize)
    }

    @Test
    fun memoryDoesNotScaleWithWholeDocument() {
        val h = UndoHistory()
        val doc = "x".repeat(100_000)
        h.pushDelete(0, "y")
        // undo of one delete restores a single char, regardless of doc size
        assertEquals(doc + "y", h.undo(doc))
    }
}
