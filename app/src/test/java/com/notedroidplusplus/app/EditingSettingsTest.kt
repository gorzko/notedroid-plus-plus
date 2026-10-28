package com.notedroidplusplus.app

import com.notedroidplusplus.app.editing.AutoComplete
import com.notedroidplusplus.app.editing.EdgeColumns
import com.notedroidplusplus.app.editing.MatchedPairs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeColumnsTest {
    @Test
    fun parse_column_lists() {
        assertEquals(listOf(80), EdgeColumns.parse("80"))
        assertEquals(listOf(72, 80, 100), EdgeColumns.parse("100, 72 80"))
        assertTrue(EdgeColumns.parse("").isEmpty())
        assertTrue(EdgeColumns.parse(null).isEmpty())
        assertTrue(EdgeColumns.parse("xx, 0, -5").isEmpty())
        assertEquals(listOf(80), EdgeColumns.parse("80, 80, 80"))
    }
}

class AutoCompleteTest {

    @Test
    fun document_words_unique() {
        val words = AutoComplete.documentWords("val alpha = 1\nval beta = alpha + 2")
        assertEquals(listOf("val", "alpha", "1", "beta", "2"), words)
    }

    @Test
    fun candidates_rank_shortest_prefix_match() {
        val cands = AutoComplete.candidates(
            "to", listOf("token", "total", "to", "totally"), listOf("toss"), minChars = 2
        )
        assertTrue(cands.contains("toss"))
        assertFalse(cands.contains("to"))
        assertTrue(cands.first().length <= cands.last().length)
        assertTrue(cands.size <= 30)
    }

    @Test
    fun candidates_respect_min_chars() {
        assertTrue(AutoComplete.candidates("a", listOf("abc"), emptyList(), minChars = 3).isEmpty())
        assertEquals(listOf("abc"), AutoComplete.candidates("ab", listOf("abc"), emptyList(), minChars = 2))
    }

    @Test
    fun prefix_extraction() {
        assertEquals("pri", AutoComplete.prefixEndingAt("print(", 3))
        assertEquals("", AutoComplete.prefixEndingAt("x + ", 4))
    }
}

class MatchedPairsTest {

    @Test
    fun typing_opener_inserts_pair() {
        val r = MatchedPairs.onTyped("ab", 2, '(', autoInsert = true)
        assertEquals("()", (r as MatchedPairs.Result.Insert).text)
        assertEquals(1, r.caretOffset)
    }

    @Test
    fun typing_closer_steps_over() {
        assertEquals(MatchedPairs.Result.StepOver, MatchedPairs.onTyped("()", 1, ')', autoInsert = true))
    }

    @Test
    fun quotes_do_not_close_inside_words() {
        val r = MatchedPairs.onTyped("say", 3, '"', autoInsert = true)
        assertEquals("\"", (r as MatchedPairs.Result.Insert).text)
    }

    @Test
    fun auto_insert_disabled() {
        assertEquals(MatchedPairs.Result.None, MatchedPairs.onTyped("ab", 2, '(', autoInsert = false))
    }

    @Test
    fun empty_pair_backspace_detection() {
        assertTrue(MatchedPairs.isEmptyPairAround("()", 1))
        assertTrue(MatchedPairs.isEmptyPairAround("''", 1))
        assertFalse(MatchedPairs.isEmptyPairAround("(x)", 1))
        assertFalse(MatchedPairs.isEmptyPairAround("", 1))
    }

    @Test
    fun html_tag_closer() {
        assertEquals("</div>", MatchedPairs.tagCloserFor("<div>", 5))
        assertEquals("</my-tag>", MatchedPairs.tagCloserFor("<my-tag attr=\"x\">", 17))
        assertNull(MatchedPairs.tagCloserFor("<br/>", 5))
        assertNull(MatchedPairs.tagCloserFor("</div>", 6))
    }
}
