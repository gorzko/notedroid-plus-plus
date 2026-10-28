package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.TabBar
import org.junit.Assert.assertEquals
import org.junit.Test

class TabBarTest {

    @Test
    fun pinnedFirstOrderKeepsPinnedLeading() {
        // tabs 0..4, pinned {2, 4} -> pinned group [2,4] then the rest in order
        assertEquals(listOf(2, 4, 0, 1, 3), TabBar.pinnedFirstOrder(5, setOf(2, 4)))
        assertEquals(listOf(0, 1, 2), TabBar.pinnedFirstOrder(3, emptySet()))
    }

    @Test
    fun closeAllButPinnedKeepsPinned() {
        assertEquals(listOf(4, 2, 1), TabBar.closeAllButPinned(5, setOf(0, 3)))
        assertEquals(emptyList<Int>(), TabBar.closeAllButPinned(2, setOf(0, 1)))
    }

    @Test
    fun closeAllButThis() {
        assertEquals(listOf(3, 2, 0), TabBar.closeAllButThis(4, 1))
    }

    @Test
    fun closeLeftAndRight() {
        assertEquals(listOf(2, 1, 0), TabBar.closeAllToLeft(3))
        assertEquals(listOf(4, 3), TabBar.closeAllToRight(5, 2))
        assertEquals(emptyList<Int>(), TabBar.closeAllToRight(5, 4))
    }

    @Test
    fun elideLabel() {
        assertEquals("README.md", TabBar.elideLabel("README.md", 0))
        assertEquals("README.md", TabBar.elideLabel("README.md", 20))
        // elided labels end with the ellipsis codepoint and keep maxChars-1 prefix
        val elided = TabBar.elideLabel("README.md", 4)
        assertEquals(4, elided.length)
        assertEquals("REA", elided.take(3))
        assertEquals(0x2026, elided.last().code)
        val single = TabBar.elideLabel("README.md", 1)
        assertEquals(1, single.length)
        assertEquals(0x2026, single.last().code)
    }
\n
    @Test
    fun indexOfUri_findsExistingTab() {
        data class T(val uri: String?)
        val tabs = listOf(T(null), T("a"), T("b"))
        assertEquals(1, TabBar.indexOfUri(tabs, { it.uri }, "a"))
        assertEquals(2, TabBar.indexOfUri(tabs, { it.uri }, "b"))
        assertEquals(-1, TabBar.indexOfUri(tabs, { it.uri }, "c"))
        assertEquals(-1, TabBar.indexOfUri(tabs, { it.uri }, "null"))
    }
}
