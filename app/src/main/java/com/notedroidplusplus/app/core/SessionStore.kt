package com.notedroidplusplus.app.core

import android.content.Context
import com.notedroidplusplus.app.LanguageMode
import com.notedroidplusplus.app.tools.Json
import java.io.File

/**
 * Persists the editor session (tabs) to app-private storage so a cold start
 * after a hard process kill can offer to restore unsaved work.
 * Format: JSON array of {uri, name, text, language, encoding, lineEnding, modified, caret}.
 */
class SessionStore(private val context: Context) {

    private fun file(): File = File(context.filesDir, "session.json")

    fun save(tabs: List<TabState>) {
        val arr = Json.Value.Arr(tabs.map { tab ->
            Json.Value.Obj(
                listOf(
                    "uri" to Json.Value.Str(tab.uri ?: ""),
                    "name" to Json.Value.Str(tab.name),
                    "text" to Json.Value.Str(tab.text),
                    "language" to Json.Value.Str(tab.language.name),
                    "encoding" to Json.Value.Str(tab.encoding),
                    "lineEnding" to Json.Value.Str(tab.lineEnding),
                    "modified" to Json.Value.Bool(tab.modified),
                    "caret" to Json.Value.Num(tab.caret.toString())
                )
            )
        })
        runCatching { file().writeText(Json.stringify(arr, 0)) }
    }

    fun load(): List<TabState>? {
        val f = file()
        if (!f.exists()) return null
        return runCatching {
            val arr = (Json.parse(f.readText()) as? Json.Value.Arr) ?: return null
            arr.items.mapNotNull { v ->
                val obj = v as? Json.Value.Obj ?: return@mapNotNull null
                fun str(key: String) = (obj.entries.firstOrNull { it.first == key }?.second as? Json.Value.Str)?.s ?: ""
                fun bool(key: String) = (obj.entries.firstOrNull { it.first == key }?.second as? Json.Value.Bool)?.b ?: false
                fun num(key: String) = ((obj.entries.firstOrNull { it.first == key }?.second as? Json.Value.Num)?.raw?.toIntOrNull() ?: 0)
                TabState(
                    uri = str("uri").ifBlank { null },
                    name = str("name"),
                    text = str("text"),
                    language = runCatching { LanguageMode.valueOf(str("language")) }.getOrDefault(LanguageMode.NORMAL_TEXT),
                    encoding = str("encoding").ifBlank { "UTF-8" },
                    lineEnding = str("lineEnding").ifBlank { "\n" },
                    modified = bool("modified"),
                    caret = num("caret")
                )
            }
        }.getOrNull()
    }

    fun clear() {
        file().delete()
    }

    data class TabState(
        val uri: String?,
        val name: String,
        val text: String,
        val language: LanguageMode,
        val encoding: String,
        val lineEnding: String,
        val modified: Boolean,
        val caret: Int
    )
}
