package com.notedroidplusplus.app

import com.notedroidplusplus.app.editing.MarkdownActions
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownActionsTest {

    @Test
    fun boldWrapsSelection() {
        val r = MarkdownActions.bold("hello world", 0, 5)
        assertEquals("**hello** world", r.text)
        assertEquals(2, r.newStart)
        assertEquals(7, r.newEnd)
    }

    @Test
    fun boldTogglesOff() {
        val r = MarkdownActions.bold("**hello** world", 2, 7)
        assertEquals("hello world", r.text)
        assertEquals(0, r.newStart)
        assertEquals(5, r.newEnd)
    }

    @Test
    fun boldTogglesOffWhenSelectionIncludesMarkers() {
        val r = MarkdownActions.bold("**hello** world", 0, 9)
        assertEquals("hello world", r.text)
        assertEquals(0, r.newStart)
        assertEquals(5, r.newEnd)
    }

    @Test
    fun italicTogglesOffWhenSelectionIncludesMarkers() {
        val r = MarkdownActions.italic("a *hi* b", 2, 6)
        assertEquals("a hi b", r.text)
        assertEquals(2, r.newStart)
        assertEquals(4, r.newEnd)
    }

    @Test
    fun singleMarkerCharDoesNotUnwrap() {
        val r = MarkdownActions.italic("*", 0, 1)
        assertEquals("***", r.text)
    }

    @Test
    fun italicWraps() {
        val r = MarkdownActions.italic("aa bb", 3, 5)
        assertEquals("aa *bb*", r.text)
    }

    @Test
    fun inlineCodeWraps() {
        val r = MarkdownActions.inlineCode("f x", 0, 1)
        assertEquals("`f` x", r.text)
    }

    @Test
    fun strikeWraps() {
        val r = MarkdownActions.strike("old", 0, 3)
        assertEquals("~~old~~", r.text)
    }

    @Test
    fun headingAddsHashesAtLineStart() {
        val r = MarkdownActions.heading("hello\nworld", 6, 6, 2)
        assertEquals("hello\n## world", r.text)
        assertEquals(9, r.newStart)
        assertEquals(9, r.newEnd)
    }

    @Test
    fun headingReplacesExistingLevel() {
        val r = MarkdownActions.heading("## title", 0, 8, 3)
        assertEquals("### title", r.text)
        assertEquals(4, r.newStart)
        assertEquals(9, r.newEnd)
    }

    @Test
    fun headingKeepsCaretWithinBody() {
        val r = MarkdownActions.heading("## title", 4, 9, 3)
        assertEquals("### title", r.text)
        assertEquals(5, r.newStart)
        assertEquals(9, r.newEnd)
    }

    @Test
    fun headingDoesNotEatSevenHashLines() {
        val r = MarkdownActions.heading("####### text", 0, 12, 2)
        assertEquals("## ####### text", r.text)
    }

    @Test
    fun headingOnEmptyLine() {
        val r = MarkdownActions.heading("", 0, 0, 2)
        assertEquals("##", r.text)
        assertEquals(2, r.newStart)
        assertEquals(2, r.newEnd)
    }

    @Test
    fun unorderedListPrefixesEachSelectedLine() {
        val text = "alpha\nbeta\ngamma"
        val r = MarkdownActions.list(text, 0, text.length, ordered = false)
        assertEquals("- alpha\n- beta\n- gamma", r.text)
    }

    @Test
    fun orderedListNumbersLines() {
        val text = "alpha\nbeta"
        val r = MarkdownActions.list(text, 0, text.length, ordered = true)
        assertEquals("1. alpha\n2. beta", r.text)
    }

    @Test
    fun listTogglesOff() {
        val text = "- alpha\n- beta"
        val r = MarkdownActions.list(text, 0, text.length, ordered = false)
        assertEquals("alpha\nbeta", r.text)
        assertEquals(0, r.newStart)
        assertEquals("alpha\nbeta".length, r.newEnd)
    }

    @Test
    fun mixedListBlockTogglesAllMarkersOff() {
        val text = "- alpha\nbeta"
        val r = MarkdownActions.list(text, 0, text.length, ordered = false)
        assertEquals("alpha\nbeta", r.text)
        val again = MarkdownActions.list(r.text, r.newStart, r.newEnd, ordered = false)
        assertEquals("- alpha\n- beta", again.text)
    }

    @Test
    fun mixedOrderedBlockTogglesAllMarkersOff() {
        val text = "1. alpha\nbeta"
        val r = MarkdownActions.list(text, 0, text.length, ordered = true)
        assertEquals("alpha\nbeta", r.text)
    }

    @Test
    fun alreadyMarkedLinesAreNotDoubleMarked() {
        val text = "- alpha\nbeta"
        val r = MarkdownActions.list(text, 0, text.length, ordered = false)
        assertEquals("alpha\nbeta", r.text)
        assertEquals(0, r.newStart)
        assertEquals("alpha\nbeta".length, r.newEnd)
        val again = MarkdownActions.list(r.text, r.newStart, r.newEnd, ordered = true)
        assertEquals("1. alpha\n2. beta", again.text)
    }

    @Test
    fun linkInsertsLabelAndUrl() {
        val r = MarkdownActions.link("see  here", 4, 4, "https://x.y", "docs")
        assertEquals("see [docs](https://x.y) here", r.text)
        assertEquals(4, r.newStart)
        assertEquals(4 + "[docs](https://x.y)".length, r.newEnd)
    }

    @Test
    fun linkUsesSelectionAsLabel() {
        val r = MarkdownActions.link("see site now", 4, 8, "https://s.io")
        assertEquals("see [site](https://s.io) now", r.text)
    }

    @Test
    fun codeBlockFencesSelection() {
        val r = MarkdownActions.codeBlock("x = 1", 0, 5)
        assertEquals("```\nx = 1\n```", r.text)
        assertEquals(0, r.newStart)
        assertEquals("```\nx = 1\n```".length, r.newEnd)
    }

    @Test
    fun tableInsertsSkeleton() {
        val r = MarkdownActions.table("before", 6)
        assertEquals("before\n\n| Column 1 | Column 2 |\n| --- | --- |\n|  |  |", r.text)
    }
    @Test
    fun collapsedCaretInsideMarkerPairDoesNotDoubleWrap() {
        val r = MarkdownActions.toggleSpan("**x**", 2, 2, "**")
        assertEquals("**x**", r.text)
        assertEquals(2, r.newStart)
        assertEquals(2, r.newEnd)
    }

    @Test
    fun collapsedCaretAtSpanEdgeStillWraps() {
        // Caret at the outer edge of the pair: existing wrap path (pre-PR-29 behavior preserved).
        val r = MarkdownActions.toggleSpan("**x**", 0, 0, "**")
        assertEquals("******x**", r.text)
        val r2 = MarkdownActions.toggleSpan("**x**", 5, 5, "**")
        assertEquals("**x******", r2.text)
    }

    @Test
    fun collapsedCaretBeforeClosingMarkerDoesNotDoubleWrap() {
        val r = MarkdownActions.toggleSpan("**x**", 3, 3, "**")
        assertEquals("**x**", r.text)
        assertEquals(3, r.newStart)
        assertEquals(3, r.newEnd)
    }

    @Test
    fun collapsedCaretOutsideAnySpanStillWraps() {
        val r = MarkdownActions.toggleSpan("ab", 1, 1, "**")
        assertEquals("a****b", r.text)
    }
    @Test
    fun italicGuardInsideBoldSpanNoOps() {
        // Single-char marker inside a ** span: the guard no-ops (pinned behavior, m1).
        val r = MarkdownActions.italic("**x**", 2, 2)
        assertEquals("**x**", r.text)
        assertEquals(2, r.newStart)
        assertEquals(2, r.newEnd)
    }

    @Test
    fun inlineCodeInsideBoldSpanStillWraps() {
        // Backtick is not the adjacent marker; the guard does not fire, wrap path taken.
        val r = MarkdownActions.inlineCode("**x**", 2, 2)
        assertEquals("**``x**", r.text)
        assertEquals(3, r.newStart)
        assertEquals(3, r.newEnd)
    }

    @Test
    fun strikeInsideBoldSpanStillWraps() {
        // Double-tilde is not the adjacent marker; the guard does not fire, wrap path taken.
        val r = MarkdownActions.strike("**x**", 2, 2)
        assertEquals("**~~~~x**", r.text)
        assertEquals(4, r.newStart)
        assertEquals(4, r.newEnd)
    }

    @Test
    fun caretBetweenAdjacentPairsNoOps() {
        // a**b****c: caret between the closing ** of pair 1 and opening ** of pair 2.
        val r = MarkdownActions.toggleSpan("a**b****c", 5, 5, "**")
        assertEquals("a**b****c", r.text)
        assertEquals(5, r.newStart)
        assertEquals(5, r.newEnd)
    }
}