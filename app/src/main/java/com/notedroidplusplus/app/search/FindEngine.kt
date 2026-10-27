package com.notedroidplusplus.app.search

/** Search options mirroring Notepad++ Find/Replace dialog. */
data class FindOptions(
    val query: String = "",
    val replaceWith: String = "",
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val wrapAround: Boolean = true,
    val regex: Boolean = false,
    val direction: Direction = Direction.DOWN
) {
    enum class Direction { UP, DOWN }

    fun matches(text: String): Boolean = findNext(text, 0, this) >= 0
}

fun findNext(text: String, start: Int, opts: FindOptions): Int {
    if (opts.query.isEmpty()) return -1
    if (opts.regex) {
        return try {
            val re = if (opts.matchCase) Regex(opts.query) else Regex(opts.query, RegexOption.IGNORE_CASE)
            val fwd = re.find(text, start)?.range?.first
            val wrapped = if (opts.wrapAround) re.find(text)?.range?.first else null
            fwd ?: wrapped ?: -1
        } catch (_: Exception) {
            -1
        }
    }
    var cur = start
    while (cur <= text.length) {
        val idx = text.indexOf(opts.query, cur, ignoreCase = !opts.matchCase)
        if (idx < 0) break
        if (opts.wholeWord && !isWholeWordAt(text, idx, opts.query.length)) {
            cur = idx + 1
            continue
        }
        return idx
    }
    if (opts.wrapAround && start > 0) return findOnceWrapped(text, opts)
    return -1
}

private fun findOnceWrapped(text: String, opts: FindOptions): Int {
    val idx = text.indexOf(opts.query, 0, ignoreCase = !opts.matchCase)
    if (idx < 0) return -1
    if (opts.wholeWord && !isWholeWordAt(text, idx, opts.query.length)) return -1
    return idx
}

fun findPrev(text: String, from: Int, opts: FindOptions): Int {
    if (opts.query.isEmpty()) return -1
    val hay = if (opts.matchCase) text else text.lowercase()
    val needle = if (opts.matchCase) opts.query else opts.query.lowercase()
    var idx = minOf(from - 1, hay.length - needle.length)
    while (idx >= 0) {
        if (hay.regionMatches(idx, needle, 0, needle.length) &&
            (!opts.wholeWord || isWholeWordAt(text, idx, needle.length))
        ) return idx
        idx--
    }
    return -1
}

fun replaceAll(text: String, opts: FindOptions): Pair<String, Int> {
    if (opts.query.isEmpty()) return text to 0
    var count = 0
    if (opts.regex) {
        return try {
            val re = if (opts.matchCase) Regex(opts.query) else Regex(opts.query, RegexOption.IGNORE_CASE)
            val result = re.replace(text) { count++; it.value }
            result to count
        } catch (_: Exception) {
            text to 0
        }
    }
    val sb = StringBuilder()
    var i = 0
    while (i <= text.length) {
        val idx = text.indexOf(opts.query, i, ignoreCase = !opts.matchCase)
        if (idx < 0) break
        if (opts.wholeWord && !isWholeWordAt(text, idx, opts.query.length)) {
            sb.append(text[i]); i++
            continue
        }
        sb.append(text, i, idx)
        sb.append(opts.replaceWith)
        i = idx + opts.query.length
        count++
    }
    sb.append(text, i, text.length)
    return sb.toString() to count
}

private fun isWholeWordAt(text: String, idx: Int, len: Int): Boolean {
    fun wc(c: Char) = c.isLetterOrDigit() || c == '_'
    val before = idx > 0 && wc(text[idx - 1])
    val after = idx + len < text.length && wc(text[idx + len])
    return !before && !after
}
