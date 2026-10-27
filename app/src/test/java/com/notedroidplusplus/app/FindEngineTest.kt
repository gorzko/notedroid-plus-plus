package com.notedroidplusplus.app

import com.notedroidplusplus.app.search.FindOptions
import com.notedroidplusplus.app.search.findNext
import com.notedroidplusplus.app.search.findAll
import com.notedroidplusplus.app.search.findPrev
import com.notedroidplusplus.app.search.replaceAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindEngineTest {

    @Test
    fun findNextPlain() {
        assertEquals(1, findNext("aXbcX", 0, FindOptions(query = "X")))
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
        assertEquals(12, findNext(text, 1, opts))
    }

    @Test
    fun findNextRegex() {
        assertEquals(2, findNext("ab12cd", 0, FindOptions(query = "\\d+", regex = true)))
    }

    @Test
    fun findNextWrapsAround() {
        val text = "x x x"
        val opts = FindOptions(query = "x", wrapAround = true)
        assertEquals(2, findNext(text, 1, opts))
        assertEquals(4, findNext(text, 4, opts))
        assertEquals(0, findNext(text, 5, opts))
    }

    @Test
    fun findPrev() {
        assertEquals(3, findPrev("aXbXc", 4, FindOptions(query = "X")))
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
    fun replaceAllRegexUsesReplacement() {
        val (text, count) = replaceAll(
            "ab12cd34",
            FindOptions(query = "\\d+", replaceWith = "#", regex = true)
        )
        assertEquals("ab#cd#", text)
        assertEquals(2, count)
    }

    @Test
    fun replaceAllRegexGroupReferences() {
        val (text, _) = replaceAll(
            "john smith",
            FindOptions(query = "(john) (smith)", replaceWith = """$2 $1""", regex = true)
        )
        assertEquals("smith john", text)
    }

    @Test
    fun findPrevRegex() {
        assertEquals(2, findPrev("ab12cd34", 6, FindOptions(query = "\\d+", regex = true)))
    }

    @Test
    fun findAllHitsPerLine() {
        val hits = findAll("ab\ncd ab\n", FindOptions(query = "ab"), file = "f.txt")
        assertEquals(2, hits.size)
        assertEquals(1, hits[0].line)
        assertEquals(1, hits[0].column)
        assertEquals(2, hits[1].line)
        assertEquals(4, hits[1].column)
        assertEquals("f.txt", hits[0].file)
    }

    @Test
    fun findAllWholeWordAndRegex() {
        assertEquals(1, findAll("cat catalog", FindOptions(query = "cat", wholeWord = true)).size)
        assertEquals(1, findAll("ab12cd", FindOptions(query = "\\d+", regex = true)).size)
    }

    @Test
    fun matchesHelper() {
        assertTrue(FindOptions(query = "zz").matches("aZZ"))
    }
}
