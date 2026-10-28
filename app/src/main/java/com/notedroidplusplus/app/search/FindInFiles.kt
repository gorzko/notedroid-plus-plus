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
 * [start] is an offset into the ORIGINAL text (CRLF preserved), suitable for
 * in-buffer span marking. Honors Extended mode via [resolved].
 */
fun findAll(text: String, optsIn: FindOptions, file: String = "", uri: String? = null): List<SearchHit> {
    val opts = optsIn.resolved()
    val hits = mutableListOf<SearchHit>()
    if (opts.query.isEmpty()) return hits
    // Multi-line queries (extended \n or multi-line regex) need a full-text scan;
    // the line-based loop below cannot match across line boundaries.
    if (!opts.regex && opts.query.contains('\n')) {
        return findAllMultiLine(text, opts, file, uri)
    }
    var lineStart = 0
    var line = 1
    var i = 0
    while (i <= text.length) {
        val atEol = i == text.length || text[i] == '\n'
        if (atEol) {
            // line excludes the terminator; strip a trailing CR for matching/display
            val rawLineEnd = i
            val lineText = text.substring(lineStart, rawLineEnd)
            val matchText = if (lineText.endsWith("\r")) lineText.dropLast(1) else lineText
            var col = 0
            while (col < matchText.length) {
                val at = if (opts.regex) {
                    runCatching {
                        val re = if (opts.matchCase) Regex(opts.query) else Regex(opts.query, RegexOption.IGNORE_CASE)
                        re.find(matchText, col)?.range
                    }.getOrNull()
                } else {
                    val idx = matchText.indexOf(opts.query, col, ignoreCase = !opts.matchCase)
                    if (idx < 0) null else idx until (idx + opts.query.length)
                }
                if (at == null) break
                if (!opts.wholeWord || isWholeWordAt(matchText, at.first, at.last - at.first + 1)) {
                    hits.add(
                        SearchHit(
                            file = file,
                            uri = uri,
                            line = line,
                            column = at.first + 1,
                            lineText = matchText.trim(),
                            start = lineStart + at.first
                        )
                    )
                }
                col = if (at.isEmpty()) at.first + 1 else at.last + 1
            }
            if (i == text.length) break
            lineStart = i + 1
            line++
        }
        i++
    }
    return hits
}

/** Full-text scan for queries that span lines; line/column derived per hit. */
private fun findAllMultiLine(text: String, opts: FindOptions, file: String, uri: String?): List<SearchHit> {
    val hits = mutableListOf<SearchHit>()
    var i = 0
    val lineStarts = mutableListOf(0)
    text.forEachIndexed { idx, c -> if (c == '\n') lineStarts.add(idx + 1) }
    while (i <= text.length) {
        val idx = text.indexOf(opts.query, i, ignoreCase = !opts.matchCase)
        if (idx < 0) break
        val end = idx + opts.query.length
        if (!opts.wholeWord || isWholeWordAt(text, idx, opts.query.length)) {
            var line = 0
            while (line + 1 < lineStarts.size && lineStarts[line + 1] <= idx) line++
            val col = idx - lineStarts[line]
            val lineEnd = text.indexOf('\n', idx).let { if (it < 0) text.length else it }
            hits.add(
                SearchHit(
                    file = file,
                    uri = uri,
                    line = line + 1,
                    column = col + 1,
                    lineText = text.substring(lineStarts[line], lineEnd).trim(),
                    start = idx
                )
            )
        }
        i = if (end == idx) idx + 1 else end
    }
    return hits
}

private fun isWholeWordAt(text: String, idx: Int, len: Int): Boolean {
    fun wc(c: Char) = c.isLetterOrDigit() || c == '_'
    val before = idx > 0 && wc(text[idx - 1])
    val after = idx + len < text.length && wc(text[idx + len])
    return !before && !after
}
