package com.notedroidplusplus.app.search

/** Search options mirroring Notepad++ Find/Replace dialog. */
data class FindOptions(
    val query: String = "",
    val replaceWith: String = "",
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val wrapAround: Boolean = true,
    val regex: Boolean = false,
    val extended: Boolean = false,
    val dotMatchesNewline: Boolean = false,
    val direction: Direction = Direction.DOWN
) {
    enum class Direction { UP, DOWN }

    fun matches(text: String): Boolean = findNext(text, 0, this) >= 0
}

/** Resolves the effective query/replace strings (extended-mode unescaping). */
fun FindOptions.resolved(): FindOptions =
    if (!extended) this
    else copy(query = ExtendedMode.unescape(query), replaceWith = ExtendedMode.unescape(replaceWith))

/** Count of all matches of [opts] in [text]. */
fun countMatches(text: String, opts: FindOptions): Int {
    val o = opts.resolved()
    if (o.query.isEmpty()) return 0
    if (o.regex) {
        return runCatching { o.buildRegex().findAll(text).count() }.getOrDefault(0)
    }
    var count = 0
    var i = 0
    while (i <= text.length) {
        val idx = text.indexOf(o.query, i, ignoreCase = !o.matchCase)
        if (idx < 0) break
        if (!o.wholeWord || isWholeWordAt(text, idx, o.query.length)) count++
        i = idx + 1
    }
    return count
}

private fun FindOptions.buildRegex(): Regex = when {
    matchCase && dotMatchesNewline -> Regex(query, RegexOption.DOT_MATCHES_ALL)
    matchCase -> Regex(query)
    dotMatchesNewline -> Regex(query, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    else -> Regex(query, RegexOption.IGNORE_CASE)
}

fun findNext(text: String, start: Int, optsIn: FindOptions): Int {
    val opts = optsIn.resolved()
    if (opts.query.isEmpty()) return -1
    if (opts.regex) {
        return try {
            val re = opts.buildRegex()
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

fun findPrev(text: String, from: Int, optsIn: FindOptions): Int {
    val opts = optsIn.resolved()
    if (opts.query.isEmpty()) return -1
    if (opts.regex) {
        return try {
            val re = opts.buildRegex()
            val starts = re.findAll(text).map { it.range.first }.toList()
            val hit = starts.lastOrNull { it < from } ?: if (opts.wrapAround) starts.lastOrNull() else null
            hit ?: -1
        } catch (_: Exception) {
            -1
        }
    }
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
    val o = opts.resolved()
    if (o.query.isEmpty()) return text to 0
    var count = 0
    if (o.regex) {
        return try {
            val re = o.buildRegex()
            count = re.findAll(text).count()
            re.replace(text, o.replaceWith) to count
        } catch (_: Exception) {
            text to 0
        }
    }
    val sb = StringBuilder()
    var i = 0
    while (i <= text.length) {
        val idx = text.indexOf(o.query, i, ignoreCase = !o.matchCase)
        if (idx < 0) break
        if (o.wholeWord && !isWholeWordAt(text, idx, o.query.length)) {
            sb.append(text[i]); i++
            continue
        }
        sb.append(text, i, idx)
        sb.append(o.replaceWith)
        i = idx + o.query.length
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
