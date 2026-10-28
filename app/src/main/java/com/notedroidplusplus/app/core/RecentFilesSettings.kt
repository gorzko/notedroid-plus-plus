package com.notedroidplusplus.app.core

/**
 * Pure logic for the Recent Files History settings (Notepad++ parity):
 * max-entry parsing/trimming and display-mode labels.
 */
object RecentFilesSettings {

    const val DEFAULT_MAX = 10
    const val MAX_LIMIT = 100

    enum class DisplayMode { NAME_ONLY, FULL_PATH }

    /** Parses "80, 100 72" style column lists. */
    fun parseMax(raw: String?, fallback: Int = DEFAULT_MAX): Int {
        val n = raw?.trim()?.toIntOrNull() ?: fallback
        return n.coerceIn(1, MAX_LIMIT)
    }

    fun <T> trim(entries: List<T>, max: Int): List<T> = entries.take(max)

    fun label(entry: RecentFiles.Entry, mode: DisplayMode): String =
        if (mode == DisplayMode.FULL_PATH) entry.uri else entry.name
}
