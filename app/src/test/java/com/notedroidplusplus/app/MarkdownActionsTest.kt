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
    }

    @Test
    fun headingReplacesExistingLevel() {
        val r = MarkdownActions.heading("## title", 0, 8, 3)
        assertEquals("### title", r.text)
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
    }

    @Test
    fun linkInsertsLabelAndUrl() {
        val r = MarkdownActions.link("see  here", 4, 4, "https://x.y", "docs")
        assertEquals("see [docs](https://x.y) here", r.text)
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
    }

    @Test
    fun tableInsertsSkeleton() {
        val r = MarkdownActions.table("before", 6)
        assertEquals("before\n\n| Column 1 | Column 2 |\n| --- | --- |\n|  |  |", r.text)
    }
}
