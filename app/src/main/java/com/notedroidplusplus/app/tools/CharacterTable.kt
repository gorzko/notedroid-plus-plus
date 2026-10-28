package com.notedroidplusplus.app.tools

import java.nio.charset.Charset

/**
 * Character Panel data (Notepad++ ASCII panel parity, values 0..255):
 * Value, Hex, Char, HTML entity name, HTML decimal entity, HTML hex entity.
 * For 8-bit encodings the char column maps the byte through that charset;
 * for UTF-8/UTF-16 the Notepad++ behavior of mapping 128-255 through the
 * system ANSI codepage is approximated with windows-1252 (the dominant ANSI
 * codepage) - values unmapped in the charset render as a placeholder box.
 */
object CharacterTable {

    data class Row(
        val value: Int,
        val hex: String,
        val char: String,
        val htmlName: String?,
        val htmlDecimal: String,
        val htmlHex: String
    )

    private val NAMED: Map<Int, String> = mapOf(
        34 to "quot", 38 to "amp", 39 to "apos", 60 to "lt", 62 to "gt",
        160 to "nbsp", 161 to "iexcl", 162 to "cent", 163 to "pound", 164 to "curren",
        165 to "yen", 166 to "brvbar", 167 to "sect", 168 to "uml", 169 to "copy",
        170 to "ordf", 171 to "laquo", 172 to "not", 173 to "shy", 174 to "reg",
        175 to "macr", 176 to "deg", 177 to "plusmn", 178 to "sup2", 179 to "sup3",
        180 to "acute", 181 to "micro", 182 to "para", 183 to "middot", 184 to "cedil",
        185 to "sup1", 186 to "ordm", 187 to "raquo", 188 to "frac14", 189 to "frac12",
        190 to "frac34", 191 to "iquest", 192 to "Agrave", 193 to "Aacute", 194 to "Acirc",
        195 to "Atilde", 196 to "Auml", 197 to "Aring", 198 to "AElig", 199 to "Ccedil",
        200 to "Egrave", 201 to "Eacute", 202 to "Ecirc", 203 to "Euml", 204 to "Igrave",
        205 to "Iacute", 206 to "Icirc", 207 to "Iuml", 208 to "ETH", 209 to "Ntilde",
        210 to "Ograve", 211 to "Oacute", 212 to "Ocirc", 213 to "Otilde", 214 to "Ouml",
        215 to "times", 216 to "Oslash", 217 to "Ugrave", 218 to "Uacute", 219 to "Ucirc",
        220 to "Uuml", 221 to "Yacute", 222 to "THORN", 223 to "szlig", 224 to "agrave",
        225 to "aacute", 226 to "acirc", 227 to "atilde", 228 to "auml", 229 to "aring",
        230 to "aelig", 231 to "ccedil", 232 to "egrave", 233 to "eacute", 234 to "ecirc",
        235 to "euml", 236 to "igrave", 237 to "iacute", 238 to "icirc", 239 to "iuml",
        240 to "eth", 241 to "ntilde", 242 to "ograve", 243 to "oacute", 244 to "ocirc",
        245 to "otilde", 246 to "ouml", 247 to "divide", 248 to "oslash", 249 to "ugrave",
        250 to "uacute", 251 to "ucirc", 252 to "uuml", 253 to "yacute", 254 to "thorn",
        255 to "yuml"
    )

    fun charFor(value: Int, charset: Charset): String {
        if (value in 0..31 || value == 127) return displayControl(value)
        // UTF-8/UTF-16 are multi-byte; a lone high byte maps through the ANSI
        // codepage (windows-1252) like Notepad++ does for its ANSI panel.
        val effective = if (charset.name().startsWith("UTF")) Charset.forName("windows-1252") else charset
        val bytes = byteArrayOf(value.toByte())
        val decoded = runCatching { String(bytes, effective) }.getOrDefault("\uFFFD")
        return if (decoded.isEmpty() || decoded.contains('\uFFFD')) "\u25A1" else decoded
    }

    private fun displayControl(value: Int): String = when (value) {
        0 -> "NUL"; 7 -> "BEL"; 8 -> "BS"; 9 -> "TAB"; 10 -> "LF"; 13 -> "CR"
        27 -> "ESC"; 127 -> "DEL"
        else -> String.format("Ctrl-%c", 'A' + value - 1)
    }

    fun rows(charset: Charset): List<Row> =
        (0..255).map { v ->
            Row(
                value = v,
                hex = String.format("%02X", v),
                char = charFor(v, charset),
                htmlName = NAMED[v]?.let { "&$it;" },
                htmlDecimal = "&#${v};",
                htmlHex = String.format("&#x%02x;", v)
            )
        }

    /** Parses a U+XXXX (or bare hex) input into a codepoint; null when invalid. */
    fun parseCodepoint(input: String): Int? {
        var raw = input.trim()
        if (raw.startsWith("U+") || raw.startsWith("u+")) raw = raw.substring(2)
        if (raw.isEmpty() || raw.length > 6) return null
        val v = raw.toIntOrNull(16) ?: return null
        return if (v in 0..0x10FFFF) v else null
    }

    fun codepointToChar(cp: Int): String = String(Character.toChars(cp))
}
