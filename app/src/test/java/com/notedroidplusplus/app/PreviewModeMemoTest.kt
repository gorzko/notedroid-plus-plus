package com.notedroidplusplus.app

import com.notedroidplusplus.app.preview.PreviewModeMemo
import com.notedroidplusplus.app.preview.PreviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewModeMemoTest {

    @Test
    fun repeatedCallsWithSameContentComputeOnlyOnce() {
        val memo = PreviewModeMemo()
        val text = "# Title\n\n- item\n"
        repeat(5) {
            assertNotNull(memo.modeFor(text, languageIsMarkdown = false, languageIsHtml = false))
        }
        assertEquals(1, memo.computations)
    }

    @Test
    fun editedContentTriggersRecompute() {
        val memo = PreviewModeMemo()
        memo.modeFor("plain words\n", false, false)
        val before = memo.computations
        val result = memo.modeFor("# now markdown\n", false, false)
        assertEquals(PreviewView.Mode.MARKDOWN, result)
        assertEquals(before + 1, memo.computations)
    }

    @Test
    fun cursorOnlyChangesDoNotRecompute() {
        val memo = PreviewModeMemo()
        val text = "# Heading\n\nbody text that is long enough to be realistic\n"
        memo.modeFor(text, false, false)
        // Simulate a keystroke that appends at the end: content change must recompute once,
        // but re-running with the same text (cursor move) must hit the cache.
        val edited = text + "more\n"
        memo.modeFor(edited, false, false)
        val after = memo.computations
        repeat(4) { memo.modeFor(edited, false, false) }
        assertEquals(after, memo.computations)
    }

    @Test
    fun languageFlagChangeTriggersRecompute() {
        val memo = PreviewModeMemo()
        val text = "plain words\n"
        assertNull(memo.modeFor(text, languageIsMarkdown = false, languageIsHtml = false))
        val before = memo.computations
        assertNotNull(memo.modeFor(text, languageIsMarkdown = true, languageIsHtml = false))
        assertEquals(before + 1, memo.computations)
    }

    @Test
    fun editsBeyondHeadStillRecomputeWhenLengthChanges() {
        val memo = PreviewModeMemo()
        val head = "plain body\n\n" + "a".repeat(5000)
        assertNull(memo.modeFor(head, false, false))
        val before = memo.computations
        // Change beyond the 4096-char head: length differs so fingerprint differs.
        assertNull(memo.modeFor(head + "z", false, false))
        assertEquals(before + 1, memo.computations)
    }

    @Test
    fun distinctMemoriesAreIndependent() {
        val a = PreviewModeMemo()
        val b = PreviewModeMemo()
        assertNull(a.modeFor("plain\n", false, false))
        assertEquals(0, b.computations)
    }
}
