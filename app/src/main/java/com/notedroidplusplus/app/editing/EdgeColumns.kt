package com.notedroidplusplus.app.editing

/**
 * Vertical edge guide settings (Notepad++ parity): one or more column
 * positions parsed from user text, optionally in background-shading mode.
 */
object EdgeColumns {

    /** Parses "80", "80, 100", "72 80 100" into a sorted distinct column list. */
    fun parse(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ' ', ';', '\t')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..9999 }
            .distinct()
            .sorted()
    }
}
