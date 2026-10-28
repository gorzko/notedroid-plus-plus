package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.WindowSort
import com.notedroidplusplus.app.core.WindowSort.DocInfo
import com.notedroidplusplus.app.core.WindowSort.Field
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSortTest {

    private data class Tab(val name: String, val type: Int, val len: Int, val mod: Long)

    private val tabs = listOf(
        Tab("zeta.kt", 2, 100, 50L),
        Tab("Alpha.md", 1, 300, 10L),
        Tab("beta.py", 3, 50, 99L)
    )

    private fun info(t: Tab) = DocInfo(t.name, t.type, t.len, t.mod)

    @Test
    fun sort_by_name_ignores_case() {
        val sorted = WindowSort.sorted(tabs, ::info, Field.NAME, descending = false)
        assertEquals(listOf("Alpha.md", "beta.py", "zeta.kt"), sorted.map { it.name })
        val desc = WindowSort.sorted(tabs, ::info, Field.NAME, descending = true)
        assertEquals(listOf("zeta.kt", "beta.py", "Alpha.md"), desc.map { it.name })
    }

    @Test
    fun sort_by_type_and_length() {
        assertEquals(
            listOf(1, 2, 3),
            WindowSort.sorted(tabs, ::info, Field.TYPE, false).map { it.type }
        )
        assertEquals(
            listOf(50, 100, 300),
            WindowSort.sorted(tabs, ::info, Field.LENGTH, false).map { it.len }
        )
        assertEquals(
            listOf(300, 100, 50),
            WindowSort.sorted(tabs, ::info, Field.LENGTH, true).map { it.len }
        )
    }

    @Test
    fun sort_by_modified_time() {
        assertEquals(
            listOf(10L, 50L, 99L),
            WindowSort.sorted(tabs, ::info, Field.MODIFIED, false).map { it.mod }
        )
        assertEquals(
            listOf(99L, 50L, 10L),
            WindowSort.sorted(tabs, ::info, Field.MODIFIED, true).map { it.mod }
        )
    }
}
