package com.notedroidplusplus.app

import com.notedroidplusplus.app.tools.CharacterTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class CharacterTableTest {

    @Test
    fun rows_cover_0_to_255_with_all_columns() {
        val rows = CharacterTable.rows(Charsets.UTF_8)
        assertEquals(256, rows.size)
        assertEquals(0, rows[0].value)
        assertEquals(255, rows[255].value)
        assertEquals("41", rows[0x41].hex)
        assertEquals("A", rows[0x41].char)
        assertEquals("&#65;", rows[0x41].htmlDecimal)
        assertEquals("&#x41;", rows[0x41].htmlHex)
        assertEquals("&quot;", rows[34].htmlName)
        assertEquals("&amp;", rows[38].htmlName)
        assertNull(rows[66].htmlName)
    }

    @Test
    fun latin1_charset_maps_directly() {
        val rows = CharacterTable.rows(Charset.forName("ISO-8859-1"))
        assertEquals("é", rows[233].char)
        assertEquals("&eacute;", rows[233].htmlName)
    }

    @Test
    fun utf8_high_values_fall_back_to_ansi_equivalent() {
        val rows = CharacterTable.rows(Charsets.UTF_8)
        assertEquals("é", rows[233].char)
    }

    @Test
    fun control_characters_get_display_names() {
        val rows = CharacterTable.rows(Charsets.UTF_8)
        assertEquals("LF", rows[10].char)
        assertEquals("CR", rows[13].char)
        assertEquals("DEL", rows[127].char)
    }

    @Test
    fun codepoint_parsing() {
        assertEquals(0x2764, CharacterTable.parseCodepoint("U+2764"))
        assertEquals(0x41, CharacterTable.parseCodepoint("41"))
        assertEquals(0x10FFFF, CharacterTable.parseCodepoint("U+10FFFF"))
        assertNull(CharacterTable.parseCodepoint("U+110000"))
        assertNull(CharacterTable.parseCodepoint("zzz"))
        assertNull(CharacterTable.parseCodepoint(""))
        assertEquals("❤", CharacterTable.codepointToChar(0x2764))
    }

    @Test
    fun surrogate_codepoints_are_rejected() {
        assertNull(CharacterTable.parseCodepoint("U+D800"))
        assertNull(CharacterTable.parseCodepoint("U+DFFF"))
        assertNull(CharacterTable.parseCodepoint("U+DC00"))
    }
}
