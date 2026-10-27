package com.notedroidplusplus.app

import com.notedroidplusplus.app.search.FindOptions
import com.notedroidplusplus.app.search.findAll
import com.notedroidplusplus.app.search.findNext
import com.notedroidplusplus.app.search.findPrev
import com.notedroidplusplus.app.search.replaceAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindEngineTest {

    @Test
    fun findNextPlain() {
        assertEquals(2, findNext("aXbcX", 0, FindOptions(query = "X")))
        assertEquals(4, findNext("aXbcX", 3, FindOptions(query = "X")))
    }

    @Test
    fun findNextIgnoreCase() {
        assertEquals(0, findNext("abc", 0, FindOptions(query = "ABC")))
        assertEquals(-1, findNext("abc", 0, FindOptions(query = "ABC", matchCase = true)))
    }

    @Test
    fun findNextWholeWord() {
        val text = "cat catalog cat"
        val opts = FindOptions(query = "cat", wholeWord = true)
        assertEquals(0, findNext(text, 0, opts))
        assertEquals(11, findNext(text, 1, opts))
    }

    @Test
    fun findNextRegex() {
        assertEquals(4, findNext("ab12cd", 0, FindOptions(query = "\\d+", regex = true)))
    }

    @Test
    fun findNextWrapsAround() {
        val text = "x x x"
        val opts = FindOptions(query = "x", wrapAround = true)
        assertEquals(2, findNext(text, 1, opts))
        assertEquals(0, findNext(text, 4, opts))
    }

    @Test
    fun findPrev() {
        assertEquals(2, findPrev("aXbXc", 4, FindOptions(query = "X")))
    }

    @Test
    fun replaceAllPlain() {
        val (text, count) = replaceAll("foo bar foo", FindOptions(query = "foo", replaceWith = "baz"))
        assertEquals("baz bar baz", text)
        assertEquals(2, count)
    }

    @Test
    fun replaceAllWholeWord() {
        val (text, count) = replaceAll(
            "cat catalog",
            FindOptions(query = "cat", replaceWith = "dog", wholeWord = true)
        )
        assertEquals("dog catalog", text)
        assertEquals(1, count)
    }

    @Test
    fun findAllHitsPerLine() {
        val hits = findAll("ab\ncd ab\n", FindOptions(query = "ab"))
        assertEquals(2, hits.size)
        assertEquals(1, hits[0].line)
        assertEquals(2, hits[1].line)
        assertEquals(4, hits[1].column)
    }

    @Test
    fun matchesHelper() {
        assertTrue(FindOptions(query = "zz").matches("aZZ"))
    }
}
