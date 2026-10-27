package com.notedroidplusplus.app.search

/** Find-in-Files result row: file display name, optional source URI, line, column, line text. */
data class SearchHit(
    val file: String,
    val uri: String?,
    val line: Int,
    val column: Int,
    val lineText: String,
    val start: Int
)

/**
 * Finds all matches of [opts] in [text], line by line, attributing each hit to
 * [file]/[uri]. Pure Kotlin; used by both current-document and directory search.
 */
fun findAll(text: String, opts: FindOptions, file: String = "", uri: String? = null): List<SearchHit> {
    val hits = mutableListOf<SearchHit>()
    if (opts.query.isEmpty()) return hits
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    var lineStart = 0
    var line = 1
    var i = 0
    while (i <= normalized.length) {
        if (i == normalized.length || normalized[i] == '\n') {
            val lineText = normalized.substring(lineStart, i)
            var col = 0
            while (col < lineText.length) {
                val at = if (opts.regex) {
                    runCatching {
                        val re = if (opts.matchCase) Regex(opts.query) else Regex(opts.query, RegexOption.IGNORE_CASE)
                        re.find(lineText, col)?.range
                    }.getOrNull()
                } else {
                    val idx = lineText.indexOf(opts.query, col, ignoreCase = !opts.matchCase)
                    if (idx < 0) null else idx until (idx + opts.query.length)
                }
                if (at == null) break
                if (!opts.wholeWord || isWholeWordAt(lineText, at.first, at.last - at.first + 1)) {
                    hits.add(SearchHit(file, uri, line, at.first + 1, lineText.trim(), lineStart + at.first))
                }
                col = if (at.isEmpty()) at.first + 1 else at.last + 1
            }
            if (i == normalized.length) break
            lineStart = i + 1
            line++
        }
        i++
    }
    return hits
}

private fun isWholeWordAt(text: String, idx: Int, len: Int): Boolean {
    fun wc(c: Char) = c.isLetterOrDigit() || c == '_'
    val before = idx > 0 && wc(text[idx - 1])
    val after = idx + len < text.length && wc(text[idx + len])
    return !before && !after
}
