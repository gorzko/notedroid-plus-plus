package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.Bookmarks
import com.notedroidplusplus.app.editing.ShowSymbol
import com.notedroidplusplus.app.editing.ShowSymbolFlags
import com.notedroidplusplus.app.search.ExtendedMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedModeTest {
    @Test
    fun unescape_basic_sequences() {
        assertEquals("a\nb", ExtendedMode.unescape("a\\nb"))
        assertEquals("a\tb", ExtendedMode.unescape("a\\tb"))
        assertEquals("a\rb", ExtendedMode.unescape("a\\rb"))
        assertEquals("a\rb", ExtendedMode.unescape("a\\rb"))
        assertEquals("a\\b", ExtendedMode.unescape("a\\\\b"))
        assertEquals("a\u0000b", ExtendedMode.unescape("a\\0b"))
    }

    @Test
    fun unescape_hex_and_unicode() {
        assertEquals("aAb", ExtendedMode.unescape("a\\x41b"))
        assertEquals("❤", ExtendedMode.unescape("\\u2764"))
        assertEquals("a\\z", ExtendedMode.unescape("a\\z"))
    }

    @Test
    fun unescape_trailing_backslash_and_short_hex() {
        assertEquals("a\\", ExtendedMode.unescape("a\\"))
        assertEquals("a\\x4", ExtendedMode.unescape("a\\x4"))
    }
}

class BookmarksTest {

    @Test
    fun toggle_adds_and_removes() {
        val s0 = emptySet<Int>()
        val s1 = Bookmarks.toggle(s0, 5)
        assertEquals(setOf(5), s1)
        assertEquals(emptySet<Int>(), Bookmarks.toggle(s1, 5))
    }

    @Test
    fun next_prev_wrap_around() {
        val set = setOf(2, 10, 20)
        assertEquals(10, Bookmarks.next(set, 5))
        assertEquals(2, Bookmarks.next(set, 20))
        assertEquals(2, Bookmarks.next(set, 25))
        assertEquals(2, Bookmarks.prev(set, 5))
        assertEquals(20, Bookmarks.prev(set, 2))
        assertEquals(-1, Bookmarks.next(emptySet(), 0))
    }

    @Test
    fun invert_within_document() {
        assertEquals(setOf(0, 2), Bookmarks.invert(setOf(1), 3))
        assertEquals(setOf(0, 1, 2), Bookmarks.invert(emptySet(), 3))
    }

    @Test
    fun collect_and_remove_lines() {
        val text = "one\ntwo\nthree\nfour"
        assertEquals("two\nfour", Bookmarks.collectLines(text, "\n", setOf(1, 3)))
        assertEquals("one\nthree", Bookmarks.removeLines(text, "\n", setOf(1, 3)))
        assertEquals(text, Bookmarks.removeLines(text, "\n", emptySet()))
    }
}

class ShowSymbolTest {

    @Test
    fun all_characters_flag_forces_everything_on() {
        val f = ShowSymbolFlags.fromPrefs(false, false, false, false, false, false, all = true)
        assertTrue(f.spacesAndTabs && f.endOfLine && f.nonPrinting && f.controlChars && f.indentGuide && f.wrapSymbol)
        val off = ShowSymbolFlags.fromPrefs(false, false, false, false, false, false, all = false)
        assertFalse(off.spacesAndTabs || off.endOfLine || off.indentGuide)
    }

    @Test
    fun individual_flags_map_through() {
        val f = ShowSymbolFlags.fromPrefs(spaces = true, eol = true, nonPrinting = false, control = false, indent = true, wrap = false, all = false)
        assertTrue(f.spacesAndTabs && f.endOfLine && f.indentGuide)
        assertFalse(f.nonPrinting || f.controlChars || f.wrapSymbol)
    }

    @Test
    fun control_abbreviations() {
        assertEquals("NUL", ShowSymbol.abbreviationFor(0))
        assertEquals("LF", ShowSymbol.abbreviationFor(10))
        assertEquals("DEL", ShowSymbol.abbreviationFor(127))
        assertNull(ShowSymbol.abbreviationFor(65))
    }

    @Test
    fun non_printing_detection() {
        assertTrue(ShowSymbol.isNonPrinting(' '))
        assertTrue(ShowSymbol.isNonPrinting('​'))
        assertFalse(ShowSymbol.isNonPrinting('a'))
        assertEquals("NBSP", ShowSymbol.nonPrintingAbbreviation(' '))
    }

    @Test
    fun indent_width_counts_tabs_to_tab_stops() {
        assertEquals(4, ShowSymbol.indentWidth("\t", 4))
        assertEquals(6, ShowSymbol.indentWidth("\t  ", 4))
        assertEquals(3, ShowSymbol.indentWidth("   x", 4))
        assertEquals(0, ShowSymbol.indentWidth("x\t", 4))
    }
}
