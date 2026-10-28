package com.notedroidplusplus.app.search

/**
 * Pure Notepad++-style Find-in-Files filter matcher.
 *
 * Filter syntax (space-separated tokens, per the Notepad++ user manual):
 *  - `*.ext`            include glob (`*` and `?` wildcards)
 *  - `!*.ext`           exclude glob (file names)
 *  - `!\dir`            exclude a first-level directory named `dir`
 *  - `!+\dir`           exclude the directory `dir` at any depth (recursive)
 *
 * An empty include list matches everything not excluded. Directory names are
 * compared case-insensitively (SAF providers vary); file globs are too.
 */
object FindFilters {

    data class Parsed(
        val includes: List<String>,
        val fileExcludes: List<String>,
        val dirExcludesTop: List<String>,
        val dirExcludesRecursive: List<String>
    ) {
        val isEmpty: Boolean get() = includes.isEmpty() && fileExcludes.isEmpty() &&
            dirExcludesTop.isEmpty() && dirExcludesRecursive.isEmpty()
    }

    fun parse(filterText: String): Parsed {
        val includes = mutableListOf<String>()
        val fileExcludes = mutableListOf<String>()
        val dirsTop = mutableListOf<String>()
        val dirsRec = mutableListOf<String>()
        filterText.trim().split(Regex("\\s+")).forEach { raw ->
            val token = raw.trim()
            if (token.isEmpty()) return@forEach
            when {
                token.startsWith("!+\\") ->
                    if (token.length > 3) dirsRec.add(token.removePrefix("!+\\").lowercase())
                token.startsWith("!\\") ->
                    if (token.length > 2) dirsTop.add(token.removePrefix("!\\").lowercase())
                token.startsWith("!") ->
                    if (token.length > 1) fileExcludes.add(token.removePrefix("!"))
                else -> includes.add(token)
            }
        }
        return Parsed(includes, fileExcludes, dirsTop, dirsRec)
    }

    /** Basic glob match with `*` (any run) and `?` (single char), case-insensitive. */
    fun globMatches(pattern: String, name: String): Boolean {
        val p = pattern.lowercase()
        val n = name.lowercase()
        return globRec(p, 0, n, 0)
    }

    private fun globRec(p: String, pi: Int, n: String, ni: Int): Boolean {
        var pp = pi
        var nn = ni
        while (pp < p.length) {
            val c = p[pp]
            when {
                c == '*' -> {
                    if (pp == p.length - 1) return true
                    for (k in nn..n.length) {
                        if (globRec(p, pp + 1, n, k)) return true
                    }
                    return false
                }
                c == '?' -> {
                    if (nn >= n.length) return false
                    nn++
                }
                else -> {
                    if (nn >= n.length || n[nn] != c) return false
                    nn++
                }
            }
            pp++
        }
        return nn == n.length
    }

    /** True when a file name passes the include/exclude globs. */
    fun acceptsFile(filters: Parsed, fileName: String): Boolean {
        if (filters.fileExcludes.any { globMatches(it, fileName) }) return false
        if (filters.includes.isEmpty()) return true
        return filters.includes.any { globMatches(it, fileName) }
    }

    /**
     * True when a directory at [depth] (0 = top level) named [dirName] may be
     * entered: `!\d` excludes only the first-level `d`; `!+\d` excludes `d`
     * everywhere; dot-prefixed (hidden) directories are excluded as well when
     * any directory exclusion exists.
     */
    fun acceptsDirectory(filters: Parsed, dirName: String, depth: Int): Boolean {
        val name = dirName.lowercase()
        if (dirName.startsWith(".")) return false
        if (filters.dirExcludesRecursive.any { globMatches(it, name) }) return false
        if (depth == 0 && filters.dirExcludesTop.any { globMatches(it, name) }) return false
        return true
    }
}
