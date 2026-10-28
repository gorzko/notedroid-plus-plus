package com.notedroidplusplus.app.editing

import com.notedroidplusplus.app.LanguageMode
import com.notedroidplusplus.app.SyntaxToken
import com.notedroidplusplus.app.TokenKind
import com.notedroidplusplus.app.tools.Json

/**
 * User Defined Language (UDL) model and tokenizer, mirroring Notepad++'s
 * UDL feature: user-defined keyword lists, comment delimiters, string
 * delimiters, operators, and case sensitivity, applied on top of the fixed
 * built-in [LanguageMode] set. Pure JVM logic — persisted as JSON via the
 * app's own [Json] tool.
 */
data class UserDefinedLanguage(
    val name: String,
    val extensions: Set<String>,
    val keywords: List<Set<String>>,
    val types: Set<String>,
    val lineComments: List<String>,
    val blockComment: Pair<String, String>?,
    val stringDelimiters: List<Char>,
    val operators: Set<String>,
    val caseInsensitive: Boolean
) {
    companion object {
        const val MAX_KEYWORD_LISTS = 8
        const val MAX_NAME_LENGTH = 64

        /** Characters never allowed in a UDL name (file-name safety + readability). */
        private val FORBIDDEN_NAME_CHARS = "/\\:?*\"<>|"

        /** Sanitizes a UDL name: strips path/format characters and trims dots. */
        fun sanitizeName(raw: String): String =
            raw.trim().filter { it !in FORBIDDEN_NAME_CHARS }.trim('.', ' ').take(MAX_NAME_LENGTH)

        /** Lowercases and strips path/dot characters from extensions. */
        private fun sanitizeExtension(raw: String): String =
            raw.trim().lowercase().filter { it.isLetterOrDigit() }

        private fun sanitizeDelimiters(raw: String): String =
            raw.take(8).filter { it.isLetterOrDigit() || it in "!#$%&*+-./:;<=>?@^_~()[]{}\"'\\" }

        private fun Json.Value?.asStrList(): List<String> = when (this) {
            is Json.Value.Arr -> items.mapNotNull { (it as? Json.Value.Str)?.s }
            is Json.Value.Str -> listOf(s)
            else -> emptyList()
        }

        private fun Json.Value?.asStrSet(): Set<String> =
            asStrList().filter { it.isNotEmpty() }.toSet()

        /**
         * Parses a UDL from JSON. Throws [IllegalArgumentException] with a
         * human-readable message on malformed input so imports surface an
         * error toast instead of a crash.
         */
        fun fromJson(text: String): UserDefinedLanguage {
            val root = Json.parse(text)
            val obj = root as? Json.Value.Obj
                ?: throw IllegalArgumentException("Expected a JSON object")
            fun field(key: String): Json.Value? =
                obj.entries.firstOrNull { it.first == key }?.second

            val name = (field("name") as? Json.Value.Str)?.s?.let(::sanitizeName)
                ?: throw IllegalArgumentException("Missing or empty \"name\"")
            if (name.isEmpty()) throw IllegalArgumentException("Missing or empty \"name\"")

            val keywords = (field("keywords") as? Json.Value.Arr)?.let { arr ->
                arr.items.take(MAX_KEYWORD_LISTS).map { it.asStrSet() }
            } ?: emptyList()

            val block = field("blockComment") as? Json.Value.Arr
            val blockComment = block?.let { arr ->
                val open = (arr.items.getOrNull(0) as? Json.Value.Str)?.s?.let(::sanitizeDelimiters)
                val close = (arr.items.getOrNull(1) as? Json.Value.Str)?.s?.let(::sanitizeDelimiters)
                if (!open.isNullOrEmpty() && !close.isNullOrEmpty()) open to close else null
            }

            return UserDefinedLanguage(
                name = name,
                extensions = field("extensions").asStrList()
                    .map(::sanitizeExtension).filter { it.isNotEmpty() }.toSet(),
                keywords = keywords,
                types = field("types").asStrSet(),
                lineComments = field("lineComments").asStrList()
                    .map(::sanitizeDelimiters).filter { it.isNotEmpty() },
                blockComment = blockComment,
                stringDelimiters = field("stringDelimiters").asStrList()
                    .flatMap { it.toList() }
                    .filter { it.isLetterOrDigit().not() && it !in "\n\r\t " }
                    .distinct(),
                operators = field("operators").asStrSet()
                    .map(::sanitizeDelimiters).filter { it.isNotEmpty() }.toSet(),
                caseInsensitive = (field("caseInsensitive") as? Json.Value.Bool)?.b ?: false
            )
        }
    }

    fun toJson(): String {
        val block: Json.Value = blockComment?.let {
            Json.Value.Arr(listOf(Json.Value.Str(it.first), Json.Value.Str(it.second)))
        } ?: Json.Value.Arr(emptyList())
        return Json.stringify(
            Json.Value.Obj(
                listOf(
                    "name" to Json.Value.Str(name),
                    "extensions" to Json.Value.Arr(extensions.map { Json.Value.Str(it) }),
                    "keywords" to Json.Value.Arr(keywords.map { list ->
                        Json.Value.Arr(list.map { Json.Value.Str(it) })
                    }),
                    "types" to Json.Value.Arr(types.map { Json.Value.Str(it) }),
                    "lineComments" to Json.Value.Arr(lineComments.map { Json.Value.Str(it) }),
                    "blockComment" to block,
                    "stringDelimiters" to Json.Value.Arr(
                        stringDelimiters.map { Json.Value.Str(it.toString()) }),
                    "operators" to Json.Value.Arr(operators.map { Json.Value.Str(it) }),
                    "caseInsensitive" to Json.Value.Bool(caseInsensitive)
                )
            ),
            indent = 2
        )
    }

    /**
     * Tokenizes [text] with this UDL definition. Keyword list 1+ map to
     * KEYWORD tokens, the types set to TYPE, operators to OPERATOR, comments
     * and strings handled by their delimiters — mirroring how the built-in
     * generic lexer works so custom languages highlight identically.
     */
    fun tokenize(text: String): List<SyntaxToken> {
        val keywordsFlat = keywords.flatten().toSet()
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        val lineCommentStarts = lineComments.sortedByDescending { it.length }
        val stringOps = stringDelimiters
        val opsSorted = operators.filter { it.isNotEmpty() }.sortedByDescending { it.length }

        fun matchesAt(pos: Int, s: String): Boolean =
            text.regionMatches(pos, s, 0, s.length, ignoreCase = caseInsensitive)

        fun classifyWord(start: Int, end: Int) {
            val w = text.substring(start, end)
            val hit = when {
                keywordsFlat.any { if (caseInsensitive) it.equals(w, true) else it == w } -> TokenKind.KEYWORD
                types.any { if (caseInsensitive) it.equals(w, true) else it == w } -> TokenKind.TYPE
                else -> null
            }
            if (hit != null) tokens.add(SyntaxToken(start, end, hit))
        }

        while (i < n) {
            val c = text[i]
            var consumed = false
            for (lc in lineCommentStarts) {
                if (matchesAt(i, lc)) {
                    val end = text.indexOf('\n', i).let { if (it < 0) n else it }
                    tokens.add(SyntaxToken(i, end, TokenKind.COMMENT))
                    i = end
                    consumed = true
                    break
                }
            }
            if (consumed) continue
            blockComment?.let { (open, close) ->
                if (matchesAt(i, open)) {
                    val end = text.indexOf(close, i + open.length)
                    val stop = if (end < 0) n else end + close.length
                    tokens.add(SyntaxToken(i, stop, TokenKind.COMMENT))
                    i = stop
                    consumed = true
                }
            }
            if (consumed) continue
            if (stringOps.contains(c)) {
                var j = i + 1
                var closed = false
                while (j < n) {
                    if (text[j] == '\\') { j += 2; continue }
                    if (text[j] == c) { closed = true; j++; break }
                    j++
                }
                val end = if (closed) j else n
                tokens.add(SyntaxToken(i, end, TokenKind.STRING))
                i = end
                continue
            }
            val op = opsSorted.firstOrNull { matchesAt(i, it) }
            if (op != null && !c.isLetterOrDigit()) {
                tokens.add(SyntaxToken(i, i + op.length, TokenKind.OPERATOR))
                i += op.length
                continue
            }
            if (c.isLetterOrDigit() || c == '_') {
                val start = i
                while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                classifyWord(start, i)
                continue
            }
            i++
        }
        return tokens
    }
}
