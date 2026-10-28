package com.notedroidplusplus.app

import com.notedroidplusplus.app.TokenKind
import com.notedroidplusplus.app.editing.UserDefinedLanguage
import com.notedroidplusplus.app.editing.UserDefinedLanguageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserDefinedLanguageTest {

    private fun fixture() = UserDefinedLanguage(
        name = "MyLang",
        extensions = setOf("myl", "ml"),
        keywords = listOf(setOf("if", "then", "else")),
        types = setOf("int", "str"),
        lineComments = listOf("//", ";"),
        blockComment = "/*" to "*/",
        stringDelimiters = listOf('"', '\''),
        operators = setOf("+", "==", ":="),
        caseInsensitive = false
    )

    @Test
    fun roundTripsThroughJson() {
        val udl = fixture()
        val parsed = UserDefinedLanguage.fromJson(udl.toJson())
        assertEquals(udl.name, parsed.name)
        assertEquals(udl.extensions, parsed.extensions)
        assertEquals(udl.keywords, parsed.keywords)
        assertEquals(udl.types, parsed.types)
        assertEquals(udl.lineComments, parsed.lineComments)
        assertEquals(udl.blockComment, parsed.blockComment)
        assertEquals(udl.stringDelimiters, parsed.stringDelimiters)
        assertEquals(udl.operators, parsed.operators)
        assertEquals(udl.caseInsensitive, parsed.caseInsensitive)
    }

    @Test
    fun tokenizesKeywordsTypesStringsCommentsOperators() {
        val text = "if x == 3 // note\nint y := \"hi\"\n"
        val tokens = fixture().tokenize(text)
        fun at(kind: TokenKind, vararg expected: String) {
            val matching = tokens.filter { it.kind == kind }.map { text.substring(it.start, it.end) }
            assertEquals(expected.toList(), matching)
        }
        at(TokenKind.KEYWORD, "if")
        at(TokenKind.TYPE, "int")
        at(TokenKind.COMMENT, "// note")
        at(TokenKind.STRING, "\"hi\"")
        val ops = tokens.filter { it.kind == TokenKind.OPERATOR }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("==", ":="), ops)
    }

    @Test
    fun blockCommentSpansLines() {
        val tokens = fixture().tokenize("a /* multi\nline */ b")
        val comment = tokens.first { it.kind == TokenKind.COMMENT }
        assertEquals("/* multi\nline */", "a /* multi\nline */ b".substring(comment.start, comment.end))
    }

    @Test
    fun caseInsensitiveMatching() {
        val udl = fixture().copy(caseInsensitive = true)
        val tokens = udl.tokenize("IF x THEN")
        assertTrue(tokens.all { it.kind == TokenKind.KEYWORD })
        assertEquals(2, tokens.size)
    }

    @Test
    fun malformedImportThrowsReadableError() {
        val bad = listOf(
            "not json at all",
            "{\"name\": \"\"}",
            "[1,2,3]",
            "{\"extensions\": [\"x\"]}"
        )
        bad.forEach { text ->
            try {
                UserDefinedLanguage.fromJson(text)
                throw AssertionError("Expected failure for: $text")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotEmpty())
            }
        }
    }

    @Test
    fun nameAndExtensionsAreSanitized() {
        val udl = UserDefinedLanguage.fromJson(
            "{\"name\": \"../ Evil <Name>\", \"extensions\": [\".X1\", \"x2 \"], \"keywords\": [[]]}"
        )
        assertEquals("Evil Name", udl.name)
        assertEquals(setOf("x1", "x2"), udl.extensions)
    }

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun storeSavesListsAndDeletes() {
        val store = UserDefinedLanguageStore(tmp.newFolder())
        val udl = fixture()
        store.save(udl)
        assertEquals(listOf(udl), store.list())
        assertEquals(udl, store.forExtension("myl"))
        assertEquals(udl, store.findByName("mylang"))
        assertNull(store.forExtension("txt"))
        assertTrue(store.delete("MyLang"))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun corruptStoreFileIsSkippedNotFatal() {
        val dir = tmp.newFolder()
        dir.resolve("broken.udl.json").writeText("{ not json")
        val store = UserDefinedLanguageStore(dir)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun spaceAndTabAreRejectedAsStringDelimiters() {
        val udl = UserDefinedLanguage.fromJson(
            "{\"name\": \"S\", \"stringDelimiters\": [\"+ -\\t_\"]}"
        )
        assertEquals(listOf('+', '-', '_'), udl.stringDelimiters)
    }

    @Test
    fun sanitizedNamePreventsDiskCollisionBetweenAliases() {
        val a = UserDefinedLanguage.fromJson("{\"name\": \"My/Lang\"}")
        val b = UserDefinedLanguage.fromJson("{\"name\": \"My:Lang\"}")
        assertEquals(a.name, b.name)
        val store = UserDefinedLanguageStore(tmp.newFolder())
        store.save(a)
        store.save(b)
        assertEquals(1, store.list().size)
    }

    @Test
    fun editWithRenameLeavesSingleDefinition() {
        val store = UserDefinedLanguageStore(tmp.newFolder())
        val original = fixture()
        store.save(original)
        val renamed = original.copy(name = "MyLang2")
        store.save(renamed)
        store.delete(original.name)
        val list = store.list()
        assertEquals(1, list.size)
        assertEquals("MyLang2", list[0].name)
        assertNull(store.findByName("MyLang"))
    }
}
