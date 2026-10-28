package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.MacroRecorder
import com.notedroidplusplus.app.core.MacroRecorder.Action
import com.notedroidplusplus.app.core.MacroRecorder.StringBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroRecorderTest {

    @Test
    fun insert_and_delete_replay_reproduces_typing() {
        val buf = StringBuffer("hello world")
        val actions = listOf(
            Action.Insert(5, ","),
            Action.Insert(6, " cruel"),
            Action.Delete(0, 6)
        )
        assertEquals(3, MacroRecorder.replay(buf, actions))
        assertEquals(" cruel world", buf.text)
    }

    @Test
    fun replace_all_literal_and_regex() {
        val buf = StringBuffer("cat bat CAT")
        assertTrue(
            MacroRecorder.apply(buf, Action.ReplaceAll("cat", "dog", regex = false, matchCase = false))
        )
        assertEquals("dog bat dog", buf.text)

        val buf2 = StringBuffer("a1 b2 c3")
        assertTrue(
            MacroRecorder.apply(buf2, Action.ReplaceAll("[a-c](\\d)", "$1x", regex = true, matchCase = true))
        )
        assertEquals("1x 2x 3x", buf2.text)
    }

    @Test
    fun replace_all_no_match_returns_false() {
        val buf = StringBuffer("abc")
        assertFalse(MacroRecorder.apply(buf, Action.ReplaceAll("zzz", "x", false, false)))
        assertEquals("abc", buf.text)
    }

    @Test
    fun transform_case_replay() {
        val buf = StringBuffer("hello world")
        assertTrue(MacroRecorder.apply(buf, Action.TransformCase("upper", 0, 5)))
        assertEquals("HELLO world", buf.text)
        assertTrue(MacroRecorder.apply(buf, Action.TransformCase("toggle", 0, 5)))
        assertEquals("hello world", buf.text)
        assertFalse(MacroRecorder.apply(buf, Action.TransformCase("bogus", 0, 1)))
    }

    @Test
    fun goto_line_replay() {
        val buf = StringBuffer("one\ntwo\nthree")
        assertTrue(MacroRecorder.apply(buf, Action.GoToLine(2)))
        assertEquals(4, buf.selection)
        assertTrue(MacroRecorder.apply(buf, Action.GoToLine(1)))
        assertEquals(0, buf.selection)
    }

    @Test
    fun invalid_actions_are_skipped_and_reported() {
        val buf = StringBuffer("abc")
        assertEquals(0, MacroRecorder.replay(buf, listOf(Action.Insert(99, "x"), Action.Delete(5, 1))))
        assertEquals("abc", buf.text)
    }
}
