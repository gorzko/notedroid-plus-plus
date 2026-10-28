package com.notedroidplusplus.app.core

import android.content.Context
import android.content.SharedPreferences
import com.notedroidplusplus.app.tools.Json

/**
 * Persists the most recently opened files (URI + display name).
 * Stored as a JSON array so names containing '|' or newlines stay intact.
 */
class RecentFiles(context: Context, private var max: Int = 10) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notedroid_recent", Context.MODE_PRIVATE)

    data class Entry(val uri: String, val name: String)

    fun add(uri: String, name: String, maxEntries: Int = max) {
        max = maxEntries.coerceIn(1, RecentFilesSettings.MAX_LIMIT)
        val list = list().filterNot { it.uri == uri }.toMutableList()
        list.add(0, Entry(uri, name))
        save(list.take(max))
    }

    fun list(): List<Entry> {
        val raw = prefs.getString("recent", null) ?: return emptyList()
        if (raw.startsWith("[")) {
            return runCatching {
                ((Json.parse(raw) as? Json.Value.Arr)?.items ?: emptyList()).mapNotNull { v ->
                    val obj = v as? Json.Value.Obj ?: return@mapNotNull null
                    val uri = (obj.entries.firstOrNull { it.first == "uri" }?.second as? Json.Value.Str)?.s
                    val name = (obj.entries.firstOrNull { it.first == "name" }?.second as? Json.Value.Str)?.s
                    if (!uri.isNullOrBlank()) Entry(uri, name ?: uri) else null
                }
            }.getOrDefault(emptyList())
        }
        // legacy pipe/newline format
        return raw.split("\n").filter { it.contains('|') }.mapNotNull { line ->
            val uri = line.substringBefore('|')
            val name = line.substringAfter('|')
            if (uri.isNotBlank()) Entry(uri, name) else null
        }
    }

    fun remove(uri: String) {
        save(list().filterNot { it.uri == uri })
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(entries: List<Entry>) {
        val arr = Json.Value.Arr(entries.map {
            Json.Value.Obj(listOf("uri" to Json.Value.Str(it.uri), "name" to Json.Value.Str(it.name)))
        })
        prefs.edit().putString("recent", Json.stringify(arr, 0)).apply()
    }
}
