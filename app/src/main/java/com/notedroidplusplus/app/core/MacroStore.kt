package com.notedroidplusplus.app.core

import android.content.Context
import com.notedroidplusplus.app.tools.Json
import com.notedroidplusplus.app.tools.Json.Value
import java.io.File

/**
 * Persists saved macros as JSON under app-private storage (filesDir/macros).
 * Macro names are sanitized file-name-wise; the JSON body keeps the full name.
 */
class MacroStore(context: Context) {

    data class Macro(val name: String, val actions: List<MacroRecorder.Action>)

    private val dir = File(context.filesDir, "macros").apply { mkdirs() }

    fun list(): List<Macro> =
        dir.listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { parse(f.readText()) }.getOrNull()
        }.orEmpty()

    fun save(macro: Macro) {
        val file = File(dir, sanitize(macro.name) + ".json")
        file.writeText(stringify(macro))
    }

    fun delete(name: String) {
        val file = File(dir, sanitize(name) + ".json")
        if (file.exists()) file.delete()
    }

    fun sanitize(name: String): String =
        name.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }
            .joinToString("")
            .take(64)
            .ifEmpty { "macro" }

    private fun stringify(m: Macro): String {
        val actions = m.actions.map { a ->
            when (a) {
                is MacroRecorder.Action.Insert -> Value.Obj(listOf(
                    "type" to Value.Str("insert"),
                    "offset" to Value.Num(a.offset.toString()),
                    "text" to Value.Str(a.text)
                ))
                is MacroRecorder.Action.Delete -> Value.Obj(listOf(
                    "type" to Value.Str("delete"),
                    "start" to Value.Num(a.start.toString()),
                    "end" to Value.Num(a.end.toString())
                ))
                is MacroRecorder.Action.ReplaceAll -> Value.Obj(listOf(
                    "type" to Value.Str("replace-all"),
                    "find" to Value.Str(a.find),
                    "replace" to Value.Str(a.replace),
                    "regex" to Value.Bool(a.regex),
                    "matchCase" to Value.Bool(a.matchCase)
                ))
                is MacroRecorder.Action.TransformCase -> Value.Obj(listOf(
                    "type" to Value.Str("transform"),
                    "kind" to Value.Str(a.kind),
                    "start" to Value.Num(a.start.toString()),
                    "end" to Value.Num(a.end.toString())
                ))
                is MacroRecorder.Action.GoToLine -> Value.Obj(listOf(
                    "type" to Value.Str("goto"),
                    "line" to Value.Num(a.line.toString())
                ))
            }
        }
        val obj = Value.Obj(listOf(
            "name" to Value.Str(m.name),
            "actions" to Value.Arr(actions)
        ))
        return Json.stringify(obj, 0)
    }

    private fun parse(raw: String): Macro? {
        val obj = Json.parse(raw) as? Value.Obj ?: return null
        val name = (obj.entries.firstOrNull { it.first == "name" }?.second as? Value.Str)?.s ?: return null
        val arr = (obj.entries.firstOrNull { it.first == "actions" }?.second as? Value.Arr) ?: return null
        val actions = arr.items.mapNotNull { v ->
            val o = v as? Value.Obj ?: return@mapNotNull null
            fun str(k: String) = (o.entries.firstOrNull { it.first == k }?.second as? Value.Str)?.s
            fun int(k: String) = (o.entries.firstOrNull { it.first == k }?.second as? Value.Num)?.n?.toInt()
            fun bool(k: String) = (o.entries.firstOrNull { it.first == k }?.second as? Value.Bool)?.b ?: false
            when (str("type")) {
                "insert" -> str("text")?.let { t -> int("offset")?.let { off -> MacroRecorder.Action.Insert(off, t) } }
                "delete" -> {
                    val s = int("start"); val e = int("end")
                    if (s != null && e != null) MacroRecorder.Action.Delete(s, e) else null
                }
                "replace-all" -> str("find")?.let { f -> str("replace")?.let { r ->
                    MacroRecorder.Action.ReplaceAll(f, r, bool("regex"), bool("matchCase"))
                } }
                "transform" -> str("kind")?.let { k ->
                    val s = int("start") ?: 0; val e = int("end") ?: 0
                    MacroRecorder.Action.TransformCase(k, s, e)
                }
                "goto" -> int("line")?.let { MacroRecorder.Action.GoToLine(it) }
                else -> null
            }
        }
        return Macro(name, actions)
    }
}
