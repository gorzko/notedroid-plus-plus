package com.notedroidplusplus.app.editing

/**
 * Auto-completion candidate model (Notepad++ Settings > Auto-Completion parity).
 * Pure prefix matching/ranking over document words and language keywords.
 */
object AutoComplete {

    data class Config(
        val wordCompletion: Boolean = false,
        val minChars: Int = 3,
        val functionCompletion: Boolean = false
    )

    private val WORD_DELIMS = Regex("[^A-Za-z0-9_]+")

    /** Unique document words (identifier-like tokens), capped for large docs. */
    fun documentWords(text: String, cap: Int = 5000): List<String> {
        val seen = LinkedHashSet<String>()
        text.split(WORD_DELIMS).forEach { w -> if (w.isNotEmpty()) seen.add(w) }
        return seen.toList().take(cap)
    }

    /**
     * Candidates matching [prefix], ranked: exact-prefix matches first
     * (shorter first), the word equal to the prefix excluded.
     */
    fun candidates(prefix: String, words: List<String>, keywords: List<String>, minChars: Int): List<String> {
        if (prefix.length < minChars.coerceAtLeast(1)) return emptyList()
        val pool = buildList {
            addAll(words)
            addAll(keywords)
        }
        return pool.asSequence()
            .filter { it.length > prefix.length }
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .distinct()
            .sortedWith(compareBy({ it.length }, { it.lowercase() }))
            .take(30)
            .toList()
    }

    /** The identifier prefix ending at [offset] (empty when none). */
    fun prefixEndingAt(text: String, offset: Int): String {
        var start = offset
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        return if (start == offset) "" else text.substring(start, offset)
    }
}

/**
 * Matched-pair auto-insert (Notepad++ auto-insert of brackets/quotes/tags).
 * Pure decision function: given the text, caret, and the typed character,
 * returns the full text to insert (typed char + optional closer) or null when
 * the typed char should be handled by step-over logic instead.
 */
object MatchedPairs {

    private val PAIRS = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')

    sealed class Result {
        data class Insert(val text: String, val caretOffset: Int) : Result()
        object StepOver : Result()
        object None : Result()
    }

    /**
     * @param text document text before the key press
     * @param pos caret offset where [typed] would be inserted
     */
    fun onTyped(text: String, pos: Int, typed: Char, autoInsert: Boolean): Result {
        val next = text.getOrNull(pos)
        // Step over an existing closer when typing it.
        if (typed in PAIRS.values && next == typed) return Result.StepOver
        val closer = PAIRS[typed]
        if (closer != null && autoInsert) {
            // For quotes, don't auto-close when the next char is alphanumeric
            // (likely typing inside a word) or when the previous char is alphanumeric.
            if (typed == '"' || typed == '\'') {
                val prev = text.getOrNull(pos - 1)
                val nextAlnum = next?.isLetterOrDigit() == true
                val prevAlnum = prev?.isLetterOrDigit() == true || prev == typed
                if (nextAlnum || prevAlnum) return Result.Insert(typed.toString(), 1)
            }
            return Result.Insert("$typed$closer", 1)
        }
        return Result.None
    }

    /** True when backspace at [pos] sits between an empty pair that should be removed together. */
    fun isEmptyPairAround(text: String, pos: Int): Boolean {
        val prev = text.getOrNull(pos - 1) ?: return false
        val next = text.getOrNull(pos) ?: return false
        val closer = PAIRS[prev] ?: return false
        return next == closer
    }

    /** For HTML/XML: the closer text for a just-typed '>' when it closes an opened tag. */
    fun tagCloserFor(text: String, pos: Int): String? {
        val open = text.lastIndexOf('<', pos - 1)
        if (open < 0) return null
        val closeIdx = text.indexOf('>', open)
        if (closeIdx != pos - 1) return null
        val inner = text.substring(open + 1, pos - 1)
        if (inner.isEmpty() || inner.startsWith("/") || inner.startsWith("!") || inner.startsWith("?")) return null
        val name = inner.trim().split(Regex("\\s+")).first()
        if (name.isEmpty() || !name[0].isLetter() && name[0] != '_') return null
        // self-closing tags have no closer
        if (inner.trimEnd().endsWith("/")) return null
        return "</$name>"
    }
}
