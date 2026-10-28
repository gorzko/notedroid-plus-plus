package com.notedroidplusplus.app.core

/**
 * Launch-keyed pending UDL export payloads (#71). SAF's CreateDocument
 * contract carries only a suggested file name (no launch token), so a
 * single activity-scoped var could be overwritten by interleaved
 * launches. This queue keys every payload by its launch id and the
 * activity enforces single-flight (a new launch is refused while one is
 * pending), which together guarantee a callback can never export the
 * wrong definition.
 */
class UdlExportQueue {
    private val map = LinkedHashMap<Int, String>()
    private var nextId = 0

    val pending: Boolean get() = map.isNotEmpty()

    /** Registers the payload for a new launch and returns its launch id. */
    fun begin(name: String): Int {
        val id = nextId++
        map[id] = name
        return id
    }

    /** Returns and removes the payload for the most recent in-flight launch. */
    fun consumeLatest(): String? {
        val key = map.keys.lastOrNull() ?: return null
        return map.remove(key)
    }

    /** Clears all in-flight payloads (e.g. on activity teardown). */
    fun clear() = map.clear()
}
