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

    @Test
    fun linkWithAttributeInjectionIsNeutralized() {
        val html = MarkdownRenderer.render("[x](https://a.com\" onmouseover=\"alert(1))")
        assertTrue("no raw quote+space may break out of the href", !html.contains("\" onmouseover"))
        assertTrue("space must be percent-encoded inside the href", html.contains("%20onmouseover"))
        assertTrue(html.contains("<a href=\""))
    }

    @Test
    fun linkWithDangerousSchemeIsRejected() {
        for (scheme in listOf("javascript", "data", "file", "vbscript")) {
            val html = MarkdownRenderer.render("[x](${scheme}:alert(1))")
            assertTrue("scheme $scheme must not survive", !html.contains("${scheme}:"))
            assertTrue("link must degrade to plain label text", html.contains("<p>x"))
            assertTrue("no anchor tag for rejected scheme", !html.contains("<a href"))
            assertTrue("no stray close paren may leak as text", !html.contains("x)"))
        }
    }

    @Test
    fun linkSafeSchemesRender() {
        val html = MarkdownRenderer.render("[docs](https://example.com/a b)")
        assertTrue(html.contains("href=\"https://example.com/a%20b\""))
        val mail = MarkdownRenderer.render("[me](mailto:a@b.c)")
        assertTrue(mail.contains("href=\"mailto:a@b.c\""))
    }
}
