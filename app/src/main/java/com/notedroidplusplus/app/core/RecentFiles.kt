package com.notedroidplusplus.app.core

import android.content.Context
import android.content.SharedPreferences

/** Persists the most recently opened files (URI + display name). */
class RecentFiles(context: Context, private val max: Int = 10) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notedroid_recent", Context.MODE_PRIVATE)

    data class Entry(val uri: String, val name: String)

    fun add(uri: String, name: String) {
        val list = list().filterNot { it.uri == uri }.toMutableList()
        list.add(0, Entry(uri, name))
        save(list.take(max))
    }

    fun list(): List<Entry> {
        val raw = prefs.getString("recent", null) ?: return emptyList()
        return raw.split("\n").filter { it.contains('|') }.mapNotNull { line ->
            val uri = line.substringBefore('|')
            val name = line.substringAfter('|')
            if (uri.isNotBlank()) Entry(uri, name) else null
        }
    }

    fun remove(uri: String) {
        save(list().filterNot { it.uri == uri })
    }

    private fun save(entries: List<Entry>) {
        prefs.edit().putString("recent", entries.joinToString("\n") { "${it.uri}|${it.name}" }).apply()
    }
}
