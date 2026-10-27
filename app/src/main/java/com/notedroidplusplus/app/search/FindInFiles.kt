package com.notedroidplusplus.app.search

/** Find-in-Files result row: file path, line number, line content, match offset. */
data class SearchHit(val file: String, val line: Int, val column: Int, val lineText: String)

fun findAll(text: String, opts: FindOptions): List<SearchHit> {
    val hits = mutableListOf<SearchHit>()
    if (opts.query.isEmpty()) return hits
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    for ((idx, line) in lines.withIndex()) {
        var start = 0
        while (true) {
            val at = findNext(line, start, opts.copy(wrapAround = false))
            if (at < 0) break
            hits.add(SearchHit("(current)", idx + 1, at + 1, line.trim()))
            start = at + maxOf(1, opts.query.length)
            if (start > line.length) break
        }
    }
    return hits
}
