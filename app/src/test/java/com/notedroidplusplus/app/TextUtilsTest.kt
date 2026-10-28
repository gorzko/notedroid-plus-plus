package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.charset.Charset
import java.util.Random

class TextUtilsTest {

    @Test
    fun detectLineEndings() {
        assertEquals("\r\n", TextUtils.detectLineEnding("a\r\nb"))
        assertEquals("\n", TextUtils.detectLineEnding("a\nb"))
    }

    @Test
    fun normalizeAndConvert() {
        assertEquals("a\nb", TextUtils.normalize("a\r\nb"))
        assertEquals("a\r\nb", TextUtils.convertLineEndings("a\nb", "\r\n"))
    }

    @Test
    fun casing() {
        assertEquals("AB cd", TextUtils.toUpper("ab cd", 0, 2))
        assertEquals("AB cD", TextUtils.toLower("AB CD", 3, 4))
        assertEquals("Ab cd", TextUtils.titleCase("ab cd", 0, 2))
        assertEquals("AB", TextUtils.toggleCase("ab", 0, 2))
    }

    @Test
    fun trimTrailing() {
        assertEquals("a\nb", TextUtils.trimTrailingWhitespace("a  \nb\t"))
    }

    @Test
    fun countMatchesWholeWord() {
        assertEquals(1, TextUtils.countMatches("cat catalog", "cat", false, true))
        assertEquals(2, TextUtils.countMatches("cat catalog", "cat", false, false))
        assertEquals(2, TextUtils.countMatches("CAT cat", "cat", true, false))
    }

    @Test
    fun lineOffsets() {
        val t = "ab\ncd\nef"
        assertEquals(3, TextUtils.lineIndexToOffset(t, 1))
        assertEquals("cd", TextUtils.lineAt(t, 1))
    }

    @Test
    fun encodingDetection() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'a'.code.toByte())
        assertEquals("UTF-8", TextUtils.detect(bytes).name())
        assertEquals("UTF-16LE", TextUtils.detect(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0)).name())
    }

    // ---- Line operations (#34) ----

    private val doc = "one\ntwo\nthree\nfour"

    @Test
    fun lineSpanForSelection() {
        assertEquals(1 to 2, TextUtils.lineSpanForSelection(doc, 4, 11))
        assertEquals(1 to 1, TextUtils.lineSpanForSelection(doc, 5, 5))
        assertEquals(0 to 3, TextUtils.lineSpanForSelection(doc, 0, doc.length))
    }

    @Test
    fun duplicateLines() {
        assertEquals("one\ntwo\ntwo\nthree\nfour", TextUtils.duplicateLines(doc, 1, 1, "\n"))
        assertEquals("one\ntwo\nthree\ntwo\nthree\nfour", TextUtils.duplicateLines(doc, 1, 2, "\n"))
        assertEquals("one\r\ntwo\r\ntwo\r\nthree", TextUtils.duplicateLines("one\r\ntwo\r\nthree", 1, 1, "\r\n"))
    }

    @Test
    fun splitJoinLines() {
        assertEquals("a\nb\nc", TextUtils.splitLines("a b c", 0, 5, "\n"))
        assertEquals("a b c", TextUtils.splitLines("a b c", 0, 0, "\n"))
        assertEquals("one\ntwo three", TextUtils.joinLines("one\ntwo\nthree", 4, 13))
        assertEquals("one\ntwo", TextUtils.joinLines("one\ntwo", 0, 3))
        assertEquals("a\r\nb\r\nc\r\nd", TextUtils.splitLines("a b\r\nc d", 0, 8, "\r\n"))
    }

    @Test
    fun removeEmptyLines() {
        assertEquals("a\nc", TextUtils.removeEmptyLines("a\n\nc", 0, 2, "\n", includingBlank = false))
        assertEquals("a\nc", TextUtils.removeEmptyLines("a\n \nc", 0, 2, "\n", includingBlank = true))
        assertEquals("a\n \nc", TextUtils.removeEmptyLines("a\n \nc", 0, 2, "\n", includingBlank = false))
    }

    @Test
    fun moveLines() {
        assertEquals("two\none\nthree\nfour", TextUtils.moveLineUp(doc, 1, "\n"))
        assertEquals("one", TextUtils.moveLineUp("one", 0, "\n"))
        assertEquals("one\nthree\ntwo\nfour", TextUtils.moveLineDown(doc, 1, "\n"))
        assertEquals("one\ntwo\nthree", TextUtils.moveLineDown("one\ntwo\nthree", 2, "\n"))
    }

    @Test
    fun reverseAndRandomizeLines() {
        assertEquals("three\ntwo\none", TextUtils.reverseLines("one\ntwo\nthree", 0, 2, "\n"))
        val r = Random(42)
        val out = TextUtils.randomizeLines("one\ntwo\nthree", 0, 2, "\n", r)
        assertEquals(listOf("one", "two", "three").sorted(), out.split("\n").sorted())
    }

    @Test
    fun sortLinesVariants() {
        assertEquals("a\nb\nc", TextUtils.sortLines("c\na\nb", 0, 2, "\n", descending = false, numeric = false, ignoreCase = false))
        assertEquals("c\nb\na", TextUtils.sortLines("c\na\nb", 0, 2, "\n", descending = true, numeric = false, ignoreCase = false))
        assertEquals("2\n10\n30", TextUtils.sortLines("10\n30\n2", 0, 2, "\n", descending = false, numeric = true, ignoreCase = false))
        assertEquals("a\nB\nc", TextUtils.sortLines("c\nB\na", 0, 2, "\n", descending = false, numeric = false, ignoreCase = true))
        assertEquals("a\nB\nc", TextUtils.sortLines("B\na\nc", 0, 2, "\n", descending = false, numeric = false, ignoreCase = true))
        assertEquals("B\na\nc", TextUtils.sortLines("B\na\nc", 0, 2, "\n", descending = false, numeric = false, ignoreCase = false))
    }

    @Test
    fun removeDuplicateLinesVariants() {
        assertEquals("a\nb\nc", TextUtils.removeDuplicateLines("a\nb\na\nc", 0, 3, "\n", consecutiveOnly = false))
        assertEquals("a\nb\na", TextUtils.removeDuplicateLines("a\nb\nb\na", 0, 3, "\n", consecutiveOnly = true))
    }

    // ---- Blank operations (#35) ----

    @Test
    fun blankOperations() {
        assertEquals("a \nb", TextUtils.trimLeadingWhitespace(" a \nb", "\n"))
        assertEquals("a\nb", TextUtils.trimLeadingAndTrailing(" a \nb ", "\n"))
        assertEquals("a b", TextUtils.eolToSpace("a\nb"))
        assertEquals("a    b", TextUtils.tabToSpace("a\tb", "\n", 4))
        assertEquals("    a\nb", TextUtils.tabToSpace("\ta\nb", "\n", 4))
    }

    @Test
    fun spaceToTabVariants() {
        assertEquals("\ta", TextUtils.spaceToTab("    a", "\n", 4, all = false))
        assertEquals("a\tb", TextUtils.spaceToTab("a    b", "\n", 4, all = true))
        assertEquals("a    b", TextUtils.spaceToTab("a    b", "\n", 4, all = false))
        assertEquals("  a", TextUtils.spaceToTab("  a", "\n", 4, all = false))
    }

    // ---- Extended case conversions (#35) ----

    @Test
    fun properCaseVariants() {
        assertEquals("Ab Cd", TextUtils.properCase("ab cd", 0, 5, blend = false))
        assertEquals("AB CD", TextUtils.properCase("aB cD", 0, 5, blend = true))
        assertEquals("Abc D", TextUtils.properCase("abc D", 0, 5, blend = true))
    }

    @Test
    fun sentenceCaseVariants() {
        assertEquals("Ab cd. Ef", TextUtils.sentenceCase("ab cd. ef", 0, 9, blend = false))
        assertEquals("AB cD. EF", TextUtils.sentenceCase("aB cD. eF", 0, 9, blend = true))
    }

    @Test
    fun invertAndRandomCase() {
        assertEquals("iNVERT cASE ok", TextUtils.invertCase("Invert Case OK", 0, 14))
        assertEquals("123-456", TextUtils.invertCase("123-456", 0, 7))
        val r = Random(7)
        val out = TextUtils.randomCase("abcd", 0, 4, r)
        assertEquals("abcd", out.lowercase())
        assertEquals(4, out.length)
    }

    // ---- Indent (#36) ----

    @Test
    fun indentOperations() {
        assertEquals("\ta\n\tb", TextUtils.indentIncrease("a\nb", 0, 1, "\n", "\t"))
        assertEquals("    a\n    b", TextUtils.indentIncrease("a\nb", 0, 1, "\n", "    "))
        assertEquals("b", TextUtils.indentDecrease("\tb", 0, 0, "\n", "\t"))
        assertEquals("b", TextUtils.indentDecrease("    b", 0, 0, "\n", "\t"))
        assertEquals("  b", TextUtils.indentDecrease("      b", 0, 0, "\n", "\t"))
        assertEquals("b", TextUtils.indentDecrease("  b", 0, 0, "\n", "\t"))
        assertEquals("x", TextUtils.indentDecrease("x", 0, 0, "\n", "\t"))
    }

    // ---- Date/time formatting (#36) ----

    @Test
    fun dateTimeFormatting() {
        val millis = java.time.ZonedDateTime.of(2026, 3, 14, 9, 5, 0, 0, java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val short = TextUtils.formatDateTime(TextUtils.DATETIME_SHORT, millis)
        assertEquals(true, short.contains("2026"))
        assertEquals(true, short.contains("3/14/2026"))
        val long = TextUtils.formatDateTime(TextUtils.DATETIME_LONG, millis)
        assertEquals(true, long.contains("March"))
        assertEquals("2026-03-14 09:05:00", TextUtils.formatDateTime("yyyy-MM-dd HH:mm:ss", millis))
        assertEquals("", TextUtils.formatDateTime("bad pattern {{{", millis))
    }
}

    // ---- Encoding families, Convert-to, prolog detection (#51) ----

    @Test
    fun charsetFamiliesResolve() {
        // every offered family charset must exist on the JVM
        TextUtils.CHARSET_FAMILIES.forEach { (_, names) ->
            names.forEach { n ->
                assertTrue("missing charset $n", Charset.forName(n) != null)
            }
        }
        // allCharsets is distinct and includes family members
        val all = TextUtils.allCharsets().map { it.name() }
        assertTrue(all.contains("KOI8-R"))
        assertTrue(all.contains("Shift_JIS"))
        assertTrue(all.contains("windows-1251"))
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun canRepresentChecks() {
        val cp1251 = Charset.forName("windows-1251")
        assertTrue(TextUtils.canRepresent("hello", cp1251))
        assertTrue(TextUtils.canRepresent("Привет", cp1251))
        assertFalse(TextUtils.canRepresent("日本語", cp1251))
        assertTrue(TextUtils.canRepresent("日本語", Charsets.UTF_8))
        assertFalse(TextUtils.canRepresent("é", Charsets.US_ASCII))
        assertTrue(TextUtils.canRepresent("plain ascii", Charsets.US_ASCII))
    }

    @Test
    fun prologDetection() {
        val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root/>"
        assertEquals(Charset.forName("ISO-8859-1"), TextUtils.detectFromProlog(xml))
        val xmlSingle = "<?xml version='1.0' encoding='Shift_JIS'?><a/>"
        // single quotes: charset= fallback covers the meta form; xml decl with quotes is primary
        assertNull(TextUtils.detectFromProlog(xmlSingle))
        val html = "<html><head><meta charset=\"windows-1252\"></head><body>x</body></html>"
        assertEquals(Charset.forName("windows-1252"), TextUtils.detectFromProlog(html))
        assertNull(TextUtils.detectFromProlog("plain text with no prolog at all"))
        // bounded: prolog beyond 4 KB is ignored
        val far = " ".repeat(5000) + "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
        assertNull(TextUtils.detectFromProlog(far))
    }

    @Test
    fun roundTripThroughEncodings() {
        val sample = "héllo wörld 42"
        listOf("UTF-8", "ISO-8859-1", "windows-1252", "KOI8-R", "Shift_JIS", "GB18030").forEach { name ->
            val cs = Charset.forName(name)
            if (TextUtils.canRepresent(sample, cs)) {
                val bytes = sample.toByteArray(cs)
                assertEquals(sample, String(bytes, cs))
            }
        }
    }
