package com.notedroidplusplus.app

import com.notedroidplusplus.app.editing.SyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun kinds(text: String, mode: LanguageMode): List<TokenKind> =
        SyntaxHighlighter.tokenize(text, mode).map { it.kind }

    @Test
    fun kotlinKeywordsAndTypes() {
        val text = "val x: Int = 42"
        val ks = kinds(text, LanguageMode.KOTLIN)
        assertTrue(ks.contains(TokenKind.KEYWORD)) // val
        assertTrue(ks.contains(TokenKind.TYPE))    // Int
        assertTrue(ks.contains(TokenKind.NUMBER))  // 42
    }

    @Test
    fun javaBlockComment() {
        val text = "int a; /* hidden */ int b;"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.JAVA)
        val comment = toks.first { it.kind == TokenKind.COMMENT }
        assertEquals("/* hidden */", text.substring(comment.start, comment.end))
    }

    @Test
    fun pythonHashComment() {
        val text = "x = 1 # note"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.PYTHON)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
    }

    @Test
    fun jsonStringsKeysNumbers() {
        val text = """{"a": 1, "b": [true, null, "x"]}"""
        val ks = kinds(text, LanguageMode.JSON)
        assertTrue(ks.contains(TokenKind.ATTRIBUTE))
        assertTrue(ks.contains(TokenKind.NUMBER))
        assertTrue(ks.contains(TokenKind.KEYWORD))
        assertTrue(ks.contains(TokenKind.STRING))
    }

    @Test
    fun htmlTagsAndAttributes() {
        val text = "<div class=\"x\">hi</div>"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.HTML)
        assertTrue(toks.any { it.kind == TokenKind.TAG })
        assertTrue(toks.any { it.kind == TokenKind.ATTRIBUTE })
        assertTrue(toks.any { it.kind == TokenKind.VALUE })
    }

    @Test
    fun xmlComment() {
        val text = "<root><!-- c --><a/></root>"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.XML)
        val comment = toks.first { it.kind == TokenKind.COMMENT }
        assertEquals("<!-- c -->", text.substring(comment.start, comment.end))
    }

    @Test
    fun markdownHeadingAndLink() {
        val text = "# Title\n[link](http://x)"
        val ks = kinds(text, LanguageMode.MARKDOWN)
        assertTrue(ks.contains(TokenKind.HEADING))
        assertTrue(ks.contains(TokenKind.LINK))
    }

    @Test
    fun languageFromFileName() {
        assertEquals(LanguageMode.JSON, LanguageMode.fromFileName("a.json"))
        assertEquals(LanguageMode.MARKDOWN, LanguageMode.fromFileName("README.MD"))
        assertEquals(LanguageMode.MAKEFILE, LanguageMode.fromFileName("Makefile"))
        assertEquals(LanguageMode.NORMAL_TEXT, LanguageMode.fromFileName("noext"))
    }

    @Test
    fun sqlCaseInsensitive() {
        val ks = kinds("SELECT * FROM t", LanguageMode.SQL)
        assertTrue(ks.contains(TokenKind.KEYWORD))
    }

    @Test
    fun emptyTextYieldsNoTokens() {
        assertTrue(SyntaxHighlighter.tokenize("", LanguageMode.JAVA).isEmpty())
    }
}
