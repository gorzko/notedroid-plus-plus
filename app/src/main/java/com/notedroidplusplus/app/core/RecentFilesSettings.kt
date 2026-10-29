package com.notedroidplusplus.app.core

/**
 * Pure logic for the Recent Files History settings (Notepad++ parity):
 * max-entry parsing/trimming and display-mode labels.
 */
object RecentFilesSettings {

    const val DEFAULT_MAX = 10
    const val MAX_LIMIT = 100

    enum class DisplayMode { NAME_ONLY, FULL_PATH }

    /** Parses the max-entries setting (single integer), clamped to 1..MAX_LIMIT. */
    fun parseMax(raw: String?, fallback: Int = DEFAULT_MAX): Int {
        val n = raw?.trim()?.toIntOrNull() ?: fallback
        return n.coerceIn(1, MAX_LIMIT)
    }

    /**
     * Resolves the max-entries setting from a raw SharedPreferences value (#72).
     * `recent_max` is persisted by SeekBarPreference as an Int, but legacy
     * installs may still hold a String, so both shapes are accepted; anything
     * else falls back to the default. Always clamped to 1..MAX_LIMIT.
     */
    fun resolveMax(raw: Any?): Int = when (raw) {
        is Int -> raw.coerceIn(1, MAX_LIMIT)
        is Long -> raw.toInt().coerceIn(1, MAX_LIMIT)
        is String -> parseMax(raw)
        else -> DEFAULT_MAX
    }

    fun <T> trim(entries: List<T>, max: Int): List<T> = entries.take(max)

    fun label(entry: RecentFiles.Entry, mode: DisplayMode): String =
        if (mode == DisplayMode.FULL_PATH) entry.uri else entry.name
}
