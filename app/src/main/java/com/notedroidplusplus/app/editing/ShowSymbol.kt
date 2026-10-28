package com.notedroidplusplus.app.editing

/**
 * View > Show Symbol decisions (Notepad++ parity). Pure: given the toggles,
 * computes the effective visualization flags; "Show All Characters" turns
 * everything on regardless of the individual flags.
 */
data class ShowSymbolFlags(
    val spacesAndTabs: Boolean = false,
    val endOfLine: Boolean = false,
    val nonPrinting: Boolean = false,
    val controlChars: Boolean = false,
    val indentGuide: Boolean = false,
    val wrapSymbol: Boolean = false
) {
    companion object {
        fun fromPrefs(
            spaces: Boolean, eol: Boolean, nonPrinting: Boolean, control: Boolean,
            indent: Boolean, wrap: Boolean, all: Boolean
        ): ShowSymbolFlags =
            if (all) ShowSymbolFlags(true, true, true, true, true, true)
            else ShowSymbolFlags(spaces, eol, nonPrinting, control, indent, wrap)
    }
}

object ShowSymbol {

    /** Middle dot for space, rightwards arrow for tab (Notepad++ glyphs). */
    const val SPACE_GLYPH = "·"
    const val TAB_GLYPH = "→"
    const val WRAP_GLYPH = "↩"
    const val EOL_LF = "LF"
    const val EOL_CR = "CR"
    const val EOL_CRLF = "CRLF"

    /** NPP-style abbreviations for control characters 0..31 (subset of the manual's table). */
    val CONTROL_ABBREVIATIONS: Map<Int, String> = mapOf(
        0 to "NUL", 1 to "SOH", 2 to "STX", 3 to "ETX", 4 to "EOT", 5 to "ENQ", 6 to "ACK",
        7 to "BEL", 8 to "BS", 9 to "HT", 10 to "LF", 11 to "VT", 12 to "FF", 13 to "CR",
        14 to "SO", 15 to "SI", 16 to "DLE", 17 to "DC1", 18 to "DC2", 19 to "DC3",
        20 to "DC4", 21 to "NAK", 22 to "SYN", 23 to "ETB", 24 to "CAN", 25 to "EM",
        26 to "SUB", 27 to "ESC", 28 to "FS", 29 to "GS", 30 to "RS", 31 to "US",
        127 to "DEL"
    )

    fun abbreviationFor(code: Int): String? = CONTROL_ABBREVIATIONS[code]

    /** True for the non-printing Unicode set NPP visualizes (NBSP, ZWSP, ZWNJ, ZWJ, BOM, LRM/RLM...). */
    fun isNonPrinting(c: Char): Boolean = when (c) {
        ' ', '​', '‌', '‍', '﻿', '‎', '‏', '⁠', '­' -> true
        else -> false
    }

    fun nonPrintingAbbreviation(c: Char): String = when (c) {
        ' ' -> "NBSP"
        '​' -> "ZWSP"
        '‌' -> "ZWNJ"
        '‍' -> "ZWJ"
        '﻿' -> "BOM"
        '‎' -> "LRM"
        '‏' -> "RLM"
        '⁠' -> "WJ"
        '­' -> "SHY"
        else -> "?"
    }

    /**
     * Leading indent width of a line in columns (tab = [tabSize]), for the
     * indent-guide dotted lines.
     */
    fun indentWidth(line: String, tabSize: Int): Int {
        var width = 0
        for (c in line) {
            when (c) {
                ' ' -> width++
                '\t' -> width += tabSize - (width % tabSize)
                else -> return width
            }
        }
        return width
    }
}
