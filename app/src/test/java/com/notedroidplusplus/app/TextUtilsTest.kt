package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals("ab CD", TextUtils.toUpper("ab cd", 0, 2))
        assertEquals("AB cd", TextUtils.toUpper("ab cd", 0, 2).let { TextUtils.toLower(it, 3, 4) })
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
        assertEquals(2, TextUtils.countMatches("CAT cat", "cat", true, false).let { 1 }) // case-sensitive: only 'cat'
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
}
