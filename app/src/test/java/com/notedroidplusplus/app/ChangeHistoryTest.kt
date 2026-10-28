package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.ChangeHistory
import com.notedroidplusplus.app.core.ChangeHistory.LineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeHistoryTest {

    @Test
    fun editMarksLineModifiedUnsaved() {
        val h = ChangeHistory()
        val base = "alpha\nbeta\ngamma"
        h.recordEdit("alpha\nbeta!\ngamma", 6 + 5, 0, 1)
        assertEquals(LineState.MODIFIED_UNSAVED, h.stateAt(1))
        assertEquals(LineState.NONE, h.stateAt(0))
        assertEquals(LineState.NONE, h.stateAt(2))
    }

    @Test
    fun saveTurnsUnsavedIntoSaved() {
        val h = ChangeHistory()
        h.recordEdit("alpha\nbeta!\ngamma", 11, 0, 1)
        h.recordSave()
        assertEquals(LineState.MODIFIED_SAVED, h.stateAt(1))
    }

    @Test
    fun undoBackToBaselineIsRevertOriginal() {
        val h = ChangeHistory()
        val base = "alpha\nbeta\ngamma"
        h.recordEdit("alpha\nbeta!\ngamma", 11, 0, 1)
        h.recordSave()
        // undo restores the line to its baseline content
        h.reconcile(base, base)
        assertEquals(LineState.REVERT_ORIGINAL, h.stateAt(1))
    }

    @Test
    fun undoToDifferentContentKeepsModifiedUnsaved() {
        val h = ChangeHistory()
        val base = "alpha\nbeta\ngamma"
        h.recordEdit("alpha\nbeta!\ngamma", 11, 0, 1)
        // undo brings back yet another content (still not the baseline)
        h.reconcile("alpha\nbeta?\ngamma", base)
        assertEquals(LineState.MODIFIED_UNSAVED, h.stateAt(1))
    }

    @Test
    fun savedThenEditedAgainIsRevertModifiedAfterUndo() {
        val h = ChangeHistory()
        val base = "alpha\nbeta\ngamma"
        h.recordEdit("alpha\nBETA\ngamma", 6, 3, 4)
        h.recordSave()
        // second edit round then undo: line differs from baseline but was saved before
        h.recordEdit("alpha\nBETA2\ngamma", 6, 4, 5)
        h.reconcile("alpha\nBETA2\ngamma", base)
        assertEquals(LineState.REVERT_MODIFIED, h.stateAt(1))
    }

    @Test
    fun multiLineEditMarksSpan() {
        val h = ChangeHistory()
        h.recordEdit("alpha\nbeta\ngamma\ndelta", 5, 1, 1)
        assertEquals(LineState.MODIFIED_UNSAVED, h.stateAt(0))
        assertEquals(LineState.MODIFIED_UNSAVED, h.stateAt(1))
        assertEquals(LineState.NONE, h.stateAt(2))
    }

    @Test
    fun reloadResetsHistory() {
        val h = ChangeHistory()
        h.recordEdit("alpha\nbeta!\ngamma", 11, 0, 1)
        h.reset()
        assertTrue(h.isEmpty())
        assertEquals(LineState.NONE, h.stateAt(1))
    }

    @Test
    fun shrinkRemovesOutOfRangeLines() {
        val h = ChangeHistory()
        h.recordEdit("alpha\nbeta\ngamma", 12, 0, 1) // line 2 exists
        h.reconcile("alpha\nbeta", "alpha\nbeta")      // document shrinks
        assertEquals(LineState.NONE, h.stateAt(2))
    }
}
