package com.notedroidplusplus.app

import com.notedroidplusplus.app.preview.MarkdownRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun heading() {
        assertEquals("<h1>Title</h1>\n", MarkdownRenderer.render("# Title"))
    }

    @Test
    fun paragraphWithBoldAndCode() {
        val html = MarkdownRenderer.render("hello **world** `code`")
        assertTrue(html.contains("<b>world</b>"))
        assertTrue(html.contains("<code>code</code>"))
        assertTrue(html.contains("<p>hello"))
    }

    @Test
    fun listItems() {
        val html = MarkdownRenderer.render("- a\n- b")
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>a</li>"))
    }

    @Test
    fun link() {
        val html = MarkdownRenderer.render("[site](https://example.com)")
        assertTrue(html.contains("<a href=\"https://example.com\">site</a>"))
    }

    @Test
    fun fencedCodeEscapesHtml() {
        val html = MarkdownRenderer.render("```\n<a>&\n```")
        assertTrue(html.contains("&lt;a&gt;&amp;"))
    }

    @Test
    fun escapesRawHtml() {
        assertEquals("&lt;script&gt;", MarkdownRenderer.escape("<script>"))
    }
}
