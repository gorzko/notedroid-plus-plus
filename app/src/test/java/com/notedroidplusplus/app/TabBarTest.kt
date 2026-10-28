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
        val elided = TabBar.elideLabel("README.md", 4)
        assertEquals("Rea" + "\u2026", elided)
        // explicit ellipsis codepoint check, independent of source escapes
        assertEquals("Rea" + String(charArrayOf(0x2026.toChar())), elided)
        assertEquals(String(charArrayOf(0x2026.toChar())), TabBar.elideLabel("README.md", 1))
    }
}
